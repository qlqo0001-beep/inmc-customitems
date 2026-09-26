package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.ItemBuilder
import com.inmc.customitems.item.ItemType
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
import org.bukkit.inventory.ItemStack

/**
 * 한 종류의 아이템 목록([type] 이 null 이면 전부). 종류 화면([ItemTypeMenu])이나 소분류 서랍([CategoryMenu])에서 들어온다.
 *
 * @param category 이 소분류만. @param unsorted 소분류가 없는 것만("분류 없음").
 *
 * 아이콘은 **실제로 만들어본 아이템**이다. 설정값을 나열하는 것보다 결과를 보여주는 쪽이
 * 낫다 — 인챈트 빛이나 모델이 제대로 붙었는지 여기서 바로 보인다.
 */
class ItemListMenu(
    custom: CustomItems,
    private val viewer: Player,
    private val type: TypeDef? = null,
    private var page: Int = 0,
    private var search: String = "",
    private val category: String? = null,
    private val unsorted: Boolean = false,
) : Menu(custom, SIZE, Text.renderFlat("<dark_gray>커스텀아이템 — " + where(custom, type, category, unsorted) + "</dark_gray>")) {

    override fun draw() {
        clear()

        val shown = visible()
        page = Paging.clamp(page, shown.size)

        for ((index, item) in Paging.slice(shown, page).withIndex()) {
            set(index, tile(item)) { event ->
                if (event.isRightClick) confirmDelete(item) else ItemEditMenu(custom, viewer, item.id).open(viewer)
            }
        }

        set(Paging.SLOT_BACK, Icon.back()) {
            if (type != null && custom.categories.of(type).isNotEmpty()) CategoryMenu(custom, viewer, type).open(viewer) else ItemTypeMenu(custom, viewer).open(viewer)
        }
        if (page > 0) {
            set(Paging.SLOT_PREV, Icon.prevPage()) {
                page--
                refresh()
            }
        }
        if (page < Paging.pageCount(shown.size) - 1) {
            set(Paging.SLOT_NEXT, Icon.nextPage()) {
                page++
                refresh()
            }
        }

        set(SLOT_SEARCH, searchButton(shown.size)) { event ->
            if (event.isRightClick) {
                search = ""
                page = 0
                refresh()
                return@set
            }
            Editors.promptText(
                custom.prompts, viewer, "이름으로 찾기",
                listOf("<gray>id 나 표시 이름의 일부를 적으세요.</gray>"),
                reopen = { open(viewer) },
            ) { raw ->
                search = raw.trim()
                page = 0
            }
        }
        set(SLOT_REGISTER, registerButton(viewer, type)) { promptRegister(custom, viewer, type, category.orEmpty()) { open(viewer) } }
        if (type != null) {
            set(SLOT_CATEGORIES, Icon.of(Material.BOOKSHELF, "<yellow>" + type.name + " 소분류 <white>" + custom.categories.of(type).size + "</white>개</yellow>", listOf(
                "<gray>이 종류를 서랍으로 나눠 봅니다(한손검·강화석처럼).</gray>", "", "<yellow>▶ 클릭: 소분류 만들기·보기</yellow>",
            ))) { CategoryMenu(custom, viewer, type).open(viewer) }
        }
        set(SLOT_INFO, Icon.of(Material.BOOK, "<aqua>" + where(custom, type, category, unsorted) + "</aqua>", listOf(
            "<gray>보이는 항목 <white>" + shown.size + "</white>개 · " + (page + 1) + "/" + Paging.pageCount(shown.size) + " 쪽</gray>",
            "",
            "<gray>종류는 설정 화면의 <white>종류</white> 칸에서 바꿉니다.</gray>",
        )))
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun visible(): List<CustomItem> {
        val all = custom.items.all().filter {
            (type == null || custom.types.of(it).id == type.id) &&
                (category == null || custom.categories.of(it)?.id == category) &&
                (!unsorted || custom.categories.of(it) == null)
        }
        if (search.isBlank()) return all
        return all.filter {
            it.id.contains(search, ignoreCase = true) ||
                it.displayName.contains(search, ignoreCase = true)
        }
    }

    private fun tile(item: CustomItem): ItemStack {
        val preview = custom.items.preview(item)
        val lore = buildList {
            add("<gray>재질: <white>" + item.material.name + "</white></gray>")
            if (item.enchants.isNotEmpty()) add("<gray>인챈트 <white>" + item.enchants.size + "</white>개</gray>")
            if (item.stats.isNotEmpty()) add("<gray>능력치 <white>" + item.stats.size + "</white>개</gray>")
            if (item.abilities.isNotEmpty()) add("<gray>기능 <white>" + item.abilities.size + "</white>개</gray>")
            if (item.data.isNotEmpty()) {
                add("<aqua>연동 값 <white>" + item.data.size + "</white>개</aqua>")
                for ((key, value) in item.data.entries.take(3)) {
                    add("<dark_gray>  " + key + " = " + value + "</dark_gray>")
                }
                if (item.data.size > 3) add("<dark_gray>  ...</dark_gray>")
            }
            add("")
            add("<dark_gray>참조: inmc:" + item.id + "</dark_gray>")
            add("<yellow>▶ 좌클릭: 설정</yellow>")
            add("<red>▶ 우클릭: 삭제</red>")
        }
        return Icon.annotate(preview, null, lore)
    }

    private fun searchButton(shown: Int) = Icon.of(
        Material.SPYGLASS,
        if (search.isBlank()) "<yellow>이름으로 찾기</yellow>" else "<yellow>찾는 중: <white>$search</white></yellow>",
        buildList {
            add("<gray>보이는 항목 <white>" + shown + "</white>개</gray>")
            add("")
            add("<yellow>▶ 좌클릭: 검색어 입력</yellow>")
            if (search.isNotBlank()) add("<red>▶ 우클릭: 검색 지우기</red>")
        },
    )

    private fun confirmDelete(item: CustomItem) {
        ConfirmMenu(
            owner = custom,
            question = "<red>'" + item.id + "' 을(를) 삭제할까요?</red>",
            detail = listOf(
                "<gray>이미 나간 아이템은 그대로 남지만</gray>",
                "<gray><white>inmc:" + item.id + "</white> 참조가 전부 깨집니다.</gray>",
                "<gray>다른 플러그인에서 쓰고 있는지 먼저 확인하세요.</gray>",
            ),
            onConfirm = {
                custom.items.remove(item.id)
                custom.messages.send(viewer, "unregistered", Ph.of().item(item.id))
                open(viewer)
            },
            onCancel = { open(viewer) },
        ).open(viewer)
    }

    companion object {
        const val SIZE = 54
        const val SLOT_SEARCH = 48
        const val SLOT_REGISTER = 49
        const val SLOT_INFO = 50
        const val SLOT_CATEGORIES = 51

        /** 제목·안내에 쓰는 자리 — `무기 › 일반 무기`. */
        private fun where(custom: CustomItems, type: TypeDef?, category: String?, unsorted: Boolean): String {
            val base = type?.let { it.symbol + " " + it.name } ?: "전체"
            return when {
                unsorted -> "$base › 분류 없음"
                category != null -> base + " › " + (custom.categories.get(category)?.name ?: category)
                else -> base
            }
        }
    }
}

/** 손에 든 것으로 새 아이템 — 버튼 모양. [type] 안에서 누르면 그 종류로 만든다. */
internal fun registerButton(viewer: Player, type: TypeDef? = null): ItemStack {
    val hand = viewer.inventory.itemInMainHand
    if (hand.type.isAir) {
        return Icon.of(
            Material.BARRIER,
            "<red>등록하려면 아이템을 손에 드세요</red>",
            listOf(
                "<gray>손에 든 것의 재질·이름·설명·인챈트를 읽어</gray>",
                "<gray>새 정의를 만듭니다. 나머지는 설정 화면에서 채우세요.</gray>",
            ),
        )
    }
    return Icon.of(
        Material.WRITABLE_BOOK,
        "<green>손에 든 것으로 새 아이템 만들기</green>",
        listOf(
            "<gray>재질: <white>" + hand.type.name + "</white></gray>",
            "<gray>종류: <white>" + (type?.name ?: "재질로 짐작") + "</white></gray>",
            "",
            "<yellow>▶ 클릭하면 이름을 물어봅니다</yellow>",
        ),
    )
}

/**
 * 이름을 물어보고 손에 든 것을 등록한 뒤 설정 화면을 연다. 취소하거나 이름이 틀리면 [back].
 *
 * 이름은 설정 파일의 **섹션 이름**이자 **참조 id** 가 된다. 점이 들어가면 YamlConfiguration 이 계층을
 * 만들어 다시 읽히지 않고, 콜론이 들어가면 참조가 깨진다.
 */
internal fun promptRegister(custom: CustomItems, viewer: Player, type: TypeDef?, category: String = "", back: () -> Unit) {
    val hand = viewer.inventory.itemInMainHand.clone()
    if (hand.type.isAir) return

    var created: String? = null
    Editors.promptText(
        custom.prompts,
        viewer,
        "아이템 이름",
        listOf(
            "<gray>" + DefinitionKey.HINT + ".</gray>",
            "<gray>예: <white>전설의검</white>, <white>golden_rod</white></gray>",
        ),
        // 값을 받은 **뒤에** 불린다. 여기서 목록을 열면 방금 연 설정 화면을 덮는다.
        reopen = { created?.let { ItemEditMenu(custom, viewer, it).open(viewer) } ?: back() },
    ) { raw ->
        val id = raw.trim().lowercase()
        if (!DefinitionKey.isValid(id)) {
            custom.messages.send(viewer, "invalid-name", Ph.of().item(raw))
            return@promptText
        }
        if (custom.items.exists(id)) {
            custom.messages.send(viewer, "already-exists", Ph.of().item(id))
            return@promptText
        }
        val captured = ItemBuilder.capture(id, hand)
        custom.items.put(if (type != null) captured.copy(type = type.base, customType = if (type.builtin) "" else type.id, category = category) else captured)
        custom.messages.send(viewer, "registered", Ph.of().item(id))
        created = id
    }
}
