package com.inmc.customitems

import com.inmc.customitems.block.ItemsAdderBlocks
import com.inmc.customitems.block.OreGen
import com.inmc.customitems.block.OreGenerator
import com.inmc.customitems.item.BlockKind
import com.inmc.customitems.item.BlockSpec
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 커스텀 블록의 월드 생성(광맥, 2026-10-09) — 자리 굴리기 · 저장 · 옛 ItemsAdder 광석 생성 옮기기. */
class OreGenTest {

    private val rule = OreGen(worlds = listOf("world"), minY = 0, maxY = 80, chunkChance = 30.0, veins = 2, veinSize = 7)

    @Test
    fun `광맥 자리는 청크 안 · 높이 안이고 광맥 수 × 크기를 넘지 않는다`() {
        repeat(500) { n ->
            val spots = OreGen.plan(rule, -64, 320, Random(n), force = true)
            assertTrue(spots.isNotEmpty())
            assertTrue(spots.size <= rule.maxPerChunk, "${spots.size}")
            assertTrue(spots.all { (x, y, z) -> x in 0..15 && z in 0..15 && y in 0..80 }, spots.toString())
            assertEquals(spots.size, spots.toSet().size, "같은 칸을 두 번 세지 않는다")
        }
    }

    @Test
    fun `청크 확률 — 0 이면 안 생기고 100 이면 늘, 평균은 확률만큼`() {
        assertTrue((0 until 200).all { OreGen.plan(rule.copy(chunkChance = 0.0), -64, 320, Random(it)).isEmpty() })
        assertTrue((0 until 200).all { OreGen.plan(rule.copy(chunkChance = 100.0), -64, 320, Random(it)).isNotEmpty() })
        val hits = (0 until 10_000).count { OreGen.plan(rule, -64, 320, Random(it)).isNotEmpty() }
        assertTrue(hits in 2_700..3_300, "30% 인데 $hits / 10000")
    }

    @Test
    fun `높이는 월드 높이로 잘리고, 겹치는 데가 없으면 안 생긴다`() {
        val deep = rule.copy(minY = -65, maxY = 0)
        repeat(200) { assertTrue(OreGen.plan(deep, -64, 320, Random(it), force = true).all { (_, y, _) -> y in -64..0 }) }
        assertEquals(emptyList(), OreGen.plan(deep, 0 + 1, 128, Random(1), force = true), "월드가 1 부터면 -65~0 은 없다")
        assertEquals(emptyList(), OreGen.plan(rule.copy(minY = 400, maxY = 500), -64, 320, Random(1), force = true))
    }

    @Test
    fun `같은 월드 시드·청크·블록이면 같은 자리`() {
        val a = OreGen.plan(rule, -64, 320, Random(OreGenerator.seed(42L, 3, -7, "block_1")), force = true)
        val b = OreGen.plan(rule, -64, 320, Random(OreGenerator.seed(42L, 3, -7, "block_1")), force = true)
        val other = OreGen.plan(rule, -64, 320, Random(OreGenerator.seed(42L, 3, -7, "block_11")), force = true)
        assertEquals(a, b)
        assertTrue(a != other)
    }

    @Test
    fun `저장했다 읽으면 같다 — 블록 상태 방식만, 엔티티는 버린다`() {
        val spec = BlockSpec(BlockKind.SOLID, "instrument=basedrum,note=9", generation = rule.copy(replace = listOf(Material.STONE, Material.NETHERRACK)))
        val yaml = YamlConfiguration().apply { spec.save(createSection("block")) }
        assertEquals(spec, BlockSpec.load(yaml.getConfigurationSection("block")))
        val entity = YamlConfiguration().apply { spec.copy(kind = BlockKind.ENTITY, state = "").save(createSection("block")) }
        assertNull(BlockSpec.load(entity.getConfigurationSection("block"))!!.generation)
        val none = YamlConfiguration().apply { BlockSpec(BlockKind.SOLID, "x").save(createSection("block")) }
        assertTrue("generation" !in none.saveToString(), "꺼져 있으면 적지 않는다")
    }

    @Test
    fun `옛 ItemsAdder worlds_populators 를 그대로 옮긴다`() {
        val yaml = YamlConfiguration().apply {
            loadFromString(
                """
                worlds_populators:
                  block_1:
                    block: inmc:block_1
                    worlds: [world, newbi_world]
                    replaceable_blocks: [STONE, DIRT, ANDESITE, GRANITE, COBBLESTONE, GRAVEL, DEEPSLATE, COBBLED_DEEPSLATE, CALCITE, TUFF]
                    chunk_chance: 30
                    max_height: 80
                    min_height: 0
                    vein_blocks: 7
                    chunk_veins: 2
                  block_11:
                    block: inmc:block_11
                    worlds: [world]
                    replaceable_blocks: [STONE, NOT_A_BLOCK]
                    chunk_chance: 30
                    max_height: 0
                    min_height: -65
                    vein_blocks: 5
                    chunk_veins: 2
                """.trimIndent(),
            )
        }
        val found = ItemsAdderBlocks.populators(yaml.getConfigurationSection("worlds_populators")!!).toMap()
        assertEquals(
            OreGen(
                worlds = listOf("world", "newbi_world"),
                replace = listOf(Material.STONE, Material.DIRT, Material.ANDESITE, Material.GRANITE, Material.COBBLESTONE, Material.GRAVEL,
                    Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.CALCITE, Material.TUFF),
                minY = 0, maxY = 80, chunkChance = 30.0, veins = 2, veinSize = 7,
            ),
            found["block_1"],
        )
        assertEquals(OreGen(listOf("world"), listOf(Material.STONE), -65, 0, 30.0, 2, 5), found["block_11"])
    }
}
