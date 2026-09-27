package com.inmc.customitems.pack

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 낡은 번호 방식(`custom_model_data`)을 걷어 낸다 — 사용자 결정 2026-09-28 "옛 방식의 모델 번호를 없애고 최신 방식으로 전부".
 *
 * 1. **옮기기** [resolve]: 번호 아이템이 지금 팩에서 **실제로 무엇으로 그려지는지** 찾는다. 그 모양을 우리 아이템 정의
 *    (`assets/inmc/items/<id>.json`, `item_model`)로 옮기면 번호 없이 똑같이 보인다.
 * 2. **걷어 내기** [strip]: 바닐라 아이템 정의에 들어 있던 번호 갈래를 지운다. 번호로 그리던 것이 우리에게 더는 없으니 그 갈래는 이름만
 *    차지하고, 바닐라 아이템(다이아몬드 검 · 플레이어 머리 …)이 남의 팩 정의로 그려지는 원인이 된다 — 옛 IA 팩의 `player_head` 정의가
 *    평범한 머리를 이상하게 그렸다(2026-09-28).
 *
 * 게임이 고르는 것과 똑같이 고른다:
 * - `range_dispatch` 는 **번호 이하 중 가장 큰 threshold**(바닐라). 정확히 같은 번호가 없어도 그 아래 것이 그려진다
 * - 새 판 클라이언트는 **자기 형식에 맞는 오버레이** 폴더의 파일을 쓴다(mcmeta 순서, 뒤가 이긴다). 없으면 뿌리의 것
 * - 1.21.4+ 정의(`items/`)가 없으면 낡은 `models/item/` 의 `overrides` — 조건에 맞는 것 가운데 **마지막**
 *
 * **Bukkit 을 모른다.** 서버 없이 전부 검증한다.
 */
object NumberMigration {

    private const val ITEMS = "assets/minecraft/items/"
    private const val MODELS = "assets/minecraft/models/item/"

    /**
     * 옮길 곳. [definition] 이 null 이면 모델 하나([model])로 충분하다(`item/generated` 한 장 같은 것). 아니면 조건·색이 있는
     * 정의 통째(던진 낚싯대 · 당긴 활 …)이고, [model] 은 그 안의 대표 모델(화면에 보여 줄 이름).
     */
    data class Target(val model: String, val definition: String?)

    /** 걷어 낸 결과 — 경로 → 새 내용, **null 이면 지운다**(클라이언트의 바닐라 정의로 돌아간다). */
    data class Stripped(val changes: Map<String, ByteArray?>, val definitions: Int, val overrides: Int)

    // --- 옮기기 ----------------------------------------------------------------------

    /** 새 판 클라이언트가 읽는 뿌리들 — `""` 와 지금 형식에 맞는 오버레이 폴더(`dir/`), 낮은 우선순위부터. */
    fun activeRoots(files: Map<String, ByteArray>): List<String> {
        val mcmeta = files["pack.mcmeta"]?.let { parse(it) } ?: return listOf("")
        val pack = mcmeta.obj("pack")
        val format = listOfNotNull(pack?.get("pack_format")?.let(::major), pack?.get("max_format")?.let(::major)).maxOrNull() ?: return listOf("")
        val overlays = mcmeta.obj("overlays")?.arr("entries") ?: return listOf("")
        val roots = arrayListOf("")
        for (element in overlays) {
            val entry = element as? JsonObject ?: continue
            val directory = entry.get("directory")?.asStringOrNull()?.trim('/')?.takeIf { it.isNotEmpty() } ?: continue
            val (low, high) = runCatching { range(entry) }.getOrNull() ?: continue
            if (format in low..high) roots += "$directory/"
        }
        return roots
    }

    /**
     * [material](`fishing_rod`)의 [number] 번이 그려지는 모양. **바닐라 모양이면 null** — 번호 갈래가 없거나, 번호가 모든 갈래보다 작거나,
     * 고른 갈래가 바닐라 모델뿐일 때(옛 팩이 빈 번호를 바닐라로 채워 둔 것). 그런 아이템은 번호만 떼면 지금과 똑같이 보인다.
     */
    fun resolve(files: Map<String, ByteArray>, material: String, number: Int, roots: List<String> = activeRoots(files)): Target? {
        for (root in roots.asReversed()) {
            val bytes = files[root + ITEMS + material + ".json"] ?: continue
            // 가장 높은 우선순위의 정의 하나만 본다 — 게임도 그것만 읽는다.
            val node = pick(parse(bytes)?.get("model"), number) ?: return null
            return if (isVanilla(node)) null else target(node)
        }
        for (root in roots.asReversed()) {
            val bytes = files[root + MODELS + material + ".json"] ?: continue
            val overrides = parse(bytes)?.arr("overrides") ?: return null
            val model = overrides.mapNotNull { element ->
                val entry = element as? JsonObject ?: return@mapNotNull null
                val value = entry.obj("predicate")?.get("custom_model_data")?.asDoubleOrNull() ?: return@mapNotNull null
                entry.get("model")?.asStringOrNull()?.takeIf { value <= number }
            }.lastOrNull() ?: return null
            return namespaced(model).takeUnless { it.startsWith("minecraft:") }?.let { Target(it, null) }
        }
        return null
    }

    /** 맨 위가 번호 갈래면 그 번호의 모양. 없으면 null. */
    private fun pick(node: JsonElement?, number: Int): JsonObject? {
        val dispatch = node as? JsonObject ?: return null
        if (!isNumberDispatch(dispatch)) return null
        val scale = dispatch.get("scale")?.asDoubleOrNull() ?: 1.0
        val value = number * scale
        return dispatch.arr("entries")
            ?.mapNotNull { element -> (element as? JsonObject)?.let { entry -> entry.get("threshold")?.asDoubleOrNull()?.let { it to entry } } }
            ?.filter { it.first <= value }
            ?.maxByOrNull { it.first }
            ?.second?.get("model") as? JsonObject
    }

    private fun target(node: JsonObject): Target? {
        val type = node.get("type")?.asStringOrNull()?.removePrefix("minecraft:")
        val simple = type == "model" && node.keySet().all { it == "type" || it == "model" }
        if (simple) return node.get("model")?.asStringOrNull()?.let { Target(namespaced(it), null) }
        val primary = firstModel(node) ?: return null
        return Target(namespaced(primary), GSON.toJson(JsonObject().apply { add("model", node.deepCopy()) }))
    }

    /** 정의 안의 첫 모델 이름 — 화면에 보여 주고, 아이템을 "모양이 있는 것"으로 친다. */
    private fun firstModel(node: JsonElement?): String? = when (node) {
        is JsonObject -> {
            val type = node.get("type")?.asStringOrNull()?.removePrefix("minecraft:")
            if (type == "model") node.get("model")?.asStringOrNull() else node.entrySet().firstNotNullOfOrNull { firstModel(it.value) }
        }
        is JsonArray -> node.firstNotNullOfOrNull { firstModel(it) }
        else -> null
    }

    // --- 걷어 내기 --------------------------------------------------------------------

    /**
     * 바닐라 아이템 정의(`assets/minecraft/items/*.json`, 오버레이 안의 것도)의 **맨 위 번호 갈래**를 걷어 낸다.
     * - 번호가 아닐 때의 모양(`fallback`)이 바닐라 것뿐이면 **파일을 지운다** — 클라이언트가 제 판의 바닐라 정의를 쓴다(판마다 다른
     *   특수 모델까지 늘 맞다). 옛 IA 팩의 `player_head` 가 이 경우다
     * - `fallback` 이 남의 모델이면(바닐라 아이템을 새로 그린 팩) 그것만 남긴다 — 그 팩의 뜻은 살린다
     *
     * 낡은 `models/item/*.json` 은 번호 조건의 `overrides` 만 뺀다(나머지 — `parent`·`textures` — 는 그대로).
     */
    fun strip(files: Map<String, ByteArray>): Stripped {
        val changes = LinkedHashMap<String, ByteArray?>()
        var definitions = 0
        var overrides = 0
        for ((path, bytes) in files) {
            when {
                isUnder(path, ITEMS) -> {
                    val json = parse(bytes) ?: continue
                    val model = json.get("model") as? JsonObject ?: continue
                    if (!isNumberDispatch(model)) continue
                    val fallback = model.get("fallback") as? JsonObject
                    definitions++
                    if (fallback == null || isVanilla(fallback)) {
                        changes[path] = null
                    } else {
                        json.add("model", fallback)
                        changes[path] = GSON.toJson(json).toByteArray(Charsets.UTF_8)
                    }
                }
                isUnder(path, MODELS) -> {
                    val json = parse(bytes) ?: continue
                    val list = json.arr("overrides") ?: continue
                    val kept = JsonArray()
                    for (element in list) {
                        val predicate = (element as? JsonObject)?.obj("predicate")
                        if (predicate?.has("custom_model_data") == true) overrides++ else kept.add(element)
                    }
                    if (kept.size() == list.size()) continue
                    if (kept.isEmpty) json.remove("overrides") else json.add("overrides", kept)
                    changes[path] = GSON.toJson(json).toByteArray(Charsets.UTF_8)
                }
            }
        }
        return Stripped(changes, definitions, overrides)
    }

    /** `assets/minecraft/items/x.json` 또는 `<오버레이>/assets/minecraft/items/x.json`. */
    private fun isUnder(path: String, folder: String): Boolean {
        if (!path.endsWith(".json")) return false
        val at = path.indexOf(folder)
        if (at < 0) return false
        val prefix = path.substring(0, at)
        return (prefix.isEmpty() || prefix.count { it == '/' } == 1) && '/' !in path.substring(at + folder.length)
    }

    private fun isNumberDispatch(node: JsonObject): Boolean =
        node.get("type")?.asStringOrNull()?.removePrefix("minecraft:") == "range_dispatch" &&
            node.get("property")?.asStringOrNull()?.removePrefix("minecraft:") == "custom_model_data" &&
            (node.get("index")?.asDoubleOrNull() ?: 0.0) == 0.0

    /** 모든 모델 이름이 `minecraft:`(또는 이름공간 없음)인가. */
    private fun isVanilla(node: JsonElement): Boolean = when (node) {
        is JsonObject -> node.entrySet().all { (key, value) ->
            if ((key == "model" || key == "base") && value.isJsonPrimitive) value.asString.let { ':' !in it || it.startsWith("minecraft:") } else isVanilla(value)
        }
        is JsonArray -> node.all { isVanilla(it) }
        else -> true
    }

    // --- 잔일 -------------------------------------------------------------------------

    private fun namespaced(model: String): String = if (':' in model) model else "minecraft:$model"

    /** 오버레이의 형식 범위. `formats`(수 · [낮, 높] · {min_inclusive, max_inclusive}) 또는 `min_format`·`max_format`. */
    private fun range(entry: JsonObject): Pair<Int, Int>? {
        val low = entry.get("min_format")?.let(::major)
        val high = entry.get("max_format")?.let(::major)
        if (low != null && high != null) return low to high
        return when (val formats = entry.get("formats")) {
            null -> null
            is JsonArray -> if (formats.size() >= 2) formats[0].asInt to formats[1].asInt else null
            is JsonObject -> (formats.get("min_inclusive")?.asInt ?: return null) to (formats.get("max_inclusive")?.asInt ?: return null)
            else -> formats.asDoubleOrNull()?.toInt()?.let { it to it }
        }
    }

    /** `84` 또는 `[84, 0]` → 84. */
    private fun major(element: JsonElement): Int? = when {
        element.isJsonArray -> element.asJsonArray.firstOrNull()?.asDoubleOrNull()?.toInt()
        else -> element.asDoubleOrNull()?.toInt()
    }

    private fun parse(bytes: ByteArray): JsonObject? =
        runCatching { JsonParser.parseString(String(bytes, Charsets.UTF_8)) }.getOrNull() as? JsonObject

    private fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject

    private fun JsonObject.arr(key: String): JsonArray? = get(key) as? JsonArray

    private fun JsonElement.asStringOrNull(): String? = runCatching { asString }.getOrNull()

    private fun JsonElement.asDoubleOrNull(): Double? = runCatching { asDouble }.getOrNull()

    private val GSON = GsonBuilder().disableHtmlEscaping().create()
}
