package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.ElementDef
import com.inmc.customitems.item.Stat
import com.inmc.customitems.util.Ph
import kr.inmc.core.gui.ConfirmMenu
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.gui.ParticlePickMenu
import kr.inmc.core.gui.PotionPickMenu
import kr.inmc.core.util.Particles
import kr.inmc.core.util.PotionEffects
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * 속성 관리 — 속성을 만들면 그 속성의 능력치 둘(피해 · 저항%)이 능력치 화면의 "원소" 묶음에 생긴다(사용자 결정 2026-10-13).
 */
class ElementListMenu(custom: CustomItems, private val viewer: Player) :
    Menu(custom, 54, Text.renderFlat("<dark_gray>속성 관리</dark_gray>")) {

    private var page = 0

    override fun draw() {
        clear()
        val all = custom.elements.all()
        page = Paging.clamp(page, all.size)
        for ((slot, def) in Paging.slice(all, page).withIndex()) {
            set(slot, Icon.of(def.icon, def.color + def.name, buildList {
                add("<dark_gray>id " + def.id + " · 능력치 " + def.id + "-damage · " + def.id + "-resist</dark_gray>")
                addAll(effectLines(def))
                add("<gray>쓰는 아이템 <white>" + users(custom, def.id) + "</white>개</gray>")
                add("")
                add("<yellow>▶ 클릭: 설정</yellow>")
            })) { ElementEditMenu(custom, viewer, def.id).open(viewer) }
        }
        set(SLOT_INFO, Icon.of(Material.BOOK, "<aqua>속성이란</aqua>", listOf(
            "<gray>속성마다 능력치 둘이 생깁니다.</gray>",
            "<gray>  <white>피해</white> — 때릴 때 더하는 고정 피해</gray>",
            "<gray>  <white>저항</white> — 그 속성 피해 -% (<white>음수면 약점</white>, -100 이면 두 배)</gray>",
            "<gray>몹은 저항이 없습니다. 맞힐 때 효과는 피해가 들어갔을 때만.</gray>",
        )))
        set(SLOT_CREATE, Icon.of(Material.WRITABLE_BOOK, "<green>새 속성</green>", listOf(
            "<gray>id 는 영어 소문자·숫자만 됩니다(예: <white>earth</white>).</gray>",
            "", "<yellow>▶ 클릭: id 입력</yellow>",
        ))) { create() }
        set(Paging.SLOT_BACK, Icon.back()) { ItemTypeMenu(custom, viewer).open(viewer) }
        if (page > 0) set(Paging.SLOT_PREV, Icon.prevPage()) { page--; refresh() }
        if (page < Paging.pageCount(all.size) - 1) set(Paging.SLOT_NEXT, Icon.nextPage()) { page++; refresh() }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun create() {
        var created: String? = null
        Editors.promptText(
            custom.prompts, viewer, "새 속성 id",
            listOf("<gray>영어 소문자·숫자만. 이미 있는 능력치 이름과 겹치면 안 됩니다(attack · pve · skill …).</gray>", "<gray>보이는 이름·효과는 다음 화면에서 정합니다.</gray>"),
            reopen = { created?.let { ElementEditMenu(custom, viewer, it).open(viewer) } ?: open(viewer) },
        ) { raw ->
            val id = raw.trim().lowercase()
            when {
                !ElementDef.isValidId(id) -> custom.messages.send(viewer, "invalid-name", Ph.of().item(raw))
                custom.elements.get(id) != null -> custom.messages.send(viewer, "already-exists", Ph.of().item(id))
                else -> {
                    custom.elements.put(ElementDef(id, id))
                    created = id
                }
            }
        }
    }

    companion object {
        const val SLOT_INFO = 48
        const val SLOT_CREATE = 49
    }
}

/** 속성 하나의 설정 — 이름 · 색 · 아이콘 · 맞힐 때 효과(태우기 · 물약 · 입자) · 지우기. */
class ElementEditMenu(custom: CustomItems, private val viewer: Player, private val id: String) :
    Menu(custom, 36, Text.renderFlat("<dark_gray>속성 — $id</dark_gray>")) {

    override fun draw() {
        clear()
        val def = custom.elements.get(id) ?: run {
            ElementListMenu(custom, viewer).open(viewer)
            return
        }
        set(SLOT_NAME, Icon.of(Material.NAME_TAG, "<yellow>보이는 이름: <white>" + def.name + "</white></yellow>", listOf(
            "<gray>능력치 이름이 됩니다: <white>" + Stat.elementDamage(id).display + "</white> · <white>" + Stat.elementResist(id).display + "</white></gray>",
            "", "<yellow>▶ 클릭: 바꾸기</yellow>",
        ))) { prompt("보이는 이름", def.name, emptyList()) { raw -> def.copy(name = raw) } }
        set(SLOT_COLOR, Icon.of(Material.ORANGE_DYE, "<yellow>색: " + def.color + def.name + "</yellow>", listOf(
            "<gray>관리 화면에 쓰입니다(로어의 능력치 줄은 회색).</gray>",
            "<dark_gray>지금: " + literal(def.color) + "</dark_gray>",
            "", "<yellow>▶ 클릭: 색 고르기</yellow>",
        ))) {
            kr.inmc.core.gui.ColorPickMenu(custom, viewer, "속성 색", custom.prompts, current = def.color, back = { open(viewer) }) { hex ->
                if (hex != null) custom.elements.get(id)?.let { now -> custom.elements.put(now.copy(color = "<$hex>")) }
                open(viewer)
            }.show()
        }
        // 손에 든 것 대신 아이템 올리기(사용자 요청 2026-10-14).
        set(SLOT_ICON, Icon.of(def.icon, "<yellow>아이콘</yellow>", listOf(
            "<yellow>▶ 클릭: 아이템을 올려 그 재질로</yellow>",
        ))) {
            kr.inmc.core.gui.ItemInputMenu(custom, viewer, "속성 아이콘 — 아이템 올리기", hint = listOf("<gray>올린 아이템의 재질이 아이콘이 됩니다.</gray>"),
                submitLabel = "아이콘으로", back = { open(viewer) }) { stack ->
                custom.elements.get(id)?.let { now -> custom.elements.put(now.copy(icon = stack.type)) }
                open(viewer)
            }.show()
        }
        set(SLOT_BURN, Editors.intIcon(Material.BLAZE_POWDER, "<yellow>맞히면 태우기</yellow>", def.burn, unit = "틱", stepLabel = "20", extra = listOf(
            "<gray>20틱 = 1초. 0 이면 안 태웁니다.</gray>",
        ))) { event ->
            if (Editors.isPrompt(event)) {
                Editors.promptInt(custom.prompts, viewer, "태우는 시간(틱)", 0, ElementDef.MAX_TICKS, reopen = { open(viewer) }) { v -> custom.elements.put(def.copy(burn = v)) }
                return@set
            }
            custom.elements.put(def.copy(burn = (def.burn + Editors.step(event, 20)).coerceIn(0, ElementDef.MAX_TICKS)))
            refresh()
        }
        set(SLOT_POTION, Icon.of(Material.POTION, "<yellow>맞히면 물약 효과: <white>" + (if (def.potion.isBlank()) "없음" else PotionEffects.lang(def.potion)) + "</white></yellow>", listOf(
            "<gray>모든 효과를 한국어 이름으로 보여 주는 화면에서 고릅니다.</gray>",
            "", "<yellow>▶ 좌클릭: 고르기</yellow>", "<yellow>▶ 우클릭: 없애기</yellow>",
        ))) { event ->
            if (event.isRightClick) {
                custom.elements.put(def.copy(potion = ""))
                refresh()
                return@set
            }
            PotionPickMenu(custom, viewer, current = def.potion, back = { open(viewer) }) { type ->
                custom.elements.get(id)?.let { now -> custom.elements.put(now.copy(potion = PotionEffects.id(type), potionTicks = now.potionTicks.takeIf { it > 0 } ?: 40)) }
                open(viewer)
            }.show()
        }
        set(SLOT_POTION_TICKS, Editors.intIcon(Material.CLOCK, "<yellow>물약 길이</yellow>", def.potionTicks, unit = "틱", stepLabel = "20", extra = listOf(
            "<gray>20틱 = 1초.</gray>",
        ))) { event ->
            if (Editors.isPrompt(event)) {
                Editors.promptInt(custom.prompts, viewer, "물약 길이(틱)", 0, ElementDef.MAX_TICKS, reopen = { open(viewer) }) { v -> custom.elements.put(def.copy(potionTicks = v)) }
                return@set
            }
            custom.elements.put(def.copy(potionTicks = (def.potionTicks + Editors.step(event, 20)).coerceIn(0, ElementDef.MAX_TICKS)))
            refresh()
        }
        set(SLOT_POTION_LEVEL, Editors.intIcon(Material.GLOWSTONE_DUST, "<yellow>물약 세기</yellow>", def.potionLevel, extra = listOf(
            "<gray>1 = I, 2 = II …</gray>",
        ))) { event ->
            if (Editors.isPrompt(event)) {
                Editors.promptInt(custom.prompts, viewer, "물약 세기", 1, ElementDef.MAX_LEVEL, reopen = { open(viewer) }) { v -> custom.elements.put(def.copy(potionLevel = v)) }
                return@set
            }
            custom.elements.put(def.copy(potionLevel = (def.potionLevel + Editors.step(event, 1)).coerceIn(1, ElementDef.MAX_LEVEL)))
            refresh()
        }
        set(SLOT_PARTICLE, Icon.of(Particles.of(def.particle)?.let(Particles::icon) ?: Material.FIREWORK_STAR, "<yellow>맞히면 입자: <white>" + (if (def.particle.isBlank()) "없음" else Particles.name(def.particle)) + "</white></yellow>", listOf(
            "<gray>모든 입자를 한국어 이름으로 보여 주는 화면에서 고릅니다(우클릭으로 미리보기).</gray>",
            "", "<yellow>▶ 좌클릭: 고르기</yellow>", "<yellow>▶ 우클릭: 없애기</yellow>",
        ))) { event ->
            if (event.isRightClick) {
                custom.elements.put(def.copy(particle = ""))
                refresh()
                return@set
            }
            ParticlePickMenu(custom, custom.plugin, viewer, current = def.particle, back = { open(viewer) }) { particle ->
                custom.elements.get(id)?.let { now -> custom.elements.put(now.copy(particle = Particles.id(particle))) }
                open(viewer)
            }.show()
        }
        set(SLOT_DELETE, Icon.of(Material.LAVA_BUCKET, "<red>지우기</red>", listOf(
            "<gray>이 속성의 능력치(" + id + "-damage · " + id + "-resist)를</gray>",
            "<gray>아이템에서 <white>같이 지웁니다</white>.</gray>",
        ))) {
            val members = custom.items.all().filter { item -> item.stats.keys.any { it.element == id } }
            ConfirmMenu(
                owner = custom,
                question = "<red>속성 '" + def.name + "' 을(를) 지울까요?</red>",
                detail = listOf(
                    "<gray>이 속성 능력치를 가진 아이템 <white>" + members.size + "</white>개에서 그 능력치를 지웁니다.</gray>",
                    "<dark_gray>세트·강화표·수식어에 적힌 것은 다음에 읽을 때 빠집니다.</dark_gray>",
                ),
                onConfirm = {
                    for (item in members) custom.items.get(item.id)?.let { current ->
                        custom.items.put(current.copy(stats = current.stats.filterKeys { it.element != id }, spreads = current.spreads.filterKeys { it.element != id }))
                    }
                    custom.elements.remove(id)
                    ElementListMenu(custom, viewer).open(viewer)
                },
                onCancel = { open(viewer) },
            ).open(viewer)
        }
        set(SLOT_BACK, Icon.back()) { ElementListMenu(custom, viewer).open(viewer) }
    }

    /** [change] 가 null 이면 못 알아본 값 — 알리고 그대로 둔다. */
    private fun prompt(label: String, now: String, hints: List<String>, change: (String) -> ElementDef?) {
        Editors.promptText(
            custom.prompts, viewer, label,
            listOf("<gray>지금: <white>" + literal(now.ifBlank { "없음" }) + "</white></gray>") + hints,
            reopen = { open(viewer) },
        ) { raw ->
            if (raw.isBlank()) return@promptText
            val next = change(raw.trim())
            if (next == null) viewer.sendMessage(Text.render("<red>알아볼 수 없는 값입니다: " + literal(raw) + "</red>")) else custom.elements.put(next)
        }
    }

    companion object {
        const val SLOT_NAME = 10
        const val SLOT_COLOR = 11
        const val SLOT_ICON = 12
        const val SLOT_DELETE = 16
        const val SLOT_BURN = 19
        const val SLOT_POTION = 20
        const val SLOT_POTION_TICKS = 21
        const val SLOT_POTION_LEVEL = 22
        const val SLOT_PARTICLE = 24
        const val SLOT_BACK = 31
    }
}

/** 맞힐 때 효과를 한 줄씩. 없으면 "효과 없음". */
private fun effectLines(def: ElementDef): List<String> = buildList {
    if (def.burn > 0) add("<gray>맞히면 <white>" + def.burn / 20.0 + "초</white> 태움</gray>")
    if (def.potion.isNotBlank()) add("<gray>맞히면 <white>" + PotionEffects.lang(def.potion) + " " + def.potionLevel + "</white> · <white>" + def.potionTicks / 20.0 + "초</white></gray>")
    if (def.particle.isNotBlank()) add("<gray>입자 <white>" + Particles.name(def.particle) + "</white></gray>")
    if (isEmpty()) add("<dark_gray>맞힐 때 효과 없음 — 피해만</dark_gray>")
}

/** 그 속성의 능력치(피해·저항)를 가진 아이템 수. */
private fun users(custom: CustomItems, element: String): Int =
    custom.items.all().count { item: CustomItem -> item.stats.keys.any { it.element == element } }
