package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.Components
import com.inmc.customitems.item.Registries
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent

/** 1.21 부품 — 최대 겹침·툴팁·착용 칸·색·갑옷 장식·머리 텍스처·활공·불 저항. */
class ComponentMenu(custom: CustomItems, viewer: Player, id: String) :
    DetailMenu(custom, viewer, id, "<dark_gray>바닐라 부품 — $id</dark_gray>") {

    private fun change(reopen: Boolean = true, transform: (Components) -> Components) =
        mutate(reopen) { it.copy(components = transform(it.components)) }

    /** 글 칸 하나. 좌클릭 적기, 우클릭 비우기. [check] 가 오류 문구를 주면 받지 않는다. */
    private fun text(event: InventoryClickEvent, label: String, hint: List<String>, check: (String) -> String?, apply: (Components, String) -> Components) {
        if (event.isRightClick) {
            change { apply(it, "") }
            return
        }
        Editors.promptText(custom.prompts, viewer, label, hint, reopen = { open(viewer) }) { raw ->
            val value = raw.trim()
            check(value)?.let {
                viewer.sendMessage(Text.render("<red>$it</red>"))
                return@promptText
            }
            change(false) { apply(it, value) }
        }
    }

    private fun textIcon(material: Material, label: String, value: String, lines: List<String>) = Icon.of(
        material,
        "<yellow>$label: <white>" + value.ifBlank { "없음" }.take(40) + "</white></yellow>",
        lines + listOf("", "<yellow>▶ 좌클릭: 적기 · 우클릭: 비우기</yellow>"),
    )

    private fun toggle(material: Material, label: String, value: Boolean, hint: String) = Icon.of(
        if (value) material else Material.GRAY_DYE,
        "<yellow>$label: " + Icon.toggle(value) + "</yellow>",
        listOf("<gray>$hint</gray>", "", "<yellow>▶ 클릭: 전환</yellow>"),
    )

    override fun draw() {
        clear()
        val item = item() ?: return ItemTypeMenu(custom, viewer).open(viewer)
        val parts = item.components

        set(SLOT_MAX_STACK, Editors.intIcon(Material.CHEST, "<yellow>최대 겹침</yellow>", parts.maxStack, extra = listOf(
            "<gray>0 이면 재질 그대로. 1~${Components.MAX_STACK}.</gray>",
            "<gray>여러 번 쓰는 소모품은 이 값과 무관하게 1 입니다.</gray>",
        ))) { event ->
            if (Editors.isPrompt(event)) {
                Editors.promptInt(custom.prompts, viewer, "최대 겹침", 0, Components.MAX_STACK, reopen = { open(viewer) }) { v -> change(false) { it.copy(maxStack = v) } }
                return@set
            }
            change { it.copy(maxStack = (it.maxStack + Editors.step(event, 1)).coerceIn(0, Components.MAX_STACK)) }
        }
        set(SLOT_TOOLTIP, textIcon(Material.ITEM_FRAME, "툴팁 모양", parts.tooltipStyle, listOf(
            "<gray>리소스팩의 툴팁 배경 이름. 예: <white>inmc:legend</white></gray>",
            "<dark_gray>팩에 tooltip/<이름>_background 가 있어야 합니다.</dark_gray>",
        ))) { e -> text(e, "툴팁 모양", listOf("<gray>예: <white>inmc:legend</white></gray>"), { if (org.bukkit.NamespacedKey.fromString(it.lowercase()) == null) "이름 모양이 아닙니다: $it" else null }) { c, v -> c.copy(tooltipStyle = v.lowercase()) } }
        set(SLOT_HIDE_TOOLTIP, toggle(Material.BARRIER, "툴팁 숨기기", parts.hideTooltip, "마우스를 올려도 설명이 안 뜹니다(장식용).")) { change { it.copy(hideTooltip = !it.hideTooltip) } }
        set(SLOT_GLIDER, toggle(Material.ELYTRA, "활공", parts.glider, "입으면 겉날개처럼 활공합니다(가슴 칸에 입는 것).")) { change { it.copy(glider = !it.glider) } }
        set(SLOT_FIRE, toggle(Material.MAGMA_CREAM, "불 저항", parts.fireResistant, "떨어뜨려도 불·용암에 타지 않습니다.")) { change { it.copy(fireResistant = !it.fireResistant) } }

        set(SLOT_EQUIP, Icon.of(Material.LEATHER_HELMET, "<yellow>입는 칸: <white>" + Components.slotLabel(parts.equipSlot) + "</white></yellow>",
            Editors.optionList(Components.SLOTS, parts.equipSlot) { Components.slotLabel(it) } + listOf(
                "", "<gray>아무 아이템이나 그 칸에 입게 합니다(모자·등 장식).</gray>", "<gray>능력치도 그 칸에서 돕니다.</gray>",
            ) + Editors.cycleHint)) { event -> change { it.copy(equipSlot = Editors.cycle(event, Components.SLOTS, it.equipSlot)) } }
        set(SLOT_COLOR, textIcon(Material.RED_DYE, "색", parts.color, listOf("<gray>가죽 갑옷·물약 색. 예: <white>#FF8800</white></gray>"))) { e ->
            text(e, "색 (#RRGGBB)", listOf("<gray>예: <white>#FF8800</white></gray>"), { if (Components.parseColor(it) == null) "#RRGGBB 모양으로 적으세요: $it" else null }) { c, v -> c.copy(color = "#" + v.removePrefix("#").uppercase()) }
        }
        set(SLOT_TRIM_PATTERN, textIcon(Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, "장식 무늬", parts.trimPattern, listOf(
            "<gray>갑옷에만. 예: <white>sentry</white>, <white>silence</white></gray>", "<gray>재료와 둘 다 있어야 붙습니다.</gray>",
        ))) { e -> text(e, "장식 무늬", listOf("<gray>예: <white>sentry</white></gray>"), { if (Registries.trimPattern(it) == null) "그런 무늬가 없습니다: $it" else null }) { c, v -> c.copy(trimPattern = v.lowercase()) } }
        set(SLOT_TRIM_MATERIAL, textIcon(Material.AMETHYST_SHARD, "장식 재료", parts.trimMaterial, listOf(
            "<gray>예: <white>gold</white>, <white>amethyst</white>, <white>netherite</white></gray>",
        ))) { e -> text(e, "장식 재료", listOf("<gray>예: <white>gold</white></gray>"), { if (Registries.trimMaterial(it) == null) "그런 재료가 없습니다: $it" else null }) { c, v -> c.copy(trimMaterial = v.lowercase()) } }
        set(SLOT_SKULL, textIcon(Material.PLAYER_HEAD, "머리 텍스처", if (parts.skull.isBlank()) "" else parts.skull.take(12) + "…", listOf(
            "<gray>재질이 플레이어 머리일 때만. 텍스처 사이트의</gray>", "<gray><white>Value</white>(base64) 를 붙여 넣으세요.</gray>",
        ))) { e -> text(e, "머리 텍스처(base64)", listOf("<gray>eyJ0ZXh0dXJlcyI6… 로 시작하는 값</gray>"), { null }) { c, v -> c.copy(skull = v) } }
        backAndClose()
    }

    private companion object {
        const val SLOT_MAX_STACK = 10
        const val SLOT_TOOLTIP = 11
        const val SLOT_HIDE_TOOLTIP = 12
        const val SLOT_GLIDER = 13
        const val SLOT_FIRE = 14
        const val SLOT_EQUIP = 19
        const val SLOT_COLOR = 20
        const val SLOT_TRIM_PATTERN = 21
        const val SLOT_TRIM_MATERIAL = 22
        const val SLOT_SKULL = 23
    }
}
