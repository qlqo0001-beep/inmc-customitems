package com.inmc.customitems.listener

import com.inmc.customitems.CustomItems
import com.inmc.customitems.player.EquipmentStore
import io.papermc.paper.connection.PlayerGameConnection
import io.papermc.paper.event.player.PlayerCustomClickEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * 장착 칸의 적재·해제와, **사망 처리 플러그인이 없을 때의** 드랍.
 *
 * 인벤키퍼가 있으면 사망 사건에서 `keepInventory` 를 켜고 장착 칸을 core [kr.inmc.core.integration.ExtraInventory]
 * 로 가져간다(가방과 같은 비율·무덤). 없으면 여기서 바닐라처럼 전부 떨군다. 게임룰 `keepInventory` 면 둘 다 안 한다 —
 * 그래서 사건의 끝(`HIGHEST`)에서 `keepInventory` 가 꺼져 있을 때만 움직인다.
 */
class EquipmentListener(private val custom: CustomItems) : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onJoin(event: PlayerJoinEvent) = custom.equipment.load(event.player.uniqueId)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) = custom.equipment.forget(event.player.uniqueId)

    /**
     * 빠른 동작 키(기본 G)의 "빠른 메뉴" 에서 [배낭 열기] — `/배낭` 과 같다. 창과 버튼은 서버가 켜지기 전에 등록된다
     * ([com.inmc.customitems.CustomItemsBootstrap]). 이 사건은 패킷을 받은 자리에서 올 수 있어 그 사람의 스케줄러로 넘긴다.
     */
    @EventHandler
    fun onQuickAction(event: PlayerCustomClickEvent) {
        if (event.identifier != com.inmc.customitems.CustomItemsBootstrap.OPEN_BACKPACK) return
        val player = (event.commonConnection as? PlayerGameConnection)?.player ?: return
        player.scheduler.run(custom.plugin, { _ ->
            if (!player.isOnline || !custom.ready) return@run
            if (!player.hasPermission(com.inmc.customitems.command.CustomItemsCommand.BACKPACK)) return@run
            custom.backpacks.openEquippedAny(player)
        }, null)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDeath(event: PlayerDeathEvent) {
        if (event.keepInventory) return
        val player = event.entity
        for (group in EquipmentStore.Group.entries) {
            for ((index, stack) in custom.equipment.slots(player.uniqueId, group).withIndex()) {
                if (stack == null) continue
                event.drops += stack.clone()
                custom.equipment.put(player.uniqueId, group, index, null)
            }
        }
    }
}
