package com.inmc.customitems.command

/**
 * 지급 명령의 계산 — 서버 없이 시험하려고 명령 클래스에서 떼어 냈다.
 */
internal object Give {

    /** 지급 대상. `*` = 지금 접속한 사람 전원, `**` = 최근 N일 안에 들어온 사람 전원(접속하지 않은 사람은 우편). */
    sealed interface Target {
        data class Named(val name: String) : Target
        data object Online : Target
        data object Recent : Target
    }

    fun parse(raw: String): Target = when (val name = raw.trim()) {
        "*" -> Target.Online
        "**" -> Target.Recent
        else -> Target.Named(name)
    }

    /** 마지막으로 본 때가 [days] 일 안인가. 0 이하는 "본 적 없음"(Bukkit 의 `getLastSeen` 이 그렇게 준다). */
    fun seenWithin(lastSeen: Long, now: Long, days: Int): Boolean =
        lastSeen > 0 && now - lastSeen <= days * DAY

    /**
     * [total] 개를 묶음으로 나눠 만든다. [make] 에 남은 수를 주면 한 묶음을 만든다 — 아이템을 한 번 만들면 한 묶음(칼은 1개)까지라
     * 그대로 주면 10개가 1개로 잘렸다. 남은 수보다 크면 [trim] 으로 줄이고, 못 만들거나(null) 크기가 0 이하면 거기서 멈춘다.
     * 한 바퀴에 적어도 하나씩 줄어 [total] 바퀴를 넘지 않는다.
     */
    fun <T> split(total: Int, make: (left: Int) -> T?, sizeOf: (T) -> Int, trim: (T, Int) -> Unit): List<T> {
        val out = ArrayList<T>()
        var left = total
        while (left > 0) {
            val part = make(left) ?: break
            val size = sizeOf(part)
            if (size <= 0) break
            if (size > left) trim(part, left)
            out += part
            left -= minOf(size, left)
        }
        return out
    }

    /** 여럿에게 줄 때 한 틱에 만드는 묶음 수(적어도 한 사람). 아이템 하나를 만들고 그리는 데 수십 µs — 한 틱 몇 ms 안쪽. */
    const val TICK_BUDGET = 200

    /**
     * 한 틱 몫. [next] 를 불러 만든 묶음 수를 더하다가 [budget] 에 닿으면 멈춘다 — 적어도 한 번은 부르고, 0 개도 1 로 센다(빈손인 사람이
     * 많아도 한 틱에 끝없이 돌지 않게). [next] 가 null 이면 다 돈 것 — false. 아직 남았을 수 있으면 true(다음 틱에 다시).
     */
    fun tick(budget: Int, next: () -> Int?): Boolean {
        var spent = 0
        while (true) {
            val cost = next() ?: return false
            spent += cost.coerceAtLeast(1)
            if (spent >= budget) return true
        }
    }

    /** 알림에 넣을 이름들 — 앞의 [max] 명과 "외 N명". */
    fun names(all: List<String>, max: Int = 10): String =
        all.take(max).joinToString(", ") + if (all.size > max) " 외 " + (all.size - max) + "명" else ""

    private const val DAY = 86_400_000L
}
