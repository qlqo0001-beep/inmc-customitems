package com.inmc.customitems.listener

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.ItemBuilder
import com.inmc.customitems.item.ItemInstance
import com.inmc.customitems.item.Upgrades
import com.inmc.customitems.player.UpgradeService
import com.inmc.customitems.util.Ph
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.inventory.meta.Damageable
import java.util.Random

/**
 * 커서에 든 것을 아이템 위에 놓아 쓰는 것들 — 보석 박기, 보석 빼기, 감정, 분해, 강화, 진화, 수리.
 *
 * **자기 가방 칸에서만.** 상자나 화면 칸에서까지 받으면 남의 상자 속 아이템을 고치거나, 우리 화면의
 * 버튼 위에서 보석이 사라진다. 쓰일 것이 분명한 순간에만 사건을 취소한다 — 아니면 바닐라처럼 맞바꾼다.
 */
class ApplyListener(private val custom: CustomItems) : Listener {

    private val random = Random()

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onClick(event: InventoryClickEvent) {
        if (event.action != InventoryAction.SWAP_WITH_CURSOR) return
        if (event.clickedInventory !is PlayerInventory) return
        val player = event.whoClicked as? Player ?: return
        val cursor = event.cursor
        val target = event.currentItem ?: return
        val tool = custom.items.identify(cursor) ?: return
        val consume = tool.consume
        when {
            tool.gem != null -> socket(event, player, cursor, tool, target)
            consume?.unsocket == true -> unsocket(event, player, cursor, tool, target)
            consume?.identify == true -> identify(event, player, cursor, tool, target)
            consume?.deconstruct == true -> deconstruct(event, player, cursor, tool, target)
            consume?.upgrade != null -> upgrade(event, player, cursor, tool, target)
            consume?.evolve != null -> evolve(event, player, cursor, tool, target)
            consume != null && (consume.repair > 0 || consume.repairPercent > 0.0) -> repair(event, player, cursor, tool, target)
        }
    }

    /** 보석을 맞는 빈 소켓에. 실패하면 보석만 깨진다. */
    private fun socket(event: InventoryClickEvent, player: Player, cursor: ItemStack, gemItem: CustomItem, target: ItemStack) {
        val gem = gemItem.gem ?: return
        val definition = custom.items.identify(target) ?: return
        if (definition.sockets.isEmpty()) return
        // 여기부터는 "보석을 소켓 아이템에" 가 분명하다. 바닐라가 둘을 맞바꾸지 않게 한다.
        event.isCancelled = true
        if (target.amount != 1) return

        val instance = ItemInstance.read(target)
        val socket = definition.sockets.indices.firstOrNull { instance.gem(it) == null && gem.fits(definition.sockets[it]) }
        if (socket == null) {
            custom.messages.send(player, "gem-no-socket", Ph.of().item(gemItem.label()))
            return
        }

        takeOne(player, cursor)
        if (random.nextDouble() * 100.0 >= gem.chance) {
            player.playSound(player.location, Sound.BLOCK_GLASS_BREAK, 1f, 1f)
            custom.messages.send(player, "gem-failed", Ph.of().item(gemItem.label()))
            return
        }
        ItemBuilder.render(target, definition, instance.withGem(socket, gemItem.id), custom.items.lookup)
        event.currentItem = target
        player.playSound(player.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f)
        custom.messages.send(player, "gem-socketed", Ph.of().item(gemItem.label()))
        custom.stats.invalidate(player)
    }

    /** 마지막으로 박힌 보석을 빼 돌려준다. 보석 정의가 사라졌으면 그 칸만 비운다. */
    private fun unsocket(event: InventoryClickEvent, player: Player, cursor: ItemStack, tool: CustomItem, target: ItemStack) {
        val definition = custom.items.identify(target) ?: return
        if (definition.sockets.isEmpty()) return
        event.isCancelled = true
        if (target.amount != 1) return
        val instance = ItemInstance.read(target)
        val index = definition.sockets.indices.lastOrNull { instance.gem(it) != null }
        if (index == null) {
            custom.messages.send(player, "gem-nothing")
            return
        }
        val gemId = instance.gem(index)!!
        player.setItemOnCursor(custom.consumes.spend(cursor, tool))
        ItemBuilder.render(target, definition, instance.withGem(index, ""), custom.items.lookup)
        event.currentItem = target
        custom.items.create(gemId)?.let { gem -> for (left in player.inventory.addItem(gem).values) player.world.dropItemNaturally(player.location, left) }
        player.playSound(player.location, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 1f)
        custom.messages.send(player, "gem-removed", Ph.of().item(custom.items.get(gemId)?.label() ?: gemId))
        custom.stats.invalidate(player)
    }

    /** 감정. 겹쳐 있으면 한꺼번에 밝힌다 — 같은 몫을 나눠 가진 것들이다. */
    private fun identify(event: InventoryClickEvent, player: Player, cursor: ItemStack, tool: CustomItem, target: ItemStack) {
        val definition = custom.items.identify(target) ?: return
        val instance = ItemInstance.read(target)
        if (!instance.unidentified) return
        event.isCancelled = true
        player.setItemOnCursor(custom.consumes.spend(cursor, tool))
        ItemBuilder.render(target, definition, instance.copy(unidentified = false), custom.items.lookup)
        ItemBuilder.applyCustomEnchants(target, definition)
        event.currentItem = target
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f)
        custom.messages.send(player, "identified", Ph.of().item(ItemBuilder.name(definition, instance).ifBlank { definition.label() }))
        custom.stats.invalidate(player)
    }

    /** 분해. 한 개를 부숴 분해물을 주고, 박힌 보석은 돌려준다 — 보석까지 사라지면 아무도 안 쓴다. */
    private fun deconstruct(event: InventoryClickEvent, player: Player, cursor: ItemStack, tool: CustomItem, target: ItemStack) {
        val definition = custom.items.identify(target) ?: return
        if (definition.salvage.isEmpty()) return
        event.isCancelled = true
        val gems = ItemInstance.read(target).gems.filter { it.isNotEmpty() }
        player.setItemOnCursor(custom.consumes.spend(cursor, tool))
        target.amount -= 1
        event.currentItem = target.takeIf { it.amount > 0 }
        custom.crafting.give(player, definition.salvage)
        for (gemId in gems) custom.items.create(gemId)?.let { gem -> for (left in player.inventory.addItem(gem).values) player.world.dropItemNaturally(player.location, left) }
        player.playSound(player.location, Sound.BLOCK_GRINDSTONE_USE, 1f, 1f)
        custom.messages.send(player, "deconstructed", Ph.of().item(definition.label()))
        custom.stats.invalidate(player)
    }

    /** 강화석. 강화할 수 없는 아이템 위면 바닐라처럼 맞바꾼다. */
    private fun upgrade(event: InventoryClickEvent, player: Player, cursor: ItemStack, tool: CustomItem, target: ItemStack) {
        val definition = custom.items.identify(target) ?: return
        val table = definition.upgrade.table(custom.items.lookup)?.takeIf { it.maxLevel > 0 } ?: return
        event.isCancelled = true
        if (target.amount != 1) return
        val instance = ItemInstance.read(target)
        val label = Ph.of().item(definition.label())
        if (instance.unidentified) return custom.messages.send(player, "upgrade-unidentified", label)
        if (instance.level >= table.maxLevel) return custom.messages.send(player, "upgrade-max", label)
        val stone = tool.consume?.upgrade ?: return
        Upgrades.refuse(tool.id, stone, definition, instance.level)?.let { return custom.messages.send(player, it, label) }

        player.setItemOnCursor(custom.consumes.spend(cursor, tool))
        val attempt = custom.upgrading.attempt(player, target, definition, table, stone)
        event.currentItem = attempt.stack
        val (sound, key) = when (attempt.outcome) {
            UpgradeService.Outcome.SUCCESS -> Sound.BLOCK_ANVIL_USE to "upgrade-success"
            UpgradeService.Outcome.KEPT -> Sound.BLOCK_ANVIL_LAND to "upgrade-fail-keep"
            UpgradeService.Outcome.DOWN -> Sound.BLOCK_ANVIL_LAND to "upgrade-fail-down"
            UpgradeService.Outcome.RESET -> Sound.BLOCK_ANVIL_LAND to "upgrade-fail-reset"
            UpgradeService.Outcome.DESTROYED -> Sound.ENTITY_ITEM_BREAK to "upgrade-destroyed"
        }
        player.playSound(player.location, sound, 0.8f, if (attempt.outcome == UpgradeService.Outcome.SUCCESS) 1.4f else 0.8f)
        custom.messages.send(player, key, label.amount(attempt.level))
        custom.stats.invalidate(player)
    }

    /** 진화석. 진화 설정이 없는 아이템 위면 바닐라처럼 맞바꾼다. */
    private fun evolve(event: InventoryClickEvent, player: Player, cursor: ItemStack, tool: CustomItem, target: ItemStack) {
        val definition = custom.items.identify(target) ?: return
        val evolution = definition.upgrade.evolution ?: return
        event.isCancelled = true
        if (target.amount != 1) return
        val label = Ph.of().item(definition.label())
        val stone = tool.consume?.evolve ?: return
        if (!evolution.byStone || (stone.items.isNotEmpty() && definition.id !in stone.items)) return custom.messages.send(player, "evolve-wrong-stone", label)
        if (!Upgrades.canEvolve(definition, ItemInstance.read(target).level, custom.items.lookup)) return custom.messages.send(player, "evolve-not-ready", label)

        player.setItemOnCursor(custom.consumes.spend(cursor, tool))
        val evolved = custom.upgrading.evolve(player, target, definition) ?: return
        event.currentItem = evolved
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f)
        custom.messages.send(player, "evolved", Ph.of().item(custom.items.get(evolution.into)?.label() ?: evolution.into))
        custom.stats.invalidate(player)
    }

    /** 내구도를 고친다. 우리 아이템이 아니어도 닳는 것이면 된다. */
    private fun repair(event: InventoryClickEvent, player: Player, cursor: ItemStack, tool: CustomItem, target: ItemStack) {
        val meta = target.itemMeta as? Damageable ?: return
        val max = if (meta.hasMaxDamage()) meta.maxDamage else target.type.maxDurability.toInt()
        if (max <= 0) return
        event.isCancelled = true
        if (!meta.hasDamage() || meta.damage <= 0) {
            custom.messages.send(player, "repair-nothing")
            return
        }
        val spec = tool.consume ?: return
        val amount = (spec.repair + (max * spec.repairPercent / 100.0).toInt()).coerceAtMost(meta.damage)
        meta.damage -= amount
        target.itemMeta = meta
        event.currentItem = target
        player.setItemOnCursor(custom.consumes.spend(cursor, tool))
        player.playSound(player.location, Sound.BLOCK_ANVIL_USE, 0.6f, 1.4f)
        custom.messages.send(player, "repaired", Ph.of().item(custom.items.identify(target)?.label() ?: target.type.name).amount(amount))
    }

    private fun takeOne(player: Player, cursor: ItemStack) {
        cursor.amount -= 1
        player.setItemOnCursor(cursor.takeIf { it.amount > 0 })
    }
}
