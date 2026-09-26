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
 * 옮기는 것: 방식(REAL_NOTE → 꽉 찬 블록 · REAL_TRANSPARENT → 투명) · 이름 · 설명 · 모델 · `drop_when_mined`.
 * **안 옮기는 것**: 단단함·도구 제한·소리·부술 때 명령어(`events`)·광석 생성(`worlds_populators`) — 이 플러그인에 그 기능이 없다.
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
    )

    /** @param noState 팩에서 모델의 상태를 못 찾은 것 · [unsupported] 이 플러그인에 없는 방식(REAL·REAL_WIRE·TILE …). */
    data class Scan(val found: List<Found>, val noState: List<String>, val unsupported: List<String>)

    fun scan(contents: File, states: Map<BlockKind, Map<String, String>>): Scan {
        val byModel = states.mapValues { (_, map) -> map.entries.associate { (key, model) -> model to key } }
        val found = ArrayList<Found>()
        val noState = ArrayList<String>()
        val unsupported = ArrayList<String>()
        val files = contents.listFiles()?.sortedBy { it.name }.orEmpty()
            .flatMap { pack -> listOf("config", "configs").mapNotNull { File(pack, it).takeIf(File::isDirectory) } }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.name.endsWith(".yml") }.sortedBy { it.path }.toList() }
        for (file in files) {
            val yaml = runCatching { YamlConfiguration.loadConfiguration(file) }.getOrNull() ?: continue
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
                )
            }
        }
        return Scan(found, noState, unsupported)
    }
}
