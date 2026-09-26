package com.inmc.customitems.craft

import com.inmc.customitems.CustomItems
import kr.inmc.core.item.StoredItem
import kr.inmc.core.store.YamlFileStore
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration

/** 바닐라 조합법의 자리. [slots] 는 재료 칸 수. */
enum class RecipeKind(val label: String, val slots: Int) {
    SHAPED("작업대 (모양대로)", 9),
    SHAPELESS("작업대 (모양 상관없이)", 9),
    FURNACE("화로", 1),
    BLASTING("용광로", 1),
    SMOKING("훈연기", 1),
    CAMPFIRE("모닥불", 1),
    SMITHING("대장장이대 (형판·재료·추가)", 3),
    STONECUTTING("석재 절단기", 1),
    ;

    val cooking: Boolean get() = this == FURNACE || this == BLASTING || this == SMOKING || this == CAMPFIRE

    companion object {
        fun of(raw: String?): RecipeKind = entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: SHAPED
    }
}

/**
 * 바닐라 조합법 하나 — 작업대·화로·대장장이대·석재 절단기에 더해진다.
 *
 * @param grid 재료 칸. 작업대는 3×3 을 왼쪽 위부터(빈 칸은 null), 굽기·절단은 첫 칸, 대장장이대는 형판·재료·추가.
 */
data class RecipeDef(
    val id: String,
    val kind: RecipeKind = RecipeKind.SHAPED,
    val result: Part? = null,
    val grid: List<StoredItem?> = emptyList(),
    val cookSeconds: Double = 10.0,
    val exp: Double = 0.0,
) {
    fun slot(index: Int): StoredItem? = grid.getOrNull(index)

    fun save(section: ConfigurationSection) {
        section.set("kind", kind.name)
        result?.let { part ->
            val node = section.createSection("result")
            part.item.save(node)
            node.set("amount", part.amount)
        }
        val node = section.createSection("grid")
        for ((index, item) in grid.withIndex()) item?.save(node.createSection(index.toString()))
        if (kind.cooking) {
            section.set("cook-seconds", cookSeconds)
            if (exp > 0.0) section.set("exp", exp)
        }
    }

    companion object {
        fun load(id: String, section: ConfigurationSection): RecipeDef {
            val kind = RecipeKind.of(section.getString("kind"))
            val grid = MutableList<StoredItem?>(kind.slots) { null }
            section.getConfigurationSection("grid")?.let { node ->
                for (key in node.getKeys(false)) {
                    val index = key.toIntOrNull()?.takeIf { it in grid.indices } ?: continue
                    grid[index] = node.getConfigurationSection(key)?.let(StoredItem::load)
                }
            }
            return RecipeDef(
                id = id,
                kind = kind,
                result = section.getConfigurationSection("result")?.let { node -> StoredItem.load(node)?.let { Part(it, node.getInt("amount", 1).coerceIn(1, 64)) } },
                grid = grid,
                cookSeconds = section.getDouble("cook-seconds", 10.0).coerceIn(0.05, 3600.0),
                exp = section.getDouble("exp", 0.0).coerceAtLeast(0.0),
            )
        }
    }
}

/** `recipes.yml`. 바뀔 때마다 서버의 조합법을 다시 건다([RecipeService.apply]). */
class RecipeRegistry(private val custom: CustomItems) : YamlFileStore(
    io = custom.io,
    path = listOf("recipes.yml"),
    header = """
        바닐라 조합법(작업대·화로·용광로·훈연기·모닥불·대장장이대·석재 절단기).
        /커스텀아이템 관리 → 제작 → 바닐라 조합법 에서 GUI 로 고치는 것을 권장합니다(칸에 아이템을 넣습니다).
        재료에 커스텀 아이템을 쓰면 그 아이템만 받습니다. 결과가 커스텀 아이템이면 만들 때마다 새로 굴립니다.
    """.trimIndent() + "\n",
    what = "조합법",
) {

    private val recipes = LinkedHashMap<String, RecipeDef>()

    fun all(): List<RecipeDef> = recipes.values.toList()

    fun get(id: String?): RecipeDef? = id?.let { recipes[it.lowercase()] }

    fun put(recipe: RecipeDef) {
        recipes[recipe.id] = recipe
        markDirty()
        custom.recipeService.apply()
    }

    fun remove(id: String): Boolean = (recipes.remove(id.lowercase()) != null).also {
        if (it) {
            markDirty()
            custom.recipeService.apply()
        }
    }

    override fun read(config: YamlConfiguration) {
        recipes.clear()
        for (key in config.getKeys(false)) config.getConfigurationSection(key)?.let { recipes[key.lowercase()] = RecipeDef.load(key.lowercase(), it) }
    }

    override fun write(config: YamlConfiguration) {
        for (recipe in recipes.values) recipe.save(config.createSection(recipe.id))
    }
}
