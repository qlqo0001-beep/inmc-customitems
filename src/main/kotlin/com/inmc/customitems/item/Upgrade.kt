package com.inmc.customitems.item

import com.inmc.customitems.craft.Part
import com.inmc.customitems.craft.loadParts
import com.inmc.customitems.craft.saveParts
import org.bukkit.configuration.ConfigurationSection

/** 강화에 실패했을 때. */
enum class FailResult(val id: String, val display: String) {
    KEEP("keep", "그대로"),
    DOWN("down", "한 단계 하락"),
    RESET("reset", "0강으로 초기화"),
    DESTROY("destroy", "파괴"),
    ;

    companion object {
        fun of(id: String?): FailResult = entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: KEEP
    }
}

/**
 * 단계의 능력치를 어떻게 적는가.
 *
 * - [ADD] 증가량 — 단계마다 더해지는 고정값과 "기본 능력치의 %". 누적된다. 무기용·방어구용 같은 **공용** 방식에 맞다 —
 *   아이템마다 기본값이 달라도 같은 표를 쓸 수 있다.
 * - [ABSOLUTE] 단계별 전체값 — "+7 일 때 능력치 전체"를 그대로 적는다(MMOItems 에서 단계마다 아이템을 따로 만들던 방식).
 *   능력치를 안 적은 단계는 바로 앞 단계의 것을 이어 쓴다(모양·등급만 바꾸는 단계).
 */
enum class UpgradeMode(val id: String, val display: String) {
    ADD("add", "증가량"),
    ABSOLUTE("absolute", "단계별 전체값"),
    ;

    companion object {
        fun of(id: String?): UpgradeMode = entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: ADD
    }
}

/**
 * 강화 한 단계 — **이 단계로 올라가는 시도**(확률·실패 결과)와 **올라간 뒤의 모습**(능력치·등급·모양).
 *
 * 등급·모양은 "이 단계부터"다. 안 적은 단계는 앞 단계의 것을 이어 쓴다.
 *
 * @param percents [UpgradeMode.ADD] 에서만 — 능력치마다 기본(굴린 값)의 이만큼(%)을 더한다. 누적된다(MMOItems 의 `공격력: 2%`).
 * @param customModelData 낡은 번호 — 옮기기 전의 값일 뿐(다음 리소스팩 빌드가 [model] 로 옮긴다, [CustomItem.customModelData] 와 같다).
 * @param texture 팩 `textures/` 의 png. 적으면 이 단계부터 그 텍스처(모델은 빌드할 때 만든다).
 * @param model 직접 만든 모델 이름. 적으면 [texture] 대신 이것.
 */
data class UpgradeStep(
    val chance: Double = 100.0,
    val fail: FailResult = FailResult.KEEP,
    /**
     * 실패했을 때 [fail] 이 실제로 일어날 확률(%) — 한 번 더 굴려 빗나가면 그대로다. 인첸트 강화 스크롤의 "실패 시 하락 확률"과 같은
     * 방식(사용자 요청 2026-10-01 — 실패 위험이 너무 크다). [FailResult.KEEP] 이면 뜻이 없다. 안 적은 단계는 100(예전처럼 실패하면 늘).
     */
    val failChance: Double = 100.0,
    val stats: Map<Stat, Double> = emptyMap(),
    val percents: Map<Stat, Double> = emptyMap(),
    val tier: Tier? = null,
    val customModelData: Int = 0,
    val texture: String = "",
    val model: String = "",
    /** 이 단계에서 배낭에 더하는 칸(사용자 요청 2026-09-30). 배낭이 아니면 뜻이 없다. 앞 단계들 것과 쌓인다([UpgradeTable.backpackAt]). */
    val backpack: Int = 0,
    /** 이 단계부터 드랍 자동 수납([CustomItem.autoPickup]). */
    val autoPickup: Boolean = false,
) {
    /** 실패하면 무언가 잃을 수 있는가 — 결과가 그대로이거나 그 확률이 0 이면 아니다. */
    val risky: Boolean get() = fail != FailResult.KEEP && failChance > 0.0

    /** "한 단계 하락" · "30% 확률로 한 단계 하락" · "그대로" — 로어와 편집 화면이 같이 쓴다. */
    val failText: String get() = when {
        !risky -> FailResult.KEEP.display
        failChance >= 100.0 -> fail.display
        else -> kr.inmc.core.util.Numbers.chance(failChance) + "% 확률로 " + fail.display
    }

    fun save(section: ConfigurationSection) {
        section.set("chance", chance)
        if (fail != FailResult.KEEP) section.set("fail", fail.id)
        // 100 은 적지 않는다 — 안 적은 것이 100 이라 옛 정의의 지문(자동 갱신)이 그대로다.
        if (failChance < 100.0) section.set("fail-chance", failChance)
        if (stats.isNotEmpty()) {
            val node = section.createSection("stats")
            for ((stat, value) in stats) node.set(stat.id, value)
        }
        if (percents.isNotEmpty()) {
            val node = section.createSection("percents")
            for ((stat, value) in percents) node.set(stat.id, value)
        }
        tier?.let { section.set("tier", it.id) }
        if (customModelData > 0) section.set("custom-model-data", customModelData)
        if (texture.isNotBlank()) section.set("texture", texture)
        if (model.isNotBlank()) section.set("model", model)
        if (backpack != 0) section.set("backpack", backpack)
        if (autoPickup) section.set("auto-pickup", true)
    }

    companion object {
        fun load(section: ConfigurationSection): UpgradeStep = UpgradeStep(
            chance = section.getDouble("chance", 100.0).coerceIn(0.0, 100.0),
            fail = FailResult.of(section.getString("fail")),
            failChance = section.getDouble("fail-chance", 100.0).coerceIn(0.0, 100.0),
            stats = readStats(section, "stats"),
            percents = readStats(section, "percents"),
            tier = section.getString("tier")?.let { raw -> Tier.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) } },
            customModelData = section.getInt("custom-model-data", 0).coerceAtLeast(0),
            texture = section.getString("texture").orEmpty(),
            model = section.getString("model").orEmpty(),
            backpack = section.getInt("backpack", 0),
            autoPickup = section.getBoolean("auto-pickup", false),
        )
    }
}

private fun readStats(section: ConfigurationSection, key: String): Map<Stat, Double> =
    section.getConfigurationSection(key)?.let { node ->
        node.getKeys(false).mapNotNull { name -> Stat.of(name)?.let { it to node.getDouble(name) } }.filter { it.second != 0.0 }.toMap()
    }.orEmpty()

/**
 * 강화표 — 0강(기본)에서 [maxLevel]강까지. `steps[0]` 이 1강으로 가는 단계다.
 *
 * 공용(무기용·방어구용…)은 `upgrades.yml` 에, 한 아이템 전용은 그 아이템 정의 안에 있다([UpgradeSpec.own]).
 */
data class UpgradeTable(
    val id: String,
    val name: String = id,
    val mode: UpgradeMode = UpgradeMode.ADD,
    val steps: List<UpgradeStep> = emptyList(),
) {
    val maxLevel: Int get() = steps.size

    /** [level] 강으로 올라가는 단계. */
    fun step(level: Int): UpgradeStep? = steps.getOrNull(level - 1)

    /** [level] 강의 등급. 안 정했으면 null(아이템 기본 등급). */
    fun tierAt(level: Int): Tier? = (level.coerceAtMost(maxLevel) downTo 1).firstNotNullOfOrNull { step(it)?.tier }

    /**
     * [level] 강의 텍스처·모델을 정한 단계와 그 번호. 없으면 null(아이템 기본 모양).
     * 번호(custom-model-data)와 따로 찾는다 — 번호만 바꾼 단계가 앞 단계의 텍스처를 지우면 안 된다.
     */
    fun modelAt(level: Int): Pair<Int, UpgradeStep>? =
        (level.coerceAtMost(maxLevel) downTo 1).firstNotNullOfOrNull { at ->
            step(at)?.takeIf { it.texture.isNotBlank() || it.model.isNotBlank() }?.let { at to it }
        }

    /** [level] 강까지 배낭에 더해진 칸(단계마다 쌓인다). */
    fun backpackAt(level: Int): Int = (1..level.coerceAtMost(maxLevel)).sumOf { step(it)?.backpack ?: 0 }

    /** [level] 강에 드랍 자동 수납이 켜졌나 — 그 단계나 앞 단계 하나라도 켰으면. */
    fun autoPickupAt(level: Int): Boolean = (1..level.coerceAtMost(maxLevel)).any { step(it)?.autoPickup == true }

    /** [level] 강의 모델 번호. 안 정했으면 null. */
    fun customModelDataAt(level: Int): Int? =
        (level.coerceAtMost(maxLevel) downTo 1).firstNotNullOfOrNull { step(it)?.customModelData?.takeIf { cmd -> cmd > 0 } }

    /** 모양이 바뀌는 단계가 하나라도 있는가(팩이 단계별 모델을 만들어야 하는가). */
    val hasLevelModels: Boolean get() = steps.any { it.texture.isNotBlank() || it.model.isNotBlank() }

    /** [base](굴린 기준값)를 [level] 강의 능력치로. */
    fun apply(base: Map<Stat, Double>, level: Int): Map<Stat, Double> {
        val top = level.coerceIn(0, maxLevel)
        if (top == 0) return base
        return when (mode) {
            UpgradeMode.ABSOLUTE -> (top downTo 1).firstNotNullOfOrNull { step(it)?.stats?.takeIf { stats -> stats.isNotEmpty() } } ?: base
            UpgradeMode.ADD -> {
                val out = LinkedHashMap(base)
                val percents = HashMap<Stat, Double>()
                for (at in 1..top) {
                    val step = step(at) ?: continue
                    for ((stat, value) in step.stats) out[stat] = (out[stat] ?: 0.0) + value
                    for ((stat, value) in step.percents) percents[stat] = (percents[stat] ?: 0.0) + value
                }
                // 기본에 없는 능력치의 % 는 0 이다(MMOItems 도 같다).
                for ((stat, percent) in percents) base[stat]?.let { out[stat] = (out[stat] ?: 0.0) + it * percent / 100.0 }
                out
            }
        }
    }

    fun save(section: ConfigurationSection) {
        section.set("name", name)
        section.set("mode", mode.id)
        val node = section.createSection("steps")
        for ((index, step) in steps.withIndex()) step.save(node.createSection((index + 1).toString()))
    }

    companion object {
        const val MAX_STEPS = 36

        fun load(id: String, section: ConfigurationSection): UpgradeTable {
            val node = section.getConfigurationSection("steps")
            val steps = node?.getKeys(false)
                ?.mapNotNull { key -> key.toIntOrNull()?.let { it to key } }
                ?.sortedBy { it.first }
                ?.mapNotNull { (_, key) -> node.getConfigurationSection(key)?.let(UpgradeStep::load) }
                .orEmpty()
            return UpgradeTable(id, section.getString("name") ?: id, UpgradeMode.of(section.getString("mode")), steps.take(MAX_STEPS))
        }
    }
}

/** 진화할 때 원래 아이템에서 무엇을 넘기는가. */
enum class EvolveKeep(val id: String, val display: String, val description: String) {
    FRESH("fresh", "새로 · 보석 반환", "0강으로 새로 만들고 박힌 보석은 가방으로 돌려줍니다"),
    INHERIT("inherit", "보석·굴림·수식어 계승", "0강이 되지만 보석·굴린 능력치·수식어를 이어받습니다(대상에 맞는 것만)"),
    NOTHING("nothing", "아무것도 안 넘김", "완전히 새 아이템 — 박힌 보석은 사라집니다"),
    ;

    companion object {
        fun of(id: String?): EvolveKeep = entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: FRESH
    }
}

/**
 * 최대 강화 뒤 [into] 로 진화한다. 강화표가 없으면 0강에서도 된다.
 *
 * @param station 제작대에서 진화할 때 어느 제작대인지. 비우면 아무 제작대.
 * @param materials 제작대에서 진화할 때 드는 재료. 진화석으로는 진화석만 든다.
 */
data class Evolution(
    val into: String,
    val keep: EvolveKeep = EvolveKeep.FRESH,
    val byStone: Boolean = true,
    val byStation: Boolean = true,
    val station: String = "",
    val materials: List<Part> = emptyList(),
) {
    fun save(section: ConfigurationSection) {
        section.set("into", into)
        section.set("keep", keep.id)
        section.set("by-stone", byStone)
        section.set("by-station", byStation)
        if (station.isNotBlank()) section.set("station", station)
        saveParts(section, "materials", materials)
    }

    companion object {
        fun load(section: ConfigurationSection): Evolution? {
            val into = section.getString("into")?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
            return Evolution(
                into = into,
                keep = EvolveKeep.of(section.getString("keep")),
                byStone = section.getBoolean("by-stone", true),
                byStation = section.getBoolean("by-station", true),
                station = section.getString("station").orEmpty().lowercase(),
                materials = loadParts(section, "materials"),
            )
        }
    }
}

/**
 * 아이템의 강화 설정.
 *
 * @param template 공용 강화 방식 id(`upgrades.yml`). [own] 이 있으면 무시한다.
 * @param own 이 아이템만의 강화표.
 * @param stones 이 아이템에 쓸 수 있는 강화석 id. 비우면 규칙에 맞는 아무 강화석이나.
 */
data class UpgradeSpec(
    val template: String = "",
    val own: UpgradeTable? = null,
    val stones: List<String> = emptyList(),
    val evolution: Evolution? = null,
) {
    val isEmpty: Boolean get() = template.isBlank() && own == null && stones.isEmpty() && evolution == null

    /** 쓰는 강화표. 없으면 강화할 수 없다. */
    fun table(lookup: Lookup): UpgradeTable? = own ?: template.takeIf { it.isNotBlank() }?.let(lookup.upgrade)

    fun save(section: ConfigurationSection) {
        if (template.isNotBlank()) section.set("template", template)
        own?.save(section.createSection("own"))
        if (stones.isNotEmpty()) section.set("stones", stones)
        evolution?.save(section.createSection("evolution"))
    }

    companion object {
        fun load(section: ConfigurationSection?, itemId: String): UpgradeSpec {
            if (section == null) return UpgradeSpec()
            return UpgradeSpec(
                template = section.getString("template").orEmpty().lowercase(),
                own = section.getConfigurationSection("own")?.let { UpgradeTable.load(itemId, it) },
                stones = section.getStringList("stones").map { it.trim().lowercase() }.filter { it.isNotEmpty() },
                evolution = section.getConfigurationSection("evolution")?.let(Evolution::load),
            )
        }
    }
}

/**
 * 강화석(소모품의 힘). 아이템 위에 끌어다 놓으면 한 단계 강화를 시도한다.
 *
 * @param templates 쓸 수 있는 공용 강화 방식. 비우면 전부(전용 표를 가진 아이템 포함).
 * @param items 쓸 수 있는 아이템 id. 비우면 전부.
 * @param minLevel 지금 단계가 이 이상일 때만.
 * @param maxLevel 지금 단계가 이 이하일 때만. null 이면 끝까지 — 0 은 "0강에서만"이다(1강 강화권).
 * @param chance 0 보다 크면 단계의 성공 확률 **대신** 이 확률(MMOItems 처럼 강화석이 확률을 정할 때).
 * @param chances 대상 아이템의 등급별 확률 — 있으면 [chance] 보다 먼저다("일반 10% · 희귀 8% · …").
 * @param bonus 성공 확률에 더하는 %p.
 */
data class UpgradeStone(
    val templates: List<String> = emptyList(),
    val items: List<String> = emptyList(),
    val minLevel: Int = 0,
    val maxLevel: Int? = null,
    val bonus: Double = 0.0,
    val chance: Double = 0.0,
    val chances: Map<Tier, Double> = emptyMap(),
) {
    fun save(section: ConfigurationSection) {
        if (templates.isNotEmpty()) section.set("templates", templates)
        if (items.isNotEmpty()) section.set("items", items)
        if (minLevel > 0) section.set("min-level", minLevel)
        maxLevel?.let { section.set("max-level", it) }
        if (bonus != 0.0) section.set("bonus", bonus)
        if (chance > 0.0) section.set("chance", chance)
        if (chances.isNotEmpty()) {
            val node = section.createSection("chances")
            for ((tier, value) in chances) node.set(tier.id, value)
        }
    }

    companion object {
        fun load(section: ConfigurationSection): UpgradeStone = UpgradeStone(
            templates = section.getStringList("templates").map { it.trim().lowercase() }.filter { it.isNotEmpty() },
            items = section.getStringList("items").map { it.trim().lowercase() }.filter { it.isNotEmpty() },
            minLevel = section.getInt("min-level", 0).coerceAtLeast(0),
            maxLevel = if (section.contains("max-level")) section.getInt("max-level").coerceAtLeast(0) else null,
            bonus = section.getDouble("bonus", 0.0).coerceIn(-100.0, 100.0),
            chance = section.getDouble("chance", 0.0).coerceIn(0.0, 100.0),
            chances = section.getConfigurationSection("chances")?.let { node ->
                node.getKeys(false).mapNotNull { key -> Tier.entries.firstOrNull { it.id.equals(key, ignoreCase = true) }?.let { it to node.getDouble(key).coerceIn(0.0, 100.0) } }.toMap()
            }.orEmpty(),
        )
    }
}

/** 진화석. 비우면 진화할 수 있는 아무 아이템에나. */
data class EvolveStone(val items: List<String> = emptyList()) {
    fun save(section: ConfigurationSection) {
        section.set("items", items)
    }

    companion object {
        fun load(section: ConfigurationSection): EvolveStone =
            EvolveStone(section.getStringList("items").map { it.trim().lowercase() }.filter { it.isNotEmpty() })
    }
}

/** 강화·진화의 규칙. 서버 없이 도는 순수 판정이다. */
object Upgrades {

    /** 강화석을 이 아이템에 못 쓰는 이유(메시지 열쇠). 쓸 수 있으면 null. */
    fun refuse(stoneId: String, stone: UpgradeStone, item: CustomItem, level: Int): String? {
        val spec = item.upgrade
        if (spec.stones.isNotEmpty() && stoneId !in spec.stones) return "upgrade-wrong-stone"
        if (stone.items.isNotEmpty() && item.id !in stone.items) return "upgrade-wrong-stone"
        // 전용 표를 가진 아이템은 공용 방식이 없다 — 방식을 가린 강화석은 못 쓴다(아이템을 콕 집은 강화석은 된다).
        if (stone.templates.isNotEmpty() && (spec.own != null || spec.template !in stone.templates) && item.id !in stone.items) return "upgrade-wrong-stone"
        if (level < stone.minLevel || (stone.maxLevel != null && level > stone.maxLevel)) return "upgrade-wrong-level"
        return null
    }

    /** 성공 확률(%) — 강화석의 등급별 확률 → 강화석의 고정 확률 → 단계의 확률 순, 그 위에 보너스. */
    fun chance(step: UpgradeStep, stone: UpgradeStone, tier: Tier): Double =
        ((stone.chances[tier] ?: stone.chance.takeIf { it > 0.0 } ?: step.chance) + stone.bonus).coerceIn(0.0, 100.0)

    /**
     * 실패했을 때 실제로 일어나는 것 — [UpgradeStep.fail] 을 [UpgradeStep.failChance] 로 한 번 더 굴린다. 빗나가면 그대로.
     * [roll] 은 0 이상 100 미만(성공 굴림과 같은 방향 — 확률보다 작으면 맞음).
     */
    fun failResult(step: UpgradeStep, roll: Double): FailResult =
        if (step.fail == FailResult.KEEP || roll >= step.failChance) FailResult.KEEP else step.fail

    /** 실패 뒤의 단계. 파괴면 -1. */
    fun afterFail(fail: FailResult, level: Int): Int = when (fail) {
        FailResult.KEEP -> level
        FailResult.DOWN -> (level - 1).coerceAtLeast(0)
        FailResult.RESET -> 0
        FailResult.DESTROY -> -1
    }

    /** 지금 진화할 수 있는가(표가 있으면 최대 단계에서만). */
    fun canEvolve(item: CustomItem, level: Int, lookup: Lookup): Boolean {
        val evolution = item.upgrade.evolution ?: return false
        if (lookup.item(evolution.into) == null) return false
        val table = item.upgrade.table(lookup) ?: return true
        return level >= table.maxLevel
    }
}
