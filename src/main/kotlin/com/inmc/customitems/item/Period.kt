package com.inmc.customitems.item

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 사용 기간이 다 된 아이템을 어떻게 할까([CustomItem.expiry]). */
enum class Expiry(val id: String, val display: String) {
    /** 사라진다. 배낭이면 안의 것을 먼저 돌려준다. */
    VANISH("vanish", "사라짐"),

    /** 남되 능력치·기능이 멈추고 장착·사용을 못 한다. */
    DISABLE("disable", "효과 정지"),
    ;

    companion object {
        fun of(raw: String?): Expiry = entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: VANISH
    }
}

/**
 * 사용 기간의 계산(사용자 결정 2026-09-30 — **만들어진 순간부터 실제 시간**). 서버 없이 도는 순수 계산이다.
 *
 * 아이템에는 기간이 아니라 **끝나는 시각**을 찍는다([ItemInstance.expires]) — 접속하지 않아도 흐르고, 관리자가 나중에 기간을
 * 고쳐도 이미 나간 아이템의 약속은 그대로다. 정의에서 기간을 지우면(0) 찍힌 시각은 무시된다(그 아이템은 다시 영구).
 */
object Periods {

    /** [now] 에 만들어진 아이템이 끝나는 시각. */
    fun stamp(period: Long, now: Long): Long = now + period * 1000L

    /** 기간이 있는 정의이고, 찍힌 시각이 지났는가. 아직 안 찍혔으면(기간이 생기기 전에 나간 아이템) 아니다. */
    fun expired(definition: CustomItem, expires: Long?, now: Long): Boolean =
        definition.period > 0 && expires != null && now >= expires

    /** 남은 초. */
    fun left(expires: Long, now: Long): Long = ((expires - now) / 1000L).coerceAtLeast(0L)

    private val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd HH:mm")

    /** 끝나는 때 — `10/07 14:00`. */
    fun until(expires: Long, zone: ZoneId = ZoneId.systemDefault()): String = FORMAT.format(Instant.ofEpochMilli(expires).atZone(zone))
}
