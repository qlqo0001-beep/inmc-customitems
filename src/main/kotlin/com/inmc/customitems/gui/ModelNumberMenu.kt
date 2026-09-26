package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.ItemType
import com.inmc.customitems.pack.ModelNumbers
import com.inmc.customitems.util.Ph
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.store.DefinitionKey
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 번호 모델 목록 — 리소스팩이 번호(custom_model_data)로 바꿔 그리는 것들. 아이콘은 **그 재질에 그 번호를 붙인 것**이라
 * 팩을 받은 사람 눈에는 실제 텍스처로 보인다.
 *
 * 좌클릭: 그 모델로 새 아이템 만들기(이름을 묻고 설정 화면으로) · Shift+클릭: 한 개 받기.
 * 목록은 열 때 팩을 읽어 만든다([com.inmc.customitems.pack.PackService.modelNumbers]).
 */
class ModelNumberMenu private constructor(
    custom: CustomItems,
    private val viewer: Player,
    private val entries: List<ModelNumbers.Entry>,
) : Menu(custom, 54, Text.renderFlat("<dark_gray>번호 모델 목록</dark_gray>")) {

    private enum class Filter(val label: String) { ALL("전부"), UNUSED("안 쓰는 번호만"), CONFLICT("겹친 번호만") }

    private var page = 0
    private var filter = Filter.ALL
    private val conflicts = ModelNumbers.conflicts(entries)

    /** 이 재질·번호를 쓰는 우리 아이템 id. */
    private fun users(entry: ModelNumbers.Entry): List<String> = custom.items.all()
        .filter { it.material.key.key == entry.material && it.customModelData == entry.number }
        .map { it.id }

    private fun shown(): List<ModelNumbers.Entry> = when (filter) {
        Filter.ALL -> entries
        Filter.UNUSED -> entries.filter { users(it).isEmpty() }
        Filter.CONFLICT -> entries.filter { (it.material to it.number) in conflicts }
    }

    private fun stack(entry: ModelNumbers.Entry): ItemStack? {
        val material = Material.matchMaterial(entry.material)?.takeIf { it.isItem && !it.isAir } ?: return null
        return ItemStack(material).also { stack ->
            @Suppress("DEPRECATION")
            stack.editMeta { it.setCustomModelData(entry.number) }
        }
    }

    override fun draw() {
        clear()
        val list = shown()
        page = Paging.clamp(page, list.size)
        for ((slot, entry) in Paging.slice(list, page).withIndex()) {
            val base = stack(entry) ?: ItemStack(Material.BARRIER)
            val users = users(entry)
            val conflict = (entry.material to entry.number) in conflicts
            set(slot, Icon.annotate(base, lore = buildList {
                add("<gray>재질 <white>" + entry.material + "</white> · 번호 <white>" + entry.number + "</white></gray>")
                if (entry.model.isNotBlank()) add("<gray>모델 <white>" + net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().escapeTags(entry.model) + "</white></gray>")
                add("<gray>팩 <white>" + net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().escapeTags(entry.source) + "</white></gray>")
                add(if (users.isEmpty()) "<dark_gray>쓰는 아이템 없음</dark_gray>" else "<green>쓰는 아이템: <white>" + users.joinToString(", ") + "</white></green>")
                if (conflict) add("<red>⚠ 다른 팩도 이 번호를 씁니다 — 나중 팩이 이깁니다</red>")
                add("")
                add("<yellow>▶ 클릭: 이 모델로 새 아이템</yellow>")
                add("<yellow>▶ Shift+클릭: 한 개 받기</yellow>")
            })) { event ->
                val item = stack(entry) ?: return@set
                if (event.isShiftClick) {
                    viewer.inventory.addItem(item).values.forEach { viewer.world.dropItemNaturally(viewer.location, it) }
                } else {
                    create(entry, item.type)
                }
            }
        }
        if (list.isEmpty()) {
            set(22, Icon.of(Material.BARRIER, "<gray>번호 모델이 없습니다</gray>", listOf(
                "<gray>pack/sources/ 에 팩을 넣거나 리소스팩을 한 번 빌드하세요.</gray>",
            )))
        }
        set(Paging.SLOT_BACK, Icon.back()) { PackMenu(custom, viewer).open(viewer) }
        if (page > 0) set(Paging.SLOT_PREV, Icon.of(Material.ARROW, "<yellow>이전</yellow>")) { page--; refresh() }
        if (page + 1 < Paging.pageCount(list.size)) set(Paging.SLOT_NEXT, Icon.of(Material.ARROW, "<yellow>다음</yellow>")) { page++; refresh() }
        set(SLOT_FILTER, Icon.of(Material.HOPPER, "<yellow>보기: <white>" + filter.label + "</white></yellow>", listOf(
            "<gray>전체 <white>" + entries.size + "</white> · 겹침 <white>" + conflicts.size + "</white></gray>",
            "", "<yellow>▶ 클릭: 바꾸기</yellow>",
        ))) {
            filter = Filter.entries[(filter.ordinal + 1) % Filter.entries.size]
            page = 0
            refresh()
        }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    /** 이름을 묻고, 그 재질·번호로 새 아이템을 만든 뒤 설정 화면으로. */
    private fun create(entry: ModelNumbers.Entry, material: Material) {
        var created: String? = null
        Editors.promptText(
            custom.prompts, viewer, "새 아이템 이름",
            listOf("<gray>" + DefinitionKey.HINT + ".</gray>", "<gray>재질 <white>" + entry.material + "</white> · 번호 <white>" + entry.number + "</white> 로 만듭니다.</gray>"),
            reopen = { created?.let { ItemEditMenu(custom, viewer, it).open(viewer) } ?: open(viewer) },
        ) { raw ->
            val id = raw.trim().lowercase()
            if (!DefinitionKey.isValid(id)) return@promptText custom.messages.send(viewer, "invalid-name", Ph.of().item(raw))
            if (custom.items.exists(id)) return@promptText custom.messages.send(viewer, "already-exists", Ph.of().item(id))
            custom.items.put(CustomItem(id, material, type = ItemType.guess(material), customModelData = entry.number))
            custom.messages.send(viewer, "registered", Ph.of().item(id))
            created = id
        }
    }

    companion object {
        const val SLOT_FILTER = 49

        /** 팩을 읽은 뒤 연다(워커에서 읽는다). */
        fun open(custom: CustomItems, viewer: Player) {
            viewer.sendMessage(Text.render("<gray>리소스팩의 번호 모델을 읽는 중…</gray>"))
            custom.pack.modelNumbers { entries ->
                if (viewer.isOnline) ModelNumberMenu(custom, viewer, entries).open(viewer)
            }
        }
    }
}
