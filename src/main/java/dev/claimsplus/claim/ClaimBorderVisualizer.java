package dev.claimsplus.claim;

import dev.claimsplus.config.ClaimsPlusConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class ClaimBorderVisualizer {
    private final JavaPlugin plugin;
    private final ClaimsPlusConfig config;
    private final Map<UUID, PreviewState> previews = new HashMap<>();

    public ClaimBorderVisualizer(JavaPlugin plugin, ClaimsPlusConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void show(Player player, Claim claim) {
        show(player, claim == null ? List.of() : List.of(claim));
    }

    public void show(Player player, Collection<Claim> claims) {
        clearPreview(player);
        if (!config.visualBorderEnabled() || player == null || player.getWorld() == null) {
            return;
        }
        World world = player.getWorld();
        if (claims == null || claims.isEmpty()) {
            return;
        }
        List<Claim> visibleClaims = claims.stream()
                .filter(claim -> world.getName().equals(claim.anchor().world()))
                .toList();
        if (visibleClaims.isEmpty()) {
            return;
        }
        List<ClaimBounds> visibleBounds = visibleClaims.stream()
                .map(Claim::bounds)
                .toList();
        List<Location> locations = borderLocations(world, visibleBounds, player.getLocation().getBlockY());
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

    public void preview(Player player, Collection<ClaimBounds> bounds) {
        if (!config.visualBorderEnabled() || !config.claimPreviewEnabled() || player == null || player.getWorld() == null) {
            clearPreview(player);
            return;
        }
        if (bounds == null || bounds.isEmpty()) {
            clearPreview(player);
            return;
        }

        World world = player.getWorld();
        List<ClaimBounds> visibleBounds = bounds.stream()
                .filter(bound -> bound.sameWorld(world.getName()))
                .toList();
        if (visibleBounds.isEmpty()) {
            clearPreview(player);
            return;
        }

        List<Location> locations = borderLocations(world, visibleBounds, player.getLocation().getBlockY());
        String signature = signature(locations);
        PreviewState current = previews.get(player.getUniqueId());
        if (current != null && current.signature().equals(signature)) {
            current.task().cancel();
            BukkitTask task = schedulePreviewClear(player);
            previews.put(player.getUniqueId(), new PreviewState(signature, locations, task));
            return;
        }

        clearPreview(player);
        BlockData visual = config.visualBorderMaterial().createBlockData();
        for (Location location : locations) {
            player.sendBlockChange(location, visual);
        }
        BukkitTask task = schedulePreviewClear(player);
        previews.put(player.getUniqueId(), new PreviewState(signature, locations, task));
    }

    public void clearPreview(Player player) {
        if (player == null) {
            return;
        }
        PreviewState state = previews.remove(player.getUniqueId());
        if (state == null) {
            return;
        }
        state.task().cancel();
        refresh(player, state.locations());
    }

    public void clearAllPreviews() {
        for (UUID uuid : List.copyOf(previews.keySet())) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                clearPreview(player);
                continue;
            }
            PreviewState state = previews.remove(uuid);
            if (state != null) {
                state.task().cancel();
            }
        }
    }

    private List<Location> borderLocations(World world, List<ClaimBounds> boundsList, int baseY) {
        int layers = Math.max(1, config.visualBorderLayers());
        int startY = clampY(world, baseY + config.visualBorderHeightOffset());
        List<Location> locations = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int layer = 0; layer < layers; layer++) {
            int y = clampY(world, startY + layer);
            for (ClaimBounds bounds : boundsList) {
                for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                    if (!contains(boundsList, x, bounds.minZ() - 1)) {
                        add(locations, seen, world, x, y, bounds.minZ());
                    }
                    if (!contains(boundsList, x, bounds.maxZ() + 1)) {
                        add(locations, seen, world, x, y, bounds.maxZ());
                    }
                }
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    if (!contains(boundsList, bounds.minX() - 1, z)) {
                        add(locations, seen, world, bounds.minX(), y, z);
                    }
                    if (!contains(boundsList, bounds.maxX() + 1, z)) {
                        add(locations, seen, world, bounds.maxX(), y, z);
                    }
                }
            }
        }
        return locations;
    }

    private boolean contains(Collection<ClaimBounds> boundsList, int x, int z) {
        for (ClaimBounds bounds : boundsList) {
            if (bounds.contains(bounds.world(), x, z)) {
                return true;
            }
        }
        return false;
    }

    private void add(List<Location> locations, Set<String> seen, World world, int x, int y, int z) {
        if (!seen.add(x + ":" + y + ":" + z)) {
            return;
        }
        locations.add(new Location(world, x, y, z));
    }

    private String signature(List<Location> locations) {
        StringBuilder signature = new StringBuilder();
        for (Location location : locations) {
            signature.append(location.getWorld() == null ? "" : location.getWorld().getName())
                    .append(':')
                    .append(location.getBlockX())
                    .append(':')
                    .append(location.getBlockY())
                    .append(':')
                    .append(location.getBlockZ())
                    .append(';');
        }
        return signature.toString();
    }

    private BukkitTask schedulePreviewClear(Player player) {
        long ticks = Math.max(1L, config.claimPreviewDurationTicks());
        return plugin.getServer().getScheduler().runTaskLater(plugin, () -> clearPreview(player), ticks);
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

    private record PreviewState(String signature, List<Location> locations, BukkitTask task) {
    }
}
