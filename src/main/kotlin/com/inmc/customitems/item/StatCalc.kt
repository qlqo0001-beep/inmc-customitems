package com.inmc.customitems.item

/**
 * 아이템 한 개의 능력치. **정의 + 그 아이템의 몫**([ItemInstance])으로 늘 새로 계산한다.
 *
 * 서버 없이 도는 순수 계산이라 테스트가 전부 덮는다.
 */
object StatCalc {

    /**
     * 굴린 기준값 → 강화 단계 → 수식어 + 박힌 보석. 0 이 된 능력치는 뺀다(로어에 `+0` 줄이 생기지 않게).
     *
     * 강화가 수식어·보석보다 먼저다 — "기본 능력치의 %" 가 보석까지 키우면 보석이 강화 단계만큼 불어난다.
     * 보석은 **보석 정의의 기준값**을 더한다 — 보석을 지우면 박혀 있던 것도 아무것도 더하지 않는다.
     */
    fun total(definition: CustomItem, instance: ItemInstance, lookup: Lookup = Lookup.NONE): Map<Stat, Double> {
        val out = LinkedHashMap<Stat, Double>()
        fun add(stat: Stat, value: Double) {
            out[stat] = Spread.round((out[stat] ?: 0.0) + value)
        }
        val base = definition.stats.mapValues { (stat, value) -> rolled(definition, instance, stat, value) }
        val table = definition.upgrade.table(lookup)
        for ((stat, value) in if (table != null && instance.level > 0) table.apply(base, instance.level) else base) out[stat] = Spread.round(value)
        for (id in instance.modifiers) {
            val modifier = definition.modifiers.firstOrNull { it.id == id } ?: continue
            for ((stat, value) in modifier.stats) add(stat, value)
        }
        for (index in definition.sockets.indices) {
            val gem = instance.gem(index)?.let(lookup.item) ?: continue
            for ((stat, value) in gem.stats) add(stat, value)
        }
        out.values.removeIf { it == 0.0 }
        return out
    }

    /** 폭이 없거나 굴린 적이 없으면(폭을 나중에 붙인 정의) 기준값 그대로. */
    fun rolled(definition: CustomItem, instance: ItemInstance, stat: Stat, base: Double): Double {
        val spread = definition.spreads[stat]?.takeIf { it.spread > 0.0 } ?: return base
        val z = instance.rolls[stat] ?: return base
        return spread.apply(base, z)
    }

    /** 이 정의로 나올 수 있는 가장 작은·큰 값. 편집 화면과 로어의 범위 표시. */
    fun range(definition: CustomItem, stat: Stat): ClosedFloatingPointRange<Double> {
        val base = definition.stat(stat)
        val spread = definition.spreads[stat]?.takeIf { it.spread > 0.0 } ?: return base..base
        val a = Spread.round(base * (1.0 - spread.cap))
        val b = Spread.round(base * (1.0 + spread.cap))
        return minOf(a, b)..maxOf(a, b)
    }
}
