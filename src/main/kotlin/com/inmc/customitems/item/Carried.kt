package com.inmc.customitems.item

/**
 * 가방에 든 부적·유물 가운데 **효과가 나는 것**을 고른다. 서버 없이 도는 순수 규칙이다.
 *
 * - 부적: 여러 개가 다 붙는다. 단, "같은 부적 중복 안 함"([CustomItem.noDuplicate])을 켠 부적은 같은 id 끼리
 *   **가장 높은 강화 단계 하나**만 — 강화 단계가 달라도 같은 부적이다.
 * - 유물: 종류가 달라도 **한 번에 하나만**. 등급이 가장 높은 것, 같으면 강화 단계가 높은 것, 그것도 같으면 가방 앞 칸.
 *
 * 한 칸에 겹쳐 든 묶음은 하나로 센다 — 64개 묶음이 64배가 되면 안 된다.
 */
object Carried {

    /** @param index 가방 칸 번호. 같으면 앞 칸이 이긴다. @param tier 강화 단계까지 반영한 등급. */
    data class Candidate(val index: Int, val definition: CustomItem, val level: Int, val tier: Tier)

    fun select(candidates: List<Candidate>): List<Candidate> {
        val best = compareBy<Candidate>({ it.level }, { -it.index })
        val talismans = candidates.filter { it.definition.type == ItemType.TALISMAN }
        val stacking = talismans.filter { !it.definition.noDuplicate }
        val single = talismans.filter { it.definition.noDuplicate }.groupBy { it.definition.id }.values.map { it.maxWith(best) }
        val relic = candidates.filter { it.definition.type == ItemType.RELIC }
            .maxWithOrNull(compareBy<Candidate>({ it.tier.ordinal }, { it.level }, { -it.index }))
        return (stacking + single + listOfNotNull(relic)).sortedBy { it.index }
    }
}
