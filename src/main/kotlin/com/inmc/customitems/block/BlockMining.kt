package com.inmc.customitems.block

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.BlockKind
import com.inmc.customitems.item.BlockSpec
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.ItemBuilder
import com.inmc.customitems.item.Stat
import kr.inmc.core.util.Text
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.attribute.AttributeModifier
import org.bukkit.block.Block
import org.bukkit.entity.ExperienceOrb
import org.bukkit.entity.Player
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.EquipmentSlotGroup
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

/**
 * 커스텀 블록을 **시간 들여 캔다** — 단단함·맞는 도구·등급([BlockSpec]), 효율·성급함·물속·공중까지 바닐라 공식([Mining]).
 *
 * 바닐라가 스스로 부수지 못하게, 캐는 동안 그 사람의 블록 파괴 속도를 0 배로 묶는다(속성 수정자 `inmc:block_mining`, 저장 안 됨).
 * 그러면 클라이언트도 금이 안 가고 손을 떼면 그만둔다고 알린다(`BlockDamageAbortEvent`). 진행은 여기서 틱마다 세고, 금은
 * `sendBlockDamage` 로 둘레 사람에게 보여 준다. 다 캐면 부수기 사건을 쏜다 — 땅 보호가 막을 수 있고, 떨구기는 그 사건의 MONITOR
 * (`BlockListener.afterBreak`)가 한다.
 *
 * 캐는 사람이 없으면 틱 작업도 없다.
 */
class BlockMining(private val custom: CustomItems) {

    private class Session(val block: Block, val itemId: String) {
        var progress = 0.0
        var stage = -1
    }

    private val sessions = HashMap<UUID, Session>()
    private var task: BukkitTask? = null

    /** 캐기 시작(`BlockDamageEvent`). 같은 블록을 계속 치고 있으면 이어서 센다. */
    fun start(player: Player, block: Block, item: CustomItem) {
        val spec = item.block ?: return
        val current = sessions[player.uniqueId]
        if (current != null && current.block == block && current.itemId == item.id) return
        stop(player)
        freeze(player)
        sessions[player.uniqueId] = Session(block, item.id)
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(custom.plugin, Runnable { tick() }, 1L, 1L)

        val tool = held(player)
        val needed = spec.tool?.let { ToolGrades.requirement(it, spec.toolTier) }
        when {
            needed == null -> Unit
            !Mining.canBreak(spec, tool) -> player.sendActionBar(Text.render("<red>" + needed + " 로만 캘 수 있습니다</red>"))
            !Mining.yields(spec, tool) -> player.sendActionBar(Text.render("<yellow>" + needed + " 가 아니면 아무것도 나오지 않습니다</yellow>"))
        }
    }

    /** 그만 캔다 — 손을 뗐거나, 다른 블록을 치기 시작했거나, 나갔거나. */
    fun stop(player: Player) {
        val session = sessions.remove(player.uniqueId) ?: return
        unfreeze(player)
        if (session.stage >= 0) crack(player, session.block, 0f)
    }

    /** 내려갈 때 — 묶어 둔 빠르기를 전부 푼다(안 풀면 리로드 뒤 그 사람들이 아무것도 못 캔다). */
    fun shutdown() {
        for (uuid in sessions.keys.toList()) Bukkit.getPlayer(uuid)?.let(::stop)
        sessions.clear()
        task?.cancel()
        task = null
    }

    private fun tick() {
        if (sessions.isEmpty()) {
            task?.cancel()
            task = null
            return
        }
        val finished = ArrayList<Pair<Player, Session>>()
        for ((uuid, session) in sessions.entries.toList()) {
            val player = Bukkit.getPlayer(uuid)
            if (player == null) {
                sessions.remove(uuid)
                continue
            }
            val item = stillMining(player, session)
            val spec = item?.block
            if (spec == null) {
                stop(player)
                continue
            }
            val tool = held(player)
            if (!Mining.canBreak(spec, tool)) continue
            session.progress += Mining.perTick(spec, speed(player, spec, tool), Mining.yields(spec, tool))
            if (session.progress >= 1.0) {
                finished += player to session
                continue
            }
            val stage = (session.progress * 10).toInt().coerceIn(0, 9)
            if (stage != session.stage) {
                session.stage = stage
                crack(player, session.block, session.progress.toFloat().coerceIn(0.01f, 1f))
            }
        }
        for ((player, session) in finished) finish(player, session)
    }

    /** 아직 그 블록을 캐고 있는가 — 야생에서, 같은 세상, 그 블록을 보고 있고, 블록이 아직 그 아이템이다. */
    private fun stillMining(player: Player, session: Session): CustomItem? {
        if (!player.isOnline || player.isDead || player.gameMode != GameMode.SURVIVAL) return null
        val block = session.block
        if (player.world != block.world) return null
        if (player.getTargetBlockExact(REACH) != block) return null
        return custom.blocks.at(block)?.takeIf { it.id == session.itemId }
    }

    private fun finish(player: Player, session: Session) {
        stop(player)
        val block = session.block
        val item = custom.blocks.at(block) ?: return
        val spec = item.block ?: return
        val look = block.blockData
        val shown = if (spec.kind == BlockKind.ENTITY) custom.items.create(item.id) else null
        // 부수기 사건 — 땅 보호가 막을 수 있다. 떨구기·경험치 값은 BlockListener 가 그 안에서 정한다.
        val event = BlockBreakEvent(block, player)
        if (!event.callEvent()) return
        block.type = Material.AIR
        val center = block.location.add(0.5, 0.5, 0.5)
        if (event.expToDrop > 0) block.world.spawn(center, ExperienceOrb::class.java) { it.experience = event.expToDrop }
        // 바닐라는 도구로 부수면 내구도를 1 쓴다(단단함 0 은 안 쓴다).
        if (spec.hardness > 0.0 && ToolKind.ofMaterial(player.inventory.itemInMainHand.type) != null) player.damageItemStack(EquipmentSlot.HAND, 1)
        if (shown != null) {
            block.world.spawnParticle(Particle.ITEM, center, 24, 0.25, 0.25, 0.25, 0.05, shown)
        } else {
            block.world.spawnParticle(Particle.BLOCK, center, 40, 0.25, 0.25, 0.25, 0.0, look)
        }
        block.world.playSound(center, look.soundGroup.breakSound, 1f, 0.8f)
    }

    // --- 손에 든 것 · 빠르기 ------------------------------------------------------------

    /** 손에 든 도구. 커스텀 도구는 제 채굴 등급(미확인이면 재질 그대로 — 규칙 48). */
    fun held(player: Player): HeldTool {
        val hand = player.inventory.itemInMainHand
        return ToolGrades.held(hand.type, custom.items.usable(hand)?.miningTier)
    }

    private fun speed(player: Player, spec: BlockSpec, tool: HeldTool): Double {
        val haste = maxOf(player.getPotionEffect(PotionEffectType.HASTE)?.amplifier ?: -1, player.getPotionEffect(PotionEffectType.CONDUIT_POWER)?.amplifier ?: -1)
        return Mining.speed(
            spec, tool,
            efficiency = value(player, Stat.MINING_EFFICIENCY) ?: 0.0,
            haste = haste,
            fatigue = player.getPotionEffect(PotionEffectType.MINING_FATIGUE)?.amplifier ?: -1,
            breakSpeed = breakSpeed(player),
            submerged = if (player.isUnderWater) value(player, Stat.SUBMERGED_MINING_SPEED) ?: 0.2 else null,
            onGround = player.isOnGround,
        )
    }

    private fun value(player: Player, stat: Stat): Double? = stat.attribute()?.let { player.getAttribute(it) }?.value

    /** 블록 파괴 속도 — 우리가 붙인 0 배 수정자를 빼고 다시 센다. */
    private fun breakSpeed(player: Player): Double {
        val instance = Stat.BLOCK_BREAK_SPEED.attribute()?.let { player.getAttribute(it) } ?: return 1.0
        val others = instance.modifiers.filter { it.key != KEY }
        fun of(operation: AttributeModifier.Operation) = others.filter { it.operation == operation }.map { it.amount }
        return Mining.attribute(
            instance.baseValue,
            of(AttributeModifier.Operation.ADD_NUMBER),
            of(AttributeModifier.Operation.ADD_SCALAR),
            of(AttributeModifier.Operation.MULTIPLY_SCALAR_1),
        )
    }

    // --- 바닐라 묶기 · 금 ---------------------------------------------------------------

    private fun freeze(player: Player) {
        val instance = Stat.BLOCK_BREAK_SPEED.attribute()?.let { player.getAttribute(it) } ?: return
        if (instance.getModifier(KEY) != null) return
        instance.addTransientModifier(AttributeModifier(KEY, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY))
    }

    private fun unfreeze(player: Player) {
        val instance = Stat.BLOCK_BREAK_SPEED.attribute()?.let { player.getAttribute(it) } ?: return
        if (instance.getModifier(KEY) != null) instance.removeModifier(KEY)
    }

    /**
     * 둘레 사람에게 금을 보여 준다. 0 이면 지운다(Paper 의 약속). 금의 주인 번호는 캐는 사람의 것이 **아니다** — 클라이언트는 자기 번호의
     * 금을 틱마다 제 진행(묶여서 늘 -1 = 지움)으로 덮어쓴다.
     */
    private fun crack(miner: Player, block: Block, progress: Float) {
        val location = block.location
        val source = -1 - miner.entityId
        for (viewer in block.world.players) {
            if (viewer.location.distanceSquared(location) > CRACK_RANGE_SQUARED) continue
            viewer.sendBlockDamage(location, progress, source)
        }
    }

    private companion object {
        /** 캐는 동안 붙이는 수정자. 저장되지 않는다(`addTransientModifier`). */
        @Suppress("DEPRECATION")
        val KEY = NamespacedKey(ItemBuilder.NAMESPACE, "block_mining")

        /** 바라보는 블록을 찾는 거리. 바닐라 손 닿는 거리(4.5)보다 넉넉히 — 그 밖이면 클라이언트가 먼저 그만둔다. */
        const val REACH = 8

        const val CRACK_RANGE_SQUARED = 32.0 * 32.0
    }
}
