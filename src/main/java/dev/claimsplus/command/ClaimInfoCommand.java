package dev.claimsplus.command;

import dev.claimsplus.claim.Claim;
import dev.claimsplus.claim.ClaimService;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ClaimInfoCommand implements CommandExecutor {
    private final ClaimService service;

    public ClaimInfoCommand(ClaimService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            service.send(sender, "players-only", Map.of());
            return true;
        }
        if (!player.hasPermission("claimsplus.info")) {
            service.send(player, "no-permission", Map.of());
            return true;
        }
        Claim claim = service.claimAt(player.getLocation()).orElse(null);
        if (claim == null) {
            service.send(player, "info-unclaimed", Map.of());
            return true;
        }
        service.send(player, "info-claimed", service.placeholders(claim));
        return true;
    }
}
