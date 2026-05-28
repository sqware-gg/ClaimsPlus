package dev.claimsplus.command;

import dev.claimsplus.claim.ClaimResult;
import dev.claimsplus.claim.ClaimService;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ClaimShowCommand implements CommandExecutor {
    private final ClaimService service;

    public ClaimShowCommand(ClaimService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            service.send(sender, "players-only", Map.of());
            return true;
        }
        if (args.length != 0) {
            service.send(player, "usage-claimshow", Map.of());
            return true;
        }
        ClaimResult result = service.showClaimBorder(player);
        service.send(player, result);
        return true;
    }
}
