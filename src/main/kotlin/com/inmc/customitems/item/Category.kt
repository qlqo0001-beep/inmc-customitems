package com.inmc.customitems.item

import com.inmc.customitems.CustomItems
import kr.inmc.core.item.StoredItem
import kr.inmc.core.store.YamlFileStore
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration

/**
 * 종류([ItemType]) 아래의 소분류 — MMOItems 에서 부모 타입을 둔 타입(`일반무기` → SWORD, `강화석` → CONSUMABLE).
 *
 * 종류처럼 **표시와 걸러보기 전용**이다. 동작은 바꾸지 않는다 — 종류가 동작(부적·유물)을 정하고, 소분류는 목록이
 * 길어졌을 때 나눠 보는 서랍일 뿐이다. 그래서 관리자가 마음대로 만들고 지운다.
 */
data class Category(
    val id: String,
    /** 종류의 id — 기본 종류(`consumable`)이거나 관리자가 만든 종류(`보호권`). */
    val type: String,
    val name: String = id,
    val icon: Material = ItemType.of(type).icon,
    /** 손에 든 것으로 정한 아이콘 — 모델(번호·item_model)·커스텀아이템 모양까지. 없으면 [icon] 재질. */
    val iconItem: StoredItem? = null,
) {
    fun save(section: ConfigurationSection) {
        section.set("type", type)
        section.set("name", name)
        section.set("icon", icon.name)
        iconItem?.save(section.createSection("icon-item"))
    }

    companion object {
        fun load(id: String, section: ConfigurationSection): Category {
            val type = section.getString("type")?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: ItemType.MISC.id
            return Category(
                id = id,
                type = type,
                name = section.getString("name")?.takeIf { it.isNotBlank() } ?: id,
                icon = section.getString("icon")?.let { Material.matchMaterial(it) } ?: ItemType.of(type).icon,
                iconItem = section.getConfigurationSection("icon-item")?.let(StoredItem::load),
            )
        }
    }
}

/** `categories.yml`. 파일에 적힌 순서가 서랍 순서다. */
class CategoryRegistry(private val custom: CustomItems) : YamlFileStore(
    io = custom.io,
    path = listOf("categories.yml"),
    header = """
        종류 아래의 소분류. /커스텀아이템 관리 → 종류 → 소분류 에서 GUI 로 만들고 고칩니다.
        표시와 걸러보기 전용입니다. 아이템 쪽은 items.yml 의 category 에 이 id 를 적습니다.

        type  weapon / armor / tool / consumable / accessory / talisman / relic / material / gem / misc 또는 만든 종류의 id(types.yml)
        name  서랍에 보이는 이름
        icon  서랍 아이콘(재질 이름)
        icon-item  손에 든 것으로 정한 아이콘(모델·커스텀아이템 모양). 화면에서 정합니다
    """.trimIndent() + "\n",
    what = "소분류",
) {

    private val categories = LinkedHashMap<String, Category>()

    fun all(): List<Category> = categories.values.toList()

    fun of(type: TypeDef): List<Category> = categories.values.filter { it.type == type.id }

    fun get(id: String?): Category? = id?.takeIf { it.isNotBlank() }?.let { categories[it.lowercase()] }

    /** 이 아이템이 든 소분류. 없거나 종류가 다르면(종류를 바꾼 뒤) null — "분류 없음"으로 보인다. */
    fun of(item: CustomItem): Category? = get(item.category)?.takeIf { it.type == custom.types.of(item).id }

    fun put(category: Category) {
        categories[category.id] = category
        markDirty()
    }

    fun remove(id: String): Boolean = (categories.remove(id.lowercase()) != null).also { if (it) markDirty() }

    override fun read(config: YamlConfiguration) {
        categories.clear()
        for (key in config.getKeys(false)) {
            config.getConfigurationSection(key)?.let { categories[key.lowercase()] = Category.load(key.lowercase(), it) }
        }
    }

    override fun write(config: YamlConfiguration) {
        for (category in categories.values) category.save(config.createSection(category.id))
    }
}
