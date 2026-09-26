package com.inmc.customitems

import com.inmc.customitems.craft.Part
import com.inmc.customitems.craft.RecipeDef
import com.inmc.customitems.craft.RecipeKind
import com.inmc.customitems.craft.Station
import com.inmc.customitems.craft.StationRecipe
import kr.inmc.core.item.ItemRef
import kr.inmc.core.item.StoredItem
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals

/** 제작대·바닐라 조합법의 모양. */
class CraftModelTest {

    private fun vanilla(material: Material) = StoredItem(ItemRef.Vanilla(material), material)

    private val ruby = StoredItem(ItemRef.Namespaced("inmc", "ruby"), Material.RED_DYE)

    @Test
    fun `제작대가 저장해도 그대로 읽힌다`() {
        val station = Station(
            id = "forge", name = "대장간", blocks = listOf("world,1,64,2"),
            recipes = listOf(StationRecipe("blade", results = listOf(Part(ruby, 1)), ingredients = listOf(Part(vanilla(Material.IRON_INGOT), 8), Part(ruby, 2)), level = 10, permission = "craft.vip", seconds = 300)),
        )
        val yaml = YamlConfiguration()
        station.save(yaml.createSection("forge"))
        val reread = YamlConfiguration().apply { loadFromString(yaml.saveToString()) }
        assertEquals(station, Station.load("forge", reread.getConfigurationSection("forge")!!))
    }

    @Test
    fun `조합법이 빈 칸 자리까지 그대로 읽힌다`() {
        val grid = MutableList<StoredItem?>(9) { null }.also {
            it[1] = ruby
            it[4] = vanilla(Material.STICK)
            it[7] = vanilla(Material.STICK)
        }
        val recipe = RecipeDef("ruby_sword", RecipeKind.SHAPED, Part(ruby, 1), grid)
        val yaml = YamlConfiguration()
        recipe.save(yaml.createSection("ruby_sword"))
        val reread = YamlConfiguration().apply { loadFromString(yaml.saveToString()) }
        val loaded = RecipeDef.load("ruby_sword", reread.getConfigurationSection("ruby_sword")!!)
        assertEquals(recipe, loaded)
        assertEquals(null, loaded.slot(0))
        assertEquals(ruby, loaded.slot(1))
    }

    @Test
    fun `굽기 조합은 시간과 경험치를 갖는다`() {
        val recipe = RecipeDef("ruby_smelt", RecipeKind.BLASTING, Part(ruby, 1), listOf(vanilla(Material.REDSTONE_BLOCK)), cookSeconds = 5.0, exp = 1.5)
        val yaml = YamlConfiguration()
        recipe.save(yaml.createSection("x"))
        assertEquals(recipe.copy(id = "x"), RecipeDef.load("x", YamlConfiguration().apply { loadFromString(yaml.saveToString()) }.getConfigurationSection("x")!!))
    }

    @Test
    fun `종류마다 재료 칸 수가 맞다`() {
        assertEquals(9, RecipeKind.SHAPED.slots)
        assertEquals(3, RecipeKind.SMITHING.slots)
        assertEquals(1, RecipeKind.STONECUTTING.slots)
        assertEquals(listOf(RecipeKind.FURNACE, RecipeKind.BLASTING, RecipeKind.SMOKING, RecipeKind.CAMPFIRE), RecipeKind.entries.filter { it.cooking })
    }
}
