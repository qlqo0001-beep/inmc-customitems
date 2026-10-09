package com.inmc.customitems.item

import com.inmc.customitems.block.Harvest
import com.inmc.customitems.block.ToolKind
import kr.inmc.core.item.StoredItem
import org.bukkit.configuration.ConfigurationSection

/**
 * 커스텀 블록을 세상에 놓는 방식. 두 갈래를 다 쓸 수 있다(사용자 결정 2026-09-26).
 *
 * - **블록 상태 방식**([SOLID]·[TRANSPARENT]) — ItemsAdder 와 같다. 바닐라 블록의 안 쓰는 상태에 모델을 입힌다. 진짜 블록이라 가볍다.
 *   Paper 의 `block-updates.disable-noteblock-updates` / `disable-chorus-plant-updates` 를 켜야 한다(안 켜면 옆 블록이 바뀔 때 모양이 풀린다).
 * - **엔티티 방식**([ENTITY]) — 방벽 블록 + 그 자리에 떠 있는 모델(ItemDisplay). 모양·개수에 제한이 없고 서버 설정을 안 건드린다.
 *   블록 하나에 엔티티 하나다.
 */
enum class BlockKind(val id: String, val label: String, val base: org.bukkit.Material) {
    /** 소리블록 상태 — 꽉 찬 블록. 675칸(바닐라 harp 25칸을 뺀 650칸). */
    SOLID("solid", "꽉 찬 블록 (소리블록)", org.bukkit.Material.NOTE_BLOCK),

    /** 후렴초 줄기 상태 — 투명·자유 모양(상자처럼). 62칸. */
    TRANSPARENT("transparent", "투명·자유 모양 (후렴초)", org.bukkit.Material.CHORUS_PLANT),

    /** 방벽 + 떠 있는 모델. */
    ENTITY("entity", "엔티티 (방벽 + 모델)", org.bukkit.Material.BARRIER),
    ;

    val usesState: Boolean get() = this != ENTITY

    companion object {
        fun of(raw: String?): BlockKind? = entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) }
    }
}

/**
 * 이 아이템을 블록으로 놓는다.
 *
 * @param state 블록 상태 방식이면 차지한 상태(`instrument=basedrum,note=9` · `down=false,…,west=true`) — 처음 정할 때 빈 칸을 골라
 *   **적어 둔다.** 다시 고르지 않는다: 바꾸면 이미 놓인 블록이 다른 블록으로 보인다. 엔티티 방식은 비어 있다.
 * @param drop 부수면 이 아이템(블록 자신)이 나온다. 끄면 [drops] 만 나온다(ItemsAdder `drop_when_mined`).
 * @param hardness 바닐라와 같은 단위의 단단함(돌 1.5 · 철광석 3 · 흑요석 50). 0 이면 한 번에 부서진다. 캐는 시간은 [com.inmc.customitems.block.Mining].
 * @param tool 맞는 도구. 이 도구면 도구 재질만큼 빨리 캔다. null 이면 무엇으로 캐든 맨손 빠르기.
 * @param toolTier 맞는 도구의 최소 등급(0 나무·금 · 1 돌·구리 · 2 철 · 3 다이아몬드 · 4 네더라이트 · 5 부터는 커스텀 도구가 [CustomItem.miningTier] 로).
 * @param harvest 맞는 도구(종류 + 등급)가 아닐 때 — [Harvest].
 * @param drops 부수면 나오는 것(확률·개수). [drop] 과 따로 — 광석처럼 블록 대신 다른 것을 주려면 [drop] 을 끄고 여기에 적는다.
 * @param silkTouch 섬세한 손길 도구로 캐면 [drops] 대신 블록 자신이 나온다(바닐라 광석).
 * @param fortune 행운이 [drops] 의 개수를 늘린다(바닐라 광석 공식).
 * @param generation 월드 생성(광맥) — 처음 만들어지는 청크에 심는다([com.inmc.customitems.block.OreGenerator]). 엔티티 방식은 안 된다. 지문에서 뺀다.
 */
data class BlockSpec(
    val kind: BlockKind,
    val state: String = "",
    val drop: Boolean = true,
    val hardness: Double = DEFAULT_HARDNESS,
    val tool: ToolKind? = null,
    val toolTier: Int = 0,
    val harvest: Harvest = Harvest.DROPS,
    val drops: List<BlockDrop> = emptyList(),
    val silkTouch: Boolean = false,
    val fortune: Boolean = false,
    val expMin: Int = 0,
    val expMax: Int = 0,
    val generation: com.inmc.customitems.block.OreGen? = null,
) {

    /** 기본값은 적지 않는다 — 적으면 이 칸이 생긴 것만으로 모든 블록 아이템의 지문이 바뀌어 다시 그린다. */
    fun save(section: ConfigurationSection) {
        section.set("kind", kind.id)
        if (state.isNotBlank()) section.set("state", state)
        if (!drop) section.set("drop", false)
        if (hardness != DEFAULT_HARDNESS) section.set("hardness", hardness)
        tool?.let { section.set("tool", it.id) }
        if (toolTier != 0) section.set("tool-tier", toolTier)
        if (harvest != Harvest.DROPS) section.set("harvest", harvest.id)
        if (drops.isNotEmpty()) {
            val node = section.createSection("drops")
            for ((index, entry) in drops.withIndex()) entry.save(node.createSection(index.toString()))
        }
        if (silkTouch) section.set("silk-touch", true)
        if (fortune) section.set("fortune", true)
        if (expMax > 0) {
            section.set("exp-min", expMin)
            section.set("exp-max", expMax)
        }
        generation?.save(section.createSection("generation"))
    }

    companion object {
        /** 돌과 같다. */
        const val DEFAULT_HARDNESS = 1.5

        const val MAX_HARDNESS = 1000.0

        fun load(section: ConfigurationSection?): BlockSpec? {
            val kind = BlockKind.of(section?.getString("kind")) ?: return null
            section!!
            val expMax = section.getInt("exp-max", 0).coerceAtLeast(0)
            return BlockSpec(
                kind = kind,
                state = if (kind.usesState) section.getString("state").orEmpty().trim() else "",
                drop = section.getBoolean("drop", true),
                hardness = section.getDouble("hardness", DEFAULT_HARDNESS).coerceIn(0.0, MAX_HARDNESS),
                tool = ToolKind.of(section.getString("tool")),
                toolTier = section.getInt("tool-tier", 0).coerceIn(0, com.inmc.customitems.block.ToolGrades.MAX_TIER),
                harvest = Harvest.of(section.getString("harvest")) ?: Harvest.DROPS,
                drops = section.getConfigurationSection("drops")?.let { node ->
                    // 번호 순서대로 — 화면의 순서가 곧 적힌 순서다.
                    node.getKeys(false).sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }.mapNotNull { key ->
                        node.getConfigurationSection(key)?.let(BlockDrop::load)
                    }
                }.orEmpty(),
                silkTouch = section.getBoolean("silk-touch", false),
                fortune = section.getBoolean("fortune", false),
                expMin = section.getInt("exp-min", 0).coerceIn(0, expMax),
                expMax = expMax,
                generation = if (kind.usesState) com.inmc.customitems.block.OreGen.load(section.getConfigurationSection("generation")) else null,
            )
        }
    }
}

/**
 * 부수면 나오는 것 하나. [chance] 퍼센트로 [min]~[max] 개. 아이템은 관리자가 손에 든 그대로(core [StoredItem] — 우리 아이템·MMOItems·바닐라).
 */
data class BlockDrop(val item: StoredItem, val min: Int = 1, val max: Int = 1, val chance: Double = 100.0) {

    fun save(section: ConfigurationSection) {
        item.save(section)
        if (min == max) {
            section.set("amount", min)
        } else {
            section.set("min", min)
            section.set("max", max)
        }
        if (chance < 100.0) section.set("chance", chance)
    }

    companion object {
        const val MAX_AMOUNT = 64 * 36

        fun load(section: ConfigurationSection): BlockDrop? {
            val item = StoredItem.load(section) ?: return null
            val amount = section.getInt("amount", 1)
            val min = section.getInt("min", amount).coerceIn(0, MAX_AMOUNT)
            val max = section.getInt("max", amount).coerceIn(min, MAX_AMOUNT)
            return BlockDrop(item, min, max, section.getDouble("chance", 100.0).coerceIn(0.0, 100.0))
        }
    }
}
