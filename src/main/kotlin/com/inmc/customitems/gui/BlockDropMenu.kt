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
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryCloseEvent

/**
 * 블록의 드랍 표 — 부쉈을 때 확률로 나오는 것. **랜덤박스의 보상 등록과 같은 방식**(2026-10-10 사용자 요청 "손에 든 것 말고 urb 처럼"):
 * - **등록 모드**: 빈 칸에 아이템을 올리고(끌기·Shift+클릭) **✔ 등록 확정** — 올린 그대로(개수 포함, 확률 100%) 들어간다. 확정하지 않고 닫거나
 *   모드를 바꾸면 올린 것은 돌려준다.
 * - **제거 모드**: 지울 항목을 눌러 빼내고(다시 누르면 되돌림) **✔ 제거 확정**.
 * - 항목 위에서 **Q · F · Shift+우클릭**: 개수·확률 적기.
 *
 * 순서는 표시 순서일 뿐 굴림은 항목마다 따로다(하나만 고르는 표가 아니다). 아이템은 우리 아이템·MMOItems·바닐라 그대로(core 아이템 해석기).
 */
class BlockDropMenu(
    custom: CustomItems,
    viewer: Player,
    id: String,
    private val removeMode: Boolean = false,
    private val back: () -> Unit,
) : DetailMenu(custom, viewer, id, "<dark_gray>드랍 표 — $id</dark_gray>") {

    /** 칸 → 그 칸에 그린 항목 번호. 그 밖의 목록 칸은 아이템을 올릴 수 있는 빈 칸이다. */
    private val slotToIndex = HashMap<Int, Int>()

    /** 제거 모드에서 빼낸 칸(확정 전). */
    private val stagedRemovals = HashSet<Int>()

    override fun draw() {
        clear()
        slotToIndex.clear()
        val spec = item()?.block ?: return back()
        for ((index, drop) in spec.drops.take(LIST).withIndex()) {
            slotToIndex[index] = index
            val icon = Icon.annotate(
                custom.crafting.resolver.icon(drop.item).stack.clone().also { it.amount = drop.max.coerceIn(1, it.maxStackSize) },
                lore = listOf(
                    "<gray>개수 <white>" + BlockMenu.amount(drop.min, drop.max) + "</white> · 확률 <white>" + BlockMenu.chance(drop.chance) + "</white></gray>",
                    "<dark_gray>" + drop.item.ref.serialize() + "</dark_gray>",
                    "",
                    if (removeMode) "<red>▶ 클릭하여 빼낸 뒤 제거 확정</red>" else "<yellow>▶ Q / F / Shift+우클릭: 개수·확률</yellow>",
                ),
            )
            set(index, if (removeMode && index in stagedRemovals) null else icon) { event ->
                if (removeMode) {
                    // 그림 사본이라 사람에게 넘기지 않는다 — 누르면 칸에서 빠지고, 빈 칸을 다시 누르면 돌아온다.
                    if (!stagedRemovals.add(index)) stagedRemovals.remove(index)
                    inventory.setItem(index, if (index in stagedRemovals) null else icon)
                    viewer.updateInventory()
                    return@set
                }
                when (event.click) {
                    ClickType.DROP, ClickType.CONTROL_DROP, ClickType.SWAP_OFFHAND, ClickType.SHIFT_RIGHT -> edit(index, drop)
                    else -> Unit
                }
            }
        }
        for (slot in LIST until 54) set(slot, Icon.EDGE)

        set(SLOT_INFO, Icon.of(Material.PAPER, "<aqua>드랍 표 <white>" + spec.drops.size + "</white>종</aqua>", listOf(
            "<gray>항목마다 따로 굴립니다 — 확률 100% 는 늘 나옵니다.</gray>",
            "<gray>블록 자신: " + Icon.toggle(spec.drop) + " · 행운: " + Icon.toggle(spec.fortune) + " · 섬세한 손길: " + Icon.toggle(spec.silkTouch) + "</gray>",
            "<gray>맞는 도구가 아니면(도구 규칙에 따라) 아무것도 안 나옵니다.</gray>",
            "",
            "<yellow>Q / F / Shift+우클릭</yellow><gray> : 개수·확률</gray>",
        )))
        set(SLOT_MODE, Icon.of(
            if (removeMode) Material.LAVA_BUCKET else Material.HOPPER,
            if (removeMode) "<red>제거 모드 (켜짐)</red>" else "<gray>제거 모드 (꺼짐)</gray>",
            if (removeMode) listOf(
                "<gray>지울 항목을 클릭해 빼낸 뒤</gray>", "<gray>확인을 누르면 삭제됩니다.</gray>", "<dark_gray>다시 클릭하면 되돌립니다.</dark_gray>",
                "", "<yellow>▶ 클릭하여 등록 모드로</yellow>",
            ) else listOf(
                "<gray>빈 칸에 아이템을 올린 뒤</gray>", "<gray>확인을 누르면 등록됩니다(개수 그대로, 확률 100%).</gray>",
                "", "<yellow>▶ 클릭하여 제거 모드로</yellow>",
            ),
        )) {
            returnStaged()
            BlockDropMenu(custom, viewer, id, !removeMode, back).open(viewer)
        }
        set(SLOT_CONFIRM, Icon.confirm(
            if (removeMode) "<red>✔ 제거 확정</red>" else "<green>✔ 등록 확정</green>",
            listOf("<gray>변경 사항을 저장합니다.</gray>"),
        )) {
            if (removeMode) applyRemovals() else applyAdditions()
            BlockDropMenu(custom, viewer, id, removeMode, back).open(viewer)
        }
        set(Paging.SLOT_BACK, Icon.back()) {
            returnStaged()
            back()
        }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    override fun isSlotEditable(slot: Int): Boolean = !removeMode && slot < LIST && !slotToIndex.containsKey(slot)

    override fun acceptsShiftInsert(): Boolean = !removeMode

    override fun onClose(event: InventoryCloseEvent) {
        returnStaged()
    }

    /** 빈 칸에 올라온 것을 전부 항목으로(개수 그대로 · 확률 100%). 올린 아이템은 등록과 함께 들어간다(랜덤박스와 같다). */
    private fun applyAdditions() {
        val adds = ArrayList<BlockDrop>()
        for (slot in 0 until LIST) {
            if (slotToIndex.containsKey(slot)) continue
            val stack = inventory.getItem(slot) ?: continue
            if (stack.type.isAir) continue
            adds += BlockDrop(custom.crafting.resolver.capture(stack), stack.amount, stack.amount)
            inventory.setItem(slot, null)
        }
        if (adds.isEmpty()) return
        val room = LIST - (item()?.block?.drops?.size ?: 0)
        val taken = adds.take(room.coerceAtLeast(0))
        // 자리가 없어 못 넣은 것은 돌려준다.
        if (taken.size < adds.size) {
            viewer.sendMessage(Text.render("<red>드랍 표는 ${LIST}종까지입니다 — " + (adds.size - taken.size) + "개는 넣지 못했습니다.</red>"))
        }
        change(reopen = false) { it + taken }
        viewer.sendMessage(Text.render("<green>드랍 <white>" + taken.size + "</white>종을 등록했습니다.</green> <gray>Q · F · Shift+우클릭으로 개수·확률을 고치세요.</gray>"))
    }

    private fun applyRemovals() {
        val removed = stagedRemovals.mapNotNull { slotToIndex[it] }.toSet()
        stagedRemovals.clear()
        if (removed.isEmpty()) return
        change(reopen = false) { drops -> drops.filterIndexed { i, _ -> i !in removed } }
        viewer.sendMessage(Text.render("<yellow>드랍 <white>" + removed.size + "</white>종을 지웠습니다.</yellow>"))
    }

    /** 올렸지만 확정하지 않은 것을 돌려준다(닫기 · 모드 바꾸기 · 뒤로 · 입력창). 가방이 차면 발밑에. */
    private fun returnStaged() {
        for (slot in 0 until LIST) {
            if (slotToIndex.containsKey(slot)) continue
            val stack = inventory.getItem(slot) ?: continue
            if (stack.type.isAir) continue
            inventory.setItem(slot, null)
            viewer.inventory.addItem(stack).values.forEach { viewer.world.dropItemNaturally(viewer.location, it) }
        }
    }

    private fun edit(index: Int, drop: BlockDrop) {
        returnStaged()
        DialogForm("드랍 — " + drop.item.label())
            .long("min", "최소 개수", drop.min.toLong(), 0, BlockDrop.MAX_AMOUNT.toLong())
            .long("max", "최대 개수", drop.max.toLong(), 0, BlockDrop.MAX_AMOUNT.toLong())
            .decimal("chance", "확률 (%)", drop.chance, 0.0, 100.0)
            .show(custom.plugin, viewer, onCancel = { open(it) }) { _, values ->
                val min = values.long("min")?.toInt() ?: drop.min
                val max = values.long("max")?.toInt() ?: drop.max
                val chance = values.decimal("chance") ?: drop.chance
                change(reopen = true) { drops ->
                    if (drops.getOrNull(index) != drop) {
                        viewer.sendMessage(Text.render("<red>그 사이 드랍 표가 바뀌었습니다. 다시 골라 주세요.</red>"))
                        return@change drops
                    }
                    drops.toMutableList().also { it[index] = drop.copy(min = minOf(min, max), max = maxOf(min, max), chance = chance) }
                }
            }
    }

    /** @param reopen true 면 이 화면을 새로 연다(입력창에서 돌아올 때). 확정 버튼은 스스로 새로 연다. */
    private fun change(reopen: Boolean, update: (List<BlockDrop>) -> List<BlockDrop>) {
        val current = item() ?: return
        custom.items.put(current.copy(block = current.block?.let { spec: BlockSpec -> spec.copy(drops = update(spec.drops)) }))
        if (reopen) BlockDropMenu(custom, viewer, id, removeMode, back).open(viewer)
    }

    companion object {
        const val LIST = 45
        const val SLOT_INFO = 48
        const val SLOT_MODE = 49
        const val SLOT_CONFIRM = 51
    }
}
