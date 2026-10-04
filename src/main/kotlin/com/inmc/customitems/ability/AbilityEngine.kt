package com.inmc.customitems.ability

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.Registries
import kr.inmc.core.util.Text
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.NamespacedKey
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Arrow
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.SmallFireball
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.Random
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 기능을 실제로 터뜨린다.
 *
 * **효과 하나하나가 여기 한 줄씩이다.** 효과마다 클래스를 만들면 파일이 열 개가 되는데,
 * 각각이 대여섯 줄짜리라 그 분할이 얻는 것이 없다. 새 효과를 더할 때 고칠 곳은
 * [EffectType] 의 목록과 이 `when` 둘뿐이고, 컴파일러가 빠뜨린 쪽을 잡아준다.
 *
 * 전부 **메인 스레드**에서 돈다 — 엔티티를 만지기 때문이다.
 */
class AbilityEngine(private val custom: CustomItems) {

    private val random = Random()
    private val cooldowns = Cooldowns()

    /**
     * 이 아이템의 이 발동 조건에 걸린 기능을 전부 시도한다.
     *
     * @param other 상대. 적중·피격이면 그 엔티티, 아니면 null.
     * @return 하나라도 터졌으면 true. 부르는 쪽이 이벤트를 취소할지 정한다.
     */
    fun fire(
        player: Player,
        definition: CustomItem,
        trigger: Trigger,
        other: LivingEntity? = null,
    ): Boolean {
        var fired = false
        for ((index, ability) in definition.abilities.withIndex()) {
            if (ability.trigger != trigger) continue
            if (!roll(ability)) continue
            if (!cooldowns.tryUse(player.uniqueId, definition.id, index, cooldown(player, ability))) {
                // 쿨다운 중. 우클릭처럼 사람이 일부러 누른 것만 알려준다 — 적중마다 알리면
                // 전투 중에 액션바가 쿨다운 안내로 도배된다.
                if (trigger == Trigger.RIGHT_CLICK || trigger == Trigger.LEFT_CLICK) {
                    val left = cooldowns.remaining(player.uniqueId, definition.id, index)
                    player.sendActionBar(
                        Text.render("<red>재사용까지 " + String.format("%.1f", left / 1000.0) + "초</red>"),
                    )
                }
                continue
            }
            run(player, ability, other)
            fired = true
        }
        return fired
    }

    /** 입고 든 것 전부(요구 조건을 채운 것만)에서 이 발동 조건을 시도한다. 이 발동 조건을 가진 아이템이 없으면 바로 끝. */
    fun fireEquipped(player: Player, trigger: Trigger, other: LivingEntity? = null) {
        if (!custom.items.hasTrigger(trigger)) return
        for (item in custom.stats.of(player).equipped) fire(player, item.definition, trigger, other)
    }

    /** [Trigger.PASSIVE] 는 쿨다운이 아니라 주기로 돈다. 틱커가 부른다. */
    fun firePassive(player: Player, definition: CustomItem, now: Long) {
        for ((index, ability) in definition.abilities.withIndex()) {
            if (ability.trigger != Trigger.PASSIVE) continue
            val period = (ability.intervalSeconds * 1000L).toLong().coerceAtLeast(500L)
            if (!cooldowns.tryUse(player.uniqueId, definition.id, index, period / 1000.0, now)) continue
            if (!roll(ability)) continue
            run(player, ability, null)
        }
    }

    fun forget(id: UUID) = cooldowns.forget(id)

    /** 재사용 대기 감소를 먹인 대기. 80% 넘게는 못 줄인다 — 0 이 되면 연타가 무너뜨린다. */
    private fun cooldown(player: Player, ability: Ability): Double {
        if (ability.cooldownSeconds <= 0.0) return 0.0
        val cut = custom.stats.of(player).stat(com.inmc.customitems.item.Stat.COOLDOWN_REDUCTION).coerceIn(0.0, 80.0)
        return ability.cooldownSeconds * (1.0 - cut / 100.0)
    }

    private fun roll(ability: Ability): Boolean =
        ability.chance >= 100.0 || random.nextDouble() * 100.0 < ability.chance

    // --- 효과 -----------------------------------------------------------------------

    /**
     * 지금 기능이 도는 중인가. 기능이 준 피해는 [com.inmc.customitems.listener.CombatListener] 가 **무기 공격으로
     * 치지 않는다** — 치면 적중 기능이 또 돌아, 확률 100%·대기 없는 적중 피해가 끝없이 자기를 부른다.
     * 메인 스레드에서만 돈다.
     */
    var casting: Boolean = false
        private set

    private fun run(player: Player, ability: Ability, other: LivingEntity?) {
        val before = casting
        casting = true
        try {
            effect(player, ability, other)
        } finally {
            casting = before
        }
    }

    private fun effect(player: Player, ability: Ability, other: LivingEntity?) {
        // 떨어질 곳·폭발 중심은 상대가 없으면 바라보는 블록으로 — 우클릭 운석이 된다.
        if ((ability.effect == EffectType.METEOR || ability.effect == EffectType.EXPLOSION) && ability.target() == Target.OTHER && other == null) {
            val block = player.getTargetBlockExact(RANGE) ?: return
            area(player, ability, block.location.add(0.5, 1.0, 0.5))
            return
        }
        val target = when (ability.target()) {
            Target.SELF -> player
            // 상대가 없으면 **아무 일도 안 한다.** 자신에게 돌리면 우클릭 한 번이 자해가 된다.
            Target.OTHER -> other ?: return
        }
        val players = ability.int("players") > 0

        when (ability.effect) {
            EffectType.POTION -> {
                val type = potion(ability.value("effect")) ?: return
                target.addPotionEffect(
                    PotionEffect(
                        type,
                        passivePotionTicks(ability.trigger, ability.number("seconds"), ability.intervalSeconds),
                        (ability.int("level") - 1).coerceAtLeast(0),
                    ),
                )
            }

            EffectType.DAMAGE -> hurt(target, ability.number("amount"), player)

            EffectType.HEAL -> heal(target, ability.number("amount"))

            EffectType.IGNITE -> target.fireTicks = (ability.number("seconds") * 20).toInt()

            EffectType.LIGHTNING -> {
                val where = target.location
                if (ability.int("damage") > 0) {
                    where.world.strikeLightning(where)
                } else {
                    where.world.strikeLightningEffect(where)
                }
            }

            EffectType.LAUNCH -> {
                val direction = player.location.direction.normalize()
                target.velocity = direction.multiply(ability.number("power"))
                    .setY(ability.number("up").coerceAtLeast(0.0))
            }

            EffectType.TELEPORT -> teleport(player, ability.number("distance"))

            EffectType.SOUND -> {
                val sound = sound(ability.value("sound")) ?: return
                player.world.playSound(
                    target.location,
                    sound,
                    ability.number("volume").toFloat(),
                    ability.number("pitch").toFloat(),
                )
            }

            EffectType.PARTICLE -> {
                val particle = particle(ability.value("particle")) ?: return
                val spread = ability.number("spread")
                target.world.spawnParticle(
                    particle,
                    target.location.add(0.0, 1.0, 0.0),
                    ability.int("count"),
                    spread, spread, spread,
                    0.0,
                )
            }

            EffectType.COMMAND -> {
                val line = ability.value("command").trim().removePrefix("/")
                    .replace("{player}", player.name)
                    .replace("{플레이어}", player.name)
                if (line.isBlank()) return
                val sender = if (ability.int("console") > 0) Bukkit.getConsoleSender() else player
                runCatching { Bukkit.dispatchCommand(sender, line) }
                    .onFailure { custom.logger.warning("기능 명령어 실패: " + line + " - " + it.message) }
            }

            EffectType.MESSAGE -> {
                val text = ability.value("text").replace("{player}", player.name)
                if (text.isBlank()) return
                val viewer = target as? Player ?: player
                if (ability.int("actionbar") > 0) {
                    viewer.sendActionBar(Text.render(text))
                } else {
                    viewer.sendMessage(Text.render(text))
                }
            }

            EffectType.FIREBALL -> {
                val ball = player.launchProjectile(SmallFireball::class.java, player.location.direction.multiply(ability.number("speed")))
                ball.setIsIncendiary(false)
                ball.yield = 0f
                tag(ball, ability.number("damage") * boost(player))
            }

            EffectType.ARROW_VOLLEY -> {
                val spread = ability.number("spread")
                repeat(ability.int("count").coerceIn(1, 50)) {
                    val direction = player.location.clone().apply {
                        yaw += ((random.nextDouble() * 2 - 1) * spread).toFloat()
                        pitch += ((random.nextDouble() * 2 - 1) * spread / 2).toFloat()
                    }.direction.multiply(ability.number("speed"))
                    val arrow = player.launchProjectile(Arrow::class.java, direction)
                    arrow.pickupStatus = AbstractArrow.PickupStatus.DISALLOWED
                    arrow.lifetimeTicks = 1100 // 100틱 뒤 사라진다(1200 에 없어진다)
                    tag(arrow, ability.number("damage") * boost(player))
                }
            }

            EffectType.SHOCKWAVE -> {
                val center = player.location
                center.world.spawnParticle(Particle.EXPLOSION, center, 1)
                center.world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f)
                for (entity in around(center, ability.number("radius"), player, players)) {
                    hurt(entity, ability.number("damage"), player)
                    entity.velocity = entity.location.toVector().subtract(center.toVector()).normalize().multiply(ability.number("power")).setY(0.35)
                }
            }

            EffectType.AOE_DAMAGE -> {
                val center = target.location
                center.world.spawnParticle(Particle.SWEEP_ATTACK, center.clone().add(0.0, 1.0, 0.0), 6, 1.0, 0.3, 1.0, 0.0)
                for (entity in around(center, ability.number("radius"), player, players)) hurt(entity, ability.number("damage"), player)
            }

            EffectType.AOE_POTION -> {
                val type = potion(ability.value("effect")) ?: return
                val effect = PotionEffect(type, (ability.number("seconds") * 20).toInt().coerceAtLeast(1), (ability.int("level") - 1).coerceAtLeast(0))
                player.world.spawnParticle(Particle.WITCH, player.location.add(0.0, 1.0, 0.0), 30, ability.number("radius") / 2, 0.5, ability.number("radius") / 2, 0.0)
                for (entity in around(player.location, ability.number("radius"), player, players)) entity.addPotionEffect(effect)
            }

            EffectType.PULL -> {
                val center = player.location
                for (entity in around(center, ability.number("radius"), player, players)) {
                    entity.velocity = center.toVector().subtract(entity.location.toVector()).normalize().multiply(ability.number("power")).setY(0.3)
                }
            }

            EffectType.KNOCKBACK -> {
                val away = target.location.toVector().subtract(player.location.toVector())
                val direction = if (away.lengthSquared() < 1e-6) player.location.direction else away.normalize()
                target.velocity = direction.multiply(ability.number("power")).setY(ability.number("up"))
            }

            EffectType.FREEZE -> {
                val ticks = (ability.number("seconds") * 20).toInt().coerceAtLeast(1)
                target.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, ticks, (ability.int("level") - 1).coerceAtLeast(0)))
                // 서리 화면만 보이게 한다. 140 에 닿으면 바닐라가 얼음 피해를 준다.
                target.freezeTicks = minOf(target.maxFreezeTicks - 1, target.freezeTicks + ticks)
                target.world.spawnParticle(Particle.SNOWFLAKE, target.location.add(0.0, 1.0, 0.0), 20, 0.4, 0.6, 0.4, 0.02)
            }

            EffectType.EXPLOSION, EffectType.METEOR -> area(player, ability, target.location)

            EffectType.BEAM -> {
                val particle = particle(ability.value("particle")) ?: Particle.END_ROD
                val start = player.eyeLocation
                val step = start.direction.multiply(0.5)
                val point = start.clone()
                val hit = HashSet<LivingEntity>()
                repeat((ability.number("length") * 2).toInt()) {
                    point.add(step)
                    if (!point.block.isPassable) return
                    point.world.spawnParticle(particle, point, 1, 0.0, 0.0, 0.0, 0.0)
                    for (entity in around(point, 1.0, player, players)) if (hit.add(entity)) hurt(entity, ability.number("damage"), player)
                }
            }

            EffectType.CHAIN_LIGHTNING -> {
                val range = ability.number("range")
                var current: LivingEntity = other ?: around(player.location, range, player, players).minByOrNull { it.location.distanceSquared(player.location) } ?: return
                val hit = HashSet<LivingEntity>()
                repeat(ability.int("jumps").coerceIn(1, 20)) {
                    hit += current
                    current.world.strikeLightningEffect(current.location)
                    hurt(current, ability.number("damage"), player)
                    current = around(current.location, range, player, players).filter { it !in hit }.minByOrNull { it.location.distanceSquared(current.location) } ?: return
                }
            }

            EffectType.HEAL_AREA -> {
                val center = player.location
                center.world.spawnParticle(Particle.HEART, center.clone().add(0.0, 1.5, 0.0), 8, 1.5, 0.5, 1.5, 0.0)
                heal(player, ability.number("amount"))
                for (entity in center.world.getNearbyPlayers(center, ability.number("radius"))) if (entity != player) heal(entity, ability.number("amount"))
            }

            EffectType.DRAIN -> {
                val before = target.health
                hurt(target, ability.number("damage"), player)
                val dealt = (before - target.health).coerceAtLeast(0.0)
                heal(player, dealt * ability.number("percent") / 100.0)
            }
        }
    }

    /** 폭발·운석: [center] 둘레의 것에 피해. 운석은 하늘에서 떨어지는 모습을 먼저 보여 준다. */
    private fun area(player: Player, ability: Ability, center: Location) {
        val players = ability.int("players") > 0
        val strike = {
            center.world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1)
            center.world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f)
            val before = casting
            casting = true
            try {
                for (entity in around(center, ability.number("radius"), player, players)) {
                    hurt(entity, ability.number("damage"), player)
                    entity.velocity = entity.location.toVector().subtract(center.toVector()).normalize().multiply(0.8).setY(0.4)
                }
            } finally {
                casting = before
            }
        }
        if (ability.effect != EffectType.METEOR) {
            strike()
            return
        }
        var step = 0
        player.scheduler.runAtFixedRate(custom.plugin, { task ->
            step++
            val height = (METEOR_STEPS - step) * 1.5
            center.world.spawnParticle(Particle.FLAME, center.clone().add(0.0, height, 0.0), 12, 0.3, 0.3, 0.3, 0.02)
            center.world.spawnParticle(Particle.LARGE_SMOKE, center.clone().add(0.0, height + 0.5, 0.0), 4, 0.2, 0.2, 0.2, 0.0)
            if (step >= METEOR_STEPS) {
                task.cancel()
                strike()
            }
        }, null, 1L, 2L)
    }

    /** 기능 피해 +% 를 먹인 배율. */
    private fun boost(player: Player): Double =
        (1.0 + custom.stats.of(player).stat(com.inmc.customitems.item.Stat.SKILL_DAMAGE) / 100.0).coerceAtLeast(0.0)

    private fun hurt(victim: LivingEntity, amount: Double, player: Player) {
        if (amount > 0.0) victim.damage(amount * boost(player), player)
    }

    private fun heal(target: LivingEntity, amount: Double) {
        if (amount <= 0.0) return
        val max = target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0
        target.health = (target.health + amount).coerceIn(0.0, max)
    }

    /** 둘레의 살아 있는 것. 쓴 사람·갑옷 거치대는 빼고, [players] 가 아니면 플레이어도 뺀다. */
    private fun around(center: Location, radius: Double, player: Player, players: Boolean): Collection<LivingEntity> =
        center.world.getNearbyLivingEntities(center, radius) { it != player && !it.isDead && it !is ArmorStand && (players || it !is Player) }

    /** 기능이 쏜 발사체. 맞으면 이 피해를 준다([skillDamage]). 무기의 능력치·적중 기능은 타지 않는다. */
    private fun tag(projectile: Projectile, damage: Double) {
        projectile.persistentDataContainer.set(SKILL_DAMAGE, PersistentDataType.DOUBLE, damage)
    }

    /**
     * 바라보는 쪽으로 순간이동한다.
     *
     * **벽을 통과하지 않는다.** 시선을 따라가며 막히기 직전 칸에 세운다 — 통과시키면
     * 스폰 보호 구역이나 감옥을 뚫는 도구가 된다.
     */
    private fun teleport(player: Player, distance: Double) {
        val from = player.location
        val direction = from.direction.normalize()
        var safe: Location = from

        var step = 1.0
        while (step <= distance) {
            val candidate = from.clone().add(direction.clone().multiply(step))
            if (!candidate.block.isPassable || !candidate.clone().add(0.0, 1.0, 0.0).block.isPassable) break
            safe = candidate
            step += 0.5
        }

        if (safe !== from) player.teleport(safe.setDirection(from.direction))
    }

    // --- 이름 찾기 -------------------------------------------------------------------
    //
    // 셋 다 레지스트리 조회다. 1.20.5 부터 `valueOf` 계열이 사라졌고, 모르는 이름이 오면
    // null 을 줘서 그 효과만 조용히 건너뛴다 — 오타 하나로 아이템 전체가 죽으면 안 된다.

    private fun potion(raw: String): PotionEffectType? = Registries.potionEffect(raw)

    private fun sound(raw: String): Sound? = Registries.sound(raw)

    private fun particle(raw: String): Particle? =
        runCatching { Particle.valueOf(raw.trim().uppercase().replace('.', '_')) }.getOrNull()

    companion object {
        /** 기능이 쏜 발사체에 적는 피해. */
        @Suppress("DEPRECATION")
        val SKILL_DAMAGE = NamespacedKey(com.inmc.customitems.item.ItemBuilder.NAMESPACE, "skill_damage")

        private const val RANGE = 40
        private const val METEOR_STEPS = 10

        /** 기능이 쏜 발사체면 그 피해. */
        fun skillDamage(entity: Entity): Double? = entity.persistentDataContainer.get(SKILL_DAMAGE, PersistentDataType.DOUBLE)

        /** 우리 활·삼지창으로 쏜 발사체에 적는 그 아이템 id. 맞았을 때 [Trigger.PROJECTILE_HIT] 를 그 아이템에서 찾는다. */
        @Suppress("DEPRECATION")
        val SOURCE = NamespacedKey(com.inmc.customitems.item.ItemBuilder.NAMESPACE, "source_item")
    }
}

/**
 * 쿨다운. 플레이어 × 아이템 × 기능 번호.
 *
 * **저장하지 않는다.** 재시작에 초기화되는 것이 표준 동작이고, 저장하면 서버가 꺼져 있던
 * 시간을 쿨다운으로 쳐야 하는지 아닌지가 애매해진다.
 */
class Cooldowns {

    private val until = ConcurrentHashMap<String, Long>()

    /**
     * 쓸 수 있으면 쓰고 true. 아직이면 false.
     *
     * **확인과 기록이 한 번에 일어난다.** 나누면 확인과 기록 사이에 다른 클릭이 끼어들어
     * 두 번 터진다.
     */
    fun tryUse(
        player: UUID,
        itemId: String,
        index: Int,
        seconds: Double,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (seconds <= 0.0) return true
        val key = key(player, itemId, index)
        val ready = until[key] ?: 0L
        if (now < ready) return false
        until[key] = now + (seconds * 1000L).toLong()
        return true
    }

    fun remaining(
        player: UUID,
        itemId: String,
        index: Int,
        now: Long = System.currentTimeMillis(),
    ): Long = ((until[key(player, itemId, index)] ?: 0L) - now).coerceAtLeast(0L)

    fun forget(player: UUID) {
        val prefix = player.toString() + "|"
        until.keys.removeIf { it.startsWith(prefix) }
    }

    private fun key(player: UUID, itemId: String, index: Int): String =
        player.toString() + "|" + itemId + "|" + index
}

/**
 * 지속([Trigger.PASSIVE]) 물약의 실제 지속 틱.
 *
 * 바닐라는 남은 시간 10초부터 화면을 깜빡인다. 주기마다 다시 걸면 남은 시간이 주기 이하로
 * 떨어져 영원히 깜빡임 구간에 갇힌다 — 그래서 남은 시간이 절대 11초 아래로 안내려가게
 * `주기 + 11초`와 설정값 중 큰 것으로 건다. 해제한 뒤에는 최대 주기+11초만 남고 꺼진다.
 *
 * 렉과 무관하다. 길이는 숫자에 불과하고(클라 카운트다운), 서버 일은 그대로다 — 1초 틱커와
 * 기능별 주기(최소 0.5초)가 그대로 돌고, 건 횟수도 같다. 무한으로 두지 않아 추적표도 안 든다.
 */
internal fun passivePotionTicks(trigger: Trigger, seconds: Double, intervalSeconds: Double): Int {
    val configured = (seconds * 20).toInt().coerceAtLeast(1)
    if (trigger != Trigger.PASSIVE) return configured
    return maxOf(configured, (intervalSeconds * 20).toInt() + FLICKER_FREE_TICKS)
}

/** 깜빡임이 시작되는 10초보다 1초 여유 있게. */
internal const val FLICKER_FREE_TICKS = 220
