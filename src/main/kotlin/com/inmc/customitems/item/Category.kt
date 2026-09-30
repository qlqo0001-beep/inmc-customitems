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
    /** 서랍 아이콘 재질. 비우면 **그 소분류 첫 아이템 모양**(사용자 요청 2026-10-01 — 생선살 서랍이 구리 주괴로 보이지 않게). */
    val icon: Material? = null,
    /** 손에 든 것으로 정한 아이콘 — 모델(번호·item_model)·커스텀아이템 모양까지. 없으면 [icon] 재질. */
    val iconItem: StoredItem? = null,
    /** 로어의 종류 줄에 종류 이름 대신 이 소분류 이름을 보인다 — 아이템이 따로 고르지 않았으면([TypeLabel.AUTO]). */
    val loreName: Boolean = false,
) {
    fun save(section: ConfigurationSection) {
        section.set("type", type)
        section.set("name", name)
        icon?.let { section.set("icon", it.name) }
        iconItem?.save(section.createSection("icon-item"))
        if (loreName) section.set("lore-name", true)
    }

    companion object {
        fun load(id: String, section: ConfigurationSection): Category {
            val type = section.getString("type")?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: ItemType.MISC.id
            return Category(
                id = id,
                type = type,
                name = section.getString("name")?.takeIf { it.isNotBlank() } ?: id,
                icon = section.getString("icon")?.let { Material.matchMaterial(it) },
                iconItem = section.getConfigurationSection("icon-item")?.let(StoredItem::load),
                loreName = section.getBoolean("lore-name", false),
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

        type  weapon / armor / tool / consumable / accessory / talisman / relic / backpack / material / gem / block / misc 또는 만든 종류의 id(types.yml)
        name  서랍에 보이는 이름
        icon  서랍 아이콘(재질 이름). 비우면 그 소분류 첫 아이템 모양
        icon-item  손에 든 것으로 정한 아이콘(모델·커스텀아이템 모양). 화면에서 정합니다
        lore-name  true 면 로어의 종류 줄에 종류 이름 대신 이 소분류 이름(아이템의 type-label 이 먼저)
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
        custom.items.onCategoriesChanged()
    }

    fun remove(id: String): Boolean = (categories.remove(id.lowercase()) != null).also {
        if (it) {
            markDirty()
            custom.items.onCategoriesChanged()
        }
    }

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

/** 로어의 종류 줄에 무엇을 보일지 — 아이템마다([CustomItem.typeLabel]). 기본은 소분류가 정한 대로([Category.loreName]). */
enum class TypeLabel(val id: String, val display: String) {
    AUTO("auto", "소분류 설정대로"),
    TYPE("type", "종류 이름"),
    CATEGORY("category", "소분류 이름"),
    ;

    /** 이 아이템이 든 [category] 의 이름을 보일까. */
    fun showsCategory(category: Category): Boolean = when (this) {
        AUTO -> category.loreName
        TYPE -> false
        CATEGORY -> true
    }

    companion object {
        fun of(id: String?): TypeLabel = entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: AUTO
    }
}
