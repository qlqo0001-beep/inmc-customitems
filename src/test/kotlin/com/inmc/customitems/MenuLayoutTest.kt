package com.inmc.customitems

import com.inmc.customitems.gui.EditButton
import com.inmc.customitems.gui.EditTab
import com.inmc.customitems.gui.ItemEditLayout
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.ItemType
import kr.inmc.core.gui.Paging
import org.bukkit.Material
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `아이템 설정 화면 — 모든 버튼이 한 탭에 한 번씩 있다`() {
        // 탭으로 나누다 빠진 버튼은 화면 어디에도 안 그려진다 — 오류 없이 그 설정만 못 고치게 된다.
        val placed = EditTab.entries.flatMap { it.rows.flatten() }
        assertEquals(EditButton.entries.toSet(), placed.toSet(), "탭에 없는 버튼: " + (EditButton.entries - placed.toSet()))
        assertEquals(placed.size, placed.toSet().size, "두 번 놓인 버튼: " + placed.groupBy { it }.filterValues { it.size > 1 }.keys)
    }

    @Test
    fun `아이템 설정 화면 — 어떤 버튼이 숨든 칸이 겹치지 않고 내용 줄 안에 있다`() {
        val fixed = listOf(ItemEditLayout.SLOT_PREVIEW, ItemEditLayout.SLOT_GIVE, Paging.SLOT_BACK, Paging.SLOT_CLOSE) + ItemEditLayout.TAB_SLOTS
        assertEquals(fixed.size, fixed.toSet().size, "고정 칸 충돌: $fixed")
        for (slot in fixed) assertTrue(slot in 0 until ItemEditLayout.SIZE, "$slot 이 창을 벗어납니다")
        assertTrue(ItemEditLayout.TAB_SLOTS.size >= EditTab.entries.size, "탭 칸이 모자랍니다")

        for (tab in EditTab.entries) {
            assertTrue(tab.rows.size <= ItemEditLayout.ROW_STARTS.size, tab.name + " 의 줄이 너무 많습니다")
            for (row in tab.rows) assertTrue(row.size <= ItemEditLayout.PER_ROW, tab.name + " 의 한 줄에 " + row.size + "개")
            // 숨을 수 있는 모든 조합 — 숨은 버튼이 있으면 그 줄이 다시 가운데로 모이는데, 그때도 겹치면 안 된다.
            val buttons = tab.rows.flatten()
            for (mask in 0 until (1 shl buttons.size)) {
                val shown = buttons.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.toSet()
                val slots = ItemEditLayout.place(tab) { it in shown }
                assertEquals(shown, slots.keys, "$tab: 보여야 할 버튼이 안 놓였습니다")
                assertEquals(slots.size, slots.values.toSet().size, "$tab $shown 에서 칸이 겹칩니다: $slots")
                for ((button, slot) in slots) {
                    assertTrue(slot in 18..44, "$tab 의 $button($slot) 이 내용 줄(탭 아래 세 줄)을 벗어납니다")
                    assertTrue(slot !in fixed, "$tab 의 $button($slot) 이 고정 칸을 덮습니다")
                }
            }
        }
    }

    @Test
    fun `아이템 설정 화면 — 한 줄의 버튼은 한 칸씩 띄워 가운데 맞춘다`() {
        assertEquals(listOf(4), ItemEditLayout.columns(1))
        assertEquals(listOf(2, 4, 6), ItemEditLayout.columns(3))
        assertEquals(listOf(1, 3, 5, 7), ItemEditLayout.columns(4))
        assertEquals(listOf(0, 2, 4, 6, 8), ItemEditLayout.columns(5))
    }

    @Test
    fun `아이템 설정 화면 — 그 아이템에 뜻이 없는 버튼은 숨는다`() {
        val sword = CustomItem("sword", Material.IRON_SWORD, type = ItemType.WEAPON)
        val pickaxe = CustomItem("pick", Material.IRON_PICKAXE, type = ItemType.TOOL)
        val talisman = CustomItem("charm", Material.PAPER, type = ItemType.TALISMAN)
        val relic = CustomItem("relic", Material.PAPER, type = ItemType.RELIC)
        val ring = CustomItem("ring", Material.GOLD_NUGGET, type = ItemType.ACCESSORY)

        val bag = CustomItem("bag", Material.BUNDLE, type = ItemType.BACKPACK)
        val gem = CustomItem("gem", Material.EMERALD, type = ItemType.GEM, gem = com.inmc.customitems.item.GemSpec(com.inmc.customitems.item.GemSpec.ANY, 100.0))
        for (item in listOf(sword, pickaxe)) {
            assertFalse(EditButton.BACKPACK.shownFor(item), item.id + " 에 배낭")
            assertFalse(EditButton.AUTO_PICKUP.shownFor(item), item.id + " 에 자동 수납")
            assertFalse(EditButton.INVENTORY_EFFECT.shownFor(item), item.id + " 에 효과가 나는 곳")
            assertFalse(EditButton.NO_DUPLICATE.shownFor(item), item.id + " 에 중복 안 함")
        }
        for (item in listOf(talisman, relic, ring, bag)) {
            assertTrue(EditButton.INVENTORY_EFFECT.shownFor(item), item.id + " 에 효과가 나는 곳이 없다")
        }
        // 배낭 칸은 배낭 종류만, 자동 수납은 배낭과 보석(박으면 그 배낭이 자동 수납)에만.
        assertTrue(EditButton.BACKPACK.shownFor(bag))
        for (item in listOf(talisman, relic, ring)) assertFalse(EditButton.BACKPACK.shownFor(item), item.id + " 에 배낭 칸")
        assertTrue(EditButton.AUTO_PICKUP.shownFor(bag))
        assertTrue(EditButton.AUTO_PICKUP.shownFor(gem))
        assertTrue(EditButton.NO_DUPLICATE.shownFor(talisman))
        assertFalse(EditButton.NO_DUPLICATE.shownFor(relic), "중복 안 함은 부적만 쓴다(Carried)")
        assertTrue(EditButton.MINING_TIER.shownFor(pickaxe))
        assertFalse(EditButton.MINING_TIER.shownFor(talisman), "도구가 아닌데 채굴 등급")
        // 숨은 버튼은 칸이 없다 — 검증기가 그 칸을 누르지 않게.
        assertEquals(null, ItemEditLayout.slotOf(sword, EditButton.BACKPACK))
        assertTrue(ItemEditLayout.slotOf(bag, EditButton.BACKPACK) != null)
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

    @Test
    fun `배낭 화면의 조작 줄은 내용 칸 아래에만 있고 넘기기와 배낭 번호가 겹치지 않는다`() {
        // 버튼이 내용 칸에 겹치면 그 자리의 물건이 버튼에 덮여 안 보이고, 창에서 옮겨 적을 때 사라진다.
        val layout = com.inmc.customitems.player.BackpackLayout
        val menu = com.inmc.customitems.gui.BackpackMenu
        for (capacity in listOf(1, 9, 10, 27, 45, 46, 100, layout.MAX)) {
            val pages = layout.pages(capacity)
            val content = layout.contentRows(capacity) * 9
            assertTrue(content >= minOf(capacity, layout.PER_PAGE), "$capacity 칸인데 내용 칸이 $content")
            for (control in listOf(false, true)) {
                val rows = layout.rows(capacity, control)
                assertTrue(rows <= 6, "$capacity 칸 · 조작 $control → $rows 줄")
                assertEquals(if (pages > 1 || control) content + 9 else content, rows * 9, "$capacity 칸의 조작 줄은 내용 칸 바로 아래 한 줄")
            }
            val paging = if (pages > 1) listOf(menu.COLUMN_PREV, menu.COLUMN_PAGE, menu.COLUMN_NEXT) else emptyList()
            val switch = layout.switchColumns(pages)
            assertEquals(switch.size, switch.toSet().size, "배낭 번호 자리 충돌: $switch")
            assertTrue(switch.all { it in 0..8 } && switch.none { it in paging }, "$capacity 칸: 배낭 번호 $switch 가 넘기기 $paging 를 덮는다")
            if (pages == 1) assertTrue(switch.size >= com.inmc.customitems.player.EquipmentStore.MAX, "한 페이지 배낭에는 배낭 줄(최대 8)이 다 들어가야 한다")
        }
    }
}
