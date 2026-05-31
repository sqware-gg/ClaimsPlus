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

    public ClaimBounds adjacent(ClaimDirection direction) {
        int width = width();
        int depth = depth();
        return switch (direction) {
            case NORTH -> new ClaimBounds(world, minX, maxX, minZ - depth, minZ - 1);
            case EAST -> new ClaimBounds(world, maxX + 1, maxX + width, minZ, maxZ);
            case SOUTH -> new ClaimBounds(world, minX, maxX, maxZ + 1, maxZ + depth);
            case WEST -> new ClaimBounds(world, minX - width, minX - 1, minZ, maxZ);
        };
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

    public boolean adjacentTo(ClaimBounds other) {
        if (!normalize(world).equals(normalize(other.world))) {
            return false;
        }
        boolean eastWest = (maxX + 1 == other.minX || other.maxX + 1 == minX)
                && rangesOverlap(minZ, maxZ, other.minZ, other.maxZ);
        boolean northSouth = (maxZ + 1 == other.minZ || other.maxZ + 1 == minZ)
                && rangesOverlap(minX, maxX, other.minX, other.maxX);
        return eastWest || northSouth;
    }

    public boolean sameWorld(String otherWorld) {
        return normalize(world).equals(normalize(otherWorld));
    }

    public long distanceSquaredToCenter(int x, int z) {
        double centerX = (minX + maxX) / 2.0D;
        double centerZ = (minZ + maxZ) / 2.0D;
        double deltaX = x - centerX;
        double deltaZ = z - centerZ;
        return Math.round(deltaX * deltaX + deltaZ * deltaZ);
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int depth() {
        return maxZ - minZ + 1;
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

    private static boolean rangesOverlap(int firstMin, int firstMax, int secondMin, int secondMax) {
        return firstMin <= secondMax && firstMax >= secondMin;
    }
}
