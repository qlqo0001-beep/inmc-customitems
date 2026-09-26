package com.inmc.customitems

import com.google.gson.JsonParser
import com.inmc.customitems.item.ItemBuilder
import com.inmc.customitems.pack.PackAssets
import com.inmc.customitems.pack.PackMerger
import com.inmc.customitems.pack.PackZip
import java.io.File
import java.util.zip.ZipFile
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 내보낸 팩이 **실제로 쓸 수 있는 팩인지** 본다.
 *
 * 여기 있는 것들은 전부 문서와 `CLAUDE.md` 에 적어놓고 **확인한 적이 없던 주장**이다.
 * 셋 다 틀려도 오류가 안 나고, 서버에 올려 사람이 접속해 봐야 드러난다.
 */
class PackOutputTest {

    private fun temp(block: (File) -> Unit) {
        val dir = createTempDirectory("packout").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun entries(zip: File): Map<String, String> =
        ZipFile(zip).use { archive ->
            archive.entries().asSequence()
                .filterNot { it.isDirectory }
                .associate { it.name to archive.getInputStream(it).use { s -> s.readBytes() } }
                .mapValues { it.value.toString(Charsets.UTF_8) }
        }

    // --- 재현성: 안 바뀌면 sha1 도 안 바뀐다 ---------------------------------------------

    @Test
    fun `같은 내용이면 바이트까지 같은 zip 이 나온다`() {
        // sha1 이 흔들리면 클라이언트가 **접속할 때마다 몇 MB 를 다시 받는다.**
        // 느릴 뿐 고장은 아니라, 아무도 신고하지 않고 계속 그런다.
        temp { dir ->
            val content = mapOf(
                "pack.mcmeta" to "{}".toByteArray(),
                "assets/inmc/items/a.json" to "{\"a\":1}".toByteArray(),
            )

            val first = File(dir, "first.zip")
            val second = File(dir, "second.zip")
            PackZip.write(first, content)
            Thread.sleep(5) // 시각이 들어가면 여기서 갈린다
            PackZip.write(second, content)

            assertEquals(PackZip.sha1(first), PackZip.sha1(second), "시각이 섞여 들어갔습니다")
            assertTrue(first.readBytes().contentEquals(second.readBytes()))
        }
    }

    @Test
    fun `넣은 순서가 달라도 같은 zip 이 나온다`() {
        // 항목 순서는 맵 순서에 흔들린다. 정렬하지 않으면 아이템 하나를 더했다 뺐을 뿐인데
        // 팩 전체가 다시 배포된다.
        temp { dir ->
            val a = File(dir, "a.zip")
            val b = File(dir, "b.zip")
            PackZip.write(a, linkedMapOf("z.json" to "1".toByteArray(), "a.json" to "2".toByteArray()))
            PackZip.write(b, linkedMapOf("a.json" to "2".toByteArray(), "z.json" to "1".toByteArray()))

            assertEquals(PackZip.sha1(a), PackZip.sha1(b), "경로 순으로 쓰지 않고 있습니다")
        }
    }

    @Test
    fun `내용이 바뀌면 sha1 도 바뀐다`() {
        // 위의 반대. 안 바뀌면 클라이언트가 **새 팩을 안 받는다.**
        temp { dir ->
            val a = File(dir, "a.zip")
            val b = File(dir, "b.zip")
            PackZip.write(a, mapOf("x.json" to "1".toByteArray()))
            PackZip.write(b, mapOf("x.json" to "2".toByteArray()))

            assertTrue(PackZip.sha1(a) != PackZip.sha1(b))
        }
    }

    @Test
    fun `sha1 이 클라이언트가 받는 모양이다`() {
        // setResourcePack 이 20바이트를 요구한다. 40자 16진수가 아니면 못 넘긴다.
        temp { dir ->
            val zip = File(dir, "p.zip")
            PackZip.write(zip, mapOf("x" to byteArrayOf(1)))

            val hash = PackZip.sha1(zip)
            assertEquals(40, hash.length)
            assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' }, hash)
        }
    }

    @Test
    fun `쓰다 만 파일을 남기지 않는다`() {
        // 쓰는 도중에 누가 받아 가면 깨진 팩을 받는다. 그래서 .tmp 로 쓰고 바꿔 끼운다.
        temp { dir ->
            val zip = File(dir, "p.zip")
            PackZip.write(zip, mapOf("x" to byteArrayOf(1)))

            assertTrue(zip.isFile)
            assertTrue(!File(dir, "p.zip.tmp").exists(), ".tmp 가 남았습니다")
        }
    }

    @Test
    fun `이미 있는 팩을 덮어쓴다`() {
        temp { dir ->
            val zip = File(dir, "p.zip")
            PackZip.write(zip, mapOf("old.json" to "1".toByteArray()))
            PackZip.write(zip, mapOf("new.json" to "2".toByteArray()))

            assertEquals(setOf("new.json"), entries(zip).keys)
        }
    }

    // --- 경로 일치: 세 파일이 서로를 가리킨다 ---------------------------------------------

    @Test
    fun `item_model 이 가리키는 곳에 정의 파일이 있다`() {
        /*
         * 이 셋이 한 줄이라도 어긋나면 아이템이 **보라-검정 네모**로 나온다.
         *
         *   아이템의 item_model  =  inmc:sword
         *   그 정의 파일         =  assets/inmc/items/sword.json
         *   그 안의 model 이름   =  inmc:item/sword
         *   그 모델 파일         =  assets/inmc/models/item/sword.json
         *
         * 각각 다른 함수가 만들기 때문에 하나만 고쳐도 어긋난다.
         */
        val id = "sword"

        // 1. 아이템에 찍히는 값 ↔ 정의 파일 경로
        assertEquals("${ItemBuilder.NAMESPACE}:$id", PackAssets.itemModelKey(id))
        assertEquals("assets/${ItemBuilder.NAMESPACE}/items/$id.json", PackAssets.itemPath(id))

        // 2. 정의 파일이 가리키는 모델 이름 ↔ 모델 파일 경로
        val definition = JsonParser.parseString(
            PackAssets.itemJson("${PackAssets.NAMESPACE}:item/$id"),
        ).asJsonObject
        val model = definition.getAsJsonObject("model").get("model").asString

        assertEquals("${PackAssets.NAMESPACE}:item/$id", model)
        assertEquals(
            "assets/${PackAssets.NAMESPACE}/models/item/$id.json",
            PackAssets.modelPath(id),
            "정의가 가리키는 이름과 실제 파일 경로가 어긋납니다",
        )

        // 3. 모델이 가리키는 텍스처 이름 ↔ 텍스처 파일 경로
        val modelJson = JsonParser.parseString(PackAssets.modelJson(id)).asJsonObject
        val texture = modelJson.getAsJsonObject("textures").get("layer0").asString

        assertEquals("${PackAssets.NAMESPACE}:item/$id", texture)
        assertEquals("assets/${PackAssets.NAMESPACE}/textures/item/$id.png", PackAssets.texturePath(id))
    }

    @Test
    fun `아이템에 찍는 네임스페이스와 팩 네임스페이스가 같다`() {
        // 다르면 아이템은 `a:sword` 를 가리키는데 팩은 `b/items/sword.json` 에 두게 된다.
        assertEquals(ItemBuilder.NAMESPACE, PackAssets.NAMESPACE)
    }

    // --- 합친 결과를 zip 으로: 한 바퀴 -----------------------------------------------------

    @Test
    fun `합친 결과가 읽히는 zip 으로 나온다`() {
        temp { dir ->
            val merger = PackMerger()

            // 우리가 만드는 것
            merger.put("gen", "pack.mcmeta", PackAssets.mcmetaJson("테스트").toByteArray())
            merger.put("gen", PackAssets.itemPath("sword"), PackAssets.itemJson("inmc:item/sword").toByteArray())
            merger.put("gen", PackAssets.modelPath("sword"), PackAssets.modelJson("sword").toByteArray())
            merger.put("gen", PackAssets.texturePath("sword"), byteArrayOf(0x89.toByte(), 'P'.code.toByte()))

            // 남의 팩이 같은 번역 파일을 갖고 있는 경우
            merger.put("a", "assets/minecraft/lang/ko_kr.json", """{"x":"1"}""".toByteArray())
            merger.put("b", "assets/minecraft/lang/ko_kr.json", """{"y":"2"}""".toByteArray())

            val zip = File(dir, "pack.zip")
            PackZip.write(zip, merger.entries())

            val out = entries(zip)

            // 팩이 갖춰야 하는 것이 다 있는지
            assertNotNull(out["pack.mcmeta"], "pack.mcmeta 가 없으면 클라이언트가 거부합니다")
            assertNotNull(out[PackAssets.itemPath("sword")])
            assertNotNull(out[PackAssets.modelPath("sword")])
            assertNotNull(out[PackAssets.texturePath("sword")])

            // 번역이 섞여 나왔는지 — 덮였으면 한쪽이 사라진다
            val lang = JsonParser.parseString(out.getValue("assets/minecraft/lang/ko_kr.json")).asJsonObject
            assertEquals(2, lang.size(), "두 팩의 번역이 둘 다 있어야 합니다")

            // 팩 안의 json 이 전부 읽히는 json 인지
            for ((path, text) in out) {
                if (!path.endsWith(".json") && path != "pack.mcmeta") continue
                assertNotNull(
                    runCatching { JsonParser.parseString(text) }.getOrNull(),
                    "$path 가 읽히는 json 이 아닙니다",
                )
            }
        }
    }

    @Test
    fun `mcmeta 의 형식 번호가 숫자다`() {
        // 문자열로 나가면 클라이언트가 팩을 통째로 거부한다.
        val pack = JsonParser.parseString(PackAssets.mcmetaJson("설명")).asJsonObject
            .getAsJsonObject("pack")

        assertTrue(pack.get("pack_format").asJsonPrimitive.isNumber)
        assertEquals(PackAssets.PACK_FORMAT, pack.get("pack_format").asInt)
    }
}
