package com.inmc.customitems.player

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.EvolveKeep
import com.inmc.customitems.item.ItemBuilder
import com.inmc.customitems.item.ItemInstance
import com.inmc.customitems.item.UpgradeTable
import com.inmc.customitems.item.Upgrades
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.Random

/**
 * 강화와 진화를 실제로 한다. 강화석·진화석을 끌어다 놓는 것([com.inmc.customitems.listener.ApplyListener])과
 * 제작대의 진화 화면이 부른다. 재료·강화석을 거두는 것은 부르는 쪽 몫이다.
 */
class UpgradeService(private val custom: CustomItems) {

    private val random = Random()

    enum class Outcome { SUCCESS, KEPT, DOWN, RESET, DESTROYED }

    /** @param stack 강화한 뒤의 아이템. 파괴됐으면 null. */
    data class Attempt(val outcome: Outcome, val level: Int, val stack: ItemStack?)

    /**
     * 한 단계 강화를 시도한다. [target] 을 그 자리에서 다시 그린다.
     *
     * 파괴되면 박혀 있던 보석은 돌려준다 — 강화 실패로 보석까지 잃으면 아무도 보석을 박지 않는다.
     */
    fun attempt(player: Player, target: ItemStack, definition: CustomItem, table: UpgradeTable, stone: com.inmc.customitems.item.UpgradeStone): Attempt {
        val instance = ItemInstance.read(target)
        val step = table.step(instance.level + 1) ?: return Attempt(Outcome.KEPT, instance.level, target)
        val chance = Upgrades.chance(step, stone, ItemBuilder.tierOf(definition, instance, custom.items.lookup))
        if (random.nextDouble() * 100.0 < chance) {
            ItemBuilder.render(target, definition, instance.copy(level = instance.level + 1), custom.items.lookup)
            return Attempt(Outcome.SUCCESS, instance.level + 1, target)
        }
        val after = Upgrades.afterFail(step.fail, instance.level)
        if (after < 0) {
            returnGems(player, instance)
            // 배낭이면 안의 것도 보석처럼 돌려준다.
            custom.backpacks.spill(player, target)
            return Attempt(Outcome.DESTROYED, instance.level, null)
        }
        if (after != instance.level) ItemBuilder.render(target, definition, instance.copy(level = after), custom.items.lookup)
        val outcome = when {
            after == instance.level -> Outcome.KEPT
            step.fail == com.inmc.customitems.item.FailResult.RESET -> Outcome.RESET
            else -> Outcome.DOWN
        }
        return Attempt(outcome, after, target)
    }

    /**
     * 진화한 새 아이템. 진화 설정([com.inmc.customitems.item.Evolution.keep])대로 넘기고, 돌려줄 보석은 가방으로.
     * 진화 대상이 없으면 null(부르는 쪽이 [Upgrades.canEvolve] 로 먼저 거른다).
     */
    fun evolve(player: Player, stack: ItemStack, definition: CustomItem): ItemStack? {
        val evolution = definition.upgrade.evolution ?: return null
        val target = custom.items.get(evolution.into) ?: return null
        val evolved = custom.items.create(target.id) ?: return null
        val old = ItemInstance.read(stack)
        when (evolution.keep) {
            EvolveKeep.FRESH -> returnGems(player, old)
            EvolveKeep.NOTHING -> Unit
            EvolveKeep.INHERIT -> {
                val created = ItemInstance.read(evolved)
                var next = created.copy(
                    // 대상에도 폭이 있는 능력치만 굴린 값을 잇는다. 나머지는 새로 굴린 그대로.
                    rolls = created.rolls + old.rolls.filterKeys { stat -> target.spreads[stat]?.spread?.let { it > 0.0 } == true },
                    modifiers = old.modifiers.filter { id -> target.modifiers.any { it.id == id } },
                    gems = emptyList(),
                )
                val leftover = ArrayList<String>()
                for (gemId in old.gems.filter { it.isNotEmpty() }) {
                    val gem = custom.items.get(gemId)?.gem
                    val socket = target.sockets.indices.firstOrNull { next.gem(it) == null && gem?.fits(target.sockets[it]) == true }
                    if (socket == null) leftover += gemId else next = next.withGem(socket, gemId)
                }
                ItemBuilder.render(evolved, target, next, custom.items.lookup)
                give(player, leftover)
            }
        }
        // 배낭 번호는 무엇을 이어받든 넘긴다 — 안 넘기면 창고의 물건이 번호 잃은 파일에 갇힌다.
        custom.backpacks.idOf(stack)?.let { id ->
            evolved.editMeta { it.persistentDataContainer.set(com.inmc.customitems.player.Backpacks.KEY, org.bukkit.persistence.PersistentDataType.STRING, id.toString()) }
        }
        return evolved
    }

    /** 박혀 있던 보석을 가방으로(가득하면 발밑으로). */
    fun returnGems(player: Player, instance: ItemInstance) = give(player, instance.gems.filter { it.isNotEmpty() })

    private fun give(player: Player, ids: List<String>) {
        for (id in ids) custom.items.create(id)?.let { stack ->
            for (left in player.inventory.addItem(stack).values) player.world.dropItemNaturally(player.location, left)
        }
    }
}
