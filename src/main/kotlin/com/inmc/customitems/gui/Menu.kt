package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.Category
import com.inmc.customitems.item.TypeDef
import com.inmc.customitems.pack.TypeIcons
import kr.inmc.core.gui.Icon
import kr.inmc.core.item.StoredItem
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * core 의 [kr.inmc.core.gui.Menu] 에 이 플러그인의 서비스 로케이터를 다시 붙인 얇은 층.
 *
 * core 는 [CustomItems] 를 알지 못하고 알 필요도 없다. 그 둘을 잇는 것이 이 파일의 전부다.
 */
abstract class Menu(
    protected val custom: CustomItems,
    size: Int,
    title: Component,
) : kr.inmc.core.gui.Menu(size, title) {

    /** 리로드 때 열린 화면을 닫는 청소가 이 값으로 우리 것을 가려낸다. */
    override val owner: Any get() = custom

    /**
     * 서랍·종류의 아이콘. 손에 든 것으로 정했으면 그 모양(모델 번호·item_model·커스텀아이템) 그대로, 아니면 재질.
     * 이름·설명은 화면 것으로 **바꿔 쓴다** — 아이콘으로 쓴 아이템의 설명이 서랍에 섞이면 안 된다.
     */
    protected fun iconOf(material: Material, item: StoredItem?, name: String, lore: List<String>): ItemStack {
        val stack = item?.let { custom.crafting.resolver.create(it, 1) } ?: return Icon.of(material, name, lore)
        return Icon.relabel(stack, name, lore)
    }

    /**
     * 종류 서랍 아이콘. 관리자가 정한 모양·재질이 먼저다. 안 정한 기본 종류는 **종류 아이콘**(우리가 그린 그림, [TypeIcons] — 사용자 요청
     * 2026-10-01) — 팩에 그 그림이 들었을 때만(없는 모델은 보라·검정 칸이 된다). 안 정한 만든 종류는 그 종류 첫 아이템 모양.
     */
    protected fun typeIcon(type: TypeDef, name: String, lore: List<String>): ItemStack {
        if (type.iconItem != null || type.icon != type.base.icon) return iconOf(type.icon, type.iconItem, name, lore)
        if (type.builtin) {
            val key = TypeIcons.key(type.base)?.takeIf { custom.pack.guiIcons } ?: return Icon.of(type.icon, name, lore)
            return Icon.of(type.icon, name, lore).apply { editMeta { it.setItemModel(key) } }
        }
        val first = custom.items.all().firstOrNull { custom.types.of(it).id == type.id }
        return first?.let { Icon.relabel(custom.items.preview(it), name, lore) } ?: Icon.of(type.icon, name, lore)
    }

    /** 소분류 서랍 아이콘. 정한 모양·재질이 먼저, 안 정했으면 **그 소분류 첫 아이템 모양**(생선살이 구리 주괴로 보이지 않게), 비었으면 종류 아이콘. */
    protected fun categoryIcon(category: Category, name: String, lore: List<String>): ItemStack {
        if (category.iconItem != null || category.icon != null) return iconOf(category.icon ?: Material.BOOKSHELF, category.iconItem, name, lore)
        val first = custom.items.all().firstOrNull { custom.categories.of(it)?.id == category.id }
        if (first != null) return Icon.relabel(custom.items.preview(first), name, lore)
        return typeIcon(custom.types.get(category.type) ?: TypeDef.of(com.inmc.customitems.item.ItemType.MISC), name, lore)
    }

    /** 손에 든 것을 아이콘으로 — 평범한 바닐라면 재질만(설정 파일이 한 줄로 남는다), 아니면 모양째. */
    protected fun captureIcon(hand: ItemStack): Pair<Material, StoredItem?> {
        val resolver = custom.crafting.resolver
        return hand.type to if (resolver.isPlainVanilla(hand)) null else resolver.capture(hand)
    }
}
