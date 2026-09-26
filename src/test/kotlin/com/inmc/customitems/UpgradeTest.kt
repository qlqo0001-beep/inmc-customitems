package com.inmc.customitems

import com.inmc.customitems.item.Carried
import com.inmc.customitems.item.ConsumeSpec
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.Evolution
import com.inmc.customitems.item.EvolveKeep
import com.inmc.customitems.item.EvolveStone
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
import com.inmc.customitems.item.UpgradeStone
import com.inmc.customitems.item.UpgradeTable
import com.inmc.customitems.item.Upgrades
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 강화·진화와 부적·유물. */
class UpgradeTest {

    private fun roundTrip(item: CustomItem): CustomItem? {
        val yaml = YamlConfiguration()
        item.save(yaml.createSection(item.id))
        val reread = YamlConfiguration().apply { loadFromString(yaml.saveToString()) }
        return CustomItem.load(item.id, reread.getConfigurationSection(item.id)!!)
    }

    /** 무기용 공용 방식 — 단계마다 공격력 +2, +3 부터 기본의 10%. */
    private val weapon = UpgradeTable("weapon", "무기용", UpgradeMode.ADD, listOf(
        UpgradeStep(chance = 100.0, stats = mapOf(Stat.ATTACK_DAMAGE to 2.0)),
        UpgradeStep(chance = 80.0, fail = FailResult.KEEP, stats = mapOf(Stat.ATTACK_DAMAGE to 2.0)),
        UpgradeStep(chance = 50.0, fail = FailResult.DOWN, percents = mapOf(Stat.ATTACK_DAMAGE to 10.0, Stat.LUCK to 50.0), tier = Tier.EPIC),
    ))

    /** 첨부한 MMOItems 강화유물처럼 단계마다 능력치 전체를 적는 전용 표(일부). */
    private val relic = UpgradeTable("relic", "강화유물", UpgradeMode.ABSOLUTE, listOf(
        UpgradeStep(stats = mapOf(Stat.CRIT_DAMAGE to 1.0), customModelData = 10075),
        UpgradeStep(stats = mapOf(Stat.CRIT_DAMAGE to 1.0, Stat.ATTACK_DAMAGE to 0.5), tier = Tier.RARE, customModelData = 10076),
        UpgradeStep(tier = Tier.EPIC, texture = "relic_3.png"),
        UpgradeStep(stats = mapOf(Stat.CRIT_DAMAGE to 3.0), fail = FailResult.DESTROY, customModelData = 10034),
    ))

    @Test
    fun `증가량 방식은 단계마다 더하고 기본의 퍼센트도 누적한다`() {
        val base = mapOf(Stat.ATTACK_DAMAGE to 10.0)
        assertEquals(base, weapon.apply(base, 0))
        assertEquals(12.0, weapon.apply(base, 1)[Stat.ATTACK_DAMAGE])
        assertEquals(14.0, weapon.apply(base, 2)[Stat.ATTACK_DAMAGE])
        assertEquals(15.0, weapon.apply(base, 3)[Stat.ATTACK_DAMAGE], "10 + 2 + 2 + 기본의 10%")
        assertNull(weapon.apply(base, 3)[Stat.LUCK], "기본에 없는 능력치의 % 는 0 이라 생기지 않는다")
        assertEquals(15.0, weapon.apply(base, 99)[Stat.ATTACK_DAMAGE], "최대 단계를 넘으면 최대 단계")
    }

    @Test
    fun `전체값 방식은 그 단계의 능력치 전체이고 안 적은 단계는 앞 단계를 잇는다`() {
        val base = mapOf(Stat.LUCK to 1.0)
        assertEquals(mapOf(Stat.CRIT_DAMAGE to 1.0), relic.apply(base, 1), "기본 능력치는 쓰이지 않는다")
        assertEquals(mapOf(Stat.CRIT_DAMAGE to 1.0, Stat.ATTACK_DAMAGE to 0.5), relic.apply(base, 2))
        assertEquals(relic.apply(base, 2), relic.apply(base, 3), "+3 은 등급·모양만 바꾼 단계")
        assertEquals(base, relic.apply(base, 0))
    }

    @Test
    fun `등급과 모양은 이 단계부터이고 번호와 텍스처는 따로 잇는다`() {
        assertNull(relic.tierAt(1))
        assertEquals(Tier.RARE, relic.tierAt(2))
        assertEquals(Tier.EPIC, relic.tierAt(4))
        assertEquals(10076, relic.customModelDataAt(3), "+3 은 번호를 안 바꿨다")
        assertEquals(10034, relic.customModelDataAt(4))
        assertEquals(3, relic.modelAt(4)?.first, "+4 가 번호만 바꿔도 +3 의 텍스처는 이어진다")
        assertNull(relic.modelAt(2))
        assertTrue(relic.hasLevelModels)
    }

    @Test
    fun `강화 설정이 저장해도 그대로 읽힌다`() {
        val sword = CustomItem("sword", Material.IRON_SWORD, upgrade = UpgradeSpec(
            template = "weapon", stones = listOf("stone_a"),
            evolution = Evolution("sword_2", EvolveKeep.INHERIT, byStone = false, station = "forge"),
        ))
        assertEquals(sword, roundTrip(sword))
        val charm = CustomItem("charm", Material.EMERALD, type = ItemType.RELIC, upgrade = UpgradeSpec(own = relic.copy(id = "charm")), noDuplicate = true)
        assertEquals(charm, roundTrip(charm))
        val stone = CustomItem("stone_a", Material.PRISMARINE_SHARD, consume = ConsumeSpec(uses = 1, upgrade = UpgradeStone(listOf("weapon"), listOf("x"), 5, 9, 10.0, 30.0, mapOf(Tier.RARE to 8.0)), evolve = EvolveStone(listOf("sword"))))
        assertEquals(stone, roundTrip(stone))
        assertTrue(stone.consume!!.dragOnly)

        val yaml = YamlConfiguration()
        weapon.save(yaml.createSection("weapon"))
        assertEquals(weapon, UpgradeTable.load("weapon", YamlConfiguration().apply { loadFromString(yaml.saveToString()) }.getConfigurationSection("weapon")!!))
    }

    @Test
    fun `능력치 계산은 강화 단계를 굴린 값 다음 보석 앞에 넣는다`() {
        val gem = CustomItem("ruby", Material.RED_DYE, stats = mapOf(Stat.ATTACK_DAMAGE to 1.0), gem = com.inmc.customitems.item.GemSpec())
        val sword = CustomItem("sword", Material.IRON_SWORD, stats = mapOf(Stat.ATTACK_DAMAGE to 10.0), sockets = listOf("any"), upgrade = UpgradeSpec(template = "weapon"))
        val lookup = Lookup(item = { if (it == "ruby") gem else null }, upgrade = { if (it == "weapon") weapon else null })
        val totals = StatCalc.total(sword, ItemInstance(gems = listOf("ruby"), level = 3), lookup)
        assertEquals(16.0, totals[Stat.ATTACK_DAMAGE], "10 + 2 + 2 + 10% (보석은 %를 안 탄다) + 보석 1")
        assertEquals(11.0, StatCalc.total(sword, ItemInstance(gems = listOf("ruby")), lookup)[Stat.ATTACK_DAMAGE])
    }

    @Test
    fun `이름 뒤에 강화 단계가 붙는다`() {
        val sword = CustomItem("sword", Material.IRON_SWORD, displayName = "철검")
        assertEquals("철검", ItemBuilder.name(sword, ItemInstance()))
        assertEquals("철검 +7", ItemBuilder.name(sword, ItemInstance(level = 7)))
    }

    @Test
    fun `강화석 규칙 — 아이템 쪽 지정·강화석 쪽 지정·방식·단계 구간`() {
        val sword = CustomItem("sword", Material.IRON_SWORD, upgrade = UpgradeSpec(template = "weapon"))
        assertNull(Upgrades.refuse("any", UpgradeStone(), sword, 0), "제한 없는 강화석은 아무 데나")
        assertEquals("upgrade-wrong-stone", Upgrades.refuse("armor_stone", UpgradeStone(templates = listOf("armor")), sword, 0))
        assertNull(Upgrades.refuse("weapon_stone", UpgradeStone(templates = listOf("weapon")), sword, 0))

        val only = sword.copy(upgrade = sword.upgrade.copy(stones = listOf("special")))
        assertEquals("upgrade-wrong-stone", Upgrades.refuse("any", UpgradeStone(), only, 0), "특정 강화석으로만 되는 아이템")
        assertNull(Upgrades.refuse("special", UpgradeStone(), only, 0))

        assertEquals("upgrade-wrong-stone", Upgrades.refuse("s", UpgradeStone(items = listOf("other")), sword, 0), "다른 아이템 전용 강화석")
        val own = CustomItem("relic", Material.EMERALD, upgrade = UpgradeSpec(own = relic))
        assertEquals("upgrade-wrong-stone", Upgrades.refuse("s", UpgradeStone(templates = listOf("weapon")), own, 0), "전용 표 아이템엔 방식 제한 강화석이 안 된다")
        assertNull(Upgrades.refuse("s", UpgradeStone(templates = listOf("weapon"), items = listOf("relic")), own, 0), "콕 집은 아이템이면 된다")

        val high = UpgradeStone(minLevel = 5, maxLevel = 9)
        assertEquals("upgrade-wrong-level", Upgrades.refuse("s", high, sword, 4))
        assertNull(Upgrades.refuse("s", high, sword, 5))
        assertNull(Upgrades.refuse("s", high, sword, 9))
        assertEquals("upgrade-wrong-level", Upgrades.refuse("s", high, sword, 10))
    }

    @Test
    fun `성공 확률 — 강화석의 등급별 확률, 강화석 고정 확률, 단계 확률 순이고 보너스는 위에 더한다`() {
        val step = UpgradeStep(chance = 40.0)
        assertEquals(40.0, Upgrades.chance(step, UpgradeStone(), Tier.RARE))
        assertEquals(45.0, Upgrades.chance(step, UpgradeStone(bonus = 5.0), Tier.RARE))
        assertEquals(10.0, Upgrades.chance(step, UpgradeStone(chance = 10.0), Tier.RARE), "MMOItems 식 — 강화석이 정한다")
        val table = UpgradeStone(chance = 10.0, chances = mapOf(Tier.COMMON to 10.0, Tier.RARE to 8.0, Tier.MYTHIC to 0.01))
        assertEquals(8.0, Upgrades.chance(step, table, Tier.RARE), "등급별 확률표(첨부 강화석 로어)")
        assertEquals(0.01, Upgrades.chance(step, table, Tier.MYTHIC))
        assertEquals(10.0, Upgrades.chance(step, table, Tier.EPIC), "표에 없는 등급은 고정 확률로")
        assertEquals(100.0, Upgrades.chance(step, UpgradeStone(chance = 90.0, bonus = 50.0), Tier.RARE), "100 을 넘지 않는다")
    }

    @Test
    fun `실패 결과`() {
        assertEquals(5, Upgrades.afterFail(FailResult.KEEP, 5))
        assertEquals(4, Upgrades.afterFail(FailResult.DOWN, 5))
        assertEquals(0, Upgrades.afterFail(FailResult.DOWN, 0))
        assertEquals(0, Upgrades.afterFail(FailResult.RESET, 5))
        assertEquals(-1, Upgrades.afterFail(FailResult.DESTROY, 5))
    }

    @Test
    fun `진화는 최대 강화에서만 되고 강화가 없으면 언제든 된다`() {
        val next = CustomItem("sword_2", Material.DIAMOND_SWORD)
        val sword = CustomItem("sword", Material.IRON_SWORD, upgrade = UpgradeSpec(template = "weapon", evolution = Evolution("sword_2")))
        val lookup = Lookup(item = { if (it == "sword_2") next else null }, upgrade = { if (it == "weapon") weapon else null })
        assertFalse(Upgrades.canEvolve(sword, 2, lookup))
        assertTrue(Upgrades.canEvolve(sword, 3, lookup))
        assertTrue(Upgrades.canEvolve(sword.copy(upgrade = UpgradeSpec(evolution = Evolution("sword_2"))), 0, lookup))
        assertFalse(Upgrades.canEvolve(sword.copy(upgrade = UpgradeSpec(evolution = Evolution("gone"))), 0, lookup), "진화 대상이 지워졌으면 안 된다")
    }

    private fun candidate(index: Int, item: CustomItem, level: Int = 0, tier: Tier = item.tier) = Carried.Candidate(index, item, level, tier)

    @Test
    fun `부적은 겹쳐 붙고 중복 안 함을 켜면 같은 부적은 가장 높은 단계 하나만`() {
        val luck = CustomItem("luck", Material.PAPER, type = ItemType.TALISMAN)
        val guard = CustomItem("guard", Material.PAPER, type = ItemType.TALISMAN, noDuplicate = true)
        val chosen = Carried.select(listOf(
            candidate(0, luck), candidate(1, luck),
            candidate(2, guard, level = 3), candidate(3, guard, level = 7), candidate(4, guard, level = 7),
        ))
        assertEquals(listOf(0, 1, 3), chosen.map { it.index }, "중복 허용 부적 둘 + 같은 부적은 +7 중 앞 칸 하나")
    }

    @Test
    fun `유물은 종류가 달라도 하나만 — 등급 다음 강화 단계 다음 앞 칸`() {
        val a = CustomItem("a", Material.HEART_OF_THE_SEA, type = ItemType.RELIC, tier = Tier.RARE)
        val b = CustomItem("b", Material.HEART_OF_THE_SEA, type = ItemType.RELIC, tier = Tier.LEGENDARY)
        assertEquals(listOf(5), Carried.select(listOf(candidate(2, a, level = 9), candidate(5, b))).map { it.index })
        assertEquals(listOf(4), Carried.select(listOf(candidate(4, a, level = 3), candidate(1, a, level = 2))).map { it.index })
        assertEquals(listOf(1), Carried.select(listOf(candidate(4, a), candidate(1, a))).map { it.index })
        assertEquals(listOf(0, 3), Carried.select(listOf(candidate(0, CustomItem("t", Material.PAPER, type = ItemType.TALISMAN)), candidate(3, b))).map { it.index }, "부적과 유물은 따로 센다")
    }
}
