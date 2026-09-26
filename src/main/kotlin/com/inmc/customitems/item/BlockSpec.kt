package com.inmc.customitems.item

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
 * @param drop 부수면 이 아이템이 나온다. 끄면 아무것도 안 나온다(ItemsAdder `drop_when_mined`).
 */
data class BlockSpec(val kind: BlockKind, val state: String = "", val drop: Boolean = true) {

    fun save(section: ConfigurationSection) {
        section.set("kind", kind.id)
        if (state.isNotBlank()) section.set("state", state)
        if (!drop) section.set("drop", false)
    }

    companion object {
        fun load(section: ConfigurationSection?): BlockSpec? {
            val kind = BlockKind.of(section?.getString("kind")) ?: return null
            return BlockSpec(kind, if (kind.usesState) section!!.getString("state").orEmpty().trim() else "", section!!.getBoolean("drop", true))
        }
    }
}
