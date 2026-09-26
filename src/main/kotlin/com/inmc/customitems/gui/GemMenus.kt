package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.GemSpec
import com.inmc.customitems.item.ItemBuilder
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import org.bukkit.Material
import org.bukkit.entity.Player

/** 소켓 색에 어울리는 염료. 모르는 색은 흰색. */
private fun dyeOf(color: String): Material =
    Material.matchMaterial(color.uppercase() + "_DYE") ?: if (color == GemSpec.ANY) Material.PRISMARINE_SHARD else Material.WHITE_DYE

/**
 * 소켓. 색은 자유 글(`red` · `blue` …)이고 `any` 는 아무 보석이나 받는다. 보석 쪽 색과 같아야 박힌다.
 * 이미 나간 아이템에 소켓을 늘리면 늘어난 칸은 빈 소켓으로 보인다.
 */
class SocketMenu(custom: CustomItems, viewer: Player, id: String) :
    DetailMenu(custom, viewer, id, "<dark_gray>소켓 — $id</dark_gray>") {

    override fun draw() {
        clear()
        val item = item() ?: return ItemTypeMenu(custom, viewer).open(viewer)
        for ((index, color) in item.sockets.take(LIST).withIndex()) {
            set(index, Icon.of(dyeOf(color), "<yellow>" + (index + 1) + "번 소켓: <white>" + ItemBuilder.socketLabel(color) + "</white></yellow>",
                listOf("", "<yellow>▶ 좌클릭: 색 바꾸기 · <red>우클릭: 빼기</red></yellow>"))) { event ->
                if (event.isRightClick) {
                    mutate { it.copy(sockets = it.sockets.filterIndexed { i, _ -> i != index }) }
                    return@set
                }
                askColor { color -> mutate(false) { it.copy(sockets = it.sockets.mapIndexed { i, c -> if (i == index) color else c }) } }
            }
        }
        set(SLOT_ADD, Icon.of(Material.LIME_DYE, "<green>소켓 추가</green>", listOf("<gray>색을 적습니다. <white>any</white> 는 아무 보석이나.</gray>"))) {
            askColor { color -> mutate(false) { it.copy(sockets = it.sockets + color) } }
        }
        set(SLOT_INFO, Icon.of(Material.BOOK, "<yellow>소켓이란</yellow>", listOf(
            "<gray>보석을 아이템 위에 끌어다 놓으면 맞는 빈 소켓에 박힙니다.</gray>",
            "<gray>박힌 보석은 자기 능력치를 더합니다.</gray>",
            "<gray>보석은 그 아이템의 <white>보석</white> 설정에서 만듭니다.</gray>",
        )))
        backAndClose()
    }

    private fun askColor(apply: (String) -> Unit) {
        Editors.promptText(custom.prompts, viewer, "소켓 색", listOf("<gray>예: <white>red · blue · green · yellow · any</white></gray>"), reopen = { open(viewer) }) { raw ->
            raw.trim().lowercase().takeIf { it.isNotEmpty() && !it.contains(',') }?.let(apply)
        }
    }

    private companion object {
        const val LIST = 36
        const val SLOT_ADD = 48
        const val SLOT_INFO = 49
    }
}

/** 이 아이템을 보석으로 쓰는 설정. 박으면 이 아이템의 기준 능력치가 더해진다. */
class GemMenu(custom: CustomItems, viewer: Player, id: String) :
    DetailMenu(custom, viewer, id, "<dark_gray>보석 — $id</dark_gray>") {

    override fun draw() {
        clear()
        val item = item() ?: return ItemTypeMenu(custom, viewer).open(viewer)
        val gem = item.gem
        set(SLOT_TOGGLE, Icon.of(Icon.toggleMaterial(gem != null), "<yellow>보석으로 쓰기: " + Icon.toggle(gem != null) + "</yellow>",
            listOf("<gray>켜면 소켓 아이템 위에 끌어다 놓아 박습니다.</gray>", "<gray>더하는 능력치는 이 아이템의 <white>능력치</white> 화면 값입니다.</gray>"))) {
            mutate { it.copy(gem = if (it.gem == null) GemSpec() else null) }
        }
        if (gem != null) {
            set(SLOT_COLOR, Icon.of(dyeOf(gem.color), "<yellow>맞는 소켓: <white>" + ItemBuilder.socketLabel(gem.color) + "</white></yellow>", listOf("", "<yellow>▶ 클릭: 적기</yellow>"))) {
                Editors.promptText(custom.prompts, viewer, "보석 색", listOf("<gray>소켓 색과 같아야 박힙니다. <white>any</white> 는 아무 소켓에나.</gray>"), reopen = { open(viewer) }) { raw ->
                    raw.trim().lowercase().takeIf { it.isNotEmpty() }?.let { color -> mutate(false) { it.copy(gem = it.gem?.copy(color = color)) } }
                }
            }
            set(SLOT_CHANCE, Editors.numberIcon(Material.RABBIT_FOOT, "<yellow>성공률</yellow>", gem.chance, unit = "%", extra = listOf("<gray>실패하면 보석만 깨집니다.</gray>"))) { event ->
                if (Editors.isPrompt(event)) {
                    Editors.promptDouble(custom.prompts, viewer, "성공률(%)", 0.0, 100.0, reopen = { open(viewer) }) { v -> mutate(false) { it.copy(gem = it.gem?.copy(chance = v)) } }
                    return@set
                }
                mutate { it.copy(gem = it.gem?.copy(chance = (gem.chance + Editors.step(event, 5.0)).coerceIn(0.0, 100.0))) }
            }
        }
        backAndClose()
    }

    private companion object {
        const val SLOT_TOGGLE = 11
        const val SLOT_COLOR = 13
        const val SLOT_CHANCE = 15
    }
}
