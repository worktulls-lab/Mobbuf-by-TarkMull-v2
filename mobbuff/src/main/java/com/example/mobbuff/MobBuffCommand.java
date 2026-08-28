package com.example.mobbuff;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class MobBuffCommand implements CommandExecutor {

    private final MobBuffPlugin plugin;

    public MobBuffCommand(MobBuffPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "on" -> {
                if (plugin.isBuffEnabled()) {
                    sender.sendMessage(ChatColor.GRAY + "Баф мобов уже включён.");
                } else {
                    plugin.setBuffEnabled(true);
                    sender.sendMessage(ChatColor.GREEN + "Баф мобов включён (хардкор-режим).");
                }
            }
            case "off" -> {
                if (!plugin.isBuffEnabled()) {
                    sender.sendMessage(ChatColor.GRAY + "Баф мобов уже выключен.");
                } else {
                    plugin.setBuffEnabled(false);
                    sender.sendMessage(ChatColor.RED + "Баф мобов выключен, здоровье возвращено к обычному.");
                }
            }
            case "status" -> {
                if (!plugin.isBuffEnabled()) {
                    sender.sendMessage(ChatColor.AQUA + "Баф мобов сейчас: " + ChatColor.RED + "ВЫКЛЮЧЕН");
                } else {
                    double dmgMult = plugin.getCurrentDamageMultiplier();
                    double hpMult = plugin.getCurrentHealthMultiplier();
                    sender.sendMessage(ChatColor.AQUA + "Баф мобов сейчас: " + ChatColor.GREEN + "ВКЛЮЧЁН");
                    sender.sendMessage(ChatColor.GRAY + String.format(
                            "Урон (обычные): x%.2f (потолок x2.5) | Здоровье: x%.2f (потолок x3.5)",
                            dmgMult, hpMult));
                    sender.sendMessage(ChatColor.GRAY + "Зомби: урон потолок x2.0, здоровье потолок x4.0 (отдельно)");
                    sender.sendMessage(ChatColor.GRAY + "Дракон: x3.3 фикс. | Иссушитель: x2.0 фикс.");
                    sender.sendMessage(ChatColor.GRAY + "Убийств в счётчике (любые мобы): " + plugin.getKillCount());
                }
            }
            case "maxwell" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Только для игрока.");
                    return true;
                }
                plugin.spawnMaxwellManually(player);
                sender.sendMessage(ChatColor.LIGHT_PURPLE + "Максвелл где-то рядом с тобой...");
            }
            case "superzombie" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Только для игрока.");
                    return true;
                }
                plugin.spawnSuperZombieManually(player);
                sender.sendMessage(ChatColor.DARK_RED + "Зомби-босс призван рядом с тобой.");
            }
            case "chickenrain" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Только для игрока.");
                    return true;
                }
                plugin.triggerChickenRain(player);
                sender.sendMessage(ChatColor.YELLOW + "Курицы приближаются...");
            }
            case "weather" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "Использование: /mobbuff weather <fog>");
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Только для игрока.");
                    return true;
                }
                plugin.triggerWeatherAnomaly(args[1], player.getWorld());
                sender.sendMessage(ChatColor.GRAY + "Погодная аномалия (" + args[1] + ") запущена, если ещё не была активна.");
            }
            default -> sendUsage(sender);
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Использование: /mobbuff <on|off|status|maxwell|superzombie|chickenrain|weather fog>");
    }
}
