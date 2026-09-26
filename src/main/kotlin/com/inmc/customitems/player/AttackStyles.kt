package com.inmc.customitems.player

import com.inmc.customitems.item.AttackStyle
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * [AttackStyle] 을 실제로 한다. 근접 방식은 [CombatListener][com.inmc.customitems.listener.CombatListener] 가
 * 적중 뒤에, 원거리 방식은 클릭 때 부른다.
 *
 * 둘레에 튀는 피해(창·망치)는 **몹에게만** 간다 — 기능의 범위 효과와 같은 기본값이다. 서버마다 PvP 사정이
 * 달라 안전한 쪽에 둔다.
 */
class AttackStyles {

    /**
     * 둘레에 튄 피해를 주는 중. 그 안에서 난 피해는 무기 공격이 아니다 — 능력치·적중 기능을 또 태우면
     * 튄 피해가 또 튀어 끝없이 돈다.
     */
    var splashing: Boolean = false
        private set

    private val ready = ConcurrentHashMap<UUID, Long>()

    /** 근접 적중 뒤. [damage] 는 최종 피해. */
    fun afterHit(style: AttackStyle, attacker: Player, victim: LivingEntity, damage: Double) {
        // 근접으로 쳤으면 같은 휘두름에 원거리 방식이 또 쏘지 않는다(휘두름이 허공 좌클릭으로도 들어온다).
        if (style.ranged) ready[attacker.uniqueId] = System.currentTimeMillis() + cooldown(style, attacker)
        when (style) {
            AttackStyle.SPEAR -> {
                val forward = attacker.location.direction.setY(0)
                if (forward.lengthSquared() == 0.0) return
                forward.normalize()
                val from = victim.location.toVector()
                splash(attacker, victim, damage * style.ratio, victim.location, style.range, lift = false) {
                    val offset = it.location.toVector().subtract(from)
                    val along = offset.dot(forward)
                    along > 0.0 && offset.subtract(forward.clone().multiply(along)).lengthSquared() <= 1.0
                }
                victim.world.spawnParticle(Particle.SWEEP_ATTACK, victim.location.add(forward.clone().multiply(1.5)).add(0.0, 1.0, 0.0), 1)
            }

            AttackStyle.HAMMER -> {
                val center = victim.location
                splash(attacker, victim, damage * style.ratio, center, style.range, lift = true) { true }
                center.world.spawnParticle(Particle.EXPLOSION, center, 1)
                center.world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.4f)
            }

            AttackStyle.GAUNTLET -> {
                val push = attacker.location.direction.setY(0)
                if (push.lengthSquared() > 0.0) victim.velocity = push.normalize().multiply(1.4).setY(0.45)
                victim.world.playSound(victim.location, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.8f, 1.0f)
            }

            else -> Unit
        }
    }

    /** 클릭. 이 아이템이 이 클릭으로 쏘는 방식이면 쏘고 true (쿨다운이면 안 쏘고 true). */
    fun click(player: Player, style: AttackStyle, left: Boolean): Boolean {
        if (!style.ranged || style.leftClick != left) return false
        val now = System.currentTimeMillis()
        if (now < (ready[player.uniqueId] ?: 0L)) return true
        ready[player.uniqueId] = now + cooldown(style, player)

        val eye = player.eyeLocation
        val direction = eye.direction
        val hit = player.world.rayTrace(eye, direction, style.range, FluidCollisionMode.NEVER, true, 0.4) {
            it is LivingEntity && it != player && it !is ArmorStand
        }
        val end = hit?.hitPosition?.toLocation(player.world) ?: eye.clone().add(direction.clone().multiply(style.range))
        trail(style, eye, end)

        val target = hit?.hitEntity as? LivingEntity ?: return true
        val power = player.getAttribute(Attribute.ATTACK_DAMAGE)?.value ?: 1.0
        // 평범한 공격처럼 들어간다 — 전투 리스너가 능력치와 적중 기능을 태운다.
        target.damage(power * style.ratio, player)
        return true
    }

    fun forget(player: UUID) {
        ready.remove(player)
    }

    private fun cooldown(style: AttackStyle, player: Player): Long =
        AttackStyle.cooldownMillis(style, player.getAttribute(Attribute.ATTACK_SPEED)?.value ?: 4.0)

    private fun splash(attacker: Player, victim: LivingEntity, amount: Double, center: Location, radius: Double, lift: Boolean, filter: (LivingEntity) -> Boolean) {
        if (amount <= 0.0) return
        val targets = center.world.getNearbyLivingEntities(center, radius) {
            it != attacker && it != victim && !it.isDead && it !is ArmorStand && it !is Player && filter(it)
        }
        if (targets.isEmpty()) return
        splashing = true
        try {
            for (target in targets) {
                target.damage(amount, attacker)
                if (lift) target.velocity = target.velocity.setY(0.35)
            }
        } finally {
            splashing = false
        }
    }

    private fun trail(style: AttackStyle, from: Location, to: Location) {
        val particle = when (style) {
            AttackStyle.STAFF -> Particle.WITCH
            AttackStyle.MUSKET -> Particle.SMOKE
            else -> Particle.CRIT
        }
        val step = to.toVector().subtract(from.toVector())
        val length = step.length()
        if (length <= 0.0) return
        step.normalize().multiply(0.5)
        val point = from.clone()
        var travelled = 0.0
        while (travelled < length) {
            from.world.spawnParticle(particle, point, 1, 0.0, 0.0, 0.0, 0.0)
            point.add(step)
            travelled += 0.5
        }
        val sound = when (style) {
            AttackStyle.STAFF -> Sound.ENTITY_EVOKER_CAST_SPELL
            AttackStyle.MUSKET -> Sound.ENTITY_GENERIC_EXPLODE
            else -> Sound.ENTITY_PLAYER_ATTACK_SWEEP
        }
        from.world.playSound(from, sound, if (style == AttackStyle.MUSKET) 0.6f else 0.8f, if (style == AttackStyle.MUSKET) 1.8f else 1.2f)
    }
}
