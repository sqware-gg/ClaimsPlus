package dev.claimsplus.claim;

import dev.claimsplus.config.ClaimsPlusConfig;
import dev.claimsplus.util.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;

public final class ClaimService {
    private final JavaPlugin plugin;
    private final ClaimsPlusConfig config;
    private final ClaimDataStore store;
    private final ClaimBorderVisualizer borderVisualizer;
    private final Map<UUID, Long> protectedFeedbackAt = new HashMap<>();
    private final Map<UUID, Long> previewSuppressedUntil = new HashMap<>();
    private BukkitTask saveTask;
    private BukkitTask previewTask;

    public ClaimService(JavaPlugin plugin, ClaimsPlusConfig config, ClaimDataStore store) {
        this.plugin = plugin;
        this.config = config;
        this.store = store;
        this.borderVisualizer = new ClaimBorderVisualizer(plugin, config);
    }

    public void start() {
        scheduleSaveTask();
        schedulePreviewTask();
    }

    public void stop() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (previewTask != null) {
            previewTask.cancel();
            previewTask = null;
        }
        borderVisualizer.clearAllPreviews();
        previewSuppressedUntil.clear();
        save();
    }

    public void reload() {
        save();
        config.reload();
        store.reload();
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (previewTask != null) {
            previewTask.cancel();
            previewTask = null;
        }
        borderVisualizer.clearAllPreviews();
        previewSuppressedUntil.clear();
        scheduleSaveTask();
        schedulePreviewTask();
    }

    public boolean save() {
        return store.save();
    }

    public ClaimsPlusConfig config() {
        return config;
    }

    public int claimCount() {
        return store.claimCount();
    }

    public int groupCount() {
        return store.groupCount();
    }

    public List<Claim> claimsForOwner(UUID owner) {
        return store.claimsForOwner(owner).stream()
                .sorted(Comparator.comparing(Claim::ownerName)
                        .thenComparing(claim -> claim.anchor().world())
                        .thenComparingInt(claim -> claim.anchor().x())
                        .thenComparingInt(claim -> claim.anchor().z()))
                .toList();
    }

    public List<Claim> claimGroupsForOwner(UUID owner) {
        return store.claimGroupsForOwner(owner);
    }

    public Optional<Claim> claimAt(Location location) {
        if (location == null || location.getWorld() == null || !config.worldEnabled(location.getWorld())) {
            return Optional.empty();
        }
        return store.claimAt(location);
    }

    public Optional<Claim> claimByAnchor(LocationKey anchor) {
        return store.claimByAnchor(anchor);
    }

    public Optional<Claim> claimById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return store.claimById(id);
    }

    public boolean canAccess(Player player, Claim claim) {
        return canUse(player, claim, TrustType.ACCESS);
    }

    public boolean canUse(Player player, Claim claim, TrustType type) {
        return claim == null || player.hasPermission("claimsplus.bypass") || claim.hasTrust(player, type);
    }

    public boolean canUse(Player player, Location location, TrustType type) {
        return claimAt(location)
                .map(claim -> canUse(player, claim, type))
                .orElse(true);
    }

    public boolean isProtected(Location location) {
        return claimAt(location).isPresent();
    }

    public boolean crossesClaimBoundary(Location first, Location second) {
        Optional<Claim> firstClaim = claimAt(first);
        Optional<Claim> secondClaim = claimAt(second);
        if (firstClaim.isEmpty() && secondClaim.isEmpty()) {
            return false;
        }
        if (firstClaim.isEmpty() || secondClaim.isEmpty()) {
            return true;
        }
        return !store.sameGroup(firstClaim.get(), secondClaim.get());
    }

    public ClaimResult createClaim(Player player, Block block) {
        if (!player.hasPermission("claimsplus.create")) {
            return ClaimResult.failure("no-permission", Map.of());
        }
        if (block.getType() != config.claimBlock()) {
            return ClaimResult.failure("claim-block-required", Map.of());
        }
        if (!config.worldEnabled(block.getWorld())) {
            return ClaimResult.failure("claims-disabled-world", Map.of());
        }
        int limit = config.maxClaimsPerPlayer();
        if (limit >= 0 && store.claimCountForOwner(player.getUniqueId()) >= limit) {
            return ClaimResult.failure("claim-limit", Map.of("limit", Integer.toString(limit)));
        }

        Optional<Claim> current = store.claimAt(block.getWorld().getName(), block.getX(), block.getZ());
        if (current.isPresent()) {
            Claim claim = current.get();
            if (!claim.isOwner(player.getUniqueId())) {
                return ClaimResult.failure("claim-overlaps", placeholders(claim));
            }
            if (!config.expandNearbyOwnedClaims()) {
                return ClaimResult.failure("claim-already-owned", placeholders(claim));
            }
            ClaimDirection direction = ClaimDirection.fromYaw(player.getLocation().getYaw());
            return expansionHint(player, claim, direction);
        }

        Optional<ExpansionTarget> expansion = expansionTarget(player, block);
        if (expansion.isPresent()) {
            return createExpansion(player, block, expansion.get());
        }

        ClaimBounds bounds = ClaimBounds.around(block.getLocation(), config.claimSize());
        Optional<Claim> overlap = store.firstIntersecting(bounds);
        if (overlap.isPresent()) {
            return ClaimResult.failure("claim-overlaps", placeholders(overlap.get()));
        }

        LocationKey anchor = LocationKey.from(block);
        Claim claim = new Claim(
                UUID.randomUUID().toString(),
                anchor,
                bounds,
                player.getUniqueId(),
                player.getName(),
                Map.of(),
                System.currentTimeMillis()
        );
        store.addClaim(claim);
        saveIfConfigured();
        borderVisualizer.show(player, claim);
        suppressClaimPreview(player);
        return ClaimResult.success("claim-created", placeholders(claim));
    }

    private ClaimResult createExpansion(Player player, Block block, ExpansionTarget target) {
        Optional<Claim> overlap = store.firstIntersecting(target.bounds());
        if (overlap.isPresent()) {
            return ClaimResult.failure("claim-overlaps", placeholders(overlap.get()));
        }

        Claim source = target.source();
        Claim claim = new Claim(
                UUID.randomUUID().toString(),
                source.groupId(),
                LocationKey.from(block),
                target.bounds(),
                source.owner(),
                player.getName(),
                source.copyTrusted(),
                System.currentTimeMillis()
        );
        store.addClaim(claim);
        mergeAdjacentOwnedGroups(claim);
        saveIfConfigured();
        borderVisualizer.show(player, store.claimsInGroup(claim));
        suppressClaimPreview(player);
        Map<String, String> placeholders = placeholders(claim);
        placeholders.put("direction", target.direction().label());
        placeholders.put("tiles", Integer.toString(store.claimCountInGroup(claim)));
        return ClaimResult.success("claim-expanded", placeholders);
    }

    private Optional<ExpansionTarget> expansionTarget(Player player, Block block) {
        if (!config.expandNearbyOwnedClaims()) {
            return Optional.empty();
        }
        String world = block.getWorld().getName();
        int x = block.getX();
        int z = block.getZ();

        List<ExpansionTarget> exact = new ArrayList<>();
        ClaimDirection facing = ClaimDirection.fromYaw(player.getLocation().getYaw());
        for (Claim claim : store.claimsForOwner(player.getUniqueId())) {
            if (!claim.bounds().sameWorld(world)) {
                continue;
            }
            for (ClaimDirection direction : ClaimDirection.values()) {
                ClaimBounds bounds = claim.bounds().adjacent(direction);
                if (bounds.contains(world, x, z)) {
                    long score = (bounds.distanceSquaredToCenter(x, z) * 10L) + (direction == facing ? 0L : 1L);
                    ExpansionTarget target = new ExpansionTarget(claim, bounds, direction, score);
                    exact.add(target);
                }
            }
        }
        return bestTarget(exact);
    }

    private ClaimResult expansionHint(Player player, Claim source, ClaimDirection direction) {
        ExpansionHint hint = expansionHintTarget(player, source, direction);
        if (hint.target().isEmpty()) {
            Claim blocker = hint.blocker().orElse(source);
            if (blocker.isOwner(player.getUniqueId())) {
                return ClaimResult.failure("claim-already-owned", placeholders(blocker));
            }
            return ClaimResult.failure("claim-overlaps", placeholders(blocker));
        }

        ClaimBounds bounds = hint.target().get().bounds();
        borderVisualizer.preview(player, List.of(bounds));
        Map<String, String> placeholders = placeholders(source);
        placeholders.put("direction", direction.label());
        return ClaimResult.failure("claim-expand-hint", placeholders);
    }

    private ExpansionHint expansionHintTarget(Player player, Claim source, ClaimDirection direction) {
        List<ExpansionTarget> targets = new ArrayList<>();
        Optional<Claim> blocker = Optional.empty();
        int playerX = player.getLocation().getBlockX();
        int playerZ = player.getLocation().getBlockZ();

        for (Claim claim : store.claimsInGroup(source)) {
            ClaimBounds bounds = claim.bounds().adjacent(direction);
            Optional<Claim> overlap = store.firstIntersecting(bounds);
            if (overlap.isEmpty()) {
                long score = bounds.distanceSquaredToCenter(playerX, playerZ);
                targets.add(new ExpansionTarget(claim, bounds, direction, score));
                continue;
            }
            if (blocker.isEmpty() && !store.sameGroup(source, overlap.get())) {
                blocker = overlap;
            }
        }
        return new ExpansionHint(bestTarget(targets), blocker);
    }

    private Optional<ExpansionTarget> bestTarget(List<ExpansionTarget> targets) {
        return targets.stream()
                .min(Comparator.comparingLong(ExpansionTarget::score)
                        .thenComparing(target -> target.direction().label())
                        .thenComparing(target -> target.source().id()));
    }

    private void mergeAdjacentOwnedGroups(Claim seed) {
        List<Claim> owned = store.claimsForOwner(seed.owner()).stream()
                .filter(claim -> claim.bounds().sameWorld(seed.bounds().world()))
                .toList();
        Set<String> mergedGroups = new LinkedHashSet<>();
        mergedGroups.add(seed.groupId());

        boolean changed;
        do {
            changed = false;
            for (Claim claim : owned) {
                if (mergedGroups.contains(claim.groupId())) {
                    continue;
                }
                if (touchesAnyMergedGroup(claim, owned, mergedGroups)) {
                    mergedGroups.add(claim.groupId());
                    changed = true;
                }
            }
        } while (changed);

        if (mergedGroups.size() <= 1) {
            return;
        }

        Map<UUID, TrustEntry> trusted = mergedTrust(owned, mergedGroups);
        for (Claim claim : owned) {
            if (!mergedGroups.contains(claim.groupId())) {
                continue;
            }
            claim.setGroupId(seed.groupId());
            applyTrust(claim, trusted);
        }
    }

    private boolean touchesAnyMergedGroup(Claim claim, List<Claim> owned, Set<String> mergedGroups) {
        for (Claim merged : owned) {
            if (mergedGroups.contains(merged.groupId()) && claim.bounds().adjacentTo(merged.bounds())) {
                return true;
            }
        }
        return false;
    }

    private Map<UUID, TrustEntry> mergedTrust(List<Claim> claims, Set<String> groupIds) {
        Map<UUID, TrustEntry> trusted = new LinkedHashMap<>();
        for (Claim claim : claims) {
            if (!groupIds.contains(claim.groupId())) {
                continue;
            }
            for (Map.Entry<UUID, TrustEntry> entry : claim.trusted().entrySet()) {
                TrustEntry merged = trusted.computeIfAbsent(
                        entry.getKey(),
                        ignored -> new TrustEntry(entry.getValue().name(), Set.of())
                );
                merged.updateName(entry.getValue().name());
                for (TrustType type : entry.getValue().types()) {
                    merged.grant(type);
                }
            }
        }
        return trusted;
    }

    private Map<UUID, TrustEntry> mergedTrust(List<Claim> claims) {
        Map<UUID, TrustEntry> trusted = new LinkedHashMap<>();
        for (Claim claim : claims) {
            for (Map.Entry<UUID, TrustEntry> entry : claim.trusted().entrySet()) {
                TrustEntry merged = trusted.computeIfAbsent(
                        entry.getKey(),
                        ignored -> new TrustEntry(entry.getValue().name(), Set.of())
                );
                merged.updateName(entry.getValue().name());
                for (TrustType type : entry.getValue().types()) {
                    merged.grant(type);
                }
            }
        }
        return trusted;
    }

    private void applyTrust(Claim claim, Map<UUID, TrustEntry> trusted) {
        for (Map.Entry<UUID, TrustEntry> entry : trusted.entrySet()) {
            for (TrustType type : entry.getValue().types()) {
                claim.grantTrust(entry.getKey(), entry.getValue().name(), type);
            }
        }
    }

    public ClaimResult removeClaim(Player player, Claim claim) {
        if (!claim.isOwner(player.getUniqueId()) && !player.hasPermission("claimsplus.bypass")) {
            return ClaimResult.failure("anchor-owner-only", Map.of("owner", claim.ownerName()));
        }
        store.removeClaim(claim.id());
        saveIfConfigured();
        return ClaimResult.success("claim-removed", placeholders(claim));
    }

    public ClaimResult removeClaimAdmin(Claim claim) {
        store.removeClaim(claim.id());
        saveIfConfigured();
        return ClaimResult.success("claim-removed", placeholders(claim));
    }

    public ClaimResult showClaimBorder(Player player) {
        if (!player.hasPermission("claimsplus.info")) {
            return ClaimResult.failure("no-permission", Map.of());
        }
        Optional<Claim> current = claimAt(player.getLocation());
        if (current.isEmpty()) {
            return ClaimResult.failure("not-in-claim", Map.of());
        }
        Claim claim = current.get();
        borderVisualizer.show(player, store.claimsInGroup(claim));
        suppressClaimPreview(player);
        return ClaimResult.success("claim-border-shown", placeholders(claim));
    }

    public void previewClaimPlacement(Player player) {
        if (!canPreviewClaimPlacement(player)) {
            borderVisualizer.clearPreview(player);
            return;
        }

        Optional<Block> target = targetedPlacementBlock(player);
        if (target.isEmpty()) {
            borderVisualizer.clearPreview(player);
            return;
        }

        Optional<ClaimBounds> bounds = previewBounds(player, target.get());
        if (bounds.isEmpty()) {
            borderVisualizer.clearPreview(player);
            return;
        }
        borderVisualizer.preview(player, List.of(bounds.get()));
    }

    private boolean canPreviewClaimPlacement(Player player) {
        return config.claimPreviewEnabled()
                && player != null
                && player.isOnline()
                && player.hasPermission("claimsplus.create")
                && !previewSuppressed(player)
                && holdsClaimBlock(player);
    }

    private boolean previewSuppressed(Player player) {
        long until = previewSuppressedUntil.getOrDefault(player.getUniqueId(), 0L);
        if (System.currentTimeMillis() < until) {
            return true;
        }
        previewSuppressedUntil.remove(player.getUniqueId());
        return false;
    }

    private void suppressClaimPreview(Player player) {
        if (!config.claimPreviewEnabled()) {
            return;
        }
        long durationMillis = Math.max(1L, config.visualBorderDurationSeconds()) * 1000L;
        previewSuppressedUntil.put(player.getUniqueId(), System.currentTimeMillis() + durationMillis);
    }

    private boolean holdsClaimBlock(Player player) {
        return isClaimBlock(player.getInventory().getItemInMainHand())
                || isClaimBlock(player.getInventory().getItemInOffHand());
    }

    private boolean isClaimBlock(ItemStack item) {
        return item != null && item.getType() == config.claimBlock();
    }

    private Optional<Block> targetedPlacementBlock(Player player) {
        RayTraceResult result = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                config.claimPreviewRangeBlocks(),
                FluidCollisionMode.NEVER,
                true
        );
        if (result == null || result.getHitBlock() == null) {
            return Optional.empty();
        }
        BlockFace face = result.getHitBlockFace();
        if (face == null || face == BlockFace.SELF) {
            return Optional.empty();
        }
        Block target = result.getHitBlock().getRelative(face);
        if (!target.getType().isAir()) {
            return Optional.empty();
        }
        return Optional.of(target);
    }

    private Optional<ClaimBounds> previewBounds(Player player, Block block) {
        if (!config.worldEnabled(block.getWorld())) {
            return Optional.empty();
        }
        int limit = config.maxClaimsPerPlayer();
        if (limit >= 0 && store.claimCountForOwner(player.getUniqueId()) >= limit) {
            return Optional.empty();
        }

        Optional<Claim> current = store.claimAt(block.getWorld().getName(), block.getX(), block.getZ());
        if (current.isPresent()) {
            Claim claim = current.get();
            if (!claim.isOwner(player.getUniqueId()) || !config.expandNearbyOwnedClaims()) {
                return Optional.empty();
            }
            ClaimDirection direction = ClaimDirection.fromYaw(player.getLocation().getYaw());
            return expansionHintTarget(player, claim, direction)
                    .target()
                    .map(ExpansionTarget::bounds);
        }

        Optional<ExpansionTarget> expansion = expansionTarget(player, block);
        if (expansion.isPresent()) {
            ClaimBounds bounds = expansion.get().bounds();
            return store.firstIntersecting(bounds).isPresent() ? Optional.empty() : Optional.of(bounds);
        }

        ClaimBounds bounds = ClaimBounds.around(block.getLocation(), config.claimSize());
        return intersectsExistingClaim(bounds) ? Optional.empty() : Optional.of(bounds);
    }

    private boolean intersectsExistingClaim(ClaimBounds bounds) {
        return store.firstIntersecting(bounds).isPresent();
    }

    public ClaimResult trust(Player player, String targetName, TrustType type) {
        if (!player.hasPermission("claimsplus.trust")) {
            return ClaimResult.failure("no-permission", Map.of());
        }
        Optional<Claim> current = claimAt(player.getLocation());
        if (current.isEmpty()) {
            return ClaimResult.failure("not-in-claim", Map.of());
        }
        Claim claim = current.get();
        if (!claim.isOwner(player.getUniqueId())
                && !player.hasPermission("claimsplus.bypass")
                && !claim.hasTrust(player, TrustType.PERMISSION)) {
            return ClaimResult.failure("not-claim-owner", placeholders(claim));
        }
        PlayerLookup target = lookupPlayer(targetName);
        if (target == null) {
            return ClaimResult.failure("player-not-found", Map.of());
        }
        if (target.uuid().equals(Claim.PUBLIC_TRUST) && type == TrustType.PERMISSION) {
            return ClaimResult.failure("public-permission-trust-disabled", Map.of());
        }
        if (claim.isOwner(target.uuid())) {
            return ClaimResult.failure("trusted-self", Map.of("player", target.name()));
        }
        boolean changed = false;
        for (Claim groupClaim : store.claimsInGroup(claim)) {
            changed = groupClaim.grantTrust(target.uuid(), target.name(), type) || changed;
        }
        if (!changed) {
            return ClaimResult.failure("already-trusted", Map.of(
                    "player", target.name(),
                    "type", type.label(),
                    "type-description", type.description()
            ));
        }
        saveIfConfigured();
        notifyTrustedTarget(player, target, claim, type);
        return ClaimResult.success("trusted", Map.of(
                "player", target.name(),
                "type", type.label(),
                "type-description", type.description()
        ));
    }

    public ClaimResult untrust(Player player, String targetName) {
        if (!player.hasPermission("claimsplus.trust")) {
            return ClaimResult.failure("no-permission", Map.of());
        }
        Optional<Claim> current = claimAt(player.getLocation());
        if (current.isEmpty()) {
            return ClaimResult.failure("not-in-claim", Map.of());
        }
        Claim claim = current.get();
        if (!claim.isOwner(player.getUniqueId())
                && !player.hasPermission("claimsplus.bypass")
                && !claim.hasTrust(player, TrustType.PERMISSION)) {
            return ClaimResult.failure("not-claim-owner", placeholders(claim));
        }
        PlayerLookup target = lookupPlayer(targetName);
        if (target == null) {
            return ClaimResult.failure("player-not-found", Map.of());
        }
        boolean changed = false;
        for (Claim groupClaim : store.claimsInGroup(claim)) {
            changed = groupClaim.removeTrusted(target.uuid()) || changed;
        }
        if (!changed) {
            return ClaimResult.failure("not-trusted", Map.of("player", target.name()));
        }
        saveIfConfigured();
        notifyUntrustedTarget(player, target, claim);
        return ClaimResult.success("untrusted", Map.of("player", target.name()));
    }

    public String trustedNames(Claim claim) {
        return trustedNames(mergedTrust(store.claimsInGroup(claim)).values());
    }

    private String trustedNames(Iterable<TrustEntry> trusted) {
        List<String> names = new ArrayList<>();
        for (TrustEntry entry : trusted) {
            names.add(entry.name());
        }
        return names.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));
    }

    public String trustedNames(Claim claim, TrustType type) {
        return trustedNames(mergedTrust(store.claimsInGroup(claim)).values(), type);
    }

    private String trustedNames(Iterable<TrustEntry> trusted, TrustType type) {
        List<String> names = new ArrayList<>();
        for (TrustEntry entry : trusted) {
            if (entry.types().contains(type)) {
                names.add(entry.name());
            }
        }
        return names.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String rendered = Text.color(config.prefix() + Text.render(config.message(key), placeholders));
        for (String line : rendered.split("\\R", -1)) {
            sender.sendMessage(line);
        }
        playFeedback(sender, key);
    }

    public void send(CommandSender sender, ClaimResult result) {
        send(sender, result.messageKey(), result.placeholders());
    }

    public void sendProtected(Player player, Claim claim) {
        long now = System.currentTimeMillis();
        long last = protectedFeedbackAt.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < config.feedbackProtectedCooldownMillis()) {
            return;
        }
        protectedFeedbackAt.put(player.getUniqueId(), now);
        send(player, "protected", Map.of("owner", claim.ownerName()));
    }

    public Map<String, String> placeholders(Claim claim) {
        List<Claim> groupClaims = store.claimsInGroup(claim);
        if (groupClaims.isEmpty()) {
            groupClaims = List.of(claim);
        }
        ClaimBounds groupBounds = groupBounds(claim, groupClaims);
        Map<UUID, TrustEntry> trusted = mergedTrust(groupClaims);
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("id", claim.id());
        placeholders.put("group-id", claim.groupId());
        placeholders.put("tiles", Integer.toString(groupClaims.isEmpty() ? 1 : groupClaims.size()));
        placeholders.put("owner", claim.ownerName());
        placeholders.put("world", claim.anchor().world());
        placeholders.put("x", Integer.toString(claim.anchor().x()));
        placeholders.put("y", Integer.toString(claim.anchor().y()));
        placeholders.put("z", Integer.toString(claim.anchor().z()));
        placeholders.put("min-x", Integer.toString(claim.bounds().minX()));
        placeholders.put("max-x", Integer.toString(claim.bounds().maxX()));
        placeholders.put("min-z", Integer.toString(claim.bounds().minZ()));
        placeholders.put("max-z", Integer.toString(claim.bounds().maxZ()));
        placeholders.put("group-min-x", Integer.toString(groupBounds.minX()));
        placeholders.put("group-max-x", Integer.toString(groupBounds.maxX()));
        placeholders.put("group-min-z", Integer.toString(groupBounds.minZ()));
        placeholders.put("group-max-z", Integer.toString(groupBounds.maxZ()));
        placeholders.put("trusted-count", Integer.toString(trusted.size()));
        placeholders.put("trusted", trustedNames(trusted.values()));
        placeholders.put("access-trusted", trustedNames(trusted.values(), TrustType.ACCESS));
        placeholders.put("container-trusted", trustedNames(trusted.values(), TrustType.CONTAINER));
        placeholders.put("build-trusted", trustedNames(trusted.values(), TrustType.BUILD));
        placeholders.put("permission-trusted", trustedNames(trusted.values(), TrustType.PERMISSION));
        return placeholders;
    }

    private ClaimBounds groupBounds(Claim fallback, List<Claim> groupClaims) {
        ClaimBounds bounds = fallback.bounds();
        int minX = bounds.minX();
        int maxX = bounds.maxX();
        int minZ = bounds.minZ();
        int maxZ = bounds.maxZ();
        for (Claim claim : groupClaims) {
            ClaimBounds candidate = claim.bounds();
            if (!candidate.sameWorld(bounds.world())) {
                continue;
            }
            minX = Math.min(minX, candidate.minX());
            maxX = Math.max(maxX, candidate.maxX());
            minZ = Math.min(minZ, candidate.minZ());
            maxZ = Math.max(maxZ, candidate.maxZ());
        }
        return new ClaimBounds(bounds.world(), minX, maxX, minZ, maxZ);
    }

    private void scheduleSaveTask() {
        int interval = config.saveIntervalSeconds();
        if (interval <= 0) {
            return;
        }
        long ticks = interval * 20L;
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::save, ticks, ticks);
    }

    private void schedulePreviewTask() {
        if (!config.claimPreviewEnabled()) {
            return;
        }
        long ticks = Math.max(1L, config.claimPreviewRefreshTicks());
        previewTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            cleanupExpiredPreviewSuppressions();
            for (Player player : Bukkit.getOnlinePlayers()) {
                previewClaimPlacement(player);
            }
        }, ticks, ticks);
    }

    private void cleanupExpiredPreviewSuppressions() {
        long now = System.currentTimeMillis();
        previewSuppressedUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private void saveIfConfigured() {
        if (config.saveOnChange()) {
            save();
        }
    }

    private void playFeedback(CommandSender sender, String key) {
        if (!config.feedbackSoundsEnabled() || !(sender instanceof Player player)) {
            return;
        }
        Sound sound = config.feedbackSound(key);
        if (sound == null) {
            return;
        }
        player.playSound(player.getLocation(), sound, SoundCategory.MASTER,
                config.feedbackSoundVolume(), config.feedbackSoundPitch());
    }

    private void notifyTrustedTarget(Player source, PlayerLookup target, Claim claim, TrustType type) {
        if (target.uuid().equals(Claim.PUBLIC_TRUST)) {
            return;
        }
        Player online = Bukkit.getPlayer(target.uuid());
        if (online == null || online.getUniqueId().equals(source.getUniqueId())) {
            return;
        }
        send(online, "trusted-target", Map.of(
                "source", source.getName(),
                "owner", claim.ownerName(),
                "type", type.label(),
                "type-description", type.description()
        ));
    }

    private void notifyUntrustedTarget(Player source, PlayerLookup target, Claim claim) {
        if (target.uuid().equals(Claim.PUBLIC_TRUST)) {
            return;
        }
        Player online = Bukkit.getPlayer(target.uuid());
        if (online == null || online.getUniqueId().equals(source.getUniqueId())) {
            return;
        }
        send(online, "untrusted-target", Map.of(
                "source", source.getName(),
                "owner", claim.ownerName()
        ));
    }

    @SuppressWarnings("deprecation")
    private PlayerLookup lookupPlayer(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        if (input.equalsIgnoreCase("public")) {
            return new PlayerLookup(Claim.PUBLIC_TRUST, "public");
        }
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            return new PlayerLookup(online.getUniqueId(), online.getName());
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(input);
        if (!config.allowUnknownTrust() && !offline.hasPlayedBefore()) {
            return null;
        }
        String name = offline.getName() == null ? input : offline.getName();
        return new PlayerLookup(offline.getUniqueId(), name);
    }

    private record ExpansionTarget(Claim source, ClaimBounds bounds, ClaimDirection direction, long score) {
    }

    private record ExpansionHint(Optional<ExpansionTarget> target, Optional<Claim> blocker) {
    }

    private record PlayerLookup(UUID uuid, String name) {
    }
}
