package com.inmc.customitems.item

import org.bukkit.Material
import org.bukkit.util.Vector

private const val BACKSTAB = 1.5
private const val PIERCE_RATIO = 0.5
private const val SLAM_RATIO = 0.4

/**
 * 무기의 공격 방식 — MMOItems 의 무기 종류별 동작(단검 뒤치기·창 관통·망치 내려찍기·지팡이 마법탄…).
 *
 * [ItemType] 과 따로 둔다. 종류는 표시와 걸러보기 전용이라는 계약을 지키고, 방식은 무기 종류와
 * 무관하게(예: 막대기 모양의 지팡이) 붙일 수 있어야 한다.
 *
 * 원거리 방식([ranged])의 피해는 **그 사람의 공격력 속성 × [ratio]** 이고, 맞히면 평소 근접 공격처럼
 * 능력치(치명타·흡혈…)와 적중 기능을 탄다. 한 발의 간격은 공격 속도가 정한다(최소 [minCooldown] 초).
 */
enum class AttackStyle(
    val id: String,
    val display: String,
    val icon: Material,
    val description: String,
    val ranged: Boolean = false,
    /** 원거리 방식이 쏘는 클릭. 좌클릭이 아니면 우클릭. */
    val leftClick: Boolean = true,
    val range: Double = 0.0,
    /** 원거리: 공격력에 곱하는 값. 근접: 둘레에 튀는 피해의 비율. */
    val ratio: Double = 1.0,
    val minCooldown: Double = 0.0,
) {
    NONE("none", "기본", Material.IRON_SWORD, "바닐라 공격 그대로"),
    DAGGER("dagger", "단검", Material.IRON_SWORD, "등 뒤에서 치면 피해 ×$BACKSTAB"),
    SPEAR("spear", "창", Material.TRIDENT, "맞힌 적 뒤로 3칸 안의 적도 꿰뚫어 피해 ${(PIERCE_RATIO * 100).toInt()}%", ratio = PIERCE_RATIO, range = 3.0),
    HAMMER("hammer", "망치", Material.MACE, "맞힌 자리 둘레 2.5칸의 적에게 피해 ${(SLAM_RATIO * 100).toInt()}% + 띄움", ratio = SLAM_RATIO, range = 2.5),
    GAUNTLET("gauntlet", "건틀릿", Material.IRON_INGOT, "맞힌 적을 멀리 밀쳐낸다"),
    WHIP("whip", "채찍", Material.LEAD, "허공에 좌클릭하면 8칸 앞의 적을 친다", ranged = true, range = 8.0),
    STAFF("staff", "지팡이", Material.BLAZE_ROD, "좌클릭으로 20칸 마법탄을 쏜다", ranged = true, range = 20.0),
    MUSKET("musket", "머스킷", Material.CROSSBOW, "우클릭으로 40칸 즉발 사격, 피해 ×1.5 (1.5초에 한 발)", ranged = true, leftClick = false, range = 40.0, ratio = 1.5, minCooldown = 1.5),
    ;

    companion object {
        fun of(id: String?): AttackStyle = entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: NONE

        const val BACKSTAB_MULTIPLIER = BACKSTAB

        /**
         * 등 뒤인가. 맞는 쪽과 때리는 쪽이 **같은 쪽을 보고 있으면** 등 뒤다(수평만 본다 — 위아래로
         * 고개를 든 것까지 셈하면 비탈에서 뒤치기가 안 들어간다). 60° 안쪽.
         */
        fun isBehind(victimFacing: Vector, attackerFacing: Vector): Boolean {
            val victim = victimFacing.clone().setY(0)
            val attacker = attackerFacing.clone().setY(0)
            if (victim.lengthSquared() == 0.0 || attacker.lengthSquared() == 0.0) return false
            return victim.normalize().dot(attacker.normalize()) > 0.5
        }

        /** 원거리 한 발의 간격(ms). 공격 속도 4 면 0.25초, 1.6(검)이면 0.625초. */
        fun cooldownMillis(style: AttackStyle, attackSpeed: Double): Long =
            maxOf(1000.0 / attackSpeed.coerceAtLeast(0.1), style.minCooldown * 1000.0).toLong()
    }
}
