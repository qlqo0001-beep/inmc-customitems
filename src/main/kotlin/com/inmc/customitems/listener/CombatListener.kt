package com.inmc.customitems.listener

import com.inmc.customitems.CustomItems
import com.inmc.customitems.ability.AbilityEngine
import com.inmc.customitems.ability.Trigger
import com.inmc.customitems.item.AttackStyle
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.Stat
import com.inmc.customitems.player.StatService
import kr.inmc.core.util.Text
import org.bukkit.Particle
import org.bukkit.Tag
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.Random

/**
 * 우리가 직접 계산하는 능력치와 전투 기능.
 *
 * 바닐라 속성(공격력·방어력…)은 여기 없다 — 그건 아이템에 붙은 `AttributeModifier` 가
 * 알아서 한다. 여기 있는 것은 **바닐라가 모르는 것들**이다.
 *
 * 숫자는 한 사람이 입고 든 것 **전부의 합**이다([StatService]) — 흉갑의 치명타도 칼의 치명타에 더해진다.
 *
 * **`HIGH` 우선순위에서 피해를 고친다.** 다른 플러그인(월드가드 등)이 이벤트를 취소할 기회를
 * 먼저 주고, 그 뒤에 숫자를 만진다. `MONITOR` 에서 고치면 안 된다 — 거기서 바꾼 값은
 * 반영되지 않는다는 것이 Bukkit 의 계약이다.
 */
class CombatListener(private val custom: CustomItems) : Listener {

    private val random = Random()

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onDamage(event: EntityDamageByEntityEvent) {
        val victim = event.entity as? LivingEntity ?: return
        // 기능이 준 피해(기능이 쏜 발사체 포함)는 무기 공격이 아니다 — 능력치·적중 기능을 또 태우면 기능이
        // 자기를 부른다. 맞는 쪽의 방어는 그대로 먹는다.
        val skill = AbilityEngine.skillDamage(event.damager)
        if (skill != null || custom.abilities.casting || custom.styles.splashing) {
            if (skill != null) event.damage = skill
            if (victim is Player) applyDefence(event, victim)
            return
        }
        // 요구 조건이 모자란 무기로는 못 때린다(맨손으로 치는 것까지 막지는 않는다 — 바닐라 공격력은 이미 무기 것이다).
        val striker = event.damager as? Player
        val weapon = striker?.let { custom.items.usable(it.inventory.itemInMainHand) }
        if (striker != null && weapon != null && !custom.requirements.check(striker, weapon)) {
            event.isCancelled = true
            return
        }
        attacker(event)?.let { attacker -> applyOffence(event, attacker, victim) }
        if (victim is Player) applyDefence(event, victim)
        projectileHit(event, victim)
    }

    /** 우리 활·삼지창으로 쏜 것이 맞았다 — 그 아이템의 발사체 적중 기능. */
    private fun projectileHit(event: EntityDamageByEntityEvent, victim: LivingEntity) {
        val projectile = event.damager as? Projectile ?: return
        val shooter = projectile.shooter as? Player ?: return
        val id = projectile.persistentDataContainer.get(AbilityEngine.SOURCE, org.bukkit.persistence.PersistentDataType.STRING) ?: return
        val item = custom.items.get(id) ?: return
        custom.abilities.fire(shooter, item, Trigger.PROJECTILE_HIT, victim)
    }

    /**
     * 때린 쪽.
     *
     * 순서가 의미를 갖는다 — **추가 피해·원소를 먼저 더하고, 비율(몹·플레이어·발사체·언데드)을 곱하고,
     * 치명타 배수를 마지막에 곱한다.** 반대로 하면 추가 피해가 치명타를 타지 않아 "치명타 2배"가 말과
     * 다른 값이 된다.
     */
    private fun applyOffence(event: EntityDamageByEntityEvent, attacker: Player, victim: LivingEntity) {
        val stats = custom.stats.of(attacker)
        var damage = event.damage + stats.stat(Stat.DAMAGE_BONUS)
        damage += elements(stats, attacker, victim)

        var percent = 0.0
        percent += if (victim is Player) stats.stat(Stat.PVP_DAMAGE) else stats.stat(Stat.PVE_DAMAGE)
        if (event.damager is Projectile) percent += stats.stat(Stat.PROJECTILE_DAMAGE)
        if (Tag.ENTITY_TYPES_UNDEAD.isTagged(victim.type)) percent += stats.stat(Stat.UNDEAD_DAMAGE)
        damage *= (1.0 + percent / 100.0).coerceAtLeast(0.0)

        // 공격 방식은 손에 든 무기로 **직접** 친 때만. 화살이 단검 뒤치기를 타면 안 된다.
        val weapon = custom.items.usable(attacker.inventory.itemInMainHand)
        val style = if (event.damager === attacker) weapon?.style ?: AttackStyle.NONE else AttackStyle.NONE
        if (style == AttackStyle.DAGGER && AttackStyle.isBehind(victim.location.direction, attacker.location.direction)) {
            damage *= AttackStyle.BACKSTAB_MULTIPLIER
            victim.world.spawnParticle(Particle.DAMAGE_INDICATOR, victim.location.add(0.0, 1.2, 0.0), 6, 0.2, 0.2, 0.2, 0.1)
        }

        val critChance = stats.stat(Stat.CRIT_CHANCE)
        if (critChance > 0.0 && random.nextDouble() * 100.0 < critChance) {
            // 배수를 안 적었으면 2배. 0 을 곱해 피해가 사라지는 것보다 낫다.
            damage *= (stats.stat(Stat.CRIT_DAMAGE).takeIf { it > 0.0 } ?: 2.0) + stats.stat(Stat.CRIT_POWER) / 100.0
            attacker.sendActionBar(Text.render("<red>✦ 치명타 <white>" + format(damage) + "</white></red>"))
        }

        if (damage != event.damage) event.damage = damage

        val lifesteal = stats.stat(Stat.LIFESTEAL)
        if (lifesteal > 0.0) heal(attacker, damage * lifesteal / 100.0)

        custom.styles.afterHit(style, attacker, victim, damage)
        fireCarried(attacker, weapon, Trigger.ON_HIT, victim)
    }

    /**
     * 원소 피해. 각자 고정값을 더하고 부가 효과를 건다. 맞는 쪽이 플레이어면 그 원소의 저항만큼 줄인다.
     * 몹은 저항이 없다.
     */
    private fun elements(stats: StatService.Snapshot, attacker: Player, victim: LivingEntity): Double {
        val resist = (victim as? Player)?.let { custom.stats.of(it) }
        var total = 0.0
        for ((damageStat, resistStat) in ELEMENTS) {
            val raw = stats.stat(damageStat)
            if (raw <= 0.0) continue
            val cut = (resist?.stat(resistStat) ?: 0.0).coerceIn(0.0, 100.0)
            val dealt = raw * (1.0 - cut / 100.0)
            if (dealt <= 0.0) continue
            total += dealt
            when (damageStat) {
                Stat.FIRE_DAMAGE -> victim.fireTicks = maxOf(victim.fireTicks, 40)
                Stat.ICE_DAMAGE -> victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 30, 1))
                Stat.LIGHTNING_DAMAGE -> victim.world.spawnParticle(Particle.ELECTRIC_SPARK, victim.location.add(0.0, 1.0, 0.0), 12, 0.3, 0.5, 0.3, 0.05)
                Stat.POISON_DAMAGE -> victim.addPotionEffect(PotionEffect(PotionEffectType.POISON, 40, 0))
                else -> Unit
            }
        }
        if (total > 0.0) attacker.world.spawnParticle(Particle.CRIT, victim.location.add(0.0, 1.0, 0.0), 6, 0.2, 0.3, 0.2, 0.0)
        return total
    }

    /**
     * 맞은 쪽. 회피 → 감소(합산 80% 까지) → 막기 → 가시 → 피격 기능.
     *
     * 회피는 사건을 통째로 취소한다 — 넉백도 없다. "피했다"는 말과 맞는 모습이다.
     */
    private fun applyDefence(event: EntityDamageByEntityEvent, victim: Player) {
        val stats = custom.stats.of(victim)
        if (stats.equipped.isEmpty()) return

        val dodge = stats.stat(Stat.DODGE_CHANCE)
        if (dodge > 0.0 && random.nextDouble() * 100.0 < dodge) {
            event.isCancelled = true
            victim.sendActionBar(Text.render("<aqua>회피!</aqua>"))
            return
        }

        val attacker = (event.damager as? Projectile)?.shooter as? LivingEntity ?: event.damager as? LivingEntity
        var reduction = stats.stat(Stat.DAMAGE_REDUCTION)
        reduction += if (attacker is Player) stats.stat(Stat.PVP_DEFENSE) else stats.stat(Stat.PVE_DEFENSE)
        if (event.damager is Projectile) reduction += stats.stat(Stat.PROJECTILE_DEFENSE)
        reduction = reduction.coerceIn(0.0, MAX_REDUCTION)
        if (reduction > 0.0) event.damage = event.damage * (1.0 - reduction / 100.0)
        // 방어 점수는 %와 따로 곱한다 — 점수는 커질수록 덜 먹는 곡선이라 80% 상한 안에 넣으면 뜻이 달라진다.
        val defense = stats.stat(Stat.DEFENSE_POINTS)
        if (defense > 0.0) event.damage = event.damage * 100.0 / (100.0 + defense)

        val block = stats.stat(Stat.BLOCK_CHANCE)
        if (block > 0.0 && random.nextDouble() * 100.0 < block) {
            val power = stats.stat(Stat.BLOCK_POWER).takeIf { it > 0.0 } ?: DEFAULT_BLOCK_POWER
            event.damage = event.damage * (1.0 - power.coerceIn(0.0, 100.0) / 100.0)
            victim.sendActionBar(Text.render("<gray>막았다 <white>-" + format(power) + "%</white></gray>"))
        }

        val thorns = stats.stat(Stat.THORNS)
        if (thorns > 0.0) attacker?.damage(thorns, victim)

        for (item in stats.equipped) custom.abilities.fire(victim, item.definition, Trigger.WHEN_DAMAGED, attacker)
    }

    /** 떨어짐·불. 누가 때린 것이 아닌 피해. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEnvironment(event: EntityDamageEvent) {
        if (event is EntityDamageByEntityEvent) return
        val victim = event.entity as? Player ?: return
        val stat = when (event.cause) {
            EntityDamageEvent.DamageCause.FALL -> Stat.FALL_DEFENSE
            EntityDamageEvent.DamageCause.FIRE, EntityDamageEvent.DamageCause.FIRE_TICK,
            EntityDamageEvent.DamageCause.LAVA, EntityDamageEvent.DamageCause.HOT_FLOOR -> Stat.FIRE_DEFENSE
            else -> return
        }
        val cut = custom.stats.of(victim).stat(stat).coerceIn(0.0, 100.0)
        if (cut > 0.0) event.damage = event.damage * (1.0 - cut / 100.0)
    }

    /** 요구 조건이 모자란 활·석궁은 쏘지 못한다. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onShoot(event: EntityShootBowEvent) {
        val shooter = event.entity as? Player ?: return
        val bow = custom.items.usable(event.bow) ?: return
        if (!custom.requirements.check(shooter, bow)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onKill(event: EntityDeathEvent) {
        val killer = event.entity.killer ?: return
        fireCarried(killer, custom.items.usable(killer.inventory.itemInMainHand), Trigger.ON_KILL, event.entity)
    }

    /**
     * 손에 든 무기와, 장착 칸·가방의 부적·유물·장신구. 뒤의 것은 **어느 무기로 쳐도** 돈다 — 거기서 효과를 내는 것이
     * 그 종류의 뜻이다(MMOItems 의 장신구와 같다). 손에 든 부적은 가방째 세는 쪽에서 한 번만 돈다.
     */
    private fun fireCarried(player: Player, weapon: CustomItem?, trigger: Trigger, other: LivingEntity) {
        if (!custom.items.hasTrigger(trigger)) return
        if (weapon != null && !weapon.type.carried && custom.equipmentSettings.worksOutside(weapon)) custom.abilities.fire(player, weapon, trigger, other)
        for (item in custom.stats.of(player).equipped) if (item.slot == null) custom.abilities.fire(player, item.definition, trigger, other)
    }

    // --- 도우미 ---------------------------------------------------------------------

    /**
     * 실제로 때린 플레이어. 화살이면 쏜 사람.
     *
     * 발사체를 빼먹으면 활에 붙인 능력치가 전부 죽는다.
     */
    private fun attacker(event: EntityDamageByEntityEvent): Player? {
        (event.damager as? Player)?.let { return it }
        val projectile = event.damager as? Projectile ?: return null
        return projectile.shooter as? Player
    }

    private fun heal(player: Player, amount: Double) {
        if (amount <= 0.0) return
        val max = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
        player.health = (player.health + amount).coerceIn(0.0, max)
    }

    private fun format(value: Double): String = String.format("%.1f", value)

    private companion object {
        /**
         * 피해 감소 합산 상한(%).
         *
         * 100 을 허용하면 **무적**이 만들어진다. 관리자가 네 부위에 30%씩 붙이는 것은
         * 흔한 실수이고, 그 결과가 죽지 않는 플레이어면 안 된다.
         */
        const val MAX_REDUCTION = 80.0

        /** 막기 위력을 안 적었으면 절반. */
        const val DEFAULT_BLOCK_POWER = 50.0

        val ELEMENTS = listOf(
            Stat.FIRE_DAMAGE to Stat.FIRE_RESIST,
            Stat.ICE_DAMAGE to Stat.ICE_RESIST,
            Stat.LIGHTNING_DAMAGE to Stat.LIGHTNING_RESIST,
            Stat.POISON_DAMAGE to Stat.POISON_RESIST,
        )
    }
}
