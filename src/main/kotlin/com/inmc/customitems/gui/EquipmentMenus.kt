package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import com.inmc.customitems.player.EquipmentStore
import com.inmc.customitems.player.EquipmentStore.Group
import com.inmc.customitems.util.Ph
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent

/**
 * 장착 화면(`/장비`) — 장신구·부적·유물을 한 줄씩.
 *
 * **바닐라가 아이템을 옮기게 두지 않는다.** 칸의 진짜 내용물은 [EquipmentStore] 에 있고 이 창은 그 그림일 뿐이다.
 * 바닐라 이동(끌기·숫자키·두 번 클릭 모으기)을 허락하면 창과 저장소가 어긋나 복사된다. 그래서 창의 클릭은
 * 전부 취소하고, 넣고 빼는 것은 여기서 직접 한 뒤 저장소에 쓰고 다시 그린다. 칸마다 한 개씩만 들어간다.
 */
class EquipmentMenu(custom: CustomItems, private val viewer: Player) :
    Menu(custom, 54, Text.renderFlat("<dark_gray>장비</dark_gray>")) {

    private val store get() = custom.equipment

    override fun draw() {
        clear()
        for (group in Group.entries) {
            val row = ROWS.getValue(group)
            val capacity = store.capacity(viewer, group)
            set(row * 9, Icon.of(group.icon, "<gold>" + group.display + "</gold>", listOf(
                "<gray>열린 칸 <white>" + capacity + "</white> / " + EquipmentStore.MAX + "</gray>",
                "<gray>" + group.display + " 종류만 넣을 수 있습니다.</gray>",
                "<gray>끌어다 놓거나 가방에서 Shift+클릭.</gray>",
            ) + if (group == Group.ACCESSORY) emptyList() else listOf("<dark_gray>가방에 든 것과 함께 효과가 납니다.</dark_gray>")))
            for (index in 0 until EquipmentStore.MAX) {
                val stored = store.get(viewer.uniqueId, group, index)
                val slot = row * 9 + 1 + index
                set(slot, when {
                    stored != null && index >= capacity -> Icon.annotate(stored.clone(), lore = listOf("", "<red>잠긴 칸 — 효과가 없습니다. 꺼내기만 됩니다.</red>"))
                    stored != null -> Icon.annotate(stored.clone(), lore = listOf("", "<yellow>▶ 클릭: 꺼내기 · Shift: 가방으로</yellow>"))
                    index >= capacity -> Icon.of(Material.IRON_BARS, "<dark_gray>잠긴 칸</dark_gray>", listOf("<gray>권한이 있어야 열립니다.</gray>"))
                    // 열린 빈 칸은 비워 둔다 — 평범한 빈 칸이 "여기 넣으면 된다"를 가장 잘 말한다.
                    else -> null
                })
            }
        }
        for (row in listOf(1, 3, 5)) for (column in 0 until 9) set(row * 9 + column, Icon.EDGE)
        set(SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    /** 칸 번호 → (종류, 칸). 장착 칸이 아니면 null. */
    private fun cell(raw: Int): Pair<Group, Int>? {
        val group = ROWS.entries.firstOrNull { it.value == raw / 9 }?.key ?: return null
        val index = raw % 9 - 1
        return if (index in 0 until EquipmentStore.MAX) group to index else null
    }

    override fun handleClick(event: InventoryClickEvent) {
        val raw = event.rawSlot
        if (raw !in 0 until size) {
            // 자기 가방: 평소처럼 두되, 창으로 넘어오는 것(Shift)과 창까지 긁어 모으는 것(두 번 클릭)은 우리가 한다.
            when {
                event.isShiftClick -> {
                    event.isCancelled = true
                    shiftIn(event)
                }
                event.action == InventoryAction.COLLECT_TO_CURSOR -> event.isCancelled = true
            }
            return
        }
        event.isCancelled = true
        val (group, index) = cell(raw) ?: return super.handleClick(event)
        // 숫자키·가운데 클릭·버리기는 받지 않는다 — 빈 커서로 보여 "꺼내기"가 되면 뜻밖의 동작이다.
        if (event.click !in HANDLED) return
        val stored = store.get(viewer.uniqueId, group, index)
        val cursor = event.view.cursor.takeIf { !it.type.isAir }

        if (cursor == null) {
            if (stored == null) return
            if (event.isShiftClick) {
                // 가방에 다 안 들어가면 칸에 그대로 둔다 — 반만 옮기면 나머지가 사라진다.
                if (viewer.inventory.addItem(stored.clone()).isNotEmpty()) return custom.messages.send(viewer, "equip-bag-full")
            } else {
                event.view.setCursor(stored.clone())
            }
            store.put(viewer.uniqueId, group, index, null)
            changed()
            return
        }

        if (index >= store.capacity(viewer, group)) return custom.messages.send(viewer, "equip-locked")
        val definition = custom.items.identify(cursor)
        if (definition == null || definition.type != group.type) return custom.messages.send(viewer, "equip-wrong-type", Ph.of().value(group.display))
        // 칸에 이미 있으면 맞바꾼다. 커서에 여러 개면 맞바꿀 수 없다(남는 것을 둘 데가 없다).
        if (stored != null && cursor.amount > 1) return
        val one = cursor.clone().also { it.amount = 1 }
        val rest = cursor.clone().also { it.amount -= 1 }
        event.view.setCursor(stored?.clone() ?: rest.takeIf { it.amount > 0 })
        store.put(viewer.uniqueId, group, index, one)
        changed()
    }

    /** 가방에서 Shift+클릭 — 그 종류의 빈 칸 첫 자리에 하나. */
    private fun shiftIn(event: InventoryClickEvent) {
        val stack = event.currentItem?.takeIf { !it.type.isAir } ?: return
        val definition = custom.items.identify(stack) ?: return
        val group = Group.of(definition.type) ?: return
        val capacity = store.capacity(viewer, group)
        val index = (0 until capacity).firstOrNull { store.get(viewer.uniqueId, group, it) == null }
            ?: return custom.messages.send(viewer, "equip-full", Ph.of().value(group.display))
        store.put(viewer.uniqueId, group, index, stack.clone().also { it.amount = 1 })
        event.currentItem = stack.clone().also { it.amount -= 1 }.takeIf { it.amount > 0 }
        changed()
    }

    private fun changed() {
        viewer.playSound(viewer.location, Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.8f, 1.2f)
        custom.stats.invalidate(viewer)
        refresh()
    }

    private companion object {
        val ROWS = mapOf(Group.ACCESSORY to 0, Group.TALISMAN to 2, Group.RELIC to 4)
        val HANDLED = setOf(ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT)
        const val SLOT_CLOSE = 53
    }
}

/** 장착 칸의 기본 수(관리). */
class EquipmentSettingsMenu(custom: CustomItems, private val viewer: Player) :
    Menu(custom, 27, Text.renderFlat("<dark_gray>장착 칸 설정</dark_gray>")) {

    override fun draw() {
        clear()
        for ((group, slot) in Group.entries.zip(SLOTS)) {
            val count = custom.equipmentSettings.slots(group)
            set(slot, Editors.intIcon(group.icon, "<yellow>" + group.display + " 기본 칸</yellow>", count, extra = listOf(
                "<gray>0~" + EquipmentStore.MAX + ". 권한 <white>" + group.permission(EquipmentStore.MAX) + "</white> 처럼</gray>",
                "<gray>더 열 수 있습니다(기본값보다 클 때만).</gray>",
            ))) { event ->
                custom.equipmentSettings.set(group, count + Editors.step(event, 1))
                refresh()
            }
        }
        val outside = custom.equipmentSettings.inventoryEffects
        set(SLOT_INVENTORY, Icon.of(
            if (outside) Material.CHEST else Material.ENDER_CHEST,
            "<yellow>가방·손에서도 효과: " + Icon.toggle(outside) + "</yellow>",
            listOf(
                "<gray>끄면 장신구·부적·유물은 /장비 칸에 끼워야만</gray>",
                "<gray>능력치·기능이 납니다(가방·손에 든 것은 멈춤).</gray>",
                "<gray>아이템마다 따로 정할 수도 있습니다</gray>",
                "<gray>(아이템 설정 → 효과가 나는 곳).</gray>",
                "", "<yellow>▶ 클릭: 전환</yellow>",
            ),
        )) {
            custom.equipmentSettings.setInventoryEffects(!outside)
            refresh()
        }
        set(SLOT_BACK, Icon.back()) { ItemTypeMenu(custom, viewer).open(viewer) }
    }

    private companion object {
        val SLOTS = listOf(11, 13, 15)
        const val SLOT_INVENTORY = 4
        const val SLOT_BACK = 22
    }
}

/** 칸 수 한 줄 요약(첫 화면 아이콘). */
internal fun equipmentSummary(custom: CustomItems): List<String> =
    Group.entries.map { "<gray>" + it.display + " 기본 <white>" + custom.equipmentSettings.slots(it) + "</white>칸</gray>" } +
        ("<gray>가방·손에서도 효과: " + Icon.toggle(custom.equipmentSettings.inventoryEffects) + "</gray>")
