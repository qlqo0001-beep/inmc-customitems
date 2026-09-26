package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.ItemModifier
import com.inmc.customitems.item.Stat
import kr.inmc.core.gui.ConfirmMenu
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.util.Numbers
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player

private val MODIFIER_ID = Regex("^[a-z0-9_]{1,24}$")

/** 수식어 한 줄 요약. */
private fun summary(modifier: ItemModifier): List<String> =
    listOf("<gray>" + (if (modifier.suffix) "이름 뒤" else "이름 앞") + " · 확률 <white>" + Numbers.chance(modifier.chance) + "%</white></gray>") +
        modifier.stats.map { (stat, value) -> stat.line(value) }

/**
 * 수식어 목록. 만들 때마다 각자 확률로 붙어 이름에 붙고 능력치를 더한다 — "날카로운 검", "불꽃의 검".
 * 붙은 수식어는 그 아이템에 기록되므로, 나중에 수식어의 능력치를 고치면 이미 나간 아이템도 따라 바뀐다.
 */
class ModifierListMenu(custom: CustomItems, viewer: Player, id: String) :
    DetailMenu(custom, viewer, id, "<dark_gray>수식어 — $id</dark_gray>") {

    override fun draw() {
        clear()
        val item = item() ?: run {
            ItemTypeMenu(custom, viewer).open(viewer)
            return
        }
        for ((index, modifier) in item.modifiers.take(LIST).withIndex()) {
            set(index, Icon.of(Material.NAME_TAG, "<yellow>" + modifier.name + "</yellow>", summary(modifier) + listOf("", "<yellow>▶ 클릭: 편집</yellow>"))) {
                ModifierEditMenu(custom, viewer, id, modifier.id).open(viewer)
            }
        }
        set(SLOT_ADD, Icon.of(Material.LIME_DYE, "<green>수식어 추가</green>", listOf("<gray>id 를 적으면 만들어집니다. 예: <white>sharp</white></gray>"))) {
            Editors.promptText(custom.prompts, viewer, "수식어 id", listOf("<gray>소문자 영문·숫자·밑줄</gray>"), reopen = { open(viewer) }) { raw ->
                val modifierId = raw.trim().lowercase()
                if (!MODIFIER_ID.matches(modifierId) || item()?.modifiers?.any { it.id == modifierId } == true) {
                    viewer.sendMessage(Text.render("<red>쓸 수 없는 id 입니다: $modifierId</red>"))
                    return@promptText
                }
                custom.items.get(id)?.let { custom.items.put(it.copy(modifiers = it.modifiers + ItemModifier(modifierId, modifierId))) }
                ModifierEditMenu(custom, viewer, id, modifierId).open(viewer)
            }
        }
        set(SLOT_INFO, Icon.of(Material.BOOK, "<yellow>수식어란</yellow>", listOf(
            "<gray>아이템을 만들 때 각자 확률로 붙습니다.</gray>",
            "<gray>이름 앞(또는 뒤)에 붙고 능력치를 더합니다.</gray>",
            "<gray>예: <white>날카로운</white> 50% · 공격력 +2</gray>",
        )))
        backAndClose()
    }

    private companion object {
        const val LIST = 36
        const val SLOT_ADD = 48
        const val SLOT_INFO = 49
    }
}

/** 수식어 하나. */
class ModifierEditMenu(custom: CustomItems, viewer: Player, id: String, private val modifierId: String) :
    DetailMenu(custom, viewer, id, "<dark_gray>수식어 — $modifierId</dark_gray>") {

    private fun modifier(): ItemModifier? = item()?.modifiers?.firstOrNull { it.id == modifierId }

    private fun change(reopen: Boolean = true, transform: (ItemModifier) -> ItemModifier) =
        mutate(reopen) { item -> item.copy(modifiers = item.modifiers.map { if (it.id == modifierId) transform(it) else it }) }

    override fun draw() {
        clear()
        val modifier = modifier() ?: run {
            ModifierListMenu(custom, viewer, id).open(viewer)
            return
        }
        set(SLOT_SUMMARY, Icon.of(Material.NAME_TAG, "<yellow>" + modifier.name + "</yellow>", summary(modifier)))
        set(SLOT_NAME, Icon.of(Material.WRITABLE_BOOK, "<yellow>이름: <white>" + modifier.name + "</white></yellow>", listOf("", "<yellow>▶ 클릭: 적기</yellow>"))) {
            Editors.promptText(custom.prompts, viewer, "수식어 이름", listOf("<gray>예: <white><red>날카로운</white></gray>"), reopen = { open(viewer) }) { raw ->
                change(false) { it.copy(name = raw.trim()) }
            }
        }
        set(SLOT_SUFFIX, Icon.of(Material.COMPARATOR, "<yellow>자리: <white>" + (if (modifier.suffix) "이름 뒤" else "이름 앞") + "</white></yellow>", listOf("", "<yellow>▶ 클릭: 바꾸기</yellow>"))) {
            change { it.copy(suffix = !it.suffix) }
        }
        set(SLOT_CHANCE, Editors.numberIcon(Material.RABBIT_FOOT, "<yellow>붙을 확률</yellow>", modifier.chance, unit = "%")) { event ->
            if (Editors.isPrompt(event)) {
                Editors.promptDouble(custom.prompts, viewer, "붙을 확률(%)", 0.0, 100.0, reopen = { open(viewer) }) { v -> change(false) { it.copy(chance = v) } }
                return@set
            }
            change { it.copy(chance = (it.chance + Editors.step(event, 5.0)).coerceIn(0.0, 100.0)) }
        }
        set(SLOT_ADD_STAT, Icon.of(Material.LIME_DYE, "<green>능력치 추가</green>", listOf("<gray>고른 뒤 값을 적습니다.</gray>"))) {
            StatPickMenu(custom, viewer, "<dark_gray>수식어 능력치</dark_gray>", back = { open(viewer) }) { stat -> askValue(stat) }.open(viewer)
        }
        for ((index, entry) in modifier.stats.entries.take(STATS).withIndex()) {
            val (stat, value) = entry
            set(FIRST_STAT + index, Icon.of(Material.MAGENTA_DYE, stat.line(value), listOf("", "<yellow>▶ 좌클릭: 값 바꾸기 · <red>우클릭: 빼기</red></yellow>"))) { event ->
                if (event.isRightClick) change { it.copy(stats = it.stats - stat) } else askValue(stat)
            }
        }
        set(SLOT_DELETE, Icon.of(Material.LAVA_BUCKET, "<red>이 수식어 지우기</red>", listOf("<gray>이미 붙은 아이템에서도 사라집니다.</gray>"))) {
            ConfirmMenu(custom, "<red>수식어 $modifierId 를 지울까요?</red>", onConfirm = {
                mutate(false) { item -> item.copy(modifiers = item.modifiers.filterNot { it.id == modifierId }) }
                ModifierListMenu(custom, viewer, id).open(viewer)
            }, onCancel = { open(viewer) }).open(viewer)
        }
        set(Paging.SLOT_BACK, Icon.back()) { ModifierListMenu(custom, viewer, id).open(viewer) }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun askValue(stat: Stat) {
        Editors.promptDouble(custom.prompts, viewer, stat.display, -StatsMenu.MAX, StatsMenu.MAX, reopen = { open(viewer) }) { value ->
            change(false) { if (value == 0.0) it.copy(stats = it.stats - stat) else it.copy(stats = it.stats + (stat to value)) }
        }
    }

    private companion object {
        const val SLOT_SUMMARY = 4
        const val SLOT_NAME = 10
        const val SLOT_SUFFIX = 11
        const val SLOT_CHANCE = 12
        const val SLOT_ADD_STAT = 14
        const val FIRST_STAT = 18
        const val STATS = 27
        const val SLOT_DELETE = 51
    }
}

/** 능력치 고르기. 묶음으로 걸러 본다. */
class StatPickMenu(
    custom: CustomItems,
    private val viewer: Player,
    title: String,
    private val back: () -> Unit,
    private val onPick: (Stat) -> Unit,
) : Menu(custom, 54, Text.renderFlat(title)) {

    private var category: Stat.Category? = null
    private var page = 0

    override fun draw() {
        clear()
        val stats = Stat.entries.filter { category == null || it.category == category }
        page = Paging.clamp(page, stats.size)
        for ((slot, stat) in Paging.slice(stats, page).withIndex()) {
            set(slot, Icon.of(if (stat.isVanilla) Material.LIME_DYE else Material.MAGENTA_DYE, "<yellow>" + stat.display + "</yellow>",
                listOf("<dark_gray>" + stat.category.display + (if (stat.unit.isNotEmpty()) " · 단위 " + stat.unit else "") + "</dark_gray>"))) { onPick(stat) }
        }
        if (page > 0) set(Paging.SLOT_PREV, Icon.prevPage()) { page--; refresh() }
        if (page < Paging.pageCount(stats.size) - 1) set(Paging.SLOT_NEXT, Icon.nextPage()) { page++; refresh() }
        set(SLOT_CATEGORY, Icon.of(Material.HOPPER, "<yellow>묶음: <white>" + (category?.display ?: "전체") + "</white></yellow>", Editors.cycleHint)) { event ->
            category = Editors.cycle(event, listOf<Stat.Category?>(null) + Stat.Category.entries, category)
            page = 0
            refresh()
        }
        set(Paging.SLOT_BACK, Icon.back()) { back() }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private companion object {
        const val SLOT_CATEGORY = 48
    }
}
