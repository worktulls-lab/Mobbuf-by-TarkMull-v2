package com.example.mobbuff;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Spider;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Witch;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Random;

public final class MobBuffListener implements Listener {

    private final MobBuffPlugin plugin;
    private static final Random RANDOM = new Random();
    private static final EntityType[] HORDE_TYPES = {
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER
    };

    public MobBuffListener(MobBuffPlugin plugin) {
        this.plugin = plugin;
    }

    /** Применяет баф каждому новому мобу. Обычные пауки ночью иногда прячутся невидимками до атаки. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!plugin.isBuffEnabled()) return;
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) return;
        MobBuffUtil.applyBuff(entity, plugin);

        if (entity instanceof Spider && !(entity instanceof CaveSpider)
                && MobBuffUtil.isDark(entity.getLocation())
                && RANDOM.nextDouble() < MobBuffUtil.SPIDER_AMBUSH_CHANCE) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
        }
    }

    /** Урон мобов (per-mob потолки), яд от пауков, заражение от зомби, вода, фантом-камикадзе, крик о помощи. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!plugin.isBuffEnabled()) return;
        if (!(event.getDamager() instanceof LivingEntity damager) || damager instanceof Player) {
            handleMobCallForHelp(event);
            return;
        }

        double multiplier = MobBuffUtil.isDragon(damager)
                ? MobBuffUtil.DRAGON_MULTIPLIER
                : (MobBuffUtil.isBoss(damager)
                        ? MobBuffUtil.BOSS_MULTIPLIER
                        : MobBuffUtil.damageMultiplier(damager.getLocation(), plugin, MobBuffUtil.damageCapFor(damager)));
        if (damager.isInWater()) {
            multiplier *= MobBuffUtil.WATER_ATTACK_BONUS;
        }
        event.setDamage(event.getDamage() * multiplier);

        if (damager instanceof Spider && damager.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            damager.removePotionEffect(PotionEffectType.INVISIBILITY);
        }

        // Фантом бьёт и сразу взрывается (без урона блокам), умирает
        if (damager instanceof Phantom phantom && event.getEntity() instanceof Player) {
            Location loc = phantom.getLocation();
            new BukkitRunnable() {
                @Override
                public void run() {
                    phantom.remove();
                    loc.getWorld().createExplosion(loc, 1.5f, false, false);
                }
            }.runTaskLater(plugin, 1L);
        }

        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        if (damager instanceof Spider && !(damager instanceof CaveSpider)
                && RANDOM.nextDouble() < MobBuffUtil.SPIDER_POISON_CHANCE) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 0));
        }

        if (damager instanceof Zombie && RANDOM.nextDouble() < MobBuffUtil.ZOMBIE_INFECT_CHANCE) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0));
        }

        handleMobCallForHelp(event);
    }

    /** Раненые мобы с шансом зовут на помощь ближайших своих; удар по жителю банит игрока от торговли. */
    private void handleMobCallForHelp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (!(event.getDamager() instanceof Player player)) return;

        if (victim instanceof Villager villager) {
            MobBuffUtil.banTraderFor(villager, player);
        }

        if (!(victim instanceof Monster)) return;
        double healthAfter = victim.getHealth() - event.getFinalDamage();
        if (healthAfter <= 0) return; // умер, звать некого
        if (RANDOM.nextDouble() > MobBuffUtil.MOB_CALL_FOR_HELP_CHANCE) return;

        victim.getNearbyEntities(MobBuffUtil.MOB_CALL_FOR_HELP_RADIUS, MobBuffUtil.MOB_CALL_FOR_HELP_RADIUS, MobBuffUtil.MOB_CALL_FOR_HELP_RADIUS)
                .stream()
                .filter(e -> e.getClass().equals(victim.getClass()))
                .filter(e -> e instanceof org.bukkit.entity.Mob mob && mob.getTarget() == null)
                .limit(3)
                .forEach(e -> ((org.bukkit.entity.Mob) e).setTarget(player));
    }

    /** Зомби и скелеты не горят, пока стоят в воде. */
    @EventHandler(ignoreCancelled = true)
    public void onEntityCombust(EntityCombustEvent event) {
        if (!plugin.isBuffEnabled()) return;
        if (event.getEntity() instanceof LivingEntity le && (le instanceof Zombie || le instanceof Skeleton)) {
            if (le.isInWater()) {
                event.setCancelled(true);
            }
        }
    }

    /** Скелеты стреляют точнее/быстрее и слегка отступают в укрытие после выстрела. */
    @EventHandler(ignoreCancelled = true)
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!plugin.isBuffEnabled()) return;
        if (!(event.getEntity() instanceof Skeleton skeleton)) return;
        if (!(event.getProjectile() instanceof Projectile projectile)) return;

        LivingEntity target = skeleton.getTarget();
        if (target != null) {
            Vector direction = target.getEyeLocation().subtract(skeleton.getEyeLocation()).toVector().normalize();
            double speed = projectile.getVelocity().length() * 1.3;
            projectile.setVelocity(direction.multiply(speed));
        } else {
            projectile.setVelocity(projectile.getVelocity().multiply(1.3));
        }

        // Упрощённая имитация "поиска укрытия": лёгкий шаг в сторону после выстрела
        if (target != null) {
            Vector dir = skeleton.getLocation().getDirection();
            Vector side = new Vector(-dir.getZ(), 0, dir.getX());
            if (side.lengthSquared() > 0) {
                skeleton.setVelocity(skeleton.getVelocity().add(side.normalize().multiply(0.3)));
            }
        }
    }

    /** Ведьмы кидают зелья точнее. */
    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!plugin.isBuffEnabled()) return;
        if (!(event.getEntity() instanceof ThrownPotion potion)) return;
        if (!(potion.getShooter() instanceof Witch witch)) return;
        LivingEntity target = witch.getTarget();
        if (target == null) return;
        Vector direction = target.getEyeLocation().subtract(witch.getEyeLocation()).toVector().normalize();
        double speed = potion.getVelocity().length() * 1.25;
        potion.setVelocity(direction.multiply(speed));
    }

    /** Файерболы гастов создают взрывную волну; стрелы скелетов поджигают в Нижнем мире. */
    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!plugin.isBuffEnabled()) return;
        Projectile projectile = event.getEntity();

        if (projectile instanceof Fireball fireball && fireball.getShooter() instanceof Ghast) {
            Location loc = projectile.getLocation();
            loc.getWorld().getNearbyEntities(loc, MobBuffUtil.GHAST_SHOCKWAVE_RADIUS, MobBuffUtil.GHAST_SHOCKWAVE_RADIUS, MobBuffUtil.GHAST_SHOCKWAVE_RADIUS)
                    .stream().filter(e -> e instanceof Player)
                    .forEach(e -> {
                        Vector push = e.getLocation().toVector().subtract(loc.toVector());
                        if (push.lengthSquared() < 0.01) push = new Vector(0, 1, 0);
                        push.normalize().multiply(MobBuffUtil.GHAST_SHOCKWAVE_STRENGTH);
                        e.setVelocity(e.getVelocity().add(push.setY(Math.max(0.3, push.getY()))));
                    });
        }

        if (projectile instanceof org.bukkit.entity.Arrow arrow
                && arrow.getShooter() instanceof Skeleton
                && arrow.getWorld().getEnvironment() == World.Environment.NETHER
                && event.getHitEntity() instanceof LivingEntity victim) {
            victim.setFireTicks(100);
        }
    }

    /** Считает килы (любые мобы), зомби-счётчик для босса, головы визер-скелетов, доп. лут, урезает опыт. */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!plugin.isBuffEnabled()) return;
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) return;

        if (entity.getKiller() != null) {
            plugin.registerHostileKill();
            if (entity instanceof Zombie && !MobBuffUtil.isZombieBoss(entity)) {
                plugin.registerZombieKillForBoss(entity.getLocation());
            }
        }

        if (entity instanceof WitherSkeleton witherSkeleton) {
            MobBuffUtil.handleWitherSkeletonDeath(witherSkeleton, event);
        }

        if (MobBuffUtil.isZombieBoss(entity)) {
            event.getDrops().clear();
            event.getDrops().add(MobBuffUtil.rottenPotatoDrop());
        } else if (MobBuffUtil.isBuffed(entity)) {
            MobBuffUtil.rollExtraLoot(event);
        }

        event.setDroppedExp((int) Math.round(event.getDroppedExp() * (1.0 - MobBuffUtil.XP_REDUCTION)));
    }

    /** Через 5-10 секунд после смерти игрока НОЧЬЮ рядом (не на самом месте) появляется орда поменьше. */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!plugin.isBuffEnabled()) return;
        Location deathLoc = event.getEntity().getLocation().clone();
        World world = deathLoc.getWorld();
        if (world == null) return;
        long time = world.getTime();
        boolean isNight = time >= 13000 && time < 23000;
        if (!isNight) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                int amount = 2 + RANDOM.nextInt(2); // 2-3 мобов
                for (int i = 0; i < amount; i++) {
                    int dx = (5 + RANDOM.nextInt(6)) * (RANDOM.nextBoolean() ? 1 : -1);
                    int dz = (5 + RANDOM.nextInt(6)) * (RANDOM.nextBoolean() ? 1 : -1);
                    Location spawnLoc = deathLoc.clone().add(dx, 0, dz);
                    EntityType type = HORDE_TYPES[RANDOM.nextInt(HORDE_TYPES.length)];
                    world.spawnEntity(spawnLoc, type);
                }
            }
        }.runTaskLater(plugin, 100L + RANDOM.nextInt(100));
    }

    /** Ловушки в пещерах, руды пореже, деревенский мародёр, Максвелл-кот. */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!plugin.isBuffEnabled() || !event.isNewChunk()) return;
        MobBuffUtil.tryPlaceCaveTrap(event.getChunk());
        MobBuffUtil.thinOresInChunk(event.getChunk());
        MobBuffUtil.trySpawnMaxwell(event.getChunk());
        tryVillagePillager(event.getChunk());
    }

    private void tryVillagePillager(org.bukkit.Chunk chunk) {
        for (var blockState : chunk.getTileEntities()) {
            if (blockState.getType() == Material.BELL) {
                Location bellLoc = blockState.getLocation();
                boolean alreadyGuarded = !bellLoc.getWorld()
                        .getNearbyEntities(bellLoc, MobBuffUtil.VILLAGE_PILLAGER_CHECK_RADIUS, 10,
                                MobBuffUtil.VILLAGE_PILLAGER_CHECK_RADIUS)
                        .stream().filter(e -> e.getType() == EntityType.PILLAGER).toList().isEmpty();
                if (!alreadyGuarded) {
                    bellLoc.getWorld().spawnEntity(bellLoc.clone().add(2, 1, 2), EntityType.PILLAGER);
                }
                return;
            }
        }
    }

    /** В Энде нельзя ставить кровати вообще. */
    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.isBuffEnabled()) return;
        if (event.getBlock().getWorld().getEnvironment() != World.Environment.THE_END) return;
        if (event.getBlock().getType().name().endsWith("_BED")) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(net.md_5.bungee.api.chat.TextComponent.fromLegacyText(
                    net.md_5.bungee.api.ChatColor.RED + "В Энде нельзя ставить кровати."));
        }
    }

    /** Максвелл взрывается при ударе игроком. */
    @EventHandler(ignoreCancelled = true)
    public void onMaxwellHit(EntityDamageByEntityEvent event) {
        if (!plugin.isBuffEnabled()) return;
        if (event.getEntity() instanceof org.bukkit.entity.Cat cat && MobBuffUtil.isMaxwell(cat)
                && event.getDamager() instanceof Player) {
            MobBuffUtil.triggerMaxwellExplosion(cat);
        }
    }

    /** Забаненный игрок не может открыть торговлю с жителем. */
    @EventHandler(ignoreCancelled = true)
    public void onInteractVillager(PlayerInteractEntityEvent event) {
        if (!plugin.isBuffEnabled()) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getRightClicked() instanceof Villager villager
                && MobBuffUtil.isBannedFromTrading(villager, event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** Кулдаун на золотые яблоки/зелья и щит. */
    @EventHandler
    public void onItemConsume(PlayerItemConsumeEvent event) {
        if (!plugin.isBuffEnabled()) return;
        Material type = event.getItem().getType();
        Player player = event.getPlayer();
        if (type == Material.GOLDEN_APPLE || type == Material.ENCHANTED_GOLDEN_APPLE) {
            player.setCooldown(type, MobBuffUtil.GOLDEN_APPLE_COOLDOWN_TICKS);
        } else if (type.name().contains("POTION")) {
            player.setCooldown(type, MobBuffUtil.POTION_COOLDOWN_TICKS);
        }
    }

    @EventHandler
    public void onShieldRaise(PlayerInteractEvent event) {
        if (!plugin.isBuffEnabled()) return;
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.SHIELD) return;
        if (event.getAction().name().startsWith("RIGHT_CLICK")) {
            event.getPlayer().setCooldown(Material.SHIELD, MobBuffUtil.SHIELD_COOLDOWN_TICKS);
        }
    }

    /** Броня изнашивается в 5 раз быстрее, инструменты/оружие в 3, щит — без изменений. */
    @EventHandler
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (!plugin.isBuffEnabled()) return;
        if (event.getItem().getType() == Material.SHIELD) return;
        String name = event.getItem().getType().name();
        boolean isArmor = name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
        double mult = isArmor ? MobBuffUtil.ARMOR_DURABILITY_MULTIPLIER : MobBuffUtil.TOOL_DURABILITY_MULTIPLIER;
        event.setDamage((int) Math.round(event.getDamage() * mult));
    }

    /** Мобы идут в последнюю известную точку игрока, если потеряли его из виду. */
    @EventHandler(ignoreCancelled = true)
    public void onEntityTarget(EntityTargetEvent event) {
        if (!plugin.isBuffEnabled()) return;
        if (!(event.getEntity() instanceof org.bukkit.entity.Mob mob)) return;
        if (event.getReason() == EntityTargetEvent.TargetReason.FORGOT_TARGET) {
            Location last = plugin.getLastKnownLocation(mob.getUniqueId());
            if (last != null) {
                mob.getPathfinder().moveTo(last, 1.0);
            }
        }
    }
}
