package com.inmc.customitems.block

import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import kotlin.random.Random

/**
 * 커스텀 블록의 **월드 생성**(2026-10-09, 사용자 요청 — ItemsAdder `worlds_populators` 와 같은 뜻). 처음 만들어지는 청크에 광맥으로 심는다.
 *
 * 청크마다 [chunkChance] % 로 광맥 [veins] 개, 광맥 하나는 [veinSize] 칸까지. 광맥은 높이 [minY]~[maxY] 의 한 점에서 옆 칸으로 걸어가며 자란다.
 * **바꿀 블록([replace]) 자리에만** 심고 공기·물·다른 광석은 건너뛴다. 광맥은 그 청크 안에만 — 옆 청크를 건드리면 그 청크를 불러와야 한다.
 *
 * @param worlds 심을 월드 이름(대소문자 무시). 비면 아무 데도 안 심는다 — "전부"로 두면 스폰·던전 월드에 새 청크가 생길 때 원치 않는 곳에 박힌다.
 */
data class OreGen(
    val worlds: List<String> = emptyList(),
    val replace: List<Material> = DEFAULT_REPLACE,
    val minY: Int = 0,
    val maxY: Int = 64,
    val chunkChance: Double = 30.0,
    val veins: Int = 2,
    val veinSize: Int = 7,
) {

    fun inWorld(name: String): Boolean = worlds.any { it.equals(name, ignoreCase = true) }

    /** 청크 하나에 심는 칸의 최댓값(확률을 곱한 평균은 [average]). */
    val maxPerChunk: Int get() = veins * veinSize

    /** 청크 하나에 평균 몇 칸까지 — 확률 × 광맥 × 크기. 바꿀 블록이 아닌 자리는 빠지므로 실제는 이보다 적다. */
    val average: Double get() = chunkChance / 100.0 * veins * veinSize

    fun save(section: ConfigurationSection) {
        section.set("worlds", worlds)
        section.set("replace", replace.map { it.name })
        section.set("min-y", minY)
        section.set("max-y", maxY)
        section.set("chunk-chance", chunkChance)
        section.set("veins", veins)
        section.set("vein-size", veinSize)
    }

    companion object {
        const val MAX_VEINS = 32
        const val MAX_VEIN_SIZE = 64
        const val MIN_Y = -2032
        const val MAX_Y = 2031

        /** 기본 바꿀 블록 — 오버월드 땅속의 돌 종류(바닐라 광석이 박히는 자리). */
        val DEFAULT_REPLACE: List<Material> = listOf(
            Material.STONE, Material.DEEPSLATE, Material.ANDESITE, Material.DIORITE, Material.GRANITE, Material.TUFF,
        )

        /** 바꿀 블록 고르기 화면에 늘 보이는 것 — 자연에 흔한 땅 블록. 여기 없는 것은 손에 든 블록으로 더한다. */
        val COMMON_REPLACE: List<Material> = listOf(
            Material.STONE, Material.DEEPSLATE, Material.ANDESITE, Material.DIORITE, Material.GRANITE, Material.TUFF, Material.CALCITE,
            Material.DIRT, Material.GRAVEL, Material.CLAY, Material.SAND, Material.SANDSTONE, Material.RED_SAND, Material.TERRACOTTA,
            Material.COBBLESTONE, Material.COBBLED_DEEPSLATE, Material.SMOOTH_BASALT, Material.DRIPSTONE_BLOCK,
            Material.NETHERRACK, Material.BASALT, Material.BLACKSTONE, Material.SOUL_SAND, Material.SOUL_SOIL, Material.END_STONE,
            Material.SNOW_BLOCK, Material.PACKED_ICE, Material.MUD,
        )

        fun load(section: ConfigurationSection?): OreGen? {
            section ?: return null
            val minY = section.getInt("min-y", 0).coerceIn(MIN_Y, MAX_Y)
            val maxY = section.getInt("max-y", 64).coerceIn(MIN_Y, MAX_Y)
            return OreGen(
                worlds = section.getStringList("worlds").map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() },
                replace = (if (section.isList("replace")) section.getStringList("replace").mapNotNull { Material.matchMaterial(it.trim()) } else DEFAULT_REPLACE).distinct(),
                minY = minOf(minY, maxY),
                maxY = maxOf(minY, maxY),
                chunkChance = section.getDouble("chunk-chance", 30.0).coerceIn(0.0, 100.0),
                veins = section.getInt("veins", 2).coerceIn(1, MAX_VEINS),
                veinSize = section.getInt("vein-size", 7).coerceIn(1, MAX_VEIN_SIZE),
            )
        }

        /**
         * 이 청크에 심을 자리(청크 안 x·z 0~15, 실제 y). 순수 — 블록을 보지 않는다(바꿀 블록인지는 놓는 쪽이 본다).
         * 확률을 넘으면 빈 목록. [force] 면 확률을 보지 않는다(관리자 시험). 높이는 월드 높이([worldMin]~[worldMax] 미만)로 자른다.
         * 광맥은 한 점에서 위아래·옆 한 칸씩 걸으며 자란다 — 이미 고른 칸은 다시 세지 않고, 청크·높이 밖으로는 안 나간다.
         */
        fun plan(rule: OreGen, worldMin: Int, worldMax: Int, random: Random, force: Boolean = false): List<Triple<Int, Int, Int>> {
            val low = maxOf(rule.minY, worldMin)
            val high = minOf(rule.maxY, worldMax - 1)
            if (low > high) return emptyList()
            if (!force && random.nextDouble() * 100.0 >= rule.chunkChance) return emptyList()
            val out = LinkedHashSet<Triple<Int, Int, Int>>()
            repeat(rule.veins) {
                var x = random.nextInt(16)
                var y = random.nextInt(low, high + 1)
                var z = random.nextInt(16)
                val vein = LinkedHashSet<Triple<Int, Int, Int>>()
                var steps = 0
                while (vein.size < rule.veinSize && steps < rule.veinSize * 4) {
                    vein += Triple(x, y, z)
                    when (random.nextInt(6)) {
                        0 -> x = (x + 1).coerceAtMost(15)
                        1 -> x = (x - 1).coerceAtLeast(0)
                        2 -> z = (z + 1).coerceAtMost(15)
                        3 -> z = (z - 1).coerceAtLeast(0)
                        4 -> y = (y + 1).coerceAtMost(high)
                        else -> y = (y - 1).coerceAtLeast(low)
                    }
                    steps++
                }
                out += vein
            }
            return out.toList()
        }
    }
}
