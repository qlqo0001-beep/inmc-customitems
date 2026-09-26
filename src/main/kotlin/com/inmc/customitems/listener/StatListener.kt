package com.inmc.customitems.listener

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent
import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.Stat
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerExpChangeEvent
import org.bukkit.event.player.PlayerItemBreakEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.Inventory

/**
 * 장비가 바뀌면 능력치 합을 다시 세고 세트 효과를 맞추며([com.inmc.customitems.player.StatService.sync]),
 * 옛 정의로 그려진 아이템을 지금 정의로 다시 그린다(자동 갱신).
 *
 * 다시 세는 것은 **다음 틱에** — 클릭 사건 순간에는 아직 장비가 안 바뀌었다. 놓친 사건은 1초 틱이 덮는다.
 *
 * 자동 갱신은 아이템이 **사람 눈에 들어오는 순간**에 한다: 접속(가방 전체)·손에 쥘 때·주울 때·상자를 열 때.
 * 서버의 모든 상자를 훑을 방법은 없고, 눈에 안 들어온 아이템은 옛 모습이어도 아무도 모른다.
 */
class StatListener(private val custom: CustomItems) : Listener {

    private fun soon(player: Player) {
        player.scheduler.run(custom.plugin, { custom.stats.sync(player) }, null)
    }

    private fun sweep(inventory: Inventory) = custom.items.refresh(inventory)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        player.scheduler.run(custom.plugin, {
            sweep(player.inventory)
            custom.equipment.refresh(player.uniqueId)
            custom.stats.sync(player)
        }, null)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) = custom.stats.forget(event.player)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onHeld(event: PlayerItemHeldEvent) {
        val inventory = event.player.inventory
        inventory.getItem(event.newSlot)?.let { if (custom.items.refresh(it)) inventory.setItem(event.newSlot, it) }
        soon(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPickup(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        val stack = event.item.itemStack
        if (custom.items.refresh(stack)) event.item.itemStack = stack
        soon(player)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onOpen(event: InventoryOpenEvent) {
        if (event.inventory.holder is kr.inmc.core.gui.Menu) return
        sweep(event.inventory)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSwap(event: PlayerSwapHandItemsEvent) = soon(event.player)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onClick(event: InventoryClickEvent) {
        (event.whoClicked as? Player)?.let(::soon)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDrag(event: InventoryDragEvent) {
        (event.whoClicked as? Player)?.let(::soon)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onClose(event: InventoryCloseEvent) {
        (event.player as? Player)?.let(::soon)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onArmor(event: PlayerArmorChangeEvent) = soon(event.player)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDrop(event: PlayerDropItemEvent) = soon(event.player)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onBreak(event: PlayerItemBreakEvent) = soon(event.player)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onRespawn(event: PlayerRespawnEvent) = soon(event.player)

    /** 경험치 획득 +%. 경험치 구슬을 먹을 때마다. */
    @EventHandler(priority = EventPriority.HIGH)
    fun onExp(event: PlayerExpChangeEvent) {
        if (event.amount <= 0) return
        val bonus = custom.stats.of(event.player).stat(Stat.EXP_BONUS)
        if (bonus != 0.0) event.amount = (event.amount * (1.0 + bonus / 100.0)).toInt().coerceAtLeast(0)
    }
}
