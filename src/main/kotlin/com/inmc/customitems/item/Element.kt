package com.inmc.customitems.item

import com.inmc.customitems.CustomItems
import kr.inmc.core.store.YamlFileStore
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration

/**
 * 속성(원소) 하나 — 관리자가 만든다(`elements.yml`, 사용자 결정 2026-10-13. 그 전에는 화염·냉기·번개·독 넷이 코드에 박혀 있었다).
 *
 * 속성마다 능력치 둘이 생긴다([Stat.elementDamage] · [Stat.elementResist]): `<id>-damage` 는 때릴 때 더하는 고정 피해,
 * `<id>-resist` 는 그 속성 피해를 이만큼(%) 줄인다 — **음수면 약점**(-100 이면 두 배로 맞는다, MMOItems 의 weakness).
 * 맞힐 때 효과([burn] · [potion] · [particle])는 피해가 들어갔을 때만 건다.
 *
 * @param color 이름 앞에 붙는 MiniMessage 색 — 로어의 능력치 줄은 늘 회색이고, 이 색은 관리 화면·고르는 화면에 쓴다.
 * @param icon 관리 화면의 아이콘.
 * @param burn 맞은 상대를 이만큼(틱) 태운다. 0 이면 안 태운다.
 * @param potion 맞은 상대에게 거는 물약 효과 id(`slowness`). 비우면 없음.
 * @param potionTicks 그 효과의 길이(틱).
 * @param potionLevel 그 효과의 세기(1 = I).
 * @param particle 맞은 자리에 뿌리는 입자 이름(`electric_spark`). 비우면 없음.
 */
data class ElementDef(
    val id: String,
    val name: String,
    val color: String = TierDef.DEFAULT_COLOR,
    val icon: Material = Material.NETHER_STAR,
    val burn: Int = 0,
    val potion: String = "",
    val potionTicks: Int = 0,
    val potionLevel: Int = 1,
    val particle: String = "",
) {

    fun save(section: ConfigurationSection) {
        section.set("name", name)
        section.set("color", color)
        section.set("icon", icon.name)
        if (burn > 0) section.set("burn-ticks", burn)
        if (potion.isNotBlank()) {
            section.set("potion", potion)
            section.set("potion-ticks", potionTicks)
            section.set("potion-level", potionLevel)
        }
        if (particle.isNotBlank()) section.set("particle", particle)
    }

    companion object {
        const val MAX_TICKS = 20 * 60
        const val MAX_LEVEL = 10

        fun load(id: String, section: ConfigurationSection): ElementDef = ElementDef(
            id = id,
            name = section.getString("name")?.takeIf { it.isNotBlank() } ?: id,
            color = TierDef.colorTag(section.getString("color")) ?: TierDef.DEFAULT_COLOR,
            icon = section.getString("icon")?.let { CustomItem.matchMaterial(it) } ?: Material.NETHER_STAR,
            burn = section.getInt("burn-ticks", 0).coerceIn(0, MAX_TICKS),
            potion = section.getString("potion")?.trim().orEmpty(),
            potionTicks = section.getInt("potion-ticks", 0).coerceIn(0, MAX_TICKS),
            potionLevel = section.getInt("potion-level", 1).coerceIn(1, MAX_LEVEL),
            particle = section.getString("particle")?.trim()?.lowercase().orEmpty(),
        )

        /** `elements.yml` 이 없을 때의 목록 — 2026-10-13 전에 코드에 박혀 있던 넷과 그 효과 그대로. */
        val DEFAULTS: List<ElementDef> = listOf(
            ElementDef("fire", "화염", "<red>", Material.BLAZE_POWDER, burn = 40),
            ElementDef("ice", "냉기", "<aqua>", Material.BLUE_ICE, potion = "slowness", potionTicks = 30, potionLevel = 2),
            ElementDef("lightning", "번개", "<yellow>", Material.LIGHTNING_ROD, particle = "electric_spark"),
            ElementDef("poison", "독", "<dark_green>", Material.SPIDER_EYE, potion = "poison", potionTicks = 40, potionLevel = 1),
        )

        /**
         * 속성 id 로 쓸 수 있는가 — 영어 소문자·숫자만(능력치 이름을 읽을 때 `_` 를 `-` 로 바꾸므로 밑줄이 든 id 는 다시 못 찾는다),
         * 그리고 능력치 이름(`<id>-damage`)이 이미 있는 능력치(`attack-damage` …)와 겹치면 안 된다.
         */
        fun isValidId(id: String): Boolean =
            Regex("^[a-z0-9]+$").matches(id) && Stat.builtin("$id-damage") == null && Stat.builtin("$id-resist") == null
    }
}

/**
 * 지금의 속성 목록 — [ElementRegistry] 가 읽고 고칠 때 바꾼다. 정적인 이유는 [Tiers] 와 같다(아이템 정의를 읽는 코드가 플러그인 없이 돈다).
 * 바뀌면 [Stat.entries] 의 속성 칸도 다시 짠다.
 */
object Elements {

    @Volatile
    private var list: List<ElementDef> = ElementDef.DEFAULTS

    fun all(): List<ElementDef> = list

    fun get(id: String): ElementDef? = list.firstOrNull { it.id == id }

    fun set(defs: List<ElementDef>) {
        list = defs.toList()
        Stat.rebuild()
    }
}

/**
 * `elements.yml` — 속성 목록. 파일에 적힌 순서가 화면·로어의 순서다. 파일이 없으면 [ElementDef.DEFAULTS].
 *
 * **목록에 없는 속성의 능력치는 읽지 않는다**([Stat.of]) — 그래서 아이템·세트·강화표보다 먼저 읽고, 지울 때는 화면이 아이템에서 그 능력치를
 * 같이 지운다(세트·강화표·수식어에 남은 것은 다음에 읽을 때 빠진다).
 */
class ElementRegistry(private val custom: CustomItems) : YamlFileStore(
    io = custom.io,
    path = listOf("elements.yml"),
    header = """
        속성(원소). /커스텀아이템 관리 → 속성 관리 에서 GUI 로 고치는 것을 권장합니다.
        속성마다 능력치 둘이 생깁니다: <id>-damage (때릴 때 더하는 고정 피해) · <id>-resist (그 속성 피해 -%, 음수면 약점).
        id 는 영어 소문자·숫자만 됩니다(attack · pve · pvp · projectile · undead · skill · crit 처럼 이미 있는 능력치와 겹치는 것은 안 됩니다).

        name          능력치 이름에 쓰입니다(화염 → 화염 피해 · 화염 저항)
        color         관리 화면의 색 — red · #FF5555 처럼
        icon          관리 화면 아이콘(재질 이름)
        burn-ticks    맞힌 상대를 태우는 시간(틱, 20 = 1초)
        potion        맞힌 상대에게 거는 물약 효과(slowness · poison …)  potion-ticks 길이(틱)  potion-level 세기(1 = I)
        particle      맞힌 자리에 뿌리는 입자(electric_spark · smoke …)
    """.trimIndent() + "\n",
    what = "속성",
) {

    fun all(): List<ElementDef> = Elements.all()

    fun get(id: String?): ElementDef? = id?.trim()?.lowercase()?.let(Elements::get)

    /** 있으면 그 자리에서 바꾸고, 없으면 맨 뒤에 더한다. */
    fun put(def: ElementDef) {
        val list = Elements.all().toMutableList()
        val at = list.indexOfFirst { it.id == def.id }
        if (at >= 0) list[at] = def else list += def
        publish(list)
    }

    fun remove(id: String): Boolean {
        val list = Elements.all().toMutableList()
        if (!list.removeIf { it.id == id.lowercase() }) return false
        publish(list)
        return true
    }

    private fun publish(list: List<ElementDef>) {
        Elements.set(list)
        markDirty()
        custom.items.onElementsChanged()
    }

    override fun read(config: YamlConfiguration) {
        val root = config.getConfigurationSection("elements")
        if (root == null) {
            Elements.set(ElementDef.DEFAULTS)
            return
        }
        val list = ArrayList<ElementDef>()
        for (key in root.getKeys(false)) {
            val id = key.trim().lowercase()
            if (!ElementDef.isValidId(id)) {
                custom.logger.warning("속성 '$key' 는 쓸 수 없는 id 입니다 - 건너뜁니다")
                continue
            }
            root.getConfigurationSection(key)?.let { list += ElementDef.load(id, it) }
        }
        Elements.set(list)
    }

    override fun write(config: YamlConfiguration) {
        val root = config.createSection("elements")
        for (def in Elements.all()) def.save(root.createSection(def.id))
    }
}
