package com.inmc.customitems.item

import com.inmc.customitems.CustomItems
import kr.inmc.core.store.YamlFileStore
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration

/** 세트 효과 한 단계. 능력치·입는 동안의 물약 효과·인첸트 효과. */
data class SetBonus(
    val stats: Map<Stat, Double> = emptyMap(),
    /** 물약 id → 단계(1 = I). */
    val potions: Map<String, Int> = emptyMap(),
    /**
     * 인첸트 효과 — 인첸트 플러그인의 세트 문법(`events`·`equipped`·`unequipped`·`disabled-worlds`)을 그대로 담은 트리.
     * **우리는 뜻을 모른다**(규칙 3). 세트를 세어 core 로 넘기면([kr.inmc.core.integration.CustomItemHook.SetEffects])
     * 인첸트 엔진이 돌리고, 편집 화면도 인첸트가 그린다.
     */
    val effects: Map<String, Any?> = emptyMap(),
) {
    val isEmpty: Boolean get() = stats.isEmpty() && potions.isEmpty() && effects.isEmpty()

    fun save(section: ConfigurationSection) {
        for ((stat, value) in stats) section.set(stat.id, value)
        for ((potion, level) in potions) section.set("potion-" + potion, level)
        if (effects.isNotEmpty()) section.createSection(EFFECTS, effects)
    }

    companion object {
        const val EFFECTS = "enchant-effects"

        /** MMOItems 모양 그대로 — `attack-damage: 5` · `potion-speed: 1`. 인첸트 효과는 `enchant-effects:` 아래에. */
        fun load(section: ConfigurationSection): SetBonus {
            val stats = LinkedHashMap<Stat, Double>()
            val potions = LinkedHashMap<String, Int>()
            val effects = section.getConfigurationSection(EFFECTS)?.let(::tree).orEmpty()
            for (key in section.getKeys(false)) {
                if (key == EFFECTS) continue
                if (key.startsWith("potion-")) {
                    potions[key.removePrefix("potion-").lowercase()] = section.getInt(key, 1).coerceIn(1, 255)
                    continue
                }
                val stat = Stat.of(key) ?: continue
                val value = section.getDouble(key)
                if (value != 0.0) stats[stat] = value
            }
            return SetBonus(stats, potions, effects)
        }

        /** 섹션을 맵 트리로(아래 섹션도 맵으로). 다시 적을 때 `createSection(경로, 맵)` 이 그대로 되살린다. */
        private fun tree(section: ConfigurationSection): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>()
            for (key in section.getKeys(false)) {
                val value = section.get(key)
                out[key] = if (value is ConfigurationSection) tree(value) else value
            }
            return out
        }
    }
}

/**
 * 아이템 세트(MMOItems 의 item-sets). 같은 세트의 아이템을 **여럿** 입고 들면 벌 수에 따라 효과가 붙는다.
 * 2벌·3벌·4벌 효과는 **쌓인다** — 4벌을 입으면 2·3·4 가 다 붙는다.
 *
 * 인첸트 플러그인의 "방어구 세트"(네 부위를 다 입어야 도는 효과 줄)와는 다른 것이다.
 */
data class ItemSet(
    val id: String,
    val name: String,
    val bonuses: Map<Int, SetBonus> = emptyMap(),
) {
    /** [pieces] 벌을 입었을 때 붙는 단계들. 작은 것부터. */
    fun active(pieces: Int): List<Pair<Int, SetBonus>> = bonuses.entries.filter { it.key <= pieces }.sortedBy { it.key }.map { it.key to it.value }

    fun save(section: ConfigurationSection) {
        section.set("name", name)
        val node = section.createSection("bonuses")
        for ((count, bonus) in bonuses.entries.sortedBy { it.key }) bonus.save(node.createSection(count.toString()))
    }

    companion object {
        fun load(id: String, section: ConfigurationSection): ItemSet {
            val bonuses = LinkedHashMap<Int, SetBonus>()
            section.getConfigurationSection("bonuses")?.let { node ->
                for (key in node.getKeys(false)) {
                    val count = key.toIntOrNull()?.takeIf { it in 1..10 } ?: continue
                    node.getConfigurationSection(key)?.let { bonuses[count] = SetBonus.load(it) }
                }
            }
            return ItemSet(id, section.getString("name") ?: id, bonuses)
        }
    }
}

/** `sets.yml`. */
class ItemSetRegistry(private val custom: CustomItems) : YamlFileStore(
    io = custom.io,
    path = listOf("sets.yml"),
    header = """
        아이템 세트. /커스텀아이템 관리 → 아이템 세트 에서 GUI 로 고치는 것을 권장합니다.
        아이템 설정 화면에서 그 아이템이 속한 세트를 고릅니다.

        bonuses 의 숫자는 벌 수입니다. 더 많이 입으면 앞 단계도 같이 붙습니다.
          능력치 id: 값      예) attack-damage: 5
          potion-물약: 단계   예) potion-speed: 1 (신속 I, 입는 동안)
          enchant-effects:   인첸트 효과(인첸트 플러그인의 세트 문법 — events · equipped · unequipped · disabled-worlds).
                             화면의 "인첸트 효과" 에서 고칩니다. 인첸트 플러그인이 돌립니다
    """.trimIndent() + "\n",
    what = "아이템 세트",
) {

    private val sets = LinkedHashMap<String, ItemSet>()

    fun all(): List<ItemSet> = sets.values.toList()

    fun get(id: String?): ItemSet? = id?.takeIf { it.isNotBlank() }?.let { sets[it.lowercase()] }

    fun put(set: ItemSet) {
        sets[set.id] = set
        markDirty()
        custom.items.onSetChanged(set.id)
    }

    fun remove(id: String): Boolean = (sets.remove(id.lowercase()) != null).also {
        if (it) {
            markDirty()
            custom.items.onSetChanged(id.lowercase())
        }
    }

    override fun read(config: YamlConfiguration) {
        sets.clear()
        for (key in config.getKeys(false)) {
            config.getConfigurationSection(key)?.let { sets[key.lowercase()] = ItemSet.load(key.lowercase(), it) }
        }
    }

    override fun write(config: YamlConfiguration) {
        for (set in sets.values) set.save(config.createSection(set.id))
    }
}
