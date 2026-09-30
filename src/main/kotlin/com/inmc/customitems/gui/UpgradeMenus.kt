package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.EvolveKeep
import com.inmc.customitems.item.Evolution
import com.inmc.customitems.item.FailResult
import com.inmc.customitems.item.Stat
import com.inmc.customitems.item.Tier
import com.inmc.customitems.item.UpgradeMode
import com.inmc.customitems.item.UpgradeSpec
import com.inmc.customitems.item.UpgradeStep
import com.inmc.customitems.item.UpgradeStone
import com.inmc.customitems.item.UpgradeTable
import kr.inmc.core.gui.ConfirmMenu
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.store.DefinitionKey
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 고치는 강화표가 어디 있는가 — 공용(`upgrades.yml`)이든 한 아이템 전용이든 같은 화면이 고친다.
 * 화면은 표를 들고 있지 않고 매번 여기서 다시 찾는다(정의가 불변이라 옛 객체를 들고 있으면 두 번째 편집이 첫 번째를 지운다).
 */
interface TableRef {
    val title: String
    fun get(): UpgradeTable?
    fun set(table: UpgradeTable)
}

class SharedTableRef(private val custom: CustomItems, private val id: String) : TableRef {
    override val title: String get() = "강화 방식 — " + (custom.upgrades.get(id)?.name ?: id)
    override fun get(): UpgradeTable? = custom.upgrades.get(id)
    override fun set(table: UpgradeTable) = custom.upgrades.put(table)
}

class ItemTableRef(private val custom: CustomItems, private val itemId: String) : TableRef {
    override val title: String get() = "전용 강화 — $itemId"
    override fun get(): UpgradeTable? = custom.items.get(itemId)?.upgrade?.own
    override fun set(table: UpgradeTable) {
        val item = custom.items.get(itemId) ?: return
        custom.items.put(item.copy(upgrade = item.upgrade.copy(own = table)))
    }
}

/** 고르기 화면 하나 — 한 개 고르기든 여러 개 켜고 끄기든. */
class ChoiceMenu(
    custom: CustomItems,
    private val viewer: Player,
    title: String,
    private val options: () -> List<Pair<String, ItemStack>>,
    private val selected: () -> Set<String>,
    private val back: () -> Unit,
    private val onPick: (String) -> Unit,
) : Menu(custom, 54, Text.renderFlat("<dark_gray>$title</dark_gray>")) {

    private var page = 0

    override fun draw() {
        clear()
        val all = options()
        val chosen = selected()
        page = Paging.clamp(page, all.size)
        for ((slot, option) in Paging.slice(all, page).withIndex()) {
            val (id, icon) = option
            val marked = icon.clone().also { if (id in chosen) it.editMeta { meta -> meta.setEnchantmentGlintOverride(true) } }
            set(slot, Icon.annotate(marked, lore = listOf("", if (id in chosen) "<green>✔ 골라져 있음</green>" else "<yellow>▶ 클릭: 고르기</yellow>"))) {
                onPick(id)
                refresh()
            }
        }
        if (page > 0) set(Paging.SLOT_PREV, Icon.prevPage()) { page--; refresh() }
        if (page < Paging.pageCount(all.size) - 1) set(Paging.SLOT_NEXT, Icon.nextPage()) { page++; refresh() }
        set(Paging.SLOT_BACK, Icon.back()) { back() }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }
}

/** 아이템 하나를 고르기 화면의 한 칸으로. */
internal fun itemOption(custom: CustomItems, item: CustomItem): Pair<String, ItemStack> =
    item.id to Icon.annotate(custom.items.preview(item), lore = listOf("<dark_gray>inmc:" + item.id + "</dark_gray>"))

/** 공용 강화 방식 목록(관리). */
class UpgradeTableListMenu(custom: CustomItems, private val viewer: Player) :
    Menu(custom, 54, Text.renderFlat("<dark_gray>강화 방식</dark_gray>")) {

    override fun draw() {
        clear()
        for ((index, table) in custom.upgrades.all().take(Paging.PER_PAGE).withIndex()) {
            val users = custom.items.all().count { it.upgrade.own == null && it.upgrade.template == table.id }
            set(index, Icon.of(Material.ANVIL, "<gold>" + table.name + "</gold>", listOf(
                "<gray>id <white>" + table.id + "</white> · " + table.mode.display + " · 최대 <white>+" + table.maxLevel + "</white></gray>",
                "<gray>쓰는 아이템 <white>" + users + "</white>개</gray>",
                "", "<yellow>▶ 좌클릭: 편집</yellow>", "<red>▶ 우클릭: 지우기</red>",
            ))) { event ->
                if (!event.isRightClick) return@set UpgradeTableMenu(custom, viewer, SharedTableRef(custom, table.id)) { open(viewer) }.open(viewer)
                ConfirmMenu(custom, "<red>강화 방식 '" + table.name + "' 을(를) 지울까요?</red>",
                    listOf("<gray>이 방식을 쓰던 아이템 " + users + "개는 강화할 수 없게 됩니다.</gray>", "<gray>이미 강화된 단계는 남지만 능력치가 기본으로 돌아갑니다.</gray>"),
                    onConfirm = { custom.upgrades.remove(table.id); open(viewer) }, onCancel = { open(viewer) }).open(viewer)
            }
        }
        set(SLOT_ADD, Icon.of(Material.LIME_DYE, "<green>새 강화 방식</green>", listOf("<gray>id 를 적으면 만들어집니다. 예: <white>weapon</white>, <white>armor</white></gray>"))) {
            Editors.promptText(custom.prompts, viewer, "강화 방식 id", listOf("<gray>" + DefinitionKey.HINT + ".</gray>"), reopen = { open(viewer) }) { raw ->
                val id = raw.trim().lowercase()
                if (!DefinitionKey.isValid(id) || custom.upgrades.get(id) != null) {
                    viewer.sendMessage(Text.render("<red>쓸 수 없는 id 입니다: $id</red>"))
                    return@promptText
                }
                custom.upgrades.put(UpgradeTable(id, id))
            }
        }
        set(SLOT_INFO, Icon.of(Material.BOOK, "<aqua>강화 방식이란</aqua>", listOf(
            "<gray>무기용·방어구용처럼 여러 아이템이 함께 쓰는 강화표입니다.</gray>",
            "<gray>아이템 설정의 <white>강화</white> 에서 고르거나,</gray>",
            "<gray>그 아이템만의 표를 따로 만들 수 있습니다.</gray>",
        )))
        set(Paging.SLOT_BACK, Icon.back()) { ItemTypeMenu(custom, viewer).open(viewer) }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private companion object {
        const val SLOT_ADD = 48
        const val SLOT_INFO = 49
    }
}

/** 강화표 하나 — 이름·방식·단계들. */
class UpgradeTableMenu(
    custom: CustomItems,
    private val viewer: Player,
    private val ref: TableRef,
    private val back: () -> Unit,
) : Menu(custom, 54, Text.renderFlat("<dark_gray>" + ref.title + "</dark_gray>")) {

    override fun draw() {
        clear()
        val table = ref.get() ?: return back()
        for ((index, step) in table.steps.take(UpgradeTable.MAX_STEPS).withIndex()) {
            val level = index + 1
            set(index, Icon.of(Material.EXPERIENCE_BOTTLE, "<gold>+" + level + " 로</gold>", stepSummary(table, step) + listOf("", "<yellow>▶ 클릭: 편집</yellow>"))) {
                UpgradeStepMenu(custom, viewer, ref, level) { open(viewer) }.open(viewer)
            }
        }
        set(SLOT_ADD, Icon.of(Material.LIME_DYE, "<green>단계 더하기 <white>(+" + (table.maxLevel + 1) + ")</white></green>", listOf(
            "<gray>앞 단계의 확률·실패 결과·능력치를 그대로 가져옵니다.</gray>",
            "<gray>등급·모양은 \"이 단계부터\"라 가져오지 않습니다.</gray>",
        ))) {
            if (table.maxLevel >= UpgradeTable.MAX_STEPS) return@set
            val step = table.steps.lastOrNull()?.copy(tier = null, customModelData = 0, texture = "", model = "", backpack = 0, autoPickup = false) ?: UpgradeStep()
            ref.set(table.copy(steps = table.steps + step))
            refresh()
        }
        set(SLOT_REMOVE, Icon.of(Material.RED_DYE, "<red>마지막 단계 지우기</red>", listOf(
            "<gray>가운데 단계를 지우면 뒤 단계가 한 칸씩 당겨져</gray>",
            "<gray>이미 강화된 아이템의 능력치가 바뀝니다. 끝에서만 지웁니다.</gray>",
        ))) {
            if (table.steps.isEmpty()) return@set
            ConfirmMenu(custom, "<red>+" + table.maxLevel + " 단계를 지울까요?</red>",
                listOf("<gray>이미 +" + table.maxLevel + " 인 아이템은 +" + (table.maxLevel - 1) + " 의 능력치로 보입니다.</gray>"),
                onConfirm = { ref.set(table.copy(steps = table.steps.dropLast(1))); open(viewer) }, onCancel = { open(viewer) }).open(viewer)
        }
        set(SLOT_MODE, Icon.of(Material.COMPARATOR, "<yellow>능력치 적는 방식: <white>" + table.mode.display + "</white></yellow>",
            Editors.optionList(UpgradeMode.entries.toList(), table.mode) { it.display } + listOf(
                "",
                "<gray>증가량: 단계마다 더하는 값과 기본의 % (누적)</gray>",
                "<gray>단계별 전체값: 그 단계의 능력치 전체</gray>",
            ) + Editors.cycleHint)) { event ->
            ref.set(table.copy(mode = Editors.cycle(event, UpgradeMode.entries.toList(), table.mode)))
            refresh()
        }
        set(SLOT_NAME, Icon.of(Material.NAME_TAG, "<yellow>이름: <white>" + table.name + "</white></yellow>", listOf("", "<yellow>▶ 클릭: 적기</yellow>"))) {
            Editors.promptText(custom.prompts, viewer, "강화 방식 이름", emptyList(), reopen = { open(viewer) }) { raw ->
                ref.get()?.let { ref.set(it.copy(name = raw.trim().ifBlank { it.id })) }
            }
        }
        set(Paging.SLOT_BACK, Icon.back()) { back() }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun stepSummary(table: UpgradeTable, step: UpgradeStep): List<String> = buildList {
        add("<gray>성공 <white>" + kr.inmc.core.util.Numbers.chance(step.chance) + "%</white> · 실패하면 <white>" + step.failText + "</white></gray>")
        for ((stat, value) in step.stats.entries.take(6)) add(stat.line(value))
        if (step.stats.size > 6) add("<dark_gray>…</dark_gray>")
        if (table.mode == UpgradeMode.ADD) for ((stat, value) in step.percents.entries.take(4)) add("<gray>" + stat.display + " 기본의 +" + kr.inmc.core.util.Numbers.chance(value) + "%</gray>")
        step.tier?.let { add("<gray>등급 → " + it.color + it.display + "</gray>") }
        if (step.customModelData > 0 || step.texture.isNotBlank() || step.model.isNotBlank()) add("<gray>모양이 바뀝니다</gray>")
        if (step.backpack != 0) add("<gold>배낭 +" + step.backpack + "칸</gold>")
        if (step.autoPickup) add("<gold>드랍 자동 수납</gold>")
    }

    private companion object {
        const val SLOT_ADD = 47
        const val SLOT_REMOVE = 48
        const val SLOT_MODE = 50
        const val SLOT_NAME = 51
    }
}

/** 강화 한 단계. */
class UpgradeStepMenu(
    custom: CustomItems,
    private val viewer: Player,
    private val ref: TableRef,
    private val level: Int,
    private val back: () -> Unit,
) : Menu(custom, 54, Text.renderFlat("<dark_gray>" + ref.title + " — +" + level + "</dark_gray>")) {

    private fun step(): UpgradeStep? = ref.get()?.step(level)

    private fun change(transform: (UpgradeStep) -> UpgradeStep) {
        val table = ref.get() ?: return
        val step = table.step(level) ?: return
        ref.set(table.copy(steps = table.steps.toMutableList().also { it[level - 1] = transform(step) }))
    }

    override fun draw() {
        clear()
        val table = ref.get() ?: return back()
        val step = table.step(level) ?: return back()

        set(SLOT_CHANCE, Editors.numberIcon(Material.RABBIT_FOOT, "<yellow>성공 확률</yellow>", step.chance, unit = "%", stepLabel = "5")) { event ->
            if (Editors.isPrompt(event)) {
                Editors.promptDouble(custom.prompts, viewer, "성공 확률(%)", 0.0, 100.0, reopen = { open(viewer) }) { v -> change { it.copy(chance = v) } }
                return@set
            }
            change { it.copy(chance = (it.chance + Editors.step(event, 5.0)).coerceIn(0.0, 100.0)) }
            refresh()
        }
        set(SLOT_FAIL, Icon.of(Material.TNT, "<yellow>실패하면: <white>" + step.fail.display + "</white></yellow>",
            Editors.optionList(FailResult.entries.toList(), step.fail) { it.display } + listOf(
                "",
                "<gray>그대로가 아니면 옆 칸의 확률로 한 번 더 굴립니다(빗나가면 그대로).</gray>",
                "<gray>파괴돼도 박혀 있던 보석은 돌려줍니다.</gray>",
            ) + Editors.cycleHint)) { event ->
            change { it.copy(fail = Editors.cycle(event, FailResult.entries.toList(), it.fail)) }
            refresh()
        }
        // 실패 결과의 확률 — 실패하면 한 번 더 굴려 빗나가면 그대로(인첸트 강화 스크롤의 하락 확률과 같다, 사용자 요청 2026-10-01).
        // 그대로면 뜻이 없어 숨긴다.
        if (step.fail != FailResult.KEEP) {
            val overall = (100.0 - step.chance) * step.failChance / 100.0
            set(SLOT_FAIL_CHANCE, Editors.numberIcon(Material.GUNPOWDER, "<yellow>실패 시 " + step.fail.display + " 확률</yellow>", step.failChance, unit = "%", extra = listOf(
                "<gray>강화에 실패하면 이 확률로 한 번 더 굴립니다 —</gray>",
                "<gray>맞으면 <white>" + step.fail.display + "</white>, 빗나가면 <white>그대로</white>.</gray>",
                "<gray>강화 한 번에 " + step.fail.display + "될 확률: <white>" + kr.inmc.core.util.Numbers.chance(overall) + "%</white></gray>",
                "<dark_gray>(단계 성공 확률 기준 — 강화석이 확률을 정하면 달라집니다)</dark_gray>",
            ), stepLabel = "5")) { event ->
                if (Editors.isPrompt(event)) {
                    Editors.promptDouble(custom.prompts, viewer, "실패 시 " + step.fail.display + " 확률(%)", 0.0, 100.0, reopen = { open(viewer) }) { v -> change { it.copy(failChance = v) } }
                    return@set
                }
                change { it.copy(failChance = (it.failChance + Editors.step(event, 5.0)).coerceIn(0.0, 100.0)) }
                refresh()
            }
        }
        val tiers = listOf<Tier?>(null) + Tier.entries
        set(SLOT_TIER, Icon.of(Material.NETHER_STAR, "<yellow>이 단계부터 등급: " + (step.tier?.let { it.color + it.display } ?: "<white>그대로</white>") + "</yellow>",
            Editors.optionList(tiers, step.tier) { it?.let { t -> t.color + t.display } ?: "그대로" } + Editors.cycleHint)) { event ->
            change { it.copy(tier = Editors.cycle(event, tiers, it.tier)) }
            refresh()
        }
        // 아이템 설정의 겉모습과 같은 손짓(사용자 요청 2026-09-30 — 여기서도 모델을 고르게).
        set(SLOT_TEXTURE, Icon.of(Material.PAINTING, "<yellow>이 단계부터 겉모습: <white>" + step.model.ifBlank { step.texture }.ifBlank { "그대로" } + "</white></yellow>", listOf(
            if (step.model.isNotBlank()) "<gray>모델: <white>" + step.model + "</white></gray>" else if (step.texture.isNotBlank()) "<gray>텍스처: <white>" + step.texture + "</white></gray>" else "<dark_gray>아이템 모양 그대로</dark_gray>",
            "<gray>팩을 다시 빌드해야 보입니다.</gray>", "",
            "<yellow>▶ 좌클릭: 팩의 모델에서 고르기(검색)</yellow>",
            "<yellow>▶ Shift+좌클릭: 텍스처·모델 이름 직접 입력</yellow>",
            "<red>▶ 우클릭: 비우기</red>",
        ))) { event ->
            when {
                event.isRightClick -> {
                    change { it.copy(texture = "", model = "") }
                    refresh()
                }
                !event.isShiftClick -> ModelListMenu.pick(custom, viewer, current = step.model.takeIf { it.isNotBlank() }, back = { open(viewer) }) { model ->
                    change { it.copy(model = model, texture = "") }
                    open(viewer)
                }
                else -> Editors.promptText(custom.prompts, viewer, "텍스처 파일 이름", listOf(
                    "<gray>pack/textures/ 에 넣은 png 이름. 예: <white>relic_5.png</white></gray>",
                    "<gray>모델을 직접 만들었으면 <white>model:이름</white> 으로 적으세요.</gray>",
                ), reopen = { open(viewer) }) { raw ->
                    val text = raw.trim()
                    change { if (text.startsWith("model:")) it.copy(model = text.removePrefix("model:").trim(), texture = "") else it.copy(texture = text, model = "") }
                }
            }
        }
        // 배낭에만 뜻이 있는 둘(사용자 요청 2026-09-30 — 강화하면 칸이 늘고, 자동 수납이 붙는다).
        set(SLOT_BACKPACK, Editors.intIcon(Material.BUNDLE, "<gold>이 단계에서 배낭 +칸</gold>", step.backpack, extra = listOf(
            "<gray>배낭에만 뜻이 있습니다. 앞 단계들 것과 쌓입니다.</gray>",
            "<gray>+" + level + " 까지 합: <white>" + table.backpackAt(level) + "</white>칸</gray>",
        ), stepLabel = "9")) { event ->
            if (Editors.isPrompt(event)) {
                Editors.promptInt(custom.prompts, viewer, "이 단계에서 더할 배낭 칸", 0, com.inmc.customitems.player.BackpackLayout.MAX, { open(viewer) }) { value -> change { it.copy(backpack = value) } }
                return@set
            }
            change { it.copy(backpack = (it.backpack + Editors.step(event, 9)).coerceIn(0, com.inmc.customitems.player.BackpackLayout.MAX)) }
            refresh()
        }
        set(SLOT_AUTO_PICKUP, Icon.of(if (step.autoPickup) Material.HOPPER else Material.GRAY_DYE, "<gold>이 단계부터 드랍 자동 수납: " + Icon.toggle(step.autoPickup) + "</gold>", listOf(
            "<gray>배낭에만 뜻이 있습니다 — 주운 물건이 가방보다 먼저</gray>",
            "<gray>배낭에 들어갑니다. 켜면 뒤 단계에도 이어집니다.</gray>",
            "", "<yellow>▶ 클릭: 전환</yellow>",
        ))) {
            change { it.copy(autoPickup = !it.autoPickup) }
            refresh()
        }

        // 고정값 다음에 % — 한 목록으로 보여준다.
        val entries = step.stats.entries.map { Triple(it.key, it.value, false) } +
            (if (table.mode == UpgradeMode.ADD) step.percents.entries.map { Triple(it.key, it.value, true) } else emptyList())
        for ((index, entry) in entries.take(STATS).withIndex()) {
            val (stat, value, percent) = entry
            val label = if (percent) "<aqua>" + stat.display + " 기본의 +" + kr.inmc.core.util.Numbers.chance(value) + "%</aqua>" else stat.line(value)
            set(FIRST_STAT + index, Icon.of(if (percent) Material.GLOWSTONE_DUST else Material.MAGENTA_DYE, label, listOf("", "<yellow>▶ 좌클릭: 값 바꾸기 · <red>우클릭: 빼기</red></yellow>"))) { event ->
                if (event.isRightClick) {
                    change { if (percent) it.copy(percents = it.percents - stat) else it.copy(stats = it.stats - stat) }
                    refresh()
                } else {
                    askValue(stat, percent)
                }
            }
        }
        set(SLOT_ADD_STAT, Icon.of(Material.LIME_DYE, "<green>능력치 추가</green>", listOf(
            if (table.mode == UpgradeMode.ADD) "<gray>이 단계에서 <white>더해지는</white> 고정값입니다.</gray>" else "<gray>이 단계의 능력치 <white>전체</white>입니다.</gray>",
        ))) {
            StatPickMenu(custom, viewer, "<dark_gray>+$level 능력치</dark_gray>", back = { open(viewer) }) { stat -> askValue(stat, false) }.open(viewer)
        }
        if (table.mode == UpgradeMode.ADD) {
            set(SLOT_ADD_PERCENT, Icon.of(Material.GLOWSTONE_DUST, "<aqua>% 추가</aqua>", listOf(
                "<gray>그 능력치의 <white>기본(굴린) 값</white>의 이만큼을 더합니다. 누적됩니다.</gray>",
                "<gray>예: 공격력 기본 10 에 2% 면 단계마다 +0.2</gray>",
            ))) {
                StatPickMenu(custom, viewer, "<dark_gray>+$level 기본의 %</dark_gray>", back = { open(viewer) }) { stat -> askValue(stat, true) }.open(viewer)
            }
        }
        set(Paging.SLOT_BACK, Icon.back()) { back() }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun askValue(stat: Stat, percent: Boolean) {
        Editors.promptDouble(custom.prompts, viewer, stat.display + if (percent) " (기본의 %)" else "", -StatsMenu.MAX, StatsMenu.MAX, reopen = { open(viewer) }) { value ->
            change {
                when {
                    percent && value == 0.0 -> it.copy(percents = it.percents - stat)
                    percent -> it.copy(percents = it.percents + (stat to value))
                    value == 0.0 -> it.copy(stats = it.stats - stat)
                    else -> it.copy(stats = it.stats + (stat to value))
                }
            }
        }
    }

    /** 윗줄은 시도(성공 · 실패 결과 · 그 확률) → 모습(등급 · 겉모습) → 배낭 둘. */
    private companion object {
        const val SLOT_CHANCE = 10
        const val SLOT_FAIL = 11
        const val SLOT_FAIL_CHANCE = 12
        const val SLOT_TIER = 13
        const val SLOT_TEXTURE = 14
        const val SLOT_BACKPACK = 15
        const val SLOT_AUTO_PICKUP = 16
        const val FIRST_STAT = 18
        const val STATS = 27
        const val SLOT_ADD_STAT = 49
        const val SLOT_ADD_PERCENT = 50
    }
}

/** 아이템 하나의 강화·진화 설정. */
class ItemUpgradeMenu(custom: CustomItems, viewer: Player, id: String) :
    DetailMenu(custom, viewer, id, "<dark_gray>강화·진화 — $id</dark_gray>") {

    private fun change(transform: (UpgradeSpec) -> UpgradeSpec) = mutate { it.copy(upgrade = transform(it.upgrade)) }

    private fun changeEvolution(transform: (Evolution) -> Evolution) = change { spec -> spec.evolution?.let { spec.copy(evolution = transform(it)) } ?: spec }

    override fun draw() {
        clear()
        val item = item() ?: return ItemTypeMenu(custom, viewer).open(viewer)
        val spec = item.upgrade
        val table = spec.table(custom.items.lookup)

        set(SLOT_TABLE, Icon.of(Material.ANVIL, "<gold>강화 방식: <white>" + when {
            spec.own != null -> "이 아이템 전용"
            table != null -> table.name
            else -> "없음"
        } + "</white></gold>", listOf(
            "<gray>최대 <white>+" + (table?.maxLevel ?: 0) + "</white></gray>",
            "", "<yellow>▶ 클릭: 고르기</yellow>",
        ))) {
            ChoiceMenu(custom, viewer, "강화 방식 고르기",
                options = {
                    listOf(NONE to Icon.of(Material.BARRIER, "<gray>강화 없음</gray>"), OWN to Icon.of(Material.WRITABLE_BOOK, "<gold>이 아이템 전용 표</gold>",
                        listOf("<gray>첨부한 MMOItems 강화유물처럼 단계마다 능력치 전체를</gray>", "<gray>이 아이템에만 적을 때.</gray>"))) +
                        custom.upgrades.all().map { it.id to Icon.of(Material.ANVIL, "<gold>" + it.name + "</gold>", listOf("<gray>" + it.mode.display + " · 최대 +" + it.maxLevel + "</gray>")) }
                },
                selected = { setOf(custom.items.get(id)?.upgrade?.let { if (it.own != null) OWN else it.template.ifBlank { NONE } } ?: NONE) },
                back = { open(viewer) },
            ) { choice ->
                mutate(false) { current ->
                    val upgrade = current.upgrade
                    current.copy(upgrade = when (choice) {
                        NONE -> upgrade.copy(template = "", own = null)
                        // 전용 표는 지금 쓰던 공용 표를 베껴 시작한다 — 빈 표에서 시작하는 것보다 고칠 것이 적다.
                        OWN -> upgrade.copy(own = upgrade.own ?: (custom.upgrades.get(upgrade.template)?.copy(id = id) ?: UpgradeTable(id, id, UpgradeMode.ABSOLUTE)))
                        else -> upgrade.copy(template = choice, own = null)
                    })
                }
            }.open(viewer)
        }
        if (spec.own != null) {
            set(SLOT_OWN, Icon.of(Material.WRITABLE_BOOK, "<gold>전용 표 편집</gold>", listOf("<gray>단계 <white>" + spec.own.maxLevel + "</white>개 · " + spec.own.mode.display + "</gray>", "", "<yellow>▶ 클릭</yellow>"))) {
                UpgradeTableMenu(custom, viewer, ItemTableRef(custom, id)) { open(viewer) }.open(viewer)
            }
        }
        set(SLOT_STONES, Icon.of(Material.PRISMARINE_CRYSTALS, "<aqua>이 아이템에 쓸 수 있는 강화석 <white>" + (if (spec.stones.isEmpty()) "아무거나" else spec.stones.size.toString() + "종") + "</white></aqua>", listOf(
            "<gray>고르면 <white>그 강화석으로만</white> 강화됩니다.</gray>",
            "<gray>비우면 규칙(강화 방식·단계)에 맞는 아무 강화석이나.</gray>",
            "", "<yellow>▶ 클릭: 고르기</yellow>",
        ))) {
            ChoiceMenu(custom, viewer, "쓸 수 있는 강화석",
                options = { custom.items.all().filter { it.consume?.upgrade != null }.map { itemOption(custom, it) } },
                selected = { custom.items.get(id)?.upgrade?.stones?.toSet().orEmpty() },
                back = { open(viewer) },
            ) { stone -> custom.items.get(id)?.let { current -> custom.items.put(current.copy(upgrade = current.upgrade.copy(stones = toggle(current.upgrade.stones, stone)))) } }.open(viewer)
        }

        val evolution = spec.evolution
        set(SLOT_INTO, Icon.of(Material.DRAGON_BREATH, "<light_purple>진화 → <white>" + (evolution?.let { custom.items.get(it.into)?.label() ?: it.into } ?: "없음") + "</white></light_purple>", listOf(
            "<gray>최대 강화(강화가 없으면 언제든) 뒤 이 아이템으로 진화합니다.</gray>", "", "<yellow>▶ 클릭: 고르기</yellow>",
        ))) {
            ChoiceMenu(custom, viewer, "진화할 아이템",
                options = { listOf(NONE to Icon.of(Material.BARRIER, "<gray>진화 없음</gray>")) + custom.items.all().filter { it.id != id }.map { itemOption(custom, it) } },
                selected = { setOf(custom.items.get(id)?.upgrade?.evolution?.into ?: NONE) },
                back = { open(viewer) },
            ) { choice ->
                mutate(false) { current ->
                    val upgrade = current.upgrade
                    current.copy(upgrade = upgrade.copy(evolution = if (choice == NONE) null else (upgrade.evolution?.copy(into = choice) ?: Evolution(choice))))
                }
            }.open(viewer)
        }
        if (evolution != null) {
            set(SLOT_KEEP, Icon.of(Material.CHEST_MINECART, "<light_purple>넘길 것: <white>" + evolution.keep.display + "</white></light_purple>",
                Editors.optionList(EvolveKeep.entries.toList(), evolution.keep) { it.display } + listOf("", "<gray>" + evolution.keep.description + "</gray>") + Editors.cycleHint)) { event ->
                changeEvolution { it.copy(keep = Editors.cycle(event, EvolveKeep.entries.toList(), it.keep)) }
            }
            set(SLOT_BY_STONE, Icon.of(Icon.toggleMaterial(evolution.byStone), "<light_purple>진화석으로: " + Icon.toggle(evolution.byStone) + "</light_purple>",
                listOf("<gray>진화석을 이 아이템 위에 끌어다 놓아 진화합니다.</gray>"))) { changeEvolution { it.copy(byStone = !it.byStone) } }
            set(SLOT_BY_STATION, Icon.of(Icon.toggleMaterial(evolution.byStation), "<light_purple>제작대에서: " + Icon.toggle(evolution.byStation) + "</light_purple>",
                listOf("<gray>제작대의 <white>진화</white> 화면에서 재료를 내고 진화합니다.</gray>"))) { changeEvolution { it.copy(byStation = !it.byStation) } }
            val stations = listOf("") + custom.stations.all().map { it.id }
            set(SLOT_STATION, Icon.of(Material.SMITHING_TABLE, "<light_purple>제작대: <white>" + (custom.stations.get(evolution.station)?.name ?: "아무 제작대") + "</white></light_purple>",
                Editors.optionList(stations, evolution.station) { custom.stations.get(it)?.name ?: "아무 제작대" } + Editors.cycleHint)) { event ->
                changeEvolution { it.copy(station = Editors.cycle(event, stations, it.station)) }
            }
            set(SLOT_MATERIALS, Icon.of(Material.BUNDLE, "<light_purple>제작대 진화 재료 <white>" + evolution.materials.size + "</white>종</light_purple>", listOf(
                "<gray>진화석으로 진화할 때는 들지 않습니다.</gray>", "", "<yellow>▶ 클릭: 칸에 넣어 정하기</yellow>",
            ))) {
                PartGridMenu(custom, viewer, "진화 재료 — $id",
                    load = { custom.items.get(id)?.upgrade?.evolution?.materials },
                    save = { parts -> custom.items.get(id)?.let { current -> current.upgrade.evolution?.let { evo -> custom.items.put(current.copy(upgrade = current.upgrade.copy(evolution = evo.copy(materials = parts)))) } } },
                    back = { open(viewer) }).open(viewer)
            }
        }
        backAndClose()
    }

    private companion object {
        const val NONE = "-"
        const val OWN = "*"
        const val SLOT_TABLE = 10
        const val SLOT_OWN = 11
        const val SLOT_STONES = 13
        const val SLOT_INTO = 28
        const val SLOT_KEEP = 29
        const val SLOT_BY_STONE = 30
        const val SLOT_BY_STATION = 31
        const val SLOT_STATION = 32
        const val SLOT_MATERIALS = 33
    }
}

/** 강화석의 규칙. */
class UpgradeStoneMenu(custom: CustomItems, viewer: Player, id: String) :
    DetailMenu(custom, viewer, id, "<dark_gray>강화석 — $id</dark_gray>") {

    private fun change(transform: (UpgradeStone) -> UpgradeStone) =
        mutate { item -> item.consume?.let { item.copy(consume = it.copy(upgrade = transform(it.upgrade ?: UpgradeStone()))) } ?: item }

    override fun draw() {
        clear()
        val item = item() ?: return ItemTypeMenu(custom, viewer).open(viewer)
        val stone = item.consume?.upgrade ?: return ConsumeMenu(custom, viewer, id).open(viewer)

        set(SLOT_TEMPLATES, Icon.of(Material.ANVIL, "<gold>쓸 수 있는 강화 방식 <white>" + (if (stone.templates.isEmpty()) "전부" else stone.templates.joinToString()) + "</white></gold>", listOf(
            "<gray>무기 강화석·방어구 강화석처럼 나눌 때.</gray>", "<gray>비우면 전부(전용 표를 가진 아이템 포함).</gray>", "", "<yellow>▶ 클릭: 고르기</yellow>",
        ))) {
            ChoiceMenu(custom, viewer, "강화석이 쓰이는 방식",
                options = { custom.upgrades.all().map { it.id to Icon.of(Material.ANVIL, "<gold>" + it.name + "</gold>") } },
                selected = { custom.items.get(id)?.consume?.upgrade?.templates?.toSet().orEmpty() },
                back = { open(viewer) },
            ) { template -> change { it.copy(templates = toggle(it.templates, template)) } }.open(viewer)
        }
        set(SLOT_ITEMS, Icon.of(Material.ITEM_FRAME, "<gold>쓸 수 있는 아이템 <white>" + (if (stone.items.isEmpty()) "전부" else stone.items.size.toString() + "종") + "</white></gold>", listOf(
            "<gray>특정 아이템 전용 강화석을 만들 때.</gray>", "<gray>여기 고른 아이템은 강화 방식 제한과 상관없이 됩니다.</gray>", "", "<yellow>▶ 클릭: 고르기</yellow>",
        ))) {
            ChoiceMenu(custom, viewer, "강화석이 쓰이는 아이템",
                options = { custom.items.all().filter { it.upgrade.table(custom.items.lookup) != null }.map { itemOption(custom, it) } },
                selected = { custom.items.get(id)?.consume?.upgrade?.items?.toSet().orEmpty() },
                back = { open(viewer) },
            ) { target -> change { it.copy(items = toggle(it.items, target)) } }.open(viewer)
        }
        set(SLOT_MIN, Editors.intIcon(Material.IRON_NUGGET, "<yellow>지금 단계가 이 이상일 때만</yellow>", stone.minLevel, extra = listOf("<gray>예: 상급 강화석은 +6 부터.</gray>"))) { event ->
            if (Editors.isPrompt(event)) {
                Editors.promptInt(custom.prompts, viewer, "최소 단계", 0, 1000, reopen = { open(viewer) }) { v -> change { it.copy(minLevel = v) } }
                return@set
            }
            change { it.copy(minLevel = (it.minLevel + Editors.step(event, 1)).coerceAtLeast(0)) }
        }
        // -1 이 "끝까지"(null) 다. 0 은 "0강에서만" 이라 따로 있어야 한다.
        set(SLOT_MAX, Editors.intIcon(Material.GOLD_NUGGET, "<yellow>지금 단계가 이 이하일 때만</yellow>", stone.maxLevel ?: -1, extra = listOf("<gray>-1 이면 끝까지. 0 이면 0강에서만.</gray>"))) { event ->
            if (Editors.isPrompt(event)) {
                Editors.promptInt(custom.prompts, viewer, "최대 단계", -1, 1000, reopen = { open(viewer) }) { v -> change { it.copy(maxLevel = v.takeIf { it >= 0 }) } }
                return@set
            }
            change { it.copy(maxLevel = ((it.maxLevel ?: -1) + Editors.step(event, 1)).takeIf { v -> v >= 0 }) }
        }
        set(SLOT_CHANCE, Editors.numberIcon(Material.EXPERIENCE_BOTTLE, "<yellow>이 강화석의 성공 확률</yellow>", stone.chance, unit = "%", stepLabel = "5", extra = listOf(
            "<gray>0 이면 강화표 단계의 확률을 씁니다.</gray>", "<gray>0 보다 크면 단계 확률 <white>대신</white> 이 확률(MMOItems 식).</gray>",
        ))) { event ->
            if (Editors.isPrompt(event)) {
                Editors.promptDouble(custom.prompts, viewer, "성공 확률(%)", 0.0, 100.0, reopen = { open(viewer) }) { v -> change { it.copy(chance = v) } }
                return@set
            }
            change { it.copy(chance = (it.chance + Editors.step(event, 5.0)).coerceIn(0.0, 100.0)) }
        }
        set(SLOT_TIERS, Icon.of(Material.NETHER_STAR, "<yellow>대상 등급별 확률 <white>" + (if (stone.chances.isEmpty()) "없음" else stone.chances.size.toString() + "개") + "</white></yellow>",
            stone.chances.entries.sortedBy { it.key.ordinal }.map { "<gray>" + it.key.color + it.key.display + " <white>" + kr.inmc.core.util.Numbers.chance(it.value) + "%</white></gray>" } + listOf(
                "", "<gray>있으면 위의 확률보다 먼저 씁니다(\"일반 10% · 희귀 8%…\").</gray>", "<yellow>▶ 클릭: 설정</yellow>",
            ))) { TierChanceMenu(custom, viewer, id).open(viewer) }
        set(SLOT_BONUS, Editors.numberIcon(Material.RABBIT_FOOT, "<yellow>성공 확률 보너스</yellow>", stone.bonus, unit = "%p", extra = listOf("<gray>위에서 정해진 확률에 더합니다(음수면 뺍니다).</gray>"))) { event ->
            if (Editors.isPrompt(event)) {
                Editors.promptDouble(custom.prompts, viewer, "확률 보너스(%p)", -100.0, 100.0, reopen = { open(viewer) }) { v -> change { it.copy(bonus = v) } }
                return@set
            }
            change { it.copy(bonus = (it.bonus + Editors.step(event, 5.0)).coerceIn(-100.0, 100.0)) }
        }
        set(Paging.SLOT_BACK, Icon.back()) { ConsumeMenu(custom, viewer, id).open(viewer) }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private companion object {
        const val SLOT_TEMPLATES = 10
        const val SLOT_ITEMS = 12
        const val SLOT_MIN = 14
        const val SLOT_MAX = 15
        const val SLOT_BONUS = 16
        const val SLOT_CHANCE = 28
        const val SLOT_TIERS = 30
    }
}

/** 강화석의 대상 등급별 확률. 0 이면 그 등급은 표에서 뺀다(다른 확률을 쓴다). */
class TierChanceMenu(custom: CustomItems, viewer: Player, id: String) :
    DetailMenu(custom, viewer, id, "<dark_gray>등급별 확률 — $id</dark_gray>") {

    private fun change(tier: Tier, value: Double) = mutate { item ->
        item.consume?.let { consume ->
            val stone = consume.upgrade ?: UpgradeStone()
            val chances = if (value <= 0.0) stone.chances - tier else stone.chances + (tier to value.coerceAtMost(100.0))
            item.copy(consume = consume.copy(upgrade = stone.copy(chances = chances)))
        } ?: item
    }

    override fun draw() {
        clear()
        val stone = item()?.consume?.upgrade ?: return ItemTypeMenu(custom, viewer).open(viewer)
        for ((index, tier) in Tier.entries.withIndex()) {
            val value = stone.chances[tier] ?: 0.0
            set(FIRST + index, Editors.numberIcon(Material.NETHER_STAR, "<yellow>" + tier.color + tier.display + "</yellow> <yellow>등급 대상</yellow>", value, unit = "%", stepLabel = "1",
                extra = listOf("<gray>0 이면 표에서 뺍니다.</gray>"))) { event ->
                if (Editors.isPrompt(event)) {
                    Editors.promptDouble(custom.prompts, viewer, tier.display + " 확률(%)", 0.0, 100.0, reopen = { open(viewer) }) { v -> change(tier, v) }
                    return@set
                }
                change(tier, (value + Editors.step(event, 1.0)).coerceIn(0.0, 100.0))
            }
        }
        set(Paging.SLOT_BACK, Icon.back()) { UpgradeStoneMenu(custom, viewer, id).open(viewer) }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private companion object {
        const val FIRST = 10
    }
}

internal fun toggle(list: List<String>, value: String): List<String> = if (value in list) list - value else list + value
