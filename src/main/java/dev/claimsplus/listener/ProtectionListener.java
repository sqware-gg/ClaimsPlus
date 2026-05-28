package dev.claimsplus.listener;

import dev.claimsplus.claim.Claim;
import dev.claimsplus.claim.ClaimResult;
import dev.claimsplus.claim.ClaimService;
import dev.claimsplus.claim.LocationKey;
import dev.claimsplus.claim.TrustType;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.entity.EntityBreakDoorEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.projectiles.ProjectileSource;

public final class ProtectionListener implements Listener {
    private final ClaimService service;

    public ProtectionListener(ClaimService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        if (service.config().protectBlocks() && deny(player, block.getLocation(), TrustType.BUILD, true)) {
            event.setCancelled(true);
            return;
        }
        if (block.getType() != service.config().claimBlock()) {
            return;
        }
        ClaimResult result = service.createClaim(player, block);
        if (!result.success()) {
            event.setCancelled(true);
        }
        service.send(player, result);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Claim anchorClaim = service.claimByAnchor(LocationKey.from(block)).orElse(null);
        if (anchorClaim != null) {
            if (!anchorClaim.isOwner(player.getUniqueId()) && !player.hasPermission("claimsplus.bypass")) {
                event.setCancelled(true);
                service.send(player, "anchor-owner-only", service.placeholders(anchorClaim));
            }
            return;
        }
        if (service.config().protectBlocks() && deny(player, block.getLocation(), TrustType.BUILD, true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClaimAnchorBroken(BlockBreakEvent event) {
        Claim anchorClaim = service.claimByAnchor(LocationKey.from(event.getBlock())).orElse(null);
        if (anchorClaim == null) {
            return;
        }
        ClaimResult result = service.removeClaim(event.getPlayer(), anchorClaim);
        service.send(event.getPlayer(), result);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        switch (event.getAction()) {
            case PHYSICAL -> {
                TrustType type = clicked.getType() == Material.FARMLAND ? TrustType.BUILD : TrustType.ACCESS;
                if (service.config().protectBlocks() && deny(event.getPlayer(), clicked.getLocation(), type, false)) {
                    event.setCancelled(true);
                }
            }
            case RIGHT_CLICK_BLOCK -> {
                TrustType type = requiredTrustForInteract(clicked);
                if (type != null && deny(event.getPlayer(), clicked.getLocation(), type, true)) {
                    event.setCancelled(true);
                }
            }
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block clicked = event.getBlockClicked();
        Block target = clicked.getRelative(event.getBlockFace());
        if (deny(event.getPlayer(), clicked.getLocation(), TrustType.BUILD, true)
                || deny(event.getPlayer(), target.getLocation(), TrustType.BUILD, true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (deny(event.getPlayer(), event.getBlockClicked().getLocation(), TrustType.BUILD, true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (service.config().protectBlocks() && deny(event.getPlayer(), event.getBlock().getLocation(), TrustType.BUILD, true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (!service.config().protectFire()) {
            return;
        }
        Player player = event.getPlayer();
        if (player != null) {
            if (deny(player, event.getBlock().getLocation(), TrustType.BUILD, true)) {
                event.setCancelled(true);
            }
            return;
        }
        if (service.isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (service.config().protectFire() && service.isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (service.config().protectFire()
                && service.crossesClaimBoundary(event.getSource().getLocation(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent event) {
        if (service.config().protectFluids()
                && service.crossesClaimBoundary(event.getBlock().getLocation(), event.getToBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (!service.config().protectBlocks()) {
            return;
        }
        Block block = event.getBlock();
        BlockData data = block.getBlockData();
        if (!(data instanceof Directional directional)) {
            return;
        }
        Block target = block.getRelative(directional.getFacing());
        if (service.crossesClaimBoundary(block.getLocation(), target.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!service.config().protectPistons()) {
            return;
        }
        if (crosses(event.getBlock(), event.getBlock().getRelative(event.getDirection()))) {
            event.setCancelled(true);
            return;
        }
        for (Block moved : event.getBlocks()) {
            if (crosses(moved, moved.getRelative(event.getDirection()))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!service.config().protectPistons()) {
            return;
        }
        if (crosses(event.getBlock(), event.getBlock().getRelative(event.getDirection()))) {
            event.setCancelled(true);
            return;
        }
        BlockFace opposite = event.getDirection().getOppositeFace();
        for (Block moved : event.getBlocks()) {
            if (crosses(moved, moved.getRelative(event.getDirection())) || crosses(moved, moved.getRelative(opposite))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (service.config().protectExplosions()) {
            event.blockList().removeIf(block -> service.isProtected(block.getLocation()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (service.config().protectExplosions()) {
            event.blockList().removeIf(block -> service.isProtected(block.getLocation()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (service.config().protectBlocks() && service.isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityBreakDoor(EntityBreakDoorEvent event) {
        if (service.config().protectBlocks() && service.isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpongeAbsorb(SpongeAbsorbEvent event) {
        if (!service.config().protectFluids()) {
            return;
        }
        Location sponge = event.getBlock().getLocation();
        event.getBlocks().removeIf(state -> service.crossesClaimBoundary(sponge, state.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        if (!service.config().protectBlocks()) {
            return;
        }
        Location source = event.getLocation();
        for (BlockState state : event.getBlocks()) {
            if (service.crossesClaimBoundary(source, state.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFertilize(BlockFertilizeEvent event) {
        if (!service.config().protectBlocks()) {
            return;
        }
        Player player = event.getPlayer();
        for (BlockState state : event.getBlocks()) {
            if (player != null) {
                if (deny(player, state.getLocation(), TrustType.BUILD, true)) {
                    event.setCancelled(true);
                    return;
                }
            } else if (service.crossesClaimBoundary(event.getBlock().getLocation(), state.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        if (service.config().protectBlocks()
                && deny(event.getPlayer(), event.getHarvestedBlock().getLocation(), TrustType.BUILD, true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        if (!service.config().protectBlocks()) {
            return;
        }
        Player player = responsiblePlayer(event.getEntity());
        for (BlockState state : event.getBlocks()) {
            if (player != null) {
                if (deny(player, state.getLocation(), TrustType.BUILD, true)) {
                    event.setCancelled(true);
                    return;
                }
            } else if (service.isProtected(state.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!service.config().protectContainers() || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        Location location = event.getInventory().getLocation();
        if (location != null && deny(player, location, TrustType.CONTAINER, true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (!service.config().protectHoppers()) {
            return;
        }
        Location source = inventoryLocation(event.getSource());
        Location destination = inventoryLocation(event.getDestination());
        if (source != null && destination != null && service.crossesClaimBoundary(source, destination)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        if (!service.config().protectHoppers()) {
            return;
        }
        Location inventory = inventoryLocation(event.getInventory());
        Location item = event.getItem().getLocation();
        if (inventory != null && service.crossesClaimBoundary(inventory, item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (service.config().protectEntities() && event.getPlayer() != null
                && deny(event.getPlayer(), event.getEntity().getLocation(), TrustType.BUILD, true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (!service.config().protectEntities()) {
            return;
        }
        Player player = responsiblePlayer(event.getRemover());
        if (player != null) {
            if (deny(player, event.getEntity().getLocation(), TrustType.BUILD, true)) {
                event.setCancelled(true);
            }
            return;
        }
        if (service.isProtected(event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (service.config().protectEntities() && event.getPlayer() != null
                && deny(event.getPlayer(), event.getEntity().getLocation(), TrustType.BUILD, true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (deny(player, event.getItem().getLocation(), TrustType.CONTAINER, false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (deny(event.getPlayer(), event.getItemDrop().getLocation(), TrustType.ACCESS, false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!service.config().protectEntities() || !isProtectedEntity(event.getEntity())) {
            return;
        }
        Player player = responsiblePlayer(event.getDamager());
        if (player != null) {
            if (deny(player, event.getEntity().getLocation(), entityTrustType(event.getEntity()), true)) {
                event.setCancelled(true);
            }
            return;
        }
        if ((event.getEntity() instanceof Hanging || event.getEntity() instanceof ArmorStand
                || event.getEntity() instanceof Vehicle)
                && service.isProtected(event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityEnvironmentalDamage(EntityDamageEvent event) {
        if (!service.config().protectEntities()
                || !service.config().protectExplosions()
                || !isProtectedEntity(event.getEntity())) {
            return;
        }
        if ((event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION)
                && service.isProtected(event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityTransform(EntityTransformEvent event) {
        if (service.config().protectEntities()
                && isProtectedEntity(event.getEntity())
                && service.isProtected(event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!service.config().protectEntities()) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND || !isProtectedEntity(event.getRightClicked())) {
            return;
        }
        if (deny(event.getPlayer(), event.getRightClicked().getLocation(), entityTrustType(event.getRightClicked()), true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (service.config().protectEntities() && deny(event.getPlayer(), event.getRightClicked().getLocation(), TrustType.BUILD, true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        if (service.config().protectEntities() && deny(event.getPlayer(), event.getEntity().getLocation(), TrustType.CONTAINER, true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        if (service.config().protectEntities() && deny(event.getPlayer(), event.getEntity().getLocation(), TrustType.CONTAINER, true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUnleash(PlayerUnleashEntityEvent event) {
        if (service.config().protectEntities() && deny(event.getPlayer(), event.getEntity().getLocation(), TrustType.CONTAINER, true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (!service.config().protectEntities()) {
            return;
        }
        Player player = responsiblePlayer(event.getAttacker());
        if (player != null && deny(player, event.getVehicle().getLocation(), TrustType.BUILD, true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (!service.config().protectEntities()) {
            return;
        }
        Player player = responsiblePlayer(event.getAttacker());
        if (player != null && deny(player, event.getVehicle().getLocation(), TrustType.BUILD, true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!service.config().protectEntities() || !(event.getEntered() instanceof Player player)) {
            return;
        }
        if (deny(player, event.getVehicle().getLocation(), TrustType.CONTAINER, true)) {
            event.setCancelled(true);
        }
    }

    private boolean deny(Player player, Location location, TrustType type, boolean message) {
        Claim claim = service.claimAt(location).orElse(null);
        if (claim == null || service.canUse(player, claim, type)) {
            return false;
        }
        if (message) {
            service.sendProtected(player, claim);
        }
        return true;
    }

    private TrustType requiredTrustForInteract(Block block) {
        if (block.getState() instanceof org.bukkit.inventory.InventoryHolder) {
            return TrustType.CONTAINER;
        }
        Material material = block.getType();
        String name = material.name();
        if (name.endsWith("_DOOR")
                || name.endsWith("_TRAPDOOR")
                || name.endsWith("_FENCE_GATE")
                || name.endsWith("_BUTTON")
                || name.endsWith("_PRESSURE_PLATE")
                || name.endsWith("_SIGN")
                || name.endsWith("_HANGING_SIGN")
                || material == Material.LEVER
                || material == Material.REPEATER
                || material == Material.COMPARATOR
                || material == Material.BELL
                || material == Material.NOTE_BLOCK) {
            return TrustType.ACCESS;
        }
        if (material == Material.CRAFTING_TABLE
                || material == Material.ENCHANTING_TABLE
                || material == Material.ANVIL
                || material == Material.CHIPPED_ANVIL
                || material == Material.DAMAGED_ANVIL
                || material == Material.CARTOGRAPHY_TABLE
                || material == Material.FLETCHING_TABLE
                || material == Material.GRINDSTONE
                || material == Material.LOOM
                || material == Material.SMITHING_TABLE
                || material == Material.STONECUTTER
                || material == Material.BEACON
                || material == Material.JUKEBOX) {
            return TrustType.CONTAINER;
        }
        if (material == Material.CAKE
                || material == Material.COMPOSTER
                || material == Material.RESPAWN_ANCHOR
                || material == Material.DRAGON_EGG
                || name.endsWith("_BED")
                || name.endsWith("_CANDLE")
                || name.endsWith("_CANDLE_CAKE")
                || name.endsWith("CAMPFIRE")) {
            return TrustType.BUILD;
        }
        return null;
    }

    private TrustType entityTrustType(Entity entity) {
        if (entity instanceof Animals || entity instanceof Villager || entity instanceof Tameable || entity instanceof Vehicle) {
            return TrustType.CONTAINER;
        }
        return TrustType.BUILD;
    }

    private boolean crosses(Block first, Block second) {
        return service.crossesClaimBoundary(first.getLocation(), second.getLocation());
    }

    private Location inventoryLocation(Inventory inventory) {
        return inventory == null ? null : inventory.getLocation();
    }

    private Player responsiblePlayer(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private boolean isProtectedEntity(Entity entity) {
        return entity instanceof Hanging
                || entity instanceof ArmorStand
                || entity instanceof Animals
                || entity instanceof Villager
                || entity instanceof Tameable
                || entity instanceof Vehicle;
    }
}
