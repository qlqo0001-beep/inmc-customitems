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
     * 빠른 동작 키(기본 G)의 "빠른 메뉴" 에서 [배낭] — 1번 배낭을 곧바로 연다(여럿이면 배낭 창 안에서 번호로 바꾼다,
     * [com.inmc.customitems.player.Backpacks.quickMenu]). 첫 창은 서버가 켜지기 전에 등록된다([com.inmc.customitems.CustomItemsBootstrap])
     * — 누르는 순간 클라이언트가 창을 닫으므로 답할 것은 없다(못 열면 메시지만). 이 사건은 패킷을 받은 자리에서 올 수 있어 그 사람의
     * 스케줄러로 넘긴다.
     */
    @EventHandler
    fun onQuickAction(event: PlayerCustomClickEvent) {
        if (event.identifier != com.inmc.customitems.CustomItemsBootstrap.OPEN_BACKPACK) return
        val player = (event.commonConnection as? PlayerGameConnection)?.player ?: return
        player.scheduler.run(custom.plugin, { _ ->
            if (!player.isOnline || !custom.ready || !player.hasPermission(com.inmc.customitems.command.CustomItemsCommand.BACKPACK)) return@run
            custom.backpacks.quickMenu(player)
        }, null)
    }

    /**
     * 드랍 자동 수납(사용자 요청 2026-09-30) — 자동 수납이 켜진 배낭이 있으면 주운 것을 가방보다 먼저 넣는다.
     *
     * `EntityPickupItemEvent` 가 아니라 이 사건에서 받는다 — 그쪽은 가방에 담을 자리가 있을 때만 와서, 자동 수납이 가장 필요한 때
     * (가방이 꽉 참)를 놓친다. 대신 넣기 전에 줍기 사건을 한 번 직접 쏴서 다른 플러그인(영혼각인 — 남의 물건 줍기 막기)이 막을 기회를
     * 준다. 우리가 넣은 경우는 이 사건을 취소하므로 바닐라가 그 사건을 또 쏘지 않는다. 보스 전리품 예약(몬스터, LOW)도 먼저 돈다.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onAttemptPickup(event: org.bukkit.event.player.PlayerAttemptPickupItemEvent) {
        val player = event.player
        if (!custom.ready || !custom.items.hasBackpacks || !player.canPickupItems) return
        val item = event.item
        if (item.owner?.let { it != player.uniqueId } == true) return
        if (!custom.backpacks.hasRoom(player, item.itemStack)) return
        val asked = org.bukkit.event.entity.EntityPickupItemEvent(player, item, event.remaining)
        if (!asked.callEvent()) return
        val stack = item.itemStack
        val left = custom.backpacks.store(player, stack)
        if (left != null && left.amount == stack.amount) return
        event.isCancelled = true
        val stored = stack.amount - (left?.amount ?: 0)
        player.playPickupItemAnimation(item, stored)
        player.playSound(player.location, org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.2f, 1.6f)
        // 배낭에 다 못 넣은 것은 가방으로, 가방도 차면 땅에 그대로.
        val rest = left?.let { player.inventory.addItem(it).values.firstOrNull() }
        if (rest == null) item.remove() else item.itemStack = rest
        custom.stats.invalidate(player)
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
