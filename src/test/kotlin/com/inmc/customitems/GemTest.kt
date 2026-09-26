package com.inmc.customitems

import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.GemSpec
import com.inmc.customitems.item.ItemBuilder
import com.inmc.customitems.item.ItemInstance
import com.inmc.customitems.item.ItemType
import com.inmc.customitems.item.Lookup
import com.inmc.customitems.item.Stat
import com.inmc.customitems.item.StatCalc
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 보석과 소켓. */
class GemTest {

    private val ruby = CustomItem("ruby", Material.RED_DYE, type = ItemType.GEM, stats = mapOf(Stat.ATTACK_DAMAGE to 3.0), gem = GemSpec("red", 80.0))
    private val sword = CustomItem("blade", Material.IRON_SWORD, stats = mapOf(Stat.ATTACK_DAMAGE to 10.0), sockets = listOf("red", "any"))
    private val lookup = Lookup(item = { if (it == "ruby") ruby else null })

    @Test
    fun `색이 같거나 어느 쪽이 any 면 맞는다`() {
        assertTrue(GemSpec("red").fits("red"))
        assertTrue(GemSpec("red").fits(GemSpec.ANY))
        assertTrue(GemSpec(GemSpec.ANY).fits("blue"))
        assertFalse(GemSpec("red").fits("blue"))
    }

    @Test
    fun `박힌 보석의 기준 능력치가 더해진다`() {
        val instance = ItemInstance().withGem(0, "ruby").withGem(1, "ruby")
        assertEquals(16.0, StatCalc.total(sword, instance, lookup)[Stat.ATTACK_DAMAGE])
    }

    @Test
    fun `지운 보석과 소켓 밖의 보석은 아무것도 더하지 않는다`() {
        // 보석 정의가 사라졌다
        assertEquals(10.0, StatCalc.total(sword, ItemInstance().withGem(0, "gone"), lookup)[Stat.ATTACK_DAMAGE])
        // 소켓을 줄인 정의로 옛 아이템(세 번째 소켓에 보석)을 읽는다
        assertEquals(10.0, StatCalc.total(sword, ItemInstance().withGem(2, "ruby"), lookup)[Stat.ATTACK_DAMAGE])
    }

    @Test
    fun `빈 소켓을 건너뛰어 박을 수 있다`() {
        val instance = ItemInstance().withGem(1, "ruby")
        assertEquals(listOf("", "ruby"), instance.gems)
        assertNull(instance.gem(0))
        assertEquals("ruby", instance.gem(1))
    }

    @Test
    fun `로어에 박힌 보석과 빈 소켓이 보인다`() {
        val lore = ItemBuilder.buildLore(sword, emptyMap(), ItemInstance().withGem(0, "ruby"), lookup)
        assertTrue(lore.any { it.contains("ruby") && it.contains("◆") }, lore.joinToString("\n"))
        assertTrue(lore.any { it.contains("빈 소켓") && it.contains("아무") }, lore.joinToString("\n"))
    }

    @Test
    fun `보석·소켓 설정이 저장해도 그대로 읽힌다`() {
        for (item in listOf(ruby, sword)) {
            val yaml = YamlConfiguration()
            item.save(yaml.createSection(item.id))
            val reread = YamlConfiguration().apply { loadFromString(yaml.saveToString()) }
            assertEquals(item, CustomItem.load(item.id, reread.getConfigurationSection(item.id)!!))
        }
    }
}
