package com.example.damage;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class DamageMessagePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(
                new DamageListener(),
                this
        );

        HealthCommands healthCommands = new HealthCommands();

        registerCommand("healhp", healthCommands);
        registerCommand("healmax", healthCommands);
        registerCommand("damagemax", healthCommands);
        registerCommand("healmissing", healthCommands);
        registerCommand("damagemissing", healthCommands);

        getLogger().info("DamageMessagePlugin 已啟用");
    }

    private void registerCommand(String name, HealthCommands executor) {
        PluginCommand command = getCommand(name);

        if (command == null) {
            getLogger().severe("plugin.yml 未宣告指令：" + name);
            return;
        }

        command.setExecutor(executor);
    }
}