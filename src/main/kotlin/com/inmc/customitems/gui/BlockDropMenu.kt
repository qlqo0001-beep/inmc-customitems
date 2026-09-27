package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.BlockDrop
import com.inmc.customitems.item.BlockSpec
import kr.inmc.core.gui.DialogForm
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * 블록의 드랍 표 — 부쉈을 때 확률로 나오는 것. 손에 든 것을 더하고(우리 아이템·MMOItems·바닐라 그대로), 항목마다 개수·확률을 적는다.
 * 순서는 표시 순서일 뿐 굴림은 항목마다 따로다(하나만 고르는 표가 아니다).
 */
class BlockDropMenu(custom: CustomItems, viewer: Player, id: String, private val back: () -> Unit) :
    DetailMenu(custom, viewer, id, "<dark_gray>드랍 표 — $id</dark_gray>") {

    override fun draw() {
        clear()
        val spec = item()?.block ?: return back()
        for ((index, drop) in spec.drops.take(LIST).withIndex()) {
            val icon = custom.crafting.resolver.icon(drop.item).stack.clone().also { it.amount = drop.max.coerceIn(1, it.maxStackSize) }
            set(index, Icon.annotate(icon, lore = listOf(
                "<gray>개수 <white>" + BlockMenu.amount(drop.min, drop.max) + "</white> · 확률 <white>" + BlockMenu.chance(drop.chance) + "</white></gray>",
                "",
                "<yellow>▶ 좌클릭: 개수·확률 적기</yellow>",
                "<yellow>▶ 우클릭: 빼기 · Shift+좌클릭: 앞으로</yellow>",
            ))) { event ->
                when {
                    event.isRightClick -> change { drops -> drops.filterIndexed { i, _ -> i != index } }
                    event.isShiftClick && index > 0 -> change { drops -> drops.toMutableList().also { it.add(index - 1, it.removeAt(index)) } }
                    else -> edit(index, drop)
                }
            }
        }

        val hand = viewer.inventory.itemInMainHand
        set(SLOT_ADD, if (hand.type.isAir) {
            Icon.of(Material.BARRIER, "<red>더하려면 아이템을 손에 드세요</red>", listOf("<gray>손에 든 그대로(개수 포함) 드랍 표에 들어갑니다.</gray>"))
        } else {
            Icon.of(Material.LIME_DYE, "<green>손에 든 것 더하기</green>", listOf(
                "<gray>" + hand.type.name + " ×" + hand.amount + " — 확률 100%</gray>",
                "<gray>더한 뒤 눌러서 개수·확률을 고치세요.</gray>",
            ))
        }) {
            val held = viewer.inventory.itemInMainHand
            if (held.type.isAir || spec.drops.size >= LIST) return@set
            val stored = custom.crafting.resolver.capture(held)
            change { it + BlockDrop(stored, held.amount, held.amount) }
        }
        set(SLOT_INFO, Icon.of(Material.PAPER, "<aqua>드랍 표</aqua>", listOf(
            "<gray>항목마다 따로 굴립니다 — 확률 100% 는 늘 나옵니다.</gray>",
            "<gray>블록 자신: " + Icon.toggle(spec.drop) + " · 행운: " + Icon.toggle(spec.fortune) + " · 섬세한 손길: " + Icon.toggle(spec.silkTouch) + "</gray>",
            "<gray>맞는 도구가 아니면(도구 규칙에 따라) 아무것도 안 나옵니다.</gray>",
        )))
        set(Paging.SLOT_BACK, Icon.back()) { back() }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun edit(index: Int, drop: BlockDrop) {
        DialogForm("드랍 — " + drop.item.label())
            .long("min", "최소 개수", drop.min.toLong(), 0, BlockDrop.MAX_AMOUNT.toLong())
            .long("max", "최대 개수", drop.max.toLong(), 0, BlockDrop.MAX_AMOUNT.toLong())
            .decimal("chance", "확률 (%)", drop.chance, 0.0, 100.0)
            .show(custom.plugin, viewer, onCancel = { open(it) }) { _, values ->
                val min = values.long("min")?.toInt() ?: drop.min
                val max = values.long("max")?.toInt() ?: drop.max
                val chance = values.decimal("chance") ?: drop.chance
                change(false) { drops ->
                    if (drops.getOrNull(index) != drop) {
                        viewer.sendMessage(Text.render("<red>그 사이 드랍 표가 바뀌었습니다. 다시 골라 주세요.</red>"))
                        return@change drops
                    }
                    drops.toMutableList().also { it[index] = drop.copy(min = minOf(min, max), max = maxOf(min, max), chance = chance) }
                }
            }
    }

    private fun change(reopen: Boolean = true, update: (List<BlockDrop>) -> List<BlockDrop>) =
        mutate(reopen) { current -> current.copy(block = current.block?.let { spec: BlockSpec -> spec.copy(drops = update(spec.drops)) }) }

    private companion object {
        const val LIST = 45
        const val SLOT_INFO = 48
        const val SLOT_ADD = 49
    }
}
