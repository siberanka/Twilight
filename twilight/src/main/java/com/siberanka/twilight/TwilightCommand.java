package com.siberanka.twilight;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

final class TwilightCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("status", "scan", "convert", "build", "deploy", "rollback", "reload");
    private final TwilightPlugin plugin;

    TwilightCommand(TwilightPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] arguments) {
        String action = arguments.length == 0 ? "status" : arguments[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "status" -> plugin.sendStatus(sender);
            case "scan" -> plugin.scan(sender, false);
            case "convert", "build" -> plugin.scan(sender, true);
            case "deploy" -> plugin.deploy(sender);
            case "rollback" -> {
                int index = 1;
                if (arguments.length > 1) {
                    try { index = Integer.parseInt(arguments[1]); }
                    catch (NumberFormatException ignored) { plugin.send(sender, "Rollback index must be a number."); return true; }
                }
                plugin.rollback(sender, index);
            }
            case "reload" -> plugin.reloadTwilight(sender);
            default -> plugin.send(sender, "Usage: /twilight <status|scan|convert|deploy|rollback|reload>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] arguments) {
        if (arguments.length != 1) return List.of();
        String prefix = arguments[0].toLowerCase(Locale.ROOT);
        return SUBCOMMANDS.stream().filter(value -> value.startsWith(prefix)).toList();
    }
}
