package com.inmc.customitems.item

import com.inmc.customitems.CustomItems
import kr.inmc.core.store.YamlFileStore
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration

/**
 * 희귀도 — 아이템에 적히는 것은 **id 하나**다.
 *
 * 이름·색·순서는 관리자가 정하는 서버 전체의 눈금([TierDef], `tiers.yml`)이다. 사용자 결정 2026-10-13: 기본은 일반~신화 여섯 개,
 * 관리자가 더하고 고치고 지운다(그 전에는 코드 목록이었다 — MMOItems 의 E~유일 열 개를 옮기면서 바꿨다). 그래서 이 손잡이는 이름·색을
 * **그때그때 지금 목록에서** 찾는다 — 관리자가 색을 고치면 이미 읽은 정의에도 곧바로 보이고, 지운 등급의 id 는 아이템에 그대로 남아
 * (배지 없이 그려진다) 같은 id 로 다시 만들면 이어진다.
 *
 * [NONE] 은 목록 밖의 "등급 없음" 이다 — 배지를 그리지 않는다(ItemsAdder 블록처럼 등급이 없던 것).
 */
@JvmInline
value class Tier(val id: String) {

    /** 지금 목록의 정의. 없음·지운 등급이면 null. */
    val def: TierDef? get() = Tiers.get(id)

    val display: String get() = def?.name ?: if (this == NONE) "없음" else id

    /** 이름 앞에 붙는 MiniMessage 색(`<gold>` · `<#FFAA00>`). */
    val color: String get() = def?.color ?: TierDef.DEFAULT_COLOR

    /** 목록의 순서(0 이 가장 아래). 없음·지운 등급은 -1 — 유물 고르기에서 가장 아래다. */
    val rank: Int get() = Tiers.rank(id)

    /** 로어 맨 아래의 등급 배지. 없음·지운 등급은 null — 그 줄을 안 그린다. */
    fun badge(): String? = def?.badge()

    companion object {
        val NONE = Tier("none")

        val COMMON = Tier("common")
        val UNCOMMON = Tier("uncommon")
        val RARE = Tier("rare")
        val EPIC = Tier("epic")
        val LEGENDARY = Tier("legendary")
        val MYTHIC = Tier("mythic")

        /** `tiers.yml` 이 없을 때의 목록 — 2026-10-13 전의 코드 목록 그대로(색·배지가 안 바뀐다). */
        val DEFAULTS: List<TierDef> = listOf(
            TierDef("common", "일반", "<white>", "#E6E6E6", "#9E9E9E"),
            TierDef("uncommon", "고급", "<green>", "#8CF58C", "#2FA84F"),
            TierDef("rare", "희귀", "<aqua>", "#7FDBFF", "#2F7DE1"),
            TierDef("epic", "영웅", "<light_purple>", "#E79CFF", "#9B3FE0"),
            TierDef("legendary", "전설", "<gold>", "#FFE36E", "#FF9A1F"),
            TierDef("mythic", "신화", "<red>", "#FF7A7A", "#C2185B"),
        )

        /** 지금 목록의 등급들, 아래부터. 없음은 빠진다. */
        val entries: List<Tier> get() = Tiers.all().map { Tier(it.id) }

        /** 고르는 화면의 보기 — 없음 + 목록. */
        val choices: List<Tier> get() = listOf(NONE) + entries

        /** 안 적었으면 [default]. 목록에 없는 id 도 그대로 쥔다(지운 등급 — 위 설명). */
        fun of(id: String?): Tier {
            val key = id?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return default()
            return Tier(key)
        }

        /** 새 아이템의 등급 — 목록의 첫 것(가장 아래). 목록이 비었으면 없음. */
        fun default(): Tier = Tiers.all().firstOrNull()?.let { Tier(it.id) } ?: NONE
    }
}

/**
 * 등급 하나의 정의.
 *
 * @param color 이름 앞에 붙는 MiniMessage 색 — `<gold>` 같은 이름이나 `<#FFAA00>`.
 * @param from 배지 그라데이션의 시작 색(`#E6E6E6` 또는 색 이름).
 * @param to 끝 색.
 */
data class TierDef(val id: String, val name: String, val color: String = DEFAULT_COLOR, val from: String = "#FFFFFF", val to: String = "#AAAAAA") {

    /** 로어 맨 아래의 등급 배지. 두 색을 잇는 그라데이션이고, 양옆 줄은 흐리게. */
    fun badge(): String =
        "<dark_gray>▬▬▬▬</dark_gray> <gradient:" + from + ":" + to + "><bold>✦ " + name + " ✦</bold></gradient> <dark_gray>▬▬▬▬</dark_gray>"

    fun save(section: ConfigurationSection) {
        section.set("name", name)
        section.set("color", color)
        section.set("badge-from", from)
        section.set("badge-to", to)
    }

    companion object {
        const val DEFAULT_COLOR = "<white>"

        private val HEX = Regex("^#[0-9A-Fa-f]{6}$")

        fun load(id: String, section: ConfigurationSection): TierDef = TierDef(
            id = id,
            name = section.getString("name")?.takeIf { it.isNotBlank() } ?: id,
            color = colorTag(section.getString("color")) ?: DEFAULT_COLOR,
            from = gradientColor(section.getString("badge-from")) ?: "#FFFFFF",
            to = gradientColor(section.getString("badge-to")) ?: "#AAAAAA",
        )

        /**
         * 적은 색을 이름 앞에 붙는 태그로 — `gold` · `<gold>` · `#FFAA00` · `<#FFAA00>` 를 다 받는다. 모르는 것은 null.
         * 색 이름은 Adventure 의 이름표(`NamedTextColor`)를 본다 — 서버 없이도 돈다.
         */
        fun colorTag(raw: String?): String? {
            val text = raw?.trim()?.removePrefix("<")?.removeSuffix(">")?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
            if (HEX.matches(text)) return "<" + text.uppercase() + ">"
            return if (net.kyori.adventure.text.format.NamedTextColor.NAMES.value(text) != null) "<$text>" else null
        }

        /** 그라데이션 끝 색 — `#FFAA00` 이나 색 이름. 모르는 것은 null. */
        fun gradientColor(raw: String?): String? = colorTag(raw)?.removePrefix("<")?.removeSuffix(">")
    }
}

/**
 * 지금의 등급 목록 — [TierRegistry] 가 읽고 고칠 때 바꾼다.
 *
 * **정적이다.** 아이템 정의·강화표를 읽는 코드([CustomItem.load] · [Upgrades])가 플러그인 없이도 돌아야 한다(테스트, 순수 계산).
 * 처음 값은 [Tier.DEFAULTS] 라 테스트는 아무것도 안 해도 예전 여섯 등급을 본다.
 */
object Tiers {

    @Volatile
    private var list: List<TierDef> = Tier.DEFAULTS

    @Volatile
    private var index: Map<String, Int> = indexOf(Tier.DEFAULTS)

    fun all(): List<TierDef> = list

    fun get(id: String): TierDef? = index[id]?.let { list[it] }

    fun rank(id: String): Int = index[id] ?: -1

    fun set(defs: List<TierDef>) {
        index = indexOf(defs)
        list = defs.toList()
    }

    private fun indexOf(defs: List<TierDef>): Map<String, Int> = defs.withIndex().associate { (i, def) -> def.id to i }
}

/**
 * `tiers.yml` — 등급 목록. 파일에 적힌 순서가 등급의 순서다(위가 가장 아래 등급). 파일이 없으면 [Tier.DEFAULTS].
 *
 * 고칠 때마다 아이템의 지문을 다시 뜬다 — 이름 색·배지가 로어에 그려지기 때문([ItemRegistry.onTiersChanged]).
 */
class TierRegistry(private val custom: CustomItems) : YamlFileStore(
    io = custom.io,
    path = listOf("tiers.yml"),
    header = """
        아이템 등급. /커스텀아이템 관리 → 등급 관리 에서 GUI 로 고치는 것을 권장합니다.
        위에서 아래로 낮은 등급 → 높은 등급 순서입니다(유물은 높은 등급 하나만 효과가 납니다).
        아이템 쪽은 items.yml 의 tier 에 이 id 를 적습니다. none 은 "등급 없음"(배지 안 그림)이라 여기 적지 않습니다.

        name        배지와 고르는 화면에 보이는 이름
        color       아이템 이름 앞의 색 — gold · #FFAA00 처럼
        badge-from  로어 맨 아래 배지 그라데이션의 시작 색
        badge-to    끝 색
    """.trimIndent() + "\n",
    what = "등급",
) {

    fun all(): List<TierDef> = Tiers.all()

    fun get(id: String?): TierDef? = id?.trim()?.lowercase()?.let(Tiers::get)

    /** 있으면 그 자리에서 바꾸고, 없으면 맨 위(가장 높은 등급)에 더한다. */
    fun put(def: TierDef) {
        val list = Tiers.all().toMutableList()
        val at = list.indexOfFirst { it.id == def.id }
        if (at >= 0) list[at] = def else list += def
        publish(list)
    }

    fun remove(id: String): Boolean {
        val list = Tiers.all().toMutableList()
        if (!list.removeIf { it.id == id.lowercase() }) return false
        publish(list)
        return true
    }

    /** [id] 를 [index] 번째 자리(0 = 가장 아래)로 옮긴다 — 나머지는 한 칸씩 밀린다. 순서 정하기 화면(집고 → 놓기)이 부른다. */
    fun moveTo(id: String, index: Int) {
        val list = Tiers.all().toMutableList()
        val from = list.indexOfFirst { it.id == id }
        if (from < 0) return
        val to = index.coerceIn(0, list.lastIndex)
        if (to == from) return
        list.add(to, list.removeAt(from))
        publish(list)
    }

    private fun publish(list: List<TierDef>) {
        Tiers.set(list)
        markDirty()
        custom.items.onTiersChanged()
    }

    override fun read(config: YamlConfiguration) {
        val root = config.getConfigurationSection("tiers")
        if (root == null) {
            Tiers.set(Tier.DEFAULTS)
            return
        }
        val list = ArrayList<TierDef>()
        for (key in root.getKeys(false)) {
            val id = key.trim().lowercase()
            if (id == Tier.NONE.id || !kr.inmc.core.store.DefinitionKey.isValid(id)) {
                custom.logger.warning("등급 '$key' 는 쓸 수 없는 id 입니다 - 건너뜁니다")
                continue
            }
            root.getConfigurationSection(key)?.let { list += TierDef.load(id, it) }
        }
        Tiers.set(list)
    }

    override fun write(config: YamlConfiguration) {
        val root = config.createSection("tiers")
        for (def in Tiers.all()) def.save(root.createSection(def.id))
    }
}
