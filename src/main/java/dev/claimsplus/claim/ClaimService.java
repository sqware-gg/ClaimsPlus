package dev.claimsplus.claim;

import dev.claimsplus.config.ClaimsPlusConfig;
import dev.claimsplus.util.Text;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class ClaimService {
    private final JavaPlugin plugin;
    private final ClaimsPlusConfig config;
    private final ClaimDataStore store;
    private final ClaimBorderVisualizer borderVisualizer;
    private final Map<UUID, Long> protectedFeedbackAt = new HashMap<>();
    private BukkitTask saveTask;

    public ClaimService(JavaPlugin plugin, ClaimsPlusConfig config, ClaimDataStore store) {
        this.plugin = plugin;
        this.config = config;
        this.store = store;
        this.borderVisualizer = new ClaimBorderVisualizer(plugin, config);
    }

    public void start() {
        scheduleSaveTask();
    }

    public void stop() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
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
        scheduleSaveTask();
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

    public List<Claim> claimsForOwner(UUID owner) {
        return store.claimsForOwner(owner).stream()
                .sorted(Comparator.comparing(Claim::ownerName)
                        .thenComparing(claim -> claim.anchor().world())
                        .thenComparingInt(claim -> claim.anchor().x())
                        .thenComparingInt(claim -> claim.anchor().z()))
                .toList();
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
        return firstClaim.map(Claim::id).orElse("").equals(secondClaim.map(Claim::id).orElse("")) == false;
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

        ClaimBounds bounds = ClaimBounds.around(block.getLocation(), config.claimSize());
        if (config.preventOverlap()) {
            Optional<Claim> overlap = store.firstIntersecting(bounds);
            if (overlap.isPresent()) {
                return ClaimResult.failure("claim-overlaps", placeholders(overlap.get()));
            }
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
        return ClaimResult.success("claim-created", placeholders(claim));
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
        borderVisualizer.show(player, claim);
        return ClaimResult.success("claim-border-shown", placeholders(claim));
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
        if (!claim.grantTrust(target.uuid(), target.name(), type)) {
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
        if (!claim.removeTrusted(target.uuid())) {
            return ClaimResult.failure("not-trusted", Map.of("player", target.name()));
        }
        saveIfConfigured();
        notifyUntrustedTarget(player, target, claim);
        return ClaimResult.success("untrusted", Map.of("player", target.name()));
    }

    public String trustedNames(Claim claim) {
        return claim.trusted().values().stream()
                .map(TrustEntry::name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));
    }

    public String trustedNames(Claim claim, TrustType type) {
        return claim.trusted().values().stream()
                .filter(entry -> entry.types().contains(type))
                .map(TrustEntry::name)
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
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("id", claim.id());
        placeholders.put("owner", claim.ownerName());
        placeholders.put("world", claim.anchor().world());
        placeholders.put("x", Integer.toString(claim.anchor().x()));
        placeholders.put("y", Integer.toString(claim.anchor().y()));
        placeholders.put("z", Integer.toString(claim.anchor().z()));
        placeholders.put("min-x", Integer.toString(claim.bounds().minX()));
        placeholders.put("max-x", Integer.toString(claim.bounds().maxX()));
        placeholders.put("min-z", Integer.toString(claim.bounds().minZ()));
        placeholders.put("max-z", Integer.toString(claim.bounds().maxZ()));
        placeholders.put("trusted-count", Integer.toString(claim.trustedCount()));
        placeholders.put("trusted", trustedNames(claim));
        placeholders.put("access-trusted", trustedNames(claim, TrustType.ACCESS));
        placeholders.put("container-trusted", trustedNames(claim, TrustType.CONTAINER));
        placeholders.put("build-trusted", trustedNames(claim, TrustType.BUILD));
        placeholders.put("permission-trusted", trustedNames(claim, TrustType.PERMISSION));
        return placeholders;
    }

    private void scheduleSaveTask() {
        int interval = config.saveIntervalSeconds();
        if (interval <= 0) {
            return;
        }
        long ticks = interval * 20L;
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::save, ticks, ticks);
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

    private record PlayerLookup(UUID uuid, String name) {
    }
}
