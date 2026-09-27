package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.ItemType
import com.inmc.customitems.pack.PackAssets
import com.inmc.customitems.util.Ph
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.store.DefinitionKey
import kr.inmc.core.util.Text
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 모델 목록 — 만든 팩에 들어 있는 모델 전부. 아이콘이 **그 모델의 미리보기 정의**(`inmc:preview/…`, 빌드가 붙인다)를 가리켜 팩을 받은
 * 사람 눈에는 실제 모양으로 보인다. 번호 없이(규칙 31) 옛 "번호 모델 목록"이 하던 일을 한다.
 *
 * 좌클릭: 그 모델로 새 아이템(이름을 묻고 설정 화면으로, 재질은 손에 든 것 · 빈손이면 종이) · Shift+클릭: 한 개 받기(보기용).
 * 목록은 열 때 만든 팩을 읽어 만든다([com.inmc.customitems.pack.PackService.models]).
 */
class ModelListMenu private constructor(
    custom: CustomItems,
    private val viewer: Player,
    private val models: List<String>,
) : Menu(custom, 54, Text.renderFlat("<dark_gray>모델 목록</dark_gray>")) {

    private enum class Filter(val label: String) { ALL("전부"), UNUSED("안 쓰는 것"), USED("쓰는 것") }

    private var page = 0
    private var filter = Filter.ALL

    /** null = 모든 이름공간. */
    private var namespace: String? = null
    private val namespaces = models.map { it.substringBefore(':') }.distinct().sorted()

    /** 모델 → 그 모델을 쓰는 우리 아이템 id. 화면을 그릴 때마다 지금 정의로. */
    private fun users(): Map<String, List<String>> {
        val out = HashMap<String, MutableList<String>>()
        for (item in custom.items.all()) {
            val model = when {
                PackAssets.isCube(item) -> PackAssets.blockModelFor(item)
                PackAssets.needsPack(item) -> PackAssets.modelNameFor(item)
                else -> null
            } ?: continue
            out.getOrPut(model) { ArrayList() } += item.id
        }
        return out
    }

    private fun preview(model: String): ItemStack = ItemStack(Material.PAPER).also { stack ->
        NamespacedKey.fromString(PackAssets.previewKey(model))?.let { key -> stack.editMeta { it.setItemModel(key) } }
    }

    override fun draw() {
        clear()
        val users = users()
        val list = models.filter { model ->
            (namespace == null || model.substringBefore(':') == namespace) && when (filter) {
                Filter.ALL -> true
                Filter.UNUSED -> users[model].isNullOrEmpty()
                Filter.USED -> !users[model].isNullOrEmpty()
            }
        }
        page = Paging.clamp(page, list.size)
        for ((slot, model) in Paging.slice(list, page).withIndex()) {
            val using = users[model].orEmpty()
            set(slot, Icon.relabel(preview(model), "<white>" + escape(model) + "</white>", buildList {
                add(if (using.isEmpty()) "<dark_gray>쓰는 아이템 없음</dark_gray>" else "<green>쓰는 아이템: <white>" + using.take(5).joinToString(", ") + (if (using.size > 5) " …" else "") + "</white></green>")
                add("")
                add("<yellow>▶ 클릭: 이 모델로 새 아이템</yellow>")
                add("<yellow>▶ Shift+클릭: 한 개 받기</yellow>")
            })) { event ->
                if (event.isShiftClick) {
                    viewer.inventory.addItem(preview(model)).values.forEach { viewer.world.dropItemNaturally(viewer.location, it) }
                } else {
                    create(model)
                }
            }
        }

        set(SLOT_INFO, Icon.of(Material.PAPER, "<aqua>모델 <white>" + list.size + "</white>개</aqua>", buildList {
            add("<gray>팩 전체 <white>" + models.size + "</white> · 아이템이 쓰는 것 <white>" + models.count { !users[it].isNullOrEmpty() } + "</white></gray>")
            add("<gray>" + (page + 1) + " / " + Paging.pageCount(list.size) + " 쪽</gray>")
            if (models.isEmpty()) {
                add("")
                add("<red>목록이 비었습니다 — 리소스팩을 한 번 만드세요.</red>")
                add("<gray>만든 팩에 들어 있는 모델만 보입니다.</gray>")
            } else {
                add("")
                add("<gray>팩을 받은 사람에게 실제 모양으로 보입니다.</gray>")
                add("<gray>팩을 고쳤으면 다시 만든 뒤 여세요.</gray>")
            }
        }))
        set(SLOT_FILTER, Icon.of(Material.HOPPER, "<yellow>보기: <white>" + filter.label + "</white></yellow>",
            Editors.optionList(Filter.entries, filter) { it.label } + listOf("", "<yellow>▶ 클릭: 바꾸기</yellow>"))) {
            filter = Filter.entries[(filter.ordinal + 1) % Filter.entries.size]
            page = 0
            refresh()
        }
        val spaces = listOf<String?>(null) + namespaces
        set(SLOT_NAMESPACE, Icon.of(Material.NAME_TAG, "<yellow>이름공간: <white>" + (namespace ?: "전부") + "</white></yellow>",
            Editors.optionList(spaces, namespace) { it ?: "전부" } + listOf("", "<yellow>▶ 좌클릭: 다음 · 우클릭: 이전</yellow>"))) { event ->
            namespace = Editors.cycle(event, spaces, namespace)
            page = 0
            refresh()
        }
        set(Paging.SLOT_BACK, Icon.back()) { PackMenu(custom, viewer).open(viewer) }
        if (page > 0) set(Paging.SLOT_PREV, Icon.prevPage()) {
            page--
            refresh()
        }
        if (page < Paging.pageCount(list.size) - 1) set(Paging.SLOT_NEXT, Icon.nextPage()) {
            page++
            refresh()
        }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    /** 이름을 묻고, 그 모델로 새 아이템을 만든 뒤 설정 화면으로. 재질은 손에 든 것(빈손이면 종이) — 모델과 재질은 따로다. */
    private fun create(model: String) {
        val hand = viewer.inventory.itemInMainHand.type
        val material = if (hand.isAir) Material.PAPER else hand
        var created: String? = null
        Editors.promptText(
            custom.prompts, viewer, "새 아이템 이름",
            listOf("<gray>" + DefinitionKey.HINT + ".</gray>", "<gray>모델 <white>" + escape(model) + "</white> · 재질 <white>" + material.name + "</white> 로 만듭니다.</gray>"),
            reopen = { created?.let { ItemEditMenu(custom, viewer, it).open(viewer) } ?: open(viewer) },
        ) { raw ->
            val id = raw.trim().lowercase()
            if (!DefinitionKey.isValid(id)) return@promptText custom.messages.send(viewer, "invalid-name", Ph.of().item(raw))
            if (custom.items.exists(id)) return@promptText custom.messages.send(viewer, "already-exists", Ph.of().item(id))
            custom.items.put(CustomItem(id, material, type = ItemType.guess(material), model = model))
            custom.messages.send(viewer, "registered", Ph.of().item(id))
            created = id
        }
    }

    private fun escape(text: String): String = MiniMessage.miniMessage().escapeTags(text)

    companion object {
        const val SLOT_FILTER = 48
        const val SLOT_INFO = 49
        const val SLOT_NAMESPACE = 50

        /** 만든 팩을 읽은 뒤 연다(워커에서 읽는다). */
        fun open(custom: CustomItems, viewer: Player) {
            viewer.sendMessage(Text.render("<gray>리소스팩의 모델을 읽는 중…</gray>"))
            custom.pack.models { models ->
                if (viewer.isOnline) ModelListMenu(custom, viewer, models).open(viewer)
            }
        }
    }
}
