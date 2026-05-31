package dev.claimsplus.config;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClaimsPlusConfig {
    private final JavaPlugin plugin;
    private Material claimBlock;
    private int claimSize;
    private int maxClaimsPerPlayer;
    private boolean expandNearbyOwnedClaims;
    private Set<String> disabledWorlds;
    private int saveIntervalSeconds;
    private boolean saveOnChange;
    private boolean allowUnknownTrust;
    private boolean protectBlocks;
    private boolean protectContainers;
    private boolean protectEntities;
    private boolean protectExplosions;
    private boolean protectPistons;
    private boolean protectFluids;
    private boolean protectHoppers;
    private boolean protectFire;
    private boolean visualBorderEnabled;
    private Material visualBorderMaterial;
    private int visualBorderDurationSeconds;
    private int visualBorderHeightOffset;
    private int visualBorderLayers;
    private boolean claimPreviewEnabled;
    private int claimPreviewRangeBlocks;
    private int claimPreviewRefreshTicks;
    private int claimPreviewDurationTicks;
    private boolean feedbackSoundsEnabled;
    private float feedbackSoundVolume;
    private float feedbackSoundPitch;
    private int feedbackProtectedCooldownMillis;
    private Sound feedbackSuccessSound;
    private Sound feedbackErrorSound;
    private Sound feedbackProtectedSound;
    private Sound feedbackClaimCreatedSound;
    private Sound feedbackClaimRemovedSound;
    private Sound feedbackTrustSound;
    private Sound feedbackBorderSound;

    public ClaimsPlusConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        claimBlock = Material.matchMaterial(plugin.getConfig().getString("claims.claim-block", "EMERALD_BLOCK"));
        if (claimBlock == null || !claimBlock.isBlock()) {
            claimBlock = Material.EMERALD_BLOCK;
        }
        claimSize = Math.max(1, plugin.getConfig().getInt("claims.size", 32));
        maxClaimsPerPlayer = plugin.getConfig().getInt("claims.max-claims-per-player", -1);
        expandNearbyOwnedClaims = plugin.getConfig().getBoolean("claims.expand-nearby-owned-claims", true);
        disabledWorlds = new HashSet<>();
        for (String world : plugin.getConfig().getStringList("claims.disabled-worlds")) {
            if (world != null && !world.isBlank()) {
                disabledWorlds.add(world.toLowerCase(Locale.ROOT));
            }
        }
        saveIntervalSeconds = Math.max(0, plugin.getConfig().getInt("storage.save-interval-seconds", 300));
        saveOnChange = plugin.getConfig().getBoolean("storage.save-on-change", true);
        allowUnknownTrust = plugin.getConfig().getBoolean("trust.allow-unknown-players", false);
        protectBlocks = plugin.getConfig().getBoolean("protection.blocks", true);
        protectContainers = plugin.getConfig().getBoolean("protection.containers", true);
        protectEntities = plugin.getConfig().getBoolean("protection.entities", true);
        protectExplosions = plugin.getConfig().getBoolean("protection.explosions", true);
        protectPistons = plugin.getConfig().getBoolean("protection.pistons", true);
        protectFluids = plugin.getConfig().getBoolean("protection.fluids", true);
        protectHoppers = plugin.getConfig().getBoolean("protection.hoppers", true);
        protectFire = plugin.getConfig().getBoolean("protection.fire", true);
        visualBorderEnabled = plugin.getConfig().getBoolean("visual-border.enabled", true);
        visualBorderMaterial = Material.matchMaterial(plugin.getConfig().getString("visual-border.material", "GLOWSTONE"));
        if (visualBorderMaterial == null || !visualBorderMaterial.isBlock()) {
            visualBorderMaterial = Material.GLOWSTONE;
        }
        visualBorderDurationSeconds = Math.max(1, plugin.getConfig().getInt("visual-border.duration-seconds", 8));
        visualBorderHeightOffset = plugin.getConfig().getInt("visual-border.height-offset", 1);
        visualBorderLayers = Math.max(1, Math.min(8, plugin.getConfig().getInt("visual-border.layers", 1)));
        claimPreviewEnabled = plugin.getConfig().getBoolean("visual-border.placement-preview.enabled", true);
        claimPreviewRangeBlocks = Math.max(1, plugin.getConfig().getInt("visual-border.placement-preview.range-blocks", 6));
        claimPreviewRefreshTicks = Math.max(1, plugin.getConfig().getInt("visual-border.placement-preview.refresh-ticks", 10));
        claimPreviewDurationTicks = Math.max(1, plugin.getConfig().getInt("visual-border.placement-preview.duration-ticks", 14));
        feedbackSoundsEnabled = plugin.getConfig().getBoolean("feedback.sounds.enabled", true);
        feedbackSoundVolume = (float) Math.max(0.0D, plugin.getConfig().getDouble("feedback.sounds.volume", 0.65D));
        feedbackSoundPitch = (float) Math.max(0.0D, plugin.getConfig().getDouble("feedback.sounds.pitch", 1.0D));
        feedbackProtectedCooldownMillis = Math.max(0, plugin.getConfig().getInt("feedback.protected-cooldown-millis", 1200));
        feedbackSuccessSound = readSound("feedback.sounds.success", "entity.experience_orb.pickup");
        feedbackErrorSound = readSound("feedback.sounds.error", "block.note_block.bass");
        feedbackProtectedSound = readSound("feedback.sounds.protected", "entity.villager.no");
        feedbackClaimCreatedSound = readSound("feedback.sounds.claim-created", "entity.player.levelup");
        feedbackClaimRemovedSound = readSound("feedback.sounds.claim-removed", "block.beacon.deactivate");
        feedbackTrustSound = readSound("feedback.sounds.trust", "entity.experience_orb.pickup");
        feedbackBorderSound = readSound("feedback.sounds.border", "block.amethyst_block.chime");
    }

    public Material claimBlock() {
        return claimBlock;
    }

    public int claimSize() {
        return claimSize;
    }

    public int maxClaimsPerPlayer() {
        return maxClaimsPerPlayer;
    }

    public boolean expandNearbyOwnedClaims() {
        return expandNearbyOwnedClaims;
    }

    public boolean worldEnabled(World world) {
        return world != null && !disabledWorlds.contains(world.getName().toLowerCase(Locale.ROOT));
    }

    public int saveIntervalSeconds() {
        return saveIntervalSeconds;
    }

    public boolean saveOnChange() {
        return saveOnChange;
    }

    public boolean allowUnknownTrust() {
        return allowUnknownTrust;
    }

    public boolean protectBlocks() {
        return protectBlocks;
    }

    public boolean protectContainers() {
        return protectContainers;
    }

    public boolean protectEntities() {
        return protectEntities;
    }

    public boolean protectExplosions() {
        return protectExplosions;
    }

    public boolean protectPistons() {
        return protectPistons;
    }

    public boolean protectFluids() {
        return protectFluids;
    }

    public boolean protectHoppers() {
        return protectHoppers;
    }

    public boolean protectFire() {
        return protectFire;
    }

    public boolean visualBorderEnabled() {
        return visualBorderEnabled;
    }

    public Material visualBorderMaterial() {
        return visualBorderMaterial;
    }

    public int visualBorderDurationSeconds() {
        return visualBorderDurationSeconds;
    }

    public int visualBorderHeightOffset() {
        return visualBorderHeightOffset;
    }

    public int visualBorderLayers() {
        return visualBorderLayers;
    }

    public boolean claimPreviewEnabled() {
        return claimPreviewEnabled;
    }

    public int claimPreviewRangeBlocks() {
        return claimPreviewRangeBlocks;
    }

    public int claimPreviewRefreshTicks() {
        return claimPreviewRefreshTicks;
    }

    public int claimPreviewDurationTicks() {
        return claimPreviewDurationTicks;
    }

    public boolean feedbackSoundsEnabled() {
        return feedbackSoundsEnabled;
    }

    public float feedbackSoundVolume() {
        return feedbackSoundVolume;
    }

    public float feedbackSoundPitch() {
        return feedbackSoundPitch;
    }

    public int feedbackProtectedCooldownMillis() {
        return feedbackProtectedCooldownMillis;
    }

    public Sound feedbackSound(String messageKey) {
        return switch (messageKey) {
            case "protected" -> feedbackProtectedSound;
            case "claim-created", "claim-expanded" -> feedbackClaimCreatedSound;
            case "claim-removed" -> feedbackClaimRemovedSound;
            case "trusted", "untrusted", "trusted-target", "untrusted-target" -> feedbackTrustSound;
            case "claim-border-shown", "claim-expand-hint" -> feedbackBorderSound;
            case "no-permission", "players-only", "player-not-found", "not-in-claim", "not-claim-owner",
                 "trusted-self", "public-permission-trust-disabled", "already-trusted", "not-trusted",
                 "claim-overlaps", "claim-not-found", "claim-limit", "claims-disabled-world",
                 "claim-block-required", "claim-already-owned",
                 "anchor-owner-only", "trust-help" -> feedbackErrorSound;
            default -> messageKey.startsWith("usage-") ? feedbackErrorSound : feedbackSuccessSound;
        };
    }

    public String prefix() {
        return plugin.getConfig().getString("messages.prefix", "");
    }

    public String message(String key) {
        return plugin.getConfig().getString("messages." + key, key);
    }

    private Sound readSound(String path, String fallback) {
        String configured = plugin.getConfig().getString(path, fallback);
        if (configured == null || configured.isBlank() || configured.equalsIgnoreCase("none")) {
            return null;
        }
        Sound sound = soundByName(configured);
        if (sound != null) {
            return sound;
        }
        Sound fallbackSound = soundByName(fallback);
        if (fallbackSound == null) {
            plugin.getLogger().warning("Invalid sound at " + path + ": " + configured);
            return null;
        }
        plugin.getLogger().warning("Invalid sound at " + path + ": " + configured);
        return fallbackSound;
    }

    private Sound soundByName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        Sound sound = Registry.SOUNDS.get(soundKey(normalized));
        if (sound != null) {
            return sound;
        }
        if (!normalized.contains(".") && !normalized.contains(":")) {
            sound = Registry.SOUNDS.get(soundKey(legacySoundKey(normalized)));
        }
        return sound;
    }

    private NamespacedKey soundKey(String value) {
        NamespacedKey key = NamespacedKey.fromString(value);
        return key == null ? NamespacedKey.minecraft(value) : key;
    }

    private String legacySoundKey(String value) {
        if (value.startsWith("block_note_block_")) {
            return "block.note_block." + value.substring("block_note_block_".length()).replace('_', '.');
        }
        if (value.startsWith("block_amethyst_block_")) {
            return "block.amethyst_block." + value.substring("block_amethyst_block_".length()).replace('_', '.');
        }
        if (value.startsWith("entity_experience_orb_")) {
            return "entity.experience_orb." + value.substring("entity_experience_orb_".length()).replace('_', '.');
        }
        return value.replace('_', '.');
    }
}
