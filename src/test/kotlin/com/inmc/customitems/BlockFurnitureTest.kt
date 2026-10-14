package com.inmc.customitems

import com.inmc.customitems.item.BlockKind
import com.inmc.customitems.item.BlockLight
import com.inmc.customitems.item.BlockSpec
import com.inmc.customitems.item.Surface
import org.bukkit.block.BlockFace
import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Nexo·ModelEngine 가구를 옮겨 오는 블록 칸 — BetterModel 모델 · 놓는 면 · 빛(2026-10-09). */
class BlockFurnitureTest {

    @Test
    fun `빛은 Nexo 와 같은 모양으로 읽고 쓴다`() {
        assertEquals(BlockLight(0, -1, 0, 15), BlockLight.parse("0,-1,0 15"))
        assertEquals(BlockLight(1, 2, -3, 7), BlockLight.parse("  1, 2,-3   7 "))
        assertEquals("0,-3,0 15", BlockLight.parse("0,-3,0 15")!!.text)
        assertEquals(15, BlockLight.parse("0,-1,0")!!.level, "밝기를 빼면 가장 밝게")
        assertEquals(15, BlockLight.parse("0,-1,0 99")!!.level, "밝기는 0~15 로 자른다")
    }

    @Test
    fun `빛의 틀린 줄은 버린다`() {
        assertNull(BlockLight.parse("0,0,0 15"), "블록 자신의 자리 — 방벽이 있다")
        assertNull(BlockLight.parse("0,-1 15"))
        assertNull(BlockLight.parse("a,b,c 15"))
        assertNull(BlockLight.parse("0,-1,0 밝게"))
        assertNull(BlockLight.parse("0,-1,0 15 extra"))
        assertNull(BlockLight.parse("0,-" + (BlockLight.MAX_DISTANCE + 1) + ",0 15"), "멀면 안 불러온 청크에 닿는다")
        assertNull(BlockLight.parse(""))
    }

    @Test
    fun `놓는 면은 누른 면으로 가린다`() {
        assertTrue(BlockFace.entries.all { Surface.ANY.allows(it) })
        assertTrue(Surface.CEILING.allows(BlockFace.DOWN))
        assertFalse(Surface.CEILING.allows(BlockFace.UP))
        assertFalse(Surface.CEILING.allows(BlockFace.NORTH))
        assertTrue(Surface.FLOOR.allows(BlockFace.UP))
        assertFalse(Surface.FLOOR.allows(BlockFace.DOWN))
        for (face in listOf(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) assertTrue(Surface.WALL.allows(face), face.name)
        assertFalse(Surface.WALL.allows(BlockFace.UP))
        assertFalse(Surface.WALL.allows(BlockFace.DOWN))
    }

    @Test
    fun `가구 칸이 저장했다 읽어도 같다`() {
        val fan = BlockSpec(
            BlockKind.ENTITY, hardness = 0.0, model = "ceiling_fan_iron", surface = Surface.CEILING,
            lights = listOf(BlockLight(0, -1, 0, 15), BlockLight(0, -2, 0, 15), BlockLight(0, -3, 0, 15)),
        )
        val yaml = YamlConfiguration()
        fan.save(yaml.createSection("b"))
        assertEquals(listOf("0,-1,0 15", "0,-2,0 15", "0,-3,0 15"), yaml.getStringList("b.lights"))
        assertEquals("ceiling", yaml.getString("b.surface"))
        assertEquals(fan, BlockSpec.load(yaml.getConfigurationSection("b")))
    }

    @Test
    fun `모델은 엔티티 방식만 읽는다`() {
        val yaml = YamlConfiguration()
        BlockSpec(BlockKind.SOLID, "instrument=bell,note=4").save(yaml.createSection("b"))
        yaml.set("b.model", "ceiling_fan_iron")
        yaml.set("b.surface", "nowhere")
        val loaded = BlockSpec.load(yaml.getConfigurationSection("b"))!!
        assertEquals("", loaded.model, "블록 상태 방식은 그 칸 자체가 모양이다")
        assertEquals(Surface.ANY, loaded.surface, "모르는 면은 아무 면으로")
    }
}
