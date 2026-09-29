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
    fun `배낭은 장신구·부적·유물만이고 크기가 있어야 한다`() {
        assertTrue(CustomItem("bag", Material.PAPER, type = ItemType.TALISMAN, backpack = 27).isBackpack)
        assertTrue(CustomItem("bag", Material.PAPER, type = ItemType.RELIC, backpack = 9).isBackpack)
        assertTrue(CustomItem("bag", Material.PAPER, type = ItemType.ACCESSORY, backpack = 9).isBackpack)
        assertFalse(CustomItem("bag", Material.PAPER, type = ItemType.TALISMAN).isBackpack, "크기 0")
        assertFalse(CustomItem("sword", Material.IRON_SWORD, type = ItemType.WEAPON, backpack = 27).isBackpack, "종류를 바꾸면 크기가 남아도 닫힌다")
    }

    @Test
    fun `배낭 크기가 왕복하고 없으면 적지 않는다`() {
        val bag = CustomItem("bag", Material.PAPER, type = ItemType.RELIC, backpack = 135)
        val section = YamlConfiguration().createSection("bag")
        bag.save(section)
        assertEquals(135, CustomItem.load("bag", section)!!.backpack)
        val plain = YamlConfiguration().createSection("stick")
        CustomItem("stick", Material.STICK).save(plain)
        assertFalse(plain.contains("backpack"), "적으면 이 칸이 생긴 것만으로 모든 아이템의 지문이 바뀐다")
    }

    @Test
    fun `로어에 배낭 표찰이 붙는다`() {
        val lines = ItemBuilder.buildLore(CustomItem("bag", Material.PAPER, type = ItemType.TALISMAN, backpack = 54), emptyMap())
        assertTrue(lines.any { "배낭" in it && "54칸" in it && "/배낭" in it }, lines.toString())
        val weapon = ItemBuilder.buildLore(CustomItem("sword", Material.IRON_SWORD, type = ItemType.WEAPON, backpack = 54), emptyMap())
        assertTrue(weapon.none { "우클릭으로 열기" in it }, "배낭이 아닌 종류에 표찰: $weapon")
    }
}
