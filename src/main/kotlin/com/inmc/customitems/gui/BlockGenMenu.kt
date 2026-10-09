package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import com.inmc.customitems.block.OreGen
import com.inmc.customitems.item.BlockSpec
import com.inmc.customitems.item.CustomItem
import kr.inmc.core.gui.DialogForm
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.gui.PickMenu
import kr.inmc.core.util.Numbers
import kr.inmc.core.util.Text
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 블록 하나의 **월드 생성**(광맥, 2026-10-09 사용자 요청) — 켜기 · 월드 · 바꿀 블록 · 높이 · 청크 확률 · 광맥 수 · 광맥 크기 · 이 청크에 시험으로 심기.
 * 처음 만들어지는 청크에만 생긴다([com.inmc.customitems.block.OreGenerator]). 엔티티 방식은 안 된다.
 */
class BlockGenMenu(custom: CustomItems, viewer: Player, id: String, private val back: () -> Unit) :
    DetailMenu(custom, viewer, id, "<dark_gray>월드 생성 — $id</dark_gray>") {

    override fun draw() {
        clear()
        val item = item() ?: return ItemTypeMenu(custom, viewer).open(viewer)
        val spec = item.block ?: return back()
        fillEmpty(Icon.EDGE)
        val rule = spec.generation

        if (!spec.kind.usesState) {
            set(SLOT_TOGGLE, Icon.of(Material.BARRIER, "<red>엔티티 방식은 월드 생성을 못 합니다</red>", listOf(
                "<gray>광맥마다 엔티티가 수십 개 생겨 무겁습니다.</gray>",
                "<gray>꽉 찬 · 투명 방식으로 바꾸면 쓸 수 있습니다.</gray>",
            )))
        } else {
            set(SLOT_TOGGLE, Icon.of(Icon.toggleMaterial(rule != null), "<yellow>월드 생성: " + Icon.toggle(rule != null) + "</yellow>", listOf(
                "<gray>처음 만들어지는 청크에 이 블록을 광맥으로 심습니다.</gray>",
                "<gray>이미 가 본 땅에는 생기지 않습니다(바닐라 광석과 같음).</gray>",
                "", if (rule == null) "<yellow>▶ 클릭: 켜기 (지금 있는 월드로 시작)</yellow>" else "<yellow>▶ 클릭: 끄기</yellow>",
            ))) {
                change { current -> if (current == null) OreGen(worlds = listOf(viewer.world.name)) else null }
            }
        }
        set(SLOT_INFO, info(item, spec, rule))
        if (rule != null && spec.kind.usesState) drawRule(item, rule)

        set(Paging.SLOT_BACK, Icon.back()) { back() }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun drawRule(item: CustomItem, rule: OreGen) {
        set(SLOT_WORLDS, Icon.of(Material.GRASS_BLOCK, "<green>월드 <white>" + rule.worlds.size + "</white>개</green>", buildList {
            if (rule.worlds.isEmpty()) add("<red>고른 월드가 없어 아무 데도 안 생깁니다.</red>")
            for (name in rule.worlds) add("<gray>· <white>" + name + "</white>" + (if (Bukkit.getWorld(name) == null) " <dark_gray>(지금 안 켜짐)</dark_gray>" else "") + "</gray>")
            add("")
            add("<yellow>▶ 클릭: 월드 고르기</yellow>")
        })) { pickWorlds() }

        set(SLOT_REPLACE, Icon.of(Material.STONE, "<green>바꿀 블록 <white>" + rule.replace.size + "</white>종</green>", buildList {
            add("<gray>이 블록 자리에만 심습니다(공기·물·다른 광석은 건너뜀).</gray>")
            if (rule.replace.isEmpty()) add("<red>하나도 없어 아무 데도 안 생깁니다.</red>")
            for (material in rule.replace.take(8)) add("<gray>· <white><lang:" + material.translationKey() + "></white></gray>")
            if (rule.replace.size > 8) add("<dark_gray>…</dark_gray>")
            add("")
            add("<yellow>▶ 클릭: 고르기</yellow>")
            add("<yellow>▶ Shift+클릭: 손에 든 블록 더하기</yellow>")
        })) { event ->
            if (event.isShiftClick) addHeld() else pickReplace()
        }

        set(SLOT_HEIGHT, Icon.of(Material.LADDER, "<green>높이 <white>" + rule.minY + " ~ " + rule.maxY + "</white></green>", listOf(
            "<gray>오버월드 땅속은 -64 ~ 60쯤, 심층암은 0 아래.</gray>",
            "<gray>월드 높이 밖은 잘립니다.</gray>",
            "", "<yellow>▶ 클릭: 적기</yellow>",
        ))) {
            DialogForm("높이 — $id")
                .line("<gray>광맥이 시작하고 자라는 높이(블록 y).</gray>")
                .long("min", "가장 낮게", rule.minY.toLong(), OreGen.MIN_Y.toLong(), OreGen.MAX_Y.toLong())
                .long("max", "가장 높게", rule.maxY.toLong(), OreGen.MIN_Y.toLong(), OreGen.MAX_Y.toLong())
                .show(custom.plugin, viewer, onCancel = { open(it) }) { _, values ->
                    val low = values.long("min")?.toInt() ?: rule.minY
                    val high = values.long("max")?.toInt() ?: rule.maxY
                    change(false) { it?.copy(minY = minOf(low, high), maxY = maxOf(low, high)) }
                }
        }

        set(SLOT_CHANCE, Editors.numberIcon(Material.RABBIT_FOOT, "<yellow>청크 확률</yellow>", rule.chunkChance, unit = "%", extra = listOf(
            "<gray>새 청크 하나에 이 광맥들이 생길 확률.</gray>",
        ), stepLabel = "5")) { event ->
            if (Editors.isPrompt(event)) {
                Editors.promptDouble(custom.prompts, viewer, "청크 확률(%)", 0.0, 100.0, { open(viewer) }) { value -> change(false) { it?.copy(chunkChance = round(value)) } }
                return@set
            }
            change { it?.copy(chunkChance = round((rule.chunkChance + Editors.step(event, 5.0)).coerceIn(0.0, 100.0))) }
        }
        set(SLOT_VEINS, Editors.intIcon(Material.IRON_ORE, "<yellow>청크마다 광맥 수</yellow>", rule.veins, unit = "개", extra = listOf(
            "<gray>확률을 넘은 청크에 생기는 광맥 개수.</gray>",
        ))) { event ->
            if (Editors.isPrompt(event)) {
                Editors.promptInt(custom.prompts, viewer, "광맥 수", 1, OreGen.MAX_VEINS, { open(viewer) }) { value -> change(false) { it?.copy(veins = value) } }
                return@set
            }
            change { it?.copy(veins = (rule.veins + Editors.step(event, 1)).coerceIn(1, OreGen.MAX_VEINS)) }
        }
        set(SLOT_SIZE, Editors.intIcon(Material.RAW_IRON_BLOCK, "<yellow>광맥 크기</yellow>", rule.veinSize, unit = "칸", extra = listOf(
            "<gray>광맥 하나가 자라는 칸 수(최대). 바닐라 철 9 · 다이아몬드 4~8.</gray>",
        ))) { event ->
            if (Editors.isPrompt(event)) {
                Editors.promptInt(custom.prompts, viewer, "광맥 크기", 1, OreGen.MAX_VEIN_SIZE, { open(viewer) }) { value -> change(false) { it?.copy(veinSize = value) } }
                return@set
            }
            change { it?.copy(veinSize = (rule.veinSize + Editors.step(event, 1)).coerceIn(1, OreGen.MAX_VEIN_SIZE)) }
        }

        set(SLOT_TEST, Icon.of(Material.GOLDEN_PICKAXE, "<gold>이 청크에 지금 심어 보기</gold>", listOf(
            "<gray>내가 선 청크에 광맥을 확률 없이 한 번 심습니다.</gray>",
            "<red>월드가 실제로 바뀝니다 — 시험 월드에서 쓰세요.</red>",
            "<gray>월드 목록은 보지 않습니다.</gray>",
            "", "<yellow>▶ Shift+클릭: 심기</yellow>",
        ))) { event ->
            if (!event.isShiftClick) return@set
            val (placed, nearest) = custom.oreGenerator.plantHere(viewer, item)
            viewer.sendMessage(Text.render(
                if (placed == 0) "<yellow>심을 자리가 없었습니다 — 이 청크의 그 높이에 바꿀 블록이 없습니다.</yellow>"
                else "<green><white>$placed</white>칸 심었습니다.</green>" + (nearest?.let { " <gray>가장 가까운 곳: <white>" + it.blockX + " " + it.blockY + " " + it.blockZ + "</white></gray>" } ?: ""),
            ))
            refresh()
        }
    }

    private fun info(item: CustomItem, spec: BlockSpec, rule: OreGen?): ItemStack = Icon.of(Material.PAPER, "<aqua>지금 설정</aqua>", buildList {
        if (rule == null) {
            add("<dark_gray>월드 생성이 꺼져 있습니다.</dark_gray>")
            return@buildList
        }
        add("<gray>월드: <white>" + rule.worlds.joinToString(", ").ifBlank { "없음" } + "</white></gray>")
        add("<gray>높이: <white>" + rule.minY + " ~ " + rule.maxY + "</white></gray>")
        add("<gray>청크 확률 <white>" + Numbers.chance(rule.chunkChance) + "%</white> · 광맥 <white>" + rule.veins + "</white>개 × <white>" + rule.veinSize + "</white>칸</gray>")
        add("<gray>청크 하나에 평균 최대 <white>" + Numbers.chance(Math.round(rule.average * 10.0) / 10.0) + "</white>칸</gray>")
        val (blocks, chunks) = custom.oreGenerator.stats(item.id)
        add("<gray>이번에 켜진 뒤 심은 것: <white>$blocks</white>칸 · <white>$chunks</white>청크</gray>")
        if (custom.blocks.updatesDisabled?.get(spec.kind) == false) {
            add("")
            add("<red>Paper 갱신 끄기가 꺼져 있어 심긴 블록의 모양이 옆 블록이 바뀔 때 풀립니다.</red>")
        }
        add("")
        add("<dark_gray>처음 만들어지는 청크에만 생깁니다.</dark_gray>")
    })

    private fun pickWorlds() {
        val rule = current() ?: return
        val options = (Bukkit.getWorlds().map { it.name } + rule.worlds).distinctBy { it.lowercase() }
        PickMenu(
            owner = custom,
            viewer = viewer,
            title = "<dark_gray>월드 — $id</dark_gray>",
            options = options,
            icon = { name -> worldIcon(name) },
            multi = true,
            selected = { val chosen = current()?.worlds.orEmpty(); options.filter { o -> chosen.any { it.equals(o, ignoreCase = true) } }.toSet() },
            back = { open(viewer) },
        ) { name ->
            change(false, reopen = false) { gen ->
                gen?.copy(worlds = if (gen.inWorld(name)) gen.worlds.filterNot { it.equals(name, ignoreCase = true) } else gen.worlds + name)
            }
        }.show()
    }

    private fun worldIcon(name: String): ItemStack {
        val world = Bukkit.getWorld(name)
        val material = when (world?.environment) {
            World.Environment.NETHER -> Material.NETHERRACK
            World.Environment.THE_END -> Material.END_STONE
            null -> Material.GRAY_DYE
            else -> Material.GRASS_BLOCK
        }
        return Icon.of(material, "<white>$name</white>", listOf(
            if (world == null) "<dark_gray>지금 안 켜진 월드</dark_gray>" else "<gray>높이 " + world.minHeight + " ~ " + (world.maxHeight - 1) + "</gray>",
        ))
    }

    private fun pickReplace() {
        val rule = current() ?: return
        val options = (OreGen.COMMON_REPLACE + rule.replace).distinct()
        PickMenu(
            owner = custom,
            viewer = viewer,
            title = "<dark_gray>바꿀 블록 — $id</dark_gray>",
            options = options,
            icon = { material -> Icon.of(material, "<white><lang:" + material.translationKey() + "></white>", listOf("<dark_gray>" + material.name + "</dark_gray>")) },
            multi = true,
            selected = { current()?.replace.orEmpty().toSet() },
            back = { open(viewer) },
        ) { material ->
            change(false, reopen = false) { gen -> gen?.copy(replace = if (material in gen.replace) gen.replace - material else gen.replace + material) }
        }.show()
    }

    private fun addHeld() {
        val material = viewer.inventory.itemInMainHand.type
        if (material.isAir || !material.isBlock) {
            viewer.sendMessage(Text.render("<red>바꿀 블록으로 더할 블록을 손에 드세요.</red>"))
            return
        }
        change { gen -> gen?.copy(replace = (gen.replace + material).distinct()) }
    }

    private fun current(): OreGen? = item()?.block?.generation

    /** @param reopen false 면 이 화면을 다시 열지 않는다(고르는 화면에서 고칠 때). */
    private fun change(redraw: Boolean = true, reopen: Boolean = true, update: (OreGen?) -> OreGen?) {
        val current = item() ?: return
        val block = current.block ?: return
        custom.items.put(current.copy(block = block.copy(generation = update(block.generation))))
        if (!reopen) return
        if (redraw) refresh() else open(viewer)
    }

    companion object {
        const val SLOT_TOGGLE = 10
        const val SLOT_WORLDS = 12
        const val SLOT_REPLACE = 14
        const val SLOT_HEIGHT = 16
        const val SLOT_INFO = 22
        const val SLOT_CHANCE = 29
        const val SLOT_VEINS = 31
        const val SLOT_SIZE = 33
        const val SLOT_TEST = 40

        private fun round(value: Double): Double = Math.round(value * 100.0) / 100.0
    }
}
