package com.inmc.customitems

import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.ItemBuilder
import com.inmc.customitems.item.ItemType
import com.inmc.customitems.player.BackpackLayout
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 배낭(사용자 결정 2026-09-30 — 장신구·부적·유물 한 개마다의 창고, 크기 자유, 45칸씩 페이지).
 * 칸 계산이 틀리면 **물건이 안 보이는 칸에 갇히거나**(사라진 것처럼 보인다) 넘기기 줄에 물건이 들어간다.
 */
class BackpackTest {

    @Test
    fun `45칸 이하는 필요한 줄만, 넘으면 6줄에 페이지`() {
        assertEquals(1, BackpackLayout.rows(9))
        assertEquals(2, BackpackLayout.rows(10), "10칸이면 두 줄(한 칸만 쓰는 둘째 줄)")
        assertEquals(5, BackpackLayout.rows(45))
        assertEquals(6, BackpackLayout.rows(46), "46칸부터 넘기기 줄")
        assertEquals(1, BackpackLayout.pages(45))
        assertEquals(2, BackpackLayout.pages(46))
        assertEquals(20, BackpackLayout.pages(BackpackLayout.MAX))
    }

    @Test
    fun `마지막 페이지의 남는 칸과 넘기기 줄에는 못 둔다`() {
        val capacity = 50 // 1페이지 45칸 + 2페이지 5칸
        assertTrue(BackpackLayout.usable(capacity, 0, 44))
        assertFalse(BackpackLayout.usable(capacity, 0, 45), "넘기기 줄")
        assertTrue(BackpackLayout.usable(capacity, 1, 4))
        assertFalse(BackpackLayout.usable(capacity, 1, 5), "50칸을 넘는 칸")
        assertEquals(49, BackpackLayout.index(1, 4))
    }

    @Test
    fun `크기를 줄여도 들어 있는 가장 뒤 칸까지는 보인다`() {
        assertEquals(27, BackpackLayout.capacity(27, null))
        assertEquals(27, BackpackLayout.capacity(27, 10))
        assertEquals(41, BackpackLayout.capacity(27, 40), "27칸으로 줄였는데 40번 칸에 물건이 있다 — 사라지면 안 된다")
        assertEquals(BackpackLayout.MAX, BackpackLayout.capacity(BackpackLayout.MAX + 500, null), "상한")
    }

    @Test
    fun `배낭은 배낭 종류이고 크기가 있어야 한다`() {
        assertTrue(CustomItem("bag", Material.BUNDLE, type = ItemType.BACKPACK, backpack = 27).isBackpack)
        assertFalse(CustomItem("bag", Material.BUNDLE, type = ItemType.BACKPACK).isBackpack, "크기 0")
        // 2026-09-30 부터 배낭은 제 종류가 있다 — 장신구·부적·유물에 남은 크기로는 열리지 않는다(종류를 배낭으로 바꾸면 내용물 그대로).
        for (type in listOf(ItemType.ACCESSORY, ItemType.TALISMAN, ItemType.RELIC, ItemType.WEAPON)) {
            assertFalse(CustomItem("bag", Material.PAPER, type = type, backpack = 27).isBackpack, "$type 에 크기가 있어도 배낭이 아니다")
        }
        assertTrue(ItemType.BACKPACK.slotted, "배낭은 장착 칸에 끼운다")
        assertFalse(ItemType.BACKPACK.carried, "배낭은 가방에 두기만 해서는 효과가 안 난다(장신구처럼)")
    }

    @Test
    fun `배낭 크기·자동 수납·사용 기간이 왕복하고 없으면 적지 않는다`() {
        val bag = CustomItem("bag", Material.BUNDLE, type = ItemType.BACKPACK, backpack = 135, autoPickup = true, period = 7 * 86400L, expiry = com.inmc.customitems.item.Expiry.DISABLE)
        val section = YamlConfiguration().createSection("bag")
        bag.save(section)
        val back = CustomItem.load("bag", section)!!
        assertEquals(135, back.backpack)
        assertTrue(back.autoPickup)
        assertEquals(7 * 86400L, back.period)
        assertEquals(com.inmc.customitems.item.Expiry.DISABLE, back.expiry)
        assertEquals("7d", section.getString("period"), "기간은 사람이 읽는 모양으로")
        val plain = YamlConfiguration().createSection("stick")
        CustomItem("stick", Material.STICK).save(plain)
        for (key in listOf("backpack", "auto-pickup", "period", "expiry")) {
            assertFalse(plain.contains(key), "$key 를 적으면 이 칸이 생긴 것만으로 모든 아이템의 지문이 바뀐다")
        }
    }

    @Test
    fun `강화 단계가 배낭 칸을 쌓아 더하고 자동 수납은 켠 단계부터 이어진다`() {
        val table = com.inmc.customitems.item.UpgradeTable("t", steps = listOf(
            com.inmc.customitems.item.UpgradeStep(backpack = 9),
            com.inmc.customitems.item.UpgradeStep(),
            com.inmc.customitems.item.UpgradeStep(backpack = 18, autoPickup = true),
            com.inmc.customitems.item.UpgradeStep(),
        ))
        assertEquals(0, table.backpackAt(0))
        assertEquals(9, table.backpackAt(1))
        assertEquals(9, table.backpackAt(2))
        assertEquals(27, table.backpackAt(3))
        assertEquals(27, table.backpackAt(10), "최대 단계를 넘어도 있는 단계까지만")
        assertFalse(table.autoPickupAt(2))
        assertTrue(table.autoPickupAt(3))
        assertTrue(table.autoPickupAt(4), "켠 단계 뒤로 이어진다")
        val section = YamlConfiguration().createSection("step")
        table.steps[2].save(section)
        val back = com.inmc.customitems.item.UpgradeStep.load(section)
        assertEquals(18, back.backpack)
        assertTrue(back.autoPickup)
    }

    @Test
    fun `로어에 배낭 안내 줄을 붙이지 않는다`() {
        // 사용자 요청 2026-09-30 — "배낭 — 우클릭으로 열기 …" 줄은 지운다.
        val lines = ItemBuilder.buildLore(CustomItem("bag", Material.PAPER, type = ItemType.TALISMAN, backpack = 54), emptyMap())
        assertTrue(lines.none { "우클릭으로 열기" in it || "/배낭" in it }, lines.toString())
    }
}
