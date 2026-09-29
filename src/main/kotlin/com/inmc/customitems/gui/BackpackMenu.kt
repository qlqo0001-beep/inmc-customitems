package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import com.inmc.customitems.player.BackpackLayout
import kr.inmc.core.gui.Icon
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * 배낭 한 개의 창고 화면 — 45칸씩 페이지, 페이지가 둘 이상이면 맨 아래 줄에서 넘긴다([BackpackLayout]).
 *
 * **내용 칸은 바닐라처럼 옮긴다**(끌기·Shift·숫자키·두 번 클릭 모으기). 옮긴 결과는 사건이 끝난 뒤에 창에 들어오므로 **다음 틱에**
 * 지금 페이지를 저장소([contents])에 옮겨 적고 파일에 쓴다. 페이지를 넘길 때와 닫을 때도 먼저 옮겨 적는다. 창이 저장소의 그림이라
 * 둘이 어긋나면 복사되는데, 한 번에 한 사람만 열고([com.inmc.customitems.player.Backpacks]) 모든 이동 뒤에 옮겨 적으므로 어긋날 틈이 없다.
 *
 * 배낭 안에 배낭은 넣지 못한다(셜커 상자처럼) — 열린 그 배낭을 넣으면 그 안의 것이 그 안에 갇힌다.
 */
class BackpackMenu(
    custom: CustomItems,
    private val viewer: Player,
    /** 배낭 번호. */
    val id: UUID,
    label: String,
    /** 칸 번호 → 아이템. 창을 여는 동안 이것이 진짜다. */
    private val contents: HashMap<Int, ItemStack>,
    size: Int,
) : Menu(
    custom,
    BackpackLayout.rows(BackpackLayout.capacity(size, contents.keys.maxOrNull())) * 9,
    Text.renderFlat(label),
) {

    private val capacity = BackpackLayout.capacity(size, contents.keys.maxOrNull())
    private val pages = BackpackLayout.pages(capacity)
    private var page = 0
    private var pending = false
    private var closed = false

    /** 내용 칸 수 — 페이지가 여럿이면 넘기기 줄을 뺀 45칸. */
    private val contentSlots: Int get() = if (pages > 1) BackpackLayout.PER_PAGE else size

    override fun draw() {
        clear()
        for (slot in 0 until contentSlots) {
            if (BackpackLayout.usable(capacity, page, slot)) set(slot, contents[BackpackLayout.index(page, slot)]?.clone())
            else set(slot, Icon.EDGE)
        }
        if (pages <= 1) return
        for (slot in BackpackLayout.PER_PAGE until size) set(slot, Icon.EDGE)
        if (page > 0) set(SLOT_PREV, Icon.prevPage()) { turn(-1) }
        set(SLOT_PAGE, Icon.of(Material.BOOK, "<yellow>" + (page + 1) + " / " + pages + " 페이지</yellow>", listOf("<gray>" + capacity + "칸</gray>")))
        if (page < pages - 1) set(SLOT_NEXT, Icon.nextPage()) { turn(1) }
    }

    override fun isSlotEditable(slot: Int): Boolean = slot < contentSlots && BackpackLayout.usable(capacity, page, slot)

    override fun acceptsShiftInsert(): Boolean = true

    override fun handleClick(event: InventoryClickEvent) {
        if (bringsBackpackIn(event)) {
            event.isCancelled = true
            custom.messages.send(viewer, "backpack-nested")
            return
        }
        super.handleClick(event)
        if (!event.isCancelled) later()
    }

    override fun onDrag(event: InventoryDragEvent) {
        super.onDrag(event)
        if (event.isCancelled) return
        if (event.rawSlots.any { it < size } && isBackpack(event.oldCursor)) {
            event.isCancelled = true
            custom.messages.send(viewer, "backpack-nested")
            return
        }
        later()
    }

    /** 이 클릭이 배낭을 창 안으로 들이는가 — 커서로 놓기 · 숫자키 · F(왼손) · 가방에서 Shift. */
    private fun bringsBackpackIn(event: InventoryClickEvent): Boolean {
        val top = event.rawSlot in 0 until size
        return when {
            top && isBackpack(event.view.cursor) -> true
            top && event.click == ClickType.NUMBER_KEY -> isBackpack(viewer.inventory.getItem(event.hotbarButton))
            top && event.click == ClickType.SWAP_OFFHAND -> isBackpack(viewer.inventory.itemInOffHand)
            !top && event.isShiftClick -> isBackpack(event.currentItem)
            else -> false
        }
    }

    private fun isBackpack(stack: ItemStack?): Boolean = custom.items.identify(stack)?.isBackpack == true

    /** 지금 페이지의 칸을 저장소에 옮겨 적고 곧바로 파일에 쓴다. */
    private fun commit() {
        for (slot in 0 until contentSlots) {
            if (!BackpackLayout.usable(capacity, page, slot)) continue
            val index = BackpackLayout.index(page, slot)
            val stack = inventory.getItem(slot)
            if (stack == null || stack.type.isAir) contents.remove(index) else contents[index] = stack.clone()
        }
        custom.backpacks.save(id, contents)
    }

    /** 바닐라가 옮긴 결과는 사건이 끝난 뒤에 창에 들어온다 — 다음 틱에 옮겨 적는다. 한 틱에 여러 번이면 한 번만. */
    private fun later() {
        if (pending) return
        pending = true
        viewer.scheduler.run(custom.plugin, {
            pending = false
            if (!closed) commit()
        }, null)
    }

    private fun turn(delta: Int) {
        commit()
        page = (page + delta).coerceIn(0, pages - 1)
        refresh()
    }

    override fun onClose(event: InventoryCloseEvent) {
        if (closed) return
        closed = true
        commit()
        custom.backpacks.release(id, viewer.uniqueId)
        // 가방 쪽도 지금 적어 둔다 — 배낭 파일만 새것이고 가방은 몇 분 전 것인 채로 서버가 죽으면 옮긴 물건이 둘이 되거나 사라진다.
        runCatching { viewer.saveData() }
    }

    companion object {
        const val SLOT_PREV = 48
        const val SLOT_PAGE = 49
        const val SLOT_NEXT = 50
    }
}
