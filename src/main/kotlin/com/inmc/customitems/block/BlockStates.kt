package com.inmc.customitems.block

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.inmc.customitems.item.BlockKind

/**
 * 블록 상태 방식 커스텀 블록의 **상태 공간** — 어느 상태가 비어 있고, 무엇이 무엇을 그리는가. ItemsAdder 의 REAL_NOTE·REAL_TRANSPARENT 와
 * 같은 방식이고 같은 상태 표기를 쓴다(옛 IA 팩과 월드에 놓인 옛 블록이 그대로 이어지게).
 *
 * | 종류 | 바닐라 블록 | 상태 | 바닐라 모습으로 남기는 것 |
 * |---|---|---|---|
 * | [BlockKind.SOLID] | 소리블록 | `instrument=…,note=0~24` (powered 는 무시) | `harp` 전부 — Paper 의 `disable-noteblock-updates` 를 켜면 놓는 소리블록은 늘 harp·0 이다 |
 * | [BlockKind.TRANSPARENT] | 후렴초 줄기 | 여섯 면의 참/거짓 | 전부 거짓(Paper 가 놓는 모습) · 전부 참 |
 *
 * **팩의 blockstates 파일은 통째로 우리가 쓴다.** 소리블록의 바닐라 파일은 `""` 한 줄이라 특정 상태와 같이 둘 수 없고(겹침),
 * 후렴초는 여러 조각(multipart)이라 한 상태에 모델 하나를 줄 수 없다. 그래서 커스텀 상태가 하나라도 있으면 모든 상태를 적은 파일을
 * 새로 만든다 — 커스텀이 아닌 상태는 바닐라와 똑같이 그리고(후렴초는 바닐라 조각에 "커스텀 상태가 아닐 때" 조건을 단다),
 * 남의 팩(`sources/`)이 쓰던 상태도 읽어 와 같이 적는다([mappings]).
 *
 * **Bukkit 을 모른다.** 서버 없이 전부 검증한다.
 */
object BlockStates {

    /**
     * 26.2 의 소리블록 악기(서버 jar 의 `NoteBlockInstrument` 순서). 새 판에 악기가 늘면 여기에 더해야 그 악기의 바닐라 모습이 팩에 적힌다
     * — 빠지면 그 악기로 놓인 소리블록이 보라·검정 칸이 된다(갱신 끄기를 켰으면 놓이는 소리블록은 늘 harp 라 드물다).
     */
    val INSTRUMENTS = listOf(
        "harp", "basedrum", "snare", "hat", "bass", "flute", "bell", "guitar", "chime", "xylophone", "iron_xylophone", "cow_bell",
        "didgeridoo", "bit", "banjo", "pling", "trumpet", "trumpet_exposed", "trumpet_oxidized", "trumpet_weathered",
        "zombie", "skeleton", "creeper", "dragon", "wither_skeleton", "piglin", "custom_head",
    )

    const val NOTES = 25

    /** 후렴초의 여섯 면 — 이름 순(ItemsAdder 가 적은 순서와 같다). */
    val FACES = listOf("down", "east", "north", "south", "up", "west")

    private const val VANILLA_NOTE = "minecraft:block/note_block"

    fun block(kind: BlockKind): String = when (kind) {
        BlockKind.SOLID -> "note_block"
        BlockKind.TRANSPARENT -> "chorus_plant"
        BlockKind.ENTITY -> error("엔티티 방식은 블록 상태를 쓰지 않는다")
    }

    fun path(kind: BlockKind): String = "assets/minecraft/blockstates/" + block(kind) + ".json"

    // --- 상태 열쇠 -------------------------------------------------------------------

    fun noteKey(instrument: String, note: Int): String = "instrument=$instrument,note=$note"

    fun chorusKey(mask: Int): String = FACES.withIndex().joinToString(",") { (i, face) -> face + "=" + ((mask shr (FACES.size - 1 - i)) and 1 == 1) }

    /** 바닐라 모습으로 남겨 두는 상태 — 커스텀에 내주지 않는다. */
    fun isReserved(kind: BlockKind, key: String): Boolean = when (kind) {
        BlockKind.SOLID -> key.startsWith("instrument=harp,")
        BlockKind.TRANSPARENT -> key == chorusKey(0) || key == chorusKey(63)
        BlockKind.ENTITY -> true
    }

    /** 새 블록에 내줄 상태 후보를 고르는 순서. */
    fun candidates(kind: BlockKind): List<String> = when (kind) {
        BlockKind.SOLID -> INSTRUMENTS.drop(1).flatMap { instrument -> (0 until NOTES).map { noteKey(instrument, it) } }
        BlockKind.TRANSPARENT -> (1 until 63).map(::chorusKey)
        BlockKind.ENTITY -> emptyList()
    }

    /** 빈 상태 하나. 다 찼으면 null. */
    fun firstFree(kind: BlockKind, taken: Set<String>): String? = candidates(kind).firstOrNull { it !in taken }

    /**
     * 어떤 표기든(속성 순서가 달라도, `powered` 가 붙어도) 우리 열쇠로. 한 상태를 가리키지 않는 것(`instrument=harp` 처럼 note 가 빠진 것)이나
     * 모르는 값이면 null.
     */
    fun normalize(kind: BlockKind, raw: String): String? {
        val props = raw.split(',').mapNotNull { part ->
            val pair = part.split('=', limit = 2)
            if (pair.size == 2) pair[0].trim() to pair[1].trim().lowercase() else null
        }.toMap()
        return when (kind) {
            BlockKind.SOLID -> {
                val instrument = props["instrument"]?.takeIf { it in INSTRUMENTS } ?: return null
                val note = props["note"]?.toIntOrNull()?.takeIf { it in 0 until NOTES } ?: return null
                noteKey(instrument, note)
            }
            BlockKind.TRANSPARENT -> {
                if (FACES.any { props[it] != "true" && props[it] != "false" }) return null
                FACES.joinToString(",") { it + "=" + props[it] }
            }
            BlockKind.ENTITY -> null
        }
    }

    /** `BlockData.getAsString()` 의 모양(`minecraft:note_block[instrument=basedrum,note=9,powered=false]`)에서 열쇠를. 그 블록이 아니면 null. */
    fun keyOf(kind: BlockKind, blockData: String): String? {
        val open = blockData.indexOf('[')
        if (open < 0 || !blockData.endsWith("]")) return null
        if (blockData.substring(0, open).substringAfter(':') != block(kind)) return null
        return normalize(kind, blockData.substring(open + 1, blockData.length - 1))
    }

    /** 그 상태의 블록 데이터 글자 — `Bukkit.createBlockData` 에 넘긴다. */
    fun blockData(kind: BlockKind, key: String): String = when (kind) {
        BlockKind.SOLID -> "minecraft:note_block[$key,powered=false]"
        BlockKind.TRANSPARENT -> "minecraft:chorus_plant[$key]"
        BlockKind.ENTITY -> error("엔티티 방식은 블록 상태를 쓰지 않는다")
    }

    // --- 팩의 blockstates 파일 ------------------------------------------------------------

    /**
     * 팩에 이미 있는 blockstates 파일에서 **한 상태를 가리키는 커스텀 칸**만 뽑는다(열쇠 → 모델). `variants` 모양만 읽는다 — 바닐라 모습으로
     * 남기는 칸([isReserved])과 여러 상태를 한꺼번에 가리키는 칸(`instrument=harp`)은 버린다. 읽지 못하면 빈 표.
     */
    fun mappings(kind: BlockKind, json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        val variants = runCatching { JsonParser.parseString(json).asJsonObject.getAsJsonObject("variants") }.getOrNull() ?: return emptyMap()
        val found = LinkedHashMap<String, String>()
        for ((raw, value) in variants.entrySet()) {
            val key = normalize(kind, raw) ?: continue
            if (isReserved(kind, key)) continue
            val model = when {
                value.isJsonObject -> value.asJsonObject.get("model")?.asString
                value.isJsonArray && value.asJsonArray.size() > 0 -> value.asJsonArray[0].asJsonObject.get("model")?.asString
                else -> null
            } ?: continue
            found[key] = model
        }
        return found
    }

    /** 커스텀 상태 표(열쇠 → 모델)로 blockstates 파일 전체를. */
    fun json(kind: BlockKind, custom: Map<String, String>): String = when (kind) {
        BlockKind.SOLID -> noteJson(custom)
        BlockKind.TRANSPARENT -> chorusJson(custom)
        BlockKind.ENTITY -> error("엔티티 방식은 블록 상태를 쓰지 않는다")
    }

    /** 소리블록: 악기 × 음 하나하나를 적는다(powered 는 빼서 둘 다 덮는다). 커스텀이 아니면 바닐라 모델. 칸끼리 겹치지 않는다. */
    private fun noteJson(custom: Map<String, String>): String {
        val variants = JsonObject()
        for (instrument in INSTRUMENTS) for (note in 0 until NOTES) {
            val key = noteKey(instrument, note)
            variants.add(key, JsonObject().apply { addProperty("model", custom[key] ?: VANILLA_NOTE) })
        }
        return GSON.toJson(JsonObject().apply { add("variants", variants) })
    }

    /**
     * 후렴초: 바닐라 조각 열두 개에 "커스텀 상태가 아닐 때" 를 단다 — 조각마다 제 조건(`north=true` …)을 만족하는 상태 가운데 커스텀이 아닌 것을
     * 전부 적은 OR. 커스텀 상태는 제 모델 하나만 그린다. 조건을 만족하는 상태가 하나도 커스텀이 아니면 바닐라 조건 그대로 둔다.
     */
    private fun chorusJson(custom: Map<String, String>): String {
        val customMasks = (0 until 64).filter { chorusKey(it) in custom }.toSet()
        val parts = JsonArray()
        for (element in JsonParser.parseString(VANILLA_CHORUS).asJsonObject.getAsJsonArray("multipart")) {
            val part = element.asJsonObject.deepCopy()
            val (face, value) = part.getAsJsonObject("when").entrySet().single().let { it.key to (it.value.asString == "true") }
            val bit = FACES.size - 1 - FACES.indexOf(face)
            val matching = (0 until 64).filter { ((it shr bit) and 1 == 1) == value }
            val allowed = matching.filter { it !in customMasks }
            if (allowed.isEmpty()) continue
            if (allowed.size < matching.size) {
                val any = JsonArray()
                for (mask in allowed) any.add(stateObject(mask))
                part.add("when", JsonObject().apply { add("OR", any) })
            }
            parts.add(part)
        }
        for (mask in customMasks.sorted()) {
            parts.add(JsonObject().apply {
                add("when", stateObject(mask))
                add("apply", JsonObject().apply { addProperty("model", custom.getValue(chorusKey(mask))) })
            })
        }
        return GSON.toJson(JsonObject().apply { add("multipart", parts) })
    }

    private fun stateObject(mask: Int): JsonObject = JsonObject().apply {
        for ((i, face) in FACES.withIndex()) addProperty(face, ((mask shr (FACES.size - 1 - i)) and 1 == 1).toString())
    }

    private val GSON = GsonBuilder().disableHtmlEscaping().create()

    /** 26.2 클라이언트의 `assets/minecraft/blockstates/chorus_plant.json` 그대로. */
    private val VANILLA_CHORUS = """
        {"multipart":[
         {"apply":{"model":"minecraft:block/chorus_plant_side"},"when":{"north":"true"}},
         {"apply":{"model":"minecraft:block/chorus_plant_side","uvlock":true,"y":90},"when":{"east":"true"}},
         {"apply":{"model":"minecraft:block/chorus_plant_side","uvlock":true,"y":180},"when":{"south":"true"}},
         {"apply":{"model":"minecraft:block/chorus_plant_side","uvlock":true,"y":270},"when":{"west":"true"}},
         {"apply":{"model":"minecraft:block/chorus_plant_side","uvlock":true,"x":270},"when":{"up":"true"}},
         {"apply":{"model":"minecraft:block/chorus_plant_side","uvlock":true,"x":90},"when":{"down":"true"}},
         {"apply":[{"model":"minecraft:block/chorus_plant_noside","weight":2},{"model":"minecraft:block/chorus_plant_noside1"},{"model":"minecraft:block/chorus_plant_noside2"},{"model":"minecraft:block/chorus_plant_noside3"}],"when":{"north":"false"}},
         {"apply":[{"model":"minecraft:block/chorus_plant_noside1","uvlock":true,"y":90},{"model":"minecraft:block/chorus_plant_noside2","uvlock":true,"y":90},{"model":"minecraft:block/chorus_plant_noside3","uvlock":true,"y":90},{"model":"minecraft:block/chorus_plant_noside","uvlock":true,"weight":2,"y":90}],"when":{"east":"false"}},
         {"apply":[{"model":"minecraft:block/chorus_plant_noside2","uvlock":true,"y":180},{"model":"minecraft:block/chorus_plant_noside3","uvlock":true,"y":180},{"model":"minecraft:block/chorus_plant_noside","uvlock":true,"weight":2,"y":180},{"model":"minecraft:block/chorus_plant_noside1","uvlock":true,"y":180}],"when":{"south":"false"}},
         {"apply":[{"model":"minecraft:block/chorus_plant_noside3","uvlock":true,"y":270},{"model":"minecraft:block/chorus_plant_noside","uvlock":true,"weight":2,"y":270},{"model":"minecraft:block/chorus_plant_noside1","uvlock":true,"y":270},{"model":"minecraft:block/chorus_plant_noside2","uvlock":true,"y":270}],"when":{"west":"false"}},
         {"apply":[{"model":"minecraft:block/chorus_plant_noside","uvlock":true,"weight":2,"x":270},{"model":"minecraft:block/chorus_plant_noside3","uvlock":true,"x":270},{"model":"minecraft:block/chorus_plant_noside1","uvlock":true,"x":270},{"model":"minecraft:block/chorus_plant_noside2","uvlock":true,"x":270}],"when":{"up":"false"}},
         {"apply":[{"model":"minecraft:block/chorus_plant_noside3","uvlock":true,"x":90},{"model":"minecraft:block/chorus_plant_noside2","uvlock":true,"x":90},{"model":"minecraft:block/chorus_plant_noside1","uvlock":true,"x":90},{"model":"minecraft:block/chorus_plant_noside","uvlock":true,"weight":2,"x":90}],"when":{"down":"false"}}
        ]}
    """.trimIndent()
}
