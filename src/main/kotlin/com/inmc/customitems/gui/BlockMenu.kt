package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import com.inmc.customitems.block.Harvest
import com.inmc.customitems.block.Mining
import com.inmc.customitems.block.ToolGrades
import com.inmc.customitems.block.ToolKind
import com.inmc.customitems.item.BlockKind
import com.inmc.customitems.item.BlockSpec
import com.inmc.customitems.pack.PackAssets
import kr.inmc.core.gui.DialogForm
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * 아이템 한 개의 블록 설정 — 놓는 방식(끔 · 꽉 찬 · 투명 · 엔티티), 캐기(단단함 · 맞는 도구 · 등급 · 도구 규칙), 나오는 것(블록 자신 ·
 * 드랍 표 · 섬세한 손길 · 행운 · 경험치).
 *
 * 블록 상태 방식을 고르면 **빈 상태를 그 자리에서 골라 적는다**(`CustomBlocks.allocate`). 다시 고르지 않는다 — 바꾸면 이미 놓인 블록이
 * 다른 블록으로 보인다. 같은 방식을 다시 눌러도 상태는 그대로다. 방식을 바꿔도 캐기·나오는 것 설정은 그대로 간다.
 *
 * @param back 뒤로 — 블록 목록에서 왔으면 그리로, 아니면 아이템 설정 화면.
 */
class BlockMenu(custom: CustomItems, viewer: Player, id: String, private val back: (() -> Unit)? = null) :
    DetailMenu(custom, viewer, id, "<dark_gray>블록 — $id</dark_gray>") {

    override fun draw() {
        clear()
        val item = item() ?: return ItemTypeMenu(custom, viewer).open(viewer)
        val spec = item.block
        fillEmpty(Icon.EDGE)

        choice(SLOT_OFF, Material.STRUCTURE_VOID, "블록 아님", spec == null, listOf("<gray>들고 우클릭해도 놓이지 않습니다.</gray>")) {
            mutate { it.copy(block = null) }
        }
        choice(SLOT_SOLID, Material.NOTE_BLOCK, BlockKind.SOLID.label, spec?.kind == BlockKind.SOLID, listOf(
            "<gray>ItemsAdder 의 REAL_NOTE 와 같습니다.</gray>",
            "<gray>꽉 찬 정육면체 블록. 가볍습니다.</gray>",
            "<gray>빈 자리 650칸.</gray>",
        )) { select(BlockKind.SOLID) }
        choice(SLOT_TRANSPARENT, Material.CHORUS_PLANT, BlockKind.TRANSPARENT.label, spec?.kind == BlockKind.TRANSPARENT, listOf(
            "<gray>ItemsAdder 의 REAL_TRANSPARENT 와 같습니다.</gray>",
            "<gray>상자처럼 비어 있거나 모양이 자유로운 블록.</gray>",
            "<gray>빈 자리 62칸.</gray>",
        )) { select(BlockKind.TRANSPARENT) }
        choice(SLOT_ENTITY, Material.ARMOR_STAND, BlockKind.ENTITY.label, spec?.kind == BlockKind.ENTITY, listOf(
            "<gray>방벽 블록 + 그 자리에 떠 있는 모델.</gray>",
            "<gray>모양·개수 제한이 없고 서버 설정을 안 바꿉니다.</gray>",
            "<gray>블록 하나에 엔티티 하나라 많이 깔면 무겁습니다.</gray>",
            "<gray>캘 때 금이 가는 모습은 안 보입니다.</gray>",
        )) { select(BlockKind.ENTITY) }

        set(SLOT_INFO, Icon.of(Material.PAPER, "<aqua>지금 설정</aqua>", buildList {
            if (spec == null) {
                add("<dark_gray>블록이 아닙니다.</dark_gray>")
                return@buildList
            }
            add("<gray>방식: <white>" + spec.kind.label + "</white></gray>")
            if (spec.kind.usesState) {
                add("<gray>상태: <white>" + spec.state.ifBlank { "—" } + "</white></gray>")
                add("<gray>모양: " + (PackAssets.blockModelFor(item)?.let { "<white>$it</white>" } ?: "<red>없음 — 모델이나 텍스처를 적으세요</red>") + "</gray>")
                add(when (custom.blocks.updatesDisabled?.get(spec.kind)) {
                    true -> "<gray>Paper 갱신 끄기: <green>켜짐</green></gray>"
                    false -> "<red>Paper 갱신 끄기가 꺼져 있습니다 — config/paper-global.yml 의 block-updates." + custom.blocks.flagName(spec.kind) + " 를 true 로 바꾸고 재시작하세요. 안 그러면 옆 블록이 바뀔 때 모양이 풀립니다.</red>"
                    null -> "<gray>Paper 갱신 끄기: <white>알 수 없음</white></gray>"
                })
                add("")
                add("<yellow>모양을 바꿨으면 리소스팩을 다시 만드세요.</yellow>")
            } else {
                add("<gray>모양: <white>이 아이템의 모양 그대로</white></gray>")
            }
            add("<dark_gray>방식을 바꾸면 이미 놓인 블록은 이 아이템으로 알아보지 못합니다.</dark_gray>")
        }))

        if (spec != null) {
            drawMining(spec)
            drawYield(spec)
        }

        set(Paging.SLOT_BACK, Icon.back()) { back?.invoke() ?: ItemEditMenu(custom, viewer, id).open(viewer) }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    // --- 캐기 -------------------------------------------------------------------------

    private fun drawMining(spec: BlockSpec) {
        set(SLOT_HARDNESS, Editors.numberIcon(Material.ANVIL, "<yellow>단단함</yellow>", spec.hardness, extra = listOf(
            "<gray>바닐라와 같은 단위 — 흙 0.5 · 돌 1.5 · 철광석 3 · 흑요석 50.</gray>",
            "<gray>0 이면 한 번에 부서집니다.</gray>",
        ), stepLabel = "0.5")) { event ->
            if (Editors.isPrompt(event)) {
                Editors.promptDouble(custom.prompts, viewer, "단단함", 0.0, BlockSpec.MAX_HARDNESS, { open(viewer) }) { value ->
                    change(false) { it.copy(hardness = round(value)) }
                }
                return@set
            }
            change { it.copy(hardness = round((spec.hardness + Editors.step(event, 0.5)).coerceIn(0.0, BlockSpec.MAX_HARDNESS))) }
        }

        val tools = listOf<ToolKind?>(null) + ToolKind.entries
        set(SLOT_TOOL, Icon.of(spec.tool?.icon ?: Material.STICK, "<yellow>맞는 도구: <white>" + (spec.tool?.label ?: "없음") + "</white></yellow>",
            listOf("<gray>이 도구로 캐면 도구 재질만큼 빨리 캡니다.</gray>", "") +
                Editors.optionList(tools, spec.tool) { it?.label ?: "없음 (무엇이든 맨손 빠르기)" } +
                listOf("", "<yellow>▶ 좌클릭: 다음 · 우클릭: 이전</yellow>"))) { event ->
            change { it.copy(tool = Editors.cycle(event, tools, spec.tool)) }
        }

        val kind = spec.tool
        if (kind == null) {
            set(SLOT_TIER, Icon.of(Material.GRAY_DYE, "<dark_gray>도구 등급</dark_gray>", listOf("<gray>맞는 도구를 먼저 고르세요.</gray>")))
            set(SLOT_HARVEST, Icon.of(Material.GRAY_DYE, "<dark_gray>도구 규칙</dark_gray>", listOf("<gray>맞는 도구를 먼저 고르세요.</gray>")))
        } else {
            set(SLOT_TIER, Editors.intIcon(Material.DIAMOND, "<aqua>도구 등급: <white>" + ToolGrades.requirement(kind, spec.toolTier) + "</white></aqua>", spec.toolTier, extra = listOf(
                "<gray>0 나무·금 · 1 돌·구리 · 2 철 · 3 다이아몬드 · 4 네더라이트</gray>",
                "<gray>5 부터는 바닐라 도구로 못 캡니다 — 커스텀 곡괭이의</gray>",
                "<gray>아이템 설정에서 <white>채굴 등급</white>을 그만큼 올리세요.</gray>",
            ))) { event ->
                if (Editors.isPrompt(event)) {
                    Editors.promptInt(custom.prompts, viewer, "도구 등급", 0, ToolGrades.MAX_TIER, { open(viewer) }) { value -> change(false) { it.copy(toolTier = value) } }
                    return@set
                }
                change { it.copy(toolTier = (spec.toolTier + Editors.step(event, 1)).coerceIn(0, ToolGrades.MAX_TIER)) }
            }
            set(SLOT_HARVEST, Icon.of(Material.IRON_BARS, "<yellow>맞는 도구가 아니면</yellow>",
                Editors.optionList(Harvest.entries, spec.harvest) { it.label } +
                    listOf("", "<gray>" + spec.harvest.detail + "</gray>", "", "<yellow>▶ 좌클릭: 다음 · 우클릭: 이전</yellow>"))) { event ->
                change { it.copy(harvest = Editors.cycle(event, Harvest.entries, spec.harvest)) }
            }
        }

        set(SLOT_TIMES, Icon.of(Material.CLOCK, "<yellow>캐는 시간</yellow>", timeLines(spec) + listOf(
            "", "<dark_gray>효율·성급함 없이, 땅에 서서 캘 때.</dark_gray>",
        )))
    }

    /** 맨손과 바닐라 도구 재질마다 몇 초 걸리는지. */
    private fun timeLines(spec: BlockSpec): List<String> {
        val kind = spec.tool
        val rows = ArrayList<Pair<String, com.inmc.customitems.block.HeldTool>>()
        rows += "맨손" to com.inmc.customitems.block.HeldTool.HAND
        when {
            kind == null -> Unit
            kind == ToolKind.SHEARS -> rows += "가위" to ToolGrades.held("SHEARS")
            else -> {
                for (prefix in listOf("WOODEN", "STONE", "IRON", "DIAMOND", "NETHERITE")) {
                    val tool = ToolGrades.held(prefix + "_" + kind.name)
                    rows += (ToolGrades.name(tool.tier).substringBefore('·') + " " + kind.label) to tool
                }
                if (spec.toolTier > 4) rows += (spec.toolTier.toString() + " 등급 " + kind.label + " (네더라이트 재질)") to ToolGrades.held("NETHERITE_" + kind.name, spec.toolTier)
            }
        }
        return rows.map { (label, tool) ->
            val ticks = Mining.ticks(spec, tool)
            val time = if (ticks == null) "<red>못 캠</red>" else "<white>" + seconds(ticks) + "초</white>"
            val yields = if (ticks != null && !Mining.yields(spec, tool)) " <dark_gray>(안 나옴)</dark_gray>" else ""
            "<gray>$label: $time$yields</gray>"
        }
    }

    // --- 나오는 것 ---------------------------------------------------------------------

    private fun drawYield(spec: BlockSpec) {
        set(SLOT_DROP, Icon.of(Icon.toggleMaterial(spec.drop), "<yellow>부수면 이 블록이 나옴: " + Icon.toggle(spec.drop) + "</yellow>",
            listOf("<gray>끄면 드랍 표에 적은 것만 나옵니다(광석처럼).</gray>"))) {
            change { it.copy(drop = !it.drop) }
        }
        set(SLOT_DROPS, Icon.of(Material.CHEST, "<gold>드랍 표 <white>" + spec.drops.size + "</white>종</gold>", buildList {
            for (drop in spec.drops.take(6)) add("<gray>· <white>" + drop.item.label() + "</white> " + amount(drop.min, drop.max) + "개 · " + chance(drop.chance) + "</gray>")
            if (spec.drops.size > 6) add("<dark_gray>…</dark_gray>")
            add("<gray>부쉈을 때 확률로 나오는 아이템.</gray>")
            add("")
            add("<yellow>▶ 클릭: 드랍 표</yellow>")
        })) { BlockDropMenu(custom, viewer, id) { open(viewer) }.open(viewer) }
        set(SLOT_SILK, Icon.of(Icon.toggleMaterial(spec.silkTouch), "<yellow>섬세한 손길이면 블록 그대로: " + Icon.toggle(spec.silkTouch) + "</yellow>",
            listOf("<gray>섬세한 손길 도구로 캐면 드랍 표 대신</gray>", "<gray>이 블록이 나오고 경험치는 없습니다(바닐라 광석).</gray>"))) {
            change { it.copy(silkTouch = !it.silkTouch) }
        }
        set(SLOT_FORTUNE, Icon.of(Icon.toggleMaterial(spec.fortune), "<yellow>행운이 개수를 늘림: " + Icon.toggle(spec.fortune) + "</yellow>",
            listOf("<gray>드랍 표의 개수가 바닐라 광석처럼 행운 단계만큼 늡니다.</gray>"))) {
            change { it.copy(fortune = !it.fortune) }
        }
        set(SLOT_EXP, Icon.of(Material.EXPERIENCE_BOTTLE, "<green>경험치: <white>" + amount(spec.expMin, spec.expMax) + "</white></green>", listOf(
            "<gray>맞는 도구로 부쉈을 때 나오는 경험치 구슬.</gray>",
            "<gray>섬세한 손길이면 안 나옵니다(바닐라 광석).</gray>",
            "", "<yellow>▶ 클릭: 적기</yellow>",
        ))) {
            DialogForm("경험치 — $id")
                .line("<gray>부쉈을 때 나오는 경험치 (바닐라: 석탄 0~2 · 다이아몬드 3~7).</gray>")
                .long("min", "최소", spec.expMin.toLong(), 0, MAX_EXP.toLong())
                .long("max", "최대", spec.expMax.toLong(), 0, MAX_EXP.toLong())
                .show(custom.plugin, viewer, onCancel = { open(it) }) { _, values ->
                    val min = values.long("min")?.toInt() ?: 0
                    val max = values.long("max")?.toInt() ?: 0
                    change(false) { it.copy(expMin = minOf(min, max), expMax = maxOf(min, max)) }
                }
        }
    }

    private fun change(reopen: Boolean = true, update: (BlockSpec) -> BlockSpec) =
        mutate(reopen) { current -> current.copy(block = current.block?.let(update)) }

    private fun choice(slot: Int, material: Material, label: String, selected: Boolean, lore: List<String>, click: () -> Unit) {
        set(slot, Icon.of(material, (if (selected) "<green>▶ " else "<white>") + label + (if (selected) "</green>" else "</white>"),
            lore + listOf("", if (selected) "<green>선택됨</green>" else "<yellow>▶ 클릭: 이 방식으로</yellow>"))) { click() }
    }

    private fun select(kind: BlockKind) {
        val item = item() ?: return
        if (item.block?.kind == kind) return
        val state = if (kind.usesState) custom.blocks.allocate(kind, id) ?: run {
            viewer.sendMessage(Text.render("<red>" + kind.label + " 의 빈 자리가 없습니다.</red>"))
            return
        } else ""
        mutate { it.copy(block = (it.block ?: BlockSpec(kind)).copy(kind = kind, state = state)) }
    }

    companion object {
        const val SLOT_OFF = 10
        const val SLOT_SOLID = 12
        const val SLOT_TRANSPARENT = 14
        const val SLOT_ENTITY = 16
        const val SLOT_INFO = 22

        const val SLOT_HARDNESS = 28
        const val SLOT_TOOL = 29
        const val SLOT_TIER = 30
        const val SLOT_HARVEST = 31
        const val SLOT_TIMES = 33
        const val SLOT_EXP = 34

        const val SLOT_DROP = 37
        const val SLOT_DROPS = 38
        const val SLOT_SILK = 39
        const val SLOT_FORTUNE = 40

        const val MAX_EXP = 10_000

        private fun round(value: Double): Double = Math.round(value * 100.0) / 100.0

        internal fun seconds(ticks: Int): String = String.format(java.util.Locale.ROOT, "%.2f", ticks / 20.0).trimEnd('0').trimEnd('.')

        internal fun amount(min: Int, max: Int): String = if (min == max) min.toString() else "$min~$max"

        internal fun chance(value: Double): String = kr.inmc.core.util.Numbers.chance(value) + "%"
    }
}
