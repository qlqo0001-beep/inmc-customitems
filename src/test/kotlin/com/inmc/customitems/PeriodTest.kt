package com.inmc.customitems

import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.Expiry
import com.inmc.customitems.item.ItemBuilder
import com.inmc.customitems.item.ItemInstance
import com.inmc.customitems.item.ItemType
import com.inmc.customitems.item.Periods
import org.bukkit.Material
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 사용 기간(사용자 결정 2026-09-30 — **받은 순간부터 실제 시간**, 다 되면 사라짐 또는 효과 정지).
 * 판정이 틀리면 조용히 깨진다 — 멀쩡한 아이템이 사라지거나, 끝난 아이템이 계속 능력치를 준다.
 */
class PeriodTest {

    private val week = 7 * 86400L
    private val ticket = CustomItem("ticket", Material.PAPER, type = ItemType.MISC, period = week)

    @Test
    fun `만들어진 순간부터 기간만큼 뒤에 끝난다`() {
        val made = 1_000_000L
        val expires = Periods.stamp(week, made)
        assertEquals(made + week * 1000L, expires)
        assertFalse(Periods.expired(ticket, expires, expires - 1))
        assertTrue(Periods.expired(ticket, expires, expires), "끝나는 시각에 바로 끝난다")
        assertEquals(1L, Periods.left(expires, expires - 1500L))
    }

    @Test
    fun `기간이 없는 정의나 시각이 안 찍힌 아이템은 끝나지 않는다`() {
        assertFalse(Periods.expired(ticket.copy(period = 0), 0L, Long.MAX_VALUE), "관리자가 기간을 지우면 찍힌 시각은 무시된다")
        assertFalse(Periods.expired(ticket, null, Long.MAX_VALUE), "기간이 생기기 전에 나간 아이템 — 처음 볼 때 찍힌다")
    }

    @Test
    fun `로어 — 찍히기 전에는 길이, 찍히면 끝나는 때, 끝나면 빨간 한 줄`() {
        val zone = ZoneId.of("Asia/Seoul")
        val preview = ItemBuilder.periodLines(ticket, ItemInstance(), 0L)
        assertTrue(preview.single().contains("7일") && preview.single().contains("받은 때부터"), preview.toString())

        val expires = java.time.LocalDateTime.of(2026, 10, 7, 14, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals("10/07 14:00", Periods.until(expires, zone))
        val active = ItemBuilder.periodLines(ticket, ItemInstance(expires = expires), expires - 60_000L)
        assertTrue(active.single().contains("까지"), active.toString())

        val over = ItemBuilder.periodLines(ticket, ItemInstance(expires = expires), expires)
        assertTrue(over.single().contains("끝났습니다"), over.toString())
        assertTrue(ItemBuilder.periodLines(ticket.copy(period = 0), ItemInstance(expires = expires), expires).isEmpty())
    }

    @Test
    fun `모르는 만료 방식은 사라짐으로 읽는다`() {
        assertEquals(Expiry.VANISH, Expiry.of(null))
        assertEquals(Expiry.VANISH, Expiry.of("???"))
        assertEquals(Expiry.DISABLE, Expiry.of("Disable"))
    }
}
