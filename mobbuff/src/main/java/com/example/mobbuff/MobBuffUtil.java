package com.example.mobbuff;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.Random;

public final class MobBuffUtil {

    // Базовая сложность
    public static final double BASE_MULTIPLIER = 1.8D;
    // Потолки для ОБЫЧНЫХ мобов
    public static final double DAMAGE_MAX_MULTIPLIER = 2.5D;
    public static final double HEALTH_MAX_MULTIPLIER = 3.5D;
    // Отдельные потолки для зомби-семейства (Zombie/Husk/Drowned/ZombieVillager/ZombifiedPiglin)
    public static final double ZOMBIE_DAMAGE_MAX_MULTIPLIER = 2.0D;
    public static final double ZOMBIE_HEALTH_MAX_MULTIPLIER = 4.0D;

    public static final double DARKNESS_BONUS = 0.3D;
    // 200 убийств (любых мобов) до потолка урона обычных мобов: (2.5-1.8)/200
    public static final double KILL_BONUS_PER_KILL = 0.0035D;
    public static final int MAX_KILL_COUNT = 650;

    public static final double SPEED_MULTIPLIER = 1.2D;
    public static final double EXPLOSION_MULTIPLIER = 2.0D;
    public static final double CREEPER_FUSE_MULTIPLIER = 0.7D;
    public static final double FOLLOW_RANGE_MULTIPLIER = 1.3D;
    public static final double WATER_ATTACK_BONUS = 1.3D;

    public static final double BOSS_MULTIPLIER = 2.0D;       // Иссушитель
    public static final double BOSS_EXTRA_SPEED = 1.15D;
    public static final double DRAGON_MULTIPLIER = 3.3D;     // Дракон усилен ещё больше
    public static final double DRAGON_EXTRA_SPEED = 1.3D;

    public static final double KNOCKBACK_RESISTANCE_BONUS = 0.25D;
    public static final double CHARGED_CREEPER_CHANCE = 0.08D;

    public static final double SKELETON_HORSE_TRAP_SPAWN_CHANCE = 0.08D;
    public static final int SKELETON_HORSE_TRAP_RADIUS = 6;
    public static final int SKELETON_HORSE_TRAP_HORDE_MIN = 5;
    public static final int SKELETON_HORSE_TRAP_HORDE_MAX = 7;

    public static final double SPIDER_POISON_CHANCE = 0.5D;
    public static final double ZOMBIE_INFECT_CHANCE = 0.3D;
    public static final double SPIDER_AMBUSH_CHANCE = 0.1D;
    public static final double CAVE_TRAP_CHANCE = 0.05D;
    public static final double XP_REDUCTION = 0.3D;
    public static final int INFECTED_WATER_DROWNED_THRESHOLD = 3;

    // Доп. лут с бафнутых мобов
    public static final double EXTRA_LOOT_CHANCE = 0.15D;

    // Руды в 2 раза реже (вероятность "стереть" сгенерированную руду обратно в камень)
    public static final double ORE_THIN_CHANCE = 0.5D;

    // Прочность предметов
    public static final double DURABILITY_MULTIPLIER_DAY = 1.5D;
    public static final double DURABILITY_MULTIPLIER_NIGHT = 2.5D;

    // Кулдауны
    public static final int GOLDEN_APPLE_COOLDOWN_TICKS = 600; // 30 сек
    public static final int POTION_COOLDOWN_TICKS = 400;       // 20 сек
    public static final int SHIELD_COOLDOWN_TICKS = 200;       // 10 сек

    // Головы иссушителей-скелетов
    public static final double WITHER_SKULL_DROP_CHANCE = 0.15D;
    public static final double WITHER_SKULL_PROJECTILE_CHANCE = 0.03D;

    // Гости из портала
    public static final double PORTAL_GUEST_CHANCE = 0.12D;

    // Максвелл-кот (мем)
    public static final double MAXWELL_SPAWN_CHANCE = 0.002D;

    // Куриный дождь
    public static final double CHICKEN_RAIN_RANDOM_CHANCE = 0.0004D;

    // Деревня-мародёр
    public static final int VILLAGE_PILLAGER_CHECK_RADIUS = 32;

    // Зомби-босс
    public static final int ZOMBIE_BOSS_KILL_THRESHOLD = 100;

    // Ночная агрессия
    public static final double BEE_NIGHT_AGGRO_RADIUS = 8;
    public static final double WOLF_NIGHT_AGGRO_RADIUS = 10;
    public static final double PIGLIN_FORCE_AGGRO_RADIUS = 10;

    public static final double ENDERMAN_AMBUSH_CHANCE = 0.1D;
    public static final double GHAST_SHOCKWAVE_RADIUS = 6D;
    public static final double GHAST_SHOCKWAVE_STRENGTH = 1.4D;
    public static final double ZOMBIE_DOOR_BREAK_CHANCE = 0.3D;
    public static final double MOB_CALL_FOR_HELP_CHANCE = 0.2D;
    public static final double MOB_CALL_FOR_HELP_RADIUS = 15D;

    private static final String NS = "mobbuff";
    private static final Random RANDOM = new Random();

    private MobBuffUtil() {
    }

    // === Аттрибуты с учётом разных версий API ===

    private static Attribute maxHealthAttribute() {
        try {
            return Attribute.valueOf("MAX_HEALTH");
        } catch (IllegalArgumentException ex) {
            return Attribute.valueOf("GENERIC_MAX_HEALTH");
        }
    }

    private static Attribute movementSpeedAttribute() {
        try {
            return Attribute.valueOf("MOVEMENT_SPEED");
        } catch (IllegalArgumentException ex) {
            return Attribute.valueOf("GENERIC_MOVEMENT_SPEED");
        }
    }

    private static Attribute followRangeAttribute() {
        try {
            return Attribute.valueOf("FOLLOW_RANGE");
        } catch (IllegalArgumentException ex) {
            return Attribute.valueOf("GENERIC_FOLLOW_RANGE");
        }
    }

    private static Attribute knockbackResistanceAttribute() {
        try {
            return Attribute.valueOf("KNOCKBACK_RESISTANCE");
        } catch (IllegalArgumentException ex) {
            return Attribute.valueOf("GENERIC_KNOCKBACK_RESISTANCE");
        }
    }

    // === Динамическая сложность ===

    public static boolean isDark(Location loc) {
        return loc.getBlock().getLightLevel() < 7;
    }

    public static boolean isBoss(LivingEntity entity) {
        return entity instanceof Wither || entity instanceof EnderDragon;
    }

    public static boolean isDragon(LivingEntity entity) {
        return entity instanceof EnderDragon;
    }

    /** Зомби, зомби-житель, толстун, утопленник, зомбифицированный пиглин — всё семейство Zombie. */
    public static boolean isZombieFamily(LivingEntity entity) {
        return entity instanceof Zombie;
    }

    public static double healthCapFor(LivingEntity entity) {
        return isZombieFamily(entity) ? ZOMBIE_HEALTH_MAX_MULTIPLIER : HEALTH_MAX_MULTIPLIER;
    }

    public static double damageCapFor(LivingEntity entity) {
        return isZombieFamily(entity) ? ZOMBIE_DAMAGE_MAX_MULTIPLIER : DAMAGE_MAX_MULTIPLIER;
    }

    public static double rawBonus(int killCount, double cap) {
        return Math.min(Math.max(0, cap - BASE_MULTIPLIER), killCount * KILL_BONUS_PER_KILL);
    }

    public static double healthMultiplier(Location loc, MobBuffPlugin plugin, double cap) {
        double value = BASE_MULTIPLIER + rawBonus(plugin.getKillCount(), cap);
        if (isDark(loc)) {
            value += DARKNESS_BONUS;
        }
        return Math.min(cap, value);
    }

    public static double damageMultiplier(Location loc, MobBuffPlugin plugin, double cap) {
        double value = BASE_MULTIPLIER + rawBonus(plugin.getKillCount(), cap);
        if (isDark(loc)) {
            value += DARKNESS_BONUS;
        }
        return Math.min(cap, value);
    }

    // === Применение бафа к мобу при спавне ===

    public static void applyBuff(LivingEntity entity, MobBuffPlugin plugin) {
        if (isBuffed(entity)) {
            return;
        }
        AttributeInstance healthAttr = entity.getAttribute(maxHealthAttribute());
        if (healthAttr == null) {
            return;
        }

        boolean boss = isBoss(entity);
        boolean dragon = isDragon(entity);
        double multiplier;
        if (dragon) {
            multiplier = DRAGON_MULTIPLIER;
        } else if (boss) {
            multiplier = BOSS_MULTIPLIER;
        } else {
            multiplier = healthMultiplier(entity.getLocation(), plugin, healthCapFor(entity));
        }
        double newMax = healthAttr.getBaseValue() * multiplier;
        healthAttr.setBaseValue(newMax);
        entity.setHealth(Math.min(newMax, healthAttr.getValue()));

        AttributeInstance speedAttr = entity.getAttribute(movementSpeedAttribute());
        double bossSpeedFactor = dragon ? DRAGON_EXTRA_SPEED : (boss ? BOSS_EXTRA_SPEED : 1.0);
        double appliedSpeedMult = SPEED_MULTIPLIER * bossSpeedFactor;
        if (speedAttr != null) {
            speedAttr.setBaseValue(speedAttr.getBaseValue() * appliedSpeedMult);
        }

        AttributeInstance knockbackAttr = entity.getAttribute(knockbackResistanceAttribute());
        if (knockbackAttr != null) {
            knockbackAttr.setBaseValue(Math.min(1.0, knockbackAttr.getBaseValue() + KNOCKBACK_RESISTANCE_BONUS));
        }

        AttributeInstance followAttr = entity.getAttribute(followRangeAttribute());
        if (followAttr != null) {
            followAttr.setBaseValue(followAttr.getBaseValue() * FOLLOW_RANGE_MULTIPLIER);
        }

        if (entity instanceof Creeper creeper) {
            creeper.setExplosionRadius((int) Math.round(creeper.getExplosionRadius() * EXPLOSION_MULTIPLIER));
            creeper.setMaxFuseTicks((int) Math.round(creeper.getMaxFuseTicks() * CREEPER_FUSE_MULTIPLIER));
            if (!creeper.isPowered() && RANDOM.nextDouble() < CHARGED_CREEPER_CHANCE) {
                creeper.setPowered(true);
            }
        }

        mark(entity, multiplier, appliedSpeedMult);
    }

    public static void removeBuff(LivingEntity entity) {
        Double appliedMultiplier = getAppliedMultiplier(entity);
        if (appliedMultiplier == null) {
            return;
        }
        Double appliedSpeedMult = getAppliedSpeedMultiplier(entity);
        if (appliedSpeedMult == null) {
            appliedSpeedMult = SPEED_MULTIPLIER;
        }

        AttributeInstance healthAttr = entity.getAttribute(maxHealthAttribute());
        if (healthAttr != null) {
            double restoredMax = healthAttr.getBaseValue() / appliedMultiplier;
            healthAttr.setBaseValue(restoredMax);
            entity.setHealth(Math.min(restoredMax, entity.getHealth()));
        }

        AttributeInstance speedAttr = entity.getAttribute(movementSpeedAttribute());
        if (speedAttr != null) {
            speedAttr.setBaseValue(speedAttr.getBaseValue() / appliedSpeedMult);
        }

        AttributeInstance followAttr = entity.getAttribute(followRangeAttribute());
        if (followAttr != null) {
            followAttr.setBaseValue(followAttr.getBaseValue() / FOLLOW_RANGE_MULTIPLIER);
        }

        AttributeInstance knockbackAttr = entity.getAttribute(knockbackResistanceAttribute());
        if (knockbackAttr != null) {
            knockbackAttr.setBaseValue(Math.max(0.0, knockbackAttr.getBaseValue() - KNOCKBACK_RESISTANCE_BONUS));
        }

        if (entity instanceof Creeper creeper) {
            creeper.setExplosionRadius((int) Math.round(creeper.getExplosionRadius() / EXPLOSION_MULTIPLIER));
            creeper.setMaxFuseTicks((int) Math.round(creeper.getMaxFuseTicks() / CREEPER_FUSE_MULTIPLIER));
        }

        unmark(entity);
    }

    public static boolean isBuffed(LivingEntity entity) {
        return entity.getPersistentDataContainer()
                .has(new NamespacedKey(NS, "applied_mult"), PersistentDataType.DOUBLE);
    }

    private static Double getAppliedMultiplier(LivingEntity entity) {
        var key = new NamespacedKey(NS, "applied_mult");
        var pdc = entity.getPersistentDataContainer();
        if (!pdc.has(key, PersistentDataType.DOUBLE)) {
            return null;
        }
        return pdc.get(key, PersistentDataType.DOUBLE);
    }

    private static Double getAppliedSpeedMultiplier(LivingEntity entity) {
        var key = new NamespacedKey(NS, "applied_speed_mult");
        var pdc = entity.getPersistentDataContainer();
        if (!pdc.has(key, PersistentDataType.DOUBLE)) {
            return null;
        }
        return pdc.get(key, PersistentDataType.DOUBLE);
    }

    private static void mark(LivingEntity entity, double multiplier, double speedMultiplier) {
        var key = new NamespacedKey(NS, "applied_mult");
        var speedKey = new NamespacedKey(NS, "applied_speed_mult");
        entity.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, multiplier);
        entity.getPersistentDataContainer().set(speedKey, PersistentDataType.DOUBLE, speedMultiplier);
    }

    private static void unmark(LivingEntity entity) {
        var key = new NamespacedKey(NS, "applied_mult");
        var speedKey = new NamespacedKey(NS, "applied_speed_mult");
        entity.getPersistentDataContainer().remove(key);
        entity.getPersistentDataContainer().remove(speedKey);
    }

    // === Фантомы каждую ночь ===

    public static void spawnNightPhantoms(Player player) {
        int amount = 2 + RANDOM.nextInt(3);
        for (int i = 0; i < amount; i++) {
            Location loc = player.getLocation().clone().add(
                    RANDOM.nextInt(11) - 5,
                    15 + RANDOM.nextInt(5),
                    RANDOM.nextInt(11) - 5
            );
            player.getWorld().spawn(loc, Phantom.class);
        }
    }

    // === Вода: тонут быстрее ===

    public static void applyWaterSink(LivingEntity entity) {
        if (entity.isInWater() && !entity.isOnGround()) {
            Vector vel = entity.getVelocity();
            entity.setVelocity(vel.add(new Vector(0, -0.05, 0)));
        }
    }

    // === Ловушки в пещерах (заражённый камень) ===

    public static void tryPlaceCaveTrap(org.bukkit.Chunk chunk) {
        if (RANDOM.nextDouble() > CAVE_TRAP_CHANCE) {
            return;
        }
        org.bukkit.World world = chunk.getWorld();
        for (int attempt = 0; attempt < 6; attempt++) {
            int x = chunk.getX() * 16 + RANDOM.nextInt(16);
            int z = chunk.getZ() * 16 + RANDOM.nextInt(16);
            int y = 5 + RANDOM.nextInt(45);
            Block block = world.getBlockAt(x, y, z);
            if (block.getType() == Material.STONE) {
                Block below = block.getRelative(org.bukkit.block.BlockFace.DOWN);
                Block above = block.getRelative(org.bukkit.block.BlockFace.UP);
                if (above.getType() == Material.CAVE_AIR || below.getType() == Material.CAVE_AIR) {
                    block.setType(Material.INFESTED_STONE);
                    return;
                }
            }
        }
    }

    // === Криперы не боятся кошек ===

    public static void suppressCreeperFear(Creeper creeper) {
        boolean catNearby = creeper.getNearbyEntities(10, 6, 10).stream()
                .anyMatch(e -> e instanceof Cat || e instanceof org.bukkit.entity.Ocelot);
        if (catNearby) {
            Vector vel = creeper.getVelocity();
            if (Math.abs(vel.getX()) > 0.02 || Math.abs(vel.getZ()) > 0.02) {
                creeper.setVelocity(new Vector(0, vel.getY(), 0));
            }
        }
    }

    // === Ловушки со скелетами-лошадьми ===

    private static final String TRAP_HORSE_KEY = "trap_horse";

    public static void trySpawnSkeletonHorseTrap(Player player) {
        if (RANDOM.nextDouble() > SKELETON_HORSE_TRAP_SPAWN_CHANCE) {
            return;
        }
        Location base = player.getLocation().clone().add(
                RANDOM.nextInt(31) - 15, 0, RANDOM.nextInt(31) - 15);
        org.bukkit.World world = base.getWorld();
        if (world == null) return;
        int highestY = world.getHighestBlockYAt(base.getBlockX(), base.getBlockZ());
        Material groundType = world.getBlockAt(base.getBlockX(), highestY, base.getBlockZ()).getType();
        if (groundType == Material.WATER || groundType == Material.LAVA) {
            return;
        }
        Location spawnLoc = new Location(world, base.getX(), highestY + 1, base.getZ());
        var horse = world.spawn(spawnLoc, org.bukkit.entity.SkeletonHorse.class);
        horse.getPersistentDataContainer().set(
                new NamespacedKey(NS, TRAP_HORSE_KEY), PersistentDataType.BYTE, (byte) 1);
    }

    public static boolean isTrapHorse(org.bukkit.entity.SkeletonHorse horse) {
        return horse.getPersistentDataContainer()
                .has(new NamespacedKey(NS, TRAP_HORSE_KEY), PersistentDataType.BYTE);
    }

    public static void triggerSkeletonHorseTrap(org.bukkit.entity.SkeletonHorse horse) {
        horse.getPersistentDataContainer().remove(new NamespacedKey(NS, TRAP_HORSE_KEY));
        org.bukkit.World world = horse.getWorld();
        Location loc = horse.getLocation();
        world.strikeLightningEffect(loc);
        int amount = SKELETON_HORSE_TRAP_HORDE_MIN
                + RANDOM.nextInt(SKELETON_HORSE_TRAP_HORDE_MAX - SKELETON_HORSE_TRAP_HORDE_MIN + 1);
        for (int i = 0; i < amount; i++) {
            Location skelLoc = loc.clone().add(RANDOM.nextInt(5) - 2, 0, RANDOM.nextInt(5) - 2);
            world.spawn(skelLoc, org.bukkit.entity.Skeleton.class);
        }
    }

    // === Руды в 2 раза реже (выборочно "стираем" часть сгенерированных руд обратно в камень) ===

    private static final java.util.Set<Material> ORE_TYPES = java.util.Set.of(
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE
    );

    /** Выборочно проходит по чанку и с шансом убирает часть руды — тяжеловато, поэтому берём сэмпл координат. */
    public static void thinOresInChunk(org.bukkit.Chunk chunk) {
        org.bukkit.World world = chunk.getWorld();
        int baseX = chunk.getX() * 16;
        int baseZ = chunk.getZ() * 16;
        int minY = world.getMinHeight();
        int maxY = Math.min(world.getMaxHeight(), 64); // руды почти всегда ниже 64
        for (int i = 0; i < 60; i++) { // сэмпл из 60 случайных точек на чанк вместо полного перебора
            int x = baseX + RANDOM.nextInt(16);
            int z = baseZ + RANDOM.nextInt(16);
            int y = minY + RANDOM.nextInt(Math.max(1, maxY - minY));
            Block block = world.getBlockAt(x, y, z);
            if (ORE_TYPES.contains(block.getType()) && RANDOM.nextDouble() < ORE_THIN_CHANCE) {
                block.setType(block.getType().name().startsWith("DEEPSLATE") || block.getType() == Material.NETHER_GOLD_ORE
                        || block.getType() == Material.NETHER_QUARTZ_ORE
                        ? guessBaseBlock(block)
                        : Material.STONE);
            }
        }
    }

    private static Material guessBaseBlock(Block block) {
        if (block.getType() == Material.NETHER_GOLD_ORE || block.getType() == Material.NETHER_QUARTZ_ORE) {
            return Material.NETHERRACK;
        }
        return Material.DEEPSLATE;
    }

    // === Максвелл-кот (мем, очень редкий, взрывается как заряженный крипер) ===

    private static final String MAXWELL_KEY = "maxwell";

    public static void trySpawnMaxwell(org.bukkit.Chunk chunk) {
        if (RANDOM.nextDouble() > MAXWELL_SPAWN_CHANCE) {
            return;
        }
        org.bukkit.World world = chunk.getWorld();
        int x = chunk.getX() * 16 + RANDOM.nextInt(16);
        int z = chunk.getZ() * 16 + RANDOM.nextInt(16);
        int y = 5 + RANDOM.nextInt(45);
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() != Material.CAVE_AIR && block.getType() != Material.AIR) {
            return;
        }
        spawnMaxwellAt(block.getLocation().add(0.5, 0, 0.5));
    }

    public static void spawnMaxwellAt(Location loc) {
        var cat = loc.getWorld().spawn(loc, Cat.class);
        cat.setCustomName("§d§lMaxwell");
        cat.setCustomNameVisible(true);
        cat.getPersistentDataContainer().set(new NamespacedKey(NS, MAXWELL_KEY), PersistentDataType.BYTE, (byte) 1);
    }

    public static boolean isMaxwell(Cat cat) {
        return cat.getPersistentDataContainer().has(new NamespacedKey(NS, MAXWELL_KEY), PersistentDataType.BYTE);
    }

    public static void triggerMaxwellExplosion(Cat cat) {
        Location loc = cat.getLocation();
        cat.remove();
        loc.getWorld().createExplosion(loc, 6.0f, true, true);
    }

    // === Житель больше не торгует с обидчиком ===

    private static final NamespacedKey BANNED_TRADERS_KEY = new NamespacedKey(NS, "banned_traders");

    public static void banTraderFor(Villager villager, Player player) {
        String existing = villager.getPersistentDataContainer()
                .getOrDefault(BANNED_TRADERS_KEY, PersistentDataType.STRING, "");
        String uuid = player.getUniqueId().toString();
        if (!existing.contains(uuid)) {
            String updated = existing.isEmpty() ? uuid : existing + "," + uuid;
            villager.getPersistentDataContainer().set(BANNED_TRADERS_KEY, PersistentDataType.STRING, updated);
        }
    }

    public static boolean isBannedFromTrading(Villager villager, Player player) {
        String existing = villager.getPersistentDataContainer()
                .getOrDefault(BANNED_TRADERS_KEY, PersistentDataType.STRING, "");
        return existing.contains(player.getUniqueId().toString());
    }

    // === Головы иссушителей-скелетов ===

    public static void handleWitherSkeletonDeath(WitherSkeleton skeleton, org.bukkit.event.entity.EntityDeathEvent event) {
        if (RANDOM.nextDouble() < WITHER_SKULL_PROJECTILE_CHANCE) {
            Location loc = skeleton.getLocation().add(0, 1, 0);
            var skull = loc.getWorld().spawn(loc, org.bukkit.entity.WitherSkull.class);
            Vector randomDir = new Vector(RANDOM.nextDouble() * 2 - 1, 0.2, RANDOM.nextDouble() * 2 - 1).normalize();
            skull.setVelocity(randomDir.multiply(1.5));
        } else if (RANDOM.nextDouble() < WITHER_SKULL_DROP_CHANCE) {
            event.getDrops().add(new ItemStack(Material.WITHER_SKELETON_SKULL));
        }
    }

    // === Доп. лут с бафнутых мобов ===

    public static void rollExtraLoot(org.bukkit.event.entity.EntityDeathEvent event) {
        if (RANDOM.nextDouble() > EXTRA_LOOT_CHANCE) {
            return;
        }
        int bonusXp = 5 + RANDOM.nextInt(15);
        event.setDroppedExp(event.getDroppedExp() + bonusXp);
    }

    // === Зомби-босс ===

    private static final NamespacedKey ZOMBIE_BOSS_KEY = new NamespacedKey(NS, "zombie_boss");

    public static Zombie spawnZombieBoss(Location loc) {
        Zombie boss = loc.getWorld().spawn(loc, Zombie.class);
        boss.setCustomName("§4§lЗомби-Апокалипсис");
        boss.setCustomNameVisible(true);
        boss.getEquipment().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        boss.getEquipment().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
        boss.getEquipment().setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
        boss.getEquipment().setBoots(new ItemStack(Material.DIAMOND_BOOTS));
        boss.getEquipment().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));

        AttributeInstance healthAttr = boss.getAttribute(maxHealthAttribute());
        if (healthAttr != null) {
            healthAttr.setBaseValue(300.0);
            boss.setHealth(300.0);
        }
        AttributeInstance damageAttr = boss.getAttribute(Attribute.valueOf(attackDamageName()));
        if (damageAttr != null) {
            damageAttr.setBaseValue(damageAttr.getBaseValue() * 2.5);
        }
        boss.getPersistentDataContainer().set(ZOMBIE_BOSS_KEY, PersistentDataType.BYTE, (byte) 1);
        return boss;
    }

    private static String attackDamageName() {
        try {
            Attribute.valueOf("ATTACK_DAMAGE");
            return "ATTACK_DAMAGE";
        } catch (IllegalArgumentException ex) {
            return "GENERIC_ATTACK_DAMAGE";
        }
    }

    public static boolean isZombieBoss(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(ZOMBIE_BOSS_KEY, PersistentDataType.BYTE);
    }

    public static ItemStack rottenPotatoDrop() {
        ItemStack potato = new ItemStack(Material.POISONOUS_POTATO);
        ItemMeta meta = potato.getItemMeta();
        meta.setDisplayName("§2Гнилая картошка");
        potato.setItemMeta(meta);
        return potato;
    }
}
