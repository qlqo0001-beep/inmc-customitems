package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
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

/**
 * 종류 관리 — 기본 종류의 이름·아이콘·기호 바꾸기와 새 종류 만들기(보호권·열쇠·캡슐 …).
 * 새 종류는 기본 종류 하나를 골라 그것처럼 동작한다([TypeDef.base]).
 */
class TypeListMenu(custom: CustomItems, private val viewer: Player) :
    Menu(custom, 54, Text.renderFlat("<dark_gray>종류 관리</dark_gray>")) {

    private var page = 0

    override fun draw() {
        clear()
        val all = custom.types.all()
        val counts = custom.items.all().groupingBy { custom.types.of(it).id }.eachCount()
        page = Paging.clamp(page, all.size)
        for ((slot, type) in Paging.slice(all, page).withIndex()) {
            set(slot, typeIcon(type, "<yellow>" + type.symbol + " " + type.name + "</yellow>", buildList {
                add(if (type.builtin) "<dark_gray>기본 종류 · id " + type.id + "</dark_gray>" else "<dark_gray>만든 종류 · id " + type.id + " · " + type.base.display + "처럼 동작</dark_gray>")
                add("<gray>아이템 <white>" + (counts[type.id] ?: 0) + "</white>개</gray>")
                add("")
                add("<yellow>▶ 클릭: 설정</yellow>")
            })) { TypeEditMenu(custom, viewer, type.id).open(viewer) }
        }
        set(SLOT_CREATE, Icon.of(Material.WRITABLE_BOOK, "<green>새 종류</green>", listOf(
            "<gray>예: <white>보호권</white> — 소모품처럼 동작하는 종류.</gray>",
            "<gray>" + DefinitionKey.HINT + "</gray>",
            "", "<yellow>▶ 클릭: id 입력</yellow>",
        ))) { create() }
        set(Paging.SLOT_BACK, Icon.back()) { ItemTypeMenu(custom, viewer).open(viewer) }
        if (page > 0) set(Paging.SLOT_PREV, Icon.prevPage()) { page--; refresh() }
        if (page < Paging.pageCount(all.size) - 1) set(Paging.SLOT_NEXT, Icon.nextPage()) { page++; refresh() }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun create() {
        var created: String? = null
        Editors.promptText(
            custom.prompts, viewer, "새 종류 id",
            listOf("<gray>" + DefinitionKey.HINT + ".</gray>", "<gray>보이는 이름·동작 기준은 다음 화면에서 고릅니다(처음엔 소모품처럼).</gray>"),
            reopen = { created?.let { TypeEditMenu(custom, viewer, it).open(viewer) } ?: open(viewer) },
        ) { raw ->
            val id = raw.trim().lowercase()
            when {
                !DefinitionKey.isValid(id) -> custom.messages.send(viewer, "invalid-name", Ph.of().item(raw))
                custom.types.get(id) != null -> custom.messages.send(viewer, "already-exists", Ph.of().item(id))
                else -> {
                    custom.types.put(TypeDef(id, ItemType.CONSUMABLE, name = id))
                    created = id
                }
            }
        }
    }

    companion object {
        const val SLOT_CREATE = 49
    }
}

/** 종류 하나의 설정. 기본 종류는 동작 기준을 바꿀 수 없고 지울 수 없다. */
class TypeEditMenu(custom: CustomItems, private val viewer: Player, private val id: String) :
    Menu(custom, 27, Text.renderFlat("<dark_gray>종류 — $id</dark_gray>")) {

    override fun draw() {
        clear()
        val type = custom.types.get(id) ?: run {
            TypeListMenu(custom, viewer).open(viewer)
            return
        }
        set(SLOT_NAME, Icon.of(Material.NAME_TAG, "<yellow>보이는 이름: <white>" + type.name + "</white></yellow>", listOf(
            "<gray>서랍·아이템 로어 첫 줄에 보입니다.</gray>", "", "<yellow>▶ 클릭: 바꾸기</yellow>",
        ))) { prompt("보이는 이름", type.name) { value -> type.copy(name = value) } }
        set(SLOT_SYMBOL, Icon.of(Material.OAK_SIGN, "<yellow>기호: <white>" + type.symbol + "</white></yellow>", listOf(
            "<gray>로어 첫 줄 앞의 작은 기호(⚔ ✚ ❂ …).</gray>", "", "<yellow>▶ 클릭: 바꾸기</yellow>",
        ))) { prompt("기호", type.symbol) { value -> type.copy(symbol = value.take(4)) } }
        set(SLOT_ICON, typeIcon(type, "<yellow>아이콘</yellow>", listOf(
            "<gray>서랍에 보이는 아이콘. 모델 번호·커스텀아이템 모양 그대로 됩니다.</gray>",
            "<gray>기본 모양은 기본 종류면 종류 아이콘(팩), 만든 종류면 첫 아이템 모양.</gray>", "",
            "<yellow>▶ 좌클릭: 손에 든 것으로 바꾸기</yellow>",
            "<yellow>▶ 우클릭: 기본 모양으로</yellow>",
        ))) { event ->
            if (event.isRightClick) {
                custom.types.put(type.copy(icon = type.base.icon, iconItem = null))
            } else {
                val hand = viewer.inventory.itemInMainHand
                if (!hand.type.isAir) captureIcon(hand).let { (material, item) -> custom.types.put(type.copy(icon = material, iconItem = item)) }
            }
            refresh()
        }
        if (!type.builtin) {
            set(SLOT_BASE, Icon.of(type.base.icon, "<yellow>동작 기준: <white>" + type.base.display + "</white></yellow>",
                Editors.optionList(ItemType.entries.toList(), type.base) { it.display } +
                    listOf("", "<gray>이 종류의 아이템은 이 기본 종류처럼 동작합니다</gray>", "<gray>(부적이면 가방에서 효과, 장신구면 장착 칸 …).</gray>") + Editors.cycleHint,
            )) { event ->
                val next = Editors.cycle(event, ItemType.entries.toList(), type.base)
                custom.types.put(type.copy(base = next))
                // 아이템의 동작도 같이 옮긴다 — 안 그러면 기준이 어긋나 기본 종류로 보인다(TypeRegistry.of).
                for (item in custom.items.all().filter { it.customType == type.id }) custom.items.put(item.copy(type = next))
                refresh()
            }
            set(SLOT_DELETE, Icon.of(Material.LAVA_BUCKET, "<red>지우기</red>", listOf(
                "<gray>아이템은 지워지지 않고 <white>" + type.base.display + "</white> 로 돌아갑니다.</gray>",
            ))) {
                val members = custom.items.all().filter { it.customType == type.id }
                ConfirmMenu(
                    owner = custom,
                    question = "<red>종류 '" + type.name + "' 을(를) 지울까요?</red>",
                    detail = listOf("<gray>들어 있던 <white>" + members.size + "</white>개는 <white>" + type.base.display + "</white> 로 갑니다.</gray>"),
                    onConfirm = {
                        custom.types.remove(type.id)
                        for (item in members) custom.items.get(item.id)?.let { custom.items.put(it.copy(customType = "", category = "")) }
                        for (category in custom.categories.all().filter { it.type == type.id }) custom.categories.remove(category.id)
                        TypeListMenu(custom, viewer).open(viewer)
                    },
                    onCancel = { open(viewer) },
                ).open(viewer)
            }
        }
        set(SLOT_BACK, Icon.back()) { TypeListMenu(custom, viewer).open(viewer) }
    }

    private fun prompt(label: String, now: String, change: (String) -> TypeDef) {
        Editors.promptText(
            custom.prompts, viewer, label,
            listOf("<gray>지금: <white>" + now + "</white></gray>", "<gray>MiniMessage 서식을 써도 됩니다.</gray>"),
            reopen = { open(viewer) },
        ) { raw ->
            val value = raw.trim()
            if (value.isNotEmpty()) custom.types.put(change(value))
        }
    }

    companion object {
        const val SLOT_NAME = 10
        const val SLOT_SYMBOL = 11
        const val SLOT_ICON = 12
        const val SLOT_BASE = 14
        const val SLOT_DELETE = 16
        const val SLOT_BACK = 22
    }
}
