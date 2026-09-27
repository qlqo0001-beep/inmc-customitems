package com.inmc.customitems

import kr.inmc.core.gui.Paging
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 화면 슬롯 배치를 못박는다.
 *
 * 슬롯 충돌은 컴파일러가 잡지 못하고 테스트도 보통 놓친다 — 두 버튼이 같은 칸에 그려지면
 * 나중에 그린 쪽만 보이고, **안 보이는 버튼의 클릭 핸들러는 그대로 남아** 엉뚱한 동작을 한다.
 * 범위 밖 슬롯은 `Menu.set` 이 **조용히 무시**하므로 버튼이 그냥 안 그려진다.
 */
class MenuLayoutTest {

    private fun source(file: String) = File("src/main/kotlin/com/inmc/customitems/gui/$file")

    private fun slotsOf(file: String): Map<String, Int> {
        val target = source(file)
        assertTrue(target.exists(), "소스를 찾을 수 없습니다: ${target.absolutePath}")
        return Regex("""const val (SLOT_[A-Z_]+) = (\d+)""")
            .findAll(target.readText())
            .associate { it.groupValues[1] to it.groupValues[2].toInt() }
    }

    private fun listOfInts(file: String, name: String): List<Int> =
        Regex("""val $name = listOf\(([^)]*)\)""")
            .find(source(file).readText())
            ?.groupValues?.get(1)
            ?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
            .orEmpty()

    @Test
    fun `아이템 설정 화면의 슬롯이 겹치지 않는다`() {
        val slots = slotsOf("ItemEditMenu.kt") + mapOf(
            "Paging.SLOT_BACK" to Paging.SLOT_BACK,
            "Paging.SLOT_CLOSE" to Paging.SLOT_CLOSE,
        )

        assertTrue(slots.isNotEmpty(), "슬롯 상수를 하나도 못 읽었습니다")
        assertEquals(
            slots.size,
            slots.values.toSet().size,
            "슬롯 충돌: " + slots.entries.groupBy { it.value }.filterValues { it.size > 1 },
        )
        for ((name, slot) in slots) assertTrue(slot in 0 until 54, "$name($slot) 이 창을 벗어납니다")
    }

    @Test
    fun `기능 설정 화면의 슬롯이 겹치지 않는다`() {
        val slots = slotsOf("AbilityMenus.kt")
        val params = listOfInts("AbilityMenus.kt", "PARAM_SLOTS")

        assertTrue(params.isNotEmpty(), "값 슬롯 목록을 못 읽었습니다")

        val all = slots.values + params + Paging.SLOT_BACK + Paging.SLOT_CLOSE
        assertEquals(all.size, all.toSet().size, "슬롯 충돌: $all")
        for (slot in all) assertTrue(slot in 0 until 54, "$slot 이 창을 벗어납니다")
    }

    @Test
    fun `값 슬롯이 가장 많은 효과를 담을 수 있다`() {
        // 효과가 요구하는 값이 슬롯보다 많으면 **뒤쪽 값이 편집 화면에 안 보인다.**
        // 보이지 않는 값은 기본값으로 굳고, 관리자는 왜 안 바뀌는지 알 수 없다.
        val params = listOfInts("AbilityMenus.kt", "PARAM_SLOTS")
        val needed = com.inmc.customitems.ability.EffectType.entries.maxOf { it.params.size }

        assertTrue(params.size >= needed, "값 슬롯 ${params.size}칸 < 필요한 ${needed}칸")
    }

    @Test
    fun `리소스팩 화면이 27칸을 벗어나지 않는다`() {
        // Menu.set 은 범위 밖 슬롯을 **조용히 무시한다** — 27칸 화면에 Paging 의 45·53번을
        // 쓰면 뒤로·닫기 버튼이 그냥 안 그려진다. 낚시의 손질대에서 실제로 그랬고,
        // 이 화면에서도 한 번 그랬다.
        val slots = slotsOf("PackMenu.kt")

        assertTrue(slots.isNotEmpty(), "슬롯 상수를 하나도 못 읽었습니다")
        assertEquals(
            slots.size,
            slots.values.toSet().size,
            "슬롯 충돌: " + slots.entries.groupBy { it.value }.filterValues { it.size > 1 },
        )
        for ((name, slot) in slots) {
            assertTrue(slot in 0 until 27, name + "(" + slot + ") 이 27칸 창을 벗어납니다")
        }
    }

    @Test
    fun `리소스팩 화면이 Paging 슬롯을 쓰지 않는다`() {
        // 위 검사는 상수만 본다. 코드에서 Paging.SLOT_BACK 을 직접 쓰면 상수가 없어
        // 통과해버리므로 소스를 직접 확인한다.
        val text = source("PackMenu.kt").readText()

        assertTrue(
            !text.contains("Paging.SLOT_"),
            "27칸 화면에서 Paging 슬롯을 쓰면 버튼이 조용히 사라집니다",
        )
    }

    @Test
    fun `목록 화면의 버튼이 항목 칸을 덮지 않는다`() {
        // 항목은 0 부터 PER_PAGE - 1 까지를 쓴다. 버튼이 그 안에 들어가면 항목 하나가
        // 통째로 가려지고, 가려진 항목은 클릭도 되지 않는다.
        // AbilityMenus.kt 는 목록·고르기·편집 세 화면이 한 파일에 있어 여기 기준이 안 맞는다.
        // 그쪽은 위의 전용 검사가 본다.
        for (file in listOf("ItemListMenu.kt", "StatsMenu.kt", "DetailMenus.kt", "CategoryMenus.kt")) {
            for ((name, slot) in slotsOf(file)) {
                assertTrue(slot >= Paging.PER_PAGE, file + " 의 " + name + "(" + slot + ") 이 항목 칸을 덮습니다")
            }
        }
    }

    @Test
    fun `목록과 소분류 서랍의 버튼이 페이지 버튼과 겹치지 않는다`() {
        val paging = mapOf(
            "Paging.SLOT_BACK" to Paging.SLOT_BACK, "Paging.SLOT_PREV" to Paging.SLOT_PREV,
            "Paging.SLOT_NEXT" to Paging.SLOT_NEXT, "Paging.SLOT_CLOSE" to Paging.SLOT_CLOSE,
        )
        for (file in listOf("ItemListMenu.kt", "CategoryMenus.kt")) {
            val all = slotsOf(file).filterKeys { it != "SIZE" } + paging
            assertEquals(all.size, all.values.toSet().size, file + " 슬롯 충돌: " + all.entries.groupBy { it.value }.filterValues { it.size > 1 })
            for ((name, slot) in all) assertTrue(slot in 0 until 54, "$file $name($slot) 이 창을 벗어납니다")
        }
    }

    @Test
    fun `발동 조건 고르기 화면이 모든 발동 조건을 담는다`() {
        // Menu.set 은 범위 밖 슬롯을 조용히 무시한다 — 칸이 모자라면 뒤쪽 발동 조건은 고를 수가 없다.
        val slots = listOfInts("AbilityMenus.kt", "SLOTS")
        assertTrue(slots.size >= com.inmc.customitems.ability.Trigger.entries.size, "발동 조건 " + com.inmc.customitems.ability.Trigger.entries.size + "개 > 칸 " + slots.size + "개")
        assertEquals(slots.size, slots.toSet().size, "칸이 겹칩니다")
        for (slot in slots) assertTrue(slot in 0 until Paging.PER_PAGE, slot.toString() + " 이 버튼 줄을 덮거나 창을 벗어납니다")
    }

    @Test
    fun `블록 화면들의 버튼이 겹치지 않고 목록 칸을 덮지 않는다`() {
        val paging = mapOf(
            "Paging.SLOT_BACK" to Paging.SLOT_BACK, "Paging.SLOT_PREV" to Paging.SLOT_PREV,
            "Paging.SLOT_NEXT" to Paging.SLOT_NEXT, "Paging.SLOT_CLOSE" to Paging.SLOT_CLOSE,
        )
        for (file in listOf("BlockMenu.kt", "BlockDropMenu.kt", "BlockHubMenu.kt", "ModelListMenu.kt")) {
            val all = slotsOf(file) + paging
            assertEquals(all.size, all.values.toSet().size, file + " 슬롯 충돌: " + all.entries.groupBy { it.value }.filterValues { it.size > 1 })
            for ((name, slot) in all) assertTrue(slot in 0 until 54, "$file $name($slot) 이 창을 벗어납니다")
        }
        // 드랍 표·블록 목록은 0 부터 항목을 깐다.
        for (file in listOf("BlockDropMenu.kt", "BlockHubMenu.kt", "ModelListMenu.kt")) {
            for ((name, slot) in slotsOf(file)) assertTrue(slot >= Paging.PER_PAGE, "$file 의 $name($slot) 이 항목 칸을 덮습니다")
        }
    }

    @Test
    fun `종류 서랍 화면이 모든 종류를 담고 칸이 겹치지 않는다`() {
        val types = listOfInts("ItemTypeMenu.kt", "TYPE_SLOTS")
        assertTrue(types.size >= com.inmc.customitems.item.ItemType.entries.size, "종류 " + com.inmc.customitems.item.ItemType.entries.size + "개 > 칸 " + types.size + "개")
        val all = types.mapIndexed { i, slot -> "TYPE_$i" to slot }.toMap() + slotsOf("ItemTypeMenu.kt")
        assertEquals(all.size, all.values.toSet().size, "슬롯 충돌: " + all.entries.groupBy { it.value }.filterValues { it.size > 1 })
        for ((name, slot) in all) assertTrue(slot in 0 until 54, "$name($slot) 이 창을 벗어납니다")
    }
}
