package dev.claimsplus.claim;

import dev.claimsplus.config.ClaimsPlusConfig;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClaimBorderVisualizer {
    private final JavaPlugin plugin;
    private final ClaimsPlusConfig config;

    public ClaimBorderVisualizer(JavaPlugin plugin, ClaimsPlusConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void show(Player player, Claim claim) {
        if (!config.visualBorderEnabled() || player == null || claim == null || player.getWorld() == null) {
            return;
        }
        World world = player.getWorld();
        if (!world.getName().equals(claim.anchor().world())) {
            return;
        }
        List<Location> locations = borderLocations(world, claim);
        if (locations.isEmpty()) {
            return;
        }
        BlockData visual = config.visualBorderMaterial().createBlockData();
        for (Location location : locations) {
            player.sendBlockChange(location, visual);
        }
        long ticks = Math.max(1L, config.visualBorderDurationSeconds() * 20L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> refresh(player, locations), ticks);
    }

    private List<Location> borderLocations(World world, Claim claim) {
        ClaimBounds bounds = claim.bounds();
        int layers = Math.max(1, config.visualBorderLayers());
        int startY = clampY(world, claim.anchor().y() + config.visualBorderHeightOffset());
        List<Location> locations = new ArrayList<>();
        for (int layer = 0; layer < layers; layer++) {
            int y = clampY(world, startY + layer);
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                add(locations, world, x, y, bounds.minZ());
                add(locations, world, x, y, bounds.maxZ());
            }
            for (int z = bounds.minZ() + 1; z < bounds.maxZ(); z++) {
                add(locations, world, bounds.minX(), y, z);
                add(locations, world, bounds.maxX(), y, z);
            }
        }
        return locations;
    }

    private void add(List<Location> locations, World world, int x, int y, int z) {
        locations.add(new Location(world, x, y, z));
    }

    private int clampY(World world, int y) {
        return Math.max(world.getMinHeight(), Math.min(world.getMaxHeight() - 1, y));
    }

    private void refresh(Player player, List<Location> locations) {
        if (player == null || !player.isOnline()) {
            return;
        }
        for (Location location : locations) {
            if (location.getWorld() == null || !location.getWorld().equals(player.getWorld())) {
                continue;
            }
            player.sendBlockChange(location, location.getBlock().getBlockData());
        }
    }
}
