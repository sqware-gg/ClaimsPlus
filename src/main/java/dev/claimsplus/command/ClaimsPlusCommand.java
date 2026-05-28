package dev.claimsplus.command;

import dev.claimsplus.claim.Claim;
import dev.claimsplus.claim.ClaimService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClaimsPlusCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final ClaimService service;

    public ClaimsPlusCommand(JavaPlugin plugin, ClaimService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            listClaims(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help", "?" -> service.send(sender, "usage-claims", Map.of());
            case "list" -> listClaims(sender);
            case "info" -> claimInfo(sender);
            case "show", "border" -> claimShow(sender);
            case "status", "stats" -> {
                if (!sender.hasPermission("claimsplus.admin")) {
                    service.send(sender, "no-permission", Map.of());
                    return true;
                }
                service.send(sender, "status", Map.of(
                        "version", plugin.getPluginMeta().getVersion(),
                        "claims", Integer.toString(service.claimCount()),
                        "size", Integer.toString(service.config().claimSize())
                ));
            }
            case "deletehere", "removehere" -> deleteHere(sender);
            case "delete", "remove" -> deleteById(sender, args);
            case "reload" -> {
                if (!sender.hasPermission("claimsplus.reload")) {
                    service.send(sender, "no-permission", Map.of());
                    return true;
                }
                service.reload();
                service.send(sender, "reloaded", Map.of());
            }
            case "save" -> {
                if (!sender.hasPermission("claimsplus.save")) {
                    service.send(sender, "no-permission", Map.of());
                    return true;
                }
                service.save();
                service.send(sender, "saved", Map.of());
            }
            default -> service.send(sender, "usage-claims", Map.of());
        }
        return true;
    }

    private void listClaims(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            service.send(sender, "usage-claims", Map.of());
            return;
        }
        if (!player.hasPermission("claimsplus.info")) {
            service.send(player, "no-permission", Map.of());
            return;
        }
        List<Claim> claims = service.claimsForOwner(player.getUniqueId());
        if (claims.isEmpty()) {
            service.send(player, "claims-empty", Map.of());
            return;
        }
        service.send(player, "claims-header", Map.of("count", Integer.toString(claims.size())));
        for (Claim claim : claims) {
            service.send(player, "claims-line", service.placeholders(claim));
        }
    }

    private void claimInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            service.send(sender, "players-only", Map.of());
            return;
        }
        if (!player.hasPermission("claimsplus.info")) {
            service.send(player, "no-permission", Map.of());
            return;
        }
        Claim claim = service.claimAt(player.getLocation()).orElse(null);
        if (claim == null) {
            service.send(player, "info-unclaimed", Map.of());
            return;
        }
        service.send(player, "info-claimed", service.placeholders(claim));
    }

    private void claimShow(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            service.send(sender, "players-only", Map.of());
            return;
        }
        service.send(player, service.showClaimBorder(player));
    }

    private void deleteHere(CommandSender sender) {
        if (!sender.hasPermission("claimsplus.delete")) {
            service.send(sender, "no-permission", Map.of());
            return;
        }
        if (!(sender instanceof Player player)) {
            service.send(sender, "players-only", Map.of());
            return;
        }
        service.claimAt(player.getLocation())
                .ifPresentOrElse(
                        claim -> service.send(sender, service.removeClaimAdmin(claim)),
                        () -> service.send(sender, "not-in-claim", Map.of())
                );
    }

    private void deleteById(CommandSender sender, String[] args) {
        if (!sender.hasPermission("claimsplus.delete")) {
            service.send(sender, "no-permission", Map.of());
            return;
        }
        if (args.length != 2) {
            service.send(sender, "usage-claims", Map.of());
            return;
        }
        service.claimById(args[1])
                .ifPresentOrElse(
                        claim -> service.send(sender, service.removeClaimAdmin(claim)),
                        () -> service.send(sender, "claim-not-found", Map.of("id", args[1]))
                );
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("list", "info", "show", "status", "deletehere", "delete", "reload", "save", "help").stream()
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
