package com.macacomilk.sharedhealth;

import java.io.File;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
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
    private Location waitingAreaCenter;
    private final AtomicInteger worldProgress = new AtomicInteger(0);
    private boolean isGeneratingWorld = false;
    private World oldWorldToDelete = null;

    @Override
    public void onEnable() {
        try {
            // Initialize waiting area
            createWaitingWorld();
            
            // Register event listeners
            getServer().getPluginManager().registerEvents(this, this);
            
            // Schedule repeating tasks
            scheduleWaitingAreaChecks();
            scheduleAmbientSounds();
            
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
                // Create patterned floor
                if ((x + z) % 2 == 0) {
                    waitingWorld.getBlockAt(x, 5, z).setType(Material.POLISHED_ANDESITE);
                } else {
                    waitingWorld.getBlockAt(x, 5, z).setType(Material.POLISHED_DIORITE);
                }
            }
        }
        
        // Create decorative walls
        for (int y = 5; y <= 10; y++) {
            for (int x = -8; x <= 8; x++) {
                Material wallMaterial = (y % 2 == 0) ? Material.STONE_BRICKS : Material.MOSSY_STONE_BRICKS;
                waitingWorld.getBlockAt(x, y, -8).setType(wallMaterial);
                waitingWorld.getBlockAt(x, y, 8).setType(wallMaterial);
            }
            for (int z = -7; z <= 7; z++) {
                Material wallMaterial = (y % 2 == 0) ? Material.STONE_BRICKS : Material.MOSSY_STONE_BRICKS;
                waitingWorld.getBlockAt(-8, y, z).setType(wallMaterial);
                waitingWorld.getBlockAt(8, y, z).setType(wallMaterial);
            }
        }
        
        // Create decorative pillars at corners
        for (int y = 5; y <= 11; y++) {
            Material pillarMaterial = (y % 3 == 0) ? Material.QUARTZ_PILLAR : Material.SMOOTH_QUARTZ;
            waitingWorld.getBlockAt(-8, y, -8).setType(pillarMaterial);
            waitingWorld.getBlockAt(-8, y, 8).setType(pillarMaterial);
            waitingWorld.getBlockAt(8, y, -8).setType(pillarMaterial);
            waitingWorld.getBlockAt(8, y, 8).setType(pillarMaterial);
        }
        
        // Create stained glass ceiling with pattern
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                Material glassType;
                if (Math.abs(x) == Math.abs(z)) {
                    glassType = Material.RED_STAINED_GLASS;
                } else if (x == 0 || z == 0) {
                    glassType = Material.BLUE_STAINED_GLASS;
                } else {
                    glassType = Material.WHITE_STAINED_GLASS;
                }
                waitingWorld.getBlockAt(x, 11, z).setType(glassType);
            }
        }
        
        // Add decorative lighting (sea lanterns)
        for (int x = -6; x <= 6; x += 4) {
            for (int z = -6; z <= 6; z += 4) {
                waitingWorld.getBlockAt(x, 10, z).setType(Material.SEA_LANTERN);
            }
        }
        
        // Add some plants and decorations
waitingWorld.getBlockAt(2, 6, 0).setType(Material.FLOWER_POT);
waitingWorld.getBlockAt(-2, 6, 0).setType(Material.FLOWER_POT);

// Add some banners on walls
for (int x = -6; x <= 6; x += 3) {
    waitingWorld.getBlockAt(x, 8, -7).setType(Material.WHITE_WALL_BANNER);
    waitingWorld.getBlockAt(x, 8, 7).setType(Material.WHITE_WALL_BANNER);
}

// Add red carpet to the 3x3 center area
for (int x = -1; x <= 1; x++) {
    for (int z = -1; z <= 1; z++) {
        waitingWorld.getBlockAt(x, 6, z).setType(Material.RED_CARPET);
    }
}

        
        // Add some seating (stairs)
        waitingWorld.getBlockAt(3, 5, 3).setType(Material.OAK_STAIRS);
        waitingWorld.getBlockAt(-3, 5, 3).setType(Material.OAK_STAIRS);
        waitingWorld.getBlockAt(3, 5, -3).setType(Material.OAK_STAIRS);
        waitingWorld.getBlockAt(-3, 5, -3).setType(Material.OAK_STAIRS);
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
                    
                    // Add ambient particles around players
                    player.spawnParticle(Particle.END_ROD, 
                        player.getLocation().add(0, 2, 0), 
                        3, 0.5, 0.5, 0.5, 0.05);
                }
            }
        }, 0L, 20L);
    }

    private void scheduleAmbientSounds() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld().equals(waitingWorld)) {
                    player.playSound(player.getLocation(), 
                        Sound.AMBIENT_UNDERWATER_LOOP, 
                        0.3f, 1.0f);
                }
            }
        }, 0L, 100L); // Every 5 seconds
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (disconnectedPlayers.remove(player.getUniqueId()) && 
            currentWorld != null && 
            !currentWorld.equals(waitingWorld)) {
            
            Bukkit.getScheduler().runTaskLater(this, () -> {
                // Don't reset inventory on join
                player.teleport(currentWorld.getSpawnLocation());
                player.setGameMode(GameMode.SURVIVAL);
                player.setInvulnerable(false);
                player.setHealth(20);
                player.setFoodLevel(20);
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

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                long seed = random.nextLong();
                String worldName = "world_" + System.currentTimeMillis();

                // Simulate generation time
                for (int i = 0; i <= 100; i++) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("World generation interrupted", e);
                    }
                }

                // Create world on main thread
                World newWorld;
                try {
                    newWorld = Bukkit.getScheduler().callSyncMethod(this, () -> {
                        WorldCreator creator = new WorldCreator(worldName)
                            .seed(seed)
                            .environment(World.Environment.NORMAL);
                        return creator.createWorld();
                    }).get();
                    
                    if (newWorld == null) {
                        throw new IllegalStateException("World creation failed");
                    }
                } catch (InterruptedException | ExecutionException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("World creation failed", e);
                }

                Bukkit.getScheduler().runTask(this, () -> {
                    oldWorldToDelete = currentWorld;
                    currentWorld = newWorld;
                    Location spawn = newWorld.getSpawnLocation();

                    // Reset only inventory for players in waiting room (those who died)
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getWorld().equals(waitingWorld)) {
                            player.getInventory().clear();
                        }
                        resetPlayerHealth(player);
                        player.teleport(spawn);
                        player.setGameMode(GameMode.SURVIVAL);
                        player.setInvulnerable(false);
                    }

                    broadcastToWaiting(Component.text("Teleporting to new world!", NamedTextColor.GREEN));
                    disconnectedPlayers.clear();
                    isGeneratingWorld = false;

                    // Schedule old world deletion after 10 seconds
                    if (oldWorldToDelete != null && !oldWorldToDelete.equals(waitingWorld)) {
                        Bukkit.getScheduler().runTaskLater(this, () -> {
                            Bukkit.unloadWorld(oldWorldToDelete, false);
                            deleteWorld(oldWorldToDelete.getWorldFolder());
                            getLogger().info(String.format("Deleted old world: %s", oldWorldToDelete.getName()));
                        }, 200L); // 10 seconds (20 ticks/second * 10)
                    }
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

    private void resetPlayerHealth(Player player) {
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setSaturation(5);
    }

    private void broadcastToWaiting(Component message) {
        Bukkit.getOnlinePlayers().stream()
            .filter(p -> p.getWorld().equals(waitingWorld))
            .forEach(p -> p.sendMessage(message));
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