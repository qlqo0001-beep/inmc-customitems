package com.inmc.customitems.item

import com.inmc.customitems.CustomItems
import kr.inmc.core.item.StoredItem
import kr.inmc.core.store.YamlFileStore
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration

/**
 * 종류(대분류) 하나 — 기본 10종([ItemType])이거나 관리자가 만든 것(보호권·열쇠·캡슐 …).
 *
 * **동작은 [base] 가 정한다.** 관리자가 만든 종류는 이름·아이콘·기호만 갖고, 부적·유물·소모품처럼 동작이 걸린 것은
 * 기본 종류 하나를 골라 그대로 따른다("보호권"은 소모품처럼). 그래서 동작 코드는 [ItemType] 만 보면 되고,
 * 새 종류를 만들어도 코드를 고칠 일이 없다.
 *
 * 기본 종류도 이름·아이콘·기호를 바꿀 수 있다([builtin] 이면 `id == base.id`).
 */
data class TypeDef(
    val id: String,
    val base: ItemType,
    val name: String = base.display,
    val icon: Material = base.icon,
    val symbol: String = base.symbol,
    /** 손에 든 것으로 정한 아이콘 — 모델(번호·item_model)·커스텀아이템 모양까지. 없으면 [icon] 재질. */
    val iconItem: StoredItem? = null,
) {
    val builtin: Boolean get() = id == base.id

    /** 로어 맨 위 — 흐린 글씨로 종류만. */
    fun header(): String = "<dark_gray>" + symbol + "</dark_gray> <gray>" + name + "</gray>"

    fun save(section: ConfigurationSection) {
        if (!builtin) section.set("base", base.id)
        section.set("name", name)
        section.set("icon", icon.name)
        section.set("symbol", symbol)
        iconItem?.save(section.createSection("icon-item"))
    }

    companion object {
        fun of(type: ItemType) = TypeDef(type.id, type)

        fun load(id: String, section: ConfigurationSection): TypeDef? {
            val builtin = ItemType.entries.firstOrNull { it.id == id }
            val base = builtin ?: ItemType.entries.firstOrNull { it.id == section.getString("base")?.lowercase() } ?: return null
            return TypeDef(
                id = id,
                base = base,
                name = section.getString("name")?.takeIf { it.isNotBlank() } ?: if (builtin != null) base.display else id,
                icon = section.getString("icon")?.let { Material.matchMaterial(it) } ?: base.icon,
                symbol = section.getString("symbol")?.takeIf { it.isNotBlank() } ?: base.symbol,
                iconItem = section.getConfigurationSection("icon-item")?.let(StoredItem::load),
            )
        }
    }
}

/** `types.yml` — 기본 종류의 이름 바꾸기와 관리자가 만든 종류. 파일에 적힌 순서가 화면 순서다. */
class TypeRegistry(private val custom: CustomItems) : YamlFileStore(
    io = custom.io,
    path = listOf("types.yml"),
    header = """
        종류(대분류). /커스텀아이템 관리 → 종류 관리 에서 GUI 로 고치는 것을 권장합니다.
        기본 10종(weapon armor tool consumable accessory talisman relic material gem misc)은 이름·아이콘·기호만 바꿀 수 있고,
        새 종류는 base 에 적은 기본 종류처럼 동작합니다(예: 보호권 → consumable). 아이템 쪽은 items.yml 의 custom-type 에 id 를 적습니다.
    """.trimIndent() + "\n",
    what = "종류",
) {

    private val defs = LinkedHashMap<String, TypeDef>()

    /** 기본 종류(이름을 바꿨으면 바꾼 것). */
    fun builtin(type: ItemType): TypeDef = defs[type.id] ?: TypeDef.of(type)

    /** 관리자가 만든 종류. */
    fun custom(): List<TypeDef> = defs.values.filter { !it.builtin }

    /** 기본 10종 다음 만든 것. */
    fun all(): List<TypeDef> = ItemType.entries.map(::builtin) + custom()

    fun get(id: String?): TypeDef? {
        val key = id?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        ItemType.entries.firstOrNull { it.id == key }?.let { return builtin(it) }
        return defs[key]?.takeIf { !it.builtin }
    }

    /**
     * 이 아이템의 종류. 만든 종류가 지워졌거나 동작 기준이 아이템과 어긋나면 기본 종류로 — 아이템이 서랍에서 사라지지 않게.
     */
    fun of(item: CustomItem): TypeDef =
        item.customType.takeIf { it.isNotBlank() }?.let { defs[it.lowercase()] }?.takeIf { !it.builtin && it.base == item.type } ?: builtin(item.type)

    fun put(def: TypeDef) {
        defs[def.id] = def
        markDirty()
        custom.items.onTypesChanged()
    }

    fun remove(id: String): Boolean = (defs.remove(id.lowercase()) != null).also {
        if (it) {
            markDirty()
            custom.items.onTypesChanged()
        }
    }

    override fun read(config: YamlConfiguration) {
        defs.clear()
        val root = config.getConfigurationSection("types") ?: return
        for (key in root.getKeys(false)) {
            val id = key.lowercase()
            root.getConfigurationSection(key)?.let { TypeDef.load(id, it) }?.let { defs[id] = it }
                ?: custom.logger.warning("종류 '$key' 를 읽지 못했습니다 - base 를 확인하세요")
        }
    }

    override fun write(config: YamlConfiguration) {
        val root = config.createSection("types")
        for (def in defs.values) def.save(root.createSection(def.id))
    }
}
