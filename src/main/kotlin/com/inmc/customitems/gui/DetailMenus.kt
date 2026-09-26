package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.Registries
import kr.inmc.core.gui.ConfirmMenu
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.integration.CustomEnchantHook
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack

/** 편집 화면들이 공유하는 것. 전부 id 를 들고 다니며 정의를 매번 다시 찾는다. */
abstract class DetailMenu(
    custom: CustomItems,
    protected val viewer: Player,
    protected val id: String,
    title: String,
) : Menu(custom, 54, Text.renderFlat(title)) {

    protected fun item(): CustomItem? = custom.items.get(id)

    protected fun mutate(reopen: Boolean = true, change: (CustomItem) -> CustomItem) {
        val current = item() ?: return
        custom.items.put(change(current))
        if (reopen) refresh() else open(viewer)
    }

    protected fun backAndClose() {
        set(Paging.SLOT_BACK, Icon.back()) { ItemEditMenu(custom, viewer, id).open(viewer) }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }
}

/**
 * 설명(로어) 편집.
 *
 * **줄 단위로 고친다.** 여러 줄을 한 번에 입력받으면 채팅으로는 줄바꿈을 넣을 수가 없고,
 * 그렇다고 `|` 같은 구분자를 쓰면 그 문자를 설명에 못 쓴다.
 *
 * 여기서 쓰는 것은 관리자가 직접 적는 설명뿐이다 — 등급·능력치·기능 줄은
 * [com.inmc.customitems.item.ItemBuilder] 가 알아서 붙인다.
 */
class LoreMenu(custom: CustomItems, viewer: Player, id: String) :
    DetailMenu(custom, viewer, id, "<dark_gray>설명 — $id</dark_gray>") {

    override fun draw() {
        clear()
        val item = item() ?: run {
            ItemTypeMenu(custom, viewer).open(viewer)
            return
        }

        for ((index, line) in item.lore.withIndex()) {
            if (index >= Paging.PER_PAGE) break
            set(index, lineIcon(index, line)) { event ->
                when {
                    event.isRightClick -> removeAt(index)
                    event.isShiftClick -> moveUp(index)
                    else -> editAt(index, line)
                }
            }
        }

        set(SLOT_ADD, addIcon()) { promptAdd() }
        set(
            SLOT_CLEAR,
            Icon.of(Material.BARRIER, "<red>전부 지우기</red>", listOf("<gray>" + item.lore.size + "줄</gray>")),
        ) {
            ConfirmMenu(
                owner = custom,
                question = "<red>설명을 전부 지울까요?</red>",
                onConfirm = {
                    mutate { it.copy(lore = emptyList()) }
                    open(viewer)
                },
                onCancel = { open(viewer) },
            ).open(viewer)
        }
        backAndClose()
    }

    private fun lineIcon(index: Int, line: String) = Icon.of(
        Material.PAPER,
        "<white>" + (index + 1) + ". " + line + "</white>",
        listOf(
            "<yellow>▶ 좌클릭: 고치기</yellow>",
            "<red>▶ 우클릭: 지우기</red>",
            "<gray>▶ Shift 클릭: 한 줄 위로</gray>",
        ),
    )

    private fun addIcon() = Icon.of(
        Material.WRITABLE_BOOK,
        "<green>줄 추가</green>",
        listOf("<gray>색코드와 MiniMessage 둘 다 됩니다.</gray>", "<gray>빈 줄은 <white>-</white> 하나로 넣으세요.</gray>"),
    )

    private fun promptAdd() {
        Editors.promptText(
            custom.prompts, viewer, "설명 줄",
            listOf("<gray>빈 줄은 <white>-</white> 하나만 적으세요.</gray>"),
            reopen = { open(viewer) },
        ) { raw -> mutate(false) { it.copy(lore = it.lore + normalize(raw)) } }
    }

    private fun editAt(index: Int, current: String) {
        Editors.promptText(
            custom.prompts, viewer, (index + 1).toString() + "번째 줄",
            listOf("<gray>지금: <white>" + current + "</white></gray>"),
            reopen = { open(viewer) },
        ) { raw ->
            mutate(false) { item ->
                item.copy(lore = item.lore.toMutableList().also { it[index] = normalize(raw) })
            }
        }
    }

    /** `-` 한 글자는 빈 줄을 뜻한다. 채팅으로는 빈 문자열을 보낼 수 없기 때문이다. */
    private fun normalize(raw: String): String = if (raw.trim() == "-") "" else raw

    private fun removeAt(index: Int) {
        mutate { it.copy(lore = it.lore.filterIndexed { i, _ -> i != index }) }
    }

    private fun moveUp(index: Int) {
        if (index == 0) return
        mutate {
            val next = it.lore.toMutableList()
            next[index] = next[index - 1].also { moved -> next[index - 1] = moved }
            it.copy(lore = next)
        }
    }

    private companion object {
        const val SLOT_ADD = 48
        const val SLOT_CLEAR = 49
    }
}

/**
 * 인챈트 고르기. 서버에 있는 인챈트를 **전부 늘어놓고 고른다** — 채팅으로 id 를 치게 하면 오타 난 인챈트가
 * 조용히 안 붙는다([CustomEnchantMenu] 와 같은 모양).
 *
 * **바닐라 상한을 넘겨 붙일 수 있다.** 날카로움 10 은 바닐라로는 못 만들지만 아이템 메타로는
 * 된다 — 커스텀아이템 플러그인을 쓰는 이유 중 하나가 그것이다.
 *
 * 좌클릭 +1 · 우클릭 -1(0 이면 뗀다) · Shift 는 한 번에 바닐라 최대/0 · 숫자키로 직접 입력(1~255).
 * 자리는 늘 열쇠 순이다 — 고른 것을 앞으로 당기면 누를 때마다 칸이 움직인다.
 */
class EnchantMenu(custom: CustomItems, viewer: Player, id: String) :
    DetailMenu(custom, viewer, id, "<dark_gray>인챈트 — $id</dark_gray>") {

    private enum class Filter(val label: String) { FITS("이 재질에 붙는 것"), CHOSEN("고른 것"), ALL("전부") }

    private var filter = Filter.FITS
    private var page = 0

    override fun draw() {
        clear()
        val item = item() ?: run {
            ItemTypeMenu(custom, viewer).open(viewer)
            return
        }

        val sample = ItemStack(item.material)
        val all = Registries.enchantments()
        // 적힌 이름(sharpness · minecraft:sharpness)을 인챈트로 — 표기가 달라도 같은 칸을 가리킨다.
        val chosen = item.enchants.entries.mapNotNull { (name, level) -> CustomItem.matchEnchant(name)?.let { it to (name to level) } }.toMap()
        val shown = when (filter) {
            Filter.FITS -> all.filter { it in chosen || fits(it, sample) }
            Filter.CHOSEN -> all.filter { it in chosen }
            Filter.ALL -> all
        }
        page = Paging.clamp(page, shown.size)
        for ((slot, enchant) in Paging.slice(shown, page).withIndex()) {
            val entry = chosen[enchant]
            val level = entry?.second ?: 0
            set(slot, entryIcon(enchant, level, fits(enchant, sample))) { event ->
                val name = entry?.first ?: nameOf(enchant)
                if (Editors.isPrompt(event)) {
                    Editors.promptInt(custom.prompts, viewer, "인챈트 레벨", 1, 255, { open(viewer) }) {
                        setLevel(name, it, reopen = false)
                    }
                    return@set
                }
                val next = when {
                    event.isShiftClick -> if (level > 0) 0 else enchant.maxLevel
                    event.isRightClick -> level - 1
                    else -> level + 1
                }.coerceIn(0, 255)
                setLevel(name, next)
            }
        }

        if (page > 0) set(Paging.SLOT_PREV, Icon.prevPage()) { page--; refresh() }
        if (page < Paging.pageCount(shown.size) - 1) set(Paging.SLOT_NEXT, Icon.nextPage()) { page++; refresh() }
        set(SLOT_FILTER, Icon.of(Material.HOPPER, "<yellow>보기: <white>" + filter.label + "</white></yellow>",
            listOf("<gray>" + shown.size + "개</gray>", "", "<yellow>▶ 클릭: 바꾸기</yellow>"))) {
            filter = Filter.entries[(filter.ordinal + 1) % Filter.entries.size]
            page = 0
            refresh()
        }
        val unknown = item.enchants.keys.filter { CustomItem.matchEnchant(it) == null }
        if (unknown.isNotEmpty()) {
            set(SLOT_UNKNOWN, Icon.of(Material.RED_DYE, "<red>없는 인챈트 " + unknown.size + "개</red>",
                unknown.map { "<gray>· $it</gray>" } + listOf("", "<red>▶ 클릭: 떼기</red>"))) {
                mutate { it.copy(enchants = it.enchants - unknown.toSet()) }
            }
        }
        backAndClose()
    }

    /** 바닐라 인챈트는 `sharpness` 로, 데이터팩 것은 `ns:id` 로 적는다(예전 파일과 같은 표기). */
    private fun nameOf(enchant: Enchantment): String =
        if (enchant.key.namespace == NamespacedKey.MINECRAFT) enchant.key.key else enchant.key.asString()

    private fun fits(enchant: Enchantment, sample: ItemStack): Boolean =
        runCatching { enchant.canEnchantItem(sample) }.getOrDefault(false)

    private fun entryIcon(enchant: Enchantment, level: Int, fits: Boolean) = Icon.of(
        if (level > 0) Material.ENCHANTED_BOOK else Material.BOOK,
        (if (level > 0) "<aqua>" else "<gray>") + "<lang:" + enchant.translationKey() + ">" +
            if (level > 0) " <white>$level</white>" else "",
        listOfNotNull(
            "<dark_gray>" + nameOf(enchant) + " · 바닐라 최대 " + enchant.maxLevel + "</dark_gray>",
            if (fits) null else "<red>이 재질에는 원래 안 붙습니다 - 그래도 붙습니다</red>",
            "<dark_gray>바닐라 상한을 넘겨도 됩니다(최대 255)</dark_gray>",
            "",
            "<yellow>▶ 좌클릭 +1 · 우클릭 -1 · Shift: 최대/떼기</yellow>",
            "<yellow>▶ 숫자키: 레벨 직접 입력</yellow>",
        ),
    )

    private fun setLevel(name: String, level: Int, reopen: Boolean = true) {
        mutate(reopen) { it.copy(enchants = if (level <= 0) it.enchants - name else it.enchants + (name to level)) }
    }

    private companion object {
        const val SLOT_FILTER = 48
        const val SLOT_UNKNOWN = 50
    }
}

/**
 * 커스텀 인첸트 고르기. 목록은 인첸트 플러그인에게서 **열 때마다** 받는다 — 이 플러그인은 인첸트를
 * 모르고, 관리자가 id 를 손으로 치면 오타 난 인첸트가 조용히 안 붙는다.
 *
 * 좌클릭 +1 · 우클릭 -1(0 이면 뗀다) · Shift 는 한 번에 최대/0. 기본은 이 재질에 붙는 것만 보여준다.
 */
class CustomEnchantMenu(custom: CustomItems, viewer: Player, id: String) :
    DetailMenu(custom, viewer, id, "<dark_gray>커스텀 인첸트 — $id</dark_gray>") {

    private enum class Filter(val label: String) { FITS("이 재질에 붙는 것"), CHOSEN("고른 것"), ALL("전부") }

    private var filter = Filter.FITS
    private var page = 0

    override fun draw() {
        clear()
        val item = item() ?: run {
            ItemTypeMenu(custom, viewer).open(viewer)
            return
        }
        if (!CustomEnchantHook.isEnabled) {
            set(22, Icon.of(Material.BARRIER, "<red>인첸트 플러그인이 없습니다</red>", "<gray>inmc-enchants 를 켜면 여기서 고를 수 있습니다.</gray>"))
            backAndClose()
            return
        }

        val all = CustomEnchantHook.all()
        val known = all.map { it.id }.toSet()
        val shown = when (filter) {
            Filter.FITS -> all.filter { it.id in item.customEnchants || CustomEnchantHook.canApply(it.id, item.material.name) }
            Filter.CHOSEN -> all.filter { it.id in item.customEnchants }
            Filter.ALL -> all
        }
        page = Paging.clamp(page, shown.size)
        for ((slot, info) in Paging.slice(shown, page).withIndex()) {
            val level = item.customEnchants[info.id] ?: 0
            set(slot, entryIcon(info, level, CustomEnchantHook.canApply(info.id, item.material.name))) { event ->
                val next = when {
                    event.isShiftClick -> if (level > 0) 0 else info.maxLevel
                    event.isRightClick -> level - 1
                    else -> level + 1
                }.coerceIn(0, info.maxLevel.coerceAtLeast(1))
                mutate { it.copy(customEnchants = if (next == 0) it.customEnchants - info.id else it.customEnchants + (info.id to next)) }
            }
        }

        if (page > 0) set(Paging.SLOT_PREV, Icon.prevPage()) { page--; refresh() }
        if (page < Paging.pageCount(shown.size) - 1) set(Paging.SLOT_NEXT, Icon.nextPage()) { page++; refresh() }
        set(SLOT_FILTER, Icon.of(Material.HOPPER, "<yellow>보기: <white>" + filter.label + "</white></yellow>",
            listOf("<gray>" + shown.size + "개</gray>", "", "<yellow>▶ 클릭: 바꾸기</yellow>"))) {
            filter = Filter.entries[(filter.ordinal + 1) % Filter.entries.size]
            page = 0
            refresh()
        }
        val unknown = item.customEnchants.keys - known
        if (unknown.isNotEmpty()) {
            set(SLOT_UNKNOWN, Icon.of(Material.RED_DYE, "<red>없는 인첸트 " + unknown.size + "개</red>",
                unknown.map { "<gray>· $it</gray>" } + listOf("", "<red>▶ 클릭: 떼기</red>"))) {
                mutate { it.copy(customEnchants = it.customEnchants - unknown) }
            }
        }
        backAndClose()
    }

    private fun entryIcon(info: CustomEnchantHook.Info, level: Int, fits: Boolean) = Icon.of(
        if (level > 0) Material.ENCHANTED_BOOK else Material.BOOK,
        info.display + if (level > 0) " <white>$level</white>" else "",
        info.description.map { "<gray>$it</gray>" } + listOfNotNull(
            "",
            "<dark_gray>" + info.id + " · " + info.group + " · 최대 " + info.maxLevel + "</dark_gray>",
            "<gray>붙는 곳: <white>" + info.appliesTo + "</white></gray>",
            if (fits) null else "<red>이 재질에는 원래 안 붙습니다 - 그래도 붙습니다</red>",
            "",
            "<yellow>▶ 좌클릭 +1 · 우클릭 -1 · Shift: 최대/떼기</yellow>",
        ),
    )

    private companion object {
        const val SLOT_FILTER = 48
        const val SLOT_UNKNOWN = 50
    }
}

/** 숨김 옵션. 바닐라 [ItemFlag] 그대로다. */
class FlagMenu(custom: CustomItems, viewer: Player, id: String) :
    DetailMenu(custom, viewer, id, "<dark_gray>숨김 옵션 — $id</dark_gray>") {

    override fun draw() {
        clear()
        val item = item() ?: run {
            ItemTypeMenu(custom, viewer).open(viewer)
            return
        }

        for ((index, flag) in ItemFlag.entries.withIndex()) {
            if (index >= Paging.PER_PAGE) break
            val on = flag.name in item.flags
            set(
                index,
                Icon.of(
                    if (on) Material.LIME_DYE else Material.GRAY_DYE,
                    "<yellow>" + label(flag) + ": " + Icon.toggle(on) + "</yellow>",
                    listOf("<dark_gray>" + flag.name + "</dark_gray>", "", "<yellow>▶ 클릭: 전환</yellow>"),
                ),
            ) {
                mutate {
                    val next = if (on) it.flags - flag.name else it.flags + flag.name
                    it.copy(flags = next)
                }
            }
        }

        backAndClose()
    }

    /**
     * 칸 이름.
     *
     * `HIDE_ADDITIONAL_TOOLTIP` 은 deprecated 지만 **아직 목록에 있다.** 화면이
     * `ItemFlag.entries` 를 통째로 도는 이상 이름을 붙일 수 있어야 하고, 빼면 그 칸만
     * `HIDE_ADDITIONAL_TOOLTIP` 이라는 영문 상수로 나온다. 바닐라가 실제로 지우는 날
     * 컴파일이 깨져서 알려줄 것이다.
     */
    @Suppress("DEPRECATION")
    private fun label(flag: ItemFlag): String = when (flag) {
        ItemFlag.HIDE_ENCHANTS -> "인챈트 숨기기"
        ItemFlag.HIDE_ATTRIBUTES -> "속성 숨기기"
        ItemFlag.HIDE_UNBREAKABLE -> "무한 내구도 숨기기"
        ItemFlag.HIDE_DESTROYS -> "파괴 가능 숨기기"
        ItemFlag.HIDE_PLACED_ON -> "설치 가능 숨기기"
        ItemFlag.HIDE_ADDITIONAL_TOOLTIP -> "추가 설명 숨기기"
        ItemFlag.HIDE_DYE -> "염색 숨기기"
        ItemFlag.HIDE_ARMOR_TRIM -> "장식 숨기기"
        else -> flag.name
    }
}

/**
 * 연동 값 편집. **이 플러그인이 다른 시스템과 이어지는 자리다.**
 *
 * 열쇠의 뜻은 **읽는 쪽이 정한다.** 여기서는 아무 검사도 하지 않는다 — 낚시가 무슨 열쇠를
 * 읽는지 이 플러그인이 알면 낚시가 바뀔 때마다 여기도 고쳐야 한다.
 *
 * 대신 **자주 쓰는 것들을 보기로 보여준다.** 아무 안내 없이 빈 화면을 주면 관리자가 무엇을
 * 적어야 할지 알 길이 없다.
 */
class DataMenu(custom: CustomItems, viewer: Player, id: String) :
    DetailMenu(custom, viewer, id, "<dark_gray>연동 값 — $id</dark_gray>") {

    override fun draw() {
        clear()
        val item = item() ?: run {
            ItemTypeMenu(custom, viewer).open(viewer)
            return
        }

        for ((index, entry) in item.data.entries.withIndex()) {
            if (index >= Paging.PER_PAGE) break
            set(index, entryIcon(entry.key, entry.value)) { event ->
                if (event.isRightClick) {
                    mutate { it.copy(data = it.data - entry.key) }
                    return@set
                }
                Editors.promptText(
                    custom.prompts, viewer, entry.key,
                    listOf("<gray>지금: <white>" + entry.value + "</white></gray>"),
                    reopen = { open(viewer) },
                ) { raw -> mutate(false) { d -> d.copy(data = d.data + (entry.key to raw.trim())) } }
            }
        }

        set(SLOT_ADD, addIcon()) { promptAdd() }
        set(SLOT_HELP, helpIcon())
        backAndClose()
    }

    private fun entryIcon(key: String, value: String) = Icon.of(
        Material.REPEATER,
        "<aqua>" + key + "</aqua>",
        listOf(
            "<white>" + value + "</white>",
            "",
            "<yellow>▶ 좌클릭: 값 고치기</yellow>",
            "<red>▶ 우클릭: 지우기</red>",
        ),
    )

    private fun addIcon() = Icon.of(
        Material.WRITABLE_BOOK,
        "<green>연동 값 추가</green>",
        listOf("<gray><white>열쇠 값</white> 형태로 한 번에 적으세요.</gray>", "<gray>예: <white>fishing.reel-power 15</white></gray>"),
    )

    private fun helpIcon() = Icon.of(
        Material.BOOK,
        "<aqua>자주 쓰는 열쇠</aqua>",
        listOf(
            "<yellow>낚시 — 낚싯대로 쓰려면</yellow>",
            "<dark_gray>fishing.rod = 1</dark_gray>",
            "<dark_gray>fishing.reel-power = 15</dark_gray>",
            "<dark_gray>fishing.line-strength = 30</dark_gray>",
            "<dark_gray>fishing.reel-durability = 15</dark_gray>",
            "<dark_gray>fishing.big-fish-chance = 2</dark_gray>",
            "<dark_gray>fishing.double-chance = 3</dark_gray>",
            "<dark_gray>fishing.max-fatigue = 500</dark_gray>",
            "<dark_gray>fishing.fatigue-recovery = 50</dark_gray>",
            "<dark_gray>fishing.grade-bonus.s = 5</dark_gray>",
            "",
            "<gray>열쇠의 뜻은 <white>읽는 플러그인</white>이 정합니다.</gray>",
            "<gray>여기서는 적힌 대로 넘겨줄 뿐입니다.</gray>",
        ),
    )

    private fun promptAdd() {
        Editors.promptText(
            custom.prompts, viewer, "연동 값",
            listOf(
                "<gray><white>열쇠 값</white> 형태로 적으세요.</gray>",
                "<gray>예: <white>fishing.reel-power 15</white></gray>",
            ),
            reopen = { open(viewer) },
        ) { raw ->
            val parts = raw.trim().split(Regex("\\s+"), limit = 2)
            if (parts.size < 2 || parts[0].isBlank()) {
                viewer.sendMessage(Text.render("<red>열쇠와 값을 띄어쓰기로 나눠 적으세요.</red>"))
                return@promptText
            }
            mutate(false) { it.copy(data = it.data + (parts[0].lowercase() to parts[1].trim())) }
        }
    }

    private companion object {
        const val SLOT_ADD = 48
        const val SLOT_HELP = 49
    }
}
