package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import com.inmc.customitems.craft.Part
import com.inmc.customitems.craft.RecipeDef
import com.inmc.customitems.craft.RecipeKind
import kr.inmc.core.gui.ConfirmMenu
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.item.StoredItem
import kr.inmc.core.util.Numbers
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryCloseEvent

private val RECIPE_ID = Regex("^[a-z0-9_]{1,32}$")

/** 바닐라 조합법 목록(관리). */
class RecipeListMenu(custom: CustomItems, private val viewer: Player) : Menu(custom, 54, Text.renderFlat("<dark_gray>바닐라 조합법</dark_gray>")) {

    override fun draw() {
        clear()
        for ((index, recipe) in custom.recipes.all().take(LIST).withIndex()) {
            val icon = recipe.result?.let { custom.crafting.resolver.icon(it.item).stack.clone() } ?: org.bukkit.inventory.ItemStack(Material.BARRIER)
            set(index, Icon.annotate(icon, lore = listOf(
                "", "<gray>id <white>" + recipe.id + "</white> · " + recipe.kind.label + "</gray>",
                if (recipe.result == null) "<red>결과가 없어 걸리지 않습니다</red>" else "<yellow>▶ 클릭: 편집</yellow>",
            ))) { RecipeEditMenu(custom, viewer, recipe.id).open(viewer) }
        }
        set(SLOT_ADD, Icon.of(Material.LIME_DYE, "<green>새 조합법</green>", listOf("<gray>id 를 적으면 만들어집니다.</gray>"))) {
            Editors.promptText(custom.prompts, viewer, "조합법 id", listOf("<gray>소문자 영문·숫자·밑줄</gray>"), reopen = { open(viewer) }) { raw ->
                val id = raw.trim().lowercase()
                if (!RECIPE_ID.matches(id) || custom.recipes.get(id) != null) {
                    viewer.sendMessage(Text.render("<red>쓸 수 없는 id 입니다: $id</red>"))
                    return@promptText
                }
                custom.recipes.put(RecipeDef(id, grid = List(RecipeKind.SHAPED.slots) { null }))
                RecipeEditMenu(custom, viewer, id).open(viewer)
            }
        }
        set(Paging.SLOT_BACK, Icon.back()) { StationListMenu(custom, viewer).open(viewer) }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private companion object {
        const val LIST = 36
        const val SLOT_ADD = 48
    }
}

/**
 * 조합법 하나. 재료 칸과 결과 칸에 아이템을 **직접 넣는다**. 닫을 때(버튼으로 옮길 때 포함) 저장하고 서버에
 * 다시 건다. 작업대는 3×3 그대로 놓고, 모양이 작으면 작업대 어디에 놓아도 된다.
 */
class RecipeEditMenu(custom: CustomItems, private val viewer: Player, private val id: String) :
    Menu(custom, 54, Text.renderFlat("<dark_gray>조합법 — $id</dark_gray>")) {

    private fun recipe(): RecipeDef? = custom.recipes.get(id)

    /** 이 종류의 재료 칸들. 순서가 곧 [RecipeDef.grid] 의 번호다. */
    private fun inputs(kind: RecipeKind): List<Int> = when (kind.slots) {
        9 -> GRID
        3 -> SMITHING
        else -> listOf(SINGLE)
    }

    override fun isSlotEditable(slot: Int): Boolean {
        val recipe = recipe() ?: return false
        return slot == SLOT_RESULT || slot in inputs(recipe.kind)
    }

    override fun acceptsShiftInsert(): Boolean = true

    override fun draw() {
        clear()
        val recipe = recipe() ?: return RecipeListMenu(custom, viewer).open(viewer)
        val inputs = inputs(recipe.kind)
        for ((index, slot) in inputs.withIndex()) recipe.slot(index)?.let { inventory.setItem(slot, custom.crafting.resolver.icon(it).stack.clone()) }
        recipe.result?.let { inventory.setItem(SLOT_RESULT, custom.crafting.resolver.icon(it.item).stack.clone().also { s -> s.amount = it.amount }) }

        set(SLOT_ARROW, Icon.of(Material.ARROW, "<gray>→ 결과</gray>", listOf("<gray>" + recipe.kind.label + "</gray>")))
        set(SLOT_KIND, Icon.of(Material.CRAFTING_TABLE, "<yellow>종류: <white>" + recipe.kind.label + "</white></yellow>",
            Editors.optionList(RecipeKind.entries, recipe.kind) { it.label } + Editors.cycleHint)) { event ->
            saveSlots()
            val next = Editors.cycle(event, RecipeKind.entries, recipe.kind)
            // 칸 수가 다른 종류로 바꾸면 넘치는 재료는 버린다.
            val current = recipe() ?: return@set
            custom.recipes.put(current.copy(kind = next, grid = List(next.slots) { current.slot(it) }))
            refresh()
        }
        if (recipe.kind.cooking) {
            set(SLOT_COOK, Editors.numberIcon(Material.CLOCK, "<yellow>굽는 시간</yellow>", recipe.cookSeconds, unit = "초")) { event ->
                saveSlots()
                if (Editors.isPrompt(event)) {
                    Editors.promptDouble(custom.prompts, viewer, "굽는 시간(초)", 0.05, 3600.0, reopen = { open(viewer) }) { v -> recipe()?.let { custom.recipes.put(it.copy(cookSeconds = v)) } }
                    return@set
                }
                recipe()?.let { custom.recipes.put(it.copy(cookSeconds = (it.cookSeconds + Editors.step(event, 1.0)).coerceIn(0.05, 3600.0))) }
                refresh()
            }
            set(SLOT_EXP, Editors.numberIcon(Material.EXPERIENCE_BOTTLE, "<yellow>경험치</yellow>", recipe.exp)) { event ->
                saveSlots()
                recipe()?.let { custom.recipes.put(it.copy(exp = (it.exp + Editors.step(event, 0.1)).coerceIn(0.0, 100.0))) }
                refresh()
            }
        }
        set(SLOT_INFO, Icon.of(Material.BOOK, "<yellow>넣는 법</yellow>", listOf(
            "<gray>재료 칸과 결과 칸에 아이템을 넣으세요.</gray>",
            "<gray>커스텀 아이템을 재료로 넣으면 그 아이템만 받습니다.</gray>",
            "<gray>결과 개수는 넣은 개수 그대로입니다.</gray>",
            "<gray>화면을 닫으면 저장하고 서버에 다시 겁니다.</gray>",
        )))
        set(SLOT_DELETE, Icon.of(Material.LAVA_BUCKET, "<red>조합법 지우기</red>")) {
            ConfirmMenu(custom, "<red>조합법 $id 를 지울까요?</red>", onConfirm = {
                custom.recipes.remove(id)
                RecipeListMenu(custom, viewer).open(viewer)
            }, onCancel = { open(viewer) }).open(viewer)
        }
        for (slot in 0 until size) if (inventory.getItem(slot) == null && !isSlotEditable(slot)) inventory.setItem(slot, Icon.FILLER)
        set(Paging.SLOT_BACK, Icon.back()) { RecipeListMenu(custom, viewer).open(viewer) }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    /** 칸에 놓인 것을 정의에 적는다. 달라진 것이 없으면 다시 걸지 않는다. */
    private fun saveSlots() {
        val recipe = recipe() ?: return
        val grid: List<StoredItem?> = inputs(recipe.kind).map { slot -> inventory.getItem(slot)?.takeIf { !it.type.isAir }?.let(custom.crafting.resolver::capture) }
        val result = inventory.getItem(SLOT_RESULT)?.takeIf { !it.type.isAir }?.let { Part(custom.crafting.resolver.capture(it), it.amount) }
        val next = recipe.copy(grid = grid, result = result)
        if (next != recipe) custom.recipes.put(next)
    }

    override fun onClose(event: InventoryCloseEvent) = saveSlots()

    private companion object {
        val GRID = listOf(10, 11, 12, 19, 20, 21, 28, 29, 30)
        val SMITHING = listOf(19, 20, 21)
        const val SINGLE = 20
        const val SLOT_ARROW = 23
        const val SLOT_RESULT = 25
        const val SLOT_KIND = 47
        const val SLOT_COOK = 48
        const val SLOT_EXP = 49
        const val SLOT_INFO = 50
        const val SLOT_DELETE = 51
    }
}
