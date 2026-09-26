package com.inmc.customitems.listener

import com.inmc.customitems.CustomItems
import com.inmc.customitems.command.CustomItemsCommand
import com.inmc.customitems.craft.Station
import com.inmc.customitems.gui.StationMenu
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

/** 제작대에 연결한 블록을 우클릭하면 그 제작대가 열린다. 그 블록의 원래 동작(상자 열기 등)은 막는다. */
class StationListener(private val custom: CustomItems) : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK || event.hand != EquipmentSlot.HAND) return
        val block = event.clickedBlock ?: return
        val station = custom.stations.at(Station.blockKey(block.world.name, block.x, block.y, block.z)) ?: return
        event.isCancelled = true
        if (!event.player.hasPermission(CustomItemsCommand.CRAFT)) {
            custom.messages.send(event.player, "no-permission")
            return
        }
        StationMenu(custom, event.player, station.id).open(event.player)
    }
}
