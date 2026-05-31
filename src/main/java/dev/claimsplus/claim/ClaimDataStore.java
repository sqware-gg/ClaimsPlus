package dev.claimsplus.claim;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClaimDataStore {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, Claim> claims = new LinkedHashMap<>();
    private final Map<LocationKey, String> anchors = new HashMap<>();
    private final Map<String, Map<Long, List<String>>> chunkIndex = new HashMap<>();

    public ClaimDataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "claims.yml");
        reload();
    }

    public synchronized void reload() {
        claims.clear();
        anchors.clear();
        chunkIndex.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("claims");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection claimSection = section.getConfigurationSection(key);
            if (claimSection == null) {
                continue;
            }
            readClaim(key, claimSection).ifPresent(claim -> claims.put(claim.id(), claim));
        }
        repairDisconnectedGroups();
        rebuildIndexes();
    }

    public synchronized int claimCount() {
        return claims.size();
    }

    public synchronized int groupCount() {
        return (int) claims.values().stream()
                .map(this::groupKey)
                .distinct()
                .count();
    }

    public synchronized Collection<Claim> claims() {
        return List.copyOf(claims.values());
    }

    public synchronized List<Claim> claimsForOwner(UUID owner) {
        return claims.values().stream()
                .filter(claim -> claim.isOwner(owner))
                .toList();
    }

    public synchronized List<Claim> claimGroupsForOwner(UUID owner) {
        Map<GroupKey, Claim> groups = new LinkedHashMap<>();
        for (Claim claim : claims.values()) {
            if (!claim.isOwner(owner)) {
                continue;
            }
            groups.compute(groupKey(claim), (ignored, current) -> earliest(current, claim));
        }
        return groups.values().stream()
                .sorted(Comparator.comparing(Claim::ownerName)
                        .thenComparing(claim -> claim.anchor().world())
                        .thenComparingInt(claim -> claim.anchor().x())
                        .thenComparingInt(claim -> claim.anchor().z()))
                .toList();
    }

    public synchronized List<Claim> claimsInGroup(Claim source) {
        if (source == null) {
            return List.of();
        }
        return claims.values().stream()
                .filter(claim -> sameGroup(source, claim))
                .sorted(Comparator.comparingLong(Claim::createdAt)
                        .thenComparing(Claim::id))
                .toList();
    }

    public synchronized int claimCountInGroup(Claim source) {
        int count = 0;
        for (Claim claim : claims.values()) {
            if (sameGroup(source, claim)) {
                count++;
            }
        }
        return count;
    }

    public synchronized boolean sameGroup(Claim first, Claim second) {
        if (first == null || second == null) {
            return false;
        }
        return groupKey(first).equals(groupKey(second));
    }

    public synchronized int claimCountForOwner(UUID owner) {
        int count = 0;
        for (Claim claim : claims.values()) {
            if (claim.isOwner(owner)) {
                count++;
            }
        }
        return count;
    }

    public synchronized Optional<Claim> claimById(String id) {
        return Optional.ofNullable(claims.get(id));
    }

    public synchronized Optional<Claim> claimAt(Location location) {
        String world = location.getWorld() == null ? "" : location.getWorld().getName();
        return claimAt(world, location.getBlockX(), location.getBlockZ());
    }

    public synchronized Optional<Claim> claimAt(String world, int x, int z) {
        Map<Long, List<String>> worldIndex = chunkIndex.get(normalizeWorld(world));
        if (worldIndex == null) {
            return Optional.empty();
        }
        List<String> ids = worldIndex.get(pack(x >> 4, z >> 4));
        if (ids == null || ids.isEmpty()) {
            return Optional.empty();
        }
        for (String id : ids) {
            Claim claim = claims.get(id);
            if (claim != null && claim.contains(world, x, z)) {
                return Optional.of(claim);
            }
        }
        return Optional.empty();
    }

    public synchronized Optional<Claim> claimByAnchor(LocationKey anchor) {
        return Optional.ofNullable(claims.get(anchors.get(anchor)));
    }

    public synchronized Optional<Claim> firstIntersecting(ClaimBounds bounds) {
        for (Claim claim : intersecting(bounds)) {
            return Optional.of(claim);
        }
        return Optional.empty();
    }

    public synchronized List<Claim> intersecting(ClaimBounds bounds) {
        Map<Long, List<String>> worldIndex = chunkIndex.get(normalizeWorld(bounds.world()));
        if (worldIndex == null) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<Claim> matches = new ArrayList<>();
        for (int chunkX = bounds.minChunkX(); chunkX <= bounds.maxChunkX(); chunkX++) {
            for (int chunkZ = bounds.minChunkZ(); chunkZ <= bounds.maxChunkZ(); chunkZ++) {
                List<String> ids = worldIndex.get(pack(chunkX, chunkZ));
                if (ids == null) {
                    continue;
                }
                for (String id : ids) {
                    if (!seen.add(id)) {
                        continue;
                    }
                    Claim claim = claims.get(id);
                    if (claim != null && claim.intersects(bounds)) {
                        matches.add(claim);
                    }
                }
            }
        }
        return matches;
    }

    public synchronized void addClaim(Claim claim) {
        claims.put(claim.id(), claim);
        rebuildIndexes();
    }

    public synchronized boolean removeClaim(String id) {
        Claim removedClaim = claims.remove(id);
        boolean removed = removedClaim != null;
        if (removed) {
            splitDisconnectedGroup(removedClaim);
            rebuildIndexes();
        }
        return removed;
    }

    public synchronized boolean save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Claim claim : claims.values()) {
            String path = "claims." + claim.id();
            yaml.set(path + ".world", claim.anchor().world());
            yaml.set(path + ".group-id", claim.groupId());
            yaml.set(path + ".anchor.x", claim.anchor().x());
            yaml.set(path + ".anchor.y", claim.anchor().y());
            yaml.set(path + ".anchor.z", claim.anchor().z());
            yaml.set(path + ".bounds.min-x", claim.bounds().minX());
            yaml.set(path + ".bounds.max-x", claim.bounds().maxX());
            yaml.set(path + ".bounds.min-z", claim.bounds().minZ());
            yaml.set(path + ".bounds.max-z", claim.bounds().maxZ());
            yaml.set(path + ".owner.uuid", claim.owner().toString());
            yaml.set(path + ".owner.name", claim.ownerName());
            yaml.set(path + ".created-at", claim.createdAt());
            for (Map.Entry<UUID, TrustEntry> trusted : claim.trusted().entrySet()) {
                String trustPath = path + ".trusted." + trusted.getKey();
                yaml.set(trustPath + ".name", trusted.getValue().name());
                yaml.set(trustPath + ".types", trusted.getValue().types().stream()
                        .map(Enum::name)
                        .sorted()
                        .toList());
            }
        }

        Path target = file.toPath();
        Path directory = target.getParent();
        Path backup = target.resolveSibling(file.getName() + ".bak");
        Path temp = null;
        try {
            Files.createDirectories(directory);
            if (Files.exists(target)) {
                Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            temp = Files.createTempFile(directory, file.getName(), ".tmp");
            Files.writeString(temp, yaml.saveToString(), StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING);
            moveIntoPlace(temp, target);
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save claims.yml: " + e.getMessage());
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
            return false;
        }
    }

    private Optional<Claim> readClaim(String id, ConfigurationSection section) {
        String world = section.getString("world", "");
        String ownerUuid = section.getString("owner.uuid", "");
        if (world.isBlank() || ownerUuid.isBlank()) {
            return Optional.empty();
        }
        try {
            UUID owner = UUID.fromString(ownerUuid);
            LocationKey anchor = new LocationKey(
                    world,
                    section.getInt("anchor.x"),
                    section.getInt("anchor.y"),
                    section.getInt("anchor.z")
            );
            ClaimBounds bounds = new ClaimBounds(
                    world,
                    section.getInt("bounds.min-x"),
                    section.getInt("bounds.max-x"),
                    section.getInt("bounds.min-z"),
                    section.getInt("bounds.max-z")
            );
            Map<UUID, TrustEntry> trusted = readTrusted(section.getConfigurationSection("trusted"));
            return Optional.of(new Claim(
                    id,
                    section.getString("group-id", id),
                    anchor,
                    bounds,
                    owner,
                    section.getString("owner.name", "Unknown"),
                    trusted,
                    section.getLong("created-at", System.currentTimeMillis())
            ));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Ignoring claim with invalid UUID: " + id);
            return Optional.empty();
        }
    }

    private Map<UUID, TrustEntry> readTrusted(ConfigurationSection section) {
        Map<UUID, TrustEntry> trusted = new LinkedHashMap<>();
        if (section == null) {
            return trusted;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                trusted.put(uuid, new TrustEntry(section.getString(key + ".name", "Unknown"), readTrustTypes(section, key)));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Ignoring trusted player with invalid UUID: " + key);
            }
        }
        return trusted;
    }

    private EnumSet<TrustType> readTrustTypes(ConfigurationSection section, String key) {
        List<String> values = section.getStringList(key + ".types");
        if (values.isEmpty()) {
            return EnumSet.of(TrustType.BUILD);
        }
        EnumSet<TrustType> types = EnumSet.noneOf(TrustType.class);
        for (String value : values) {
            try {
                types.add(TrustType.valueOf(value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Ignoring invalid trust type '" + value + "' for " + key);
            }
        }
        if (types.isEmpty()) {
            types.add(TrustType.BUILD);
        }
        return types;
    }

    private void splitDisconnectedGroup(Claim removedClaim) {
        List<Claim> groupClaims = claims.values().stream()
                .filter(claim -> sameGroup(removedClaim, claim))
                .sorted(Comparator.comparingLong(Claim::createdAt)
                        .thenComparing(Claim::id))
                .toList();
        splitDisconnectedClaims(groupClaims, removedClaim.groupId());
    }

    private void repairDisconnectedGroups() {
        Map<GroupKey, List<Claim>> groups = new LinkedHashMap<>();
        for (Claim claim : claims.values()) {
            groups.computeIfAbsent(groupKey(claim), ignored -> new ArrayList<>()).add(claim);
        }
        for (List<Claim> groupClaims : groups.values()) {
            groupClaims.sort(Comparator.comparingLong(Claim::createdAt)
                    .thenComparing(Claim::id));
            splitDisconnectedClaims(groupClaims, groupClaims.getFirst().groupId());
        }
    }

    private void splitDisconnectedClaims(List<Claim> groupClaims, String groupId) {
        if (groupClaims.size() <= 1) {
            return;
        }
        List<List<Claim>> components = connectedComponents(groupClaims);
        if (components.size() <= 1) {
            return;
        }
        for (int i = 0; i < components.size(); i++) {
            List<Claim> component = components.get(i);
            String componentGroupId = i == 0 ? groupId : component.getFirst().id();
            for (Claim claim : component) {
                claim.setGroupId(componentGroupId);
            }
        }
    }

    private List<List<Claim>> connectedComponents(List<Claim> groupClaims) {
        Set<String> remaining = new HashSet<>();
        Map<String, Claim> byId = new HashMap<>();
        for (Claim claim : groupClaims) {
            remaining.add(claim.id());
            byId.put(claim.id(), claim);
        }
        List<List<Claim>> components = new ArrayList<>();
        while (!remaining.isEmpty()) {
            String first = remaining.iterator().next();
            List<Claim> component = new ArrayList<>();
            Deque<Claim> queue = new ArrayDeque<>();
            queue.add(byId.get(first));
            remaining.remove(first);
            while (!queue.isEmpty()) {
                Claim current = queue.removeFirst();
                component.add(current);
                List<String> connected = new ArrayList<>();
                for (String candidateId : remaining) {
                    Claim candidate = byId.get(candidateId);
                    if (current.bounds().adjacentTo(candidate.bounds())) {
                        connected.add(candidateId);
                    }
                }
                for (String candidateId : connected) {
                    remaining.remove(candidateId);
                    queue.add(byId.get(candidateId));
                }
            }
            component.sort(Comparator.comparingLong(Claim::createdAt)
                    .thenComparing(Claim::id));
            components.add(component);
        }
        components.sort(Comparator.comparing((List<Claim> component) -> component.getFirst().createdAt())
                .thenComparing(component -> component.getFirst().id()));
        return components;
    }

    private Claim earliest(Claim current, Claim candidate) {
        if (current == null) {
            return candidate;
        }
        int created = Long.compare(candidate.createdAt(), current.createdAt());
        if (created < 0 || (created == 0 && candidate.id().compareTo(current.id()) < 0)) {
            return candidate;
        }
        return current;
    }

    private void rebuildIndexes() {
        anchors.clear();
        chunkIndex.clear();
        for (Claim claim : claims.values()) {
            anchors.put(claim.anchor(), claim.id());
            String world = normalizeWorld(claim.bounds().world());
            Map<Long, List<String>> worldIndex = chunkIndex.computeIfAbsent(world, ignored -> new HashMap<>());
            for (int chunkX = claim.bounds().minChunkX(); chunkX <= claim.bounds().maxChunkX(); chunkX++) {
                for (int chunkZ = claim.bounds().minChunkZ(); chunkZ <= claim.bounds().maxChunkZ(); chunkZ++) {
                    worldIndex.computeIfAbsent(pack(chunkX, chunkZ), ignored -> new ArrayList<>()).add(claim.id());
                }
            }
        }
    }

    private long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private String normalizeWorld(String world) {
        return world == null ? "" : world.toLowerCase(Locale.ROOT);
    }

    private GroupKey groupKey(Claim claim) {
        return new GroupKey(claim.owner(), normalizeWorld(claim.bounds().world()), claim.groupId());
    }

    private void moveIntoPlace(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private record GroupKey(UUID owner, String world, String groupId) {
    }
}
