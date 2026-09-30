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

    // --- 부적·유물·장신구·배낭 표찰 — 없앴다(사용자 요청 2026-10-01: 따옴표 안 내용은 모두 없애) -------------------

    @Test
    fun `장착 효과·소지 효과·중복 불가·단 하나 표찰은 어느 종류에도 없다`() {
        val tags = listOf("소지 효과", "장착 효과", "중복 불가", "단 하나", "가방에 지니기만", "장착 칸(/장비)에 끼워야")
        val items = listOf(
            CustomItem("charm", Material.PAPER, type = ItemType.TALISMAN, noDuplicate = true),
            CustomItem("relic", Material.HEART_OF_THE_SEA, type = ItemType.RELIC),
            CustomItem("ring", Material.GOLD_NUGGET, type = ItemType.ACCESSORY),
            CustomItem("bag", Material.BUNDLE, type = ItemType.BACKPACK, backpack = 9, stats = mapOf(Stat.CRIT_CHANCE to 1.0)),
        )
        for (item in items) for (effects in listOf(true, false)) {
            val lines = ItemBuilder.buildLore(item, emptyMap(), lookup = Lookup(inventoryEffects = { effects }))
            assertTrue(lines.none { line -> tags.any { it in line } }, item.id + " 에 표찰이 남았다: $lines")
        }
    }

    // --- 물약 기능 줄 (사용자 요청 2026-09-30: "지속효과 : 야간투시") ------------------------------

    private fun potion(trigger: com.inmc.customitems.ability.Trigger, vararg values: Pair<String, String>, effect: com.inmc.customitems.ability.EffectType = com.inmc.customitems.ability.EffectType.POTION) =
        com.inmc.customitems.ability.Ability.of(trigger, effect).copy(values = values.toMap()).line()

    @Test
    fun `지속 물약은 어떤 효과인지 적고 시간은 적지 않는다`() {
        val line = potion(com.inmc.customitems.ability.Trigger.PASSIVE, "effect" to "NIGHT_VISION", "seconds" to "15")
        assertTrue("지속 효과" in line, line)
        assertTrue("<lang:effect.minecraft.night_vision>" in line, line)
        assertTrue("물약 효과" !in line && "초" !in line, line)
    }

    @Test
    fun `다른 발동 물약은 레벨과 시간과 대상까지 적는다`() {
        val self = potion(com.inmc.customitems.ability.Trigger.RIGHT_CLICK, "effect" to "minecraft:speed", "level" to "2", "seconds" to "5")
        assertTrue("<lang:effect.minecraft.speed> <lang:enchantment.level.2> 5초" in self, self)
        assertTrue("지속 효과" !in self, self)
        val other = potion(com.inmc.customitems.ability.Trigger.ON_HIT, "effect" to "SLOWNESS", "seconds" to "3", "target" to "other")
        assertTrue("대상에게 <lang:effect.minecraft.slowness> 3초" in other, other)
        val area = potion(com.inmc.customitems.ability.Trigger.ON_HIT, "effect" to "POISON", effect = com.inmc.customitems.ability.EffectType.AOE_POTION)
        assertTrue("주변에 <lang:effect.minecraft.poison>" in area, area)
    }

    @Test
    fun `물약이 아닌 기능과 비어 있는 효과는 종류 이름 그대로다`() {
        val bolt = com.inmc.customitems.ability.Ability.of(com.inmc.customitems.ability.Trigger.ON_HIT, com.inmc.customitems.ability.EffectType.LIGHTNING).line()
        assertTrue(com.inmc.customitems.ability.EffectType.LIGHTNING.display in bolt, bolt)
        assertEquals(null, com.inmc.customitems.ability.Ability.potionKey("  "))
        assertEquals("effect.minecraft.night_vision", com.inmc.customitems.ability.Ability.potionKey("minecraft:NIGHT_VISION"))
    }
}
