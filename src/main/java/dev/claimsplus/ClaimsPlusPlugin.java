package dev.claimsplus;

import dev.claimsplus.claim.ClaimDataStore;
import dev.claimsplus.claim.ClaimService;
import dev.claimsplus.claim.TrustType;
import dev.claimsplus.command.ClaimInfoCommand;
import dev.claimsplus.command.ClaimShowCommand;
import dev.claimsplus.command.ClaimsPlusCommand;
import dev.claimsplus.command.TrustCommand;
import dev.claimsplus.command.TrustListCommand;
import dev.claimsplus.config.ClaimsPlusConfig;
import dev.claimsplus.config.ConfigReferenceWriter;
import dev.claimsplus.listener.ProtectionListener;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClaimsPlusPlugin extends JavaPlugin {
    private static final int BSTATS_PLUGIN_ID = 31624;

    private ClaimsPlusConfig claimsConfig;
    private ClaimDataStore claimDataStore;
    private ClaimService claimService;

    @Override
    public void onEnable() {
        new Metrics(this, BSTATS_PLUGIN_ID);
        ConfigReferenceWriter.saveDefaultAndReferenceIfNeeded(this);

        claimsConfig = new ClaimsPlusConfig(this);
        claimDataStore = new ClaimDataStore(this);
        claimService = new ClaimService(this, claimsConfig, claimDataStore);

        registerCommands();
        getServer().getPluginManager().registerEvents(new ProtectionListener(claimService), this);
        claimService.start();

        getLogger().info("Loaded " + claimService.claimCount() + " claims.");
    }

    @Override
    public void onDisable() {
        if (claimService != null) {
            claimService.stop();
        }
    }

    private void registerCommands() {
        register("trust", new TrustCommand(claimService, TrustType.BUILD));
        register("accesstrust", new TrustCommand(claimService, TrustType.ACCESS));
        register("containertrust", new TrustCommand(claimService, TrustType.CONTAINER));
        register("permissiontrust", new TrustCommand(claimService, TrustType.PERMISSION));
        register("untrust", new TrustCommand(claimService, null));
        register("trustlist", new TrustListCommand(claimService));
        register("claiminfo", new ClaimInfoCommand(claimService));
        register("claimshow", new ClaimShowCommand(claimService));
        register("claims", new ClaimsPlusCommand(this, claimService));
    }

    private void register(String name, Object commandHandler) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            return;
        }
        if (commandHandler instanceof org.bukkit.command.CommandExecutor executor) {
            command.setExecutor(executor);
        }
        if (commandHandler instanceof org.bukkit.command.TabCompleter completer) {
            command.setTabCompleter(completer);
        }
    }
}
