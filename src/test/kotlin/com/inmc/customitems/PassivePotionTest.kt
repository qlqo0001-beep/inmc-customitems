package com.inmc.customitems

import com.inmc.customitems.ability.FLICKER_FREE_TICKS
import com.inmc.customitems.ability.Trigger
import com.inmc.customitems.ability.passivePotionTicks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 지속 물약 깜빡임 방지 — 남은 시간이 절대 11초 아래로 안내려간다.
 * 바닐라는 10초부터 깜빡이므로 주기+11초와 설정값 중 큰 것으로 건다.
 */
class PassivePotionTest {

    @Test
    fun `지속이 아니면 설정값 그대로`() {
        assertEquals(60, passivePotionTicks(Trigger.RIGHT_CLICK, 3.0, 5.0))
    }

    @Test
    fun `지속인데 짧게 적으면 주기+11초로 올라간다`() {
        // 2초 설정·5초 주기 → 100틱이 아니라 100+220틱.
        assertEquals(100 + FLICKER_FREE_TICKS, passivePotionTicks(Trigger.PASSIVE, 2.0, 5.0))
    }

    @Test
    fun `지속인데 길게 적으면 설정값이 이긴다`() {
        assertEquals(600, passivePotionTicks(Trigger.PASSIVE, 30.0, 5.0))
    }

    @Test
    fun `남은 시간 하한은 11초 위다`() {
        // 주기가 최소(0.5초)여도 남은 시간은 11초에서 시작한다.
        val ticks = passivePotionTicks(Trigger.PASSIVE, 0.0, 0.5)
        assertTrue(ticks >= FLICKER_FREE_TICKS)
        assertEquals(220, FLICKER_FREE_TICKS)
    }
}
