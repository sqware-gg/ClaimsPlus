package dev.claimsplus.command;

import dev.claimsplus.claim.ClaimResult;
import dev.claimsplus.claim.ClaimService;
import dev.claimsplus.claim.TrustType;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class TrustCommand implements CommandExecutor, TabCompleter {
    private final ClaimService service;
    private final TrustType type;

    public TrustCommand(ClaimService service, TrustType type) {
        this.service = service;
        this.type = type;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            service.send(sender, "players-only", java.util.Map.of());
            return true;
        }
        if (args.length != 1) {
            service.send(player, "trust-help", java.util.Map.of());
            return true;
        }
        ClaimResult result = type == null ? service.untrust(player, args[0]) : service.trust(player, args[0], type);
        service.send(player, result);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        Stream<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName);
        if (type == null || type != TrustType.PERMISSION) {
            names = Stream.concat(names, Stream.of("public"));
        }
        return names
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
