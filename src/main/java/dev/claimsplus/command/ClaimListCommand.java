package dev.claimsplus.command;

import dev.claimsplus.claim.Claim;
import dev.claimsplus.claim.ClaimService;
import java.util.List;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ClaimListCommand implements CommandExecutor {
    private final ClaimService service;

    public ClaimListCommand(ClaimService service) {
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
        List<Claim> claims = service.claimGroupsForOwner(player.getUniqueId());
        if (claims.isEmpty()) {
            service.send(player, "claims-empty", Map.of());
            return true;
        }
        service.send(player, "claims-header", Map.of("count", Integer.toString(claims.size())));
        for (Claim claim : claims) {
            service.send(player, "claims-line", service.placeholders(claim));
        }
        return true;
    }
}
