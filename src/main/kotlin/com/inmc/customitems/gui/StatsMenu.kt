package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.Spread
import com.inmc.customitems.item.Stat
import com.inmc.customitems.item.StatCalc
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.util.Numbers
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent

/**
 * 능력치 설정.
 *
 * 능력치가 예순 개가 넘어 **묶음으로 걸러 본다**(공격·방어·원소·이동·그 밖). 붙어 있는 것과 이 재질에
 * 어울리는 것을 **앞에** 놓는다 — 장화 편집 화면 첫 줄이 공격 속도면 관리자가 잘못 만든다.
 *
 * 두 가지 모드: **값**(기준값) · **무작위 폭**(아이템마다 흔들리는 정도, MMOItems 의 spread).
 *
 * 0 인 능력치는 저장하지 않는다. 남겨두면 설정 파일이 안 쓰는 항목으로 채워지고,
 * 로어에도 `+0 흡혈` 같은 줄이 뜬다.
 */
class StatsMenu(
    custom: CustomItems,
    private val viewer: Player,
    private val id: String,
) : Menu(custom, SIZE, Text.renderFlat("<dark_gray>능력치 — " + id + "</dark_gray>")) {

    private var category: Stat.Category? = null
    private var spreadMode = false
    private var page = 0

    private fun item(): CustomItem? = custom.items.get(id)

    override fun draw() {
        clear()
        val item = item() ?: run {
            ItemTypeMenu(custom, viewer).open(viewer)
            return
        }

        val stats = ordered(item)
        page = Paging.clamp(page, stats.size)
        for ((slot, stat) in Paging.slice(stats, page).withIndex()) {
            set(slot, tile(item, stat)) { event -> if (spreadMode) editSpread(event, stat, item) else edit(event, stat, item.stat(stat)) }
        }

        if (page > 0) set(Paging.SLOT_PREV, Icon.prevPage()) { page--; refresh() }
        if (page < Paging.pageCount(stats.size) - 1) set(Paging.SLOT_NEXT, Icon.nextPage()) { page++; refresh() }
        set(SLOT_CATEGORY, categoryIcon()) { event ->
            category = Editors.cycle(event, listOf<Stat.Category?>(null) + Stat.Category.entries, category)
            page = 0
            refresh()
        }
        set(SLOT_CLEAR, clearIcon(item)) { mutate { it.copy(stats = emptyMap(), spreads = emptyMap()) } }
        set(SLOT_MODE, modeIcon()) {
            spreadMode = !spreadMode
            refresh()
        }
        set(Paging.SLOT_BACK, Icon.back()) { ItemEditMenu(custom, viewer, id).open(viewer) }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    /**
     * 이 재질에 어울리는 것 → 나머지. 묶음을 골랐으면 그 묶음만.
     * **값을 넣었다고 자리가 바뀌지 않는다**(사용자 요청 2026-09-25) — 고른 칸이 앞으로 튀면 이어서 고치던 칸을 다시 찾아야 한다.
     */
    private fun ordered(item: CustomItem): List<Stat> {
        val suggested = Stat.suggestedFor(item.material)
        return Stat.entries.filter { category == null || it.category == category }.sortedWith(
            compareBy(
                { if (it in suggested) 0 else 1 },
                { it.ordinal },
            ),
        )
    }

    private fun tile(item: CustomItem, stat: Stat) = Editors.numberIcon(
        material(stat, item.has(stat)),
        (if (item.has(stat)) "<yellow>" else "<gray>") + stat.display + "</yellow>",
        item.stat(stat),
        unit = stat.unit,
        extra = buildList {
            add("<dark_gray>" + stat.category.display + " · " + (if (stat.isVanilla) "바닐라 속성" else "이 플러그인이 계산") + "</dark_gray>")
            val spread = item.spreads[stat]
            if (spread != null && item.has(stat)) {
                val range = StatCalc.range(item, stat)
                add("<aqua>무작위 폭 ±" + Numbers.chance(spread.spread * 100) + "% <gray>(" + Numbers.chance(range.start) + " ~ " + Numbers.chance(range.endInclusive) + ")</gray></aqua>")
            }
            when (stat) {
                Stat.CRIT_DAMAGE -> add("<gray>치명타 확률이 0 이면 의미가 없습니다.</gray>")
                Stat.DAMAGE_REDUCTION -> add("<gray>감소 계열은 합쳐서 최대 80% 입니다.</gray>")
                Stat.ATTACK_SPEED -> add("<gray>바닐라 기본값은 4 입니다. 음수로 느리게 할 수 있습니다.</gray>")
                Stat.BLOCK_POWER -> add("<gray>비우면 막을 때 절반을 줄입니다.</gray>")
                else -> Unit
            }
            if (spreadMode) {
                add("<aqua>무작위 폭 모드: 클릭이 폭(%)을 바꿉니다</aqua>")
                add("<dark_gray>숫자 입력은 <white>폭</white> 또는 <white>폭 최대</white> (예: 10 30)</dark_gray>")
            } else {
                add("<dark_gray>0 으로 만들면 없앱니다</dark_gray>")
            }
        },
        stepLabel = if (spreadMode) "1%" else if (stat.percent) "1" else "0.5",
    )

    /** 붙어 있는 것은 색 있는 염료로. 한눈에 무엇이 설정됐는지 보인다. */
    private fun material(stat: Stat, active: Boolean): Material = when {
        !active -> Material.GRAY_DYE
        stat.isVanilla -> Material.LIME_DYE
        else -> Material.MAGENTA_DYE
    }

    private fun categoryIcon() = Icon.of(
        Material.HOPPER,
        "<yellow>묶음: <white>" + (category?.display ?: "전체") + "</white></yellow>",
        Editors.optionList(listOf<Stat.Category?>(null) + Stat.Category.entries, category) { it?.display ?: "전체" } + Editors.cycleHint,
    )

    private fun modeIcon() = Icon.of(
        if (spreadMode) Material.PRISMARINE_CRYSTALS else Material.COMPARATOR,
        "<yellow>모드: <white>" + (if (spreadMode) "무작위 폭" else "값") + "</white></yellow>",
        listOf(
            "<gray>무작위 폭은 아이템을 만들 때마다 값이 흔들리는 정도입니다.</gray>",
            "<gray>예: 공격력 10 · 폭 10% → 대개 9~11, 드물게 7~13</gray>",
            "", "<yellow>▶ 클릭: 바꾸기</yellow>",
        ),
    )

    private fun clearIcon(item: CustomItem) = Icon.of(
        Material.BARRIER,
        "<red>능력치 전부 지우기</red>",
        listOf("<gray>지금 <white>" + item.stats.size + "</white>개가 붙어 있습니다.</gray>"),
    )

    private fun edit(event: InventoryClickEvent, stat: Stat, current: Double) {
        if (Editors.isPrompt(event)) {
            Editors.promptDouble(
                custom.prompts, viewer, stat.display, -MAX, MAX,
                reopen = { open(viewer) },
            ) { value -> apply(stat, value, reopen = false) }
            return
        }
        val step = if (stat.percent) 1.0 else 0.5
        apply(stat, (current + Editors.step(event, step)).coerceIn(-MAX, MAX))
    }

    /** 폭은 % 로 다룬다. 기준값이 없는 능력치에는 폭을 못 준다 — 흔들 값이 없다. */
    private fun editSpread(event: InventoryClickEvent, stat: Stat, item: CustomItem) {
        if (!item.has(stat)) return
        val current = item.spreads[stat] ?: Spread(0.0)
        if (Editors.isPrompt(event)) {
            Editors.promptText(custom.prompts, viewer, stat.display + " 무작위 폭(%)", listOf("<gray>예: <white>10</white> 또는 <white>10 30</white>(최대 30%)"), reopen = { open(viewer) }) { raw ->
                val parts = raw.trim().split(Regex("\\s+")).mapNotNull { it.toDoubleOrNull() }
                val spread = parts.getOrNull(0) ?: return@promptText
                applySpread(stat, Spread(spread.coerceIn(0.0, 100.0) / 100.0, (parts.getOrNull(1) ?: 0.0).coerceIn(0.0, 100.0) / 100.0), reopen = false)
            }
            return
        }
        val next = (current.spread * 100.0 + Editors.step(event, 1.0)).coerceIn(0.0, 100.0) / 100.0
        applySpread(stat, current.copy(spread = next))
    }

    private fun apply(stat: Stat, value: Double, reopen: Boolean = true) {
        mutate(reopen) { item ->
            val next = LinkedHashMap(item.stats)
            // 0 은 표에서 아예 뺀다. 남겨두면 로어에 `+0` 줄이 생긴다. 폭도 같이 뺀다.
            if (value == 0.0) next.remove(stat) else next[stat] = value
            item.copy(stats = next, spreads = if (value == 0.0) item.spreads - stat else item.spreads)
        }
    }

    private fun applySpread(stat: Stat, spread: Spread, reopen: Boolean = true) {
        mutate(reopen) { item -> item.copy(spreads = if (spread.spread <= 0.0) item.spreads - stat else item.spreads + (stat to spread)) }
    }

    private fun mutate(reopen: Boolean = true, change: (CustomItem) -> CustomItem) {
        val current = item() ?: return
        custom.items.put(change(current))
        if (reopen) refresh() else open(viewer)
    }

    companion object {
        const val SIZE = 54
        const val MAX = 10_000.0
        const val SLOT_CATEGORY = 48
        const val SLOT_CLEAR = 49
        const val SLOT_MODE = 50
    }
}
