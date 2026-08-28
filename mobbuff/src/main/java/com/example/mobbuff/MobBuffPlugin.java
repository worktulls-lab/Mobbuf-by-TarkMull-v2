package com.example.mobbuff;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Player;
import org.bukkit.entity.SkeletonHorse;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MobBuffPlugin extends JavaPlugin {

    private boolean buffEnabled = false;

    private int killCount = 0;
    private long lastKillMillis = System.currentTimeMillis();

    private int zombieBossKillCounter = 0;

    private final Set<UUID> phantomHandledTonight = new HashSet<>();
    private final Map<UUID, Location> lastKnownTargetLocation = new HashMap<>();
    private final Set<String> quicksandSpots = new HashSet<>(); // "world:x:z"

    private boolean weatherAnomalyActive = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        buffEnabled = getConfig().getBoolean("enabled", false);
        killCount = getConfig().getInt("killCount", 0);

        getCommand("mobbuff").setExecutor(new MobBuffCommand(this));
        getServer().getPluginManager().registerEvents(new MobBuffListener(this), this);

        if (buffEnabled) {
            applyToAllExistingMobs(true);
        }

        registerCustomRecipes();

        startDecayTask();
        startPhantomTask();
        startWaterSinkTask();
        startInfectedWaterTask();
        startDragonFireballTask();
        startCreeperNoFearTask();
        startSkeletonHorseTrapTask();
        startSkeletonHorseTrapCheckTask();
        startBeeNightAggroTask();
        startWolfNightAggroTask();
        startPiglinForceAggroTask();
        startEndermanAmbushTask();
        startZombieDoorBreakTask();
        startPortalGuestTask();
        startChickenRainRandomTask();
        startQuicksandTask();
        startTargetMemoryTask();
        startAmbientScareTask();
        startZombieBossBlockBreakTask();

        getLogger().info("MobBuff запущен. Баф мобов сейчас: " + (buffEnabled ? "ВКЛЮЧЁН" : "ВЫКЛЮЧЕН"));
    }

    @Override
    public void onDisable() {
        getConfig().set("enabled", buffEnabled);
        getConfig().set("killCount", killCount);
        saveConfig();
    }

    public boolean isBuffEnabled() {
        return buffEnabled;
    }

    public void setBuffEnabled(boolean enabled) {
        this.buffEnabled = enabled;
        getConfig().set("enabled", enabled);
        saveConfig();
        applyToAllExistingMobs(enabled);
        if (!enabled) {
            killCount = 0;
            phantomHandledTonight.clear();
        }
    }

    public void applyToAllExistingMobs(boolean enable) {
        getServer().getWorlds().forEach(world ->
                world.getLivingEntities().forEach(entity -> {
                    if (!(entity instanceof Player)) {
                        if (enable) {
                            MobBuffUtil.applyBuff(entity, this);
                        } else {
                            MobBuffUtil.removeBuff(entity);
                        }
                    }
                })
        );
    }

    // --- Переопределённые рецепты брони: x2 материала (железо + медь, если есть) ---

    private void registerCustomRecipes() {
        registerArmorSet(Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
                Material.IRON_INGOT, 10, 16, 14, 8);

        Material copperHelmet = Material.matchMaterial("COPPER_HELMET");
        Material copperChest = Material.matchMaterial("COPPER_CHESTPLATE");
        Material copperLegs = Material.matchMaterial("COPPER_LEGGINGS");
        Material copperBoots = Material.matchMaterial("COPPER_BOOTS");
        Material copperIngot = Material.matchMaterial("COPPER_INGOT");
        if (copperHelmet != null && copperChest != null && copperLegs != null
                && copperBoots != null && copperIngot != null) {
            registerArmorSet(copperHelmet, copperChest, copperLegs, copperBoots, copperIngot, 10, 16, 14, 8);
        }
    }

    private void registerArmorSet(Material helmet, Material chest, Material legs, Material boots,
                                   Material ingot, int helmetCount, int chestCount, int legsCount, int bootsCount) {
        registerDoubledRecipe(helmet, ingot, helmetCount, "helmet");
        registerDoubledRecipe(chest, ingot, chestCount, "chest");
        registerDoubledRecipe(legs, ingot, legsCount, "legs");
        registerDoubledRecipe(boots, ingot, bootsCount, "boots");
    }

    private void registerDoubledRecipe(Material result, Material ingredient, int amount, String suffix) {
        try {
            NamespacedKey vanillaKey = NamespacedKey.minecraft(result.name().toLowerCase());
            getServer().removeRecipe(vanillaKey);
        } catch (Exception ignored) {
        }
        NamespacedKey key = new NamespacedKey(this, result.name().toLowerCase() + "_x2_" + suffix);
        ShapelessRecipe recipe = new ShapelessRecipe(key, new ItemStack(result));
        recipe.addIngredient(amount, ingredient);
        try {
            getServer().addRecipe(recipe);
        } catch (Exception e) {
            getLogger().warning("Не удалось зарегистрировать рецепт для " + result + ": " + e.getMessage());
        }
    }

    // --- Динамическая сложность ---

    public void registerHostileKill() {
        killCount = Math.min(MobBuffUtil.MAX_KILL_COUNT, killCount + 1);
        lastKillMillis = System.currentTimeMillis();
    }

    public int getKillCount() {
        return killCount;
    }

    public double getCurrentDamageMultiplier() {
        return Math.min(MobBuffUtil.DAMAGE_MAX_MULTIPLIER,
                MobBuffUtil.BASE_MULTIPLIER + MobBuffUtil.rawBonus(killCount, MobBuffUtil.DAMAGE_MAX_MULTIPLIER));
    }

    public double getCurrentHealthMultiplier() {
        return Math.min(MobBuffUtil.HEALTH_MAX_MULTIPLIER,
                MobBuffUtil.BASE_MULTIPLIER + MobBuffUtil.rawBonus(killCount, MobBuffUtil.HEALTH_MAX_MULTIPLIER));
    }

    private void startDecayTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!buffEnabled || killCount <= 0) return;
                if (System.currentTimeMillis() - lastKillMillis > 60_000L) {
                    killCount = Math.max(0, killCount - 1);
                }
            }
        }.runTaskTimer(this, 300L, 300L);
    }

    // --- Зомби-босс: общий счётчик убитых зомби ---

    public void registerZombieKillForBoss(Location lastKillLoc) {
        zombieBossKillCounter++;
        if (zombieBossKillCounter >= MobBuffUtil.ZOMBIE_BOSS_KILL_THRESHOLD) {
            zombieBossKillCounter = 0;
            spawnZombieBossNear(lastKillLoc);
        }
    }

    public void spawnZombieBossNear(Location loc) {
        if (loc.getWorld() == null) return;
        MobBuffUtil.spawnZombieBoss(loc);
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) < 64 * 64) {
                p.sendActionBar(net.md_5.bungee.api.chat.TextComponent.fromLegacyText(
                        net.md_5.bungee.api.ChatColor.DARK_RED + "Что-то огромное поднимается из земли..."));
            }
        }
    }

    // --- Фантомы каждую ночь ---

    private void startPhantomTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!buffEnabled) return;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    long time = player.getWorld().getTime();
                    boolean isNight = time >= 13000 && time < 23000;
                    if (isNight) {
                        if (!phantomHandledTonight.contains(player.getUniqueId())) {
                            MobBuffUtil.spawnNightPhantoms(player);
                            phantomHandledTonight.add(player.getUniqueId());
                        }
                    } else {
                        phantomHandledTonight.remove(player.getUniqueId());
                    }
                }
            }
        }.runTaskTimer(this, 100L, 100L);
    }

    private void startWaterSinkTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!buffEnabled) return;
                getServer().getWorlds().forEach(world ->
                        world.getLivingEntities().forEach(entity -> {
                            if (!(entity instanceof Player) && MobBuffUtil.isBuffed(entity)) {
                                MobBuffUtil.applyWaterSink(entity);
                            }
                        })
                );
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    private void startInfectedWaterTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!buffEnabled) return;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!player.isInWater()) continue;
                    long nearbyDrowned = player.getNearbyEntities(12, 6, 12).stream()
                            .filter(e -> e instanceof Drowned).count();
                    if (nearbyDrowned >= MobBuffUtil.INFECTED_WATER_DROWNED_THRESHOLD) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 80, 0));
                    }
                }
            }
        }.runTaskTimer(this, 60L, 60L);
    }

    private void startDragonFireballTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!buffEnabled) return;
                getServer().getWorlds().forEach(world ->
                        world.getEntitiesByClass(EnderDragon.class).forEach(dragon -> {
                            Player nearest = null;
                            double bestDist = Double.MAX_VALUE;
                            for (Player player : world.getPlayers()) {
                                double dist = player.getLocation().distanceSquared(dragon.getLocation());
                                if (dist < bestDist && dist < 60 * 60) {
                                    bestDist = dist;
                                    nearest = player;
                                }
                            }
                            if (nearest != null) {
                                Location from = dragon.getLocation().add(0, 2, 0);
                                Vector direction = nearest.getLocation().toVector().subtract(from.toVector()).normalize();
                                DragonFireball fireball = world.spawn(from, DragonFireball.class);
                                fireball.setVelocity(direction.multiply(1.2));
                            }
                        })
                );
            }
        }.runTaskTimer(this, 100L, 100L); // ещё чаще: ~5 секунд
    }

    private void startCreeperNoFearTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!buffEnabled) return;
                getServer().getWorlds().forEach(world ->
                        world.getEntitiesByClass(Creeper.class).forEach(MobBuffUtil::suppressCreeperFear)
                );
            }
        }.runTaskTimer(this, 5L, 5L);
    }

    private void startSkeletonHorseTrapTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!buffEnabled) return;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    MobBuffUtil.trySpawnSkeletonHorseTrap(player);
                }
            }
        }.runTaskTimer(this, 12000L, 12000L);
    }

    private void startSkeletonHorseTrapCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!buffEnabled) return;
                getServer().getWorlds().forEach(world ->
                        world.getEntitiesByClass(SkeletonHorse.class).forEach(horse -> {
                            if (!MobBuffUtil.isTrapHorse(horse)) return;
                            boolean playerClose = horse.getNearbyEntities(
                                            MobBuffUtil.SKELETON_HORSE_TRAP_RADIUS,
                                            MobBuffUtil.SKELETON_HORSE_TRAP_RADIUS,
                                            MobBuffUtil.SKELETON_HORSE_TRAP_RADIUS)
                                    .stream().anyMatch(e -> e instanceof Player);
                            if (playerClose) {
                                MobBuffUtil.triggerSkeletonHorseTrap(horse);
                            }
                        })
                );
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    // --- Пчёлы враждебны ночью (без потери жала) ---

    private void startBeeNightAggroTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!buffEnabled) return;
                getServer().getWorlds().forEach(world -> {
                    boolean night = world.getTime() >= 13000 && world.getTime() < 23000;
                    if (!night) return;
                    world.getEntitiesByClass(Bee.class).forEach(bee -> {
                        bee.setHasStung(false);
                        if (bee.getTarget() == null) {
                            bee.getNearbyEntities(MobBuffUtil.BEE_NIGHT_AGGRO_RADIUS, MobBuffUtil.BEE_NIGHT_AGGRO_RADIUS,
                                            MobBuffUtil.BEE_NIGHT_AGGRO_RADIUS).stream()
                                    .filter(e -> e instanceof Player).findAny()
                                    .ifPresent(p -> bee.setTarget((Player) p));
                        }
                    });
                });
            }
        }.runTaskTimer(this, 60L, 60L);
    }

    // --- Дикие волки враждебны ночью ---

    private void startWolfNightAggroTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!buffEnabled) return;
                getServer().getWorlds().forEach(world -> {
                    boolean night = world.getTime() >= 13000 && world.getTime() < 23000;
                    if (!night) return;
                    world.getEntitiesByClass(Wolf.class).forEach(wolf -> {
                        if (wolf.isTamed() || wolf.getTarget() != null) return;
                        wolf.getNearbyEntities(MobBuffUtil.WOLF_NIGHT_AGGRO_RADIUS, MobBuffUtil.WOLF_NIGHT_AGGRO_RADIUS,
                                        MobBuffUtil.WOLF_NIGHT_AGGRO_RADIUS).stream()
                                .filter(e -> e instanceof Player).findAny()
                                .ifPresent(p -> wolf.setTarget((Player) p));
                    });
                });
            }
        }.runTaskTimer(this, 60L, 60L);
    }

    // --- Пиглины враждебны даже в золотой броне ---

    private void startPiglinForceAggroTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!buffEnabled) return;
                getServer().getWorlds().forEach(world ->
                        world.getEntitiesByClass(Piglin.class).forEach(piglin -> {
                            if (piglin.getTarget() != null) return;
                            piglin.getNearbyEntities(MobBuffUtil.PIGLIN_FORCE_AGGRO_RADIUS, MobBuffUtil.PIGLIN_FORCE_AGGRO_RADIUS,
                                            MobBuffUtil.PIGLIN_FORCE_AGGRO_RADIUS).stream()
                                    .filter(e -> e instanceof Player).findAny()
                                    .ifPresent(p -> piglin.setTarget((Player) p));
                        })
                );
            }
        }.runTaskTimer(this, 60L, 60L);
    }

    // --- Эндермены чаще телепортируются за спину ---

    private void startEndermanAmbushTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!buffEnabled) return;
                getServer().getWorlds().forEach(world ->
                        world.getEntitiesByClass(Enderman.class).forEach(enderman -> {
                            if (!(enderman.getTarget() instanceof Player target)) return;
                            if (Math.random() > MobBuffUtil.ENDERMAN_AMBUSH_CHANCE) return;
                            Vector back = target.getLocation().getDirection().multiply(-2.5);
                            Location dest = target.getLocation().add(back).add(0, 0.5, 0);
                            enderman.teleport(dest);
                        })
                );
            }
        }.runTaskTimer(this, 40L, 40L);
    }

    // --- Зомби ломают двери намного быстрее ---

    private void startZombieDoorBreakTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!buffEnabled) return;
                getServer().getWorlds().forEach(world ->
                        world.getEntitiesByClass(Zombie.class).forEach(zombie -> {
                            if (zombie.getTarget() == null) return;
                            if (Math.random() > MobBuffUtil.ZOMBIE_DOOR_BREAK_CHANCE) return;
                            var block = zombie.getLocation().add(zombie.getLocation().getDirection()).getBlock();
                            if (block.getBlockData() instanceof Openable openable) {
                                openable.setOpen(true);
                                block.setBlockData(openable);
                            }
                        })
                );
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    // --- Мобы помнят последнюю точку игрока ---

    private void startTargetMemoryTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!buffEnabled) return;
                getServer().getWorlds().forEach(world ->
                        world.getLivingEntities().forEach(entity -> {
                            if (entity instanceof Mob mob && mob.getTarget() instanceof Player player) {
                                lastKnownTargetLocation.put(mob.getUniqueId(), player.getLocation().clone());
                            }
                        })
                );
                if (lastKnownTargetLocation.size() > 2000) {
                    lastKnownTargetLocation.clear(); // защита от разрастания карты
                }
            }
        }.runTaskTimer(this, 40L, 40L);
    }

    public Location getLastKnownLocation(UUID mobId) {
        return lastKnownTargetLocation.get(mobId);
    }

    // --- Гости из портала: временный разлом с мобами Нижнего мира ---

    private void startPortalGuestTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!buffEnabled) return;
                if (Math.random() > MobBuffUtil.PORTAL_GUEST_CHANCE) return;
                var players = Bukkit.getOnlinePlayers();
                if (players.isEmpty()) return;
                Player target = players.stream().skip((int) (Math.random() * players.size())).findFirst().orElse(null);
                if (target == null) return;

                Location loc = target.getLocation().clone().add(
                        (Math.random() * 40 - 20), 0, (Math.random() * 40 - 20));
                loc.setY(loc.getWorld().getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ()) + 1);
                loc.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, loc, 100, 1, 1, 1);
                loc.getWorld().playSound(loc, org.bukkit.Sound.BLOCK_PORTAL_TRIGGER, 1f, 1f);

                int amount = 2 + (int) (Math.random() * 3);
                for (int i = 0; i < amount; i++) {
                    var mob = loc.getWorld().spawnEntity(loc, EntityType.ZOMBIFIED_PIGLIN);
                    Bukkit.getScheduler().runTaskLater(MobBuffPlugin.this, () -> {
                        if (mob.isValid()) mob.remove();
                    }, 3600L); // самоуничтожатся через 3 минуты, если не убиты
                }
            }
        }.runTaskTimer(this, 24000L, 24000L); // проверка раз в ~20 минут
    }

    // --- Куриный дождь (случайное редкое событие) ---

    private void startChickenRainRandomTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!buffEnabled) return;
                if (Math.random() > MobBuffUtil.CHICKEN_RAIN_RANDOM_CHANCE) return;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    triggerChickenRain(player);
                    break; // одному случайному онлайн-игроку за раз достаточно
                }
            }
        }.runTaskTimer(this, 6000L, 6000L);
    }

    public void triggerChickenRain(Player player) {
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 15 || !player.isOnline()) {
                    cancel();
                    return;
                }
                Location loc = player.getLocation().clone().add(
                        Math.random() * 10 - 5, 10 + Math.random() * 5, Math.random() * 10 - 5);
                player.getWorld().spawnEntity(loc, EntityType.CHICKEN);
                ticks++;
            }
        }.runTaskTimer(this, 0L, 10L);
    }

    // --- Максвелл-кот и супер-зомби вручную ---

    public void spawnMaxwellManually(Player player) {
        MobBuffUtil.spawnMaxwellAt(player.getLocation().add(player.getLocation().getDirection().multiply(2)));
    }

    public void spawnSuperZombieManually(Player player) {
        spawnZombieBossNear(player.getLocation().add(player.getLocation().getDirection().multiply(3)));
    }

    // --- Зыбучие пески (упрощённая версия, в памяти) ---

    private void startQuicksandTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!buffEnabled) return;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    Location below = player.getLocation().clone().subtract(0, 1, 0);
                    if (below.getBlock().getType() != Material.SAND) continue;
                    String key = below.getWorld().getName() + ":" + below.getBlockX() + ":" + below.getBlockZ();
                    if (!quicksandSpots.contains(key)) {
                        // 25% шанс, что именно этот блок песка окажется зыбучим при первом наступлении
                        if (Math.random() < 0.25) {
                            quicksandSpots.add(key);
                        } else {
                            continue;
                        }
                    }
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));
                    player.setVelocity(player.getVelocity().add(new Vector(0, -0.08, 0)));
                }
            }
        }.runTaskTimer(this, 10L, 10L);
    }

    // --- Погодные аномалии (чёрный туман) ---

    public void triggerWeatherAnomaly(String type, World world) {
        if (weatherAnomalyActive) return;
        if (!"fog".equalsIgnoreCase(type)) return;
        weatherAnomalyActive = true;
        Location center = world.getPlayers().isEmpty() ? world.getSpawnLocation() : world.getPlayers().get(0).getLocation();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 200) { // ~2 минуты (10 тиков * 200 / 20 = 100 сек, оставим как есть)
                    weatherAnomalyActive = false;
                    cancel();
                    return;
                }
                for (Player player : world.getPlayers()) {
                    if (player.getLocation().distanceSquared(center) < 60 * 60) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                        player.getWorld().spawnParticle(org.bukkit.Particle.CAMPFIRE_COSY_SMOKE,
                                player.getLocation(), 10, 3, 3, 3);
                    }
                }
                if (ticks % 60 == 0) {
                    world.playSound(center, org.bukkit.Sound.AMBIENT_CAVE, 1f, 0.5f);
                }
                ticks += 10;
            }
        }.runTaskTimer(this, 0L, 10L);
    }

    // --- Зомби-босс ломает блоки на своём пути ---

    private void startZombieBossBlockBreakTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!buffEnabled) return;
                getServer().getWorlds().forEach(world ->
                        world.getEntitiesByClass(Zombie.class).stream()
                                .filter(MobBuffUtil::isZombieBoss)
                                .forEach(boss -> {
                                    if (boss.getTarget() == null) return;
                                    var block = boss.getLocation().add(boss.getLocation().getDirection()).getBlock();
                                    if (block.getType().isSolid() && block.getType() != Material.BEDROCK
                                            && block.getType() != Material.OBSIDIAN) {
                                        block.getWorld().playSound(block.getLocation(), block.getType().name().contains("WOOD")
                                                ? org.bukkit.Sound.BLOCK_WOOD_BREAK : org.bukkit.Sound.BLOCK_STONE_BREAK, 1f, 1f);
                                        block.setType(Material.AIR);
                                    }
                                })
                );
            }
        }.runTaskTimer(this, 10L, 10L);
    }

    // --- "Ты слышишь это?" — редкий атмосферный звук ---

    public void startAmbientScareTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!buffEnabled) return;
                if (Math.random() > 0.0006) return;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (Math.random() < 0.3) {
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WITHER_AMBIENT, 0.6f, 1f);
                    }
                }
            }
        }.runTaskTimer(this, 200L, 200L);
    }
}
