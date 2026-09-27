package com.inmc.customitems

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.pack.JsonMerge
import com.inmc.customitems.pack.PackAssets
import com.inmc.customitems.pack.PackMerger
import org.bukkit.Material
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 리소스팩 병합.
 *
 * **덮으면 두 팩 중 하나가 조용히 사라진다** — 오류도 안 나고, 게임에 들어가서 텍스처가
 * 없는 것을 보고서야 안다. 그래서 "무엇을 섞고 무엇을 덮는가"가 이 기능의 전부이고,
 * 여기가 그걸 못박는다.
 *
 * 서버가 필요 없다. 병합기는 파일과 문자열만 만진다.
 */
class PackMergeTest {

    private fun obj(text: String): JsonObject = JsonParser.parseString(text).asJsonObject

    // --- 섞어야 하는 것 ---------------------------------------------------------------

    @Test
    fun `번역은 키 단위로 합쳐진다`() {
        // 두 팩이 서로 다른 번역을 더하면 **둘 다 살아야 한다.** 덮으면 한쪽 팩의 모든
        // 아이템 이름이 영어 id 로 보인다.
        val merged = JsonMerge.merge(
            "assets/minecraft/lang/ko_kr.json",
            """{"item.a":"가","item.b":"나"}""",
            """{"item.b":"나2","item.c":"다"}""",
        )

        val result = obj(merged)
        assertEquals("가", result.get("item.a").asString, "먼저 온 것이 살아남아야 한다")
        assertEquals("나2", result.get("item.b").asString, "겹치면 나중 것이 이긴다")
        assertEquals("다", result.get("item.c").asString)
    }

    @Test
    fun `소리 정의도 키 단위로 합쳐진다`() {
        val merged = JsonMerge.merge(
            "assets/minecraft/sounds.json",
            """{"custom.a":{"sounds":["x"]}}""",
            """{"custom.b":{"sounds":["y"]}}""",
        )

        val result = obj(merged)
        assertEquals(2, result.size())
        assertNotNull(result.get("custom.a"))
        assertNotNull(result.get("custom.b"))
    }

    @Test
    fun `깊은 병합은 배열을 잇지 않는다`() {
        // 배열은 "이 소리의 후보 목록" 같은 완결된 값이다. 이으면 한쪽이 정의한 소리에
        // 다른 쪽 후보가 섞여 들어간다.
        val merged = JsonMerge.merge(
            "assets/minecraft/sounds.json",
            """{"custom.a":{"sounds":["x","y"]}}""",
            """{"custom.a":{"sounds":["z"]}}""",
        )

        val sounds = obj(merged).getAsJsonObject("custom.a").getAsJsonArray("sounds")
        assertEquals(1, sounds.size(), "배열은 새 값이 통째로 이긴다")
        assertEquals("z", sounds[0].asString)
    }

    @Test
    fun `아틀라스 소스는 이어 붙인다`() {
        // 아틀라스는 목록이라 덮으면 한쪽 팩의 텍스처가 통째로 안 붙는다.
        val merged = JsonMerge.merge(
            "assets/minecraft/atlases/blocks.json",
            """{"sources":[{"type":"directory","source":"a"}]}""",
            """{"sources":[{"type":"directory","source":"b"}]}""",
        )

        val sources = obj(merged).getAsJsonArray("sources")
        assertEquals(2, sources.size())
    }

    @Test
    fun `같은 아틀라스 소스를 두 번 넣어도 한 번만 남는다`() {
        val entry = """{"type":"directory","source":"a"}"""
        val merged = JsonMerge.merge(
            "assets/minecraft/atlases/blocks.json",
            """{"sources":[$entry]}""",
            """{"sources":[$entry]}""",
        )

        assertEquals(1, obj(merged).getAsJsonArray("sources").size())
    }

    // --- overrides: 가장 흔한 충돌 ------------------------------------------------------

    @Test
    fun `두 팩의 모델 오버라이드가 둘 다 살아남는다`() {
        // 팩 병합에서 가장 흔한 사고다. 두 팩이 `models/item/paper.json` 을 각각 갖고
        // 자기 아이템들을 오버라이드해 두는데, 덮으면 한쪽 팩의 아이템이 **전부** 사라진다.
        val left = """
            {"parent":"item/generated","overrides":[
              {"predicate":{"custom_model_data":1},"model":"item/a"}
            ]}
        """.trimIndent()
        val right = """
            {"parent":"item/generated","overrides":[
              {"predicate":{"custom_model_data":2},"model":"item/b"}
            ]}
        """.trimIndent()

        val overrides = obj(JsonMerge.merge("assets/minecraft/models/item/paper.json", left, right))
            .getAsJsonArray("overrides")

        assertEquals(2, overrides.size(), "한쪽이 사라지면 안 된다")
    }

    @Test
    fun `오버라이드는 번호 순으로 정렬된다`() {
        // 마인크래프트는 오버라이드를 **순서대로** 보고 첫 일치를 쓴다. 뒤섞여 있으면
        // 작은 번호가 큰 번호를 가로챈다.
        val left = """{"overrides":[{"predicate":{"custom_model_data":30},"model":"c"}]}"""
        val right = """
            {"overrides":[
              {"predicate":{"custom_model_data":10},"model":"a"},
              {"predicate":{"custom_model_data":20},"model":"b"}
            ]}
        """.trimIndent()

        val overrides = obj(JsonMerge.merge("assets/minecraft/models/item/paper.json", left, right))
            .getAsJsonArray("overrides")

        val numbers = overrides.map {
            it.asJsonObject.getAsJsonObject("predicate").get("custom_model_data").asInt
        }
        assertEquals(listOf(10, 20, 30), numbers)
    }

    @Test
    fun `오버라이드가 없는 모델은 그냥 덮인다`() {
        // 통짜 정의라 섞을 수가 없다. 파츠를 섞으면 어느 쪽도 아닌 모델이 나온다.
        val merged = JsonMerge.merge(
            "assets/minecraft/models/item/x.json",
            """{"parent":"item/generated","textures":{"layer0":"a"}}""",
            """{"parent":"item/handheld","textures":{"layer0":"b"}}""",
        )

        assertEquals("item/handheld", obj(merged).get("parent").asString)
        assertEquals("b", obj(merged).getAsJsonObject("textures").get("layer0").asString)
    }

    @Test
    fun `통짜 모델이 남의 오버라이드를 지우지 않는다`() {
        // 한쪽에만 overrides 가 있어도 섞는 이유. 나중 팩이 바닐라 모델을 통째로 넣었다고
        // 앞 팩의 아이템이 전부 사라지면 안 된다.
        val withOverrides = """
            {"parent":"item/generated","overrides":[
              {"predicate":{"custom_model_data":1},"model":"item/a"}
            ]}
        """.trimIndent()
        val plain = """{"parent":"item/handheld","textures":{"layer0":"b"}}"""

        val merged = obj(JsonMerge.merge("assets/minecraft/models/item/paper.json", withOverrides, plain))

        assertEquals("item/handheld", merged.get("parent").asString, "새 값이 이긴다")
        assertEquals(1, merged.getAsJsonArray("overrides").size(), "앞 팩의 아이템이 남아야 한다")
    }

    @Test
    fun `오버라이드 조각이 바닐라 모델을 지우지 않는다`() {
        // 남의 팩이 이 모양의 조각을 준다 — parent 도 textures 도 없는 overrides 만. 그걸 그대로 쓰면 그 아이템이
        // 통째로 안 보인다. **우리 아이템만이 아니라 평범한 그 아이템까지.**
        val base = """{"parent":"item/handheld","textures":{"layer0":"minecraft:item/paper"}}"""
        val fragment = """{"overrides":[{"predicate":{"custom_model_data":1000},"model":"inmc:item/a"}]}"""

        val merged = obj(JsonMerge.merge("assets/minecraft/models/item/paper.json", base, fragment))

        assertEquals("item/handheld", merged.get("parent").asString)
        assertNotNull(merged.get("textures"))
        assertEquals(1, merged.getAsJsonArray("overrides").size())
    }

    // --- pack.mcmeta ---------------------------------------------------------------

    @Test
    fun `형식 번호는 가장 높은 것을 쓴다`() {
        // 낮은 쪽에 맞추면 높은 형식으로 만든 팩이 클라이언트에서 통째로 안 읽힌다.
        val merged = JsonMerge.merge(
            "pack.mcmeta",
            """{"pack":{"pack_format":46,"description":"새것"}}""",
            """{"pack":{"pack_format":22,"description":"낡은것"}}""",
        )

        val pack = obj(merged).getAsJsonObject("pack")
        assertEquals(46, pack.get("pack_format").asInt, "낮은 쪽으로 내려가면 안 된다")
        assertEquals("낡은것", pack.get("description").asString, "설명은 나중 것이 이긴다")
    }

    @Test
    fun `오버레이 목록은 두 팩 것을 잇는다`() {
        // 한쪽 목록만 남으면 다른 팩의 오버레이 폴더(버전별 셰이더)가 zip 에 있는데도 안 켜진다.
        val merged = JsonMerge.merge(
            "pack.mcmeta",
            """{"pack":{"pack_format":84},"overlays":{"entries":[{"directory":"ia_a","formats":[1,9]},{"directory":"shared","formats":[1,1]}]}}""",
            """{"pack":{"pack_format":88},"overlays":{"entries":[{"directory":"hud_26","formats":[84,99]},{"directory":"shared","formats":[2,2]}]}}""",
        )

        val entries = obj(merged).getAsJsonObject("overlays").getAsJsonArray("entries").map { it.asJsonObject }
        assertEquals(listOf("ia_a", "hud_26", "shared"), entries.map { it.get("directory").asString }, "나중 팩의 것이 뒤에(뒤가 이긴다)")
        assertEquals(2, entries.last().getAsJsonArray("formats")[0].asInt, "같은 폴더는 나중 것")
        assertEquals(88, obj(merged).getAsJsonObject("pack").get("pack_format").asInt)
    }

    @Test
    fun `병합기가 pack mcmeta 를 덮지 않고 섞는다`() {
        // JsonMerge 만 시험하면 병합기가 그 규칙을 안 부르는 것을 못 잡는다(확장자가 .json 이 아니다).
        val merger = PackMerger()
        merger.put("09-hud", "pack.mcmeta", """{"pack":{"pack_format":88},"overlays":{"entries":[{"directory":"hud_26"}]}}""".toByteArray())
        merger.put("10-base", "pack.mcmeta", """{"pack":{"pack_format":84},"overlays":{"entries":[{"directory":"ia_26"}]}}""".toByteArray())

        val meta = obj(merger.entries().getValue("pack.mcmeta").toString(Charsets.UTF_8))
        assertEquals(88, meta.getAsJsonObject("pack").get("pack_format").asInt)
        assertEquals(listOf("hud_26", "ia_26"), meta.getAsJsonObject("overlays").getAsJsonArray("entries").map { it.asJsonObject.get("directory").asString })
    }

    // --- 안전장치 -------------------------------------------------------------------

    @Test
    fun `깨진 json 은 팩 전체를 막지 않는다`() {
        // 손으로 고치다 만 json 하나 때문에 팩을 못 만들면 안 된다.
        val merged = JsonMerge.merge(
            "assets/minecraft/lang/ko_kr.json",
            """{"a":"1"}""",
            "이건 json 이 아니다",
        )

        assertEquals("""{"a":"1"}""", merged, "읽을 수 있는 쪽이 남는다")
    }

    @Test
    fun `규칙이 경로로 정해진다`() {
        assertEquals(JsonMerge.Rule.MCMETA, JsonMerge.ruleFor("pack.mcmeta"))
        assertEquals(JsonMerge.Rule.DEEP, JsonMerge.ruleFor("assets/minecraft/lang/ko_kr.json"))
        assertEquals(JsonMerge.Rule.DEEP, JsonMerge.ruleFor("assets/minecraft/sounds.json"))
        assertEquals(JsonMerge.Rule.ATLAS, JsonMerge.ruleFor("assets/minecraft/atlases/blocks.json"))
        assertEquals(JsonMerge.Rule.REPLACE, JsonMerge.ruleFor("assets/minecraft/textures/item/a.png"))
    }

    // --- 병합기 ---------------------------------------------------------------------

    @Test
    fun `나중 소스가 이긴다`() {
        val merger = PackMerger()
        merger.put("a", "assets/x/textures/a.png", byteArrayOf(1))
        merger.put("b", "assets/x/textures/a.png", byteArrayOf(2))

        assertEquals(2, merger.entries().getValue("assets/x/textures/a.png")[0])

        val report = merger.result(2)
        assertEquals(1, report.replacedCount)
        assertEquals("b", report.conflicts.first().winner)
    }

    @Test
    fun `zip 바깥으로 나가는 경로를 받지 않는다`() {
        // `../` 가 든 항목을 그대로 두면 이 팩을 푸는 쪽이 서버 폴더 아무 데나 파일을 쓰게
        // 된다 (Zip Slip). 우리가 풀지 않더라도 만든 팩을 누가 풀지는 우리가 정하지 않는다.
        val merger = PackMerger()
        merger.put("evil", "../../server.properties", byteArrayOf(1))
        merger.put("evil", "assets/../../x", byteArrayOf(1))

        assertEquals(0, merger.entries().size)
    }

    @Test
    fun `맥이 넣는 쓰레기를 거른다`() {
        val merger = PackMerger()
        merger.put("a", "__MACOSX/x", byteArrayOf(1))
        merger.put("a", "assets/.DS_Store", byteArrayOf(1))
        merger.put("a", "assets/x.png", byteArrayOf(1))

        assertEquals(setOf("assets/x.png"), merger.entries().keys)
    }

    @Test
    fun `zip 소스를 읽어 합친다`() {
        val dir = createTempDirectory("packtest").toFile()
        try {
            val zip = File(dir, "10-base.zip")
            ZipOutputStream(zip.outputStream()).use { out ->
                out.putNextEntry(ZipEntry("pack.mcmeta"))
                out.write("""{"pack":{"pack_format":30}}""".toByteArray())
                out.closeEntry()
                out.putNextEntry(ZipEntry("assets/minecraft/lang/ko_kr.json"))
                out.write("""{"a":"1"}""".toByteArray())
                out.closeEntry()
            }

            val folder = File(dir, "20-extra/assets/minecraft/lang").apply { mkdirs() }
            File(folder, "ko_kr.json").writeText("""{"b":"2"}""")

            val merger = PackMerger()
            merger.add("10-base.zip", zip)
            merger.add("20-extra", File(dir, "20-extra"))

            val lang = obj(
                merger.entries().getValue("assets/minecraft/lang/ko_kr.json").toString(Charsets.UTF_8),
            )
            assertEquals(2, lang.size(), "zip 과 폴더가 둘 다 읽혀 합쳐져야 한다")
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `폴더째 압축한 팩은 그 폴더를 벗겨 읽는다`() {
        // BetterHud 처럼 `resourcepack/` 째 압축한 팩을 그대로 합치면 `resourcepack/assets/…` 가 되어
        // 클라이언트가 그 팩을 통째로 무시한다. 오류는 없다.
        val dir = createTempDirectory("packtest").toFile()
        try {
            val zip = File(dir, "20-hud.zip")
            ZipOutputStream(zip.outputStream()).use { out ->
                for ((name, text) in listOf(
                    "resourcepack/pack.mcmeta" to """{"pack":{"pack_format":88}}""",
                    "resourcepack/assets/hud/font/a.json" to """{"providers":[]}""",
                    "읽어보세요.txt" to "팩 밖의 파일",
                )) {
                    out.putNextEntry(ZipEntry(name))
                    out.write(text.toByteArray())
                    out.closeEntry()
                }
            }
            val folder = File(dir, "30-folder/inner").apply { mkdirs() }
            File(folder, "pack.mcmeta").writeText("""{"pack":{"pack_format":88}}""")
            File(folder, "assets/x/textures").apply { mkdirs() }.resolve("b.png").writeBytes(byteArrayOf(1))

            val merger = PackMerger()
            merger.add("20-hud.zip", zip)
            merger.add("30-folder", File(dir, "30-folder"))

            assertEquals(setOf("pack.mcmeta", "assets/hud/font/a.json", "assets/x/textures/b.png"), merger.entries().keys)
        } finally {
            dir.deleteRecursively()
        }
    }

    // --- 아이템 에셋 -----------------------------------------------------------------

    @Test
    fun `텍스처도 모델도 없으면 팩에 안 들어간다`() {
        // 바닐라 모양 그대로 쓰는 아이템까지 넣으면 빈 정의가 아이템 수만큼 쌓인다.
        val plain = CustomItem(id = "x", material = Material.STICK)
        val textured = CustomItem(id = "y", material = Material.STICK, texture = "y.png")
        val modelled = CustomItem(id = "z", material = Material.STICK, model = "item/z")

        assertTrue(!PackAssets.needsPack(plain))
        assertTrue(PackAssets.needsPack(textured))
        assertTrue(PackAssets.needsPack(modelled))
    }

    @Test
    fun `모델 이름이 우리 네임스페이스로 채워진다`() {
        val auto = CustomItem(id = "sword", material = Material.STICK, texture = "a.png")
        assertEquals("inmc:item/sword", PackAssets.modelNameFor(auto))

        val shorthand = CustomItem(id = "sword", material = Material.STICK, model = "item/custom")
        assertEquals("inmc:item/custom", PackAssets.modelNameFor(shorthand))

        // 남의 네임스페이스를 적었으면 그대로 둔다.
        val other = CustomItem(id = "sword", material = Material.STICK, model = "minecraft:item/stick")
        assertEquals("minecraft:item/stick", PackAssets.modelNameFor(other))
    }

    @Test
    fun `생성한 json 이 읽히는 json 이다`() {
        // 문자열로 만들기 때문에 따옴표 하나만 빠져도 클라이언트가 팩을 거부한다.
        assertNotNull(JsonMerge.parse(PackAssets.modelJson("sword")))
        assertNotNull(JsonMerge.parse(PackAssets.itemJson("inmc:item/sword")))
        assertNotNull(JsonMerge.parse(PackAssets.mcmetaJson("설명")))

        val model = obj(PackAssets.modelJson("sword"))
        assertEquals("inmc:item/sword", model.getAsJsonObject("textures").get("layer0").asString)
    }

    @Test
    fun `에셋 경로가 마인크래프트 규칙을 따른다`() {
        assertEquals("assets/inmc/textures/item/sword.png", PackAssets.texturePath("sword"))
        assertEquals("assets/inmc/models/item/sword.json", PackAssets.modelPath("sword"))
        // 1.21.4 부터 item_model 이 가리키는 정의는 `items/` 아래에 있다.
        assertEquals("assets/inmc/items/sword.json", PackAssets.itemPath("sword"))
    }

    @Test
    fun `모델 목록은 남의 모델에 미리보기 정의를 붙이고 바닐라와 IA 내부는 뺀다`() {
        assertEquals("itemsadder:auto_generated/coin", PackAssets.listedModel("assets/itemsadder/models/auto_generated/coin.json"))
        assertNull(PackAssets.listedModel("assets/minecraft/models/item/paper.json"), "바닐라 모델은 목록에 없다")
        assertNull(PackAssets.listedModel("assets/_iainternal/models/entity/player/phead_0.json"), "IA 내부")
        assertNull(PackAssets.listedModel("ia_overlay_1_21_6_plus/assets/itemsadder/models/x.json"), "오버레이 사본")
        assertNull(PackAssets.listedModel("assets/itemsadder/textures/x.png"))

        val model = "1_splatus:item/blaze_staff"
        assertEquals("assets/inmc/items/preview/1_splatus/item/blaze_staff.json", PackAssets.previewPath(model))
        assertEquals("inmc:preview/1_splatus/item/blaze_staff", PackAssets.previewKey(model))
        assertEquals(model, PackAssets.previewModel(PackAssets.previewPath(model)), "만든 팩을 읽어 목록을 되살린다")
        assertNull(PackAssets.previewModel("assets/inmc/items/sword.json"), "우리 아이템 정의는 미리보기가 아니다")
    }

    @Test
    fun `못 읽는 json 은 null 이다`() {
        assertNull(JsonMerge.parse("{"))
        assertNull(JsonMerge.parse("null"))
    }
}
