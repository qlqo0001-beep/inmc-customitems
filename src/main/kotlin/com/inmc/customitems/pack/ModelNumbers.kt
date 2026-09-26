package com.inmc.customitems.pack

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 리소스팩이 **번호(custom_model_data)** 로 바꿔 그리는 모델의 목록. 서버 없이 도는 순수 읽기다.
 *
 * 두 모양을 다 읽는다.
 * - 낡은 방식: `assets/minecraft/models/item/<재질>.json` 의 `overrides` — `predicate.custom_model_data`
 * - 1.21.4+: `assets/minecraft/items/<재질>.json` 의 `range_dispatch`(속성 `custom_model_data`) — `entries[].threshold`
 *
 * 번호가 겹치면(두 팩이 같은 재질·같은 번호) 합칠 때 한쪽이 조용히 진다 — 목록이 그걸 보여 주는 게 이 기능의 절반이다.
 */
object ModelNumbers {

    /** @param source 어느 팩에서 왔나(팩 파일 이름). */
    data class Entry(val material: String, val number: Int, val model: String, val source: String)

    private const val LEGACY = "assets/minecraft/models/item/"
    private const val ITEMS = "assets/minecraft/items/"

    fun scan(source: String, files: Map<String, ByteArray>): List<Entry> {
        val out = ArrayList<Entry>()
        for ((path, bytes) in files) {
            if (!path.endsWith(".json")) continue
            val material = when {
                path.startsWith(LEGACY) -> path.removePrefix(LEGACY)
                path.startsWith(ITEMS) -> path.removePrefix(ITEMS)
                else -> continue
            }.removeSuffix(".json")
            if ('/' in material) continue
            val json = runCatching { JsonParser.parseString(String(bytes, Charsets.UTF_8)) }.getOrNull() as? JsonObject ?: continue
            if (path.startsWith(LEGACY)) legacy(material, json, source, out) else dispatch(material, json, source, out)
        }
        return out.sortedWith(compareBy({ it.material }, { it.number }))
    }

    private fun legacy(material: String, json: JsonObject, source: String, out: MutableList<Entry>) {
        val overrides = json.getAsJsonArray("overrides") ?: return
        for (element in overrides) {
            val entry = element as? JsonObject ?: continue
            val number = entry.getAsJsonObject("predicate")?.get("custom_model_data")?.let(::number) ?: continue
            out += Entry(material, number, entry.get("model")?.asStringOrNull().orEmpty(), source)
        }
    }

    /** `range_dispatch` 는 조건·선택 안에 숨어 있을 수 있어 나무 전체를 훑는다. */
    private fun dispatch(material: String, json: JsonObject, source: String, out: MutableList<Entry>) {
        fun walk(node: JsonElement?) {
            when (node) {
                is JsonObject -> {
                    val type = node.get("type")?.asStringOrNull()?.removePrefix("minecraft:")
                    val property = node.get("property")?.asStringOrNull()?.removePrefix("minecraft:")
                    if (type == "range_dispatch" && property == "custom_model_data") {
                        for (element in node.getAsJsonArray("entries") ?: return) {
                            val entry = element as? JsonObject ?: continue
                            val number = entry.get("threshold")?.let(::number) ?: continue
                            out += Entry(material, number, modelName(entry.get("model")), source)
                        }
                    }
                    for ((_, child) in node.entrySet()) walk(child)
                }
                is com.google.gson.JsonArray -> for (child in node) walk(child)
                else -> Unit
            }
        }
        walk(json.get("model"))
    }

    /** `{type: model, model: "ns:item/x"}` 면 그 이름, 아니면 종류라도. */
    private fun modelName(node: JsonElement?): String {
        val obj = node as? JsonObject ?: return ""
        obj.get("model")?.asStringOrNull()?.let { return it }
        return obj.get("type")?.asStringOrNull().orEmpty()
    }

    private fun number(element: JsonElement): Int? = runCatching { element.asDouble.toInt() }.getOrNull()

    private fun JsonElement.asStringOrNull(): String? = runCatching { asString }.getOrNull()

    /** 같은 재질·번호를 둘 이상의 팩이 쓰는 것. */
    fun conflicts(entries: List<Entry>): Set<Pair<String, Int>> =
        entries.groupBy { it.material to it.number }.filterValues { list -> list.map { it.source }.distinct().size > 1 }.keys
}
