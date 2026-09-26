package com.inmc.customitems

import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.FailResult
import com.inmc.customitems.item.ItemBuilder
import com.inmc.customitems.item.ItemInstance
import com.inmc.customitems.item.ItemType
import com.inmc.customitems.item.Lookup
import com.inmc.customitems.item.Stat
import com.inmc.customitems.item.StatCalc
import com.inmc.customitems.item.Tier
import com.inmc.customitems.item.UpgradeMode
import com.inmc.customitems.item.UpgradeSpec
import com.inmc.customitems.item.UpgradeStep
import com.inmc.customitems.item.UpgradeTable
import org.bukkit.Material
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 툴팁 순서(사용자 결정 2026-09-25): **이름 → 종류 → 인첸트 → 능력치 → 강화·세트·소켓·요구 조건 → 등급 → 설명**.
 * 이름은 로어 밖이라 로어는 종류부터다.
 */
class LoreOrderTest {

    private val table = UpgradeTable("t", "t", UpgradeMode.ADD, listOf(
        UpgradeStep(chance = 100.0, fail = FailResult.KEEP, stats = mapOf(Stat.CRIT_CHANCE to 3.0)),
    ))

    private val sword = CustomItem(
        "blade", Material.IRON_SWORD, type = ItemType.WEAPON, tier = Tier.RARE,
        lore = listOf("오래된 검."),
        stats = mapOf(Stat.CRIT_CHANCE to 10.0),
        upgrade = UpgradeSpec(template = "t"),
    )

    private val lookup = Lookup(upgrade = { if (it == "t") table else null })

    private fun lore(item: CustomItem, instance: ItemInstance = ItemInstance(), enchants: List<String> = emptyList(), status: List<String> = emptyList()) =
        ItemBuilder.buildLore(item, StatCalc.total(item, instance, lookup), instance, lookup, enchants, status)

    @Test
    fun `종류 다음 인첸트, 그다음 능력치, 강화, 등급, 설명 순이다`() {
        val lines = lore(sword, ItemInstance(level = 1), enchants = listOf("<gray>날카로움 V</gray>"), status = listOf("<white>보호됨</white>"))
        val type = lines.indexOf(ItemType.WEAPON.header())
        val enchant = lines.indexOf("<gray>날카로움 V</gray>")
        val stat = lines.indexOfFirst { "치명타 확률" in it }
        val upgrade = lines.indexOfFirst { "강화" in it && "+1" in it }
        val status = lines.indexOf("<white>보호됨</white>")
        val tier = lines.indexOf(Tier.RARE.badge())
        val description = lines.indexOf("오래된 검.")
        assertEquals(0, type, lines.toString())
        assertEquals(type + 1, enchant, "인첸트는 종류 바로 아래: $lines")
        assertTrue(enchant < stat && stat < upgrade && upgrade < status && status < tier && tier < description, lines.toString())
    }

    @Test
    fun `강화로 늘어난 몫은 능력치 옆에 (+n) 로 보인다`() {
        val line = lore(sword, ItemInstance(level = 1)).first { "치명타 확률" in it }
        assertTrue("+13" in line && "(+3" in line, line)
        val plain = lore(sword).first { "치명타 확률" in it }
        assertTrue("(+" !in plain, "강화 전에는 (+n) 이 없어야: $plain")
    }

    @Test
    fun `기본에 없던 능력치는 초록 줄로 덧붙는다`() {
        val line = ItemBuilder.statLine(Stat.LIFESTEAL, 4.0, added = true)
        assertTrue(line.startsWith("<green>") && "(+" !in line, line)
    }

    @Test
    fun `공격력과 공격 속도는 바닐라처럼 맨손 기준값을 더해 보인다`() {
        // 네더라이트 곡괭이: 바닐라가 "6 공격 피해 · 1.2 공격 속도" 로 보이던 것(기본 속성 +5 · -2.8)
        val pick = CustomItem("pick", Material.NETHERITE_PICKAXE, type = ItemType.TOOL)
        val lines = ItemBuilder.buildLore(pick, emptyMap(), defaults = mapOf(Stat.ATTACK_DAMAGE to 5.0, Stat.ATTACK_SPEED to -2.8), handHeld = true)
        assertTrue(lines.any { "6 공격력" in it }, lines.toString())
        assertTrue(lines.any { "1.2 공격 속도" in it }, lines.toString())
    }
}
