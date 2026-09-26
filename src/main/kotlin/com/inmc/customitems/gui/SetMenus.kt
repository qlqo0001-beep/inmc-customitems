package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.ItemSet
import com.inmc.customitems.item.SetBonus
import kr.inmc.core.gui.ConfirmMenu
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.integration.CustomEnchantHook
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player

private val SET_ID = Regex("^[a-z0-9_]{1,32}$")

private fun bonusLines(bonus: SetBonus): List<String> =
    bonus.stats.map { (stat, value) -> stat.line(value) } + bonus.potions.map { (potion, level) -> "<gray>" + potion + " " + level + "</gray>" } +
        listOfNotNull(if (bonus.effects.isEmpty()) null else "<light_purple>인첸트 효과</light_purple> " + CustomEnchantHook.describeEffects(bonus.effects))

/** 아이템 세트 목록. */
class ItemSetListMenu(custom: CustomItems, private val viewer: Player) :
    Menu(custom, 54, Text.renderFlat("<dark_gray>아이템 세트</dark_gray>")) {

    override fun draw() {
        clear()
        for ((index, set) in custom.sets.all().take(LIST).withIndex()) {
            val members = custom.items.all().count { it.set == set.id }
            set(index, Icon.of(Material.CHAINMAIL_CHESTPLATE, "<green>" + set.name + "</green>", listOf(
                "<gray>id <white>" + set.id + "</white> · 아이템 <white>" + members + "</white>개</gray>",
            ) + set.bonuses.entries.sortedBy { it.key }.flatMap { (count, bonus) -> bonusLines(bonus).map { "<dark_gray>[" + count + "벌]</dark_gray> $it" } } +
                listOf("", "<yellow>▶ 클릭: 편집</yellow>"))) { ItemSetEditMenu(custom, viewer, set.id).open(viewer) }
        }
        set(SLOT_ADD, Icon.of(Material.LIME_DYE, "<green>새 세트</green>", listOf("<gray>id 를 적으면 만들어집니다.</gray>"))) {
            Editors.promptText(custom.prompts, viewer, "세트 id", listOf("<gray>소문자 영문·숫자·밑줄. 예: <white>knight</white></gray>"), reopen = { open(viewer) }) { raw ->
                val id = raw.trim().lowercase()
                if (!SET_ID.matches(id) || custom.sets.get(id) != null) {
                    viewer.sendMessage(Text.render("<red>쓸 수 없는 id 입니다: $id</red>"))
                    return@promptText
                }
                custom.sets.put(ItemSet(id, id))
                ItemSetEditMenu(custom, viewer, id).open(viewer)
            }
        }
        set(Paging.SLOT_BACK, Icon.back()) { ItemTypeMenu(custom, viewer).open(viewer) }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private companion object {
        const val LIST = 36
        const val SLOT_ADD = 48
    }
}

/** 세트 하나. 이름과 벌 수별 효과. */
class ItemSetEditMenu(custom: CustomItems, private val viewer: Player, private val id: String) :
    Menu(custom, 54, Text.renderFlat("<dark_gray>세트 — $id</dark_gray>")) {

    private fun set(): ItemSet? = custom.sets.get(id)

    private fun mutate(change: (ItemSet) -> ItemSet) {
        set()?.let { custom.sets.put(change(it)) }
    }

    override fun draw() {
        clear()
        val set = set() ?: return ItemSetListMenu(custom, viewer).open(viewer)
        set(SLOT_NAME, Icon.of(Material.NAME_TAG, "<yellow>이름: <white>" + set.name + "</white></yellow>", listOf("", "<yellow>▶ 클릭: 적기</yellow>"))) {
            Editors.promptText(custom.prompts, viewer, "세트 이름", listOf("<gray>예: <white>기사</white></gray>"), reopen = { open(viewer) }) { raw -> mutate { it.copy(name = raw.trim()) } }
        }
        // 1~10 벌. 효과가 있는 칸은 색 있는 염료로.
        for (count in 1..MAX_PIECES) {
            val bonus = set.bonuses[count]
            set(FIRST_COUNT + count - 1, Icon.of(if (bonus != null) Material.LIME_DYE else Material.GRAY_DYE,
                "<yellow>" + count + "벌 효과</yellow>", (bonus?.let(::bonusLines) ?: listOf("<gray>없음</gray>")) + listOf("", "<yellow>▶ 클릭: 편집</yellow>"))) {
                SetBonusMenu(custom, viewer, id, count).open(viewer)
            }
        }
        val members = custom.items.all().filter { it.set == id }
        set(SLOT_MEMBERS, Icon.of(Material.CHEST, "<yellow>이 세트의 아이템 <white>" + members.size + "</white>개</yellow>",
            members.take(10).map { "<gray>· " + it.label() + "</gray>" } + listOf("<gray>아이템 설정 화면에서 세트를 고릅니다.</gray>")))
        set(SLOT_DELETE, Icon.of(Material.LAVA_BUCKET, "<red>세트 지우기</red>", listOf("<gray>아이템은 남고 세트만 사라집니다.</gray>"))) {
            ConfirmMenu(custom, "<red>세트 $id 를 지울까요?</red>", onConfirm = {
                custom.sets.remove(id)
                ItemSetListMenu(custom, viewer).open(viewer)
            }, onCancel = { open(viewer) }).open(viewer)
        }
        set(Paging.SLOT_BACK, Icon.back()) { ItemSetListMenu(custom, viewer).open(viewer) }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private companion object {
        const val SLOT_NAME = 4
        const val FIRST_COUNT = 19
        const val MAX_PIECES = 7
        const val SLOT_MEMBERS = 31
        const val SLOT_DELETE = 51
    }
}

/** 한 단계(몇 벌)의 효과. 능력치·물약·인첸트 효과(화면은 인첸트 플러그인이 그린다). */
class SetBonusMenu(custom: CustomItems, private val viewer: Player, private val id: String, private val count: Int) :
    Menu(custom, 54, Text.renderFlat("<dark_gray>세트 $id — ${count}벌</dark_gray>")) {

    private fun bonus(): SetBonus? = custom.sets.get(id)?.let { it.bonuses[count] ?: SetBonus() }

    private fun change(transform: (SetBonus) -> SetBonus) {
        val set = custom.sets.get(id) ?: return
        val next = transform(set.bonuses[count] ?: SetBonus())
        val bonuses = if (next.isEmpty) set.bonuses - count else set.bonuses + (count to next)
        custom.sets.put(set.copy(bonuses = bonuses))
    }

    override fun draw() {
        clear()
        val bonus = bonus() ?: return ItemSetListMenu(custom, viewer).open(viewer)
        var slot = 0
        for ((stat, value) in bonus.stats) {
            if (slot >= LIST) break
            set(slot++, Icon.of(Material.MAGENTA_DYE, stat.line(value), listOf("", "<yellow>▶ 좌클릭: 값 · <red>우클릭: 빼기</red></yellow>"))) { event ->
                if (event.isRightClick) {
                    change { it.copy(stats = it.stats - stat) }
                    refresh()
                } else {
                    askStat(stat)
                }
            }
        }
        for ((potion, level) in bonus.potions) {
            if (slot >= LIST) break
            set(slot++, Icon.of(Material.POTION, "<gray>" + potion + " " + level + "</gray>", listOf("<gray>입는 동안 걸립니다.</gray>", "", "<red>▶ 우클릭: 빼기</red>"))) { event ->
                if (!event.isRightClick) return@set
                change { it.copy(potions = it.potions - potion) }
                refresh()
            }
        }
        set(SLOT_ADD_STAT, Icon.of(Material.LIME_DYE, "<green>능력치 추가</green>", listOf("<gray>바닐라 속성도 사람에게 직접 붙습니다.</gray>"))) {
            StatPickMenu(custom, viewer, "<dark_gray>세트 능력치</dark_gray>", back = { open(viewer) }) { askStat(it) }.open(viewer)
        }
        set(SLOT_ADD_POTION, Icon.of(Material.BREWING_STAND, "<green>물약 추가</green>", listOf("<gray>예: <white>speed 1</white> (신속 I)</gray>"))) {
            Editors.promptText(custom.prompts, viewer, "물약 단계", listOf("<gray><white>물약 단계</white> 모양. 예: <white>speed 1</white> · <white>night_vision 1</white></gray>"), reopen = { open(viewer) }) { raw ->
                val name = raw.trim().substringBefore(' ').lowercase()
                val level = raw.trim().substringAfter(' ', "1").trim().toIntOrNull()?.coerceIn(1, 255) ?: 1
                if (com.inmc.customitems.item.Registries.potionEffect(name) == null) {
                    viewer.sendMessage(Text.render("<red>그런 물약이 없습니다: $name</red>"))
                    return@promptText
                }
                change { it.copy(potions = it.potions + (name to level)) }
            }
        }
        set(SLOT_EFFECTS, effectsIcon(bonus)) {
            val opened = CustomEnchantHook.editEffects(
                viewer, "$id ${count}벌", bonus.effects,
                save = { tree -> change { it.copy(effects = tree) } },
                back = { open(viewer) },
            )
            if (!opened) viewer.sendMessage(Text.render("<red>인첸트 플러그인(inmc-enchants)이 없어 인첸트 효과를 고칠 수 없습니다.</red>"))
        }
        set(Paging.SLOT_BACK, Icon.back()) { ItemSetEditMenu(custom, viewer, id).open(viewer) }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun effectsIcon(bonus: SetBonus) = Icon.of(
        if (bonus.effects.isEmpty()) Material.BLAZE_POWDER else Material.ENCHANTED_BOOK,
        "<light_purple>인첸트 효과</light_purple>",
        listOfNotNull(
            if (bonus.effects.isEmpty()) "<gray>없음</gray>" else CustomEnchantHook.describeEffects(bonus.effects).ifBlank { "<gray>있음</gray>" },
            "<gray>인첸트와 같은 효과 줄(피해 증가·물약·불태우기 …)을</gray>",
            "<gray>공격·방어·채굴 같은 발동 조건마다 적습니다.</gray>",
            if (CustomEnchantHook.isEnabled) null else "<red>인첸트 플러그인이 없어 돌지 않습니다</red>",
            "",
            "<yellow>▶ 클릭: 편집</yellow>",
        ),
    )

    private fun askStat(stat: com.inmc.customitems.item.Stat) {
        Editors.promptDouble(custom.prompts, viewer, stat.display, -StatsMenu.MAX, StatsMenu.MAX, reopen = { open(viewer) }) { value ->
            change { if (value == 0.0) it.copy(stats = it.stats - stat) else it.copy(stats = it.stats + (stat to value)) }
        }
    }

    private companion object {
        const val LIST = 45
        const val SLOT_ADD_STAT = 48
        const val SLOT_EFFECTS = 49
        const val SLOT_ADD_POTION = 50
    }
}

/** 아이템이 속할 세트 고르기. */
class SetChooseMenu(custom: CustomItems, viewer: Player, id: String) :
    DetailMenu(custom, viewer, id, "<dark_gray>세트 고르기 — $id</dark_gray>") {

    override fun draw() {
        clear()
        val item = item() ?: return ItemTypeMenu(custom, viewer).open(viewer)
        set(0, Icon.of(Material.BARRIER, "<yellow>세트 없음</yellow>", if (item.set.isBlank()) listOf("<green>지금 이것</green>") else emptyList())) {
            mutate(false) { it.copy(set = "") }
            ItemEditMenu(custom, viewer, id).open(viewer)
        }
        for ((index, set) in custom.sets.all().take(LIST).withIndex()) {
            set(index + 1, Icon.of(Material.CHAINMAIL_CHESTPLATE, "<green>" + set.name + "</green>",
                listOf("<gray>id " + set.id + "</gray>") + (if (item.set == set.id) listOf("<green>지금 이것</green>") else emptyList()))) {
                mutate(false) { it.copy(set = set.id) }
                ItemEditMenu(custom, viewer, id).open(viewer)
            }
        }
        backAndClose()
    }

    private companion object {
        const val LIST = 44
    }
}
