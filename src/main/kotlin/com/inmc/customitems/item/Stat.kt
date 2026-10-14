package com.inmc.customitems.item

import org.bukkit.Material
import org.bukkit.attribute.Attribute

/**
 * 아이템이 가질 수 있는 능력치.
 *
 * **두 종류가 한 목록에 있고 그게 의도다.** 바닐라 속성으로 내려가는 것([attribute] 이 있는
 * 것)과 이 플러그인이 전투 이벤트에서 직접 계산하는 것이 섞여 있는데, 관리자 입장에서는
 * 둘 다 "이 칼의 능력치" 일 뿐이다. 어느 쪽이 바닐라인지는 구현 사정이지 사용자의 관심사가
 * 아니다 — 화면에서도 구분하지 않는다.
 *
 * 우리가 직접 계산하는 것들은 **아이템에 적힌 값을 전투 때 읽는다**. PDC 에 따로 찍지 않는
 * 이유는 관리자가 정의를 고쳤을 때 이미 나간 아이템도 같이 바뀌어야 하기 때문이다.
 */
class Stat private constructor(
    val id: String,
    private val label: String,
    val unit: String = "",
    /** 대응하는 바닐라 속성. null 이면 우리가 직접 계산한다. */
    val attributeKey: String? = null,
    /** 백분율로 다루는 값인지. 화면과 로어가 이걸 보고 표기를 정한다. */
    val percent: Boolean = false,
    val category: Category = Category.UTILITY,
    /** 속성 능력치면 그 속성의 id([ElementDef]). */
    val element: String? = null,
) {

    /** 보이는 이름. 속성 능력치는 지금 속성 이름에서 짓는다(화염 → 화염 피해 · 화염 저항). 지운 속성이면 id 그대로. */
    val display: String
        get() {
            val key = element ?: return label
            val def = Elements.get(key) ?: return id
            return def.name + if (percent) " 저항" else " 피해"
        }

    /** 지금 [entries] 의 몇 번째인가 — 화면·로어의 순서. 지운 속성은 맨 뒤. */
    val index: Int get() = indexes()[id] ?: Int.MAX_VALUE

    override fun equals(other: Any?): Boolean = other is Stat && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = id

    /** 화면이 능력치를 나눠 보여주는 묶음. */
    enum class Category(val display: String) {
        ATTACK("공격"), DEFENSE("방어"), ELEMENT("원소"), MOVEMENT("이동"), UTILITY("그 밖"),
    }

    val isVanilla: Boolean get() = attributeKey != null

    /** 바닐라 속성 객체. 없거나 이 서버 버전에 없으면 null. */
    fun attribute(): Attribute? {
        val key = attributeKey ?: return null
        return Registries.attribute(key)
    }

    /** 로어 한 줄. `+5 공격력` 처럼. */
    fun line(value: Double): String {
        val sign = if (value > 0) "+" else ""
        val shown = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
        return "<gray>" + sign + shown + unit + " " + display + "</gray>"
    }

    companion object {

        // 상수보다 먼저 — companion 의 초기화는 적힌 순서다.
        private val head = ArrayList<Stat>()
        private val tail = ArrayList<Stat>()
        private val elementStats = java.util.concurrent.ConcurrentHashMap<String, Stat>()

        /** 속성 id — 영어 소문자·숫자만(능력치 이름을 읽을 때 `_` 를 `-` 로 바꾸므로 밑줄을 받으면 그 속성을 다시 못 찾는다). */
        private val ELEMENT_ID = Regex("^[a-z0-9]+$")

        private fun stat(
            id: String,
            display: String,
            unit: String = "",
            attributeKey: String? = null,
            percent: Boolean = false,
            category: Category = Category.UTILITY,
            tail: Boolean = false,
        ): Stat = Stat(id, display, unit, attributeKey, percent, category).also { if (tail) this.tail += it else head += it }

        /** 그 속성의 고정 피해. 속성이 목록에 없어도 만든다 — 읽을 때 거르는 것은 [of] 다. */
        fun elementDamage(element: String): Stat =
            elementStats.getOrPut("$element-damage") { Stat("$element-damage", "", category = Category.ELEMENT, element = element) }

        /** 그 속성 피해 -%(음수면 약점). */
        fun elementResist(element: String): Stat =
            elementStats.getOrPut("$element-resist") { Stat("$element-resist", "", "%", percent = true, category = Category.ELEMENT, element = element) }

        // --- 바닐라 속성으로 내려가는 것 ---------------------------------------------------
        val ATTACK_DAMAGE = stat("attack-damage", "공격력", attributeKey = "attack_damage", category = Category.ATTACK)
        val ATTACK_SPEED = stat("attack-speed", "공격 속도", attributeKey = "attack_speed", category = Category.ATTACK)
        val ARMOR = stat("armor", "방어력", attributeKey = "armor", category = Category.DEFENSE)
        val ARMOR_TOUGHNESS = stat("armor-toughness", "방어 강도", attributeKey = "armor_toughness", category = Category.DEFENSE)
        val MAX_HEALTH = stat("max-health", "최대 체력", attributeKey = "max_health", category = Category.DEFENSE)
        val MOVEMENT_SPEED = stat("movement-speed", "이동 속도", attributeKey = "movement_speed", category = Category.MOVEMENT)
        val KNOCKBACK_RESISTANCE = stat("knockback-resistance", "밀림 저항", attributeKey = "knockback_resistance", category = Category.DEFENSE)
        val LUCK = stat("luck", "행운", attributeKey = "luck")
        val ATTACK_KNOCKBACK = stat("attack-knockback", "공격 밀림", attributeKey = "attack_knockback", category = Category.ATTACK)
        val SWEEPING_DAMAGE_RATIO = stat("sweeping-damage-ratio", "휩쓸기 피해 비율", attributeKey = "sweeping_damage_ratio", category = Category.ATTACK)
        val ENTITY_INTERACTION_RANGE = stat("entity-interaction-range", "공격 거리", attributeKey = "entity_interaction_range", category = Category.ATTACK)
        val MAX_ABSORPTION = stat("max-absorption", "최대 흡수 체력", attributeKey = "max_absorption", category = Category.DEFENSE)
        val EXPLOSION_KNOCKBACK_RESISTANCE = stat("explosion-knockback-resistance", "폭발 밀림 저항", attributeKey = "explosion_knockback_resistance", category = Category.DEFENSE)
        val BURNING_TIME = stat("burning-time", "불붙는 시간 배율", attributeKey = "burning_time", category = Category.DEFENSE)
        val FALL_DAMAGE_MULTIPLIER = stat("fall-damage-multiplier", "낙하 피해 배율", attributeKey = "fall_damage_multiplier", category = Category.DEFENSE)
        val SAFE_FALL_DISTANCE = stat("safe-fall-distance", "안전 낙하 거리", attributeKey = "safe_fall_distance", category = Category.MOVEMENT)
        val JUMP_STRENGTH = stat("jump-strength", "점프력", attributeKey = "jump_strength", category = Category.MOVEMENT)
        val STEP_HEIGHT = stat("step-height", "오를 수 있는 높이", attributeKey = "step_height", category = Category.MOVEMENT)
        val GRAVITY = stat("gravity", "중력", attributeKey = "gravity", category = Category.MOVEMENT)
        val SNEAKING_SPEED = stat("sneaking-speed", "웅크리기 속도", attributeKey = "sneaking_speed", category = Category.MOVEMENT)
        val MOVEMENT_EFFICIENCY = stat("movement-efficiency", "험지 이동 효율", attributeKey = "movement_efficiency", category = Category.MOVEMENT)
        val WATER_MOVEMENT_EFFICIENCY = stat("water-movement-efficiency", "물속 이동 효율", attributeKey = "water_movement_efficiency", category = Category.MOVEMENT)
        val OXYGEN_BONUS = stat("oxygen-bonus", "산소", attributeKey = "oxygen_bonus", category = Category.MOVEMENT)
        val SCALE = stat("scale", "크기", attributeKey = "scale", category = Category.MOVEMENT)
        val BLOCK_BREAK_SPEED = stat("block-break-speed", "블록 파괴 속도", attributeKey = "block_break_speed")
        val MINING_EFFICIENCY = stat("mining-efficiency", "채굴 효율", attributeKey = "mining_efficiency")
        val SUBMERGED_MINING_SPEED = stat("submerged-mining-speed", "물속 채굴 속도", attributeKey = "submerged_mining_speed")
        val BLOCK_INTERACTION_RANGE = stat("block-interaction-range", "블록 닿는 거리", attributeKey = "block_interaction_range")

        // --- 이 플러그인이 직접 계산하는 것 -------------------------------------------------
        /** 치명타가 터질 확률(%). */
        val CRIT_CHANCE = stat("crit-chance", "치명타 확률", "%", percent = true, category = Category.ATTACK)

        /** 치명타일 때 피해 배수. 2.0 이면 두 배. */
        val CRIT_DAMAGE = stat("crit-damage", "치명타 배수", "배", category = Category.ATTACK)

        /** 치명타 피해 +%. 배수에 더한다(배수 2 에 +50% 면 2.5 배). MMOItems 의 critical-strike-power. */
        val CRIT_POWER = stat("crit-power", "치명타 피해", "%", percent = true, category = Category.ATTACK)

        /** 준 피해의 이만큼(%)을 체력으로 돌려받는다. */
        val LIFESTEAL = stat("lifesteal", "흡혈", "%", percent = true, category = Category.ATTACK)

        /** 모든 피해에 더하는 고정값. */
        val DAMAGE_BONUS = stat("damage-bonus", "추가 피해", category = Category.ATTACK)

        /** 몹에게 주는 피해 +%. */
        val PVE_DAMAGE = stat("pve-damage", "몹 피해", "%", percent = true, category = Category.ATTACK)

        /** 플레이어에게 주는 피해 +%. */
        val PVP_DAMAGE = stat("pvp-damage", "플레이어 피해", "%", percent = true, category = Category.ATTACK)

        /** 화살·삼지창 같은 발사체 피해 +%. */
        val PROJECTILE_DAMAGE = stat("projectile-damage", "발사체 피해", "%", percent = true, category = Category.ATTACK)

        /** 언데드에게 주는 피해 +%. */
        val UNDEAD_DAMAGE = stat("undead-damage", "언데드 피해", "%", percent = true, category = Category.ATTACK)

        /** 기능의 피해 효과 +%. */
        val SKILL_DAMAGE = stat("skill-damage", "기능 피해", "%", percent = true, category = Category.ATTACK)

        /** 받는 피해를 이만큼(%) 줄인다. 방어구에 붙인다. */
        val DAMAGE_REDUCTION = stat("damage-reduction", "피해 감소", "%", percent = true, category = Category.DEFENSE)

        /** 몹에게 받는 피해 -%. */
        val PVE_DEFENSE = stat("pve-defense", "몹 피해 감소", "%", percent = true, category = Category.DEFENSE)

        /** 플레이어에게 받는 피해 -%. */
        val PVP_DEFENSE = stat("pvp-defense", "플레이어 피해 감소", "%", percent = true, category = Category.DEFENSE)

        /** 발사체에게 받는 피해 -%. */
        val PROJECTILE_DEFENSE = stat("projectile-defense", "발사체 피해 감소", "%", percent = true, category = Category.DEFENSE)

        /** 떨어져 받는 피해 -%. */
        val FALL_DEFENSE = stat("fall-defense", "낙하 피해 감소", "%", percent = true, category = Category.DEFENSE)

        /** 불·용암에게 받는 피해 -%. */
        val FIRE_DEFENSE = stat("fire-defense", "화염 피해 감소", "%", percent = true, category = Category.DEFENSE)

        /** 맞은 상대에게 이만큼 되돌려준다. */
        val THORNS = stat("thorns", "가시", category = Category.DEFENSE)

        /** 공격을 통째로 피할 확률(%). */
        val DODGE_CHANCE = stat("dodge-chance", "회피", "%", percent = true, category = Category.DEFENSE)

        /** 방어 점수. 받는 피해에 100/(100+점수) 를 곱한다 — 100 이면 절반. MMOItems(MythicLib) 의 defense. */
        val DEFENSE_POINTS = stat("defense", "방어 점수", category = Category.DEFENSE)

        /** 막을 확률(%). 막으면 [BLOCK_POWER] 만큼 줄인다. */
        val BLOCK_CHANCE = stat("block-chance", "막기 확률", "%", percent = true, category = Category.DEFENSE)

        /** 막았을 때 줄이는 피해(%). 없으면 50%. */
        val BLOCK_POWER = stat("block-power", "막기 위력", "%", percent = true, category = Category.DEFENSE)

        /** 초당 회복하는 체력. */
        val HEALTH_REGEN = stat("health-regen", "초당 체력 회복", category = Category.DEFENSE)

        // --- 원소: 기본 넷의 상수. 능력치 자체는 속성 목록(elements.yml)이 만든다 — 관리자가 더한 것도 같은 모양이다 ---
        val FIRE_DAMAGE = elementDamage("fire")
        val ICE_DAMAGE = elementDamage("ice")
        val LIGHTNING_DAMAGE = elementDamage("lightning")
        val POISON_DAMAGE = elementDamage("poison")
        val FIRE_RESIST = elementResist("fire")
        val ICE_RESIST = elementResist("ice")
        val LIGHTNING_RESIST = elementResist("lightning")
        val POISON_RESIST = elementResist("poison")

        // --- 그 밖 ---------------------------------------------------------------------
        /** 얻는 경험치 +%. */
        val EXP_BONUS = stat("exp-bonus", "경험치 획득", "%", percent = true, tail = true)

        /** 기능의 재사용 대기 -%. 합산 최대 80%. */
        val COOLDOWN_REDUCTION = stat("cooldown-reduction", "재사용 대기 감소", "%", percent = true, tail = true)


        @Volatile
        private var cached: List<Stat>? = null

        @Volatile
        private var cachedIndexes: Map<String, Int>? = null

        /**
         * 지금의 능력치 전부, 화면·로어 순서로 — 바닐라·우리 계산 → 속성(피해 전부 다음 저항 전부) → 경험치·재사용 대기.
         * 속성 칸은 지금 속성 목록이 정한다(지운 속성은 빠진다).
         */
        val entries: List<Stat>
            get() = cached ?: (head + Elements.all().map { elementDamage(it.id) } + Elements.all().map { elementResist(it.id) } + tail)
                .also { cached = it }

        private fun indexes(): Map<String, Int> =
            cachedIndexes ?: entries.withIndex().associate { (i, stat) -> stat.id to i }.also { cachedIndexes = it }

        /** 속성 목록이 바뀌었다([Elements.set]). */
        internal fun rebuild() {
            cached = null
            cachedIndexes = null
        }

        /** 속성이 아닌 능력치. 속성 id 가 이것들과 겹치지 않게 본다([ElementDef.isValidId]). */
        fun builtin(id: String): Stat? = head.firstOrNull { it.id == id } ?: tail.firstOrNull { it.id == id }

        /**
         * 적힌 이름의 능력치. `<속성>-damage` · `<속성>-resist` 는 **그 속성이 지금 목록에 있을 때만** — 없는 속성까지 받으면 MMOItems 의
         * `magic-damage` · `physical-damage` 같은 남의 능력치 이름이 보이지 않는 속성 값으로 조용히 쌓인다(테스트 ItemSetTest).
         * 그래서 속성 목록은 아이템보다 먼저 읽는다(CustomItemsPlugin.reload).
         */
        fun of(id: String?): Stat? {
            val text = id?.trim()?.lowercase()?.replace('_', '-') ?: return null
            builtin(text)?.let { return it }
            val damage = text.endsWith("-damage")
            if (!damage && !text.endsWith("-resist")) return null
            val element = text.substringBeforeLast('-')
            if (!ELEMENT_ID.matches(element) || Elements.get(element) == null) return null
            return if (damage) elementDamage(element) else elementResist(element)
        }

        /**
         * 이 재질에 어울리는 능력치들. 편집 화면이 **먼저 보여줄** 순서다.
         *
         * 전부 보여줘도 되지만, 장화에 공격 속도를 권하는 화면은 관리자를 헷갈리게 한다.
         * 막지는 않는다 — 특이한 조합을 만들고 싶을 수 있고, 그건 관리자의 자유다.
         */
        fun suggestedFor(material: Material): List<Stat> {
            val name = material.name
            val armor = name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") ||
                name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
            return when {
                armor -> entries.filter { it.category == Category.DEFENSE || it.category == Category.MOVEMENT }
                name.endsWith("_SWORD") || name.endsWith("_AXE") || name == "TRIDENT" || name == "MACE" ->
                    entries.filter { it.category == Category.ATTACK || it.category == Category.ELEMENT }
                name == "BOW" || name == "CROSSBOW" ->
                    listOf(PROJECTILE_DAMAGE, CRIT_CHANCE, CRIT_DAMAGE, PVE_DAMAGE, PVP_DAMAGE, LIFESTEAL) + entries.filter { it.category == Category.ELEMENT }
                else -> entries
            }
        }
    }
}

/**
 * 아이템의 쓰임새.
 *
 * **동작을 바꾸지는 않는다.** 화면에서 걸러 보고, 로어에 적히고, 다른 플러그인이
 * `inmc:아이디` 를 받았을 때 무엇인지 짐작하게 하는 분류다. 동작은 능력치와 기능이 정한다 —
 * 타입마다 코드를 다르게 두면 타입을 하나 늘릴 때마다 코드를 고쳐야 한다.
 */
enum class ItemType(val id: String, val display: String, val icon: Material, val symbol: String) {
    WEAPON("weapon", "무기", Material.DIAMOND_SWORD, "⚔"),
    ARMOR("armor", "방어구", Material.DIAMOND_CHESTPLATE, "⛨"),
    TOOL("tool", "도구", Material.DIAMOND_PICKAXE, "⛏"),
    CONSUMABLE("consumable", "소모품", Material.POTION, "✚"),
    ACCESSORY("accessory", "장신구", Material.AMETHYST_SHARD, "❖"),
    TALISMAN("talisman", "부적", Material.PAPER, "✧"),
    RELIC("relic", "유물", Material.HEART_OF_THE_SEA, "❂"),
    /** 들고 다니는 창고([CustomItem.backpack], 사용자 결정 2026-09-30). 장착 칸의 배낭 줄에 끼우고, 능력치·기능은 장신구처럼 난다. */
    BACKPACK("backpack", "배낭", Material.BUNDLE, "▣"),
    MATERIAL("material", "재료", Material.COPPER_INGOT, "◈"),
    GEM("gem", "보석", Material.EMERALD, "◆"),
    /**
     * 놓으면 커스텀 블록이 되는 것([CustomItem.block], 사용자 결정 2026-10-01 — 기타에서 떼어 냄). 동작은 기타와 같다 —
     * 놓고 캐는 것은 블록 설정이 정하지 종류가 정하지 않는다.
     */
    BLOCK("block", "블록", Material.GRASS_BLOCK, "▦"),
    MISC("misc", "기타", Material.FLOWER_BANNER_PATTERN, "✦"),
    ;

    /**
     * 가방 어디에 있어도 효과가 나는 종류. 부적은 여러 개가 겹쳐 붙고(아이템마다 "같은 것 중복 안 함"을 켤 수 있다),
     * 유물은 종류가 달라도 **한 번에 하나만** 붙는다.
     */
    val carried: Boolean get() = this == TALISMAN || this == RELIC

    /** 장착 칸(`/장비`)에 끼우는 종류 — 장신구·배낭·부적·유물. 장착 칸 밖(가방·손)에서의 효과를 서버·아이템이 정한다. */
    val slotted: Boolean get() = this == ACCESSORY || this == BACKPACK || carried

    /** 로어 맨 위 — 흐린 글씨로 종류만. 등급은 이름 색과 맨 아래 배지([Tier.badge])가 말한다. */
    fun header(): String = "<dark_gray>" + symbol + "</dark_gray> <gray>" + display + "</gray>"

    companion object {
        fun of(id: String?): ItemType =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: MISC

        /**
         * 재질에서 짐작한다. 손에 든 것을 등록할 때의 **초깃값**이다.
         *
         * **이름만 본다.** `Material.isEdible` 은 레지스트리를 타서 서버 없이는 못 부르고,
         * 서버 버전마다 답이 달라질 수 있다. 어차피 한 번 클릭으로 고치는 값이라 정확할
         * 이유가 없고, 대신 **어디서 돌려도 같은 답**이 나오는 쪽이 낫다.
         */
        fun guess(material: Material): ItemType {
            val name = material.name
            return when {
                name.endsWith("_SWORD") || name.endsWith("_AXE") || name == "BOW" ||
                    name == "CROSSBOW" || name == "TRIDENT" || name == "MACE" -> WEAPON

                name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") ||
                    name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS") ||
                    name == "ELYTRA" || name == "SHIELD" || name == "TURTLE_HELMET" -> ARMOR

                name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE") ||
                    name == "FISHING_ROD" || name == "SHEARS" || name == "FLINT_AND_STEEL" ||
                    name == "BRUSH" || name == "SPYGLASS" -> TOOL

                name.endsWith("POTION") || name.endsWith("_APPLE") || name.endsWith("STEW") ||
                    name.endsWith("_BERRIES") || name.startsWith("COOKED_") ||
                    name == "BREAD" || name == "COOKIE" || name == "CAKE" ||
                    name == "MILK_BUCKET" || name == "HONEY_BOTTLE" ||
                    name == "GOLDEN_CARROT" || name == "CHORUS_FRUIT" ||
                    name == "DRIED_KELP" || name == "ENCHANTED_GOLDEN_APPLE" -> CONSUMABLE

                name.endsWith("_INGOT") || name.endsWith("_NUGGET") || name.endsWith("_SCRAP") ||
                    name.endsWith("_SHARD") || name.endsWith("_GEM") || name == "DIAMOND" ||
                    name == "EMERALD" || name == "LEATHER" || name == "STICK" -> MATERIAL

                else -> MISC
            }
        }
    }
}
