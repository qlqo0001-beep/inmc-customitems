package com.inmc.customitems.item

import com.inmc.customitems.ability.Ability
import com.inmc.customitems.ability.Trigger
import com.inmc.customitems.craft.Part
import com.inmc.customitems.craft.loadParts
import com.inmc.customitems.craft.saveParts
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.EquipmentSlotGroup
import org.bukkit.inventory.ItemFlag

/**
 * 보석. 소켓에 박으면 보석 아이템의 **기준** 능력치가 더해진다(보석 자신의 무작위 폭은 박는 순간 버린다).
 *
 * @param color 맞는 소켓의 색. 소켓이나 보석 어느 쪽이 `any` 여도 맞는다.
 * @param chance 박기 성공률(%). 실패하면 보석만 깨진다.
 */
data class GemSpec(val color: String = ANY, val chance: Double = 100.0) {
    fun fits(socket: String): Boolean = socket == ANY || color == ANY || socket == color

    companion object {
        const val ANY = "any"
    }
}

/**
 * 소모품. 우클릭하면 쓰인다(바닐라의 먹기·마시기·놓기는 막는다). 효과는 체력·배고픔 회복과,
 * 발동 조건 [com.inmc.customitems.ability.Trigger.CONSUME] 에 붙인 기능(물약·명령어…)이다.
 *
 * 끌어다 놓아 쓰는 힘도 여기 있다 — 보석 빼기, 수리.
 *
 * @param uses 한 개로 몇 번 쓰는가. 0 이면 닳지 않는다. 2 이상이면 겹치지 않게 한다(남은 횟수가 한 개마다 다르다).
 * @param cooldown 다시 쓰기까지 초. 사람·아이템마다.
 * @param unsocket true 면 소켓 아이템 위에 끌어다 놓아 마지막 보석을 빼 돌려준다.
 * @param repair 끌어다 놓으면 이만큼 내구도를 고친다.
 * @param repairPercent 끌어다 놓으면 최대 내구도의 이만큼(%)을 고친다.
 */
data class ConsumeSpec(
    val health: Double = 0.0,
    val food: Int = 0,
    val saturation: Double = 0.0,
    val uses: Int = 1,
    val cooldown: Double = 0.0,
    val unsocket: Boolean = false,
    val repair: Int = 0,
    val repairPercent: Double = 0.0,
    /** 미확인 아이템 위에 놓으면 정체를 밝힌다(감정서). */
    val identify: Boolean = false,
    /** 아이템 위에 놓으면 그 아이템을 분해해 [CustomItem.salvage] 를 준다. */
    val deconstruct: Boolean = false,
    /** 강화석. 아이템 위에 놓으면 한 단계 강화를 시도한다. */
    val upgrade: UpgradeStone? = null,
    /** 진화석. 최대 강화한 아이템 위에 놓으면 진화한다. */
    val evolve: EvolveStone? = null,
) {
    /** 끌어다 놓아 쓰는 힘이 있는가. 있으면 우클릭으로는 안 쓴다. */
    val dragOnly: Boolean get() = unsocket || repair > 0 || repairPercent > 0.0 || identify || deconstruct || upgrade != null || evolve != null

    fun save(section: ConfigurationSection) {
        if (health != 0.0) section.set("health", health)
        if (food != 0) section.set("food", food)
        if (saturation != 0.0) section.set("saturation", saturation)
        section.set("uses", uses)
        if (cooldown > 0.0) section.set("cooldown", cooldown)
        if (unsocket) section.set("unsocket", true)
        if (repair > 0) section.set("repair", repair)
        if (repairPercent > 0.0) section.set("repair-percent", repairPercent)
        if (identify) section.set("identify", true)
        if (deconstruct) section.set("deconstruct", true)
        upgrade?.save(section.createSection("upgrade-stone"))
        evolve?.save(section.createSection("evolve-stone"))
    }

    companion object {
        fun load(section: ConfigurationSection): ConsumeSpec = ConsumeSpec(
            health = section.getDouble("health", 0.0),
            food = section.getInt("food", 0),
            saturation = section.getDouble("saturation", 0.0),
            uses = section.getInt("uses", 1).coerceIn(0, 10_000),
            cooldown = section.getDouble("cooldown", 0.0).coerceAtLeast(0.0),
            unsocket = section.getBoolean("unsocket", false),
            repair = section.getInt("repair", 0).coerceAtLeast(0),
            repairPercent = section.getDouble("repair-percent", 0.0).coerceIn(0.0, 100.0),
            identify = section.getBoolean("identify", false),
            deconstruct = section.getBoolean("deconstruct", false),
            upgrade = section.getConfigurationSection("upgrade-stone")?.let(UpgradeStone::load),
            evolve = section.getConfigurationSection("evolve-stone")?.let(EvolveStone::load),
        )
    }
}

/**
 * 쓰려면 갖춰야 하는 것. 모자라면 무기는 못 때리고, 방어구는 벗겨지고, 능력치·기능·소모품이 안 돈다.
 *
 * @param level 바닐라 레벨(경험치 레벨).
 * @param permission 비우면 없음.
 */
data class Requirement(val level: Int = 0, val permission: String = "") {
    val isEmpty: Boolean get() = level <= 0 && permission.isBlank()

    fun save(section: ConfigurationSection) {
        if (level > 0) section.set("level", level)
        if (permission.isNotBlank()) section.set("permission", permission)
    }

    companion object {
        fun load(section: ConfigurationSection?): Requirement =
            if (section == null) Requirement() else Requirement(section.getInt("level", 0).coerceAtLeast(0), section.getString("permission").orEmpty())
    }
}

/**
 * 관리자가 만든 아이템 하나.
 *
 * 다섯 층으로 나뉜다.
 *
 * | 층 | 무엇 | 누가 해석하나 |
 * |---|---|---|
 * | 모양 | 재질·이름·설명·인챈트·플래그 | 바닐라 |
 * | 겉모습 | [texture] · [model] · [customModelData] | 리소스팩 |
 * | 분류 | [type] · [tier] | 표시와 걸러보기 전용 |
 * | 공격 방식 | [style] | 우리가 전투·클릭 때 한다 |
 * | 능력치 | [stats] | 바닐라 속성으로 내려가거나 우리가 전투 때 계산 |
 * | 기능 | [abilities] | 우리가 발동시킨다 |
 * | 연동 값 | [data] | **다른 플러그인이** 읽는다 |
 *
 * 마지막 층이 이 플러그인이 다른 시스템과 이어지는 방식이다. `fishing.reel-power: 15` 를
 * 적어두면 낚시가 그걸 읽어 낚싯대로 쓴다. 이 플러그인은 그 열쇠가 무슨 뜻인지 모르고,
 * 낚시는 이 플러그인이 있는지 모른다 — core 의
 * [kr.inmc.core.integration.CustomItemHook.data] 가 그 사이를 잇는다.
 */
data class CustomItem(
    val id: String,
    val material: Material,
    val type: ItemType = ItemType.MISC,
    val tier: Tier = Tier.COMMON,
    /** 종류 아래의 소분류 id(`categories.yml`). 비우면 "분류 없음". 표시와 걸러보기 전용. */
    val category: String = "",
    /** 무기의 공격 방식(단검·창·지팡이…). */
    val style: AttackStyle = AttackStyle.NONE,

    // --- 모양 ---
    /** MiniMessage 또는 레거시 색코드. 비우면 바닐라 이름 그대로. */
    val displayName: String = "",
    /** 관리자가 직접 적는 설명. 능력치·기능 줄은 [ItemBuilder] 가 따로 붙인다. */
    val lore: List<String> = emptyList(),

    // --- 겉모습 (리소스팩) ---
    /**
     * 팩 폴더의 png 이름. 예: `boss_sword.png`
     *
     * 적으면 빌드할 때 모델까지 자동으로 만들고 아이템에 `item_model` 을 붙인다.
     */
    val texture: String = "",

    /**
     * 직접 만든 모델 이름. 예: `item/boss_sword`
     *
     * 적으면 [texture] 로 모델을 만들지 않고 이것을 가리킨다. 검처럼 손에 들었을 때 모양이
     * 달라야 하는 것은 평면 아이콘으로 안 되므로 이 길이 필요하다.
     */
    val model: String = "",

    /**
     * 낡은 리소스팩 모델 번호 — **옮기기 전의 값일 뿐 더 쓰지 않는다**(사용자 결정 2026-09-28). 옛 정의나 다른 플러그인에서 옮겨 온
     * 아이템에 남아 있으면 다음 리소스팩 빌드가 팩이 그리던 모양 그대로 [model]·`item_model` 로 옮기고 0 으로 만든다
     * ([com.inmc.customitems.pack.NumberMigration]). 화면에서 적을 수 없다.
     */
    val customModelData: Int = 0,

    /** 인챈트 id → 레벨. 바닐라 상한을 넘겨도 된다. */
    val enchants: Map<String, Int> = emptyMap(),
    /**
     * 커스텀 인첸트 id → 레벨. 붙이는 것은 인첸트 플러그인이다 — core 의
     * [kr.inmc.core.integration.CustomEnchantHook] 을 거치므로 이 플러그인은 그 인첸트가 무엇인지 모른다.
     * 인첸트 플러그인이 없으면 조용히 안 붙는다.
     */
    val customEnchants: Map<String, Int> = emptyMap(),
    val flags: Set<String> = emptySet(),
    val unbreakable: Boolean = false,
    /** 최대 내구도. 0 이면 바닐라 기본값. */
    val maxDurability: Int = 0,
    /** 인챈트 없이 빛나게 한다. */
    val glow: Boolean = false,
    /** 1.21 부품 — 최대 겹침·툴팁·착용 칸·색·갑옷 장식·머리 텍스처·활공·불 저항. */
    val components: Components = Components(),

    // --- 능력치와 기능 ---
    /** 기준값. 아이템마다 [spreads] 만큼 흔들린다. */
    val stats: Map<Stat, Double> = emptyMap(),
    /** 능력치별 무작위 폭. 없으면 기준값 그대로. */
    val spreads: Map<Stat, Spread> = emptyMap(),
    /** 만들 때 확률로 붙는 수식어. */
    val modifiers: List<ItemModifier> = emptyList(),
    /** 속한 아이템 세트 id. 비우면 없음. */
    val set: String = "",
    /** 보석 소켓의 색. `any` 는 아무 보석이나. */
    val sockets: List<String> = emptyList(),
    /** 이 아이템이 보석이면 그 설정. 보석을 박으면 이 아이템의 기준 능력치가 더해진다. */
    val gem: GemSpec? = null,
    /** 소모품이면 그 설정. */
    val consume: ConsumeSpec? = null,
    /**
     * 재질의 바닐라 **설치·먹기·마시기**를 막는다(사용자 요청 2026-09-30) — 돌로 만든 재료가 놓이거나 빵으로 만든 증표가 먹히지 않게.
     * 먹는 것은 아이템에서 먹는 부품(`consumable`)을 떼어 먹기가 시작조차 안 되고([ItemBuilder.render]), 놓는 것은 `BlockPlaceEvent` 를
     * 막는다. 우리 것(우클릭 기능 · 소모품 · 커스텀 블록)은 그대로 돈다.
     */
    val preventVanillaUse: Boolean = false,
    /**
     * 배낭 크기(칸 수, 0 = 배낭 아님) — 배낭 종류([ItemType.BACKPACK], [isBackpack])를 우클릭하면 **이 아이템 한 개만의** 창고가 열린다
     * (사용자 결정 2026-09-30: 셜커 상자처럼 아이템에 붙는다, 크기는 자유 — 45칸씩 페이지). 내용물은 아이템이 아니라 서버 파일에 있고
     * 아이템에는 배낭 번호만 찍힌다([com.inmc.customitems.player.Backpacks]). 장착 칸의 배낭 줄에 끼우면 `/배낭 <번호>` 로 연다.
     * 강화 단계가 칸을 더할 수 있다([UpgradeStep.backpack]).
     */
    val backpack: Int = 0,
    /**
     * 드랍 자동 수납(사용자 요청 2026-09-30) — 배낭이면 주운 물건이 가방보다 먼저 이 배낭에 들어간다. **보석**에 켜 두면 그 보석을
     * 박은 배낭이 그렇게 된다. 강화 단계로도 켠다([UpgradeStep.autoPickup]). 켜졌는지는 [com.inmc.customitems.player.Backpacks.autoPickup].
     */
    val autoPickup: Boolean = false,
    /**
     * 사용 기간(초, 0 = 없음) — **만들어진 순간부터 실제 시간**으로 흐른다(사용자 결정 2026-09-30). 끝나는 시각은 아이템 한 개마다
     * 찍힌다([ItemInstance.expires]). 다 되면 [expiry] 대로 — 사라지거나, 남되 능력치·기능·장착·사용이 멈춘다([Periods]).
     */
    val period: Long = 0L,
    val expiry: Expiry = Expiry.VANISH,
    val requirement: Requirement = Requirement(),
    /** 만들 때 미확인으로 나온다. 감정서로 밝힐 때까지 능력치·기능이 돌지 않는다. */
    val unidentified: Boolean = false,
    /** 분해하면 나오는 것. 비우면 분해할 수 없다. */
    val salvage: List<Part> = emptyList(),
    /** 강화·진화. */
    val upgrade: UpgradeSpec = UpgradeSpec(),
    /** 부적: 가방에 같은 부적이 여럿 있어도(강화 단계가 달라도) 가장 높은 단계 하나만 효과가 난다. */
    val noDuplicate: Boolean = false,
    /**
     * 장착 칸에 끼우는 종류(장신구·부적·유물)가 **장착 칸 밖**(가방·손)에서도 효과를 내는가. null 이면 서버 설정
     * (`equipment.yml` 의 `inventory-effects`)을 따른다. 다른 종류에는 뜻이 없다.
     */
    val inventoryEffect: Boolean? = null,
    /** 관리자가 만든 종류의 id(`types.yml`, 보호권 등). 비우면 기본 종류([type]) 그대로. 동작은 늘 [type] 이 정한다. */
    val customType: String = "",
    /**
     * 다른 플러그인에서 맡는 역할(core `ItemRoles`) → 그 역할의 설정 값. 열쇠는 `invkeeper.item` 처럼 `<플러그인>.<역할>`.
     * 이 플러그인은 값의 뜻을 모른다 — 역할을 내놓은 플러그인이 읽는다.
     */
    val roles: Map<String, Map<String, String>> = emptyMap(),
    /** 겉모습 모델을 남의 팩 것으로(`ia:coin`). 비우면 우리 규칙대로. 다른 플러그인의 아이템을 받아들일 때 옮겨 온다. */
    val itemModel: String = "",
    val abilities: List<Ability> = emptyList(),

    /** 다른 플러그인이 읽을 값들. 이 플러그인은 뜻을 모른다. */
    val data: Map<String, String> = emptyMap(),

    /** 블록으로 놓이면 그 방식. null 이면 블록이 아니다. */
    val block: BlockSpec? = null,

    /**
     * 도구의 채굴 등급(커스텀 블록의 광물 등급과 견준다). null 이면 재질의 바닐라 등급(다이아몬드 곡괭이 3 · 네더라이트 4).
     * 5 부터는 바닐라에 없는 등급이라 "이 커스텀 곡괭이부터 캐지는 광석"을 만든다. 도구 재질이 아니면 뜻이 없다.
     */
    val miningTier: Int? = null,
) {

    fun label(): String = displayName.ifBlank { id }

    /** 배낭으로 열리는가 — 배낭 종류이고 크기가 있을 때만. 종류를 바꾸면 크기는 남아도 닫힌다. */
    val isBackpack: Boolean get() = backpack > 0 && type == ItemType.BACKPACK

    /** 마인크래프트 열쇠·리소스팩 경로에 쓰는 이름([resourceId]). */
    val resourceId: String get() = resourceId(id)

    /** 능력치가 도는 칸. 장신구와 방패는 양손 어디서나, 그 밖은 재질이 정한다([slotFor]). */
    fun slotGroup(): EquipmentSlotGroup = Components.slotGroup(components.equipSlot)
        ?: if (type == ItemType.ACCESSORY || type == ItemType.BACKPACK || material.name == "SHIELD") EquipmentSlotGroup.HAND else slotFor(material)

    /** 장착 칸 밖(가방·손)에서 효과를 내는가. 장착 칸에 끼우는 종류만 [inventoryEffect]·[serverDefault] 를 탄다. */
    fun worksOutsideSlots(serverDefault: Boolean): Boolean =
        !type.slotted || (inventoryEffect ?: serverDefault)

    fun stat(stat: Stat): Double = stats[stat] ?: 0.0

    fun has(stat: Stat): Boolean = (stats[stat] ?: 0.0) != 0.0

    fun abilitiesOf(trigger: Trigger): List<Ability> = abilities.filter { it.trigger == trigger }

    fun save(section: ConfigurationSection) {
        section.set("material", material.name)
        section.set("type", type.id)
        section.set("tier", tier.id)
        if (category.isNotBlank()) section.set("category", category)
        if (style != AttackStyle.NONE) section.set("attack-style", style.id)

        if (displayName.isNotBlank()) section.set("display-name", displayName)
        if (lore.isNotEmpty()) section.set("lore", lore)
        if (texture.isNotBlank()) section.set("texture", texture)
        if (model.isNotBlank()) section.set("model", model)
        if (customModelData > 0) section.set("custom-model-data", customModelData)
        if (unbreakable) section.set("unbreakable", true)
        if (glow) section.set("glow", true)
        if (maxDurability > 0) section.set("max-durability", maxDurability)
        if (flags.isNotEmpty()) section.set("flags", flags.toList())
        if (!components.isEmpty) components.save(section.createSection("components"))

        if (enchants.isNotEmpty()) {
            val node = section.createSection("enchants")
            for ((key, level) in enchants) node.set(key, level)
        }
        if (customEnchants.isNotEmpty()) {
            val node = section.createSection("custom-enchants")
            for ((key, level) in customEnchants) node.set(key, level)
        }
        if (stats.isNotEmpty()) {
            val node = section.createSection("stats")
            for ((stat, value) in stats) {
                val spread = spreads[stat]
                if (spread == null || spread.spread <= 0.0) {
                    node.set(stat.id, value)
                    continue
                }
                val child = node.createSection(stat.id)
                child.set("base", value)
                child.set("spread", spread.spread)
                if (spread.max > 0.0) child.set("max-spread", spread.max)
            }
        }
        if (set.isNotBlank()) section.set("set", set)
        if (sockets.isNotEmpty()) section.set("sockets", sockets)
        gem?.let {
            val node = section.createSection("gem")
            node.set("color", it.color)
            node.set("chance", it.chance)
        }
        consume?.save(section.createSection("consume"))
        if (preventVanillaUse) section.set("prevent-vanilla-use", true)
        if (backpack > 0) section.set("backpack", backpack)
        if (autoPickup) section.set("auto-pickup", true)
        if (period > 0) {
            section.set("period", kr.inmc.core.util.Durations.format(period))
            if (expiry != Expiry.VANISH) section.set("expiry", expiry.id)
        }
        if (!requirement.isEmpty) requirement.save(section.createSection("requirement"))
        if (unidentified) section.set("unidentified", true)
        saveParts(section, "salvage", salvage)
        if (!upgrade.isEmpty) upgrade.save(section.createSection("upgrade"))
        if (noDuplicate) section.set("no-duplicate", true)
        inventoryEffect?.let { section.set("inventory-effect", it) }
        if (customType.isNotBlank()) section.set("custom-type", customType)
        if (roles.isNotEmpty()) {
            // 역할 열쇠의 점은 YAML 이 경로로 읽는다 — `:` 로 바꿔 적는다.
            val node = section.createSection("roles")
            for ((role, values) in roles) {
                val child = node.createSection(role.replace('.', ':'))
                for ((key, value) in values) child.set(key, value)
            }
        }
        if (itemModel.isNotBlank()) section.set("item-model", itemModel)
        if (modifiers.isNotEmpty()) {
            val node = section.createSection("modifiers")
            for (modifier in modifiers) modifier.save(node.createSection(modifier.id))
        }
        if (abilities.isNotEmpty()) {
            val node = section.createSection("abilities")
            for ((index, ability) in abilities.withIndex()) {
                ability.save(node.createSection(index.toString()))
            }
        }
        if (data.isNotEmpty()) {
            val node = section.createSection("data")
            for ((key, value) in data) node.set(key, value)
        }
        block?.save(section.createSection("block"))
        miningTier?.let { section.set("mining-tier", it) }
    }

    companion object {

        /** 읽는다. **재질이 없거나 못 알아보면 null** — 재질 없는 아이템은 만들 수가 없다. */
        fun load(key: String, section: ConfigurationSection): CustomItem? {
            val id = key.lowercase().ifBlank { return null }
            val material = matchMaterial(section.getString("material")) ?: return null

            // `공격력: 5` 도 `공격력: { base: 5, spread: 0.1, max-spread: 0.3 }`(MMOItems 모양)도 받는다.
            val stats = LinkedHashMap<Stat, Double>()
            val spreads = LinkedHashMap<Stat, Spread>()
            section.getConfigurationSection("stats")?.let { node ->
                for (statKey in node.getKeys(false)) {
                    val stat = Stat.of(statKey) ?: continue
                    val child = node.getConfigurationSection(statKey)
                    val value = child?.getDouble("base", 0.0) ?: node.getDouble(statKey, 0.0)
                    if (value == 0.0) continue
                    stats[stat] = value
                    val spread = child?.getDouble("spread", 0.0) ?: 0.0
                    if (spread > 0.0) spreads[stat] = Spread(spread, child?.getDouble("max-spread", 0.0)?.coerceAtLeast(0.0) ?: 0.0)
                }
            }
            val modifiers = section.getConfigurationSection("modifiers")?.let { node ->
                node.getKeys(false).mapNotNull { key -> node.getConfigurationSection(key)?.let { ItemModifier.load(key.lowercase(), it) } }
            }.orEmpty()

            val abilities = ArrayList<Ability>()
            section.getConfigurationSection("abilities")?.let { node ->
                // 번호 순서대로. 순서가 곧 발동 순서고, 맵 순서에 흔들리면 안 된다.
                for (abilityKey in node.getKeys(false).sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }) {
                    val entry = node.getConfigurationSection(abilityKey) ?: continue
                    Ability.load(entry)?.let { abilities += it }
                }
            }

            return CustomItem(
                id = id,
                material = material,
                type = ItemType.of(section.getString("type")),
                tier = Tier.of(section.getString("tier")),
                category = section.getString("category").orEmpty().trim().lowercase(),
                style = AttackStyle.of(section.getString("attack-style")),
                displayName = section.getString("display-name").orEmpty(),
                lore = section.getStringList("lore"),
                texture = section.getString("texture").orEmpty(),
                model = section.getString("model").orEmpty(),
                customModelData = section.getInt("custom-model-data", 0).coerceAtLeast(0),
                enchants = readInts(section.getConfigurationSection("enchants")),
                customEnchants = readInts(section.getConfigurationSection("custom-enchants")),
                flags = section.getStringList("flags").map { it.uppercase() }.toSet(),
                unbreakable = section.getBoolean("unbreakable", false),
                maxDurability = section.getInt("max-durability", 0).coerceAtLeast(0),
                glow = section.getBoolean("glow", false),
                components = Components.load(section.getConfigurationSection("components")),
                stats = stats,
                spreads = spreads,
                modifiers = modifiers,
                set = section.getString("set").orEmpty().lowercase(),
                sockets = section.getStringList("sockets").map { it.trim().lowercase() }.filter { it.isNotEmpty() },
                gem = section.getConfigurationSection("gem")?.let {
                    GemSpec(it.getString("color", GemSpec.ANY)!!.lowercase(), it.getDouble("chance", 100.0).coerceIn(0.0, 100.0))
                },
                consume = section.getConfigurationSection("consume")?.let(ConsumeSpec::load),
                preventVanillaUse = section.getBoolean("prevent-vanilla-use", false),
                backpack = section.getInt("backpack", 0).coerceIn(0, com.inmc.customitems.player.BackpackLayout.MAX),
                autoPickup = section.getBoolean("auto-pickup", false),
                period = kr.inmc.core.util.Durations.parse(section.getString("period"), 0L).coerceAtLeast(0L),
                expiry = Expiry.of(section.getString("expiry")),
                requirement = Requirement.load(section.getConfigurationSection("requirement")),
                unidentified = section.getBoolean("unidentified", false),
                salvage = loadParts(section, "salvage"),
                upgrade = UpgradeSpec.load(section.getConfigurationSection("upgrade"), id),
                noDuplicate = section.getBoolean("no-duplicate", false),
                inventoryEffect = if (section.isBoolean("inventory-effect")) section.getBoolean("inventory-effect") else null,
                customType = section.getString("custom-type")?.trim()?.lowercase().orEmpty(),
                roles = section.getConfigurationSection("roles")?.let { node ->
                    node.getKeys(false).associate { key ->
                        key.replace(':', '.') to (node.getConfigurationSection(key)?.let { child -> child.getKeys(false).associateWith { child.getString(it).orEmpty() } } ?: emptyMap())
                    }
                } ?: emptyMap(),
                itemModel = section.getString("item-model")?.trim().orEmpty(),
                abilities = abilities,
                data = readStrings(section.getConfigurationSection("data")),
                block = BlockSpec.load(section.getConfigurationSection("block")),
                miningTier = if (section.isInt("mining-tier")) section.getInt("mining-tier").coerceIn(0, com.inmc.customitems.block.ToolGrades.MAX_TIER) else null,
            )
        }

        private val RESOURCE_SAFE = Regex("^[a-z0-9_.-]+$")

        /**
         * 마인크래프트가 받는 이름. 속성 수정자 열쇠·`item_model`·팩 파일 경로는 `[a-z0-9_.-]` 만 받는데
         * 아이템 id 는 한글을 허용한다([kr.inmc.core.store.DefinitionKey]). 한글 id 를 그대로 넣으면 열쇠를
         * 만드는 순간 던져 **그 아이템을 그릴 때마다** 실패하고, 목록 화면 전체가 안 열린다(2026-09-24 실제로).
         *
         * 영문 id 는 그대로 — 이미 나간 아이템의 열쇠가 바뀌지 않는다. 그 밖은 id 의 CRC32 로 늘 같은 이름.
         */
        fun resourceId(id: String): String {
            if (RESOURCE_SAFE.matches(id)) return id
            val crc = java.util.zip.CRC32().apply { update(id.toByteArray(Charsets.UTF_8)) }.value
            return "u" + java.lang.Long.toHexString(crc).padStart(8, '0')
        }

        /** `DIAMOND_SWORD` 도 `minecraft:diamond_sword` 도 받는다. */
        fun matchMaterial(raw: String?): Material? {
            val text = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
            return Material.matchMaterial(text)
                ?: Material.matchMaterial("minecraft:" + text.lowercase())
        }

        /**
         * 인챈트를 찾는다. 바닐라 id 와 네임스페이스 표기를 둘 다 받는다.
         *
         * `Enchantment.getByName` 은 1.20.5 에서 사라졌고 레지스트리 조회만 남았다.
         */
        fun matchEnchant(raw: String?): Enchantment? = Registries.enchantment(raw)

        fun matchFlag(raw: String?): ItemFlag? =
            runCatching { ItemFlag.valueOf(raw.orEmpty().uppercase()) }.getOrNull()

        /**
         * 능력치가 어느 부위에서 동작하는지.
         *
         * **손에 든 것만으로 하면 갑옷에 붙인 방어력이 안 먹는다.** 재질에서 유도하는 것이
         * 관리자가 부위까지 적게 하는 것보다 낫다 — 다이아몬드 흉갑의 방어력이 손에 들었을
         * 때만 동작할 이유가 없다.
         */
        fun slotFor(material: Material): EquipmentSlotGroup {
            val name = material.name
            return when {
                name.endsWith("_HELMET") || name == "TURTLE_HELMET" || name == "CARVED_PUMPKIN" ->
                    EquipmentSlotGroup.HEAD

                name.endsWith("_CHESTPLATE") || name == "ELYTRA" -> EquipmentSlotGroup.CHEST
                name.endsWith("_LEGGINGS") -> EquipmentSlotGroup.LEGS
                name.endsWith("_BOOTS") -> EquipmentSlotGroup.FEET
                else -> EquipmentSlotGroup.MAINHAND
            }
        }

        private fun readInts(section: ConfigurationSection?): Map<String, Int> {
            if (section == null) return emptyMap()
            return section.getKeys(false).associateWith { section.getInt(it, 0) }
                .filterValues { it != 0 }
        }

        private fun readStrings(section: ConfigurationSection?): Map<String, String> {
            if (section == null) return emptyMap()
            // 깊은 키까지 훑는다. `fishing.reel-power` 를 적으면 YamlConfiguration 이
            // 계층으로 만들어버리는데, 그래도 원래 적은 이름으로 되읽혀야 한다.
            return section.getKeys(true)
                .filter { section.get(it) !is ConfigurationSection }
                .associateWith { section.get(it)?.toString().orEmpty() }
                .filterValues { it.isNotBlank() }
        }
    }
}
