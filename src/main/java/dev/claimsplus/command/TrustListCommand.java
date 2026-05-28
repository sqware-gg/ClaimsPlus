package dev.claimsplus.command;

import dev.claimsplus.claim.Claim;
import dev.claimsplus.claim.ClaimService;
import dev.claimsplus.claim.TrustType;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class TrustListCommand implements CommandExecutor {
    private final ClaimService service;

    public TrustListCommand(ClaimService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            service.send(sender, "players-only", Map.of());
            return true;
        }
        if (!player.hasPermission("claimsplus.trust")) {
            service.send(player, "no-permission", Map.of());
            return true;
        }
        Claim claim = service.claimAt(player.getLocation()).orElse(null);
        if (claim == null) {
            service.send(player, "not-in-claim", Map.of());
            return true;
        }
        if (!claim.isOwner(player.getUniqueId())
                && !player.hasPermission("claimsplus.bypass")
                && !claim.hasTrust(player, TrustType.PERMISSION)) {
            service.send(player, "not-claim-owner", service.placeholders(claim));
            return true;
        }
        String players = service.trustedNames(claim);
        if (players.isBlank()) {
            service.send(player, "trust-list-empty", Map.of());
            return true;
        }
        service.send(player, "trust-list", service.placeholders(claim));
        return true;
    }
}
