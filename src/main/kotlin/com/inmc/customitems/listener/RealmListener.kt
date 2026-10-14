package com.inmc.customitems.listener

import com.inmc.customitems.CustomItems
import com.inmc.customitems.craft.CraftService
import com.inmc.customitems.player.EquipmentStore
import kr.inmc.core.event.RealmChangeEvent
import kr.inmc.core.event.RealmFeatureChangeEvent
import kr.inmc.core.integration.Realms
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

/**
 * 모드(core [Realms] — inmc-worlds, 2026-10-15). 장착 칸·제작 대기열은 `(사람, 모드)` 로 쥐므로 모드가 바뀌면 다음 조회가 새 칸을 본다 —
 * 여기서는 **사람에게 건 것**(장신구·부적·유물의 능력치·세트 효과)을 다시 걸고, 토글이 바뀌면 열린 우리 화면을 닫는다(낡은 칸을 보며 옮기지 않게).
 */
class RealmListener(private val custom: CustomItems) : Listener {

    @EventHandler
    fun onRealm(event: RealmChangeEvent) = refresh(event.player)

    @EventHandler
    fun onFeature(event: RealmFeatureChangeEvent) {
        if (event.key !in KEYS) return
        for (player in Bukkit.getOnlinePlayers()) {
            val holder = player.openInventory.topInventory.holder
            if (holder is kr.inmc.core.gui.Menu && holder.owner === custom) player.closeInventory()
            refresh(player)
        }
    }

    private fun refresh(player: Player) {
        custom.equipment.load(player.uniqueId)
        custom.equipment.refresh(player.uniqueId)
        custom.stats.invalidate(player)
        player.scheduler.run(custom.plugin, { custom.stats.sync(player) }, null)
    }

    companion object {
        val KEYS = setOf(EquipmentStore.FEATURE, CraftService.FEATURE)

        /** 켤 때 — 관리자 토글 목록(inmc-worlds `/모드 관리`)에 올린다. */
        fun registerFeatures() {
            Realms.register(Realms.Feature(EquipmentStore.FEATURE, OWNER, "장착 칸", Material.AMETHYST_SHARD,
                listOf("장신구 · 부적 · 유물 · 배낭 줄(/장비 · /배낭)")))
            Realms.register(Realms.Feature(CraftService.FEATURE, OWNER, "제작 대기열", Material.CRAFTING_TABLE,
                listOf("제작대에 걸어 둔 제작(재료를 거둔 모드에서 받는다)")))
        }

        const val OWNER = "커스텀아이템"
    }
}
