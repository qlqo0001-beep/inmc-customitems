package com.inmc.customitems

import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.ItemBuilder
import com.inmc.customitems.item.ItemInstance
import com.inmc.customitems.item.ItemModifier
import com.inmc.customitems.item.Spread
import com.inmc.customitems.item.Stat
import com.inmc.customitems.item.StatCalc
import org.bukkit.Material
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 아이템마다 흔들리는 능력치·수식어. 서버 없이 도는 순수 계산이다. */
class StatRollTest {

    private val sword = CustomItem(
        id = "blade",
        material = Material.IRON_SWORD,
        displayName = "검",
        stats = mapOf(Stat.ATTACK_DAMAGE to 10.0, Stat.CRIT_CHANCE to 5.0),
        spreads = mapOf(Stat.ATTACK_DAMAGE to Spread(0.1, 0.3)),
        modifiers = listOf(
            ItemModifier("sharp", "날카로운", chance = 100.0, stats = mapOf(Stat.ATTACK_DAMAGE to 2.0)),
            ItemModifier("fiery", "불꽃의", suffix = true, chance = 0.0, stats = mapOf(Stat.FIRE_DAMAGE to 3.0)),
        ),
    )

    @Test
    fun `폭이 없거나 굴린 적이 없으면 기준값 그대로다`() {
        assertEquals(5.0, StatCalc.rolled(sword, ItemInstance(), Stat.CRIT_CHANCE, 5.0))
        // 폭을 나중에 붙인 정의로 옛 아이템을 읽는 경우
        assertEquals(10.0, StatCalc.rolled(sword, ItemInstance(), Stat.ATTACK_DAMAGE, 10.0))
    }

    @Test
    fun `굴린 값은 최대 폭을 넘지 않는다`() {
        // z = 3 이면 폭 10% 로 +30% 가 되지만 최대가 30% 라 딱 거기서 멈춘다. z = 5 여도 같다.
        assertEquals(13.0, StatCalc.rolled(sword, ItemInstance(rolls = mapOf(Stat.ATTACK_DAMAGE to 3.0)), Stat.ATTACK_DAMAGE, 10.0))
        assertEquals(13.0, StatCalc.rolled(sword, ItemInstance(rolls = mapOf(Stat.ATTACK_DAMAGE to 5.0)), Stat.ATTACK_DAMAGE, 10.0))
        assertEquals(7.0, StatCalc.rolled(sword, ItemInstance(rolls = mapOf(Stat.ATTACK_DAMAGE to -5.0)), Stat.ATTACK_DAMAGE, 10.0))
        assertEquals(7.0..13.0, StatCalc.range(sword, Stat.ATTACK_DAMAGE))
    }

    @Test
    fun `최대를 안 적으면 폭의 세 배다`() {
        assertEquals(0.3, Spread(0.1).cap, 1e-9)
        assertEquals(0.5, Spread(0.1, 0.5).cap, 1e-9)
    }

    @Test
    fun `많이 굴리면 전부 범위 안이고 가운데에 몰린다`() {
        val random = Random(42)
        val values = (1..2000).map { StatCalc.total(sword, ItemInstance.roll(sword, random)).getValue(Stat.ATTACK_DAMAGE) - 2.0 }
        assertTrue(values.all { it in 7.0..13.0 }, "범위 밖: " + values.filter { it !in 7.0..13.0 }.take(5))
        val near = values.count { it in 9.0..11.0 }
        assertTrue(near > 1200, "±1 안에 든 것이 $near/2000 — 정규분포가 아니다")
    }

    @Test
    fun `수식어는 확률대로 붙고 능력치를 더한다`() {
        val instance = ItemInstance.roll(sword, Random(1))
        assertEquals(listOf("sharp"), instance.modifiers, "100% 는 늘, 0% 는 절대")
        val total = StatCalc.total(sword, instance.copy(rolls = emptyMap()))
        assertEquals(12.0, total[Stat.ATTACK_DAMAGE])
        assertEquals(null, total[Stat.FIRE_DAMAGE])
    }

    @Test
    fun `지운 수식어는 무시하고 0 이 된 능력치는 뺀다`() {
        val instance = ItemInstance(modifiers = listOf("gone", "sharp"))
        val weakened = sword.copy(stats = mapOf(Stat.ATTACK_DAMAGE to -2.0))
        val total = StatCalc.total(weakened, instance)
        assertEquals(false, total.containsKey(Stat.ATTACK_DAMAGE), "로어에 +0 줄이 생긴다")
    }

    @Test
    fun `아이템에 적는 모양이 왕복한다`() {
        val rolls = mapOf(Stat.ATTACK_DAMAGE to 1.25, Stat.CRIT_CHANCE to -0.5)
        assertEquals(rolls, ItemInstance.decodeRolls(ItemInstance.encodeRolls(rolls)))
        // 능력치를 지운 뒤에도 옛 아이템이 읽혀야 한다
        assertEquals(mapOf(Stat.ATTACK_DAMAGE to 1.0), ItemInstance.decodeRolls("attack-damage=1.0;없는능력치=2;crit-chance=깨짐"))
    }

    @Test
    fun `수식어가 이름 앞뒤에 붙는다`() {
        assertEquals("날카로운 검 불꽃의", ItemBuilder.name(sword, ItemInstance(modifiers = listOf("sharp", "fiery"))))
        assertEquals("검", ItemBuilder.name(sword, ItemInstance()))
        // 이름을 안 적었고 수식어도 없으면 바닐라 이름 그대로
        assertEquals("", ItemBuilder.name(sword.copy(displayName = ""), ItemInstance()))
        assertEquals("날카로운 blade", ItemBuilder.name(sword.copy(displayName = ""), ItemInstance(modifiers = listOf("sharp"))))
    }

    @Test
    fun `능력치 id 는 겹치지 않는다`() {
        val ids = Stat.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (stat in Stat.entries) assertEquals(stat, Stat.of(stat.id))
    }
}
