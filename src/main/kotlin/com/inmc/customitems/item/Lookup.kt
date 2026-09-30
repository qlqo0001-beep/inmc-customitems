package com.inmc.customitems.item

/**
 * 아이템을 그리고 계산할 때 **다른 정의**를 찾는 길 — 세트·박힌 보석·공용 강화 방식. [ItemBuilder]·[StatCalc] 는
 * 레지스트리를 모르는 순수한 쪽이라 이것을 받아 쓴다(테스트는 [NONE] 이나 손으로 만든 것을 넘긴다).
 * [inventoryEffects] 는 서버의 "장착 칸 밖에서도 효과" 설정 — 로어와 속성이 그걸 따른다.
 */
class Lookup(
    val set: (String) -> ItemSet? = { null },
    val item: (String) -> CustomItem? = { null },
    val upgrade: (String) -> UpgradeTable? = { null },
    val inventoryEffects: () -> Boolean = { true },
    /** 아이템의 종류(이름을 바꾼 기본 종류·만든 종류). 없으면 기본 종류 그대로. */
    val type: (CustomItem) -> TypeDef? = { null },
    /** 아이템이 든 소분류 — 로어의 종류 줄이 소분류 이름일 수 있다. */
    val category: (CustomItem) -> Category? = { null },
) {
    companion object {
        val NONE = Lookup()
    }
}
