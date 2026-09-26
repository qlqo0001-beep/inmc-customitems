package com.inmc.customitems

import com.inmc.customitems.item.ConsumeSpec
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.ItemBuilder
import com.inmc.customitems.item.ItemInstance
import com.inmc.customitems.item.Requirement
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 소모품과 요구 조건. */
class UseTest {

    private val potion = CustomItem(
        "elixir", Material.POTION,
        consume = ConsumeSpec(health = 6.0, food = 4, uses = 3, cooldown = 10.0),
        requirement = Requirement(level = 20, permission = "items.vip"),
    )

    private fun roundTrip(item: CustomItem): CustomItem? {
        val yaml = YamlConfiguration()
        item.save(yaml.createSection(item.id))
        val reread = YamlConfiguration().apply { loadFromString(yaml.saveToString()) }
        return CustomItem.load(item.id, reread.getConfigurationSection(item.id)!!)
    }

    @Test
    fun `소모품과 요구 조건이 저장해도 그대로 읽힌다`() {
        assertEquals(potion, roundTrip(potion))
        val tool = CustomItem("whetstone", Material.FLINT, consume = ConsumeSpec(uses = 0, unsocket = true, repair = 100, repairPercent = 25.0))
        assertEquals(tool, roundTrip(tool))
    }

    @Test
    fun `요구 조건이 없으면 저장하지 않는다`() {
        val yaml = YamlConfiguration()
        CustomItem("plain", Material.STICK).save(yaml.createSection("plain"))
        assertFalse(yaml.contains("plain.requirement"))
    }

    @Test
    fun `끌어다 놓는 힘이 있으면 우클릭으로 안 쓴다`() {
        assertFalse(ConsumeSpec(health = 4.0).dragOnly)
        assertTrue(ConsumeSpec(unsocket = true).dragOnly)
        assertTrue(ConsumeSpec(repairPercent = 10.0).dragOnly)
    }

    @Test
    fun `로어에 회복·남은 횟수·요구 조건이 보인다`() {
        val lore = ItemBuilder.buildLore(potion, emptyMap(), ItemInstance(usesLeft = 2)).joinToString("\n")
        assertTrue(lore.contains("체력 +6"), lore)
        assertTrue(lore.contains("2</white>/3"), lore)
        assertTrue(lore.contains("요구 레벨 20"), lore)
        assertTrue(lore.contains("우클릭"), lore)
    }

    @Test
    fun `감정서와 분해 도구는 끌어다 놓아 쓰고 저장해도 그대로 읽힌다`() {
        val scroll = CustomItem("scroll", Material.PAPER, consume = ConsumeSpec(identify = true))
        val hammer = CustomItem("salvager", Material.IRON_INGOT, consume = ConsumeSpec(uses = 10, deconstruct = true))
        assertEquals(scroll, roundTrip(scroll))
        assertEquals(hammer, roundTrip(hammer))
        assertTrue(scroll.consume!!.dragOnly, "우클릭으로 먹히면 안 된다")
        assertTrue(hammer.consume!!.dragOnly)
    }

    @Test
    fun `미확인 아이템은 만들 때 미확인으로 굴려지고 분해물이 저장된다`() {
        val ruby = kr.inmc.core.item.StoredItem(kr.inmc.core.item.ItemRef.Namespaced("inmc", "ruby"), Material.RED_DYE)
        val relic = CustomItem(
            "relic", Material.GOLDEN_SWORD, unidentified = true,
            salvage = listOf(com.inmc.customitems.craft.Part(ruby, 2)),
        )
        assertEquals(relic, roundTrip(relic))
        assertTrue(ItemInstance.roll(relic, java.util.Random(1)).unidentified)
        assertFalse(ItemInstance.roll(relic.copy(unidentified = false), java.util.Random(1)).unidentified)
    }
}
