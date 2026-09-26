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
enum class Stat(
    val id: String,
    val display: String,
    val unit: String = "",
    /** 대응하는 바닐라 속성. null 이면 우리가 직접 계산한다. */
    val attributeKey: String? = null,
    /** 백분율로 다루는 값인지. 화면과 로어가 이걸 보고 표기를 정한다. */
    val percent: Boolean = false,
    val category: Category = Category.UTILITY,
) {

    // --- 바닐라 속성으로 내려가는 것 ---------------------------------------------------
    ATTACK_DAMAGE("attack-damage", "공격력", attributeKey = "attack_damage", category = Category.ATTACK),
    ATTACK_SPEED("attack-speed", "공격 속도", attributeKey = "attack_speed", category = Category.ATTACK),
    ARMOR("armor", "방어력", attributeKey = "armor", category = Category.DEFENSE),
    ARMOR_TOUGHNESS("armor-toughness", "방어 강도", attributeKey = "armor_toughness", category = Category.DEFENSE),
    MAX_HEALTH("max-health", "최대 체력", attributeKey = "max_health", category = Category.DEFENSE),
    MOVEMENT_SPEED("movement-speed", "이동 속도", attributeKey = "movement_speed", category = Category.MOVEMENT),
    KNOCKBACK_RESISTANCE("knockback-resistance", "밀림 저항", attributeKey = "knockback_resistance", category = Category.DEFENSE),
    LUCK("luck", "행운", attributeKey = "luck"),
    ATTACK_KNOCKBACK("attack-knockback", "공격 밀림", attributeKey = "attack_knockback", category = Category.ATTACK),
    SWEEPING_DAMAGE_RATIO("sweeping-damage-ratio", "휩쓸기 피해 비율", attributeKey = "sweeping_damage_ratio", category = Category.ATTACK),
    ENTITY_INTERACTION_RANGE("entity-interaction-range", "공격 거리", attributeKey = "entity_interaction_range", category = Category.ATTACK),
    MAX_ABSORPTION("max-absorption", "최대 흡수 체력", attributeKey = "max_absorption", category = Category.DEFENSE),
    EXPLOSION_KNOCKBACK_RESISTANCE("explosion-knockback-resistance", "폭발 밀림 저항", attributeKey = "explosion_knockback_resistance", category = Category.DEFENSE),
    BURNING_TIME("burning-time", "불붙는 시간 배율", attributeKey = "burning_time", category = Category.DEFENSE),
    FALL_DAMAGE_MULTIPLIER("fall-damage-multiplier", "낙하 피해 배율", attributeKey = "fall_damage_multiplier", category = Category.DEFENSE),
    SAFE_FALL_DISTANCE("safe-fall-distance", "안전 낙하 거리", attributeKey = "safe_fall_distance", category = Category.MOVEMENT),
    JUMP_STRENGTH("jump-strength", "점프력", attributeKey = "jump_strength", category = Category.MOVEMENT),
    STEP_HEIGHT("step-height", "오를 수 있는 높이", attributeKey = "step_height", category = Category.MOVEMENT),
    GRAVITY("gravity", "중력", attributeKey = "gravity", category = Category.MOVEMENT),
    SNEAKING_SPEED("sneaking-speed", "웅크리기 속도", attributeKey = "sneaking_speed", category = Category.MOVEMENT),
    MOVEMENT_EFFICIENCY("movement-efficiency", "험지 이동 효율", attributeKey = "movement_efficiency", category = Category.MOVEMENT),
    WATER_MOVEMENT_EFFICIENCY("water-movement-efficiency", "물속 이동 효율", attributeKey = "water_movement_efficiency", category = Category.MOVEMENT),
    OXYGEN_BONUS("oxygen-bonus", "산소", attributeKey = "oxygen_bonus", category = Category.MOVEMENT),
    SCALE("scale", "크기", attributeKey = "scale", category = Category.MOVEMENT),
    BLOCK_BREAK_SPEED("block-break-speed", "블록 파괴 속도", attributeKey = "block_break_speed"),
    MINING_EFFICIENCY("mining-efficiency", "채굴 효율", attributeKey = "mining_efficiency"),
    SUBMERGED_MINING_SPEED("submerged-mining-speed", "물속 채굴 속도", attributeKey = "submerged_mining_speed"),
    BLOCK_INTERACTION_RANGE("block-interaction-range", "블록 닿는 거리", attributeKey = "block_interaction_range"),

    // --- 이 플러그인이 직접 계산하는 것 -------------------------------------------------
    /** 치명타가 터질 확률(%). */
    CRIT_CHANCE("crit-chance", "치명타 확률", "%", percent = true, category = Category.ATTACK),

    /** 치명타일 때 피해 배수. 2.0 이면 두 배. */
    CRIT_DAMAGE("crit-damage", "치명타 배수", "배", category = Category.ATTACK),

    /** 치명타 피해 +%. 배수에 더한다(배수 2 에 +50% 면 2.5 배). MMOItems 의 critical-strike-power. */
    CRIT_POWER("crit-power", "치명타 피해", "%", percent = true, category = Category.ATTACK),

    /** 준 피해의 이만큼(%)을 체력으로 돌려받는다. */
    LIFESTEAL("lifesteal", "흡혈", "%", percent = true, category = Category.ATTACK),

    /** 모든 피해에 더하는 고정값. */
    DAMAGE_BONUS("damage-bonus", "추가 피해", category = Category.ATTACK),

    /** 몹에게 주는 피해 +%. */
    PVE_DAMAGE("pve-damage", "몹 피해", "%", percent = true, category = Category.ATTACK),

    /** 플레이어에게 주는 피해 +%. */
    PVP_DAMAGE("pvp-damage", "플레이어 피해", "%", percent = true, category = Category.ATTACK),

    /** 화살·삼지창 같은 발사체 피해 +%. */
    PROJECTILE_DAMAGE("projectile-damage", "발사체 피해", "%", percent = true, category = Category.ATTACK),

    /** 언데드에게 주는 피해 +%. */
    UNDEAD_DAMAGE("undead-damage", "언데드 피해", "%", percent = true, category = Category.ATTACK),

    /** 기능의 피해 효과 +%. */
    SKILL_DAMAGE("skill-damage", "기능 피해", "%", percent = true, category = Category.ATTACK),

    /** 받는 피해를 이만큼(%) 줄인다. 방어구에 붙인다. */
    DAMAGE_REDUCTION("damage-reduction", "피해 감소", "%", percent = true, category = Category.DEFENSE),

    /** 몹에게 받는 피해 -%. */
    PVE_DEFENSE("pve-defense", "몹 피해 감소", "%", percent = true, category = Category.DEFENSE),

    /** 플레이어에게 받는 피해 -%. */
    PVP_DEFENSE("pvp-defense", "플레이어 피해 감소", "%", percent = true, category = Category.DEFENSE),

    /** 발사체에게 받는 피해 -%. */
    PROJECTILE_DEFENSE("projectile-defense", "발사체 피해 감소", "%", percent = true, category = Category.DEFENSE),

    /** 떨어져 받는 피해 -%. */
    FALL_DEFENSE("fall-defense", "낙하 피해 감소", "%", percent = true, category = Category.DEFENSE),

    /** 불·용암에게 받는 피해 -%. */
    FIRE_DEFENSE("fire-defense", "화염 피해 감소", "%", percent = true, category = Category.DEFENSE),

    /** 맞은 상대에게 이만큼 되돌려준다. */
    THORNS("thorns", "가시", category = Category.DEFENSE),

    /** 공격을 통째로 피할 확률(%). */
    DODGE_CHANCE("dodge-chance", "회피", "%", percent = true, category = Category.DEFENSE),

    /** 방어 점수. 받는 피해에 100/(100+점수) 를 곱한다 — 100 이면 절반. MMOItems(MythicLib) 의 defense. */
    DEFENSE_POINTS("defense", "방어 점수", category = Category.DEFENSE),

    /** 막을 확률(%). 막으면 [BLOCK_POWER] 만큼 줄인다. */
    BLOCK_CHANCE("block-chance", "막기 확률", "%", percent = true, category = Category.DEFENSE),

    /** 막았을 때 줄이는 피해(%). 없으면 50%. */
    BLOCK_POWER("block-power", "막기 위력", "%", percent = true, category = Category.DEFENSE),

    /** 초당 회복하는 체력. */
    HEALTH_REGEN("health-regen", "초당 체력 회복", category = Category.DEFENSE),

    // --- 원소: 공격은 고정 피해 + 부가 효과, 저항은 상대의 그 원소 피해 -% -------------------
    FIRE_DAMAGE("fire-damage", "화염 피해", category = Category.ELEMENT),
    ICE_DAMAGE("ice-damage", "냉기 피해", category = Category.ELEMENT),
    LIGHTNING_DAMAGE("lightning-damage", "번개 피해", category = Category.ELEMENT),
    POISON_DAMAGE("poison-damage", "독 피해", category = Category.ELEMENT),
    FIRE_RESIST("fire-resist", "화염 저항", "%", percent = true, category = Category.ELEMENT),
    ICE_RESIST("ice-resist", "냉기 저항", "%", percent = true, category = Category.ELEMENT),
    LIGHTNING_RESIST("lightning-resist", "번개 저항", "%", percent = true, category = Category.ELEMENT),
    POISON_RESIST("poison-resist", "독 저항", "%", percent = true, category = Category.ELEMENT),

    // --- 그 밖 ---------------------------------------------------------------------
    /** 얻는 경험치 +%. */
    EXP_BONUS("exp-bonus", "경험치 획득", "%", percent = true),

    /** 기능의 재사용 대기 -%. 합산 최대 80%. */
    COOLDOWN_REDUCTION("cooldown-reduction", "재사용 대기 감소", "%", percent = true),
    ;

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

        fun of(id: String?): Stat? {
            val text = id?.trim()?.lowercase()?.replace('_', '-') ?: return null
            return entries.firstOrNull { it.id == text }
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
    MATERIAL("material", "재료", Material.COPPER_INGOT, "◈"),
    GEM("gem", "보석", Material.EMERALD, "◆"),
    MISC("misc", "기타", Material.FLOWER_BANNER_PATTERN, "✦"),
    ;

    /**
     * 가방 어디에 있어도 효과가 나는 종류. 부적은 여러 개가 겹쳐 붙고(아이템마다 "같은 것 중복 안 함"을 켤 수 있다),
     * 유물은 종류가 달라도 **한 번에 하나만** 붙는다.
     */
    val carried: Boolean get() = this == TALISMAN || this == RELIC

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

/**
 * 희귀도.
 *
 * 색과 이름만 정한다. **설정값이 아니라 코드 목록인 이유**는 이게 아이템의 값이 아니라
 * 서버 전체가 공유하는 눈금이기 때문이다 — 아이템마다 다른 등급 체계를 만들 수 있으면
 * 등급이라는 말이 의미를 잃는다.
 */
enum class Tier(val id: String, val display: String, val color: String, private val from: String, private val to: String) {
    COMMON("common", "일반", "<white>", "#E6E6E6", "#9E9E9E"),
    UNCOMMON("uncommon", "고급", "<green>", "#8CF58C", "#2FA84F"),
    RARE("rare", "희귀", "<aqua>", "#7FDBFF", "#2F7DE1"),
    EPIC("epic", "영웅", "<light_purple>", "#E79CFF", "#9B3FE0"),
    LEGENDARY("legendary", "전설", "<gold>", "#FFE36E", "#FF9A1F"),
    MYTHIC("mythic", "신화", "<red>", "#FF7A7A", "#C2185B"),
    ;

    /** 로어 맨 아래의 등급 배지. 등급마다 두 색을 잇는 그라데이션이고, 양옆 줄은 흐리게. */
    fun badge(): String =
        "<dark_gray>▬▬▬▬</dark_gray> <gradient:" + from + ":" + to + "><bold>✦ " + display + " ✦</bold></gradient> <dark_gray>▬▬▬▬</dark_gray>"

    companion object {
        fun of(id: String?): Tier =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: COMMON
    }
}
