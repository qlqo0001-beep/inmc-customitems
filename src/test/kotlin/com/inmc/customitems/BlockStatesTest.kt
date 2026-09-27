package com.inmc.customitems

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.inmc.customitems.block.BlockStates
import com.inmc.customitems.block.ItemsAdderBlocks
import com.inmc.customitems.item.BlockKind
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 블록 상태 방식의 상태 공간과 팩 파일. 틀리면 **조용히** 깨진다 — 바닐라 소리블록·후렴초가 보라·검정 칸이 되거나, 커스텀 블록 위에
 * 바닐라 후렴초 조각이 겹쳐 그려지거나, 옛 IA 블록이 다른 아이템으로 이어진다.
 */
class BlockStatesTest {

    private val chest = "down=false,east=false,north=false,south=false,up=false,west=true"

    // --- 상태 공간 -----------------------------------------------------------------------

    @Test
    fun `바닐라 모습으로 남길 상태는 새 블록에 내주지 않는다`() {
        val solid = BlockStates.candidates(BlockKind.SOLID)
        assertEquals(BlockStates.INSTRUMENTS.size * BlockStates.NOTES - BlockStates.NOTES, solid.size)
        assertTrue(solid.none { it.startsWith("instrument=harp,") }, "Paper 가 놓는 소리블록은 늘 harp 다")

        val transparent = BlockStates.candidates(BlockKind.TRANSPARENT)
        assertEquals(62, transparent.size)
        assertTrue(BlockStates.chorusKey(0) !in transparent, "전부 거짓 — Paper 가 놓는 후렴초")
        assertTrue(BlockStates.chorusKey(63) !in transparent, "전부 참")
    }

    @Test
    fun `후렴초 첫 칸은 옛 IA 가 준 첫 상자와 같은 표기다`() {
        assertEquals(chest, BlockStates.chorusKey(1))
        assertEquals(chest, BlockStates.candidates(BlockKind.TRANSPARENT).first())
    }

    @Test
    fun `빈 칸은 차지한 것을 건너뛴다`() {
        val first = BlockStates.candidates(BlockKind.SOLID).first()
        val second = BlockStates.candidates(BlockKind.SOLID)[1]
        assertEquals(second, BlockStates.firstFree(BlockKind.SOLID, setOf(first)))
        assertNull(BlockStates.firstFree(BlockKind.TRANSPARENT, BlockStates.candidates(BlockKind.TRANSPARENT).toSet()))
    }

    @Test
    fun `표기는 속성 순서와 powered 에 흔들리지 않는다`() {
        assertEquals("instrument=basedrum,note=9", BlockStates.normalize(BlockKind.SOLID, "powered=false,note=9,instrument=basedrum"))
        assertEquals(chest, BlockStates.normalize(BlockKind.TRANSPARENT, "west=true,up=false,south=false,north=false,east=false,down=false"))
        assertNull(BlockStates.normalize(BlockKind.SOLID, "instrument=harp"), "note 가 없으면 한 상태가 아니다")
        assertNull(BlockStates.normalize(BlockKind.SOLID, "instrument=kazoo,note=1"))
        assertNull(BlockStates.normalize(BlockKind.TRANSPARENT, "west=true"))
    }

    @Test
    fun `블록 데이터 글자에서 열쇠를 읽고 다시 만든다`() {
        assertEquals("instrument=basedrum,note=9", BlockStates.keyOf(BlockKind.SOLID, "minecraft:note_block[instrument=basedrum,note=9,powered=true]"))
        assertNull(BlockStates.keyOf(BlockKind.SOLID, "minecraft:chorus_plant[$chest]"), "다른 블록")
        assertEquals(chest, BlockStates.keyOf(BlockKind.TRANSPARENT, BlockStates.blockData(BlockKind.TRANSPARENT, chest)))
        assertEquals("instrument=bit,note=3", BlockStates.keyOf(BlockKind.SOLID, BlockStates.blockData(BlockKind.SOLID, "instrument=bit,note=3")))
    }

    // --- 남의 팩 읽기 ----------------------------------------------------------------------

    @Test
    fun `팩의 커스텀 칸만 읽고 바닐라 칸과 여러 상태를 덮는 칸은 버린다`() {
        val ia = """{"variants":{
            "instrument=harp":{"model":"block/original/note_block"},
            "instrument=basedrum,note=9,powered=false":{"model":"stonevariations:item/ia_auto/roadandesite"}}}"""
        assertEquals(mapOf("instrument=basedrum,note=9" to "stonevariations:item/ia_auto/roadandesite"), BlockStates.mappings(BlockKind.SOLID, ia))

        val chorus = """{"variants":{
            "west=true,up=true,south=true,north=true,east=true,down=true":{"model":"block/original/chorus_plant"},
            "$chest":{"model":"inmc:fuben/block/chest/chest"}}}"""
        assertEquals(mapOf(chest to "inmc:fuben/block/chest/chest"), BlockStates.mappings(BlockKind.TRANSPARENT, chorus))
        assertEquals(emptyMap(), BlockStates.mappings(BlockKind.TRANSPARENT, """{"multipart":[]}"""), "여러 조각 모양은 커스텀 칸이 아니다")
    }

    // --- 만든 팩 파일 -----------------------------------------------------------------------

    @Test
    fun `소리블록 파일은 모든 상태를 정확히 한 번씩 덮는다`() {
        val custom = mapOf("instrument=basedrum,note=9" to "inmc:block/ore")
        val variants = JsonParser.parseString(BlockStates.json(BlockKind.SOLID, custom)).asJsonObject.getAsJsonObject("variants")
        assertEquals(BlockStates.INSTRUMENTS.size * BlockStates.NOTES, variants.size(), "겹치거나 빠진 칸이 있으면 보라·검정 칸이나 엉뚱한 모양이 된다")
        assertEquals("inmc:block/ore", variants.getAsJsonObject("instrument=basedrum,note=9")["model"].asString)
        assertEquals("minecraft:block/note_block", variants.getAsJsonObject("instrument=harp,note=0")["model"].asString)
        assertEquals("minecraft:block/note_block", variants.getAsJsonObject("instrument=trumpet,note=24")["model"].asString)
    }

    @Test
    fun `후렴초 파일은 커스텀 상태에 제 모델 하나만 바닐라 상태에 바닐라 조각 여섯만 그린다`() {
        val custom = mapOf(chest to "inmc:fuben/block/chest/chest", BlockStates.chorusKey(40) to "inmc:x")
        val parts = JsonParser.parseString(BlockStates.json(BlockKind.TRANSPARENT, custom)).asJsonObject.getAsJsonArray("multipart")
        for (mask in 0 until 64) {
            val state = BlockStates.chorusKey(mask).split(',').associate { it.substringBefore('=') to it.substringAfter('=') }
            val applied = parts.map { it.asJsonObject }.filter { matches(it["when"], state) }.map { model(it["apply"]) }
            val key = BlockStates.chorusKey(mask)
            if (key in custom) {
                assertEquals(listOf(custom.getValue(key)), applied, "커스텀 상태 $mask 에 바닐라 조각이 겹친다")
            } else {
                assertEquals(6, applied.size, "바닐라 상태 $mask 는 면마다 조각 하나")
                assertTrue(applied.all { it.startsWith("minecraft:block/chorus_plant") }, "바닐라 상태 $mask 에 커스텀 모델이 섞인다: $applied")
            }
        }
    }

    /** 클라이언트의 multipart 조건 — 속성 하나하나가 같거나 `OR` 안의 하나가 맞으면. */
    private fun matches(condition: JsonElement?, state: Map<String, String>): Boolean {
        if (condition == null) return true
        val obj = condition.asJsonObject
        obj.getAsJsonArray("OR")?.let { any -> return any.any { matches(it, state) } }
        return obj.entrySet().all { (key, value) -> value.asString.split('|').contains(state[key]) }
    }

    private fun model(apply: JsonElement): String =
        if (apply.isJsonArray) (apply.asJsonArray[0] as JsonObject)["model"].asString else apply.asJsonObject["model"].asString

    // --- 옛 IA 옮기기 ---------------------------------------------------------------------

    @Test
    fun `옛 IA 블록은 팩이 그 모델에 준 상태를 그대로 받는다`() {
        val contents = Files.createTempDirectory("ia").toFile()
        try {
            File(contents, "inmc/configs").mkdirs()
            File(contents, "inmc/configs/chest.yml").writeText(
                """
                info:
                  namespace: inmc
                items:
                  fb_chest_68:
                    display_name: 상자
                    resource: { material: STICK, generate: false, model_path: fuben/block/chest/chest }
                    specific_properties: { block: { placed_model: { type: REAL_TRANSPARENT } } }
                  ore:
                    display_name: 광석
                    resource: { material: BRICK, generate: true }
                    specific_properties: { block: { placed_model: { type: REAL_NOTE }, drop_when_mined: false, hardness: 3, break_tools_whitelist: [IRON_PICKAXE, DIAMOND_PICKAXE] } }
                  wire:
                    resource: { material: PAPER }
                    specific_properties: { block: { placed_model: { type: REAL_WIRE } } }
                  lost:
                    resource: { material: PAPER, model_path: nowhere }
                    specific_properties: { block: { placed_model: { type: REAL_NOTE } } }
                  plain:
                    resource: { material: PAPER }
                """.trimIndent(),
                Charsets.UTF_8,
            )
            val states = mapOf(
                BlockKind.TRANSPARENT to mapOf(chest to "inmc:fuben/block/chest/chest"),
                BlockKind.SOLID to mapOf("instrument=basedrum,note=1" to "inmc:item/ia_auto/ore"),
            )
            val scan = ItemsAdderBlocks.scan(contents, states)
            val byId = scan.found.associateBy { it.id }

            assertEquals(setOf("fb_chest_68", "ore"), byId.keys)
            assertEquals(chest, byId.getValue("fb_chest_68").state)
            assertEquals(BlockKind.TRANSPARENT, byId.getValue("fb_chest_68").kind)
            assertEquals("inmc:fuben/block/chest/chest", byId.getValue("fb_chest_68").model)
            assertEquals("inmc:item/ia_auto/ore", byId.getValue("ore").model, "model_path 가 없으면 IA 가 만든 모델")
            assertEquals(false, byId.getValue("ore").drop)
            assertEquals(3.0, byId.getValue("ore").hardness)
            assertEquals(com.inmc.customitems.block.ToolKind.PICKAXE, byId.getValue("ore").tool)
            assertEquals(2, byId.getValue("ore").toolTier)
            assertEquals(null, byId.getValue("fb_chest_68").hardness, "안 적었으면 우리 기본값")
            assertEquals(listOf("inmc:lost"), scan.noState)
            assertEquals(1, scan.unsupported.size)
        } finally {
            contents.deleteRecursively()
        }
    }
}
