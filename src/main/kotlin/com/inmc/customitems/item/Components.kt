package com.inmc.customitems.item

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.EquipmentSlotGroup

/**
 * 1.21 의 아이템 부품 — 바닐라가 아이템마다 따로 들 수 있게 된 것들.
 *
 * **비어 있는 칸은 아무것도 건드리지 않는다.** 색·갑옷 장식은 플레이어가 염색대·대장장이 작업대로 바꿀 수
 * 있는데, 다시 그릴 때(자동 갱신) 정의에 없는 것을 지우면 그걸 날린다.
 *
 * @param maxStack 0 이면 바닐라. 여러 번 쓰는 소모품은 이 값과 무관하게 1 이다.
 * @param tooltipStyle 리소스팩의 툴팁 모양(`minecraft:…`/`이름공간:…`). 비우면 바닐라.
 * @param equipSlot 이 칸에 입을 수 있다([SLOTS]). 모자·등 장식에 — 능력치도 그 칸에서 돈다.
 * @param color `#RRGGBB`. 가죽 갑옷·물약 색.
 * @param trimPattern 갑옷 장식 무늬. [trimMaterial] 과 둘 다 있어야 붙는다.
 * @param skull 플레이어 머리의 텍스처(`textures` 의 base64 값).
 */
data class Components(
    val maxStack: Int = 0,
    val tooltipStyle: String = "",
    val hideTooltip: Boolean = false,
    val glider: Boolean = false,
    val fireResistant: Boolean = false,
    val equipSlot: String = "",
    val color: String = "",
    val trimPattern: String = "",
    val trimMaterial: String = "",
    val skull: String = "",
) {
    val isEmpty: Boolean get() = this == Components()

    fun save(section: ConfigurationSection) {
        if (maxStack > 0) section.set("max-stack", maxStack)
        if (tooltipStyle.isNotBlank()) section.set("tooltip-style", tooltipStyle)
        if (hideTooltip) section.set("hide-tooltip", true)
        if (glider) section.set("glider", true)
        if (fireResistant) section.set("fire-resistant", true)
        if (equipSlot.isNotBlank()) section.set("equip-slot", equipSlot)
        if (color.isNotBlank()) section.set("color", color)
        if (trimPattern.isNotBlank()) section.set("trim-pattern", trimPattern)
        if (trimMaterial.isNotBlank()) section.set("trim-material", trimMaterial)
        if (skull.isNotBlank()) section.set("skull", skull)
    }

    companion object {
        /** 입을 수 있는 칸. 빈 글은 "정의 안 함"(재질 그대로). */
        val SLOTS = listOf("", "head", "chest", "legs", "feet")

        const val MAX_STACK = 99

        fun load(section: ConfigurationSection?): Components {
            if (section == null) return Components()
            return Components(
                maxStack = section.getInt("max-stack", 0).coerceIn(0, MAX_STACK),
                tooltipStyle = section.getString("tooltip-style").orEmpty().trim(),
                hideTooltip = section.getBoolean("hide-tooltip", false),
                glider = section.getBoolean("glider", false),
                fireResistant = section.getBoolean("fire-resistant", false),
                equipSlot = section.getString("equip-slot").orEmpty().trim().lowercase().takeIf { it in SLOTS }.orEmpty(),
                color = section.getString("color").orEmpty().trim(),
                trimPattern = section.getString("trim-pattern").orEmpty().trim(),
                trimMaterial = section.getString("trim-material").orEmpty().trim(),
                skull = section.getString("skull").orEmpty().trim(),
            )
        }

        fun slotLabel(slot: String): String = when (slot) {
            "head" -> "머리"
            "chest" -> "가슴"
            "legs" -> "다리"
            "feet" -> "발"
            else -> "재질 그대로"
        }

        fun equipmentSlot(slot: String): EquipmentSlot? = when (slot) {
            "head" -> EquipmentSlot.HEAD
            "chest" -> EquipmentSlot.CHEST
            "legs" -> EquipmentSlot.LEGS
            "feet" -> EquipmentSlot.FEET
            else -> null
        }

        fun slotGroup(slot: String): EquipmentSlotGroup? = when (slot) {
            "head" -> EquipmentSlotGroup.HEAD
            "chest" -> EquipmentSlotGroup.CHEST
            "legs" -> EquipmentSlotGroup.LEGS
            "feet" -> EquipmentSlotGroup.FEET
            else -> null
        }

        /** `#RRGGBB` 또는 `RRGGBB` → RGB. 못 읽으면 null. */
        fun parseColor(raw: String): Int? {
            val hex = raw.trim().removePrefix("#")
            if (hex.length != 6) return null
            return hex.toIntOrNull(16)
        }
    }
}
