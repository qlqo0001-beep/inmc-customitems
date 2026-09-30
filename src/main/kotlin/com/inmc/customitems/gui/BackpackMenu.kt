package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import com.inmc.customitems.player.BackpackLayout
import kr.inmc.core.gui.Icon
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * 배낭 한 개의 창고 화면 — 45칸씩 페이지([BackpackLayout]). 내용 줄 아래 **조작 줄**: 가운데는 페이지 넘기기, 양옆은 장착 칸 배낭 줄의
 * **배낭 번호 버튼**(사용자 요청 2026-09-30 — G 로 1번을 연 뒤 여기서 한 번에 다른 배낭으로). 넘길 페이지도 바꿀 배낭도 없으면 조작 줄은 없다.
 *
 * **내용 칸은 바닐라처럼 옮긴다**(끌기·Shift·숫자키·두 번 클릭 모으기). 옮긴 결과는 사건이 끝난 뒤에 창에 들어오므로 **다음 틱에**
 * 지금 페이지를 저장소([contents])에 옮겨 적고 파일에 쓴다. 페이지를 넘길 때·다른 배낭으로 바꿀 때·닫을 때도 먼저 옮겨 적는다. 창이
 * 저장소의 그림이라 둘이 어긋나면 복사되는데, 한 번에 한 사람만 열고([com.inmc.customitems.player.Backpacks]) 모든 이동 뒤에 옮겨 적으므로
 * 어긋날 틈이 없다.
 *
 * 배낭 안에 배낭은 넣지 못한다(셜커 상자처럼) — 열린 그 배낭을 넣으면 그 안의 것이 그 안에 갇힌다.
 */
class BackpackMenu(
    custom: CustomItems,
    private val viewer: Player,
    /** 배낭 번호. */
    val id: UUID,
    /** 창 제목 — 배낭 아이템에 보이는 이름 그대로(사용자 요청 2026-09-30). */
    title: net.kyori.adventure.text.Component,
    /** 칸 번호 → 아이템. 창을 여는 동안 이것이 진짜다. */
    private val contents: HashMap<Int, ItemStack>,
    size: Int,
    /** 장착 칸 배낭 줄의 배낭들(번호 순서) — 조작 줄의 번호 버튼. */
    private val bags: List<ItemStack> = emptyList(),
    /** 이 배낭이 [bags] 의 몇째인가(0부터). 손에 들고 연 것처럼 줄에 없으면 -1. */
    private val current: Int = -1,
) : Menu(
    custom,
    BackpackLayout.rows(BackpackLayout.capacity(size, contents.keys.maxOrNull()), control = bags.indices.any { it != current }) * 9,
    title,
) {

    private val capacity = BackpackLayout.capacity(size, contents.keys.maxOrNull())
    private val pages = BackpackLayout.pages(capacity)
    private var page = 0
    private var pending = false
    private var closed = false

    /** 내용 칸 수 — 조작 줄을 뺀 칸. 조작 줄은 늘 그 바로 아래 줄이다. */
    private val contentSlots: Int = BackpackLayout.contentRows(capacity) * 9

    override fun draw() {
        clear()
        for (slot in 0 until contentSlots) {
            if (BackpackLayout.usable(capacity, page, slot)) set(slot, contents[BackpackLayout.index(page, slot)]?.clone())
            else set(slot, Icon.EDGE)
        }
        if (size <= contentSlots) return
        for (slot in contentSlots until size) set(slot, Icon.EDGE)
        if (pages > 1) {
            if (page > 0) set(contentSlots + COLUMN_PREV, Icon.prevPage()) { turn(-1) }
            set(contentSlots + COLUMN_PAGE, Icon.of(Material.BOOK, "<yellow>" + (page + 1) + " / " + pages + " 페이지</yellow>", listOf("<gray>" + capacity + "칸</gray>")))
            if (page < pages - 1) set(contentSlots + COLUMN_NEXT, Icon.nextPage()) { turn(1) }
        }
        drawBags()
    }

    /**
     * 배낭 번호 버튼 — 누르면 지금 것을 옮겨 적고 그 배낭으로 바꿔 연다(`/배낭 N` 과 같은 길). 자리가 모자라면(페이지가 여럿인데
     * 배낭이 일곱 이상) 마지막 자리가 "나머지 배낭" 고르기다.
     */
    private fun drawBags() {
        if (bags.indices.none { it != current }) return
        val columns = BackpackLayout.switchColumns(pages)
        val shown = if (bags.size <= columns.size) bags.size else columns.size - 1
        for (index in 0 until shown) {
            val here = index == current
            val icon = Icon.annotate(bags[index].clone(), lore = listOf(
                "<gold>" + (index + 1) + "번 배낭</gold> <dark_gray>/배낭 " + (index + 1) + "</dark_gray>",
                if (here) "<green>▶ 지금 보는 배낭</green>" else "<yellow>▶ 클릭: 이 배낭으로</yellow>",
            )).apply { if (here) editMeta { it.setEnchantmentGlintOverride(true) } }
            set(contentSlots + columns[index], icon) { if (!here) switchTo(index + 1) }
        }
        if (shown < bags.size) {
            set(contentSlots + columns.last(), Icon.of(Material.CHEST, "<gold>나머지 배낭</gold>", listOf(
                "<gray>" + (shown + 1) + "번 ~ " + bags.size + "번</gray>", "", "<yellow>▶ 클릭: 고르기</yellow>",
            ))) { chooseRest(shown) }
        }
    }

    private fun switchTo(number: Int) {
        commit()
        custom.backpacks.openNumber(viewer, number)
    }

    private fun chooseRest(from: Int) {
        commit()
        ChoiceMenu(
            custom, viewer, "배낭 고르기",
            options = { bags.indices.drop(from).map { index -> (index + 1).toString() to Icon.annotate(bags[index].clone(), lore = listOf("<gold>" + (index + 1) + "번 배낭</gold>")) } },
            selected = { emptySet() },
            back = { custom.backpacks.openNumber(viewer, 1) },
        ) { picked -> picked.toIntOrNull()?.let { custom.backpacks.openNumber(viewer, it) } }.open(viewer)
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
        if (event.rawSlots.any { it < contentSlots } && isBackpack(event.oldCursor)) {
            event.isCancelled = true
            custom.messages.send(viewer, "backpack-nested")
            return
        }
        later()
    }

    /** 이 클릭이 배낭을 내용 칸으로 들이는가 — 커서로 놓기 · 숫자키 · F(왼손) · 가방에서 Shift. 조작 줄의 버튼은 들이지 않는다. */
    private fun bringsBackpackIn(event: InventoryClickEvent): Boolean {
        val top = event.rawSlot in 0 until contentSlots
        return when {
            top && isBackpack(event.view.cursor) -> true
            top && event.click == ClickType.NUMBER_KEY -> isBackpack(viewer.inventory.getItem(event.hotbarButton))
            top && event.click == ClickType.SWAP_OFFHAND -> isBackpack(viewer.inventory.itemInOffHand)
            event.rawSlot >= size && event.isShiftClick -> isBackpack(event.currentItem)
            else -> false
        }
    }

    private fun isBackpack(stack: ItemStack?): Boolean = custom.items.identify(stack)?.isBackpack == true

    /** 지금 페이지의 칸을 저장소에 옮겨 적고 곧바로 파일에 쓴다. */
    private fun commit() {
        if (closed) return
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
        commit()
        closed = true
        custom.backpacks.release(id, viewer.uniqueId)
        // 가방 쪽도 지금 적어 둔다 — 배낭 파일만 새것이고 가방은 몇 분 전 것인 채로 서버가 죽으면 옮긴 물건이 둘이 되거나 사라진다.
        runCatching { viewer.saveData() }
    }

    companion object {
        /** 조작 줄 안의 페이지 넘기기 자리(가운데 셋, 열 번호). 페이지가 여럿이면 조작 줄이 45번부터라 48·49·50번 칸이다. */
        const val COLUMN_PREV = 3
        const val COLUMN_PAGE = 4
        const val COLUMN_NEXT = 5
    }
}
