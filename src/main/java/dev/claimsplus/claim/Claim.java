package dev.claimsplus.claim;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class Claim {
    public static final UUID PUBLIC_TRUST = new UUID(0L, 0L);

    private final String id;
    private String groupId;
    private final LocationKey anchor;
    private final ClaimBounds bounds;
    private final UUID owner;
    private String ownerName;
    private final Map<UUID, TrustEntry> trusted;
    private final long createdAt;

    public Claim(String id, LocationKey anchor, ClaimBounds bounds, UUID owner, String ownerName,
                 Map<UUID, TrustEntry> trusted, long createdAt) {
        this(id, id, anchor, bounds, owner, ownerName, trusted, createdAt);
    }

    public Claim(String id, String groupId, LocationKey anchor, ClaimBounds bounds, UUID owner, String ownerName,
                 Map<UUID, TrustEntry> trusted, long createdAt) {
        this.id = id;
        this.groupId = cleanId(groupId, id);
        this.anchor = anchor;
        this.bounds = bounds;
        this.owner = owner;
        this.ownerName = cleanName(ownerName);
        this.trusted = new LinkedHashMap<>(trusted);
        this.createdAt = createdAt;
    }

    public String id() {
        return id;
    }

    public String groupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = cleanId(groupId, id);
    }

    public LocationKey anchor() {
        return anchor;
    }

    public ClaimBounds bounds() {
        return bounds;
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public long createdAt() {
        return createdAt;
    }

    public boolean contains(Location location) {
        return bounds.contains(location);
    }

    public boolean contains(String world, int x, int z) {
        return bounds.contains(world, x, z);
    }

    public boolean intersects(ClaimBounds otherBounds) {
        return bounds.intersects(otherBounds);
    }

    public boolean isAnchor(LocationKey location) {
        return anchor.equals(location);
    }

    public boolean isOwner(UUID uuid) {
        return owner.equals(uuid);
    }

    public boolean isTrusted(UUID uuid) {
        return trusted.containsKey(uuid);
    }

    public boolean hasTrust(Player player, TrustType type) {
        UUID uuid = player.getUniqueId();
        return isOwner(uuid) || hasTrust(uuid, type);
    }

    public boolean hasTrust(UUID uuid, TrustType type) {
        if (type == TrustType.PERMISSION && PUBLIC_TRUST.equals(uuid)) {
            return false;
        }
        TrustEntry entry = trusted.get(uuid);
        if (entry != null && entry.has(type)) {
            return true;
        }
        if (type == TrustType.PERMISSION) {
            return false;
        }
        TrustEntry publicEntry = trusted.get(PUBLIC_TRUST);
        return publicEntry != null && publicEntry.has(type);
    }

    public void updateOwnerName(String name) {
        ownerName = cleanName(name);
    }

    public boolean grantTrust(UUID uuid, String name, TrustType type) {
        if (owner.equals(uuid)) {
            return false;
        }
        TrustEntry entry = trusted.computeIfAbsent(uuid, ignored -> new TrustEntry(name, Set.of()));
        entry.updateName(name);
        return entry.grant(type);
    }

    public boolean removeTrusted(UUID uuid) {
        return trusted.remove(uuid) != null;
    }

    public Map<UUID, TrustEntry> trusted() {
        return Map.copyOf(trusted);
    }

    public Map<UUID, TrustEntry> copyTrusted() {
        Map<UUID, TrustEntry> copy = new LinkedHashMap<>();
        for (Map.Entry<UUID, TrustEntry> entry : trusted.entrySet()) {
            copy.put(entry.getKey(), new TrustEntry(entry.getValue().name(), entry.getValue().types()));
        }
        return copy;
    }

    public int trustedCount() {
        return trusted.size();
    }

    private String cleanName(String name) {
        return name == null || name.isBlank() ? "Unknown" : name;
    }

    private String cleanId(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
