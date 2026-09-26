package com.inmc.customitems

import com.inmc.customitems.item.AttackStyle
import com.inmc.customitems.item.CustomItem
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.util.Vector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 무기의 공격 방식. */
class AttackStyleTest {

    @Test
    fun `같은 쪽을 보고 있으면 등 뒤다`() {
        val north = Vector(0, 0, -1)
        assertTrue(AttackStyle.isBehind(north, north))
        assertFalse(AttackStyle.isBehind(north, Vector(0, 0, 1)), "마주 보고 친 것은 뒤치기가 아니다")
        assertFalse(AttackStyle.isBehind(north, Vector(1, 0, 0)), "옆에서 친 것도 아니다")
    }

    @Test
    fun `고개를 위아래로 든 것은 뒤치기 판정에 안 들어간다`() {
        // 비탈 아래에서 올려다보며 쳐도 등 뒤는 등 뒤다.
        assertTrue(AttackStyle.isBehind(Vector(0.0, 0.0, -1.0), Vector(0.0, 0.9, -0.3)))
        // 똑바로 위를 보면 수평 방향이 없다 — 판정하지 않는다(0 으로 나누지 않는다).
        assertFalse(AttackStyle.isBehind(Vector(0, 0, -1), Vector(0, 1, 0)))
    }

    @Test
    fun `원거리 한 발의 간격은 공격 속도가 정하고 최소 간격 아래로 안 내려간다`() {
        assertEquals(250L, AttackStyle.cooldownMillis(AttackStyle.STAFF, 4.0))
        assertEquals(625L, AttackStyle.cooldownMillis(AttackStyle.STAFF, 1.6))
        assertEquals(1500L, AttackStyle.cooldownMillis(AttackStyle.MUSKET, 4.0), "머스킷은 공격 속도가 빨라도 1.5초")
        assertEquals(10_000L, AttackStyle.cooldownMillis(AttackStyle.WHIP, 0.0), "0 으로 나누지 않는다")
    }

    @Test
    fun `원거리 방식만 쏘는 클릭을 갖고 머스킷만 우클릭이다`() {
        assertEquals(setOf(AttackStyle.WHIP, AttackStyle.STAFF, AttackStyle.MUSKET), AttackStyle.entries.filter { it.ranged }.toSet())
        assertEquals(listOf(AttackStyle.MUSKET), AttackStyle.entries.filter { it.ranged && !it.leftClick })
        for (style in AttackStyle.entries.filter { it.ranged }) assertTrue(style.range > 0.0, style.id)
    }

    @Test
    fun `공격 방식이 저장해도 그대로 읽히고 기본은 적지 않는다`() {
        val dagger = CustomItem("knife", Material.IRON_SWORD, style = AttackStyle.DAGGER)
        val yaml = YamlConfiguration()
        dagger.save(yaml.createSection("knife"))
        assertEquals("dagger", yaml.getString("knife.attack-style"))
        assertEquals(AttackStyle.DAGGER, CustomItem.load("knife", yaml.getConfigurationSection("knife")!!)!!.style)

        val plain = YamlConfiguration()
        CustomItem("stick", Material.STICK).save(plain.createSection("stick"))
        assertFalse(plain.contains("stick.attack-style"))
        assertEquals(AttackStyle.NONE, AttackStyle.of("모르는값"))
    }
}
