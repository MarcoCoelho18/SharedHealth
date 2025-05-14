package com.macacomilk.sharedhealth;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class SharedHealthPlugin extends JavaPlugin implements Listener {

    // Plugin state
    private boolean isSyncing = false;
    private World waitingWorld;
    private World currentWorld;
    private final Random random = new Random();
    private final Set<UUID> disconnectedPlayers = new HashSet<>();
    private static final String WAITING_WORLD_NAME = "sharedhealth_waiting_area";
    private static final int MAX_WORLD_HISTORY = 2;
    private Location waitingAreaCenter;
    private final AtomicInteger worldProgress = new AtomicInteger(0);
    private boolean isGeneratingWorld = false;

    @Override
    public void onEnable() {
        try {
            // Initialize waiting area
            createWaitingWorld();
            
            // Register event listeners
            getServer().getPluginManager().registerEvents(this, this);
            
            // Schedule repeating tasks
            scheduleWaitingAreaChecks();
            scheduleProgressUpdates();
            
            // Set initial world
            currentWorld = Bukkit.getWorlds().get(0);
            getLogger().info("SharedHealthPlugin enabled");
        } catch (Exception e) {
            getLogger().severe(String.format("Failed to enable plugin: %s", e.getMessage()));
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    private void createWaitingWorld() {
        // Create or load waiting world
        waitingWorld = Bukkit.getWorld(WAITING_WORLD_NAME);
        
        if (waitingWorld == null) {
            WorldCreator creator = new WorldCreator(WAITING_WORLD_NAME)
                .environment(World.Environment.NORMAL)
                .generateStructures(false)
                .type(WorldType.FLAT);
            waitingWorld = creator.createWorld();
            
            if (waitingWorld == null) {
                throw new IllegalStateException("Failed to create waiting world");
            }
        }
        
        // Configure waiting world rules
        waitingWorld.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        waitingWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        waitingWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        waitingWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        
        // Build waiting platform
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
                    player.setNoDamageTicks(20);
                    
                    if (player.getLocation().getX() < -7 || player.getLocation().getX() > 7 ||
                        player.getLocation().getZ() < -7 || player.getLocation().getZ() > 7) {
                        player.teleport(waitingAreaCenter);
                    }
                }
            }
        }, 0L, 20L);
    }

    private void scheduleProgressUpdates() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (isGeneratingWorld) {
                int progress = worldProgress.get();
                Component message = Component.text()
                    .append(Component.text("Generating world: ", NamedTextColor.YELLOW))
                    .append(Component.text(createProgressBar(progress), NamedTextColor.GREEN))
                    .append(Component.text(" " + progress + "%", NamedTextColor.WHITE))
                    .build();
                
                Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.getWorld().equals(waitingWorld))
                    .forEach(p -> p.sendActionBar(message));
            }
        }, 0L, 10L);
    }

    private String createProgressBar(int progress) {
        int bars = (int) (progress / 5.0);
        return "§a" + "|".repeat(Math.max(0, bars)) +
               "§7" + "|".repeat(Math.max(0, 20 - bars));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (disconnectedPlayers.remove(player.getUniqueId()) && 
            currentWorld != null && 
            !currentWorld.equals(waitingWorld)) {
            
            Bukkit.getScheduler().runTaskLater(this, () -> {
                resetPlayer(player);
                player.teleport(currentWorld.getSpawnLocation());
                player.setGameMode(GameMode.SURVIVAL);
                player.setInvulnerable(false);
            }, 1L);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().equals(waitingWorld)) {
            disconnectedPlayers.add(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        
        Player player = event.getPlayer();
        disconnectedPlayers.remove(player.getUniqueId());
        
        Bukkit.getScheduler().runTask(this, () -> {
            player.spigot().respawn();
            player.teleport(waitingAreaCenter);
            player.setGameMode(GameMode.ADVENTURE);
            player.setInvulnerable(true);
            player.sendMessage(Component.text("Generating new world...", NamedTextColor.YELLOW));
        });

        Bukkit.getScheduler().runTaskLater(this, () -> {
            generateNewWorld();
        }, 20L);
    }

    private void generateNewWorld() {
    if (isGeneratingWorld) return;
    isGeneratingWorld = true;
    worldProgress.set(0);

    broadcastToWaiting(Component.text("Starting world generation...", NamedTextColor.YELLOW));
    updateProgress(5, "Preparing world generator");

    Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
        try {
            long seed = random.nextLong();
            String worldName = "world_" + System.currentTimeMillis();

            updateProgress(10, "Setting up world parameters");
            WorldCreator creator = new WorldCreator(worldName)
                .seed(seed)
                .environment(World.Environment.NORMAL);

            updateProgress(20, "Generating terrain");

            // Create world on main thread
            World newWorld;
            try {
                newWorld = Bukkit.getScheduler().callSyncMethod(this, () -> creator.createWorld()).get();
                if (newWorld == null) {
                    throw new IllegalStateException("World creation failed");
                }
            } catch (InterruptedException | ExecutionException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("World creation failed", e);
            }

            for (int i = 0; i < 5; i++) {
                updateProgress(30 + (i * 10), "Loading chunks (" + (i + 1) + "/5)");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("World generation interrupted", e);
                }
            }

            updateProgress(90, "Finalizing world");

            Bukkit.getScheduler().runTask(this, () -> {
                World oldWorld = currentWorld;
                currentWorld = newWorld;
                Location spawn = newWorld.getSpawnLocation();

                for (Player player : Bukkit.getOnlinePlayers()) {
                    resetPlayer(player);
                    player.teleport(spawn);
                    player.setGameMode(GameMode.SURVIVAL);
                    player.setInvulnerable(false);
                }

                updateProgress(100, "World ready!");
                broadcastToWaiting(Component.text("Teleporting to new world...", NamedTextColor.GREEN));

                disconnectedPlayers.clear();
                if (oldWorld != null && !oldWorld.equals(waitingWorld)) {
                    cleanUpOldWorlds();
                }

                isGeneratingWorld = false;
            });

        } catch (IllegalStateException e) {
            Bukkit.getScheduler().runTask(this, () -> {
                getLogger().severe(String.format("World generation failed: %s", e.getMessage()));
                broadcastToWaiting(Component.text("World generation failed!", NamedTextColor.RED));
                isGeneratingWorld = false;
            });
        }
    });
}


    private void updateProgress(int progress, String message) {
        worldProgress.set(progress);
        broadcastToWaiting(Component.text(message + "... (" + progress + "%)", NamedTextColor.YELLOW));
    }

    private void broadcastToWaiting(Component message) {
        Bukkit.getOnlinePlayers().stream()
            .filter(p -> p.getWorld().equals(waitingWorld))
            .forEach(p -> p.sendMessage(message));
    }

    private void cleanUpOldWorlds() {
        try {
            File worldContainer = Bukkit.getWorldContainer();
            
            List<File> worldFolders = Arrays.stream(worldContainer.listFiles())
                .filter(f -> f.isDirectory() && f.getName().startsWith("world_"))
                .sorted(Comparator.comparingLong(File::lastModified).reversed())
                .collect(Collectors.toList());
            
            for (int i = MAX_WORLD_HISTORY; i < worldFolders.size(); i++) {
                File worldFolder = worldFolders.get(i);
                if (worldFolder.getName().equals(WAITING_WORLD_NAME)) continue;
                
                World world = Bukkit.getWorld(worldFolder.getName());
                if (world != null) {
                    Bukkit.unloadWorld(world, false);
                }
                
                deleteWorld(worldFolder);
                getLogger().info(String.format("Deleted old world: %s", worldFolder.getName()));
            }
        } catch (Exception e) {
            getLogger().warning(String.format("Failed to clean up old worlds: %s", e.getMessage()));
        }
    }

    private boolean deleteWorld(File path) {
        if (path.exists()) {
            File[] files = path.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteWorld(file);
                    } else {
                        file.delete();
                    }
                }
            }
        }
        return path.delete();
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
        
        Player damagedPlayer = (Player) event.getEntity();
        double damageAmount = event.getFinalDamage();
        double newHealth = Math.max(damagedPlayer.getHealth() - damageAmount, 0);
        
        String cause = event.getCause().toString().toLowerCase().replace("_", " ");
        Component damageMessage = Component.text()
            .append(Component.text(damagedPlayer.getName(), NamedTextColor.RED))
            .append(Component.text(" took ", NamedTextColor.GRAY))
            .append(Component.text("❤ ", NamedTextColor.RED))
            .append(Component.text(String.format("%.1f", damageAmount), NamedTextColor.RED))
            .append(Component.text(" damage from ", NamedTextColor.GRAY))
            .append(Component.text(cause, NamedTextColor.YELLOW))
            .build();
        
        Bukkit.broadcast(damageMessage);
        syncHealth(newHealth, damagedPlayer);
    }

    private void syncHealth(double newHealth, Player sourcePlayer) {
        isSyncing = true;
        for (Player p : Bukkit.getOnlinePlayers()) {
            AttributeInstance maxHealthAttr = p.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealthAttr != null) {
                double currentHealth = p.getHealth();
                p.setHealth(Math.max(Math.min(newHealth, maxHealthAttr.getValue()), 0.0));
                
                if (p != sourcePlayer && currentHealth != newHealth) {
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
                    p.setVelocity(p.getLocation().getDirection().multiply(-0.1).setY(0.1));
                }
            }
        }
        isSyncing = false;
    }

    @EventHandler
    public void onHeal(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player) || isSyncing) return;

        Player player = (Player) event.getEntity();
        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr == null) {
            getLogger().warning(String.format("MAX_HEALTH attribute missing for %s", player.getName()));
            return;
        }
        
        double newHealth = Math.min(player.getHealth() + event.getAmount(), maxHealthAttr.getValue());
        syncHealth(newHealth, player);
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player) || isSyncing) return;
        syncHunger(event.getFoodLevel());
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