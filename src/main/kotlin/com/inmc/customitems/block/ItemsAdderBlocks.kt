package com.inmc.customitems.block

import com.inmc.customitems.item.BlockKind
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * 옛 ItemsAdder 의 블록 정의를 읽는다(`plugins/ItemsAdder/contents/<묶음>/config` 또는 `configs` 폴더의 yml). 옮기기는 [CustomBlocks.importItemsAdder].
 *
 * 블록마다 **상태는 팩에서 찾는다** — IA 는 설정에 상태를 적지 않고 팩의 blockstates 에 "이 상태 = 이 모델" 로만 남긴다. 그래서 블록의 모델
 * (`resource.model_path`, 없으면 IA 가 만든 `<묶음>:item/ia_auto/<id>`)을 [states] 에서 거꾸로 찾아 같은 상태를 준다. 그래야 이미 월드에
 * 놓인 옛 블록이 새 아이템으로 이어진다.
 *
 * 옮기는 것: 방식(REAL_NOTE → 꽉 찬 블록 · REAL_TRANSPARENT → 투명) · 이름 · 설명 · 모델 · `drop_when_mined` · `hardness` ·
 * `break_tools_whitelist`(한 갈래의 도구만 적혀 있으면 → 맞는 도구 + 가장 낮은 등급 + "맞는 도구여야 부서짐" — IA 의 뜻이 "이것만 부순다"라서).
 * 광석 생성(`worlds_populators`)은 그 블록의 월드 생성([OreGen])으로(2026-10-09) — 한 블록에 여럿이면 첫 것만.
 * **안 옮기는 것**: 소리·부술 때 명령어(`events`)·IA 의 드랍 표(`loots/`) — 이 플러그인에 그 기능이 없거나 파일이 따로다.
 */
object ItemsAdderBlocks {

    data class Found(
        val id: String,
        val kind: BlockKind,
        val model: String,
        val state: String,
        val name: String,
        val lore: List<String>,
        val drop: Boolean,
        val hardness: Double? = null,
        val tool: ToolKind? = null,
        val toolTier: Int = 0,
    )

    /**
     * @param noState 팩에서 모델의 상태를 못 찾은 것 · [unsupported] 이 플러그인에 없는 방식(REAL·REAL_WIRE·TILE …).
     * @param populators 블록 id(이름공간 없이, 소문자) → 광석 생성.
     */
    data class Scan(val found: List<Found>, val noState: List<String>, val unsupported: List<String>, val populators: Map<String, OreGen> = emptyMap())

    fun scan(contents: File, states: Map<BlockKind, Map<String, String>>): Scan {
        val byModel = states.mapValues { (_, map) -> map.entries.associate { (key, model) -> model to key } }
        val found = ArrayList<Found>()
        val noState = ArrayList<String>()
        val unsupported = ArrayList<String>()
        val populators = LinkedHashMap<String, OreGen>()
        val files = contents.listFiles()?.sortedBy { it.name }.orEmpty()
            .flatMap { pack -> listOf("config", "configs").mapNotNull { File(pack, it).takeIf(File::isDirectory) } }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.name.endsWith(".yml") }.sortedBy { it.path }.toList() }
        for (file in files) {
            val yaml = runCatching { YamlConfiguration.loadConfiguration(file) }.getOrNull() ?: continue
            yaml.getConfigurationSection("worlds_populators")?.let { section ->
                for ((id, gen) in populators(section)) populators.putIfAbsent(id, gen)
            }
            val namespace = yaml.getString("info.namespace")?.trim()?.lowercase() ?: continue
            val items = yaml.getConfigurationSection("items") ?: continue
            for (key in items.getKeys(false)) {
                val item = items.getConfigurationSection(key) ?: continue
                if (!item.getBoolean("enabled", true)) continue
                val placed = item.getConfigurationSection("specific_properties.block.placed_model") ?: continue
                val id = key.lowercase()
                val kind = when (placed.getString("type")?.uppercase()) {
                    "REAL_NOTE" -> BlockKind.SOLID
                    "REAL_TRANSPARENT" -> BlockKind.TRANSPARENT
                    else -> {
                        unsupported += "$namespace:$id (" + placed.getString("type") + ")"
                        continue
                    }
                }
                val model = item.getString("resource.model_path")?.trim()?.takeIf { it.isNotEmpty() }?.let { "$namespace:$it" }
                    ?: "$namespace:item/ia_auto/$key"
                val whitelist = whitelist(item.getStringList("specific_properties.block.break_tools_whitelist"))
                val state = byModel[kind]?.get(model)
                if (state == null) {
                    noState += "$namespace:$id"
                    continue
                }
                found += Found(
                    id = id,
                    kind = kind,
                    model = model,
                    state = state,
                    name = item.getString("display_name").orEmpty(),
                    lore = item.getStringList("lore"),
                    drop = item.getBoolean("specific_properties.block.drop_when_mined", true),
                    hardness = if (item.isSet("specific_properties.block.hardness")) item.getDouble("specific_properties.block.hardness").coerceAtLeast(0.0) else null,
                    tool = whitelist?.first,
                    toolTier = whitelist?.second ?: 0,
                )
            }
        }
        return Scan(found, noState, unsupported, populators)
    }

    /**
     * IA `worlds_populators` 한 묶음 → (블록 id, 월드 생성). IA 의 `chunk_chance`·`chunk_veins`·`vein_blocks`·`min_height`·`max_height`·
     * `replaceable_blocks`·`worlds` 가 우리 [OreGen] 과 같은 뜻이다. 바꿀 블록이 하나도 안 읽히면 기본(땅속 돌 종류).
     */
    internal fun populators(section: org.bukkit.configuration.ConfigurationSection): List<Pair<String, OreGen>> =
        section.getKeys(false).mapNotNull { key ->
            val node = section.getConfigurationSection(key) ?: return@mapNotNull null
            if (!node.getBoolean("enabled", true)) return@mapNotNull null
            val id = node.getString("block")?.substringAfter(':')?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val replace = node.getStringList("replaceable_blocks").mapNotNull { org.bukkit.Material.matchMaterial(it.trim()) }.distinct()
            val low = node.getInt("min_height", 0)
            val high = node.getInt("max_height", 64)
            id to OreGen(
                worlds = node.getStringList("worlds").map { it.trim() }.filter { it.isNotEmpty() },
                replace = replace.ifEmpty { OreGen.DEFAULT_REPLACE },
                minY = minOf(low, high).coerceIn(OreGen.MIN_Y, OreGen.MAX_Y),
                maxY = maxOf(low, high).coerceIn(OreGen.MIN_Y, OreGen.MAX_Y),
                chunkChance = node.getDouble("chunk_chance", 30.0).coerceIn(0.0, 100.0),
                veins = node.getInt("chunk_veins", 1).coerceIn(1, OreGen.MAX_VEINS),
                veinSize = node.getInt("vein_blocks", 4).coerceIn(1, OreGen.MAX_VEIN_SIZE),
            )
        }

    /**
     * `[DIAMOND_PICKAXE, NETHERITE_PICKAXE]` → (곡괭이, 3). 도구 갈래가 하나일 때만 — 곡괭이와 도끼가 섞여 있으면 우리 규칙(맞는 도구 하나)으로
     * 옮길 수 없어 null(도구 제한 없이 옮긴다). 재질 앞말이 없으면(`PICKAXE`) 등급 0.
     */
    internal fun whitelist(entries: List<String>): Pair<ToolKind, Int>? {
        val tools = entries.map { it.trim() }.filter { it.isNotEmpty() }.map { it.substringAfter(':') }
        if (tools.isEmpty()) return null
        val kinds = tools.map { ToolKind.ofName(it) ?: return null }.toSet()
        val kind = kinds.singleOrNull() ?: return null
        return kind to tools.minOf { ToolGrades.held(it).tier }
    }
}
