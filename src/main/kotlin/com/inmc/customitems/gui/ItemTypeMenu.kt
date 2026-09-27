package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.ItemType
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * 관리 화면의 첫 장 — 종류별 서랍(MMOItems 의 타입 목록).
 *
 * 아이템이 수백 개가 되면 한 목록에서 찾을 수가 없다. 종류를 먼저 고르고 들어가서 본다.
 * 한꺼번에 보고 싶으면 가운데 **전체 보기**.
 */
class ItemTypeMenu(custom: CustomItems, private val viewer: Player) :
    Menu(custom, SIZE, Text.renderFlat("<dark_gray>커스텀아이템</dark_gray>")) {

    override fun draw() {
        clear()
        val all = custom.items.all()
        val counts = all.groupingBy { custom.types.of(it).id }.eachCount()
        val drawers = ItemType.entries.map(custom.types::builtin).zip(TYPE_SLOTS) + custom.types.custom().zip(CUSTOM_SLOTS)

        for ((type, slot) in drawers) {
            val count = counts[type.id] ?: 0
            val categories = custom.categories.of(type)
            set(slot, iconOf(type.icon, type.iconItem, "<yellow>" + type.symbol + " " + type.name + "</yellow>", buildList {
                if (!type.builtin) add("<dark_gray>" + type.base.display + "처럼 동작</dark_gray>")
                add("<gray>아이템 <white>" + count + "</white>개</gray>")
                if (categories.isNotEmpty()) {
                    add("<gray>소분류 <white>" + categories.size + "</white>개</gray>")
                    for (category in categories.take(6)) add("<dark_gray>  ▪ " + category.name + "</dark_gray>")
                    if (categories.size > 6) add("<dark_gray>  …</dark_gray>")
                }
                add("")
                add("<yellow>▶ 클릭: 보기</yellow>")
            })) { openType(custom, viewer, type) }
        }
        set(SLOT_ALL, Icon.of(Material.CHEST, "<gold>전체 보기</gold>", listOf(
            "<gray>종류와 상관없이 전부 <white>" + all.size + "</white>개</gray>",
            "",
            "<yellow>▶ 클릭: 보기</yellow>",
        ))) { ItemListMenu(custom, viewer).open(viewer) }

        set(SLOT_REGISTER, registerButton(viewer)) { promptRegister(custom, viewer, null) { open(viewer) } }
        set(SLOT_SEARCH, Icon.of(Material.SPYGLASS, "<yellow>이름으로 찾기</yellow>", listOf(
            "<gray>모든 종류에서 id 나 표시 이름으로 찾습니다.</gray>", "", "<yellow>▶ 클릭: 검색어 입력</yellow>",
        ))) {
            var query = ""
            Editors.promptText(
                custom.prompts, viewer, "이름으로 찾기",
                listOf("<gray>id 나 표시 이름의 일부를 적으세요.</gray>"),
                reopen = { if (query.isBlank()) open(viewer) else ItemListMenu(custom, viewer, search = query).open(viewer) },
            ) { raw -> query = raw.trim() }
        }
        set(SLOT_UPGRADES, Icon.of(Material.ANVIL, "<gold>강화 방식 <white>" + custom.upgrades.all().size + "</white>개</gold>", listOf(
            "<gray>무기용·방어구용처럼 여러 아이템이 함께 쓰는 강화표.</gray>", "", "<yellow>▶ 클릭</yellow>",
        ))) { UpgradeTableListMenu(custom, viewer).open(viewer) }
        set(SLOT_EQUIPMENT, Icon.of(Material.ARMOR_STAND, "<gold>장착 칸</gold>", equipmentSummary(custom) + listOf(
            "<dark_gray>플레이어는 /장비 로 엽니다.</dark_gray>", "", "<yellow>▶ 클릭: 기본 칸 수</yellow>",
        ))) { EquipmentSettingsMenu(custom, viewer).open(viewer) }
        set(SLOT_PACK, packButton()) { PackMenu(custom, viewer).open(viewer) }
        set(SLOT_BLOCKS, Icon.of(Material.NOTE_BLOCK, "<gold>커스텀 블록 <white>" + all.count { it.block != null } + "</white>개</gold>", listOf(
            "<gray>블록으로 놓이는 아이템 · 캐는 시간 · 캘 수 있는 도구 ·</gray>",
            "<gray>도구 등급 · 드랍 표 · 옛 ItemsAdder 블록 옮기기</gray>",
            "", "<yellow>▶ 클릭</yellow>",
        ))) { BlockHubMenu(custom, viewer).open(viewer) }
        set(SLOT_SETS, Icon.of(Material.CHAINMAIL_CHESTPLATE, "<yellow>아이템 세트 <white>" + custom.sets.all().size + "</white>개</yellow>",
            listOf("<gray>여러 벌 입으면 벌 수에 따라 효과가 붙습니다.</gray>", "", "<yellow>▶ 클릭</yellow>"))) { ItemSetListMenu(custom, viewer).open(viewer) }
        set(SLOT_CRAFT, Icon.of(Material.SMITHING_TABLE, "<yellow>제작 <white>" + custom.stations.all().size + "</white>곳</yellow>",
            listOf("<gray>제작대와 바닐라 조합법.</gray>", "", "<yellow>▶ 클릭</yellow>"))) { StationListMenu(custom, viewer).open(viewer) }
        set(SLOT_INFO, Icon.of(Material.BOOK, "<aqua>커스텀아이템이란</aqua>", listOf(
            "<gray>여기서 만든 아이템은 <white>다른 INMC 플러그인 전부</white>에서</gray>",
            "<gray>쓸 수 있습니다. 참조는 <white>inmc:아이디</white> 입니다.</gray>",
            "",
            "<gray>낚시의 낚싯대, 인벤키퍼의 보호권, 랜덤박스의 보상에</gray>",
            "<gray>그대로 넣으면 됩니다.</gray>",
        )))
        set(SLOT_TYPES, Icon.of(Material.NAME_TAG, "<yellow>종류 관리</yellow>", listOf(
            "<gray>기본 종류 이름 바꾸기 · 새 종류 만들기(보호권·열쇠 …)</gray>",
            "<gray>만든 종류 <white>" + custom.types.custom().size + "</white>개</gray>",
            "", "<yellow>▶ 클릭</yellow>",
        ))) { TypeListMenu(custom, viewer).open(viewer) }
        set(SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
        fillEmpty(Icon.EDGE)
    }

    private fun packButton() = Icon.of(
        Material.PAINTING,
        "<yellow>리소스팩</yellow>",
        buildList {
            val last = custom.pack.lastReport
            add("<gray>겉모습이 있는 아이템 <white>" + custom.items.all().count { it.texture.isNotBlank() || it.model.isNotBlank() } + "</white>개</gray>")
            if (last != null && last.ok) {
                add("<gray>마지막 빌드: 파일 <white>" + last.fileCount + "</white>개</gray>")
                if (last.missingTextures.isNotEmpty()) add("<red>빠진 텍스처 " + last.missingTextures.size + "개</red>")
            } else {
                add("<dark_gray>아직 만든 적이 없습니다.</dark_gray>")
            }
            add("")
            add("<yellow>▶ 클릭: 팩 만들기 · 합치기</yellow>")
        },
    )

    companion object {
        const val SIZE = 54

        /** 종류 칸. 두 줄에 다섯씩 — 종류를 늘리면 여기도 늘린다(`MenuLayoutTest` 가 모자라면 잡는다). */
        val TYPE_SLOTS = listOf(11, 12, 13, 14, 15, 29, 30, 31, 32, 33)
        const val SLOT_ALL = 22

        /** 만든 종류 칸 — 아홉까지. 더 많으면 종류 관리 화면에서 본다. */
        val CUSTOM_SLOTS = (36..44).toList()
        const val SLOT_TYPES = 4
        const val SLOT_BLOCKS = 8

        const val SLOT_REGISTER = 45
        const val SLOT_UPGRADES = 46
        const val SLOT_EQUIPMENT = 47
        const val SLOT_SEARCH = 48
        const val SLOT_INFO = 49
        const val SLOT_PACK = 50
        const val SLOT_SETS = 51
        const val SLOT_CRAFT = 52
        const val SLOT_CLOSE = 53
    }
}
