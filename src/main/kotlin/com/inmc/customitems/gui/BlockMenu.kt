package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.BlockKind
import com.inmc.customitems.item.BlockSpec
import com.inmc.customitems.pack.PackAssets
import kr.inmc.core.gui.Icon
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * 아이템 한 개의 블록 설정 — 놓는 방식(끔 · 꽉 찬 · 투명 · 엔티티)과 부수면 나오는지.
 *
 * 블록 상태 방식을 고르면 **빈 상태를 그 자리에서 골라 적는다**(`CustomBlocks.allocate`). 다시 고르지 않는다 — 바꾸면 이미 놓인 블록이
 * 다른 블록으로 보인다. 같은 방식을 다시 눌러도 상태는 그대로다.
 */
class BlockMenu(custom: CustomItems, viewer: Player, id: String) :
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
            "<gray>좌클릭 한 번에 부서집니다.</gray>",
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
            set(SLOT_DROP, Icon.of(Icon.toggleMaterial(spec.drop), "<yellow>부수면 이 아이템이 나옴: " + Icon.toggle(spec.drop) + "</yellow>",
                listOf("<gray>끄면 아무것도 안 나옵니다(광석처럼 다른 보상을 줄 때).</gray>"))) {
                mutate { current -> current.copy(block = current.block?.let { it.copy(drop = !it.drop) }) }
            }
        }
        backAndClose()
    }

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
        mutate { it.copy(block = BlockSpec(kind, state, it.block?.drop ?: true)) }
    }

    companion object {
        const val SLOT_OFF = 10
        const val SLOT_SOLID = 12
        const val SLOT_TRANSPARENT = 14
        const val SLOT_ENTITY = 16
        const val SLOT_INFO = 22
        const val SLOT_DROP = 31
    }
}
