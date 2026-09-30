package com.inmc.customitems.gui

import com.inmc.customitems.block.ToolGrades
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.ItemType
import org.bukkit.Material

/**
 * 아이템 설정 화면([ItemEditMenu])의 배치(사용자 요청 2026-09-30 — "너무 난잡해, 사람이 편한 방식으로").
 *
 * 버튼 서른 개 남짓을 한 창에 깔지 않는다. 미리보기 아래에 **탭 넷**을 두고, 탭 안에서도 **주제마다 한 줄**에
 * 한 칸씩 띄워 가운데 맞춘다. 그 아이템에 뜻이 없는 버튼(부적이 아닌데 "중복 안 함", 도구가 아닌데 "채굴 등급")은
 * 그리지 않는다 — 눌러도 아무 일이 없는 버튼이 가장 헷갈린다.
 *
 * 서버 없이 도는 계산이라 어떤 버튼이 숨든 칸이 겹치지 않는지 `MenuLayoutTest` 가 본다.
 */
enum class EditTab(
    val label: String,
    /** 이름 색(MiniMessage 여는 태그). */
    val color: String,
    val icon: Material,
    /** 이 탭을 보는 동안 탭 줄의 빈칸을 채우는 색유리 — 지금 어느 탭인지 한눈에 보인다. */
    val band: Material,
    /** 한 줄에 한 주제. 순서가 곧 화면의 위 → 아래. */
    val rows: List<List<EditButton>>,
) {
    BASIC("기본", "<aqua>", Material.ITEM_FRAME, Material.LIGHT_BLUE_STAINED_GLASS_PANE, listOf(
        listOf(EditButton.MATERIAL, EditButton.NAME, EditButton.LORE, EditButton.TEXTURE),
        listOf(EditButton.TYPE, EditButton.CATEGORY, EditButton.TIER, EditButton.PERIOD),
    )),
    POWER("능력", "<red>", Material.NETHERITE_SWORD, Material.RED_STAINED_GLASS_PANE, listOf(
        listOf(EditButton.STATS, EditButton.ABILITIES, EditButton.STYLE, EditButton.UPGRADE, EditButton.MINING_TIER),
        listOf(EditButton.ENCHANTS, EditButton.CUSTOM_ENCHANTS, EditButton.MODIFIERS, EditButton.SET),
        listOf(EditButton.REQUIREMENT, EditButton.INVENTORY_EFFECT, EditButton.NO_DUPLICATE),
    )),
    USE("용도", "<green>", Material.BARREL, Material.LIME_STAINED_GLASS_PANE, listOf(
        listOf(EditButton.CONSUME, EditButton.GEM, EditButton.SOCKETS, EditButton.BLOCK),
        listOf(EditButton.BACKPACK, EditButton.AUTO_PICKUP),
        listOf(EditButton.UNIDENTIFIED, EditButton.SALVAGE, EditButton.ROLES, EditButton.DATA),
    )),
    VANILLA("바닐라", "<yellow>", Material.GRASS_BLOCK, Material.YELLOW_STAINED_GLASS_PANE, listOf(
        listOf(EditButton.GLOW, EditButton.UNBREAKABLE, EditButton.DURABILITY),
        listOf(EditButton.FLAGS, EditButton.COMPONENTS, EditButton.VANILLA_USE),
    )),
}

/** 설정 화면의 버튼. 어느 탭의 어느 줄에 있는지는 [EditTab.rows] 가 정한다. */
enum class EditButton(val label: String) {
    MATERIAL("재질"), NAME("표시 이름"), LORE("설명"), TEXTURE("겉모습"),
    TYPE("종류"), CATEGORY("소분류"), TIER("등급"), PERIOD("사용 기간"),
    STATS("능력치"), ABILITIES("기능"), STYLE("공격 방식"), UPGRADE("강화·진화"), MINING_TIER("채굴 등급"),
    ENCHANTS("인챈트"), CUSTOM_ENCHANTS("커스텀 인첸트"), MODIFIERS("수식어"), SET("세트"),
    REQUIREMENT("요구 조건"), INVENTORY_EFFECT("효과가 나는 곳"), NO_DUPLICATE("중복 안 함"),
    CONSUME("소모품"), GEM("보석"), SOCKETS("소켓"), BLOCK("블록"), BACKPACK("배낭"), AUTO_PICKUP("드랍 자동 수납"),
    UNIDENTIFIED("미확인"), SALVAGE("분해물"),
    ROLES("연동 역할"), DATA("연동 값"),
    GLOW("빛나게"), UNBREAKABLE("무한 내구도"), DURABILITY("최대 내구도"),
    FLAGS("숨김 옵션"), COMPONENTS("바닐라 부품"), VANILLA_USE("설치·소모 막기");

    /** 이 버튼이 있는 탭. */
    val tab: EditTab get() = EditTab.entries.first { tab -> tab.rows.any { this in it } }

    /** 이 아이템에 뜻이 있는 버튼인가. */
    fun shownFor(item: CustomItem): Boolean = when (this) {
        BACKPACK -> item.type == ItemType.BACKPACK
        // 보석에 켜 두면 그 보석을 박은 배낭이 자동 수납이 된다.
        AUTO_PICKUP -> item.type == ItemType.BACKPACK || item.gem != null
        INVENTORY_EFFECT -> item.type.slotted
        NO_DUPLICATE -> item.type == ItemType.TALISMAN
        MINING_TIER -> ToolGrades.vanillaTier(item.material) != null
        else -> true
    }
}

object ItemEditLayout {

    const val SIZE = 54

    /** 맨 윗줄 가운데 — 어느 탭에서든 결과가 보인다. */
    const val SLOT_PREVIEW = 4

    /** 둘째 줄에 한 칸씩 띄워 — [EditTab] 순서대로. */
    val TAB_SLOTS = listOf(10, 12, 14, 16)

    const val SLOT_GIVE = 49

    /** 내용 줄의 첫 칸 — 탭 아래 세 줄. 맨 아랫줄은 뒤로·지급·닫기. */
    val ROW_STARTS = listOf(18, 27, 36)

    /** 한 칸씩 띄우면 한 줄에 다섯이 끝이다. */
    const val PER_ROW = 5

    /** [count] 개를 한 칸씩 띄워 가운데 맞춘 열 — 넷이면 1·3·5·7열, 셋이면 2·4·6열. */
    fun columns(count: Int): List<Int> {
        require(count in 0..PER_ROW) { "한 줄에 ${count}개는 못 둡니다" }
        return List(count) { 4 - (count - 1) + it * 2 }
    }

    /** 이 탭에서 보이는 버튼과 그 칸. 숨은 버튼이 있으면 그 줄의 나머지가 다시 가운데로 모인다. */
    fun place(tab: EditTab, shown: (EditButton) -> Boolean): Map<EditButton, Int> = buildMap {
        tab.rows.forEachIndexed { row, buttons ->
            val visible = buttons.filter(shown)
            for ((button, column) in visible.zip(columns(visible.size))) put(button, ROW_STARTS[row] + column)
        }
    }

    /** 그 아이템의 설정 화면에서 [button] 이 있는 칸(그 버튼의 탭을 연 때). 숨은 버튼이면 null. */
    fun slotOf(item: CustomItem, button: EditButton): Int? = place(button.tab) { it.shownFor(item) }[button]
}
