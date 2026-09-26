package com.inmc.customitems

import com.inmc.customitems.item.ItemSet
import com.inmc.customitems.item.SetBonus
import com.inmc.customitems.item.Stat
import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals

/** 아이템 세트 — 벌 수에 따라 쌓이는 효과. */
class ItemSetTest {

    private val knight = ItemSet(
        id = "knight", name = "기사",
        bonuses = mapOf(
            2 to SetBonus(stats = mapOf(Stat.ARMOR to 2.0)),
            4 to SetBonus(stats = mapOf(Stat.DAMAGE_REDUCTION to 5.0), potions = mapOf("speed" to 1)),
        ),
    )

    @Test
    fun `효과는 벌 수만큼 쌓인다`() {
        assertEquals(emptyList(), knight.active(1).map { it.first })
        assertEquals(listOf(2), knight.active(3).map { it.first })
        assertEquals(listOf(2, 4), knight.active(4).map { it.first }, "4벌이면 2벌 효과도 같이")
        assertEquals(listOf(2, 4), knight.active(6).map { it.first })
    }

    @Test
    fun `MMOItems 모양의 효과를 읽는다`() {
        val yaml = YamlConfiguration().apply {
            loadFromString(
                """
                name: '&2Arcane Set'
                bonuses:
                  '3':
                    magic-damage: 20
                    attack-damage: 3
                  '4':
                    potion-speed: 1
                    potion-INCREASE_DAMAGE: 3
                """.trimIndent(),
            )
        }
        val set = ItemSet.load("arcane", yaml)
        // 모르는 능력치(magic-damage)는 건너뛴다
        assertEquals(mapOf(Stat.ATTACK_DAMAGE to 3.0), set.bonuses.getValue(3).stats)
        assertEquals(mapOf("speed" to 1, "increase_damage" to 3), set.bonuses.getValue(4).potions)
    }

    @Test
    fun `세트가 저장해도 그대로 읽힌다`() {
        val yaml = YamlConfiguration()
        knight.save(yaml.createSection("knight"))
        val reread = YamlConfiguration().apply { loadFromString(yaml.saveToString()) }
        assertEquals(knight, ItemSet.load("knight", reread.getConfigurationSection("knight")!!))
    }

    @Test
    fun `인첸트 효과 트리는 뜻을 몰라도 그대로 저장되고 읽힌다`() {
        // 인첸트 플러그인의 세트 문법 — 우리는 모양만 지킨다(목록·아래 트리·숫자 그대로).
        val effects = mapOf(
            "events" to mapOf("ATTACK" to mapOf("chance" to 15.0, "effects" to listOf("INCREASE_DAMAGE:10", "POTION:SLOWNESS:1:60 @Victim"))),
            "equipped" to listOf("입었다"),
        )
        val set = knight.copy(bonuses = knight.bonuses + (4 to knight.bonuses.getValue(4).copy(effects = effects)) + (5 to SetBonus(effects = effects)))
        val yaml = YamlConfiguration()
        set.save(yaml.createSection("knight"))
        val reread = YamlConfiguration().apply { loadFromString(yaml.saveToString()) }
        val loaded = ItemSet.load("knight", reread.getConfigurationSection("knight")!!)
        assertEquals(set, loaded)
        assertEquals(mapOf(Stat.DAMAGE_REDUCTION to 5.0), loaded.bonuses.getValue(4).stats, "효과 트리가 능력치로 섞이면 안 된다")
        kotlin.test.assertTrue(!SetBonus(effects = effects).isEmpty, "효과만 있는 단계도 비어 있지 않다(지워지면 안 된다)")
    }
}
