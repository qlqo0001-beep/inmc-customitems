package com.inmc.customitems

import com.inmc.customitems.block.Harvest
import com.inmc.customitems.block.HeldTool
import com.inmc.customitems.block.ItemsAdderBlocks
import com.inmc.customitems.block.Mining
import com.inmc.customitems.block.ToolGrades
import com.inmc.customitems.block.ToolKind
import com.inmc.customitems.item.BlockDrop
import com.inmc.customitems.item.BlockKind
import com.inmc.customitems.item.BlockSpec
import kr.inmc.core.item.ItemRef
import kr.inmc.core.item.StoredItem
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MiningTest {

    private fun block(hardness: Double, tool: ToolKind? = null, tier: Int = 0, harvest: Harvest = Harvest.DROPS) =
        BlockSpec(BlockKind.SOLID, "instrument=basedrum,note=0", hardness = hardness, tool = tool, toolTier = tier, harvest = harvest)

    private val stone = block(1.5, ToolKind.PICKAXE)
    private val obsidian = block(50.0, ToolKind.PICKAXE, tier = 3)

    // --- 도구 알아보기 --------------------------------------------------------------------

    @Test
    fun `곡괭이는 도끼로 읽히지 않는다 - 이름이 _AXE 로도 끝나 보이지만 앞이 PICK 이다`() {
        assertEquals(ToolKind.PICKAXE, ToolKind.ofMaterial(Material.DIAMOND_PICKAXE))
        assertEquals(ToolKind.AXE, ToolKind.ofMaterial(Material.DIAMOND_AXE))
        assertEquals(ToolKind.SHEARS, ToolKind.ofMaterial(Material.SHEARS))
        assertNull(ToolKind.ofMaterial(Material.STICK))
    }

    @Test
    fun `재질 등급은 바닐라 순서다 - 금은 나무와 같고 구리는 돌과 같다`() {
        assertEquals(0, ToolGrades.held(Material.WOODEN_PICKAXE).tier)
        assertEquals(0, ToolGrades.held(Material.GOLDEN_PICKAXE).tier)
        assertEquals(1, ToolGrades.held(Material.STONE_PICKAXE).tier)
        assertEquals(1, ToolGrades.held("COPPER_PICKAXE").tier)
        assertEquals(2, ToolGrades.held(Material.IRON_PICKAXE).tier)
        assertEquals(3, ToolGrades.held(Material.DIAMOND_PICKAXE).tier)
        assertEquals(4, ToolGrades.held(Material.NETHERITE_PICKAXE).tier)
        assertEquals(HeldTool.HAND, ToolGrades.held(Material.STICK))
        assertNull(ToolGrades.vanillaTier(Material.STICK), "도구가 아니면 채굴 등급 칸이 안 보인다")
    }

    // --- 캐는 시간 = 바닐라 -----------------------------------------------------------------

    @Test
    fun `돌과 같은 블록은 바닐라 돌과 같은 시간에 캐진다`() {
        // 바닐라 위키: 돌 — 맨손 7.5초(안 나옴) · 나무 곡괭이 1.15초 · 다이아몬드 곡괭이 0.3초
        assertEquals(150, Mining.ticks(stone, HeldTool.HAND))
        assertFalse(Mining.yields(stone, HeldTool.HAND))
        assertEquals(23, Mining.ticks(stone, ToolGrades.held(Material.WOODEN_PICKAXE)))
        assertEquals(6, Mining.ticks(stone, ToolGrades.held(Material.DIAMOND_PICKAXE)))
    }

    @Test
    fun `흑요석과 같은 블록은 등급이 모자라면 느리고 아무것도 안 나온다`() {
        // 바닐라 위키: 흑요석 — 다이아몬드 곡괭이 9.4초 · 철 곡괭이 41.7초(안 나옴)
        val diamond = ToolGrades.held(Material.DIAMOND_PICKAXE)
        val iron = ToolGrades.held(Material.IRON_PICKAXE)
        assertEquals(188, Mining.ticks(obsidian, diamond))
        assertTrue(Mining.yields(obsidian, diamond))
        assertEquals(834, Mining.ticks(obsidian, iron), "등급이 모자라도 빠르기는 재질대로 — 바닐라와 같다")
        assertFalse(Mining.yields(obsidian, iron))
    }

    @Test
    fun `맞는 도구여야 부서짐이면 등급이 모자란 도구로는 아예 못 캔다`() {
        val strict = obsidian.copy(harvest = Harvest.BREAK)
        assertNull(Mining.ticks(strict, ToolGrades.held(Material.IRON_PICKAXE)))
        assertNull(Mining.ticks(strict, HeldTool.HAND))
        assertTrue(Mining.canBreak(strict, ToolGrades.held(Material.DIAMOND_PICKAXE)))
    }

    @Test
    fun `아무 도구나면 맨손으로도 나온다 - 맞는 도구는 빠를 뿐`() {
        val dirt = block(0.5, ToolKind.SHOVEL, harvest = Harvest.ANY)
        assertTrue(Mining.yields(dirt, HeldTool.HAND))
        // 바닐라 흙: 맨손 0.75초 · 나무 삽 0.4초
        assertEquals(15, Mining.ticks(dirt, HeldTool.HAND))
        assertEquals(8, Mining.ticks(dirt, ToolGrades.held(Material.WOODEN_SHOVEL)))
    }

    @Test
    fun `다이아몬드보다 높은 등급은 커스텀 곡괭이의 채굴 등급으로만 캔다`() {
        val mythril = block(10.0, ToolKind.PICKAXE, tier = 5, harvest = Harvest.BREAK)
        assertFalse(Mining.canBreak(mythril, ToolGrades.held(Material.NETHERITE_PICKAXE)))
        assertTrue(Mining.canBreak(mythril, ToolGrades.held(Material.NETHERITE_PICKAXE, tierOverride = 5)))
        assertTrue(Mining.canBreak(mythril, ToolGrades.held(Material.IRON_PICKAXE, tierOverride = 6)), "등급만 오르고 빠르기는 재질 그대로")
        assertFalse(Mining.canBreak(mythril, ToolGrades.held(Material.DIAMOND_AXE, tierOverride = 9)), "갈래가 다르면 등급이 높아도 안 된다")
    }

    @Test
    fun `맞는 도구를 안 정한 블록은 무엇으로든 같은 빠르기로 캔다`() {
        val glass = block(0.3)
        assertEquals(Mining.ticks(glass, HeldTool.HAND), Mining.ticks(glass, ToolGrades.held(Material.NETHERITE_PICKAXE)))
        assertTrue(Mining.yields(glass, HeldTool.HAND))
    }

    @Test
    fun `효율은 맞는 도구에만 더하고 공중이면 다섯 배 느리다`() {
        val diamond = ToolGrades.held(Material.DIAMOND_PICKAXE)
        assertEquals(8.0 + 26.0, Mining.speed(stone, diamond, efficiency = 26.0))
        assertEquals(1.0, Mining.speed(stone, HeldTool.HAND, efficiency = 26.0), "맨손(빠르기 1)에는 효율이 안 붙는다 — 바닐라")
        assertEquals(8.0 * 1.4, Mining.speed(stone, diamond, haste = 1), 1e-9)
        assertEquals(8.0 / 5.0, Mining.speed(stone, diamond, onGround = false), 1e-9)
        assertEquals(8.0 * 0.2, Mining.speed(stone, diamond, submerged = 0.2), 1e-9)
    }

    @Test
    fun `단단함 0 은 한 번에 부서진다`() {
        assertEquals(1, Mining.ticks(block(0.0), HeldTool.HAND))
    }

    @Test
    fun `우리 수정자를 뺀 블록 파괴 속도를 바닐라 셈대로 다시 센다`() {
        // 기본 1 + 0.5 → ×(1 + 0.5·1.5) → 전체 ×1.2
        assertEquals((1.5 + 1.5 * 0.5) * 1.2, Mining.attribute(1.0, listOf(0.5), listOf(0.5), listOf(0.2)), 1e-9)
        assertEquals(1.0, Mining.attribute(1.0, emptyList(), emptyList(), emptyList()))
    }

    // --- 나오는 것 -----------------------------------------------------------------------

    private val diamondItem = StoredItem(ItemRef.parse("minecraft:diamond"), Material.DIAMOND)

    @Test
    fun `확률 100 은 늘 나오고 0 은 한 번도 안 나온다`() {
        val random = Random(1)
        repeat(200) {
            val rolled = Mining.roll(listOf(BlockDrop(diamondItem, 1, 1, 100.0), BlockDrop(diamondItem, 1, 1, 0.0)), 0, random)
            assertEquals(1, rolled.size)
            assertEquals(100.0, rolled.single().first.chance)
        }
    }

    @Test
    fun `개수는 최소와 최대 사이이고 행운은 개수를 늘리기만 한다`() {
        val random = Random(7)
        val drop = BlockDrop(diamondItem, 2, 4)
        val plain = (1..500).map { Mining.roll(listOf(drop), 0, random).single().second }
        assertEquals(setOf(2, 3, 4), plain.toSet())
        val lucky = (1..500).map { Mining.roll(listOf(drop), 3, random).single().second }
        assertTrue(lucky.all { it >= 2 && it <= 4 * 4 }, "행운 III 은 최대 ×4 (바닐라 광석)")
        assertTrue(lucky.average() > plain.average())
    }

    @Test
    fun `경험치는 적은 범위 안에서 나오고 안 적으면 0`() {
        val random = Random(3)
        assertEquals(0, Mining.exp(block(1.5), random))
        val ore = block(3.0).copy(expMin = 3, expMax = 7)
        assertTrue((1..200).map { Mining.exp(ore, random) }.all { it in 3..7 })
    }

    // --- 저장 ----------------------------------------------------------------------------

    @Test
    fun `블록 설정이 저장했다 읽어도 같고 기본값은 적지 않는다`() {
        val full = BlockSpec(
            BlockKind.SOLID, "instrument=bell,note=4", drop = false,
            hardness = 3.0, tool = ToolKind.PICKAXE, toolTier = 5, harvest = Harvest.BREAK,
            drops = listOf(BlockDrop(diamondItem, 1, 3, 25.0), BlockDrop(diamondItem, 2, 2)),
            silkTouch = true, fortune = true, expMin = 3, expMax = 7,
        )
        val yaml = YamlConfiguration()
        full.save(yaml.createSection("b"))
        assertEquals(full, BlockSpec.load(yaml.getConfigurationSection("b")))

        val plain = YamlConfiguration()
        BlockSpec(BlockKind.ENTITY).save(plain.createSection("b"))
        assertEquals(setOf("kind"), plain.getConfigurationSection("b")!!.getKeys(false), "기본값을 적으면 이 칸이 생긴 것만으로 모든 블록 아이템의 지문이 바뀐다")
    }

    // --- 옛 IA ---------------------------------------------------------------------------

    @Test
    fun `IA 의 부술 수 있는 도구 목록은 한 갈래일 때만 가장 낮은 등급으로 옮긴다`() {
        assertEquals(ToolKind.PICKAXE to 2, ItemsAdderBlocks.whitelist(listOf("DIAMOND_PICKAXE", "IRON_PICKAXE", "NETHERITE_PICKAXE")))
        assertEquals(ToolKind.PICKAXE to 0, ItemsAdderBlocks.whitelist(listOf("PICKAXE")))
        assertEquals(ToolKind.AXE to 3, ItemsAdderBlocks.whitelist(listOf("minecraft:diamond_axe")))
        assertNull(ItemsAdderBlocks.whitelist(listOf("DIAMOND_PICKAXE", "DIAMOND_AXE")), "갈래가 섞이면 우리 규칙으로 옮길 수 없다")
        assertNull(ItemsAdderBlocks.whitelist(listOf("STICK")))
        assertNull(ItemsAdderBlocks.whitelist(emptyList()))
    }
}
