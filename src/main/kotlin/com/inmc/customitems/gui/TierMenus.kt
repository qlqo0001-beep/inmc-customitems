package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.Tier
import com.inmc.customitems.item.TierDef
import com.inmc.customitems.util.Ph
import kr.inmc.core.gui.ConfirmMenu
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.store.DefinitionKey
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * 등급 관리 — 아래 등급부터 위 등급 순서(사용자 결정 2026-10-13: 기본 여섯 개에 관리자가 더하고 고치고 지운다).
 * 순서가 곧 높낮이다 — 유물은 가장 높은 등급 하나만 효과가 난다([com.inmc.customitems.item.Carried]).
 */
class TierListMenu(custom: CustomItems, private val viewer: Player) :
    Menu(custom, 54, Text.renderFlat("<dark_gray>등급 관리</dark_gray>")) {

    private var page = 0

    override fun draw() {
        clear()
        val all = custom.tiers.all()
        val counts = custom.items.all().groupingBy { it.tier.id }.eachCount()
        page = Paging.clamp(page, all.size)
        for ((slot, def) in Paging.slice(all, page).withIndex()) {
            set(slot, Icon.of(Material.NETHER_STAR, def.color + def.name, listOf(
                "<dark_gray>id " + def.id + " · " + (all.indexOf(def) + 1) + "번째(아래부터)</dark_gray>",
                def.badge(),
                "<gray>아이템 <white>" + (counts[def.id] ?: 0) + "</white>개</gray>",
                "", "<yellow>▶ 클릭: 설정</yellow>",
            ))) { TierEditMenu(custom, viewer, def.id).open(viewer) }
        }
        val gone = counts.keys.filter { it != Tier.NONE.id && custom.tiers.get(it) == null }
        set(SLOT_INFO, Icon.of(Material.BOOK, "<aqua>등급이란</aqua>", buildList {
            add("<gray>아이템 이름 색과 로어 맨 아래 배지.</gray>")
            add("<gray>위 목록은 <white>낮은 등급 → 높은 등급</white> 순서입니다.</gray>")
            add("<gray>등급 없음(배지 안 그림)은 언제나 고를 수 있습니다 — 아이템 <white>" + (counts[Tier.NONE.id] ?: 0) + "</white>개</gray>")
            if (gone.isNotEmpty()) {
                add("")
                add("<red>지운 등급을 쥔 아이템</red> <dark_gray>(같은 id 로 다시 만들면 이어집니다)</dark_gray>")
                for (id in gone.take(6)) add("<dark_gray>  ▪ " + id + " — " + counts.getValue(id) + "개</dark_gray>")
            }
        }))
        set(SLOT_ORDER, Icon.of(Material.HOPPER, "<yellow>순서 정하기</yellow>", listOf(
            "<gray>등급을 눌러 집고, 놓을 자리의 등급을 누르면 그 자리로 옮깁니다.</gray>",
            "", "<yellow>▶ 클릭</yellow>",
        ))) { TierOrderMenu(custom, viewer) { open(viewer) }.open(viewer) }
        set(SLOT_CREATE, Icon.of(Material.WRITABLE_BOOK, "<green>새 등급</green>", listOf(
            "<gray>맨 위(가장 높은 등급)에 더합니다. 자리는 '순서 정하기'에서 옮깁니다.</gray>",
            "<gray>" + DefinitionKey.HINT + "</gray>",
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
            custom.prompts, viewer, "새 등급 id",
            listOf("<gray>" + DefinitionKey.HINT + ".</gray>", "<gray>보이는 이름·색은 다음 화면에서 정합니다.</gray>"),
            reopen = { created?.let { TierEditMenu(custom, viewer, it).open(viewer) } ?: open(viewer) },
        ) { raw ->
            val id = raw.trim().lowercase()
            when {
                id == Tier.NONE.id || !DefinitionKey.isValid(id) -> custom.messages.send(viewer, "invalid-name", Ph.of().item(raw))
                custom.tiers.get(id) != null -> custom.messages.send(viewer, "already-exists", Ph.of().item(id))
                else -> {
                    custom.tiers.put(TierDef(id, id))
                    created = id
                }
            }
        }
    }

    companion object {
        const val SLOT_INFO = 48
        const val SLOT_CREATE = 49
        const val SLOT_ORDER = 50
    }
}

/**
 * 등급 순서 정하기 — 등급을 눌러 **집고**, 놓을 자리의 등급을 누르면 그 자리로 옮긴다(나머지는 한 칸씩 밀린다). 사용자 요청 2026-10-14:
 * 한 칸씩 위/아래로 누르는 것은 열 개를 다시 세우려면 수십 번이라 불편하다.
 * 칸 번호가 곧 순서다(왼쪽 위가 가장 낮은 등급).
 */
class TierOrderMenu(custom: CustomItems, private val viewer: Player, private val back: () -> Unit) :
    Menu(custom, 54, Text.renderFlat("<dark_gray>등급 순서 — 집고 놓기</dark_gray>")) {

    /** 집은 등급 id. 없으면 null. */
    private var held: String? = null

    override fun draw() {
        clear()
        val all = custom.tiers.all()
        for ((index, def) in all.take(SLOTS).withIndex()) {
            val picked = def.id == held
            val icon = Icon.of(if (picked) Material.GLOWSTONE_DUST else Material.NETHER_STAR, (if (picked) "<green>✋ " else "") + def.color + def.name, buildList {
                add("<dark_gray>" + (index + 1) + "번째(아래부터) · id " + def.id + "</dark_gray>")
                add(def.badge())
                add("")
                when {
                    held == null -> add("<yellow>▶ 클릭: 이 등급 집기</yellow>")
                    picked -> add("<gray>▶ 다시 누르면 내려놓기(그대로)</gray>")
                    else -> add("<yellow>▶ 클릭: 집은 등급을 이 자리(" + (index + 1) + "번째)로</yellow>")
                }
            }).also { if (picked) it.editMeta { meta -> meta.setEnchantmentGlintOverride(true) } }
            set(index, icon) {
                val holding = held
                when {
                    holding == null -> held = def.id
                    holding == def.id -> held = null
                    else -> {
                        custom.tiers.moveTo(holding, index)
                        held = null
                    }
                }
                refresh()
            }
        }
        set(SLOT_HELP, Icon.of(Material.BOOK, "<aqua>순서 정하기</aqua>", listOf(
            "<gray>왼쪽 위가 <white>가장 낮은</white> 등급, 뒤로 갈수록 높은 등급입니다.</gray>",
            "<gray>1) 옮길 등급을 눌러 집습니다.</gray>",
            "<gray>2) 놓을 자리의 등급을 누르면 그 자리로 갑니다.</gray>",
            "<dark_gray>유물은 가장 높은 등급 하나만 효과가 납니다.</dark_gray>",
        ) + (held?.let { listOf("", "<green>집은 등급: " + (custom.tiers.get(it)?.let { d -> d.color + d.name } ?: it) + "</green>") } ?: emptyList())))
        set(Paging.SLOT_BACK, Icon.back()) { back() }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    companion object {
        /** 등급 칸 — 위 다섯 줄. */
        const val SLOTS = 45
        const val SLOT_HELP = 49
    }
}

/** 등급 하나의 설정 — 이름 · 이름 색 · 배지 색 · 순서 · 지우기. */
class TierEditMenu(custom: CustomItems, private val viewer: Player, private val id: String) :
    Menu(custom, 27, Text.renderFlat("<dark_gray>등급 — $id</dark_gray>")) {

    override fun draw() {
        clear()
        val def = custom.tiers.get(id) ?: run {
            TierListMenu(custom, viewer).open(viewer)
            return
        }
        val all = custom.tiers.all()
        val at = all.indexOfFirst { it.id == id }
        set(SLOT_NAME, Icon.of(Material.NAME_TAG, "<yellow>보이는 이름: <white>" + def.name + "</white></yellow>", listOf(
            "<gray>배지와 고르는 화면에 보입니다.</gray>", "", "<yellow>▶ 클릭: 바꾸기</yellow>",
        ))) {
            prompt("보이는 이름", def.name, listOf("<gray>MiniMessage 서식을 써도 됩니다.</gray>")) { raw -> def.copy(name = raw) }
        }
        // 색은 고르는 화면(색표 + RGB, core ColorPickMenu)으로 — 사용자 요청 2026-10-14.
        set(SLOT_COLOR, Icon.of(Material.ORANGE_DYE, "<yellow>이름 색: " + def.color + "가나다 Abc</yellow>", listOf(
            "<gray>아이템 이름 앞에 붙습니다. 바닥에 떨어진 아이템의 발광 색도</gray>",
            "<gray>이 색에서 가장 가까운 것.</gray>",
            "<dark_gray>지금: " + literal(def.color) + "</dark_gray>",
            "", "<yellow>▶ 클릭: 색 고르기</yellow>",
        ))) {
            pickColor("등급 이름 색", def.color) { hex, now -> now.copy(color = "<$hex>") }
        }
        set(SLOT_BADGE_FROM, Icon.of(Material.PAINTING, "<yellow>배지 시작 색: <color:" + def.from + ">■■■</color></yellow>", listOf(
            def.badge(),
            "<gray>로어 맨 아래 배지 그라데이션의 시작 색.</gray>",
            "<dark_gray>지금: " + def.from + "</dark_gray>",
            "", "<yellow>▶ 클릭: 색 고르기</yellow>",
        ))) { pickColor("배지 시작 색", def.from) { hex, now -> now.copy(from = hex) } }
        set(SLOT_BADGE_TO, Icon.of(Material.ITEM_FRAME, "<yellow>배지 끝 색: <color:" + def.to + ">■■■</color></yellow>", listOf(
            def.badge(),
            "<gray>로어 맨 아래 배지 그라데이션의 끝 색.</gray>",
            "<dark_gray>지금: " + def.to + "</dark_gray>",
            "", "<yellow>▶ 클릭: 색 고르기</yellow>",
        ))) { pickColor("배지 끝 색", def.to) { hex, now -> now.copy(to = hex) } }
        set(SLOT_ORDER, Icon.of(Material.HOPPER, "<yellow>순서: <white>아래부터 " + (at + 1) + "번째</white> / " + all.size + "</yellow>", listOf(
            "<gray>순서 정하기 화면에서 집고 놓아 옮깁니다.</gray>",
            "", "<yellow>▶ 클릭: 순서 정하기</yellow>",
        ))) { TierOrderMenu(custom, viewer) { open(viewer) }.open(viewer) }
        set(SLOT_DELETE, Icon.of(Material.LAVA_BUCKET, "<red>지우기</red>", listOf(
            "<gray>이 등급의 아이템은 지워지지 않고 <white>배지 없이</white> 보입니다.</gray>",
            "<gray>같은 id 로 다시 만들면 이어집니다.</gray>",
        ))) {
            val members = custom.items.all().count { it.tier.id == id }
            ConfirmMenu(
                owner = custom,
                question = "<red>등급 '" + def.name + "' 을(를) 지울까요?</red>",
                detail = listOf("<gray>이 등급의 아이템 <white>" + members + "</white>개는 배지 없이 보입니다.</gray>"),
                onConfirm = {
                    custom.tiers.remove(id)
                    TierListMenu(custom, viewer).open(viewer)
                },
                onCancel = { open(viewer) },
            ).open(viewer)
        }
        set(SLOT_BACK, Icon.back()) { TierListMenu(custom, viewer).open(viewer) }
    }

    /** 색 고르기 화면 — 고르면 그때의 정의에 [change] 를 적고 이 화면으로 돌아온다. */
    private fun pickColor(title: String, current: String, change: (String, TierDef) -> TierDef) {
        kr.inmc.core.gui.ColorPickMenu(custom, viewer, title, custom.prompts, current = current, back = { open(viewer) }) { hex ->
            if (hex != null) custom.tiers.get(id)?.let { now -> custom.tiers.put(change(hex, now)) }
            open(viewer)
        }.show()
    }

    /** [change] 가 null 이면 못 알아본 값 — 알리고 그대로 둔다. */
    private fun prompt(label: String, now: String, hints: List<String>, change: (String) -> TierDef?) {
        Editors.promptText(
            custom.prompts, viewer, label,
            listOf("<gray>지금: <white>" + literal(now) + "</white></gray>") + hints,
            reopen = { open(viewer) },
        ) { raw ->
            if (raw.isBlank()) return@promptText
            val next = change(raw.trim())
            if (next == null) viewer.sendMessage(Text.render("<red>알아볼 수 없는 값입니다: " + literal(raw) + "</red>")) else custom.tiers.put(next)
        }
    }

    companion object {
        const val SLOT_NAME = 10
        const val SLOT_COLOR = 11
        const val SLOT_BADGE_FROM = 12
        const val SLOT_BADGE_TO = 13
        const val SLOT_ORDER = 14
        const val SLOT_DELETE = 16
        const val SLOT_BACK = 22
    }
}

/** 태그를 글자 그대로 보이게(`<gold>` 를 색이 아니라 글자로) — MiniMessage 의 `\<`. */
internal fun literal(text: String): String = text.replace("\\", "\\\\").replace("<", "\\<")
