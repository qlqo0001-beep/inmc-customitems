package com.inmc.customitems

import com.google.gson.JsonParser
import com.inmc.customitems.hook.RoleAppearance
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.Tier
import com.inmc.customitems.pack.PackAssets
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 역할 아이템의 기본 겉모습(`role-appearance.yml` + 그림).
 *
 * 그림 경로 하나가 어긋나면 **오류 없이** 그 아이템만 바닐라 모양으로 나온다(빌드 보고서의 "빠진 텍스처"에만 적힌다).
 */
class RoleAppearanceTest {

    private val entries: List<RoleAppearance.Entry> by lazy {
        val stream = javaClass.classLoader.getResourceAsStream(RoleAppearance.RESOURCE)
        assertNotNull(stream, "role-appearance.yml 이 없습니다")
        RoleAppearance.load(stream.use { YamlConfiguration.loadConfiguration(InputStreamReader(it, StandardCharsets.UTF_8)) })
    }

    @Test
    fun `배포 항목이 전부 읽힌다 — 물고기 68 · 생선살 21 · 미끼 11 · 낚싯대 10`() {
        val byRole = entries.groupingBy { it.role }.eachCount()
        assertEquals(mapOf("fishing.fish" to 68, "fishing.fillet" to 21, "fishing.bait" to 11, "fishing.rod" to 10), byRole)
    }

    @Test
    fun `항목마다 그림이 16x16 png 로 들어 있다`() {
        for (entry in entries) {
            val stream = javaClass.classLoader.getResourceAsStream(RoleAppearance.FOLDER + "/" + entry.texture)
            assertNotNull(stream, "${entry.id} 의 그림이 없습니다: ${entry.texture}")
            val image = stream.use { ImageIO.read(it) }
            assertNotNull(image, "${entry.id} 의 그림을 읽지 못했습니다")
            assertEquals(16, image.width, entry.id)
            assertEquals(16, image.height, entry.id)
        }
    }

    @Test
    fun `낚싯대는 던진 모양도 들어 있다`() {
        for (entry in entries.filter { it.role == "fishing.rod" }) {
            val cast = RoleAppearance.FOLDER + "/" + PackAssets.castTexture(entry.texture)
            assertNotNull(javaClass.classLoader.getResource(cast), "${entry.id} 의 던진 모양이 없습니다: $cast")
        }
    }

    @Test
    fun `물고기 등급과 커스텀아이템 등급이 맞다`() {
        fun tier(fish: String) = entries.first { it.match["fish"] == fish }.tier
        assertEquals(Tier.COMMON, tier("cod"))
        assertEquals(Tier.UNCOMMON, tier("pufferfish"))
        assertEquals(Tier.RARE, tier("tuna"))
        assertEquals(Tier.EPIC, tier("coelacanth"))
        assertEquals(Tier.EPIC, tier("megalodon"))
        assertEquals(Tier.LEGENDARY, tier("world_serpent"))
        assertEquals(Tier.MYTHIC, tier("genesis_fish"))
    }

    private fun adopted(role: String, values: Map<String, String>, material: Material = Material.COD) =
        CustomItem("cod", material, displayName = "<dark_aqua>생대구", roles = mapOf(role to values))

    @Test
    fun `옮겨 온 역할 아이템에 그림·등급을 입히고 이름 색을 뗀다`() {
        val decorated = RoleAppearance.decorate(adopted("fishing.fish", mapOf("fish" to "cod", "legacy" to "x")), entries)
        assertNotNull(decorated)
        assertEquals("fishing/fish/cod.png", decorated.texture)
        assertEquals(Tier.COMMON, decorated.tier)
        assertEquals("생대구", decorated.displayName, "옮겨 온 색이 등급 색을 가리면 안 된다")
        assertTrue(PackAssets.needsPack(decorated))
    }

    @Test
    fun `겉모습이 이미 있으면 건드리지 않는다`() {
        val item = adopted("fishing.fish", mapOf("fish" to "cod"))
        assertNull(RoleAppearance.decorate(item.copy(texture = "mine.png"), entries))
        assertNull(RoleAppearance.decorate(item.copy(customModelData = 12), entries))
        assertNull(RoleAppearance.decorate(item.copy(itemModel = "ia:fish"), entries))
    }

    @Test
    fun `역할 값이 다르면 입히지 않는다`() {
        assertNull(RoleAppearance.decorate(adopted("fishing.fish", mapOf("fish" to "no_such_fish")), entries))
        assertNull(RoleAppearance.decorate(adopted("fishing.rod", mapOf("fish" to "cod")), entries))
        assertNull(RoleAppearance.decorate(CustomItem("plain", Material.COD), entries))
    }

    @Test
    fun `낚싯대 텍스처는 손에 든 막대 모양이고 던지면 모양이 바뀐다`() {
        val rod = RoleAppearance.decorate(adopted("fishing.rod", mapOf("id" to "basic_rod"), Material.FISHING_ROD), entries)
        assertNotNull(rod)
        assertTrue(PackAssets.isRod(rod))
        assertFalse(PackAssets.isRod(CustomItem("x", Material.COD, texture = "a.png")))

        val model = JsonParser.parseString(PackAssets.rodModelJson("basic_rod")).asJsonObject
        assertEquals("minecraft:item/handheld_rod", model.get("parent").asString)

        val definition = JsonParser.parseString(PackAssets.rodItemJson("inmc:item/a", "inmc:item/a_cast")).asJsonObject.getAsJsonObject("model")
        assertEquals("minecraft:fishing_rod/cast", definition.get("property").asString)
        assertEquals("inmc:item/a", definition.getAsJsonObject("on_false").get("model").asString)
        assertEquals("inmc:item/a_cast", definition.getAsJsonObject("on_true").get("model").asString)
        assertEquals("rod/basic_cast.png", PackAssets.castTexture("rod/basic.png"))
    }
}
