package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import com.inmc.customitems.ability.Ability
import com.inmc.customitems.ability.EffectType
import com.inmc.customitems.ability.Param
import com.inmc.customitems.ability.Target
import com.inmc.customitems.ability.Trigger
import com.inmc.customitems.item.CustomItem
import kr.inmc.core.gui.ConfirmMenu
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent

/**
 * 아이템에 붙은 기능 목록.
 *
 * 새 기능은 **발동 조건을 먼저 고르고 효과를 고른다** — 반대 순서면 "우클릭에 쓸 수 있는
 * 효과가 뭐지"를 먼저 묻게 되는데, 실제로는 모든 효과가 모든 조건에 붙는다.
 */
class AbilityListMenu(
    custom: CustomItems,
    private val viewer: Player,
    private val id: String,
) : Menu(custom, SIZE, Text.renderFlat("<dark_gray>기능 — " + id + "</dark_gray>")) {

    private fun item(): CustomItem? = custom.items.get(id)

    override fun draw() {
        clear()
        val item = item() ?: run {
            ItemTypeMenu(custom, viewer).open(viewer)
            return
        }

        for ((index, ability) in item.abilities.withIndex()) {
            if (index >= Paging.PER_PAGE) break
            set(index, tile(index, ability)) { event ->
                if (event.isRightClick) confirmDelete(index, ability) else {
                    AbilityEditMenu(custom, viewer, id, index).open(viewer)
                }
            }
        }

        set(SLOT_ADD, addIcon()) { TriggerPickMenu(custom, viewer, id).open(viewer) }
        set(SLOT_INFO, infoIcon(item))
        set(Paging.SLOT_BACK, Icon.back()) { ItemEditMenu(custom, viewer, id).open(viewer) }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun tile(index: Int, ability: Ability) = Icon.of(
        ability.effect.icon,
        "<yellow>" + ability.trigger.display + " → " + ability.effect.display + "</yellow>",
        buildList {
            add("<gray>" + ability.trigger.hint + "</gray>")
            add("")
            for (param in ability.effect.params) {
                add("<dark_gray>" + param.display + ": <white>" + shown(ability, param) + "</white></dark_gray>")
            }
            add("")
            if (ability.chance < 100.0) add("<gray>확률 <white>" + ability.chance + "%</white></gray>")
            if (ability.cooldownSeconds > 0.0) {
                add("<gray>쿨다운 <white>" + ability.cooldownSeconds + "초</white></gray>")
            }
            if (ability.trigger == Trigger.PASSIVE) {
                add("<gray>주기 <white>" + ability.intervalSeconds + "초</white></gray>")
            }
            add("")
            add("<yellow>▶ 좌클릭: 설정</yellow>")
            add("<red>▶ 우클릭: 삭제</red>")
            add("<dark_gray>#" + index + "</dark_gray>")
        },
    )

    private fun shown(ability: Ability, param: Param): String {
        val raw = ability.value(param.key)
        return when (param.kind) {
            Param.Kind.TARGET -> Target.of(raw).display
            Param.Kind.TEXT -> raw.ifBlank { "(비어 있음)" }
            else -> raw
        }
    }

    private fun addIcon() = Icon.of(
        Material.WRITABLE_BOOK,
        "<green>기능 추가</green>",
        listOf("<gray>발동 조건을 먼저 고릅니다.</gray>"),
    )

    private fun infoIcon(item: CustomItem) = Icon.of(
        Material.BOOK,
        "<aqua>기능이란</aqua>",
        listOf(
            "<gray>조건이 맞으면 효과가 터집니다.</gray>",
            "<gray>같은 조건에 여러 개를 붙일 수 있고,</gray>",
            "<gray>목록 순서대로 터집니다.</gray>",
            "",
            "<gray>확률과 쿨다운은 <white>모든 기능</white>이 갖습니다.</gray>",
            "<gray>여기 없는 것은 <white>명령어</white> 효과로 하세요.</gray>",
            "",
            "<dark_gray>붙은 기능 " + item.abilities.size + "개</dark_gray>",
        ),
    )

    private fun confirmDelete(index: Int, ability: Ability) {
        ConfirmMenu(
            owner = custom,
            question = "<red>'" + ability.trigger.display + " → " + ability.effect.display + "' 를 지울까요?</red>",
            onConfirm = {
                mutate { it.copy(abilities = it.abilities.filterIndexed { i, _ -> i != index }) }
                open(viewer)
            },
            onCancel = { open(viewer) },
        ).open(viewer)
    }

    private fun mutate(change: (CustomItem) -> CustomItem) {
        val current = item() ?: return
        custom.items.put(change(current))
    }

    companion object {
        const val SIZE = 54
        const val SLOT_ADD = 48
        const val SLOT_INFO = 49
    }
}

/** 새 기능의 발동 조건을 고른다. */
class TriggerPickMenu(
    custom: CustomItems,
    private val viewer: Player,
    private val id: String,
) : Menu(custom, SIZE, Text.renderFlat("<dark_gray>언제 터뜨릴까요?</dark_gray>")) {

    override fun draw() {
        clear()
        fillEmpty(Icon.EDGE)

        for ((index, trigger) in Trigger.entries.withIndex()) {
            set(
                SLOTS[index],
                Icon.of(
                    trigger.icon,
                    "<yellow>" + trigger.display + "</yellow>",
                    listOf("<gray>" + trigger.hint + "</gray>", "", "<yellow>▶ 클릭: 효과 고르기</yellow>"),
                ),
            ) { EffectPickMenu(custom, viewer, id, trigger).open(viewer) }
        }

        set(Paging.SLOT_BACK, Icon.back()) { AbilityListMenu(custom, viewer, id).open(viewer) }
    }

    private companion object {
        const val SIZE = 54
        val SLOTS = listOf(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34)
    }
}

/** 새 기능의 효과를 고른다. 고르는 즉시 붙고 설정 화면이 열린다. */
class EffectPickMenu(
    custom: CustomItems,
    private val viewer: Player,
    private val id: String,
    private val trigger: Trigger,
) : Menu(custom, SIZE, Text.renderFlat("<dark_gray>" + trigger.display + " — 무엇을?</dark_gray>")) {

    override fun draw() {
        clear()

        for ((index, effect) in EffectType.entries.withIndex()) {
            if (index >= Paging.PER_PAGE) break
            set(
                index,
                Icon.of(
                    effect.icon,
                    "<yellow>" + effect.display + "</yellow>",
                    effect.params.map { "<dark_gray>" + it.display + "</dark_gray>" } +
                        listOf("", "<yellow>▶ 클릭: 이 효과로 만들기</yellow>"),
                ),
            ) { add(effect) }
        }

        set(Paging.SLOT_BACK, Icon.back()) { TriggerPickMenu(custom, viewer, id).open(viewer) }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun add(effect: EffectType) {
        val item = custom.items.get(id) ?: return
        val next = item.abilities + Ability.of(trigger, effect)
        custom.items.put(item.copy(abilities = next))
        AbilityEditMenu(custom, viewer, id, next.size - 1).open(viewer)
    }

    private companion object {
        const val SIZE = 54
    }
}

/**
 * 기능 하나의 설정.
 *
 * **효과가 요구하는 값만 보여준다** ([EffectType.params]). 모든 효과의 모든 값을 한 화면에
 * 늘어놓으면 관리자가 "번개에 지속 시간을 넣었는데 왜 안 먹지" 를 묻게 된다.
 *
 * **정의가 아니라 번호를 들고 있다.** 목록에서 앞의 기능이 지워지면 번호가 밀리는데, 그때는
 * 정의를 다시 찾으면서 자연히 드러난다 — 옛 객체를 붙들고 있으면 엉뚱한 기능을 고친다.
 */
class AbilityEditMenu(
    custom: CustomItems,
    private val viewer: Player,
    private val id: String,
    private val index: Int,
) : Menu(custom, SIZE, Text.renderFlat("<dark_gray>기능 설정</dark_gray>")) {

    private fun item(): CustomItem? = custom.items.get(id)

    private fun ability(): Ability? = item()?.abilities?.getOrNull(index)

    override fun draw() {
        clear()
        val ability = ability() ?: run {
            AbilityListMenu(custom, viewer, id).open(viewer)
            return
        }
        fillEmpty(Icon.EDGE)

        // 발동 15개·효과 25개 — 좌/우클릭으로 돌리기엔 많아 고르는 화면으로(2026-10-08, 종류 칸과 같은 손짓).
        set(
            SLOT_TRIGGER,
            Icon.of(
                ability.trigger.icon,
                "<yellow>발동: <white>" + ability.trigger.display + "</white></yellow>",
                listOf("<gray>" + ability.trigger.hint + "</gray>") + Editors.pickHint,
            ),
        ) {
            ChoiceMenu(
                custom, viewer, "발동 조건 고르기",
                options = { Trigger.entries.map { it.name to Icon.of(it.icon, "<yellow>" + it.display + "</yellow>", listOf("<gray>" + it.hint + "</gray>")) } },
                selected = { setOf(ability.trigger.name) },
                back = { open(viewer) },
            ) { picked -> mutate(reopen = false) { it.copy(trigger = Trigger.valueOf(picked)) } }.open(viewer)
        }

        set(
            SLOT_EFFECT,
            Icon.of(
                ability.effect.icon,
                "<yellow>효과: <white>" + ability.effect.display + "</white></yellow>",
                listOf("<red>바꾸면 설정값이 초기화됩니다.</red>") + Editors.pickHint,
            ),
        ) {
            ChoiceMenu(
                custom, viewer, "효과 고르기",
                options = {
                    EffectType.entries.map { effect ->
                        effect.name to Icon.of(effect.icon, "<yellow>" + effect.display + "</yellow>", effect.params.map { "<dark_gray>" + it.display + "</dark_gray>" })
                    }
                },
                selected = { setOf(ability.effect.name) },
                back = { open(viewer) },
            ) { picked ->
                val next = EffectType.valueOf(picked)
                // 값의 뜻이 효과마다 달라 그대로 옮길 수 없다. 새 효과의 기본값으로 시작한다.
                mutate(reopen = false) { if (next == it.effect) it else it.copy(effect = next, values = next.defaults()) }
            }.open(viewer)
        }

        set(
            SLOT_CHANCE,
            Editors.numberIcon(
                Material.SUNFLOWER, "<yellow>발동 확률</yellow>", ability.chance, unit = "%",
                extra = listOf("<gray>100 이면 항상 터집니다.</gray>"),
            ),
        ) { event ->
            nudge(event, "발동 확률", ability.chance, 0.0, 100.0, 5.0) { a, v -> a.copy(chance = v) }
        }

        set(
            SLOT_COOLDOWN,
            Editors.numberIcon(
                Material.CLOCK, "<yellow>쿨다운</yellow>", ability.cooldownSeconds, unit = "초",
                extra = listOf(
                    "<gray>0 이면 제한 없습니다.</gray>",
                    "<gray>우클릭 기능은 두는 편이 좋습니다 —</gray>",
                    "<gray>없으면 연타로 무너집니다.</gray>",
                ),
            ),
        ) { event ->
            nudge(event, "쿨다운", ability.cooldownSeconds, 0.0, 3600.0, 1.0) { a, v ->
                a.copy(cooldownSeconds = v)
            }
        }

        if (ability.trigger == Trigger.PASSIVE) {
            set(
                SLOT_INTERVAL,
                Editors.numberIcon(
                    Material.REPEATER, "<yellow>발동 주기</yellow>", ability.intervalSeconds, unit = "초",
                    extra = listOf("<gray>들거나 착용한 동안 이 간격으로 터집니다.</gray>"),
                ),
            ) { event ->
                nudge(event, "발동 주기", ability.intervalSeconds, 0.5, 3600.0, 1.0) { a, v ->
                    a.copy(intervalSeconds = v)
                }
            }
        }

        // 효과가 요구하는 값들.
        for ((slot, param) in PARAM_SLOTS.zip(ability.effect.params)) {
            set(slot, paramIcon(ability, param)) { event -> editParam(event, ability, param) }
        }

        set(Paging.SLOT_BACK, Icon.back()) { AbilityListMenu(custom, viewer, id).open(viewer) }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun paramIcon(ability: Ability, param: Param): org.bukkit.inventory.ItemStack {
        val raw = ability.value(param.key)
        return when (param.kind) {
            Param.Kind.TARGET -> Icon.of(
                Material.TARGET,
                "<yellow>" + param.display + ": <white>" + Target.of(raw).display + "</white></yellow>",
                Editors.optionList(Target.entries.toList(), Target.of(raw)) { it.display } +
                    Editors.cycleHint,
            )

            Param.Kind.TEXT -> Icon.of(
                Material.NAME_TAG,
                "<yellow>" + param.display + "</yellow>",
                listOf(
                    if (raw.isBlank()) "<red>비어 있음</red>" else "<white>" + raw + "</white>",
                    "",
                    "<yellow>▶ 클릭: 입력</yellow>",
                ),
            )

            Param.Kind.POTION, Param.Kind.SOUND, Param.Kind.PARTICLE -> Icon.of(
                iconFor(param.kind),
                "<yellow>" + param.display + ": <white>" + raw + "</white></yellow>",
                listOf(
                    "<gray>" + hintFor(param.kind) + "</gray>",
                    "",
                    "<yellow>▶ 클릭: 입력</yellow>",
                ),
            )

            Param.Kind.INT -> Editors.intIcon(
                Material.PAPER, "<yellow>" + param.display + "</yellow>",
                raw.toDoubleOrNull()?.toInt() ?: 0,
            )

            Param.Kind.NUMBER -> Editors.numberIcon(
                Material.PAPER, "<yellow>" + param.display + "</yellow>",
                raw.toDoubleOrNull() ?: 0.0,
            )
        }
    }

    private fun iconFor(kind: Param.Kind): Material = when (kind) {
        Param.Kind.POTION -> Material.POTION
        Param.Kind.SOUND -> Material.NOTE_BLOCK
        else -> Material.FIREWORK_STAR
    }

    private fun hintFor(kind: Param.Kind): String = when (kind) {
        Param.Kind.POTION -> "예: SPEED, REGENERATION, STRENGTH"
        Param.Kind.SOUND -> "예: ENTITY_GENERIC_EXPLODE, BLOCK_ANVIL_LAND"
        else -> "예: FLAME, HEART, CRIT"
    }

    private fun editParam(event: InventoryClickEvent, ability: Ability, param: Param) {
        when (param.kind) {
            Param.Kind.TARGET -> {
                val next = Editors.cycle(event, Target.entries.toList(), ability.target(param.key))
                setValue(param.key, next.id)
            }

            Param.Kind.TEXT, Param.Kind.POTION, Param.Kind.SOUND, Param.Kind.PARTICLE ->
                Editors.promptText(
                    custom.prompts, viewer, param.display,
                    listOf("<gray>" + hintFor(param.kind) + "</gray>"),
                    reopen = { open(viewer) },
                ) { raw -> setValue(param.key, raw.trim(), reopen = false) }

            Param.Kind.INT -> {
                val current = ability.int(param.key)
                if (Editors.isPrompt(event)) {
                    Editors.promptInt(
                        custom.prompts, viewer, param.display,
                        param.min.toInt(), param.max.toInt(), { open(viewer) },
                    ) { value -> setValue(param.key, value.toString(), reopen = false) }
                    return
                }
                val next = (current + Editors.step(event, 1)).coerceIn(param.min.toInt(), param.max.toInt())
                setValue(param.key, next.toString())
            }

            Param.Kind.NUMBER -> {
                val current = ability.number(param.key)
                if (Editors.isPrompt(event)) {
                    Editors.promptDouble(
                        custom.prompts, viewer, param.display, param.min, param.max, { open(viewer) },
                    ) { value -> setValue(param.key, trim(value), reopen = false) }
                    return
                }
                val next = (current + Editors.step(event, 1.0)).coerceIn(param.min, param.max)
                setValue(param.key, trim(next))
            }
        }
    }

    private fun trim(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private fun setValue(key: String, value: String, reopen: Boolean = true) {
        mutate(reopen) { it.copy(values = it.values + (key to value)) }
    }

    private fun nudge(
        event: InventoryClickEvent,
        label: String,
        current: Double,
        min: Double,
        max: Double,
        step: Double,
        apply: (Ability, Double) -> Ability,
    ) {
        if (Editors.isPrompt(event)) {
            Editors.promptDouble(custom.prompts, viewer, label, min, max, { open(viewer) }) { value ->
                mutate(false) { apply(it, value) }
            }
            return
        }
        mutate { apply(it, (current + Editors.step(event, step)).coerceIn(min, max)) }
    }

    private fun mutate(reopen: Boolean = true, change: (Ability) -> Ability) {
        val item = item() ?: return
        val current = item.abilities.getOrNull(index) ?: return
        val next = item.abilities.toMutableList()
        next[index] = change(current)
        custom.items.put(item.copy(abilities = next))
        if (reopen) refresh() else open(viewer)
    }

    companion object {
        const val SIZE = 54

        const val SLOT_TRIGGER = 10
        const val SLOT_EFFECT = 11
        const val SLOT_CHANCE = 13
        const val SLOT_COOLDOWN = 14
        const val SLOT_INTERVAL = 15

        /** 효과가 요구하는 값들이 놓이는 자리. 가장 많은 효과가 넷을 요구한다. */
        val PARAM_SLOTS = listOf(28, 29, 30, 31, 32, 33)
    }
}
