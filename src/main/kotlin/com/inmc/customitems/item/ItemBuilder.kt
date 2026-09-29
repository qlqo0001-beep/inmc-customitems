package com.inmc.customitems.item

import kr.inmc.core.integration.CustomEnchantHook
import kr.inmc.core.util.Text
import org.bukkit.NamespacedKey
import org.bukkit.attribute.AttributeModifier
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.persistence.PersistentDataType
import java.util.Random

/**
 * [CustomItem] 정의를 실제 아이템으로 만들고, 만든 것을 되알아본다.
 *
 * **되알아보는 것은 이름이 아니라 PDC 태그다.** 이름으로 비교하면 스틱을 같은 이름으로
 * 바꾼 사람이 전설 무기 행세를 할 수 있고, 관리자가 이름을 고치는 순간 이미 나간 아이템이
 * 전부 정체를 잃는다.
 */
object ItemBuilder {

    /**
     * 우리가 만든 아이템에 찍는 태그.
     *
     * 네임스페이스를 플러그인 이름이 아니라 **상수로 고정**한다. 플러그인 이름이 바뀌어도
     * 이미 나간 아이템이 정체를 잃지 않는다 — 인벤키퍼가 같은 이유로 `invkeeper` 를 박아뒀다.
     */
    const val NAMESPACE = "inmc"

    /** 마지막으로 그릴 때 붙어 있던 인챈트의 지문([enchantSignature]). */
    @Suppress("DEPRECATION")
    val ENCHANT_SIGNATURE = NamespacedKey(NAMESPACE, "enchants_drawn")

    @Suppress("DEPRECATION")
    private val ID_KEY = NamespacedKey(NAMESPACE, "item_id")

    /** 이 스택이 우리 아이템이면 그 id. 아니면 null. **핫 패스다 — 전투마다 불린다.** */
    private val RANDOM = Random()

    fun identify(stack: ItemStack?): String? {
        if (stack == null || stack.type.isAir) return null
        val meta = stack.itemMeta ?: return null
        return meta.persistentDataContainer.get(ID_KEY, PersistentDataType.STRING)
    }


    /**
     * 새로 만든다. 이 아이템의 몫([ItemInstance] — 굴린 능력치·수식어)을 굴리고 [render] 로 그린다.
     *
     * @param revision 정의의 지문. 나중에 정의가 바뀌었는지 알아보는 데 쓴다([ItemRegistry.revision]).
     */
    fun create(definition: CustomItem, amount: Int = 1, revision: Long = 0L, random: Random = RANDOM, lookup: Lookup = Lookup.NONE): ItemStack {
        val stack = ItemStack(definition.material)
        stack.amount = amount.coerceIn(1, definition.material.maxStackSize.coerceAtLeast(1))
        stack.editMeta { it.persistentDataContainer.set(ID_KEY, PersistentDataType.STRING, definition.id) }
        val instance = ItemInstance.roll(definition, random).copy(revision = revision)
        render(stack, definition, instance, lookup)
        if (!instance.unidentified) applyCustomEnchants(stack, definition)
        return stack
    }

    /** 그린 **뒤에**. 인첸트 플러그인이 자기 줄을 로어 위에 얹고 아이템에 기록한다. 미확인이면 감정할 때 붙인다. */
    fun applyCustomEnchants(stack: ItemStack, definition: CustomItem) {
        for ((id, level) in definition.customEnchants) CustomEnchantHook.apply(stack, id, level)
    }

    /**
     * 정의대로 다시 그린다. 이 아이템의 몫과 **플레이어가 얻은 것은 그대로** 둔다 — 내구도, 모루로 붙인
     * 바닐라 인챈트, 부여서로 붙인 커스텀 인첸트. 정의의 인챈트는 그보다 낮으면 올려 준다.
     *
     * 이름·설명·능력치 속성·모델·숨김은 정의가 정한다. 이미 나간 아이템을 정의에 맞추는 길이 이것 하나다
     * (명령어 갱신·자동 갱신).
     *
     * 로어는 **덩이가 순서대로** — 등급/종류 → 관리자가 쓴 설명 → 능력치 → 기능. MMOItems 를 비롯한
     * 대부분의 아이템 플러그인이 쓰는 관례고, 플레이어가 "이거 뭐 하는 거지"를 찾는 자리가 일정해야 한다.
     */
    fun render(stack: ItemStack, definition: CustomItem, instance: ItemInstance = ItemInstance.read(stack), lookup: Lookup = Lookup.NONE) {
        val totals = StatCalc.total(definition, instance, lookup)
        val meta = stack.itemMeta ?: return
        instance.write(meta.persistentDataContainer)

        // 미확인이면 정체(이름·능력치·인챈트·속성)를 감춘다. 모양과 등급 색만 남는다 — 무엇을 주웠는지는 몰라도 얼마나 귀한지는 보인다.
        val hidden = instance.unidentified
        val table = definition.upgrade.table(lookup)
        val tier = tierOf(definition, instance, lookup)
        val plain = name(definition, instance)
        val name = when {
            hidden -> "미확인 " + (lookup.type(definition) ?: TypeDef.of(definition.type)).name
            // 이름을 안 적은 아이템도 강화하면 "+3" 은 붙어야 한다 — 바닐라 이름을 번역 열쇠로 불러 붙인다.
            plain.isBlank() && instance.level > 0 -> "<lang:" + definition.material.translationKey() + "> +" + instance.level
            else -> plain
        }
        meta.displayName(if (name.isBlank()) null else Text.renderFlat(tier.color + name))
        // 인첸트 플러그인의 줄은 아이템의 PDC 에서 나온다 — 우리가 고치기 전에 받아 둔다.
        val custom = if (hidden) CustomEnchantHook.EnchantLines() else CustomEnchantHook.lines(stack)

        /*
         * 겉모습. **번호보다 `item_model` 이 먼저다.**
         *
         * 1.21.4 부터 아이템에 모델 이름을 직접 붙일 수 있고, 그러면 번호를 나눠 줄 필요가
         * 없다 — 아이템 id 가 곧 모델 이름이다. 번호 방식은 팩 두 개를 합칠 때 번호가
         * 겹치는 것이 가장 흔한 사고였고, 그 사고 자체가 없어진다.
         *
         * **번호는 더 쓰지 않는다**(사용자 결정 2026-09-28). 남은 번호(옛 정의 · 다른 플러그인에서 옮겨 온 아이템)는 다음
         * 리소스팩 빌드가 팩이 그리던 모양 그대로 `item_model` 로 옮기고 0 으로 만든다([com.inmc.customitems.pack.NumberMigration]).
         * 그때까지만 번호를 붙여 지금 올라가 있는 팩으로 보이게 한다.
         */
        // 강화 단계가 모양을 바꿨으면 그 단계의 것(단계별 모델은 팩 빌드가 `<이름>_lv<단계>` 로 만든다).
        val levelModel = table?.modelAt(instance.level)
        runCatching {
            meta.setItemModel(
                when {
                    levelModel != null -> NamespacedKey(NAMESPACE, com.inmc.customitems.pack.PackAssets.levelId(definition, levelModel.first))
                    definition.itemModel.isNotBlank() -> NamespacedKey.fromString(definition.itemModel)
                    com.inmc.customitems.pack.PackAssets.needsPack(definition) -> NamespacedKey(NAMESPACE, definition.resourceId)
                    else -> null
                },
            )
        }
        @Suppress("DEPRECATION")
        meta.setCustomModelData(table?.customModelDataAt(instance.level) ?: definition.customModelData.takeIf { it > 0 })
        meta.isUnbreakable = definition.unbreakable
        // 여러 번 쓰는 소모품은 남은 횟수가 한 개마다 달라 겹치면 안 된다. 배낭도 — 한 개마다 창고가 따로다(배낭 번호).
        runCatching { meta.setMaxStackSize(if ((definition.consume?.uses ?: 0) > 1 || definition.isBackpack) 1 else definition.components.maxStack.takeIf { it > 0 }) }
        components(meta, definition.components)
        // 1.20.5 부터 아이템마다 최대 내구도를 조절할 수 있다. 낡은 서버에서는
        // 그냥 건너뛴다 — 여기서 터지면 아이템 자체가 안 만들어진다.
        if (meta is Damageable) runCatching { meta.setMaxDamage(definition.maxDurability.takeIf { it > 0 }) }

        // 인챈트 레벨 상한을 무시한다 — 관리자가 날카로움 10 을 적었으면 그렇게 나와야 한다.
        for ((enchantName, level) in if (hidden) emptyMap() else definition.enchants) {
            val enchant = CustomItem.matchEnchant(enchantName) ?: continue
            if (meta.getEnchantLevel(enchant) < level) meta.addEnchant(enchant, level, true)
        }
        meta.setEnchantmentGlintOverride(if (definition.glow) true else null)

        meta.removeItemFlags(*ItemFlag.entries.toTypedArray())
        for (flagName in definition.flags) CustomItem.matchFlag(flagName)?.let { meta.addItemFlags(it) }

        // 속성은 **이 아이템의** 값으로 — 굴린 값·수식어가 들어 있다. 우리 것만 지우고 다시 단다.
        meta.attributeModifiers?.entries()?.filter { it.value.key.namespace == NAMESPACE }?.forEach { meta.removeAttributeModifier(it.key, it.value) }
        val slot = definition.slotGroup()
        var addedAttribute = false
        // 부적·유물은 가방에서 효과가 나므로 바닐라 속성을 아이템에 달지 않는다 — 사람에게 직접 건다(StatService).
        // 달면 손에 들었을 때 한 번 더 붙는다. 장착 칸에서만 효과가 나는 장신구도 같다 — 달면 손에 들었을 때 바닐라가 붙인다.
        for ((stat, value) in if (hidden || definition.type.carried || !definition.worksOutsideSlots(lookup.inventoryEffects())) emptyMap() else totals) {
            val attribute = stat.attribute() ?: continue
            meta.addAttributeModifier(
                attribute,
                AttributeModifier(NamespacedKey(NAMESPACE, definition.resourceId + "_" + stat.id), value, AttributeModifier.Operation.ADD_NUMBER, slot),
            )
            addedAttribute = true
        }
        /*
         * 툴팁은 **이름 → 종류 → 인첸트 → 능력치 → (강화·세트·소켓·요구 조건) → 등급 → 설명** 한 줄기로 우리가 적는다.
         * 바닐라는 인챈트를 로어 위에, "주로 사용하는 손에 있을 때"(속성)를 로어 아래에 그려 이 순서를 깨므로 둘 다 숨기고,
         * 그 내용(인챈트·기본 공격력/속도/방어)을 로어의 제자리에 옮긴다. 관리자가 숨김(flags)을 골랐으면 옮기지도 않는다.
         */
        val showEnchants = !hidden && ItemFlag.HIDE_ENCHANTS.name !in definition.flags
        val showAttributes = !hidden && ItemFlag.HIDE_ATTRIBUTES.name !in definition.flags
        val enchantLines = if (showEnchants) vanillaEnchantLines(meta.enchants) + custom.enchants else emptyList()
        val defaults = if (showAttributes && !addedAttribute) defaultStats(definition.material) else emptyMap()
        val handHeld = slot == org.bukkit.inventory.EquipmentSlotGroup.MAINHAND
        val lore = if (hidden) unidentifiedLore(definition, lookup) else buildLore(definition, totals, instance, lookup, enchantLines, custom.status, defaults, handHeld)
        meta.lore(lore.map { Text.renderFlat(it) })
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)
        meta.persistentDataContainer.set(ENCHANT_SIGNATURE, org.bukkit.persistence.PersistentDataType.STRING, enchantSignature(meta.enchants, CustomEnchantHook.levels(stack)))

        stack.itemMeta = meta
        // 메타를 다 쓴 **뒤에** — 메타를 쓰면서 부품이 덮이지 않게.
        vanillaUse(stack, definition)
    }

    /**
     * 바닐라 먹기·마시기 막기([CustomItem.preventVanillaUse]) — 먹는 부품(`consumable`)을 떼면 바닐라가 먹기를 **시작조차** 못 한다.
     * 먹는 모습만 나오고 끝에 안 먹히는 것(사건 취소)보다 낫다. 끄면 재질 기본값으로 되돌린다 — 떼어 둔 것만(우리가 붙인 적 없는 부품은 그대로).
     * 놓기는 부품으로 못 막아 `InteractListener` 가 `BlockPlaceEvent` 를 막는다.
     */
    @Suppress("UnstableApiUsage")
    private fun vanillaUse(stack: ItemStack, definition: CustomItem) {
        runCatching {
            val consumable = io.papermc.paper.datacomponent.DataComponentTypes.CONSUMABLE
            if (definition.preventVanillaUse) stack.unsetData(consumable)
            else if (stack.isDataOverridden(consumable)) stack.resetData(consumable)
        }
    }

    /** 바닐라 인챈트 줄 — 이름·레벨은 번역 열쇠로(서버 말이 아니라 보는 사람의 말로). 저주는 빨갛게. */
    private fun vanillaEnchantLines(enchants: Map<org.bukkit.enchantments.Enchantment, Int>): List<String> = enchants.entries
        .sortedBy { it.key.key().value() }
        .map { (enchant, level) ->
            val color = if (enchant.isCursed) "red" else "gray"
            val levelText = if (enchant.maxLevel == 1 && level == 1) "" else " " + if (level in 1..10) "<lang:enchantment.level.$level>" else level.toString()
            "<$color><lang:" + enchant.translationKey() + ">" + levelText + "</$color>"
        }

    /** 재질의 기본 속성(곡괭이의 공격력 5·속도 -2.8, 갑옷의 방어 등)을 우리 능력치로. 우리 속성을 하나라도 달면 바닐라가 이것을 버린다. */
    private fun defaultStats(material: org.bukkit.Material): Map<Stat, Double> {
        val modifiers = runCatching { material.defaultAttributeModifiers }.getOrNull() ?: return emptyMap()
        val out = LinkedHashMap<Stat, Double>()
        for ((attribute, modifier) in modifiers.entries()) {
            if (modifier.operation != AttributeModifier.Operation.ADD_NUMBER) continue
            val stat = Stat.entries.firstOrNull { it.attribute() == attribute } ?: continue
            out[stat] = Spread.round((out[stat] ?: 0.0) + modifier.amount)
        }
        out.values.removeIf { it == 0.0 }
        return out
    }

    /** 붙은 인챈트(바닐라·커스텀)의 지문. 바뀌면 자동 갱신이 로어를 다시 그린다 — 모루·부여대·명령어로 붙인 것도 로어에 오게. */
    fun enchantSignature(vanilla: Map<org.bukkit.enchantments.Enchantment, Int>, custom: Map<String, Int>): String =
        (vanilla.entries.map { it.key.key().asString() + "=" + it.value } + custom.entries.map { "+" + it.key + "=" + it.value }).sorted().joinToString(",")

    fun enchantSignature(stack: ItemStack): String =
        enchantSignature(stack.itemMeta?.enchants.orEmpty(), CustomEnchantHook.levels(stack))

    /** 마지막으로 그릴 때의 인챈트 지문. 없으면 null(옛 아이템). */
    fun renderedEnchants(stack: ItemStack): String? =
        stack.itemMeta?.persistentDataContainer?.get(ENCHANT_SIGNATURE, org.bukkit.persistence.PersistentDataType.STRING)

    /** 강화 단계가 정한 등급. 안 정했으면 아이템 기본 등급. */
    fun tierOf(definition: CustomItem, instance: ItemInstance, lookup: Lookup): Tier =
        definition.upgrade.table(lookup)?.tierAt(instance.level) ?: definition.tier

    fun unidentifiedLore(definition: CustomItem, lookup: Lookup = Lookup.NONE): List<String> = listOf(
        (lookup.type(definition) ?: TypeDef.of(definition.type)).header(),
        "",
        "<gray>정체를 알 수 없는 아이템입니다.</gray>",
        "<dark_gray>감정서를 끌어다 놓으면 밝혀집니다.</dark_gray>",
        "",
        definition.tier.badge(),
    )

    /**
     * 1.21 부품. 서버·버전이 모르는 부품은 그것만 건너뛴다 — 여기서 터지면 아이템 자체가 안 만들어진다.
     * 색·장식·머리는 **정의에 있을 때만** 덮는다. 플레이어가 염색하거나 장식을 단 것을 다시 그릴 때 날리지 않는다.
     */
    private fun components(meta: org.bukkit.inventory.meta.ItemMeta, parts: Components) {
        runCatching { meta.setTooltipStyle(parts.tooltipStyle.takeIf { it.isNotBlank() }?.let { NamespacedKey.fromString(it) }) }
        runCatching { meta.isHideTooltip = parts.hideTooltip }
        runCatching { meta.isGlider = parts.glider }
        runCatching { meta.isFireResistant = parts.fireResistant }
        runCatching {
            val slot = Components.equipmentSlot(parts.equipSlot)
            meta.setEquippable(slot?.let { meta.equippable.apply { this.slot = it } })
        }
        Components.parseColor(parts.color)?.let { rgb ->
            val color = org.bukkit.Color.fromRGB(rgb)
            when (meta) {
                is org.bukkit.inventory.meta.LeatherArmorMeta -> meta.setColor(color)
                is org.bukkit.inventory.meta.PotionMeta -> meta.color = color
            }
        }
        if (meta is org.bukkit.inventory.meta.ArmorMeta) {
            val pattern = Registries.trimPattern(parts.trimPattern)
            val material = Registries.trimMaterial(parts.trimMaterial)
            if (pattern != null && material != null) meta.trim = org.bukkit.inventory.meta.trim.ArmorTrim(material, pattern)
        }
        if (meta is org.bukkit.inventory.meta.SkullMeta && parts.skull.isNotBlank()) runCatching {
            // 텍스처가 같으면 같은 머리로 겹치도록 uuid 를 텍스처에서 뽑는다.
            val profile = org.bukkit.Bukkit.createProfile(java.util.UUID.nameUUIDFromBytes(parts.skull.toByteArray()), "inmc")
            profile.setProperty(com.destroystokyo.paper.profile.ProfileProperty("textures", parts.skull))
            meta.playerProfile = profile
        }
    }

    /** 소켓 색의 이름. `any` 는 "아무". */
    fun socketLabel(color: String): String = if (color == GemSpec.ANY) "아무" else color

    private fun signed(value: Double): String = (if (value > 0) "+" else "") + trim(value)

    private fun trim(value: Double): String = if (value == Math.floor(value)) value.toLong().toString() else value.toString()

    /** 수식어와 강화 단계(`+3`)를 붙인 이름. 이름을 안 적었고 수식어도 없으면 빈 글(바닐라 이름 그대로). */
    fun name(definition: CustomItem, instance: ItemInstance): String {
        val applied = instance.modifiers.mapNotNull { id -> definition.modifiers.firstOrNull { it.id == id } }
        if (definition.displayName.isBlank() && applied.isEmpty()) return ""
        val prefix = applied.filter { !it.suffix }.joinToString("") { it.name + " " }
        val suffix = applied.filter { it.suffix }.joinToString("") { " " + it.name }
        val level = if (instance.level > 0) " +" + instance.level else ""
        return prefix + definition.displayName.ifBlank { definition.id } + suffix + level
    }

    /**
     * 로어를 만든다.
     *
     * 빈 줄은 **덩이가 실제로 있을 때만** 넣는다. 없는 칸 자리에 빈 줄이 남으면 아이템 설명에
     * 이유 없는 여백이 생긴다.
     */
    fun buildLore(
        definition: CustomItem,
        totals: Map<Stat, Double>,
        instance: ItemInstance = ItemInstance(),
        lookup: Lookup = Lookup.NONE,
        /** 인첸트 줄(바닐라 + 인첸트 플러그인). 종류 바로 아래에 온다. */
        enchantLines: List<String> = emptyList(),
        /** 인첸트 플러그인의 상태 줄(보호됨·영혼·킬 수). 강화·세트 쪽에 온다. */
        statusLines: List<String> = emptyList(),
        /** 우리 속성을 안 단 아이템의 재질 기본 속성(곡괭이 공격력 등). 능력치에 섞어 보인다. */
        defaults: Map<Stat, Double> = emptyMap(),
        /** 손에 드는 것 — 공격력·공격 속도를 바닐라처럼 맨손 기준값을 더한 값으로 보인다. */
        handHeld: Boolean = false,
    ): List<String> = buildList {
        add((lookup.type(definition) ?: TypeDef.of(definition.type)).header())
        addAll(carryLines(definition, definition.worksOutsideSlots(lookup.inventoryEffects())))
        addAll(enchantLines)

        // 능력치는 목록 순서가 아니라 enum 순서로. 칼마다 공격력이 다른 줄에 있으면 읽기 어렵다.
        // 강화·보석·수식어로 늘어난 몫은 옆에 (+n), 기본에 없던 능력치면 초록 줄로.
        val shown = LinkedHashMap(totals)
        for ((stat, value) in defaults) shown[stat] = Spread.round((shown[stat] ?: 0.0) + value)
        val stats = Stat.entries.filter { (shown[it] ?: 0.0) != 0.0 }
        if (stats.isNotEmpty()) {
            add("")
            for (stat in stats) {
                val base = definition.stats[stat]?.let { StatCalc.rolled(definition, instance, stat, it) }
                val bonus = if (base == null) 0.0 else Spread.round((totals[stat] ?: 0.0) - base)
                add(statLine(stat, shown.getValue(stat), bonus, added = base == null && stat !in defaults, absolute = handHeld && stat in ABSOLUTE))
            }
        }

        // 강화·공격 방식·소켓·보석·소모품·기능·요구 조건·세트·인첸트 상태 — 능력치와 등급 사이.
        val upgrade = upgradeLines(definition, instance, lookup)
        if (upgrade.isNotEmpty() || definition.style != AttackStyle.NONE) add("")
        addAll(upgrade)
        if (definition.style != AttackStyle.NONE) {
            add("<gray>⚔ " + definition.style.display + " — " + definition.style.description + "</gray>")
        }

        // 소켓: 박힌 보석은 이름과 더하는 능력치, 빈 소켓은 색.
        if (definition.sockets.isNotEmpty()) {
            add("")
            for ((index, color) in definition.sockets.withIndex()) {
                val gem = instance.gem(index)?.let(lookup.item)
                add(
                    if (gem == null) "<dark_gray>◇ 빈 소켓 <gray>(" + socketLabel(color) + ")</gray></dark_gray>"
                    else "<aqua>◆ </aqua>" + gem.tier.color + gem.label() + " <gray>" +
                        gem.stats.entries.joinToString(" ") { (stat, value) -> Text.plain(Text.render(stat.line(value))) } + "</gray>",
                )
            }
        }

        val gemSpec = definition.gem
        if (gemSpec != null) {
            add("")
            add("<aqua>보석 <gray>· " + socketLabel(gemSpec.color) + " 소켓 · 성공률 " + trim(gemSpec.chance) + "%</gray></aqua>")
            add("<dark_gray>소켓이 있는 아이템 위에 끌어다 놓으세요.</dark_gray>")
        }

        val consume = definition.consume
        if (consume != null) {
            add("")
            val restores = buildList {
                if (consume.health != 0.0) add("체력 " + signed(consume.health))
                if (consume.food != 0) add("배고픔 " + signed(consume.food.toDouble()))
                if (consume.saturation != 0.0) add("포만감 " + signed(consume.saturation))
            }
            if (restores.isNotEmpty()) add("<green>사용: " + restores.joinToString(" · ") + "</green>")
            if (consume.unsocket) add("<aqua>소켓 아이템 위에 놓으면 마지막 보석을 빼냅니다.</aqua>")
            consume.upgrade?.let { stone ->
                add("<gold>강화할 아이템 위에 놓으면 한 단계 강화를 시도합니다.</gold>")
                if (stone.chances.isNotEmpty()) {
                    add("<gray>성공 확률: " + Tier.entries.filter { it in stone.chances }.joinToString(" · ") { it.color + it.display + " <white>" + trim(stone.chances.getValue(it)) + "%</white><gray>" } + "</gray>")
                } else if (stone.chance > 0.0) {
                    add("<gray>성공 확률 <white>" + trim(stone.chance) + "%</white></gray>")
                }
                if (stone.bonus != 0.0) add("<gray>성공 확률 " + signed(stone.bonus) + "%p</gray>")
                if (stone.minLevel > 0 || stone.maxLevel != null) {
                    add("<gray>+" + stone.minLevel + " ~ " + (stone.maxLevel?.let { "+$it" } ?: "끝") + " 단계에서만</gray>")
                }
            }
            if (consume.evolve != null) add("<light_purple>최대 강화한 아이템 위에 놓으면 진화합니다.</light_purple>")
            if (consume.repair > 0 || consume.repairPercent > 0.0) {
                val amount = listOfNotNull(consume.repair.takeIf { it > 0 }?.toString(), consume.repairPercent.takeIf { it > 0.0 }?.let { trim(it) + "%" })
                add("<aqua>아이템 위에 놓으면 내구도 " + amount.joinToString(" + ") + " 수리</aqua>")
            }
            if (consume.uses > 1) add("<gray>남은 횟수 <white>" + (instance.usesLeft ?: consume.uses) + "</white>/" + consume.uses + "</gray>")
            if (consume.cooldown > 0.0) add("<dark_gray>재사용 대기 " + trim(consume.cooldown) + "초</dark_gray>")
            if (!consume.dragOnly) add("<dark_gray>우클릭으로 씁니다.</dark_gray>")
        }

        if (definition.abilities.isNotEmpty()) {
            add("")
            for (ability in definition.abilities) add(ability.line())
        }

        val requirement = definition.requirement
        if (!requirement.isEmpty) {
            add("")
            if (requirement.level > 0) add("<red>요구 레벨 " + requirement.level + "</red>")
            if (requirement.permission.isNotBlank()) add("<red>권한이 있어야 씁니다</red>")
        }

        val set = lookup.set(definition.set)
        // 세트: 벌 수마다 무엇이 붙는지. 몇 벌을 입었는지는 사람마다 달라 로어에 적지 않는다.
        if (set != null && set.bonuses.isNotEmpty()) {
            add("")
            add("<green>" + set.name + " <dark_green>세트</dark_green></green>")
            for ((count, bonus) in set.bonuses.entries.sortedBy { it.key }) {
                for ((stat, value) in bonus.stats) add("<dark_gray>[" + count + "벌]</dark_gray> " + stat.line(value))
                for ((potion, level) in bonus.potions) add("<dark_gray>[" + count + "벌]</dark_gray> <gray>" + potion + " " + level + "</gray>")
                if (bonus.effects.isNotEmpty()) {
                    add("<dark_gray>[" + count + "벌]</dark_gray> " + CustomEnchantHook.describeEffects(bonus.effects).ifBlank { "<light_purple>특수 효과</light_purple>" })
                }
            }
        }

        if (statusLines.isNotEmpty()) {
            add("")
            addAll(statusLines)
        }

        add("")
        add(tierOf(definition, instance, lookup).badge())

        if (definition.lore.isNotEmpty()) {
            add("")
            addAll(definition.lore)
        }
    }

    /**
     * 부적·유물·장신구가 **어디서 효과가 나는지**와 **겹치는 규칙** — 종류 바로 밑에 표찰 한 줄씩(사용자 요청 2026-09-30: 더 세련되게).
     * 손에서도 되는 장신구는 적을 것이 없다.
     */
    private fun carryLines(definition: CustomItem, outside: Boolean): List<String> = buildList {
        val carried = definition.type == ItemType.TALISMAN || definition.type == ItemType.RELIC
        if (carried || (definition.type == ItemType.ACCESSORY && !outside)) {
            add(if (outside) tag(WHERE, "소지 효과", "가방에 지니기만 해도 발휘") else tag(WHERE, "장착 효과", "장착 칸(/장비)에 끼워야 발휘"))
        }
        if (definition.type == ItemType.TALISMAN && definition.noDuplicate) add(tag(LIMIT, "중복 불가", "같은 부적은 가장 높은 강화 하나만"))
        if (definition.type == ItemType.RELIC) add(tag(LIMIT, "단 하나", "여러 유물 중 가장 높은 등급 하나만"))
        if (definition.isBackpack) add(tag(WHERE, "배낭", "우클릭으로 열기 · " + definition.backpack + "칸 · 장착하면 /배낭"))
    }

    /** 표찰 한 줄 — 그라데이션 이름표 · 옅은 설명. */
    private fun tag(colors: String, label: String, detail: String): String =
        "<gradient:" + colors + ">" + label + "</gradient> <dark_gray>—</dark_gray> <gray>" + detail + "</gray>"

    /** 효과가 나는 곳(물빛) · 겹치는 규칙(노을빛). */
    private const val WHERE = "#7ee0f0:#9d9bff"
    private const val LIMIT = "#ffc46b:#ff8a8a"

    /** 공격력·공격 속도는 바닐라처럼 맨손 기준값(1 · 4)을 더해 보인다 — 곡괭이의 "6 공격 피해" 가 그대로 옮겨 오게. */
    private val ABSOLUTE = mapOf(Stat.ATTACK_DAMAGE to 1.0, Stat.ATTACK_SPEED to 4.0)

    /** 능력치 한 줄. [bonus] 가 있으면 옆에 `(+n)`, 기본에 없던 것([added])이면 초록으로. */
    fun statLine(stat: Stat, value: Double, bonus: Double = 0.0, added: Boolean = false, absolute: Boolean = false): String {
        fun number(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else Spread.round(v).toString()
        val main = if (absolute) {
            "<gray>" + number(Spread.round(value + ABSOLUTE.getValue(stat))) + stat.unit + " " + stat.display + "</gray>"
        } else if (added) {
            stat.line(value).replace("<gray>", "<green>").replace("</gray>", "</green>")
        } else {
            stat.line(value)
        }
        if (bonus == 0.0 || added) return main
        return main + " <green>(" + (if (bonus > 0) "+" else "") + number(bonus) + stat.unit + ")</green>"
    }

    /** 강화 단계와 다음 단계(또는 진화). 강화도 진화도 없으면 빈 목록. */
    private fun upgradeLines(definition: CustomItem, instance: ItemInstance, lookup: Lookup): List<String> = buildList {
        val table = definition.upgrade.table(lookup)
        val evolution = definition.upgrade.evolution
        if (table != null && table.maxLevel > 0) {
            val level = instance.level.coerceAtMost(table.maxLevel)
            add("<gold>강화 <white>+" + level + "</white><gray> / " + table.maxLevel + "</gray></gold>")
            val next = table.step(level + 1)
            if (next != null) {
                // 확률 100%·실패해도 그대로면 강화석이 확률을 정하는 것이다(MMOItems 식) — 100% 라고 적으면 거짓말이 된다.
                if (next.chance < 100.0 || next.fail != FailResult.KEEP) {
                    add("<dark_gray>다음 강화 성공 " + trim(next.chance) + "% · 실패하면 " + next.fail.display + "</dark_gray>")
                }
                return@buildList
            }
        }
        if (evolution != null) {
            val target = lookup.item(evolution.into)?.label() ?: evolution.into
            add("<light_purple>진화 → <white>" + target + "</white></light_purple>" + if (table != null && table.maxLevel > 0) " <dark_gray>(최대 강화)</dark_gray>" else "")
        }
    }

    /**
     * 손에 든 것을 정의로 되읽는다. 등록 화면이 쓴다.
     *
     * **읽을 수 있는 것만 읽는다.** NBT 를 통째로 베끼면 남의 플러그인 데이터까지 끌고 와
     * 우리가 다시 만들 수 없는 아이템이 된다 — 그건 스냅샷이 할 일이고 core 가 이미 한다.
     *
     * 로어는 가져오지 않는다. 우리가 등급·능력치 줄을 붙이므로 원본 로어를 그대로 두면
     * 같은 내용이 두 번 적힌다.
     */
    fun capture(id: String, stack: ItemStack): CustomItem {
        val meta = stack.itemMeta
        return CustomItem(
            id = id.lowercase(),
            material = stack.type,
            type = ItemType.guess(stack.type),
            displayName = meta?.displayName()?.let { Text.plain(it) }.orEmpty(),
            enchants = stack.enchantments.entries.associate { it.key.key().value() to it.value },
            flags = meta?.itemFlags?.map { it.name }?.toSet().orEmpty(),
            unbreakable = meta?.isUnbreakable ?: false,
        )
    }
}
