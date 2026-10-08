package com.inmc.customitems.listener

import com.inmc.customitems.CustomItems
import com.inmc.customitems.ability.Trigger
import com.inmc.customitems.player.EquipmentStore
import kr.inmc.core.input.Clicks
import org.bukkit.event.Event
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot

/**
 * 좌/우클릭 기능과 소모품. 요구 조건이 모자라면 둘 다 안 돈다.
 *
 * **기능은 이벤트를 취소하지 않는다.** (소모품은 바닐라 사용을 막는다 — 물약을 쓰는데 마시기도 하면 안 된다.) 검을 우클릭해 기능을 쓰면서 방패도 들 수 있어야 하고,
 * 곡괭이에 기능을 붙였다고 블록을 못 캐면 안 된다. 재질의 바닐라 동작(설치·먹기)을 막는 것은 기능이 아니라 별개의 설정이다
 * ([com.inmc.customitems.item.CustomItem.preventVanillaUse] — [onPlace]·[onConsume]).
 */
class InteractListener(private val custom: CustomItems) : Listener {

    private val quickEquip = com.inmc.customitems.player.QuickEquip(custom)

    /** 허공 클릭은 처음부터 '취소됨'으로 태어난다(클릭한 블록이 없어 블록 사용이 DENY) — `ignoreCancelled` 로 받으면 허공 클릭이 통째로 빠진다. 다른 플러그인이 막았는지는 아이템 사용 쪽을 본다. */
    @EventHandler(priority = EventPriority.NORMAL)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.useItemInHand() == Event.Result.DENY) return
        // 한 번의 클릭이 주 손과 왼손으로 두 번 들어온다. 한 번만 센다.
        if (event.hand != EquipmentSlot.HAND) return
        // 우클릭에 딸려 오는 손 흔들기 유령 좌클릭 — 받으면 우클릭 한 번에 좌클릭 기능까지 돈다.
        if (Clicks.isGhost(event)) return

        var trigger = when (event.action) {
            Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK -> Trigger.RIGHT_CLICK
            Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK -> Trigger.LEFT_CLICK
            else -> return
        }

        val player = event.player
        val stack = player.inventory.itemInMainHand
        // 사용 기간이 끝난 것은 쓰지 못한다(사용자 결정 2026-09-30) — 활 당기기·던지기·먹기 같은 바닐라 사용까지.
        if (custom.items.isExpired(stack)) {
            event.setUseItemInHand(Event.Result.DENY)
            if (trigger == Trigger.RIGHT_CLICK) custom.messages.send(player, "item-expired-use")
            return
        }
        val item = custom.items.usable(stack) ?: return
        // 배낭(장신구·부적·유물)은 우클릭이 창고를 연다 — 장착 칸에서만 효과가 나는 종류여도(효과가 아니라 여는 것이다).
        // 웅크려 우클릭 기능이 있으면 그쪽으로, 상자·문 같은 블록을 누르면 그 블록을 연다.
        if (trigger == Trigger.RIGHT_CLICK && item.isBackpack && !(player.isSneaking && item.abilities.any { it.trigger == Trigger.SHIFT_RIGHT_CLICK }) && !opensBlock(event)) {
            if (!custom.requirements.check(player, item)) return
            event.setUseItemInHand(Event.Result.DENY)
            event.setUseInteractedBlock(Event.Result.DENY)
            custom.backpacks.openHand(player)
            return
        }
        // 장신구·부적·유물은 우클릭이 장착이다(사용자 요청 2026-10-07 — 배낭은 위에서 연다). 손에서도 효과가 나면서 우클릭 기능이
        // 붙은 것은 기능이 먼저고(장착은 /acc), 소모품도 그대로. 상자·문을 누르면 그 블록이 먼저다.
        if (trigger == Trigger.RIGHT_CLICK && item.consume == null && !opensBlock(event)) {
            val group = EquipmentStore.Group.of(item.type)?.takeIf { it != EquipmentStore.Group.BACKPACK }
            if (group != null && player.hasPermission(com.inmc.customitems.command.CustomItemsCommand.EQUIPMENT) && !rightClickAbility(item, player)) {
                event.setUseItemInHand(Event.Result.DENY)
                event.setUseInteractedBlock(Event.Result.DENY)
                quickEquip.equip(player, group)
                return
            }
        }
        // 장착 칸에서만 효과가 나는 장신구·부적·유물은 손에 들고 눌러도 아무 일이 없다.
        if (!custom.equipmentSettings.worksOutside(item)) return
        if (!custom.requirements.check(player, item)) return
        // 웅크린 채면 웅크려 클릭 기능이 있을 때만 그쪽으로. 없으면 평소 클릭 그대로다.
        if (player.isSneaking) {
            val shifted = if (trigger == Trigger.RIGHT_CLICK) Trigger.SHIFT_RIGHT_CLICK else Trigger.SHIFT_LEFT_CLICK
            if (item.abilities.any { it.trigger == shifted }) trigger = shifted
        }

        // 원거리 공격 방식. 우클릭으로 쐈으면 그 재질의 바닐라 사용(당기기·놓기)은 막는다.
        val left = trigger == Trigger.LEFT_CLICK || trigger == Trigger.SHIFT_LEFT_CLICK
        if (custom.styles.click(player, item.style, left) && !left) event.setUseItemInHand(Event.Result.DENY)

        val consume = item.consume
        if (trigger == Trigger.RIGHT_CLICK && consume != null && !consume.dragOnly) {
            // 바닐라의 먹기·마시기·놓기 대신 우리 것이 쓰인다.
            event.setUseItemInHand(Event.Result.DENY)
            event.setUseInteractedBlock(Event.Result.DENY)
            player.inventory.setItemInMainHand(custom.consumes.use(player, stack, item))
            return
        }
        custom.abilities.fire(player, item, trigger)
    }

    /** 손에 든 채 우클릭하면 도는 기능이 있는가 — 장착 칸에서만 효과가 나는 것은 손에서 기능이 안 돌므로 없는 셈. */
    private fun rightClickAbility(item: com.inmc.customitems.item.CustomItem, player: org.bukkit.entity.Player): Boolean =
        custom.equipmentSettings.worksOutside(item) &&
            item.abilities.any { it.trigger == Trigger.RIGHT_CLICK || (player.isSneaking && it.trigger == Trigger.SHIFT_RIGHT_CLICK) }

    /** 웅크리지 않고 상자·문처럼 여는 블록을 눌렀는가 — 그러면 배낭보다 그 블록이 먼저다(바닐라와 같다). */
    @Suppress("DEPRECATION")
    private fun opensBlock(event: PlayerInteractEvent): Boolean =
        event.action == Action.RIGHT_CLICK_BLOCK && !event.player.isSneaking && event.clickedBlock?.type?.isInteractable == true

    /**
     * 바닐라 설치 막기([com.inmc.customitems.item.CustomItem.preventVanillaUse]) — 재질이 블록인 아이템이 그 블록으로 놓이지 않게.
     * 커스텀 블록(블록 설정)은 빼고 — 그건 우리가 놓으며 땅 보호를 묻는 이 사건을 직접 쏜다(`BlockListener`).
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) {
        val item = custom.items.identify(event.itemInHand) ?: return
        if (item.preventVanillaUse && item.block == null) event.isCancelled = true
        // 사용 기간이 끝난 것은 놓지도 못한다(커스텀 블록 포함 — BlockListener 가 이 사건을 직접 쏘므로 여기서 같이 막힌다).
        if (custom.items.isExpired(event.itemInHand)) event.isCancelled = true
    }

    /**
     * 바닐라 먹기·마시기 막기. 막은 아이템은 먹는 부품을 떼어 두어 보통은 여기까지 오지 않는다(`ItemBuilder.render`) — 설정을 켜기 전에
     * 나가 아직 다시 그려지지 않은 아이템을 위한 것이다.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onConsume(event: PlayerItemConsumeEvent) {
        if (custom.items.identify(event.item)?.preventVanillaUse == true || custom.items.isExpired(event.item)) event.isCancelled = true
    }

    /**
     * 지식책 바닐라 사용 막기([com.inmc.customitems.item.CustomItem.preventVanillaUse]) — 지식책은 우클릭하면
     * 레시피를 풀고 **책을 먹는다.** 설치·먹기와 달리 먹는 부품을 뗄 수 없어 설정 전후를 막론하고 여기까지 온다 —
     * "사용 안 됨"을 켜도 우클릭 한 번에 커스텀 아이템이 통째로 사라졌다. 양손 둘 다 본다(주 손만 보면 왼손 책이 샌다).
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onKnowledgeBook(event: PlayerInteractEvent) {
        val stack = event.item ?: return
        if (stack.type != Material.KNOWLEDGE_BOOK) return
        if (custom.items.identify(stack)?.preventVanillaUse != true) return
        event.setUseItemInHand(Event.Result.DENY)
    }

    /** 나간 사람의 쿨다운은 들고 있을 이유가 없다. */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        custom.abilities.forget(event.player.uniqueId)
        custom.consumes.forget(event.player.uniqueId)
        custom.requirements.forget(event.player.uniqueId)
        custom.styles.forget(event.player.uniqueId)
    }
}
