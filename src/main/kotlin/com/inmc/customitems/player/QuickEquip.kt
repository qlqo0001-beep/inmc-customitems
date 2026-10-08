package com.inmc.customitems.player

import com.inmc.customitems.CustomItems
import com.inmc.customitems.player.EquipmentStore.Group
import kr.inmc.core.CorePlugin
import org.bukkit.Sound
import org.bukkit.entity.Player

/**
 * 손에 든 장신구·부적·유물을 **우클릭으로 장착**(사용자 요청 2026-10-07). 배낭은 우클릭이 여는 것이라 빠진다.
 *
 * - 그 종류의 **열린 빈 칸**이 있으면 첫 빈 칸에 넣는다.
 * - 다 찼으면 **마지막 열린 칸**의 것과 맞바꾼다(칸이 하나면 그 칸 — "끼운 것이 있으면 갈아끼우기").
 * - 손에 여러 개 들었으면 하나만 끼우고, 빼낸 것은 가방으로 — 가방에 자리가 없으면 바꾸지 않는다(반만 옮기면 하나가 사라진다).
 * - 처음 장착하면 채팅에 한 번 `/acc` 를 알려 준다(PlayerStore `inmc_equip` 의 `hinted`).
 *
 * 메인 스레드. 칸 쓰기는 [EquipmentStore.put](바꿀 때마다 즉시 원자 저장 — 규칙 58).
 */
class QuickEquip(private val custom: CustomItems) {

    /** 끼웠으면 true. 못 끼웠으면 알리고 false. */
    fun equip(player: Player, group: Group): Boolean {
        val store = custom.equipment
        val id = player.uniqueId
        val hand = player.inventory.itemInMainHand
        if (hand.type.isAir) return false
        val capacity = store.capacity(player, group)
        if (capacity <= 0) {
            custom.messages.send(player, "equip-locked")
            return false
        }
        val index = (0 until capacity).firstOrNull { store.get(id, group, it) == null } ?: (capacity - 1)
        val old = store.get(id, group, index)?.clone()
        val rest = hand.clone().also { it.amount -= 1 }.takeIf { it.amount > 0 }
        if (old != null && rest != null && player.inventory.firstEmpty() < 0) {
            custom.messages.send(player, "equip-bag-full")
            return false
        }

        store.put(id, group, index, hand.clone().also { it.amount = 1 })
        player.inventory.setItemInMainHand(rest ?: old)
        if (old != null && rest != null) player.inventory.addItem(old)

        player.playSound(player.location, Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.8f, 1.2f)
        custom.stats.invalidate(player)
        hintOnce(player)
        return true
    }

    private fun hintOnce(player: Player) {
        val players = CorePlugin.get().players
        if (players.getLong(player.uniqueId, NAMESPACE, HINTED) > 0L) return
        players.set(player.uniqueId, NAMESPACE, HINTED, System.currentTimeMillis())
        custom.messages.send(player, "equip-first-hint")
    }

    companion object {
        /** PlayerStore 네임스페이스 — ARCHITECTURE "PlayerStore 네임스페이스" 표에 있다. */
        const val NAMESPACE = "inmc_equip"
        const val HINTED = "hinted"
    }
}
