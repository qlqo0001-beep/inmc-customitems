package com.inmc.customitems

import com.google.gson.JsonParser
import com.inmc.customitems.craft.RecipeDef
import com.inmc.customitems.craft.Station
import com.inmc.customitems.item.Category
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.ItemSet
import com.inmc.customitems.item.ItemType
import com.inmc.customitems.item.Tier
import com.inmc.customitems.item.UpgradeTable
import com.inmc.customitems.item.Upgrades
import com.inmc.customitems.pack.JsonMerge
import com.inmc.customitems.pack.PackAssets
import kr.inmc.core.item.ItemRef
import kr.inmc.core.item.StoredItem
import org.bukkit.configuration.file.YamlConfiguration
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 배포 기본값(MMOItems 에서 옮겨 온 아이템·강화 방식·세트·제작대·조합법과 기본 리소스팩).
 *
 * 파일마다 따로 읽히므로 **서로를 가리키는 이름이 끊겨도 오류가 나지 않는다** — 강화석이 없는 방식을 가리키면
 * 그 강화석이 조용히 아무것도 못 하고, 제작대 재료가 없는 아이템이면 그 조합법이 영영 안 된다.
 */
class DefaultContentTest {

    private fun yaml(path: String): YamlConfiguration {
        val stream = javaClass.classLoader.getResourceAsStream(path)
        assertNotNull(stream, "리소스를 찾을 수 없습니다: $path")
        return stream.use { YamlConfiguration.loadConfiguration(InputStreamReader(it, StandardCharsets.UTF_8)) }
    }

    private val items: Map<String, CustomItem> by lazy {
        val root = yaml("items.yml").getConfigurationSection("items")!!
        root.getKeys(false).associateWith { CustomItem.load(it, root.getConfigurationSection(it)!!)!! }
    }

    private val tables: Map<String, UpgradeTable> by lazy {
        val config = yaml("upgrades.yml")
        config.getKeys(false).associateWith { UpgradeTable.load(it, config.getConfigurationSection(it)!!) }
    }

    private val stations: List<Station> by lazy {
        val config = yaml("stations.yml")
        config.getKeys(false).map { Station.load(it, config.getConfigurationSection(it)!!) }
    }

    private val recipes: List<RecipeDef> by lazy {
        val config = yaml("recipes.yml")
        config.getKeys(false).map { RecipeDef.load(it, config.getConfigurationSection(it)!!) }
    }

    private fun assertOurs(item: StoredItem?, where: String) {
        val ref = item?.ref as? ItemRef.Namespaced ?: return
        if (ref.namespace != "inmc") return
        assertTrue(ref.id in items, "$where 이(가) 없는 아이템 ${ref.id} 를 가리킵니다")
    }

    @Test
    fun `강화 방식과 강화석과 세트가 가리키는 이름이 전부 있다`() {
        val sets = yaml("sets.yml").getKeys(false)
        for (item in items.values) {
            val spec = item.upgrade
            if (spec.template.isNotBlank()) assertTrue(spec.template in tables, "${item.id} 의 강화 방식 ${spec.template} 이 없습니다")
            for (stone in spec.stones) assertTrue(items[stone]?.consume?.upgrade != null, "${item.id} 가 강화석이 아닌 $stone 을 받습니다")
            item.consume?.upgrade?.let { stone ->
                for (template in stone.templates) assertTrue(template in tables, "${item.id} 강화석의 방식 $template 이 없습니다")
                for (target in stone.items) assertTrue(target in items, "${item.id} 강화석의 대상 $target 이 없습니다")
            }
            if (item.set.isNotBlank()) assertTrue(item.set in sets, "${item.id} 의 세트 ${item.set} 이 없습니다")
        }
    }

    @Test
    fun `아이템의 소분류가 있고 같은 종류의 것이다`() {
        val config = yaml("categories.yml")
        val categories = config.getKeys(false).associateWith { Category.load(it, config.getConfigurationSection(it)!!) }
        assertTrue(categories.isNotEmpty())
        for (id in categories.keys) assertTrue(kr.inmc.core.store.DefinitionKey.isValid(id), "소분류 id $id 는 규칙에 안 맞습니다")
        for (item in items.values) {
            if (item.category.isBlank()) continue
            val category = categories[item.category]
            assertNotNull(category, "${item.id} 의 소분류 ${item.category} 가 categories.yml 에 없습니다")
            assertEquals(category.type, item.customType.ifBlank { item.type.id }, "${item.id} 는 ${item.type} 인데 소분류 ${item.category} 는 ${category.type} 것입니다")
        }
        // 빈 서랍은 누르면 아무것도 없어 헷갈린다.
        for ((id, category) in categories) assertTrue(items.values.any { it.category == id }, "소분류 $id(${category.type}) 에 든 아이템이 없습니다")
    }

    @Test
    fun `세트 효과가 전부 읽힌다`() {
        val config = yaml("sets.yml")
        for (id in config.getKeys(false)) {
            val set = ItemSet.load(id, config.getConfigurationSection(id)!!)
            assertTrue(set.bonuses.isNotEmpty() && set.bonuses.values.all { it.stats.isNotEmpty() }, "$id 세트의 효과가 비었습니다 — 능력치 이름이 틀렸을 수 있습니다")
        }
    }

    @Test
    fun `제작대와 조합법의 재료와 결과가 전부 있다`() {
        val craftId = Regex("^[a-z0-9_]{1,32}$")
        assertTrue(stations.isNotEmpty())
        for (station in stations) {
            assertTrue(craftId.matches(station.id), "제작대 id ${station.id} 는 /제작 에 못 씁니다")
            assertTrue(station.recipes.isNotEmpty(), "${station.id} 에 조합법이 없습니다")
            for (recipe in station.recipes) {
                assertTrue(craftId.matches(recipe.id), "${station.id}/${recipe.id} 는 조합법 id 규칙에 안 맞습니다")
                assertTrue(recipe.results.isNotEmpty() && recipe.ingredients.isNotEmpty(), "${station.id}/${recipe.id} 가 비었습니다")
                for (part in recipe.results + recipe.ingredients) assertOurs(part.item, "${station.id}/${recipe.id}")
            }
        }
        for (recipe in recipes) {
            assertTrue(craftId.matches(recipe.id), "조합법 id ${recipe.id} 는 마인크래프트 열쇠가 못 됩니다")
            assertNotNull(recipe.result, "${recipe.id} 에 결과가 없습니다")
            assertOurs(recipe.result.item, recipe.id)
            assertTrue(recipe.grid.any { it != null }, "${recipe.id} 에 재료가 없습니다")
            for (cell in recipe.grid) assertOurs(cell, recipe.id)
        }
    }

    @Test
    fun `유물은 타임스톤으로만 오른다 — 재료이면서 강화석, 옛 이벤트 교환권은 없다`() {
        val relic = items.getValue("강화유물")
        assertEquals(ItemType.RELIC, relic.type)
        val table = relic.upgrade.own!!
        assertEquals(10, table.maxLevel)
        assertEquals(30.0, table.step(1)!!.chance)
        assertEquals(0.1, table.step(10)!!.chance)
        assertEquals(Tier.MYTHIC, table.tierAt(10))

        // 사용자 결정 2026-10-01: 유물 강화 아이템은 타임스톤 하나 — 재료 종류인데 강화석으로도 쓴다. 소모품의 교환권은 없앴다.
        assertEquals(listOf("타임스톤"), relic.upgrade.stones)
        val time = items.getValue("타임스톤")
        assertEquals(ItemType.MATERIAL, time.type)
        val stone = time.consume!!.upgrade!!
        assertNull(Upgrades.refuse("타임스톤", stone, relic, 0))
        assertNull(Upgrades.refuse("타임스톤", stone, relic, 9))
        assertTrue(items.keys.none { it.startsWith("이벤트교환권") }, "유물 교환권이 남았다")
        // 평범한 강화석은 유물에 못 쓴다(MMOItems 에서도 유물은 강화석을 받지 않았다).
        val common = items.getValue("티어1-하급").consume!!.upgrade!!
        assertEquals("upgrade-wrong-stone", Upgrades.refuse("티어1-하급", common, relic, 0))
    }

    @Test
    fun `강화석은 대상의 등급마다 확률이 다르다`() {
        val stone = items.getValue("티어1-하급").consume!!.upgrade!!
        val weapon = items.values.first { it.upgrade.template == "희귀" }
        assertNull(Upgrades.refuse("티어1-하급", stone, weapon, 0))
        val step = tables.getValue("희귀").step(1)!!
        assertEquals(10.0, Upgrades.chance(step, stone, Tier.COMMON))
        assertEquals(0.01, Upgrades.chance(step, stone, Tier.MYTHIC))
    }

    @Test
    fun `무기의 공격력과 공격 속도는 속성 값으로 옮겨졌다`() {
        // MMOItems 의 7 · 1.6 은 합계다. 속성으로는 6 · -2.4 — 그대로 옮기면 맨손 1 과 기본 4 가 한 번 더 붙는다.
        val sword = items.getValue("강화돌검")
        assertEquals(6.0, sword.stats.getValue(com.inmc.customitems.item.Stat.ATTACK_DAMAGE))
        assertEquals(-2.4, sword.stats.getValue(com.inmc.customitems.item.Stat.ATTACK_SPEED), 1e-9)
    }

    @Test
    fun `기본 리소스팩이 들어 있고 합쳐도 오버레이를 잃지 않는다`() {
        val stream = javaClass.classLoader.getResourceAsStream("pack/default-pack.zip")
        assertNotNull(stream, "기본 리소스팩이 jar 에 없습니다")
        var mcmeta: String? = null
        ZipInputStream(stream).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "pack.mcmeta") mcmeta = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertNotNull(mcmeta, "기본 팩에 pack.mcmeta 가 없습니다")
        val merged = JsonParser.parseString(JsonMerge.merge("pack.mcmeta", PackAssets.mcmetaJson("INMC"), mcmeta)).asJsonObject
        assertTrue(merged.has("overlays"), "오버레이를 잃으면 1.21.4+ 클라이언트가 모델 번호를 못 읽습니다")
        assertTrue(merged.getAsJsonObject("pack").get("pack_format").asInt >= PackAssets.PACK_FORMAT)
    }

    /** 기본 팩을 파일 표로(경로 → 내용). 팩 합치기와 같은 모양. */
    private val defaultPack: Map<String, ByteArray> by lazy {
        val files = LinkedHashMap<String, ByteArray>()
        ZipInputStream(assertNotNull(javaClass.classLoader.getResourceAsStream("pack/default-pack.zip"))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) files[entry.name] = zip.readBytes()
            }
        }
        files
    }

    @Test
    fun `옮겨 온 아이템의 모델 번호는 전부 기본 팩의 모양으로 옮겨진다`() {
        // 번호가 남은 아이템이 빌드에서 모양을 잃지 않는다 — 찾으면 모델, 바닐라로 그려지던 번호면 null(번호만 뗀다).
        val roots = com.inmc.customitems.pack.NumberMigration.activeRoots(defaultPack)
        val numbered = items.values.filter { it.customModelData > 0 }
        // 105 → 95: 유물 교환권 열 개를 뺐다(사용자 2026-10-01).
        assertTrue(numbered.size >= 90, "옮겨 온 MMOItems 아이템의 번호가 있어야 한다")
        val resolved = numbered.associateWith { com.inmc.customitems.pack.NumberMigration.resolve(defaultPack, it.material.name.lowercase(), it.customModelData, roots) }
        val vanilla = resolved.filterValues { it == null }.keys.map { it.id }.toSet()
        assertEquals(setOf("2차낚싯대", "3차낚싯대", "4차낚싯대", "5차낚싯대"), vanilla, "기본 팩이 이 번호를 바닐라 낚싯대로 채워 둔 것만 바닐라")
        for ((item, target) in resolved) {
            if (target == null) continue
            assertTrue(':' in target.model && !target.model.startsWith("minecraft:"), item.id + " → " + target.model)
        }
    }

    @Test
    fun `기본 팩의 플레이어 머리 정의는 번호 갈래뿐이라 걷어 낸다`() {
        val stripped = com.inmc.customitems.pack.NumberMigration.strip(defaultPack)
        val heads = stripped.changes.filterKeys { it.endsWith("assets/minecraft/items/player_head.json") }
        assertTrue(heads.isNotEmpty())
        assertTrue(heads.values.all { it == null }, "머리 정의가 남으면 다른 플러그인 화면의 평범한 머리가 옛 IA 정의로 그려진다")
    }
}
