package com.inmc.customitems.listener

import com.inmc.customitems.CustomItems
import com.inmc.customitems.block.Mining
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockDamageAbortEvent
import org.bukkit.event.block.BlockDamageEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.NotePlayEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.util.BoundingBox

/**
 * 커스텀 블록 놓기·캐기·부수기와 바닐라가 모양을 바꾸는 길 막기. 판단은 [com.inmc.customitems.block.CustomBlocks] 가, 캐는 시간은
 * [com.inmc.customitems.block.BlockMining] 이 한다.
 *
 * **값은 HIGH 에서 고치고, 떨구기·치우기는 MONITOR 에서 한다**(규칙 11). 랜덤박스가 상자를 HIGHEST 에서 지킨다 — 그 전에 우리가
 * 엔티티 모델을 치우거나 아이템을 떨구면 부서지지 않은 상자에서 아이템이 나온다.
 *
 * 블록 아이템이 하나도 없으면 사건마다 곧바로 돌아간다([com.inmc.customitems.item.ItemRegistry.hasBlocks]).
 */
class BlockListener(private val custom: CustomItems) : Listener {

    // --- 놓기 · 소리블록 조율 막기 ------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    fun onInteract(event: PlayerInteractEvent) {
        if (!custom.items.hasBlocks) return
        val clicked = event.clickedBlock ?: return
        when (event.action) {
            Action.RIGHT_CLICK_BLOCK -> {
                val clickedOurs = if (clicked.type in HOSTS) custom.blocks.at(clicked) else null
                // 우클릭은 소리블록의 음을 바꾼다 — 우리 블록이면 모양이 바뀐다. 블록 쓰기만 막고 손의 아이템(블록 놓기)은 그대로.
                if (clickedOurs != null && clicked.type == Material.NOTE_BLOCK) event.setUseInteractedBlock(Event.Result.DENY)
                place(event, clicked, clickedOurs != null)
            }
            else -> Unit
        }
    }

    private fun place(event: PlayerInteractEvent, clicked: Block, clickedOurs: Boolean) {
        if (event.useItemInHand() == Event.Result.DENY) return
        val hand = event.hand ?: return
        val stack = event.item ?: return
        val item = custom.items.identify(stack)?.takeIf { it.block != null } ?: return
        val player = event.player
        // 상자·문 같은 것은 웅크리지 않으면 그쪽을 연다(바닐라 블록 놓기와 같다).
        @Suppress("DEPRECATION")
        if (!player.isSneaking && !clickedOurs && clicked.type.isInteractable) return

        // 어느 쪽이든 바닐라는 아무것도 안 한다 — 재질이 블록이면 그 블록까지 놓인다.
        event.setUseItemInHand(Event.Result.DENY)
        event.setUseInteractedBlock(Event.Result.DENY)

        val target = if (clicked.isReplaceable) clicked else clicked.getRelative(event.blockFace)
        if (!target.isReplaceable || target.y < target.world.minHeight || target.y >= target.world.maxHeight) return
        val box = BoundingBox.of(target)
        if (target.world.getNearbyEntities(box) { it is LivingEntity && !(it is Player && it.gameMode == GameMode.SPECTATOR) }.isNotEmpty()) return

        val replaced = target.state
        if (!custom.blocks.place(target, item)) return
        // 놓은 뒤에 묻는다(바닐라와 같다) — 땅 보호 플러그인이 취소하면 되돌린다.
        val placeEvent = BlockPlaceEvent(target, replaced, clicked, stack, player, true, hand)
        if (!placeEvent.callEvent() || !placeEvent.canBuild()) {
            custom.blocks.clear(target)
            replaced.update(true, false)
            return
        }
        if (player.gameMode != GameMode.CREATIVE) stack.amount -= 1
        target.world.playSound(target.location.add(0.5, 0.5, 0.5), target.blockData.soundGroup.placeSound, 1f, 0.8f)
        player.swingHand(hand)
    }

    // --- 캐기 (시간 들여) ---------------------------------------------------------------

    /**
     * 치기 시작 — 우리 블록이면 [com.inmc.customitems.block.BlockMining] 이 센다. 다른 블록을 치기 시작하면 캐던 것은 끝.
     * 크리에이티브는 이 사건 전에 바닐라가 한 번에 부순다.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    fun onDamage(event: BlockDamageEvent) {
        if (!custom.items.hasBlocks) return
        val item = if (!event.isCancelled && event.block.type in HOSTS) custom.blocks.at(event.block) else null
        if (item == null) {
            custom.mining.stop(event.player)
            return
        }
        // 효율·성급함이 높으면 바닐라가 한 번에 부수려 한다(소리블록 0.8 · 후렴초 0.4).
        event.instaBreak = false
        custom.mining.start(event.player, event.block, item)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onAbort(event: BlockDamageAbortEvent) = custom.mining.stop(event.player)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) = custom.mining.stop(event.player)

    // --- 부수기 -----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) {
        if (!custom.items.hasBlocks || event.block.type !in HOSTS) return
        val spec = custom.blocks.at(event.block)?.block ?: return
        val player = event.player
        // 다른 플러그인이 대신 부수는 길(여러 칸 캐기 인첸트 …)도 도구 규칙을 지킨다.
        if (player.gameMode == GameMode.SURVIVAL && !Mining.canBreak(spec, custom.mining.held(player))) {
            event.isCancelled = true
            return
        }
        // 바닐라 것(소리블록·후렴초 열매)이 안 나오게. 우리 것은 부서진 것이 확정된 뒤에(afterBreak).
        event.isDropItems = false
        event.expToDrop = if (player.gameMode == GameMode.CREATIVE) 0 else custom.blocks.expFor(spec, player)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun afterBreak(event: BlockBreakEvent) {
        if (!custom.items.hasBlocks || event.block.type !in HOSTS) return
        val item = custom.blocks.at(event.block) ?: return
        custom.blocks.clear(event.block)
        if (event.player.gameMode != GameMode.CREATIVE) drop(event.block, custom.blocks.loot(item, event.player))
    }

    /** 폭발 — 남은 목록의 우리 블록을 먼저 비우고 우리 아이템을 떨군다(바닐라가 소리블록·후렴초 열매를 떨구지 않게). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) = exploded(event.blockList(), event.yield)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) = exploded(event.blockList(), event.yield)

    private fun exploded(blocks: List<Block>, yield: Float) {
        if (!custom.items.hasBlocks) return
        for (block in blocks) {
            if (block.type !in HOSTS) continue
            val item = custom.blocks.at(block) ?: continue
            custom.blocks.clear(block)
            block.setType(Material.AIR, false)
            if (Math.random() < yield) drop(block, custom.blocks.loot(item, null))
        }
    }

    private fun drop(block: Block, stacks: List<org.bukkit.inventory.ItemStack>) {
        val center = block.location.add(0.5, 0.5, 0.5)
        for (stack in stacks) block.world.dropItemNaturally(center, stack)
    }

    // --- 바닐라가 모양을 바꾸는 길 ----------------------------------------------------------

    /** 소리블록을 치거나 레드스톤이 울리게 하면 음이 난다 — 우리 블록은 소리블록이 아니다. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onNote(event: NotePlayEvent) {
        if (custom.items.hasBlocks && custom.blocks.at(event.block) != null) event.isCancelled = true
    }

    /** 피스톤은 후렴초를 부순다(바닐라 열매가 나온다). 소리블록은 상태째 밀려 그대로 우리 블록이라 둔다. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        if (pushesOurs(event.blocks)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        if (pushesOurs(event.blocks)) event.isCancelled = true
    }

    private fun pushesOurs(blocks: List<Block>): Boolean =
        custom.items.hasBlocks && blocks.any { it.type == Material.CHORUS_PLANT && custom.blocks.at(it) != null }

    /**
     * 휠클릭(블록 고르기)으로 커스텀 블록 — 바닐라는 받침 블록(소리 블록·후렴초·방벽)을 준다. 우리가 막고 그 블록의 아이템으로 같은 일을 한다
     * (테섭 요청 2026-10-02): 가방에 있으면 단축바로 가져와 들고, 없으면 창작 모드에서만 하나 만들어 쥐어 준다.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPick(event: io.papermc.paper.event.player.PlayerPickBlockEvent) {
        if (event.block.type !in HOSTS) return
        val item = custom.blocks.at(event.block) ?: return
        event.isCancelled = true
        val inventory = event.player.inventory
        val owned = (0 until 36).firstOrNull { custom.items.identify(inventory.getItem(it))?.id == item.id }
        if (owned != null && owned < 9) {
            inventory.heldItemSlot = owned
            return
        }
        val target = (0 until 9).firstOrNull { inventory.getItem(it)?.isEmpty != false } ?: inventory.heldItemSlot
        if (owned != null) {
            val previous = inventory.getItem(target)
            inventory.setItem(target, inventory.getItem(owned))
            inventory.setItem(owned, previous)
        } else {
            if (event.player.gameMode != GameMode.CREATIVE) return
            inventory.setItem(target, custom.items.create(item.id) ?: return)
        }
        inventory.heldItemSlot = target
    }

    private companion object {
        /** 우리 블록이 설 수 있는 바닐라 블록. 이것이 아니면 [com.inmc.customitems.block.CustomBlocks.at] 을 부르지도 않는다. */
        val HOSTS = setOf(Material.NOTE_BLOCK, Material.CHORUS_PLANT, Material.BARRIER)
    }
}
