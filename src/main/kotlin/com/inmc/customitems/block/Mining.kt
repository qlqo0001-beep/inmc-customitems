package com.inmc.customitems.block

import com.inmc.customitems.item.BlockDrop
import com.inmc.customitems.item.BlockSpec
import org.bukkit.Material
import kotlin.random.Random

/** 커스텀 블록에 맞는 도구의 갈래. 재질 이름으로 가른다(규칙 20 — 서버 없이 어디서 돌려도 같은 답). */
enum class ToolKind(val id: String, val label: String, val icon: Material) {
    PICKAXE("pickaxe", "곡괭이", Material.IRON_PICKAXE),
    AXE("axe", "도끼", Material.IRON_AXE),
    SHOVEL("shovel", "삽", Material.IRON_SHOVEL),
    HOE("hoe", "괭이", Material.IRON_HOE),
    SWORD("sword", "검", Material.IRON_SWORD),
    SHEARS("shears", "가위", Material.SHEARS),
    ;

    companion object {
        fun of(raw: String?): ToolKind? = entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) }

        /** 재질이 어느 도구인가. `DIAMOND_PICKAXE` 는 `_AXE` 로도 끝나지 않는다(앞이 `PICK`). */
        fun ofMaterial(material: Material): ToolKind? = ofName(material.name)

        fun ofName(name: String): ToolKind? {
            val upper = name.uppercase()
            return when {
                upper == "SHEARS" -> SHEARS
                upper == "PICKAXE" || upper.endsWith("_PICKAXE") -> PICKAXE
                upper == "AXE" || upper.endsWith("_AXE") -> AXE
                upper == "SHOVEL" || upper.endsWith("_SHOVEL") -> SHOVEL
                upper == "HOE" || upper.endsWith("_HOE") -> HOE
                upper == "SWORD" || upper.endsWith("_SWORD") -> SWORD
                else -> null
            }
        }
    }
}

/** 맞는 도구가 아닐 때 어떻게 되는가. */
enum class Harvest(val id: String, val label: String, val detail: String) {
    ANY("any", "아무 도구나", "무엇으로 캐든 나온다. 맞는 도구는 빨리 캘 뿐."),
    DROPS("drops", "맞는 도구여야 나옴 (바닐라)", "다른 도구·맨손으로도 부서지지만 느리고 아무것도 안 나온다(돌·광석처럼)."),
    BREAK("break", "맞는 도구여야 부서짐", "맞는 도구가 아니면 아예 캘 수 없다."),
    ;

    companion object {
        fun of(raw: String?): Harvest? = entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) }
    }
}

/** 손에 든 것 — 도구 갈래·등급·재질 빠르기. 도구가 아니면 [kind] 가 null, 빠르기 1. */
data class HeldTool(val kind: ToolKind?, val tier: Int, val speed: Double) {
    companion object {
        val HAND = HeldTool(null, 0, 1.0)
    }
}

/**
 * 바닐라 도구 등급. 등급은 "이 이상이면 캔다"의 숫자이고, 5 부터는 바닐라에 없다 — 커스텀 도구가
 * [com.inmc.customitems.item.CustomItem.miningTier] 로 받는다(특정 커스텀 곡괭이부터 캐지는 광석).
 */
object ToolGrades {

    /** 등급은 여기까지 적을 수 있다. */
    const val MAX_TIER = 20

    /** 재질 앞말 → (등급, 빠르기). 26.2 의 도구 재질(바닐라 `ToolMaterial`). */
    private val GRADES = mapOf(
        "WOODEN" to (0 to 2.0),
        "GOLDEN" to (0 to 12.0),
        "STONE" to (1 to 4.0),
        "COPPER" to (1 to 5.0),
        "IRON" to (2 to 6.0),
        "DIAMOND" to (3 to 8.0),
        "NETHERITE" to (4 to 9.0),
    )

    /** 이 재질을 들었을 때. 도구가 아니면 맨손. [tierOverride] 는 커스텀 도구가 적은 등급(빠르기는 재질 그대로). */
    fun held(material: Material, tierOverride: Int? = null): HeldTool = held(material.name, tierOverride)

    fun held(name: String, tierOverride: Int? = null): HeldTool {
        val kind = ToolKind.ofName(name) ?: return HeldTool.HAND
        val (tier, speed) = when (kind) {
            ToolKind.SHEARS -> 0 to 5.0
            else -> GRADES[name.uppercase().substringBefore('_')] ?: (0 to 1.0)
        }
        return HeldTool(kind, tierOverride ?: tier, if (kind == ToolKind.SWORD) 1.5 else speed)
    }

    /** 재질의 바닐라 등급(도구가 아니면 null). */
    fun vanillaTier(material: Material): Int? = held(material).takeIf { it.kind != null }?.tier

    fun name(tier: Int): String = when (tier) {
        0 -> "나무·금"
        1 -> "돌·구리"
        2 -> "철"
        3 -> "다이아몬드"
        4 -> "네더라이트"
        else -> "$tier 등급"
    }

    /** `다이아몬드 곡괭이 이상` · 등급 0 이면 `곡괭이`. */
    fun requirement(kind: ToolKind, tier: Int): String = if (tier <= 0) kind.label else name(tier) + " " + kind.label + " 이상"
}

/**
 * 캐는 시간과 나오는 것 — 바닐라 공식 그대로라 "진짜 블록처럼" 캐진다. **Bukkit 을 부르지 않는다**: 값은 부르는 쪽이 읽어 넘긴다.
 *
 * 바닐라(`Player.getDestroySpeed`·`BlockBehaviour.getDestroyProgress`)와 같게:
 * - 맞는 도구 갈래면 재질 빠르기, 아니면 1 — 등급이 모자라도 빠르기는 재질대로(나무 곡괭이로 흑요석을 느리게라도 파는 것과 같다)
 * - 빠르기가 1 보다 크면 채굴 효율(효율 인챈트·능력치)을 더한다
 * - 성급함 ×(1 + 0.2·단계) · 채굴 피로 ×0.3^단계 · 블록 파괴 속도 능력치 · 물속 ×물속 채굴 속도 · 공중 ÷5
 * - 한 틱에 `빠르기 ÷ 단단함 ÷ (나오면 30, 아니면 100)` 만큼 진행하고 1 이 되면 부서진다
 */
object Mining {

    /** 맞는 도구(갈래 + 등급)인가. 맞는 도구를 안 정한 블록은 무엇이든 맞다. */
    fun fits(spec: BlockSpec, tool: HeldTool): Boolean = spec.tool == null || (tool.kind == spec.tool && tool.tier >= spec.toolTier)

    fun canBreak(spec: BlockSpec, tool: HeldTool): Boolean = spec.harvest != Harvest.BREAK || fits(spec, tool)

    /** 부쉈을 때 무언가 나오는가. */
    fun yields(spec: BlockSpec, tool: HeldTool): Boolean = spec.harvest == Harvest.ANY || fits(spec, tool)

    /**
     * @param efficiency 채굴 효율(`mining_efficiency` 속성 값)
     * @param haste 성급함·전달체 힘 단계(0 부터), 없으면 -1
     * @param fatigue 채굴 피로 단계, 없으면 -1
     * @param breakSpeed 블록 파괴 속도(`block_break_speed` 속성 값, 우리가 붙인 0 배 수정자는 뺀 것)
     * @param submerged 눈이 물에 잠겼으면 물속 채굴 속도(`submerged_mining_speed`), 아니면 null
     */
    fun speed(
        spec: BlockSpec,
        tool: HeldTool,
        efficiency: Double = 0.0,
        haste: Int = -1,
        fatigue: Int = -1,
        breakSpeed: Double = 1.0,
        submerged: Double? = null,
        onGround: Boolean = true,
    ): Double {
        var speed = if (spec.tool != null && tool.kind == spec.tool) tool.speed else 1.0
        if (speed > 1.0) speed += efficiency
        if (haste >= 0) speed *= 1.0 + (haste + 1) * 0.2
        if (fatigue >= 0) speed *= when (fatigue) {
            0 -> 0.3
            1 -> 0.09
            2 -> 0.0027
            else -> 8.1E-4
        }
        speed *= breakSpeed
        if (submerged != null) speed *= submerged
        if (!onGround) speed /= 5.0
        return speed
    }

    /** 한 틱의 진행(1 이면 부서짐). 단단함 0 은 한 번에. */
    fun perTick(spec: BlockSpec, speed: Double, yields: Boolean): Double =
        if (spec.hardness <= 0.0) 1.0 else speed / spec.hardness / (if (yields) 30.0 else 100.0)

    /** 이 도구로 가만히 서서 캘 때 걸리는 틱 — 화면 안내용. 못 캐면 null. */
    fun ticks(spec: BlockSpec, tool: HeldTool): Int? {
        if (!canBreak(spec, tool)) return null
        val step = perTick(spec, speed(spec, tool), yields(spec, tool))
        if (step <= 0.0) return null
        return kotlin.math.ceil(1.0 / step - 1e-9).toInt().coerceAtLeast(1)
    }

    /** 드랍 표를 굴린다 — 항목마다 확률, 개수는 [BlockDrop.min]~[BlockDrop.max]. [fortune] 은 행운 단계(행운을 안 받는 블록이면 0). */
    fun roll(drops: List<BlockDrop>, fortune: Int, random: Random): List<Pair<BlockDrop, Int>> = drops.mapNotNull { drop ->
        if (drop.chance < 100.0 && random.nextDouble() * 100.0 >= drop.chance) return@mapNotNull null
        var count = if (drop.max > drop.min) random.nextInt(drop.min, drop.max + 1) else drop.min
        // 바닐라 광석(`apply_bonus` ore_drops): ×(1 + max(0, rand(0..행운+1) - 1))
        if (fortune > 0) count *= 1 + (random.nextInt(fortune + 2) - 1).coerceAtLeast(0)
        if (count > 0) drop to count else null
    }

    fun exp(spec: BlockSpec, random: Random): Int =
        if (spec.expMax <= 0) 0 else if (spec.expMax > spec.expMin) random.nextInt(spec.expMin, spec.expMax + 1) else spec.expMax

    /**
     * 속성 값 — 바닐라 `AttributeInstance.calculateValue` 그대로. 우리가 붙인 수정자를 빼고 다시 셀 때 쓴다.
     * @param adds 더하기 · [baseMultipliers] 기본값의 배수 더하기 · [totalMultipliers] 전체 곱하기(1 + 값)
     */
    fun attribute(base: Double, adds: List<Double>, baseMultipliers: List<Double>, totalMultipliers: List<Double>, min: Double = 0.0, max: Double = 1024.0): Double {
        val added = base + adds.sum()
        var value = added
        for (amount in baseMultipliers) value += added * amount
        for (amount in totalMultipliers) value *= 1.0 + amount
        return value.coerceIn(min, max)
    }
}
