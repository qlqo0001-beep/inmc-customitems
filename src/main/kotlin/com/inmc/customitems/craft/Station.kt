package com.inmc.customitems.craft

import com.inmc.customitems.CustomItems
import kr.inmc.core.item.StoredItem
import kr.inmc.core.store.YamlFileStore
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration

/** 재료나 결과 한 칸. 관리자가 화면에 넣은 그대로(core [StoredItem] — 우리 아이템·MMOItems·바닐라). */
data class Part(val item: StoredItem, val amount: Int)

internal fun saveParts(section: ConfigurationSection, key: String, parts: List<Part>) {
    if (parts.isEmpty()) return
    val node = section.createSection(key)
    for ((index, part) in parts.withIndex()) {
        val child = node.createSection(index.toString())
        part.item.save(child)
        child.set("amount", part.amount)
    }
}

internal fun loadParts(section: ConfigurationSection, key: String): List<Part> {
    val node = section.getConfigurationSection(key) ?: return emptyList()
    return node.getKeys(false).sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }.mapNotNull { k ->
        val child = node.getConfigurationSection(k) ?: return@mapNotNull null
        StoredItem.load(child)?.let { Part(it, child.getInt("amount", 1).coerceIn(1, 64 * 36)) }
    }
}

/**
 * 제작대의 조합법 하나(MMOItems 의 crafting station recipe).
 *
 * @param seconds 0 이면 바로 받는다. 아니면 대기열에 들어가 그만큼 뒤에 받는다(접속을 끊어도 흐른다).
 */
data class StationRecipe(
    val id: String,
    val results: List<Part> = emptyList(),
    val ingredients: List<Part> = emptyList(),
    val level: Int = 0,
    val permission: String = "",
    val seconds: Int = 0,
) {
    fun save(section: ConfigurationSection) {
        saveParts(section, "results", results)
        saveParts(section, "ingredients", ingredients)
        if (level > 0) section.set("level", level)
        if (permission.isNotBlank()) section.set("permission", permission)
        if (seconds > 0) section.set("seconds", seconds)
    }

    companion object {
        fun load(id: String, section: ConfigurationSection): StationRecipe = StationRecipe(
            id = id,
            results = loadParts(section, "results"),
            ingredients = loadParts(section, "ingredients"),
            level = section.getInt("level", 0).coerceAtLeast(0),
            permission = section.getString("permission").orEmpty(),
            seconds = section.getInt("seconds", 0).coerceAtLeast(0),
        )
    }
}

/**
 * 제작대. `/제작 <id>` 로 열거나, 연결한 블록을 우클릭해 연다.
 *
 * @param blocks 연결한 블록. `월드,x,y,z`.
 */
data class Station(
    val id: String,
    val name: String,
    val recipes: List<StationRecipe> = emptyList(),
    val blocks: List<String> = emptyList(),
) {
    fun recipe(id: String): StationRecipe? = recipes.firstOrNull { it.id == id }

    fun save(section: ConfigurationSection) {
        section.set("name", name)
        if (blocks.isNotEmpty()) section.set("blocks", blocks)
        val node = section.createSection("recipes")
        for (recipe in recipes) recipe.save(node.createSection(recipe.id))
    }

    companion object {
        fun load(id: String, section: ConfigurationSection): Station = Station(
            id = id,
            name = section.getString("name") ?: id,
            recipes = section.getConfigurationSection("recipes")?.let { node ->
                node.getKeys(false).mapNotNull { key -> node.getConfigurationSection(key)?.let { StationRecipe.load(key.lowercase(), it) } }
            }.orEmpty(),
            blocks = section.getStringList("blocks"),
        )

        fun blockKey(world: String, x: Int, y: Int, z: Int): String = "$world,$x,$y,$z"
    }
}

/** `stations.yml`. */
class StationRegistry(custom: CustomItems) : YamlFileStore(
    io = custom.io,
    path = listOf("stations.yml"),
    header = """
        제작대. /커스텀아이템 관리 → 제작 에서 GUI 로 고치는 것을 권장합니다(재료·결과는 화면에 끌어다 넣습니다).
        플레이어는 /제작 <제작대> 로 열거나 연결한 블록을 우클릭합니다.

        seconds  0 이면 바로 받습니다. 아니면 대기열에 들어가 그만큼 뒤에 받습니다(접속을 끊어도 흐릅니다).
        level    바닐라 레벨. permission 비우면 누구나.
    """.trimIndent() + "\n",
    what = "제작대",
) {

    private val stations = LinkedHashMap<String, Station>()

    /** "월드,x,y,z" → 제작대 id. */
    private val byBlock = HashMap<String, String>()

    fun all(): List<Station> = stations.values.toList()

    fun get(id: String?): Station? = id?.let { stations[it.lowercase()] }

    fun at(block: String): Station? = byBlock[block]?.let { stations[it] }

    fun put(station: Station) {
        stations[station.id] = station
        reindex()
        markDirty()
    }

    fun remove(id: String): Boolean = (stations.remove(id.lowercase()) != null).also {
        if (it) {
            reindex()
            markDirty()
        }
    }

    private fun reindex() {
        byBlock.clear()
        for (station in stations.values) for (block in station.blocks) byBlock[block] = station.id
    }

    override fun read(config: YamlConfiguration) {
        stations.clear()
        for (key in config.getKeys(false)) config.getConfigurationSection(key)?.let { stations[key.lowercase()] = Station.load(key.lowercase(), it) }
        reindex()
    }

    override fun write(config: YamlConfiguration) {
        for (station in stations.values) station.save(config.createSection(station.id))
    }
}
