package com.example.damage;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HealthCommands implements CommandExecutor {

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        String action = command.getName().toLowerCase(Locale.ROOT);

        boolean damageCommand =
                action.equals("damagemax")
                        || action.equals("damagemissing");

        if (!damageCommand && args.length != 2) {
            sender.sendMessage("用法：" + command.getUsage());
            return true;
        }

        if (damageCommand) {
            DamageArguments damageArgs =
                    parseDamageArguments(sender, action, args);

            if (damageArgs == null) {
                return true;
            }

            execute(
                    sender,
                    action,
                    damageArgs.selector(),
                    damageArgs.value(),
                    damageArgs.damageType(),
                    damageArgs.damageSourceSelector()
            );
            return true;
        }

        execute(
                sender,
                action,
                args[0],
                parseValue(sender, args[1]),
                null,
                null
        );

        return true;
    }

    private void execute(
            CommandSender sender,
            String action,
            String targetSelector,
            Double value,
            DamageType damageType,
            String damageSourceSelector
    ) {
        if (value == null) {
            return;
        }

        if (!action.equals("healhp") && value > 100.0) {
            sender.sendMessage(
                    "百分比必須大於 0 且不超過 100。"
            );
            return;
        }

        final List<Entity> selectedTargets;

        try {
            selectedTargets =
                    Bukkit.getServer().selectEntities(
                            sender,
                            targetSelector
                    );
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(
                    "目標選擇器無效：" + targetSelector
            );
            return;
        }

        List<LivingEntity> targets = new ArrayList<>();

        for (Entity entity : selectedTargets) {
            if (entity instanceof LivingEntity living
                    && !living.isDead()) {
                targets.add(living);
            }
        }

        if (targets.isEmpty()) {
            sender.sendMessage(
                    "沒有找到符合條件的存活實體。"
            );
            return;
        }

        Entity damageSource = null;

        if (damageSourceSelector != null) {
            final List<Entity> selectedSources;

            try {
                selectedSources =
                        Bukkit.getServer().selectEntities(
                                sender,
                                damageSourceSelector
                        );
            } catch (IllegalArgumentException exception) {
                sender.sendMessage(
                        "傷害源選擇器無效：" + damageSourceSelector
                );
                return;
            }

            if (selectedSources.size() != 1) {
                sender.sendMessage(
                        "傷害源選擇器必須剛好選中一個實體；"
                                + "目前選中 "
                                + selectedSources.size()
                                + " 個。"
                );
                return;
            }

            damageSource = selectedSources.get(0);
        }

        int processed = 0;

        for (LivingEntity target : targets) {
            var maxHealthAttribute =
                    target.getAttribute(Attribute.MAX_HEALTH);

            if (maxHealthAttribute == null || target.isDead()) {
                continue;
            }

            double maxHealth = maxHealthAttribute.getValue();
            double currentHealth = target.getHealth();
            double missingHealth =
                    Math.max(0.0, maxHealth - currentHealth);

            switch (action) {
                case "healhp" -> {
                    double newHealth =
                            Math.min(maxHealth, currentHealth + value);

                    if (newHealth > currentHealth) {
                        target.setHealth(newHealth);
                    }
                }

                case "healmax" -> {
                    double amount = maxHealth * value / 100.0;
                    double newHealth =
                            Math.min(maxHealth, currentHealth + amount);

                    if (newHealth > currentHealth) {
                        target.setHealth(newHealth);
                    }
                }

                case "healmissing" -> {
                    double amount = missingHealth * value / 100.0;
                    double newHealth =
                            Math.min(maxHealth, currentHealth + amount);

                    if (newHealth > currentHealth) {
                        target.setHealth(newHealth);
                    }
                }

                case "damagemax" -> {
                    double amount = maxHealth * value / 100.0;
                    applyDamage(
                            target,
                            amount,
                            damageType,
                            damageSource
                    );
                }

                case "damagemissing" -> {
                    double amount = missingHealth * value / 100.0;
                    applyDamage(
                            target,
                            amount,
                            damageType,
                            damageSource
                    );
                }

                default -> {
                    sender.sendMessage("未知的指令：" + action);
                    return;
                }
            }

            processed++;
        }

        sender.sendMessage("已處理 " + processed + " 個目標。");
    }

    private DamageArguments parseDamageArguments(
            CommandSender sender,
            String action,
            String[] args
    ) {
        if (args.length < 3) {
            sender.sendMessage(
                    "用法：/" + action
                            + " <目標> <百分比> "
                            + "<physical|magic> [by <傷害源>] "
                            + "[damagecause]"
            );
            return null;
        }

        Double value = parseValue(sender, args[1]);

        if (value == null) {
            return null;
        }

        DamageType damageType =
                parseDamageType(sender, args[2]);

        if (damageType == null) {
            return null;
        }

        String damageSourceSelector = null;

        if (args.length >= 5
                && args[3].equalsIgnoreCase("by")) {
            damageSourceSelector = args[4];
        } else if (args.length == 4) {
            sender.sendMessage(
                    "傷害源格式錯誤，請使用："
                            + "physical|magic by <傷害源>"
            );
            return null;
        } else if (args.length > 5) {
            sender.sendMessage(
                    "參數過多。用法：/" + action
                            + " <目標> <百分比> "
                            + "<physical|magic> [by <傷害源>] "
                            + "[damagecause]"
            );
            return null;
        }

        return new DamageArguments(
                args[0],
                value,
                damageType,
                damageSourceSelector
        );
    }

    private DamageType parseDamageType(
            CommandSender sender,
            String type
    ) {
        if (type.equalsIgnoreCase("physical")) {
            return DamageType.GENERIC_KILL;
        }

        if (type.equalsIgnoreCase("magic")) {
            return DamageType.MAGIC;
        }

        sender.sendMessage(
                "傷害類型無效。請使用 physical 或 magic。"
        );
        return null;
    }

    private Double parseValue(
            CommandSender sender,
            String text
    ) {
        final double value;

        try {
            value = Double.parseDouble(text);
        } catch (NumberFormatException exception) {
            sender.sendMessage("數值格式無效，請輸入數字。");
            return null;
        }

        if (!Double.isFinite(value) || value <= 0.0) {
            sender.sendMessage("數值必須大於 0。");
            return null;
        }

        return value;
    }

    private void applyDamage(
            LivingEntity target,
            double amount,
            DamageType damageType,
            Entity damageSource
    ) {
        if (target.isDead() || amount <= 0.0) {
            return;
        }

        if (damageType == DamageType.MAGIC) {
            DamageSource.Builder builder =
                    DamageSource.builder(DamageType.MAGIC);

            if (damageSource != null) {
                builder.withCausingEntity(damageSource);
            }

            target.damage(
                    amount,
                    builder.build()
            );
            return;
        }

        if (damageSource != null) {
            target.damage(amount, damageSource);
        } else {
            target.damage(amount);
        }
    }

    private record DamageArguments(
            String selector,
            double value,
            DamageType damageType,
            String damageSourceSelector
    ) {}
}