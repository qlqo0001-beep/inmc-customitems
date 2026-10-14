package com.inmc.customitems

import com.inmc.customitems.item.Carried
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.ElementDef
import com.inmc.customitems.item.Elements
import com.inmc.customitems.item.ItemBuilder
import com.inmc.customitems.item.ItemType
import com.inmc.customitems.item.Stat
import com.inmc.customitems.item.Tier
import com.inmc.customitems.item.TierDef
import com.inmc.customitems.item.Tiers
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 관리자가 만드는 등급·속성(사용자 결정 2026-10-13). 목록은 정적이라 검사마다 기본으로 되돌린다. */
class TierElementTest {

    @AfterTest
    fun reset() {
        Tiers.set(Tier.DEFAULTS)
        Elements.set(ElementDef.DEFAULTS)
    }

    private val mmo = listOf(
        TierDef("trash", "E", "<#808080>", "#808080", "#505050"),
        TierDef("common", "D", "<gray>", "#AAAAAA", "#777777"),
        TierDef("unique", "유일", "<#FF75C3>", "#FF75C3", "#C2185B"),
    )

    @Test
    fun `등급 목록이 없으면 예전 여섯 개 그대로 — 색·배지가 안 바뀐다`() {
        assertEquals(listOf("common", "uncommon", "rare", "epic", "legendary", "mythic"), Tier.entries.map { it.id })
        assertEquals("<gold>", Tier.LEGENDARY.color)
        assertEquals(
            "<dark_gray>▬▬▬▬</dark_gray> <gradient:#FFE36E:#FF9A1F><bold>✦ 전설 ✦</bold></gradient> <dark_gray>▬▬▬▬</dark_gray>",
            Tier.LEGENDARY.badge(),
        )
        assertEquals(Tier.COMMON, Tier.of(null), "안 적었으면 목록의 첫 등급")
    }

    @Test
    fun `목록을 바꾸면 이미 읽은 정의에도 곧바로 보이고 순서가 높낮이다`() {
        val item = CustomItem("x", Material.STONE, tier = Tier("unique"))
        assertNull(item.tier.badge(), "목록에 없는 등급은 배지가 없다")
        Tiers.set(mmo)
        assertEquals("유일", item.tier.display)
        assertEquals(2, item.tier.rank)
        assertEquals(Tier("trash"), Tier.default())
        assertTrue(item.tier.badge()!!.contains("유일"))
    }

    @Test
    fun `지운 등급의 id 는 아이템에 남는다 — 같은 id 로 다시 만들면 이어진다`() {
        Tiers.set(mmo)
        val yaml = YamlConfiguration()
        CustomItem("x", Material.STONE, tier = Tier("unique")).save(yaml.createSection("x"))
        Tiers.set(mmo.filter { it.id != "unique" })
        val loaded = CustomItem.load("x", yaml.getConfigurationSection("x")!!)!!
        assertEquals("unique", loaded.tier.id)
        assertNull(loaded.tier.badge())
        assertEquals(-1, loaded.tier.rank)
        assertEquals("unique", YamlConfiguration().also { loaded.save(it.createSection("x")) }.getString("x.tier"))
    }

    @Test
    fun `등급 없음은 배지 줄을 안 그린다`() {
        val lore = ItemBuilder.unidentifiedLore(CustomItem("x", Material.STONE, tier = Tier.NONE))
        assertTrue(lore.none { "▬▬▬▬" in it }, lore.toString())
        assertEquals("없음", Tier.NONE.display)
    }

    @Test
    fun `유물은 지금 목록에서 높은 등급 하나`() {
        Tiers.set(mmo)
        val low = CustomItem("a", Material.HEART_OF_THE_SEA, type = ItemType.RELIC, tier = Tier("unique"))
        val high = CustomItem("b", Material.HEART_OF_THE_SEA, type = ItemType.RELIC, tier = Tier("trash"))
        val chosen = Carried.select(listOf(Carried.Candidate(0, low, 0, low.tier), Carried.Candidate(1, high, 0, high.tier)))
        assertEquals("a", chosen.single().definition.id, "유일(위) 이 E(아래) 를 이긴다")
    }

    @Test
    fun `색은 이름·#값·태그를 다 받고 모르는 것은 버린다`() {
        assertEquals("<gold>", TierDef.colorTag("gold"))
        assertEquals("<gold>", TierDef.colorTag("<GOLD>"))
        assertEquals("<#FFAA00>", TierDef.colorTag("#ffaa00"))
        assertEquals("<#FFAA00>", TierDef.colorTag("<#FFAA00>"))
        assertNull(TierDef.colorTag("금색"))
        assertEquals("#FFAA00", TierDef.gradientColor("#ffaa00"))
    }

    @Test
    fun `기본 속성 넷은 예전 능력치 id 그대로`() {
        assertEquals(Stat.FIRE_DAMAGE, Stat.of("fire-damage"))
        assertEquals(Stat.LIGHTNING_RESIST, Stat.of("LIGHTNING_RESIST"))
        assertEquals("화염 피해", Stat.FIRE_DAMAGE.display)
        assertEquals("냉기 저항", Stat.ICE_RESIST.display)
        assertTrue(Stat.ICE_RESIST.percent)
    }

    @Test
    fun `관리자가 더한 속성은 능력치 둘이 원소 칸에 생기고, 없는 속성은 읽지 않는다`() {
        assertNull(Stat.of("earth-damage"), "목록에 없는 속성 — MMOItems 의 magic-damage 같은 남의 이름이 쌓이지 않게")
        Elements.set(ElementDef.DEFAULTS + ElementDef("earth", "대지"))
        val damage = Stat.of("earth-damage")!!
        assertEquals("대지 피해", damage.display)
        assertEquals("대지 저항", Stat.of("earth-resist")!!.display)
        assertEquals(Stat.Category.ELEMENT, damage.category)
        val ids = Stat.entries.map { it.id }
        assertTrue(ids.indexOf("earth-damage") < ids.indexOf("fire-resist"), "피해 전부 다음 저항 전부: $ids")
        assertTrue(ids.indexOf("earth-resist") < ids.indexOf("exp-bonus"))
        assertEquals(ids.size, ids.toSet().size)
        Elements.set(ElementDef.DEFAULTS)
        assertFalse(damage in Stat.entries, "지우면 목록에서 빠진다")
    }

    @Test
    fun `속성 id 는 영어 소문자·숫자 — 이미 있는 능력치 이름과 겹치면 안 된다`() {
        assertTrue(ElementDef.isValidId("earth"))
        assertTrue(ElementDef.isValidId("wind2"))
        assertFalse(ElementDef.isValidId("attack"), "attack-damage 가 이미 있다")
        assertFalse(ElementDef.isValidId("skill"))
        assertFalse(ElementDef.isValidId("crit"), "crit-damage")
        assertFalse(ElementDef.isValidId("very_cold"), "읽을 때 _ 가 - 로 바뀐다")
        assertFalse(ElementDef.isValidId("땅"))
    }

    @Test
    fun `아이템의 속성 능력치는 저장하고 다시 읽어도 같다`() {
        Elements.set(ElementDef.DEFAULTS + ElementDef("darkness", "어둠"))
        val item = CustomItem("x", Material.IRON_SWORD, stats = mapOf(Stat.elementDamage("darkness") to 7.0, Stat.elementResist("darkness") to -100.0))
        val yaml = YamlConfiguration()
        item.save(yaml.createSection("x"))
        assertEquals(item.stats, CustomItem.load("x", yaml.getConfigurationSection("x")!!)!!.stats)
    }
}
