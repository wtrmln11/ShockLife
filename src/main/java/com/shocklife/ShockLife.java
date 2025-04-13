package com.shocklife;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.NamespacedKey;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;

public class ShockLife extends JavaPlugin implements Listener {

    private final HashMap<UUID, Integer> playerShock = new HashMap<>();
    private final HashMap<UUID, Long> dashCooldowns = new HashMap<>();
    private final HashMap<UUID, Boolean> wasOnGround = new HashMap<>();
    private ItemStack shockCatalystItem;

    @Override
    public void onEnable() {
        // Initialize custom item
        shockCatalystItem = createShockCatalyst();

        // Register events and commands
        getServer().getPluginManager().registerEvents(this, this);

        getCommand("shock").setExecutor(new CommandExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                    return true;
                }
                Player player = (Player) sender;
                int shock = playerShock.getOrDefault(player.getUniqueId(), 0);
                player.sendMessage(ChatColor.GOLD + "⚡ Shock Level: " + ChatColor.AQUA + shock + "/10");
                if (shock >= 10) {
                    player.sendMessage(ChatColor.GREEN + "Dash: Sneak + Jump while airborne");
                }
                return true;
            }
        });

        // Register crafting recipe
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(this, "shock_catalyst"), shockCatalystItem);
        recipe.shape("ENE", "HSH", "ENE");
        recipe.setIngredient('E', Material.END_CRYSTAL);
        recipe.setIngredient('N', Material.NETHERITE_INGOT);
        recipe.setIngredient('H', Material.HEART_OF_THE_SEA);
        recipe.setIngredient('S', Material.NETHER_STAR);
        getServer().addRecipe(recipe);

        getLogger().info("ShockLife activated!");
    }

    private ItemStack createShockCatalyst() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Shock Catalyst");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Infuses the wielder with electrifying energy",
                "",
                ChatColor.YELLOW + "Right-click to gain +3 Shock"
        ));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        removeShock(victim);
        if (killer != null) addShock(killer);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Sneak+Jump dash detection
        if (player.isSneaking() && !player.isOnGround()) {
            attemptDash(player);
        }

        // Update ground state for fall damage prevention
        wasOnGround.put(player.getUniqueId(), player.isOnGround());
    }

    @EventHandler
    public void onItemUse(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR &&
                event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item != null && item.isSimilar(shockCatalystItem)) {
            Player player = event.getPlayer();
            for (int i = 0; i < 3; i++) addShock(player);
            item.setAmount(item.getAmount() - 1);
            player.sendMessage(ChatColor.LIGHT_PURPLE + "You feel a surge of energy!");
        }
    }

    private void addShock(Player player) {
        int current = playerShock.getOrDefault(player.getUniqueId(), 0);
        if (current >= 10) return;
        playerShock.put(player.getUniqueId(), current + 1);
        updateEffects(player);
    }

    private void removeShock(Player player) {
        int current = playerShock.getOrDefault(player.getUniqueId(), 0);
        playerShock.put(player.getUniqueId(), Math.max(current - 2, 0));
        updateEffects(player);
    }

    private void updateEffects(Player player) {
        int shock = playerShock.getOrDefault(player.getUniqueId(), 0);
        player.removePotionEffect(PotionEffectType.SPEED);

        if (shock >= 3 && shock <= 5) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SPEED,
                    PotionEffect.INFINITE_DURATION,
                    0,
                    false,
                    false,
                    true
            ));
        } else if (shock >= 6 && shock <= 8) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SPEED,
                    PotionEffect.INFINITE_DURATION,
                    0,
                    false,
                    false,
                    true
            ));
            spawnParticles(player, Particle.ELECTRIC_SPARK, 5);
        } else if (shock >= 9) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SPEED,
                    PotionEffect.INFINITE_DURATION,
                    1,
                    false,
                    false,
                    true
            ));
            spawnParticles(player, Particle.ELECTRIC_SPARK, 10);
        }
    }

    private void attemptDash(Player player) {
        // Only at max shock level
        if (playerShock.getOrDefault(player.getUniqueId(), 0) < 10) return;

        // Cooldown check (5 seconds)
        long now = System.currentTimeMillis();
        if (dashCooldowns.getOrDefault(player.getUniqueId(), 0L) > now) {
            player.spigot().sendMessage(
                    ChatMessageType.ACTION_BAR,
                    new TextComponent(ChatColor.RED + "Dash on cooldown (" +
                            ((dashCooldowns.get(player.getUniqueId()) - now)/1000) + "s)")
            );
            return;
        }

        // Execute dash
        player.setVelocity(player.getLocation().getDirection().multiply(2.0).setY(0.5));
        dashCooldowns.put(player.getUniqueId(), now + 5000); // 5s cooldown

        // Effects
        player.getWorld().playSound(
                player.getLocation(),
                Sound.ENTITY_LIGHTNING_BOLT_THUNDER,
                1.0f,
                1.0f
        );
        spawnParticles(player, Particle.ELECTRIC_SPARK, 20);
    }

    private void spawnParticles(Player player, Particle particle, int count) {
        player.getWorld().spawnParticle(
                particle,
                player.getLocation().add(0, 1, 0),
                count,
                0.5, 0.5, 0.5,
                0.1
        );
    }
}