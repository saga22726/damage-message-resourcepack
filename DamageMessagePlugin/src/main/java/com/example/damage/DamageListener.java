package com.example.damage;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public final class DamageListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }

        Entity damager = event.getDamager();

        if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }

        if (!(damager instanceof Player source)) {
            return;
        }

        double damage = event.getFinalDamage();
        double remaining =
                Math.max(0.0, target.getHealth() - damage);

        target.sendMessage(Component.text(
                "【" + source.getName() + "】對你造成了"
                        + String.format("%.2f", damage)
                        + "點傷害，剩餘生命"
                        + String.format("%.2f", remaining)
        ));

        source.sendMessage(Component.text(
                "你對【" + target.getName() + "】造成了"
                        + String.format("%.2f", damage)
                        + "點傷害，剩餘生命"
                        + String.format("%.2f", remaining)
        ));
    }
}