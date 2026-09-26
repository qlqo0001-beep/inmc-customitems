package com.inmc.customitems.item

import org.bukkit.configuration.ConfigurationSection
import java.util.Random
import kotlin.math.roundToLong

/**
 * 능력치 하나의 무작위 폭. 아이템을 만들 때마다 기준값 둘레에서 굴린다(MMOItems 의 spread·max-spread).
 *
 * @param spread 표준편차. 기준값의 비율(0.1 = 10%).
 * @param max 벗어날 수 있는 최대 폭. 기준값의 비율. 0 이면 [spread] 의 세 배.
 */
data class Spread(val spread: Double, val max: Double = 0.0) {

    /** 실제로 쓰는 상한. */
    val cap: Double get() = if (max > 0.0) max else spread * 3.0

    /** 굴린 값 [z](표준정규)를 적용한 결과. */
    fun apply(base: Double, z: Double): Double = round(base * (1.0 + (z * spread).coerceIn(-cap, cap)))

    companion object {
        /** 표준정규 한 번. ±3 에서 자른다 — 그 밖은 천에 셋이라 폭이 뜻을 잃는다. */
        fun roll(random: Random): Double = (random.nextGaussian().coerceIn(-3.0, 3.0) * 1000.0).roundToLong() / 1000.0

        fun round(value: Double): Double = (value * 100.0).roundToLong() / 100.0
    }
}

/**
 * 수식어. 아이템을 만들 때 [chance]% 로 붙어 이름 앞(또는 뒤)에 [name] 을 달고 [stats] 를 더한다
 * (MMOItems 의 modifiers — "날카로운 검", "불꽃의 검").
 */
data class ItemModifier(
    val id: String,
    val name: String,
    /** true 면 이름 뒤에 붙는다. */
    val suffix: Boolean = false,
    val chance: Double = 50.0,
    val stats: Map<Stat, Double> = emptyMap(),
) {
    fun save(section: ConfigurationSection) {
        section.set("name", name)
        if (suffix) section.set("suffix", true)
        section.set("chance", chance)
        if (stats.isNotEmpty()) {
            val node = section.createSection("stats")
            for ((stat, value) in stats) node.set(stat.id, value)
        }
    }

    companion object {
        fun load(id: String, section: ConfigurationSection): ItemModifier = ItemModifier(
            id = id,
            name = section.getString("name") ?: id,
            suffix = section.getBoolean("suffix", false),
            chance = section.getDouble("chance", 50.0).coerceIn(0.0, 100.0),
            stats = section.getConfigurationSection("stats")?.let { node ->
                node.getKeys(false).mapNotNull { key -> Stat.of(key)?.let { it to node.getDouble(key) } }.filter { it.second != 0.0 }.toMap()
            }.orEmpty(),
        )
    }
}
