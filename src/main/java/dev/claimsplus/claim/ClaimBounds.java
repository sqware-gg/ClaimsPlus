package dev.claimsplus.claim;

import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.World;

public record ClaimBounds(String world, int minX, int maxX, int minZ, int maxZ) {
    public static ClaimBounds around(Location location, int size) {
        int safeSize = Math.max(1, size);
        int minX = location.getBlockX() - (safeSize / 2);
        int minZ = location.getBlockZ() - (safeSize / 2);
        return new ClaimBounds(worldName(location.getWorld()), minX, minX + safeSize - 1, minZ, minZ + safeSize - 1);
    }

    public boolean contains(Location location) {
        return contains(worldName(location.getWorld()), location.getBlockX(), location.getBlockZ());
    }

    public boolean contains(String otherWorld, int x, int z) {
        return normalize(world).equals(normalize(otherWorld))
                && x >= minX
                && x <= maxX
                && z >= minZ
                && z <= maxZ;
    }

    public boolean intersects(ClaimBounds other) {
        return normalize(world).equals(normalize(other.world))
                && minX <= other.maxX
                && maxX >= other.minX
                && minZ <= other.maxZ
                && maxZ >= other.minZ;
    }

    public int minChunkX() {
        return minX >> 4;
    }

    public int maxChunkX() {
        return maxX >> 4;
    }

    public int minChunkZ() {
        return minZ >> 4;
    }

    public int maxChunkZ() {
        return maxZ >> 4;
    }

    private static String worldName(World world) {
        return world == null ? "" : world.getName();
    }

    private static String normalize(String world) {
        return world == null ? "" : world.toLowerCase(Locale.ROOT);
    }
}
