package com.inmc.customitems

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.inmc.customitems.pack.NumberMigration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 낡은 번호(`custom_model_data`)를 최신 방식으로 옮기고 바닐라 정의에서 걷어 낸다 — 게임이 고르는 것과 같게. */
class NumberMigrationTest {

    private fun files(vararg pairs: Pair<String, String>) = pairs.associate { it.first to it.second.toByteArray() }

    private fun obj(bytes: ByteArray?) = JsonParser.parseString(String(bytes!!, Charsets.UTF_8)) as JsonObject

    /** 옛 IA 팩의 모양 — 26.x 는 형식 84, 오버레이 셋 중 둘만 맞다. */
    private val mcmeta = """
        {"pack":{"pack_format":84},"overlays":{"entries":[
          {"directory":"ia_overlay_1_21_4_to_5","formats":[46,55]},
          {"directory":"ia_overlay_1_21_6_plus","formats":[63,9999],"min_format":63,"max_format":9999},
          {"directory":"ia_overlay_26_1_plus","formats":[84,9999],"min_format":[84,0],"max_format":9999}
        ]}}
    """.trimIndent()

    private fun paper(entries: String, fallback: String = """{"type":"minecraft:model","model":"minecraft:item/paper"}""") =
        """{"model":{"type":"range_dispatch","property":"custom_model_data","index":0,"fallback":$fallback,"entries":[$entries]}}"""

    private fun entry(threshold: Int, model: String) = """{"threshold":$threshold,"model":{"type":"model","model":"$model"}}"""

    @Test
    fun `지금 형식에 맞는 오버레이만 읽는다`() {
        val roots = NumberMigration.activeRoots(files("pack.mcmeta" to mcmeta))
        assertEquals(listOf("", "ia_overlay_1_21_6_plus/", "ia_overlay_26_1_plus/"), roots)
    }

    @Test
    fun `번호 이하 중 가장 큰 갈래가 그려진다 - 게임과 같다`() {
        val pack = files("pack.mcmeta" to mcmeta, "ia_overlay_1_21_6_plus/assets/minecraft/items/paper.json" to paper(
            entry(10000, "ia:item/a") + "," + entry(10005, "ia:item/b"),
        ))
        assertEquals(NumberMigration.Target("ia:item/a", null), NumberMigration.resolve(pack, "paper", 10000))
        assertEquals(NumberMigration.Target("ia:item/a", null), NumberMigration.resolve(pack, "paper", 10004), "사이 번호는 아래 갈래")
        assertEquals(NumberMigration.Target("ia:item/b", null), NumberMigration.resolve(pack, "paper", 99999))
        assertNull(NumberMigration.resolve(pack, "paper", 9999), "모든 갈래보다 작으면 바닐라 모양")
        assertNull(NumberMigration.resolve(pack, "stick", 10000), "정의가 없으면 바닐라 모양")
    }

    @Test
    fun `맞는 오버레이 중 뒤의 것이 뿌리를 이긴다 - 맞지 않는 오버레이는 안 본다`() {
        val pack = files(
            "pack.mcmeta" to mcmeta,
            "assets/minecraft/items/paper.json" to paper(entry(1, "root:item/x")),
            "ia_overlay_1_21_4_to_5/assets/minecraft/items/paper.json" to paper(entry(1, "old:item/x")),
            "ia_overlay_26_1_plus/assets/minecraft/items/paper.json" to paper(entry(1, "new:item/x")),
        )
        assertEquals("new:item/x", NumberMigration.resolve(pack, "paper", 1)?.model)
    }

    @Test
    fun `바닐라 모델로 채운 번호는 옮길 것이 없다 - 옛 팩의 빈 낚싯대 번호`() {
        val rod = """{"threshold":10006,"model":{"type":"minecraft:condition","property":"minecraft:fishing_rod/cast",
            "on_false":{"type":"model","model":"minecraft:item/fishing_rod"},"on_true":{"type":"minecraft:model","model":"minecraft:item/fishing_rod_cast"}}}"""
        val pack = files("assets/minecraft/items/fishing_rod.json" to paper(rod))
        assertNull(NumberMigration.resolve(pack, "fishing_rod", 10008), "번호만 떼면 지금과 똑같이 보인다")
    }

    @Test
    fun `조건이 있는 모양은 정의 통째로 옮긴다 - 던진 낚싯대`() {
        val rod = """{"threshold":50005,"model":{"type":"minecraft:condition","property":"minecraft:fishing_rod/cast",
            "on_false":{"type":"model","model":"inmc:item/ia_auto/fishing1"},"on_true":{"type":"model","model":"inmc:item/ia_auto/fishing1_cast"}}}"""
        val target = assertNotNull(NumberMigration.resolve(files("assets/minecraft/items/fishing_rod.json" to paper(rod)), "fishing_rod", 50005))
        assertEquals("inmc:item/ia_auto/fishing1", target.model, "화면에 보여 줄 대표 모델")
        val definition = JsonParser.parseString(assertNotNull(target.definition)).asJsonObject.getAsJsonObject("model")
        assertEquals("minecraft:condition", definition.get("type").asString)
        assertEquals("inmc:item/ia_auto/fishing1_cast", definition.getAsJsonObject("on_true").get("model").asString)
    }

    @Test
    fun `1_21_4 정의가 없으면 낡은 overrides 의 마지막 맞는 것`() {
        val pack = files("assets/minecraft/models/item/paper.json" to """
            {"parent":"item/generated","overrides":[
              {"predicate":{"custom_model_data":1},"model":"ia:item/one"},
              {"predicate":{"custom_model_data":5},"model":"item/five"}
            ]}
        """.trimIndent())
        assertEquals("ia:item/one", NumberMigration.resolve(pack, "paper", 3)?.model)
        assertNull(NumberMigration.resolve(pack, "paper", 7), "이름공간 없는 모델은 바닐라(minecraft:) 것이다")
    }

    @Test
    fun `번호 갈래만 있던 바닐라 정의는 지운다 - 클라이언트가 제 판의 것을 쓴다(옛 IA 팩의 플레이어 머리)`() {
        val head = """{"model":{"type":"range_dispatch","property":"custom_model_data","index":0,
            "fallback":{"type":"minecraft:special","base":"minecraft:item/template_skull","model":{"type":"minecraft:player_head"}},
            "entries":[{"threshold":10000,"model":{"type":"special","base":"_iainternal:entity/player/phead_0","model":{"type":"player_head"}}}]},
            "oversized_in_gui":true}"""
        val stripped = NumberMigration.strip(files(
            "ia_overlay_1_21_6_plus/assets/minecraft/items/player_head.json" to head,
            "assets/minecraft/items/paper.json" to paper(entry(1, "ia:item/a")),
        ))
        assertEquals(2, stripped.definitions)
        assertTrue(stripped.changes.containsKey("ia_overlay_1_21_6_plus/assets/minecraft/items/player_head.json"))
        assertNull(stripped.changes["ia_overlay_1_21_6_plus/assets/minecraft/items/player_head.json"])
        assertNull(stripped.changes["assets/minecraft/items/paper.json"])
    }

    @Test
    fun `바닐라 아이템을 새로 그린 팩은 그 모양을 남긴다`() {
        val retextured = paper(entry(1, "ia:item/a"), fallback = """{"type":"model","model":"shiny:item/paper"}""")
        val stripped = NumberMigration.strip(files("assets/minecraft/items/paper.json" to retextured))
        val kept = obj(stripped.changes["assets/minecraft/items/paper.json"])
        assertEquals("shiny:item/paper", kept.getAsJsonObject("model").get("model").asString)
    }

    @Test
    fun `낡은 overrides 는 번호 조건만 빼고 parent 는 그대로`() {
        val stripped = NumberMigration.strip(files("assets/minecraft/models/item/bow.json" to """
            {"parent":"item/generated","textures":{"layer0":"item/bow"},"overrides":[
              {"predicate":{"pulling":1},"model":"item/bow_pulling_0"},
              {"predicate":{"custom_model_data":7},"model":"ia:item/x"}
            ]}
        """.trimIndent()))
        assertEquals(1, stripped.overrides)
        val bow = obj(stripped.changes["assets/minecraft/models/item/bow.json"])
        assertEquals("item/generated", bow.get("parent").asString)
        assertEquals(1, bow.getAsJsonArray("overrides").size())
    }

    @Test
    fun `번호가 아닌 정의와 남의 이름공간은 건드리지 않는다`() {
        val stripped = NumberMigration.strip(files(
            "assets/minecraft/items/bow.json" to """{"model":{"type":"minecraft:condition","property":"using_item","on_true":{"type":"model","model":"a"},"on_false":{"type":"model","model":"b"}}}""",
            "assets/inmc/items/sword.json" to paper(entry(1, "ia:item/a")),
            "assets/minecraft/items/sub/deep.json" to paper(entry(1, "ia:item/a")),
        ))
        assertTrue(stripped.changes.isEmpty())
        assertFalse(stripped.definitions > 0)
    }
}
