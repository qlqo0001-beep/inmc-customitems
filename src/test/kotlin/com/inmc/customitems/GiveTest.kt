package com.inmc.customitems

import com.inmc.customitems.command.Give
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `/커스텀아이템 지급` 의 계산(2026-10-10). 개수가 한 묶음에서 잘리던 결함(칼 10개 → 1개)과 대상 글자(`*`·`**`)를 지킨다.
 */
class GiveTest {

    /** 시험용 묶음 — 크기를 줄일 수 있어야 한다(ItemStack 처럼). */
    private class Part(var size: Int)

    /** 한 묶음이 최대 [max] 개인 아이템을 [total] 개 만들 때 나오는 묶음 크기들. */
    private fun sizes(total: Int, max: Int): List<Int> =
        Give.split(total, { left -> Part(minOf(left, max)) }, { it.size }, { part, size -> part.size = size }).map { it.size }

    @Test
    fun `겹치지 않는 아이템은 하나씩 개수만큼 나온다`() {
        // 칼 10개 → 묶음 10개(전에는 1개만 나가고 "10개" 라고 알렸다).
        assertEquals(List(10) { 1 }, sizes(10, 1))
    }

    @Test
    fun `겹치는 아이템은 한 묶음씩 채우고 나머지를 마지막에`() {
        assertEquals(listOf(64, 64, 2), sizes(130, 64))
        assertEquals(listOf(16, 4), sizes(20, 16))
        assertEquals(listOf(64), sizes(64, 64))
        assertEquals(List(36) { 64 }, sizes(2304, 64))
    }

    @Test
    fun `남은 수보다 크게 만들어지면 남은 수로 줄인다`() {
        // 역할 공급처가 부탁한 수를 무시하고 늘 64개를 줘도 합계가 넘지 않는다.
        val parts = Give.split(70, { Part(64) }, { it.size }, { part, size -> part.size = size })
        assertEquals(listOf(64, 6), parts.map { it.size })
    }

    @Test
    fun `못 만들거나 크기가 0 이면 멈춘다`() {
        assertEquals(emptyList(), Give.split(5, { null }, { it: Part -> it.size }, { _, _ -> }))
        var calls = 0
        val zero = Give.split(5, { calls++; Part(0) }, { it.size }, { _, _ -> })
        assertEquals(emptyList(), zero.map { it.size })
        assertEquals(1, calls, "크기 0 에서 멈추지 않으면 끝없이 돈다")
        // 두 번째부터 못 만들면 거기까지만.
        var made = 0
        val partial = Give.split(10, { if (made++ == 0) Part(1) else null }, { it.size }, { _, _ -> })
        assertEquals(listOf(1), partial.map { it.size })
    }

    @Test
    fun `0 개 이하는 아무것도 만들지 않는다`() {
        assertEquals(emptyList(), sizes(0, 64))
        assertEquals(emptyList(), sizes(-3, 64))
    }

    @Test
    fun `대상 글자를 가른다`() {
        assertEquals(Give.Target.Online, Give.parse("*"))
        assertEquals(Give.Target.Recent, Give.parse("**"))
        assertEquals(Give.Target.Recent, Give.parse(" ** "))
        assertEquals(Give.Target.Named("Steve"), Give.parse("Steve"))
        assertEquals(Give.Target.Named("Steve"), Give.parse(" Steve "))
        assertEquals(Give.Target.Named("***"), Give.parse("***"), "별 셋은 이름으로 — 없는 사람으로 끝난다")
    }

    @Test
    fun `최근 접속은 날 수 안쪽만`() {
        val day = 86_400_000L
        val now = 100 * day
        assertTrue(Give.seenWithin(now - 1, now, 30))
        assertTrue(Give.seenWithin(now - 30 * day, now, 30), "경계는 들어간다")
        assertFalse(Give.seenWithin(now - 30 * day - 1, now, 30))
        assertFalse(Give.seenWithin(0, now, 30), "본 적 없음(0)은 빠진다")
    }

    /** [costs] 를 차례로 [budget] 씩 돌릴 때 틱마다 몇 명을 처리하는가. */
    private fun ticks(costs: List<Int>, budget: Int): List<Int> {
        val queue = costs.iterator()
        val out = ArrayList<Int>()
        var more = true
        while (more) {
            var n = 0
            more = Give.tick(budget) { if (queue.hasNext()) { n++; queue.next() } else null }
            out += n
            assertTrue(out.size <= costs.size + 1, "끝나지 않는다")
        }
        return out
    }

    @Test
    fun `여럿에게 줄 때 한 틱에 예산만큼만 만든다`() {
        // 한 사람 1묶음씩 5명, 예산 2 → 2·2·1 명, 마지막 틱에 끝을 안다.
        assertEquals(listOf(2, 2, 1), ticks(List(5) { 1 }, 2))
        // 예산에 딱 맞게 끝나면 다음 틱이 "다 돌았다"만 확인한다(빈 틱 하나).
        assertEquals(listOf(2, 0), ticks(List(2) { 1 }, 2))
        // 한 사람 몫이 예산보다 커도 그 틱에 한 사람은 처리한다(멈추지 않는다).
        assertEquals(listOf(1, 1, 0), ticks(listOf(2304, 2304), Give.TICK_BUDGET))
        // 0 개(빈손·건너뜀)도 1 로 세어 한 틱에 끝없이 돌지 않는다.
        assertEquals(listOf(3, 2), ticks(List(5) { 0 }, 3))
        // 아무도 없으면 첫 틱에 끝.
        assertEquals(listOf(0), ticks(emptyList(), 10))
    }

    @Test
    fun `알림의 이름은 열 명까지와 나머지 수`() {
        assertEquals("a, b", Give.names(listOf("a", "b")))
        assertEquals((1..10).joinToString(", "), Give.names((1..10).map { it.toString() }))
        assertEquals((1..10).joinToString(", ") + " 외 290명", Give.names((1..300).map { it.toString() }))
    }
}
