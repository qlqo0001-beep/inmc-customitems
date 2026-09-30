package com.inmc.customitems.player

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.Expiry
import com.inmc.customitems.item.ItemInstance
import com.inmc.customitems.item.Periods
import com.inmc.customitems.util.Ph
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 사용 기간이 끝난 아이템의 뒤처리([com.inmc.customitems.item.CustomItem.period], 사용자 결정 2026-09-30 — 받은 순간부터 실제 시간).
 *
 * **기능을 끄는 것은 여기가 아니다** — [com.inmc.customitems.item.ItemRegistry.usable] 이 끝나는 시각을 그 자리에서 보므로 기간이
 * 다 되는 순간 능력치·기능·장착·사용이 멈춘다. 여기는 **눈에 보이는 것**을 맞춘다: 사라짐이면 치우고, 효과 정지면 다시 그려 로어와
 * 바닐라 부품(입기·먹기·캐기 …)을 맞춘다. 배낭이면 **안의 것을 먼저 돌려준다**(두 방식 모두 — 기간이 끝났다고 남의 물건을 가두지 않는다).
 *
 * 접속자의 가방·갑옷·왼손과 장착 칸을 몇 초마다 본다. 상자 안의 것은 열 때(자동 갱신), 배낭 안의 것은 배낭을 열 때 맞춘다.
 */
class ExpiryService(private val custom: CustomItems) {

    fun sweep() {
        if (!custom.items.hasPeriod) return
        for (player in Bukkit.getOnlinePlayers()) sweep(player)
    }

    fun sweep(player: Player) {
        val now = System.currentTimeMillis()
        val inventory = player.inventory
        for (index in 0 until inventory.size) {
            val stack = inventory.getItem(index) ?: continue
            when (check(player, stack, now)) {
                Outcome.GONE -> inventory.setItem(index, null)
                Outcome.REDRAWN -> inventory.setItem(index, stack)
                Outcome.SAME -> Unit
            }
        }
        var changed = false
        for (group in EquipmentStore.Group.entries) {
            for (index in 0 until EquipmentStore.MAX) {
                val stack = custom.equipment.get(player.uniqueId, group, index)?.clone() ?: continue
                when (check(player, stack, now)) {
                    Outcome.GONE -> custom.equipment.put(player.uniqueId, group, index, null).also { changed = true }
                    Outcome.REDRAWN -> custom.equipment.put(player.uniqueId, group, index, stack).also { changed = true }
                    Outcome.SAME -> Unit
                }
            }
        }
        if (changed) custom.stats.invalidate(player)
    }

    private enum class Outcome { SAME, REDRAWN, GONE }

    /** 한 개. 기간이 없거나 안 끝났으면 자동 갱신만(처음 보는 옛 아이템에 시각을 찍는다). */
    private fun check(player: Player, stack: ItemStack, now: Long): Outcome {
        val definition = custom.items.identify(stack) ?: return Outcome.SAME
        if (definition.period <= 0) return Outcome.SAME
        if (Periods.expired(definition, ItemInstance.expiresAt(stack), now)) {
            if (definition.isBackpack) custom.backpacks.spill(player, stack)
            if (definition.expiry == Expiry.VANISH) {
                custom.messages.send(player, "item-expired", Ph.of().item(definition.label()))
                return Outcome.GONE
            }
        }
        return if (custom.items.refresh(stack)) Outcome.REDRAWN else Outcome.SAME
    }
}
