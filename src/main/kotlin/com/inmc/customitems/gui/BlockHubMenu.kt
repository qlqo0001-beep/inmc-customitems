package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import com.inmc.customitems.block.BlockStates
import com.inmc.customitems.block.ToolGrades
import com.inmc.customitems.item.BlockKind
import com.inmc.customitems.item.CustomItem
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 커스텀 블록 모아 보기 — 블록으로 놓이는 아이템 전부, 방식별 남은 칸, Paper 갱신 끄기, 옛 ItemsAdder 블록 옮기기.
 * 블록도 아이템이다: 만드는 것은 아이템 설정의 "블록" 칸이고, 여기서 누르면 그 블록 설정으로 간다.
 */
class BlockHubMenu(custom: CustomItems, private val viewer: Player, private var page: Int = 0) :
    Menu(custom, 54, Text.renderFlat("<dark_gray>커스텀 블록</dark_gray>")) {

    override fun draw() {
        clear()
        val blocks = custom.items.all().filter { it.block != null }.sortedBy { it.id }
        page = Paging.clamp(page, blocks.size)
        for ((index, item) in Paging.slice(blocks, page).withIndex()) {
            set(index, icon(item)) { BlockMenu(custom, viewer, item.id) { BlockHubMenu(custom, viewer, page).open(viewer) }.open(viewer) }
        }
        if (page > 0) set(Paging.SLOT_PREV, Icon.prevPage()) {
            page--
            refresh()
        }
        if (page < Paging.pageCount(blocks.size) - 1) set(Paging.SLOT_NEXT, Icon.nextPage()) {
            page++
            refresh()
        }

        set(SLOT_STATUS, Icon.of(Material.NOTE_BLOCK, "<aqua>블록 <white>" + blocks.size + "</white>개</aqua>", buildList {
            for (kind in BlockKind.entries) {
                val count = blocks.count { it.block?.kind == kind }
                val room = when (kind) {
                    BlockKind.ENTITY -> "제한 없음"
                    else -> {
                        // allocate 와 같은 셈 — 우리 아이템과 sources/ 의 팩이 쓰는 칸을 뺀 것.
                        val taken = custom.items.blockStatesOf(kind) + custom.blocks.sourceStates[kind]?.keys.orEmpty()
                        "빈 자리 " + BlockStates.candidates(kind).count { it !in taken } + "칸"
                    }
                }
                add("<gray>" + kind.label + ": <white>" + count + "</white>개 · " + room + "</gray>")
            }
            add("")
            val flags = custom.blocks.updatesDisabled
            for (kind in listOf(BlockKind.SOLID, BlockKind.TRANSPARENT)) {
                add("<gray>" + custom.blocks.flagName(kind) + ": " + when (flags?.get(kind)) {
                    true -> "<green>켜짐</green>"
                    false -> "<red>꺼짐 — 블록 상태 방식이면 켜야 합니다</red>"
                    null -> "<white>알 수 없음</white>"
                } + "</gray>")
            }
            add("<dark_gray>config/paper-global.yml 의 block-updates</dark_gray>")
        }))
        set(SLOT_HELP, Icon.of(Material.BOOK, "<yellow>블록 만들기</yellow>", listOf(
            "<gray>블록도 아이템입니다. 아이템 설정 화면의</gray>",
            "<gray><white>블록</white> 칸에서 방식을 고르면 여기에 나타납니다.</gray>",
            "",
            "<gray>단단함·맞는 도구·도구 등급으로 캐는 시간과</gray>",
            "<gray>캘 수 있는 도구를, 드랍 표로 나오는 것을 정합니다.</gray>",
            "<gray>다이아몬드보다 높은 등급(5~)은 커스텀 곡괭이의</gray>",
            "<gray><white>채굴 등급</white>으로 캡니다.</gray>",
        )))
        set(SLOT_IMPORT, Icon.of(Material.CHORUS_PLANT, "<gold>옛 ItemsAdder 블록 옮기기</gold>", listOf(
            "<gray>plugins/ItemsAdder/contents 의 블록 정의를</gray>",
            "<gray>커스텀아이템 블록으로 옮깁니다(꽉 찬 · 투명).</gray>",
            "<gray>상태는 팩(sources/)이 쓰던 그대로라 월드에 놓인</gray>",
            "<gray>옛 블록이 새 아이템으로 이어집니다.</gray>",
            "<gray>단단함과 캘 수 있는 도구(break_tools_whitelist)도 옮깁니다.</gray>",
            "<dark_gray>같은 이름이 있으면 건너뜁니다 — 여러 번 눌러도 됩니다.</dark_gray>",
            "<gray>광석 생성(worlds_populators)도 그 블록의 월드 생성으로 옮깁니다.</gray>",
            "<dark_gray>소리·부술 때 명령어는 안 옮깁니다.</dark_gray>",
            "", "<yellow>▶ 클릭: 옮기기</yellow>",
        ))) { importItemsAdder() }
        set(Paging.SLOT_BACK, Icon.back()) { ItemTypeMenu(custom, viewer).open(viewer) }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun icon(item: CustomItem): ItemStack {
        val spec = item.block!!
        val stack = custom.items.create(item.id) ?: ItemStack(item.material)
        return Icon.annotate(stack, lore = buildList {
            add("<gray>" + item.id + " · " + spec.kind.label + "</gray>")
            add("<gray>단단함 <white>" + kr.inmc.core.util.Numbers.chance(spec.hardness) + "</white>" +
                (spec.tool?.let { " · <white>" + ToolGrades.requirement(it, spec.toolTier) + "</white>" } ?: "") + "</gray>")
            add("<gray>나오는 것: " + buildList {
                if (spec.drop) add("블록 자신")
                if (spec.drops.isNotEmpty()) add("드랍 표 " + spec.drops.size + "종")
            }.ifEmpty { listOf("없음") }.joinToString(" · ") + "</gray>")
            spec.generation?.let { add("<gold>월드 생성: <white>" + it.worlds.joinToString(", ").ifBlank { "월드 없음" } + "</white> · " + it.minY + "~" + it.maxY + "</gold>") }
            add("")
            add("<yellow>▶ 클릭: 블록 설정</yellow>")
        })
    }

    private fun importItemsAdder() {
        viewer.sendMessage(Text.render("<gray>옛 ItemsAdder 블록을 읽는 중…</gray>"))
        custom.blocks.importItemsAdder { report ->
            if (!report.contentsFound) {
                viewer.sendMessage(Text.render("<red>plugins/ItemsAdder/contents 폴더가 없습니다.</red>"))
                return@importItemsAdder
            }
            viewer.sendMessage(Text.render("<green>블록 <white>" + report.added.size + "</white>개를 옮겼습니다.</green> <gray>리소스팩을 다시 만드세요.</gray>"))
            if (report.generation.isNotEmpty()) viewer.sendMessage(Text.render("<green>광석 생성 <white>" + report.generation.size + "</white>개를 옮겼습니다: <white>" + report.generation.take(6).joinToString(", ") + "</white></green>"))
            if (report.skipped.isNotEmpty()) viewer.sendMessage(Text.render("<gray>건너뜀 " + report.skipped.size + "개: <white>" + report.skipped.take(6).joinToString(", ") + (if (report.skipped.size > 6) " …" else "") + "</white></gray>"))
            if (report.noState.isNotEmpty()) viewer.sendMessage(Text.render("<yellow>팩에서 상태를 못 찾음 " + report.noState.size + "개: <white>" + report.noState.take(6).joinToString(", ") + "</white></yellow>"))
            if (report.unsupported.isNotEmpty()) viewer.sendMessage(Text.render("<yellow>이 방식은 없음 " + report.unsupported.size + "개: <white>" + report.unsupported.take(6).joinToString(", ") + "</white></yellow>"))
            refresh()
        }
    }

    private companion object {
        const val SLOT_STATUS = 48
        const val SLOT_HELP = 49
        const val SLOT_IMPORT = 50
    }
}
