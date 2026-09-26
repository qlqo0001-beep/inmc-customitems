package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.Category
import com.inmc.customitems.item.TypeDef
import com.inmc.customitems.util.Ph
import kr.inmc.core.gui.ConfirmMenu
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.store.DefinitionKey
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player

/** 종류를 눌렀을 때. 소분류가 있으면 소분류 서랍, 없으면 곧바로 목록 — 서랍 하나를 더 거칠 이유가 없다. */
internal fun openType(custom: CustomItems, viewer: Player, type: TypeDef) {
    if (custom.categories.of(type).isEmpty()) ItemListMenu(custom, viewer, type).open(viewer) else CategoryMenu(custom, viewer, type).open(viewer)
}

/**
 * 한 종류의 소분류 서랍(MMOItems 에서 부모 타입을 둔 타입들). 종류 하나에 아이템이 수십 개가 되면 한 목록에서 못 찾는다.
 *
 * 서랍 좌클릭 = 보기, 우클릭 = 이름, Shift+좌클릭 = 손에 든 것으로 아이콘, Shift+우클릭 = 지우기(아이템은 "분류 없음"으로).
 */
class CategoryMenu(custom: CustomItems, private val viewer: Player, private val type: TypeDef) :
    Menu(custom, SIZE, Text.renderFlat("<dark_gray>커스텀아이템 — " + type.name + "</dark_gray>")) {

    private var page = 0

    override fun draw() {
        clear()
        val categories = custom.categories.of(type)
        val items = custom.items.all().filter { custom.types.of(it).id == type.id }
        val counts = items.groupingBy { custom.categories.of(it)?.id }.eachCount()
        page = Paging.clamp(page, categories.size)

        for ((slot, category) in Paging.slice(categories, page).withIndex()) {
            set(slot, iconOf(category.icon, category.iconItem, "<yellow>" + category.name + "</yellow>", listOf(
                "<gray>아이템 <white>" + (counts[category.id] ?: 0) + "</white>개</gray>",
                "<dark_gray>id " + category.id + "</dark_gray>",
                "",
                "<yellow>▶ 좌클릭: 보기</yellow>",
                "<yellow>▶ 우클릭: 이름 바꾸기</yellow>",
                "<yellow>▶ Shift+좌클릭: 손에 든 것으로 아이콘</yellow>",
                "<red>▶ Shift+우클릭: 지우기</red>",
            ))) { event ->
                when {
                    event.isShiftClick && event.isRightClick -> confirmRemove(category)
                    event.isShiftClick -> setIcon(category)
                    event.isRightClick -> rename(category)
                    else -> ItemListMenu(custom, viewer, type, category = category.id).open(viewer)
                }
            }
        }

        set(SLOT_ALL, Icon.of(Material.CHEST, "<gold>" + type.symbol + " " + type.name + " 전부</gold>", listOf(
            "<gray>소분류와 상관없이 <white>" + items.size + "</white>개</gray>", "", "<yellow>▶ 클릭: 보기</yellow>",
        ))) { ItemListMenu(custom, viewer, type).open(viewer) }
        set(SLOT_UNSORTED, Icon.of(Material.BARREL, "<gray>분류 없음</gray>", listOf(
            "<gray>소분류가 없는 <white>" + (counts[null] ?: 0) + "</white>개</gray>",
            "<dark_gray>아이템 설정 화면의 소분류 칸에서 넣습니다.</dark_gray>",
            "", "<yellow>▶ 클릭: 보기</yellow>",
        ))) { ItemListMenu(custom, viewer, type, unsorted = true).open(viewer) }
        set(SLOT_CREATE, Icon.of(Material.WRITABLE_BOOK, "<green>새 소분류</green>", listOf(
            "<gray>" + type.name + " 아래에 서랍을 하나 더 만듭니다.</gray>",
            "<gray>" + DefinitionKey.HINT + "</gray>",
            "", "<yellow>▶ 클릭: id 입력</yellow>",
        ))) { create() }

        set(Paging.SLOT_BACK, Icon.back()) { ItemTypeMenu(custom, viewer).open(viewer) }
        if (page > 0) set(Paging.SLOT_PREV, Icon.prevPage()) { page--; refresh() }
        if (page < Paging.pageCount(categories.size) - 1) set(Paging.SLOT_NEXT, Icon.nextPage()) { page++; refresh() }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun create() {
        Editors.promptText(
            custom.prompts, viewer, "새 소분류 id",
            listOf("<gray>" + DefinitionKey.HINT + ".</gray>", "<gray>예: <white>한손검</white>, <white>강화석</white> — 보이는 이름은 나중에 바꿀 수 있습니다.</gray>"),
            reopen = { open(viewer) },
        ) { raw ->
            val id = raw.trim().lowercase()
            when {
                !DefinitionKey.isValid(id) -> custom.messages.send(viewer, "invalid-name", Ph.of().item(raw))
                custom.categories.get(id) != null -> custom.messages.send(viewer, "already-exists", Ph.of().item(id))
                else -> custom.categories.put(Category(id, type.id, icon = type.icon, iconItem = type.iconItem))
            }
        }
    }

    private fun rename(category: Category) {
        Editors.promptText(
            custom.prompts, viewer, "보이는 이름",
            listOf("<gray>지금: <white>" + category.name + "</white></gray>", "<gray>MiniMessage 서식을 써도 됩니다.</gray>"),
            reopen = { open(viewer) },
        ) { raw ->
            val name = raw.trim()
            if (name.isNotEmpty()) custom.categories.get(category.id)?.let { custom.categories.put(it.copy(name = name)) }
        }
    }

    private fun setIcon(category: Category) {
        val hand = viewer.inventory.itemInMainHand
        if (hand.type.isAir) return
        val (material, item) = captureIcon(hand)
        custom.categories.get(category.id)?.let { custom.categories.put(it.copy(icon = material, iconItem = item)) }
        refresh()
    }

    private fun confirmRemove(category: Category) {
        val members = custom.items.all().filter { it.category == category.id }
        ConfirmMenu(
            owner = custom,
            question = "<red>소분류 '" + category.name + "' 을(를) 지울까요?</red>",
            detail = listOf(
                "<gray>아이템은 지워지지 않습니다.</gray>",
                "<gray>들어 있던 <white>" + members.size + "</white>개는 <white>분류 없음</white>으로 갑니다.</gray>",
            ),
            onConfirm = {
                custom.categories.remove(category.id)
                for (item in members) custom.items.get(item.id)?.let { custom.items.put(it.copy(category = "")) }
                openType(custom, viewer, type)
            },
            onCancel = { open(viewer) },
        ).open(viewer)
    }

    companion object {
        const val SIZE = 54
        const val SLOT_ALL = 48
        const val SLOT_UNSORTED = 49
        const val SLOT_CREATE = 50
    }
}
