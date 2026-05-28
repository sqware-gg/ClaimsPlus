package dev.claimsplus.claim;

import org.bukkit.Location;
import org.bukkit.block.Block;

public record LocationKey(String world, int x, int y, int z) {
    public static LocationKey from(Block block) {
        return from(block.getLocation());
    }

    public static LocationKey from(Location location) {
        String worldName = location.getWorld() == null ? "" : location.getWorld().getName();
        return new LocationKey(worldName, location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
