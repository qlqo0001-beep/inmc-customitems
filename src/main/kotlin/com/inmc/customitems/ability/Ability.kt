package com.inmc.customitems.ability

import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection

/** 기능이 언제 터지는가. */
enum class Trigger(val id: String, val display: String, val hint: String, val icon: Material) {
    RIGHT_CLICK("right-click", "우클릭", "들고 우클릭할 때", Material.BLAZE_ROD),
    LEFT_CLICK("left-click", "좌클릭", "들고 좌클릭할 때", Material.STICK),
    ON_HIT("on-hit", "적중", "이 무기로 때렸을 때 (부적·유물·장착 칸은 아무 무기로)", Material.IRON_SWORD),
    ON_KILL("on-kill", "처치", "이 무기로 죽였을 때 (부적·유물·장착 칸은 아무 무기로)", Material.WITHER_SKELETON_SKULL),
    WHEN_DAMAGED("when-damaged", "피격", "이걸 착용/소지한 채 맞았을 때", Material.SHIELD),
    PASSIVE("passive", "지속", "들거나 착용한 동안 주기적으로 (부적·유물·장착 칸은 지니기만 해도)", Material.BEACON),
    CONSUME("consume", "사용", "소모품을 쓸 때", Material.HONEY_BOTTLE),
    SHIFT_RIGHT_CLICK("shift-right-click", "웅크려 우클릭", "웅크린 채 우클릭할 때(없으면 우클릭으로)", Material.SHULKER_SHELL),
    SHIFT_LEFT_CLICK("shift-left-click", "웅크려 좌클릭", "웅크린 채 좌클릭할 때(없으면 좌클릭으로)", Material.FEATHER),
    SNEAK("sneak", "웅크리기", "착용·소지한 채 웅크리기 시작할 때", Material.LEATHER_BOOTS),
    JUMP("jump", "점프", "착용·소지한 채 뛸 때", Material.RABBIT_FOOT),
    SHOOT("shoot", "쏘기", "이 활·석궁·삼지창으로 쏠 때", Material.BOW),
    PROJECTILE_HIT("projectile-hit", "발사체 적중", "이 아이템으로 쏜 것이 맞았을 때", Material.ARROW),
    BLOCK_BREAK("block-break", "블록 캐기", "이 도구로 블록을 캤을 때", Material.IRON_PICKAXE),
    DEATH("death", "죽음", "착용·소지한 채 죽었을 때", Material.TOTEM_OF_UNDYING),
    ;

    companion object {
        fun of(id: String?): Trigger =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: RIGHT_CLICK
    }
}

/** 효과가 누구에게 가는가. */
enum class Target(val id: String, val display: String) {
    SELF("self", "자신"),
    OTHER("other", "상대"),
    ;

    companion object {
        fun of(id: String?): Target =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: SELF
    }
}

/** 효과가 받는 값 하나. GUI 가 이 정보로 편집 칸을 만든다. */
data class Param(
    val key: String,
    val display: String,
    val kind: Kind,
    val default: String,
    /** 숫자 칸의 범위. 텍스트 칸에서는 무시된다. */
    val min: Double = 0.0,
    val max: Double = 100_000.0,
) {
    enum class Kind { NUMBER, INT, TEXT, TARGET, POTION, SOUND, PARTICLE }
}

/**
 * 할 수 있는 일의 목록.
 *
 * **[COMMAND] 가 탈출구다.** 여기 없는 것은 명령어로 하면 되고, 그래서 이 목록이 모든 서버의
 * 모든 요구를 미리 맞힐 필요가 없다. 자주 쓰는 것만 전용 효과로 두고 나머지는 명령어에 맡긴다 —
 * 효과를 백 개 만들어두고 그중 다섯 개만 쓰이는 것보다 낫다.
 */
enum class EffectType(
    val id: String,
    val display: String,
    val icon: Material,
    val params: List<Param>,
) {

    POTION(
        "potion", "물약 효과", Material.POTION,
        listOf(
            Param("effect", "효과", Param.Kind.POTION, "SPEED"),
            Param("seconds", "지속 시간", Param.Kind.NUMBER, "5", min = 0.5, max = 3600.0),
            Param("level", "레벨", Param.Kind.INT, "1", min = 1.0, max = 255.0),
            Param("target", "대상", Param.Kind.TARGET, "self"),
        ),
    ),

    DAMAGE(
        "damage", "피해", Material.IRON_SWORD,
        listOf(
            Param("amount", "피해량", Param.Kind.NUMBER, "4"),
            Param("target", "대상", Param.Kind.TARGET, "other"),
        ),
    ),

    HEAL(
        "heal", "회복", Material.GOLDEN_APPLE,
        listOf(
            Param("amount", "회복량", Param.Kind.NUMBER, "4"),
            Param("target", "대상", Param.Kind.TARGET, "self"),
        ),
    ),

    IGNITE(
        "ignite", "점화", Material.FLINT_AND_STEEL,
        listOf(
            Param("seconds", "지속 시간", Param.Kind.NUMBER, "3", min = 0.5, max = 300.0),
            Param("target", "대상", Param.Kind.TARGET, "other"),
        ),
    ),

    LIGHTNING(
        "lightning", "번개", Material.LIGHTNING_ROD,
        listOf(
            Param("target", "대상", Param.Kind.TARGET, "other"),
            // 연출만 하고 피해는 DAMAGE 로 따로 주고 싶을 때가 있다.
            Param("damage", "피해를 줄지 (1=예 0=아니오)", Param.Kind.INT, "1", min = 0.0, max = 1.0),
        ),
    ),

    LAUNCH(
        "launch", "돌진", Material.FIREWORK_ROCKET,
        listOf(
            Param("power", "세기", Param.Kind.NUMBER, "1.5", min = 0.1, max = 10.0),
            Param("up", "위로 뜨는 정도", Param.Kind.NUMBER, "0.3", min = 0.0, max = 10.0),
            Param("target", "대상", Param.Kind.TARGET, "self"),
        ),
    ),

    TELEPORT(
        "teleport", "순간이동", Material.ENDER_PEARL,
        listOf(Param("distance", "거리", Param.Kind.NUMBER, "8", min = 1.0, max = 64.0)),
    ),

    SOUND(
        "sound", "소리", Material.NOTE_BLOCK,
        listOf(
            Param("sound", "소리", Param.Kind.SOUND, "ENTITY_GENERIC_EXPLODE"),
            Param("volume", "크기", Param.Kind.NUMBER, "1", min = 0.0, max = 10.0),
            Param("pitch", "높이", Param.Kind.NUMBER, "1", min = 0.5, max = 2.0),
        ),
    ),

    PARTICLE(
        "particle", "입자", Material.FIREWORK_STAR,
        listOf(
            Param("particle", "입자", Param.Kind.PARTICLE, "FLAME"),
            Param("count", "개수", Param.Kind.INT, "20", min = 1.0, max = 1000.0),
            Param("spread", "퍼짐", Param.Kind.NUMBER, "0.5", min = 0.0, max = 10.0),
            Param("target", "대상", Param.Kind.TARGET, "self"),
        ),
    ),

    COMMAND(
        "command", "명령어", Material.COMMAND_BLOCK,
        listOf(
            Param("command", "명령어", Param.Kind.TEXT, ""),
            // 1 이면 콘솔, 0 이면 플레이어 권한으로. 콘솔이 기본인 이유는 보상 명령어가
            // 대개 권한을 요구하기 때문이다.
            Param("console", "콘솔로 실행 (1=예 0=아니오)", Param.Kind.INT, "1", min = 0.0, max = 1.0),
        ),
    ),

    MESSAGE(
        "message", "메시지", Material.PAPER,
        listOf(
            Param("text", "내용", Param.Kind.TEXT, ""),
            Param("actionbar", "액션바로 (1=예 0=아니오)", Param.Kind.INT, "0", min = 0.0, max = 1.0),
        ),
    ),

    // --- 스킬 ---------------------------------------------------------------------
    // 피해를 주는 것은 전부 "기능 피해" 능력치를 탄다. 범위 효과는 기본으로 몹에게만 — 플레이어까지 치려면
    // "플레이어도" 를 1 로. 서버마다 PvP 사정이 달라 기본을 안전한 쪽에 둔다.

    FIREBALL(
        "fireball", "화염구", Material.FIRE_CHARGE,
        listOf(
            Param("damage", "피해", Param.Kind.NUMBER, "6"),
            Param("speed", "속도", Param.Kind.NUMBER, "1.5", min = 0.1, max = 5.0),
        ),
    ),

    ARROW_VOLLEY(
        "arrow-volley", "화살 세례", Material.SPECTRAL_ARROW,
        listOf(
            Param("count", "화살 수", Param.Kind.INT, "5", min = 1.0, max = 50.0),
            Param("damage", "한 발 피해", Param.Kind.NUMBER, "4"),
            Param("spread", "퍼짐(도)", Param.Kind.NUMBER, "10", min = 0.0, max = 90.0),
            Param("speed", "속도", Param.Kind.NUMBER, "2.5", min = 0.1, max = 5.0),
        ),
    ),

    SHOCKWAVE(
        "shockwave", "충격파", Material.TNT_MINECART,
        listOf(
            Param("radius", "반경", Param.Kind.NUMBER, "4", min = 0.5, max = 32.0),
            Param("damage", "피해", Param.Kind.NUMBER, "4"),
            Param("power", "밀어내는 세기", Param.Kind.NUMBER, "1", min = 0.0, max = 5.0),
            Param("players", "플레이어도 (1=예 0=아니오)", Param.Kind.INT, "0", min = 0.0, max = 1.0),
        ),
    ),

    AOE_DAMAGE(
        "aoe-damage", "범위 피해", Material.NETHERITE_SWORD,
        listOf(
            Param("radius", "반경", Param.Kind.NUMBER, "4", min = 0.5, max = 32.0),
            Param("damage", "피해", Param.Kind.NUMBER, "6"),
            Param("players", "플레이어도 (1=예 0=아니오)", Param.Kind.INT, "0", min = 0.0, max = 1.0),
            Param("target", "중심", Param.Kind.TARGET, "self"),
        ),
    ),

    AOE_POTION(
        "aoe-potion", "범위 물약", Material.LINGERING_POTION,
        listOf(
            Param("effect", "효과", Param.Kind.POTION, "SLOWNESS"),
            Param("seconds", "지속 시간", Param.Kind.NUMBER, "5", min = 0.5, max = 3600.0),
            Param("level", "레벨", Param.Kind.INT, "1", min = 1.0, max = 255.0),
            Param("radius", "반경", Param.Kind.NUMBER, "5", min = 0.5, max = 32.0),
            Param("players", "플레이어도 (1=예 0=아니오)", Param.Kind.INT, "0", min = 0.0, max = 1.0),
        ),
    ),

    PULL(
        "pull", "끌어당기기", Material.FISHING_ROD,
        listOf(
            Param("radius", "반경", Param.Kind.NUMBER, "6", min = 0.5, max = 32.0),
            Param("power", "세기", Param.Kind.NUMBER, "1", min = 0.1, max = 5.0),
            Param("players", "플레이어도 (1=예 0=아니오)", Param.Kind.INT, "0", min = 0.0, max = 1.0),
        ),
    ),

    KNOCKBACK(
        "knockback", "밀쳐내기", Material.PISTON,
        listOf(
            Param("power", "세기", Param.Kind.NUMBER, "1.5", min = 0.1, max = 10.0),
            Param("up", "띄우기", Param.Kind.NUMBER, "0.4", min = 0.0, max = 5.0),
            Param("target", "대상", Param.Kind.TARGET, "other"),
        ),
    ),

    FREEZE(
        "freeze", "얼리기", Material.PACKED_ICE,
        listOf(
            Param("seconds", "지속 시간", Param.Kind.NUMBER, "3", min = 0.5, max = 60.0),
            Param("level", "느려짐 레벨", Param.Kind.INT, "2", min = 1.0, max = 10.0),
            Param("target", "대상", Param.Kind.TARGET, "other"),
        ),
    ),

    EXPLOSION(
        "explosion", "폭발(블록 안 부숨)", Material.TNT,
        listOf(
            Param("radius", "반경", Param.Kind.NUMBER, "3", min = 0.5, max = 16.0),
            Param("damage", "피해", Param.Kind.NUMBER, "8"),
            Param("players", "플레이어도 (1=예 0=아니오)", Param.Kind.INT, "0", min = 0.0, max = 1.0),
            Param("target", "중심", Param.Kind.TARGET, "other"),
        ),
    ),

    BEAM(
        "beam", "광선", Material.END_ROD,
        listOf(
            Param("length", "길이", Param.Kind.NUMBER, "12", min = 1.0, max = 64.0),
            Param("damage", "피해", Param.Kind.NUMBER, "6"),
            Param("particle", "입자", Param.Kind.PARTICLE, "END_ROD"),
            Param("players", "플레이어도 (1=예 0=아니오)", Param.Kind.INT, "0", min = 0.0, max = 1.0),
        ),
    ),

    METEOR(
        "meteor", "운석", Material.MAGMA_BLOCK,
        listOf(
            Param("damage", "피해", Param.Kind.NUMBER, "10"),
            Param("radius", "반경", Param.Kind.NUMBER, "3", min = 0.5, max = 16.0),
            Param("players", "플레이어도 (1=예 0=아니오)", Param.Kind.INT, "0", min = 0.0, max = 1.0),
            Param("target", "떨어질 곳", Param.Kind.TARGET, "other"),
        ),
    ),

    CHAIN_LIGHTNING(
        "chain-lightning", "연쇄 번개", Material.LIGHTNING_ROD,
        listOf(
            Param("damage", "피해", Param.Kind.NUMBER, "5"),
            Param("jumps", "튀는 횟수", Param.Kind.INT, "3", min = 1.0, max = 20.0),
            Param("range", "튀는 거리", Param.Kind.NUMBER, "6", min = 1.0, max = 32.0),
            Param("players", "플레이어도 (1=예 0=아니오)", Param.Kind.INT, "0", min = 0.0, max = 1.0),
        ),
    ),

    HEAL_AREA(
        "heal-area", "범위 회복", Material.GLISTERING_MELON_SLICE,
        listOf(
            Param("amount", "회복량", Param.Kind.NUMBER, "4"),
            Param("radius", "반경", Param.Kind.NUMBER, "6", min = 0.5, max = 32.0),
        ),
    ),

    DRAIN(
        "drain", "흡수", Material.WITHER_ROSE,
        listOf(
            Param("damage", "피해", Param.Kind.NUMBER, "4"),
            Param("percent", "피해의 몇 %를 회복", Param.Kind.NUMBER, "50", min = 0.0, max = 1000.0),
            Param("target", "대상", Param.Kind.TARGET, "other"),
        ),
    ),
    ;

    fun defaults(): Map<String, String> = params.associate { it.key to it.default }

    companion object {
        fun of(id: String?): EffectType? =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) }
    }
}

/**
 * 아이템에 붙은 기능 하나.
 *
 * **발동 조건과 효과를 분리했다.** 같은 "번개" 효과를 우클릭에도, 적중에도, 피격에도 붙일 수
 * 있어야 하고, 그러려면 둘이 따로 골라져야 한다. 합쳐두면 조합 수만큼 항목이 생긴다.
 *
 * [chance] 와 [cooldownSeconds] 는 **모든 기능이 공통으로** 갖는다. 확률 없는 적중 효과는
 * 금방 지겹고, 쿨다운 없는 우클릭 효과는 연타로 무너진다 — 둘 다 효과별 설정으로 두면
 * 새 효과를 만들 때마다 빠뜨린다.
 */
data class Ability(
    val trigger: Trigger,
    val effect: EffectType,
    /** 발동 확률(%). 100 이면 항상. */
    val chance: Double = 100.0,
    val cooldownSeconds: Double = 0.0,
    /** [PASSIVE] 일 때 몇 초마다 도는지. */
    val intervalSeconds: Double = 5.0,
    val values: Map<String, String> = emptyMap(),
) {

    fun value(key: String): String =
        values[key] ?: effect.params.firstOrNull { it.key == key }?.default.orEmpty()

    fun number(key: String): Double = value(key).toDoubleOrNull() ?: 0.0

    fun int(key: String): Int = value(key).toDoubleOrNull()?.toInt() ?: 0

    fun target(key: String = "target"): Target = Target.of(value(key))

    /** 로어 한 줄. 관리자가 아니라 **플레이어가 읽는다.** */
    fun line(): String = buildString {
        val potion = potionText()
        append("<dark_gray>▸ </dark_gray><yellow>")
        append(trigger.display)
        // 지속 물약은 "지속 효과: 야간 투시"(사용자 요청 2026-09-30) — 어떤 효과인지가 곧 이 기능의 전부다.
        if (potion != null && trigger == Trigger.PASSIVE) append(" 효과")
        append("</yellow><gray>: ")
        append(potion ?: effect.display)
        if (chance < 100.0) append(" (" + trim(chance) + "%)")
        if (cooldownSeconds > 0.0) append(" <dark_gray>[" + trim(cooldownSeconds) + "s]</dark_gray>")
        append("</gray>")
    }

    /**
     * 물약 기능이면 **어떤 효과인지** — `야간 투시` · `신속 II 5초` · `대상에게 구속 II 3초` · `주변에 독 5초`. 이름은 클라이언트의 번역
     * 열쇠(`effect.minecraft.night_vision`)라 보는 사람의 말로 나오고, 레지스트리를 타지 않아 서버 없이 정해진다. 지속 기능은 계속 다시
     * 걸리므로 시간을 적지 않는다. 물약이 아니거나 효과 값을 못 읽으면 null — 효과 종류 이름(물약 효과)을 쓴다.
     */
    private fun potionText(): String? {
        if (effect != EffectType.POTION && effect != EffectType.AOE_POTION) return null
        val key = potionKey(value("effect")) ?: return null
        val level = int("level").coerceAtLeast(1)
        val name = "<lang:" + key + ">" + when {
            level in 2..10 -> " <lang:enchantment.level.$level>"
            level > 10 -> " $level"
            else -> ""
        }
        val seconds = number("seconds")
        val duration = if (trigger == Trigger.PASSIVE || seconds <= 0.0) "" else " " + trim(seconds) + "초"
        return when {
            effect == EffectType.AOE_POTION -> "주변에 " + name + duration
            target() == Target.OTHER -> "대상에게 " + name + duration
            else -> name + duration
        }
    }

    fun save(section: ConfigurationSection) {
        section.set("trigger", trigger.id)
        section.set("effect", effect.id)
        if (chance != 100.0) section.set("chance", chance)
        if (cooldownSeconds > 0.0) section.set("cooldown", cooldownSeconds)
        if (trigger == Trigger.PASSIVE) section.set("interval", intervalSeconds)
        if (values.isNotEmpty()) {
            val node = section.createSection("values")
            for ((key, value) in values) node.set(key, value)
        }
    }

    private fun trim(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    companion object {

        /**
         * 적힌 물약 효과(`NIGHT_VISION` · `minecraft:night_vision`)의 번역 열쇠(`effect.minecraft.night_vision`). 엔진이 효과를 찾는 규칙
         * ([com.inmc.customitems.item.Registries.potionEffect] — 소문자, 이름공간이 없으면 `minecraft`, 점은 밑줄)과 같게 푼다. 비었으면 null.
         */
        fun potionKey(raw: String): String? {
            val text = raw.trim().lowercase().takeIf { it.isNotEmpty() } ?: return null
            val namespace = if (':' in text) text.substringBefore(':') else "minecraft"
            val path = if (':' in text) text.substringAfter(':') else text.replace('.', '_')
            if (namespace.isEmpty() || path.isEmpty()) return null
            return "effect.$namespace.$path"
        }

        /** 읽는다. 효과를 못 알아보면 null — 모르는 효과를 조용히 무시하면 안 터지는 이유를 못 찾는다. */
        fun load(section: ConfigurationSection): Ability? {
            val effect = EffectType.of(section.getString("effect")) ?: return null
            val values = HashMap<String, String>()
            section.getConfigurationSection("values")?.let { node ->
                for (key in node.getKeys(false)) values[key] = node.get(key)?.toString().orEmpty()
            }
            return Ability(
                trigger = Trigger.of(section.getString("trigger")),
                effect = effect,
                chance = section.getDouble("chance", 100.0).coerceIn(0.0, 100.0),
                cooldownSeconds = section.getDouble("cooldown", 0.0).coerceAtLeast(0.0),
                intervalSeconds = section.getDouble("interval", 5.0).coerceAtLeast(0.5),
                values = values,
            )
        }

        /** 새로 만들 때의 기본값. 효과가 요구하는 값이 전부 채워져 있다. */
        fun of(trigger: Trigger, effect: EffectType): Ability =
            Ability(trigger = trigger, effect = effect, values = effect.defaults())
    }
}
