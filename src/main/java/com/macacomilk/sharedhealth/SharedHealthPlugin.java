package com.macacomilk.sharedhealth;


import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class SharedHealthPlugin extends JavaPlugin implements Listener {

    private boolean isSyncing = false;
    private World waitingWorld;
    private final Random random = new Random();
    private static final String WAITING_WORLD_NAME = "sharedhealth_waiting_area";
    private Location waitingAreaCenter;

    @Override
    public void onEnable() {
        try {
            createWaitingWorld();
            getServer().getPluginManager().registerEvents(this, this);
            scheduleWaitingAreaChecks();
            getLogger().info(() -> "SharedHealthPlugin enabled");
        } catch (Exception e) {
            getLogger().severe(() -> "Failed to enable plugin: " + e.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    private void createWaitingWorld() {
        waitingWorld = Bukkit.getWorld(WAITING_WORLD_NAME);
        
        if (waitingWorld == null) {
            WorldCreator creator = new WorldCreator(WAITING_WORLD_NAME);
            creator.environment(World.Environment.NORMAL);
            creator.generateStructures(false);
            creator.type(WorldType.FLAT);
            waitingWorld = creator.createWorld();
            
            if (waitingWorld == null) {
                throw new IllegalStateException("Failed to create waiting world");
            }
        }
        
        waitingWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        waitingWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        waitingWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        
        createWaitingPlatform();
        waitingAreaCenter = new Location(waitingWorld, 0.5, 6, 0.5);
    }

    private void createWaitingPlatform() {
        // Create bedrock floor (15x15)
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                waitingWorld.getBlockAt(x, 4, z).setType(Material.BEDROCK);
                waitingWorld.getBlockAt(x, 5, z).setType(Material.GRASS_BLOCK);
            }
        }
        
        // Create walls
        for (int y = 5; y <= 10; y++) {
            for (int x = -8; x <= 8; x++) {
                waitingWorld.getBlockAt(x, y, -8).setType(Material.BEDROCK);
                waitingWorld.getBlockAt(x, y, 8).setType(Material.BEDROCK);
            }
            for (int z = -7; z <= 7; z++) {
                waitingWorld.getBlockAt(-8, y, z).setType(Material.BEDROCK);
                waitingWorld.getBlockAt(8, y, z).setType(Material.BEDROCK);
            }
        }
        
        // Create glass ceiling
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                waitingWorld.getBlockAt(x, 11, z).setType(Material.GLASS);
            }
        }
    }

    private void scheduleWaitingAreaChecks() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld().equals(waitingWorld)) {
                    player.setGameMode(GameMode.ADVENTURE);
                    player.setInvulnerable(true);
                    
                    if (player.getLocation().getX() < -7 || player.getLocation().getX() > 7 ||
                        player.getLocation().getZ() < -7 || player.getLocation().getZ() > 7) {
                        player.teleport(waitingAreaCenter);
                    }
                }
            }
        }, 0L, 20L);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        
        Player player = event.getPlayer();
        
        Bukkit.getScheduler().runTask(this, () -> {
            player.spigot().respawn();
            player.teleport(waitingAreaCenter);
            player.setGameMode(GameMode.ADVENTURE);
            player.setInvulnerable(true);
            player.sendMessage(Component.text("Generating new world...", NamedTextColor.YELLOW));
        });

        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                generateNewWorld();
            } catch (Exception e) {
                getLogger().severe(() -> "World generation failed: " + e.getMessage());
                player.sendMessage(Component.text("World generation failed!", NamedTextColor.RED));
            }
        }, 20L);
    }

    private void generateNewWorld() {
        long seed = random.nextLong();
        String worldName = "world_" + System.currentTimeMillis();
        
        WorldCreator creator = new WorldCreator(worldName);
        creator.seed(seed);
        creator.environment(World.Environment.NORMAL);
        World newWorld = Bukkit.createWorld(creator);
        
        if (newWorld == null) {
            throw new IllegalStateException("Failed to create new world");
        }
        
        Location spawn = newWorld.getSpawnLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            resetPlayer(player);
            player.teleport(spawn);
            player.setGameMode(GameMode.SURVIVAL);
            player.setInvulnerable(false);
            player.sendMessage(Component.text("Welcome to the new world!", NamedTextColor.GREEN));
        }
    }

    private void resetPlayer(Player player) {
        player.getInventory().clear();
        player.setExp(0);
        player.setLevel(0);
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setSaturation(5);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player) || isSyncing) return;
        Player player = (Player) event.getEntity();
        syncHealth(Math.max(player.getHealth() - event.getFinalDamage(), 0));
    }

    @EventHandler
    public void onHeal(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player) || isSyncing) return;

        Player player = (Player) event.getEntity();
        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr == null) {
            getLogger().warning(() -> "MAX_HEALTH attribute missing for " + player.getName());
            return;
        }
        syncHealth(Math.min(player.getHealth() + event.getAmount(), maxHealthAttr.getValue()));
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player) || isSyncing) return;
        syncHunger(event.getFoodLevel());
    }

    private void syncHealth(double newHealth) {
        isSyncing = true;
        for (Player p : Bukkit.getOnlinePlayers()) {
            AttributeInstance maxHealthAttr = p.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealthAttr != null) {
                p.setHealth(Math.max(Math.min(newHealth, maxHealthAttr.getValue()), 0.0));
            }
        }
        isSyncing = false;
    }

    private void syncHunger(int newFood) {
        isSyncing = true;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setFoodLevel(Math.min(newFood, 20));
            p.setSaturation(5);
        }
        isSyncing = false;
    }
}