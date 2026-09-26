package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import kr.inmc.core.gui.ConfirmMenu
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.integration.ItemRoles
import kr.inmc.core.item.ItemRef
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * 한 아이템의 연동 역할 — 다른 플러그인(인벤키퍼·랜덤박스·낚시·화폐 …)이 내놓은 역할을 전부 보여 주고 붙이고 뗀다.
 * 화면 모양은 역할이 내놓은 칸([ItemRoles.Field])으로 그린다 — 플러그인마다 화면을 따로 만들지 않는다.
 */
class RoleListMenu(custom: CustomItems, private val viewer: Player, private val id: String) :
    Menu(custom, 54, Text.renderFlat("<dark_gray>연동 역할 — $id</dark_gray>")) {

    private var page = 0

    override fun draw() {
        clear()
        val item = custom.items.get(id) ?: run {
            ItemTypeMenu(custom, viewer).open(viewer)
            return
        }
        val roles = ItemRoles.roles().sortedWith(compareBy({ it.key !in item.roles }, { it.owner }, { it.label }))
        page = Paging.clamp(page, roles.size)
        for ((slot, role) in Paging.slice(roles, page).withIndex()) {
            val values = item.roles[role.key]
            set(slot, Icon.of(if (values != null) role.icon else Material.GRAY_DYE, (if (values != null) "<green>" else "<gray>") + role.owner + " · " + role.label, buildList {
                addAll(role.description.map { "<gray>$it</gray>" })
                if (values != null) {
                    add("")
                    for (field in role.fields) if (field.visible(values)) add("<dark_gray>" + field.label + ": <white>" + shown(field, values[field.key] ?: field.default) + "</white></dark_gray>")
                }
                add("")
                add(if (values == null) "<yellow>▶ 클릭: 이 역할 붙이기</yellow>" else "<yellow>▶ 클릭: 설정</yellow>")
                if (values != null) add("<red>▶ Shift+우클릭: 떼기</red>")
            })) { event ->
                if (values != null && event.isShiftClick && event.isRightClick) {
                    ConfirmMenu(custom, "<red>'" + role.label + "' 역할을 뗄까요?</red>", listOf("<gray>" + role.owner + " 에서 이 아이템을 더는 쓰지 않습니다.</gray>"),
                        onConfirm = {
                            ItemRoles.assign(ItemRef.Namespaced(com.inmc.customitems.item.ItemBuilder.NAMESPACE, id), role.key, null)
                            open(viewer)
                        }, onCancel = { open(viewer) }).open(viewer)
                    return@set
                }
                if (values == null) ItemRoles.assign(ItemRef.Namespaced(com.inmc.customitems.item.ItemBuilder.NAMESPACE, id), role.key, role.defaults())
                RoleEditMenu(custom, viewer, id, role.key).open(viewer)
            }
        }
        if (roles.isEmpty()) set(22, Icon.of(Material.BARRIER, "<gray>역할을 내놓은 플러그인이 없습니다</gray>", listOf(
            "<gray>인벤키퍼·랜덤박스·낚시·인첸트·화폐 … 가 켜져 있으면 여기 보입니다.</gray>",
        )))
        set(Paging.SLOT_BACK, Icon.back()) { ItemEditMenu(custom, viewer, id).open(viewer) }
        if (page > 0) set(Paging.SLOT_PREV, Icon.prevPage()) { page--; refresh() }
        if (page < Paging.pageCount(roles.size) - 1) set(Paging.SLOT_NEXT, Icon.nextPage()) { page++; refresh() }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }
}

/** 역할 하나의 설정 칸. 숫자는 좌/우클릭(Shift 10배)·숫자키로 입력, 켜고 끄기, 고르기는 좌/우로 돌리기, 글은 채팅으로. */
class RoleEditMenu(custom: CustomItems, private val viewer: Player, private val id: String, private val roleKey: String) :
    Menu(custom, 54, Text.renderFlat("<dark_gray>역할 — " + (ItemRoles.role(roleKey)?.label ?: roleKey) + "</dark_gray>")) {

    private val ref get() = ItemRef.Namespaced(com.inmc.customitems.item.ItemBuilder.NAMESPACE, id)

    private fun values(): Map<String, String>? = custom.items.get(id)?.roles?.get(roleKey)

    private fun put(key: String, value: String) {
        val now = values() ?: return
        ItemRoles.assign(ref, roleKey, now + (key to value))
    }

    override fun draw() {
        clear()
        val role = ItemRoles.role(roleKey)
        val values = values()
        if (role == null || values == null) {
            RoleListMenu(custom, viewer, id).open(viewer)
            return
        }
        set(4, Icon.of(role.icon, "<aqua>" + role.owner + " · " + role.label + "</aqua>", role.description.map { "<gray>$it</gray>" } + listOf("", "<dark_gray>아이템 $id</dark_gray>")))
        val shown = role.fields.filter { it.visible(values) }
        for ((index, field) in shown.withIndex()) {
            if (index >= FIELD_SLOTS.size) break
            val raw = values[field.key] ?: field.default
            val slot = FIELD_SLOTS[index]
            when (field) {
                is ItemRoles.Number -> {
                    val value = raw.toDoubleOrNull() ?: field.default.toDoubleOrNull() ?: field.min
                    val label = "<yellow>" + field.label + "</yellow>"
                    val icon = if (field.integer) Editors.intIcon(Material.CLOCK, label, value.toInt(), stepLabel = trim(field.step))
                        else Editors.numberIcon(Material.CLOCK, label, value, stepLabel = trim(field.step))
                    set(slot, icon) { event ->
                        if (Editors.isPrompt(event)) {
                            Editors.promptDouble(custom.prompts, viewer, field.label, field.min, field.max, reopen = { open(viewer) }) { typed ->
                                put(field.key, number(field, typed))
                            }
                            return@set
                        }
                        put(field.key, number(field, (value + Editors.step(event, field.step)).coerceIn(field.min, field.max)))
                        refresh()
                    }
                }
                is ItemRoles.Toggle -> {
                    val on = raw.toBoolean()
                    set(slot, Icon.of(if (on) Material.LIME_DYE else Material.GRAY_DYE, "<yellow>" + field.label + ": " + Icon.toggle(on) + "</yellow>", listOf("", "<yellow>▶ 클릭: 전환</yellow>"))) {
                        put(field.key, (!on).toString())
                        refresh()
                    }
                }
                is ItemRoles.Choice -> {
                    val options = field.options()
                    val current = options.firstOrNull { it.first == raw }
                    set(slot, Icon.of(Material.BOOK, "<yellow>" + field.label + ": <white>" + (current?.second ?: raw.ifBlank { "없음" }) + "</white></yellow>",
                        (if (options.isEmpty()) listOf("<gray>고를 것이 없습니다.</gray>") else Editors.optionList(options, current ?: options.first()) { it.second }) + Editors.cycleHint)) { event ->
                        if (options.isEmpty()) return@set
                        put(field.key, Editors.cycle(event, options, current ?: options.first()).first)
                        refresh()
                    }
                }
                is ItemRoles.Text -> {
                    set(slot, Icon.of(Material.NAME_TAG, "<yellow>" + field.label + ": <white>" + raw.ifBlank { "없음" } + "</white></yellow>", listOf("", "<yellow>▶ 클릭: 입력</yellow>"))) {
                        Editors.promptText(custom.prompts, viewer, field.label, listOf("<gray>지금: <white>" + raw + "</white></gray>"), reopen = { open(viewer) }) { typed ->
                            put(field.key, typed.trim())
                        }
                    }
                }
            }
        }
        set(SLOT_REMOVE, Icon.of(Material.LAVA_BUCKET, "<red>이 역할 떼기</red>", listOf("<gray>" + role.owner + " 에서 이 아이템을 더는 쓰지 않습니다.</gray>"))) {
            ConfirmMenu(custom, "<red>'" + role.label + "' 역할을 뗄까요?</red>", onConfirm = {
                ItemRoles.assign(ref, roleKey, null)
                RoleListMenu(custom, viewer, id).open(viewer)
            }, onCancel = { open(viewer) }).open(viewer)
        }
        set(Paging.SLOT_BACK, Icon.back()) { RoleListMenu(custom, viewer, id).open(viewer) }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun number(field: ItemRoles.Number, value: Double): String =
        if (field.integer) value.toLong().toString() else trim(kotlin.math.round(value * 1000.0) / 1000.0)

    companion object {
        /** 칸 자리 — 세 줄에 일곱씩. 역할이 이보다 많은 칸을 내놓으면 앞의 것만 보인다(지금 가장 많은 것도 한참 적다). */
        val FIELD_SLOTS = (10..16) + (19..25) + (28..34)
        const val SLOT_REMOVE = 49
    }
}

private fun trim(value: Double): String = if (value == Math.floor(value)) value.toLong().toString() else value.toString()

/** 목록에 보일 값 — 고르기는 보이는 이름으로. */
private fun shown(field: ItemRoles.Field, raw: String): String = when (field) {
    is ItemRoles.Choice -> field.options().firstOrNull { it.first == raw }?.second ?: raw
    is ItemRoles.Toggle -> if (raw.toBoolean()) "켜짐" else "꺼짐"
    else -> raw.ifBlank { "없음" }
}
