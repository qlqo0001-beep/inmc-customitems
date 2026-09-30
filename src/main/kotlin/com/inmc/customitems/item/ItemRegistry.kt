package com.inmc.customitems.item

import com.inmc.customitems.CustomItems
import kr.inmc.core.store.YamlFileStore
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack

/**
 * 등록된 아이템 전부. `items.yml` 한 파일에 담긴다.
 *
 * 조회는 id 하나로 끝난다 — [ItemBuilder] 가 PDC 에 id 를 찍어두므로 재질로 거를 필요도
 * 없다. 다른 INMC 플러그인들의 레지스트리가 재질 색인을 갖는 것은 **남이 만든 아이템**을
 * 알아보기 위해서인데, 여기서는 우리가 만든 것만 알아보면 된다.
 */
class ItemRegistry(private val plugin: CustomItems) : YamlFileStore(
    io = plugin.io,
    path = listOf("items.yml"),
    header = "",
    what = "커스텀 아이템",
) {

    private val byId = LinkedHashMap<String, CustomItem>()

    /** id → 정의의 지문. 아이템에 적힌 지문과 다르면 그 아이템은 옛 정의로 그려진 것이다. */
    private val revisions = HashMap<String, Long>()

    val size: Int get() = byId.size

    fun all(): List<CustomItem> = byId.values.toList()

    fun ids(): List<String> = byId.keys.toList()

    fun get(id: String?): CustomItem? = id?.let { byId[it.lowercase()] }

    fun exists(id: String?): Boolean = get(id) != null

    /**
     * 지속 기능을 가진 아이템이 하나라도 있는지.
     *
     * 틱커가 매초 묻는다. 대부분의 서버가 false 일 테고, 그때는 접속자 목록조차 훑지 않고
     * 돌아간다. 목록이 바뀔 때만 다시 계산한다.
     */
    @Volatile
    var hasPassive: Boolean = false
        private set

    /** 이 스택이 우리 아이템이고, 그 정의가 아직 남아 있으면 그것. */
    fun identify(stack: ItemStack?): CustomItem? = get(ItemBuilder.identify(stack))

    /**
     * 쓸 수 있는 우리 아이템. **미확인이면 null** — 감정하기 전에는 능력치·기능·공격 방식이 돌지 않는다.
     * **사용 기간이 끝났어도 null**([Periods]) — 끝나는 시각을 그 자리에서 보므로 다음 점검을 기다리지 않고 바로 멈춘다.
     */
    fun usable(stack: ItemStack?): CustomItem? = identify(stack)?.takeIf { !ItemInstance.isUnidentified(stack) && !expired(it, stack) }

    /** 사용 기간이 끝난 우리 아이템인가(사라짐이든 효과 정지든). */
    fun isExpired(stack: ItemStack?): Boolean = identify(stack)?.let { expired(it, stack) } == true

    private fun expired(definition: CustomItem, stack: ItemStack?): Boolean =
        definition.period > 0 && Periods.expired(definition, ItemInstance.expiresAt(stack), System.currentTimeMillis())

    fun create(id: String?, amount: Int = 1): ItemStack? {
        val item = get(id) ?: return null
        // 제 표식이 있어야 동작하는 역할(인첸트의 가루 …)은 그 플러그인이 만든다 — 우리가 그리면 모양만 같은 가짜가 된다.
        for ((key, values) in item.roles) {
            val factory = kr.inmc.core.integration.ItemRoles.role(key)?.factory ?: continue
            factory(values, amount)?.let { return it }
        }
        return ItemBuilder.create(item, amount, revision(item.id), lookup = lookup)
    }

    /** 화면에 보여줄 한 개. 저장된 정의가 아니어도(편집 중인 사본) 된다. 미확인 아이템도 관리 화면에는 정체를 보인다. */
    fun preview(definition: CustomItem): ItemStack =
        // 사용 기간은 찍지 않는다 — 미리보기에는 "7일 (받은 때부터)" 처럼 길이가 보여야 한다.
        ItemBuilder.create(definition.copy(unidentified = false), 1, revision(definition.id), lookup = lookup, stamp = false)

    fun revision(id: String): Long = revisions[id.lowercase()] ?: 0L

    /** 세트·보석을 이 레지스트리와 세트 레지스트리에서 찾는다. */
    val lookup = Lookup(
        set = { plugin.sets.get(it) }, item = { get(it) }, upgrade = { plugin.upgrades.get(it) },
        inventoryEffects = { plugin.equipmentSettings.inventoryEffects },
        type = { plugin.types.of(it) },
        category = { plugin.categories.of(it) },
    )

    /**
     * 옛 정의로 그려진 우리 아이템이면 지금 정의로 다시 그린다. 그렸으면 true.
     * 몫(굴린 값·수식어)과 내구도·붙은 인챈트는 그대로다([ItemBuilder.render]).
     */
    fun refresh(stack: ItemStack?): Boolean {
        val definition = identify(stack) ?: return false
        val instance = ItemInstance.read(stack)
        val revision = revision(definition.id)
        // 기간이 생기기 전에 나간 아이템은 처음 눈에 들어온 지금부터 센다. 만료 모습과 지금 상태가 다르면 다시 그린다.
        val now = System.currentTimeMillis()
        val expires = instance.expires ?: if (definition.period > 0) Periods.stamp(definition.period, now) else null
        val expired = Periods.expired(definition, expires, now)
        if (instance.revision == revision && expires == instance.expires && expired == ItemInstance.drawnExpired(stack) &&
            ItemBuilder.renderedEnchants(stack!!) == ItemBuilder.enchantSignature(stack)
        ) return false
        ItemBuilder.render(stack!!, definition, instance.copy(revision = revision, expires = expires), lookup)
        return true
    }

    /**
     * 사용 기간이 끝나 **사라져야** 하는 것. 배낭은 여기서 치우지 않는다 — 안의 것을 돌려받을 사람이 있어야 해서
     * 그 사람의 점검([com.inmc.customitems.player.ExpiryService])이 치운다.
     */
    fun vanishes(stack: ItemStack?): Boolean {
        val definition = identify(stack) ?: return false
        return definition.expiry == Expiry.VANISH && !definition.isBackpack && expired(definition, stack)
    }

    /** 가방(또는 상자) 전체를 훑어 옛 것을 다시 그린다. `getItem` 이 복사본일 수 있어 다시 넣는다. */
    fun refresh(inventory: org.bukkit.inventory.Inventory) {
        for (index in 0 until inventory.size) {
            val stack = inventory.getItem(index) ?: continue
            if (hasPeriod && vanishes(stack)) inventory.setItem(index, null)
            else if (refresh(stack)) inventory.setItem(index, stack)
        }
    }

    /** 공용 강화 방식이 바뀌었다 — 그 방식을 쓰는 아이템의 능력치·모양이 달라지므로 지문을 다시 뜬다. */
    fun onUpgradeChanged(templateId: String) {
        for (item in byId.values) if (item.upgrade.own == null && item.upgrade.template == templateId) revisions[item.id] = fingerprint(item)
    }

    /** 가방에서 효과가 나는 아이템(부적·유물)이 하나라도 있는가. 없으면 능력치 계산이 가방을 훑지 않는다. */
    @Volatile
    var hasCarried: Boolean = false
        private set

    /** 서버의 "장착 칸 밖에서도 효과" 설정이 바뀌었다 — 그걸 따르는 장신구·부적·유물의 로어·속성이 달라지므로 지문을 다시 뜬다. */
    fun onEquipmentRuleChanged() {
        for (item in byId.values) revisions[item.id] = fingerprint(item)
    }

    /** 종류의 이름·기호가 바뀌었거나 종류가 생기고 지워졌다 — 로어 첫 줄이 달라지므로 지문을 다시 뜬다. */
    fun onTypesChanged() {
        for (item in byId.values) revisions[item.id] = fingerprint(item)
    }

    /** 소분류가 바뀌었다 — 로어의 종류 줄이 소분류 이름인 아이템은 그 줄이 달라지므로 지문을 다시 뜬다. */
    fun onCategoriesChanged() {
        for (item in byId.values) revisions[item.id] = fingerprint(item)
    }

    /** 세트가 바뀌었다 — 그 세트의 아이템은 로어(세트 효과)가 달라지므로 지문을 다시 뜬다. */
    fun onSetChanged(setId: String) {
        for (item in byId.values) if (item.set == setId) revisions[item.id] = fingerprint(item)
    }

    // --- 변경 -------------------------------------------------------------------

    fun put(item: CustomItem) {
        val before = byId[item.id]?.roles.orEmpty()
        byId[item.id] = item
        revisions[item.id] = fingerprint(item)
        reindex()
        markDirty()
        rolesChanged(before, item.roles)
    }

    fun remove(id: String): Boolean {
        val removed = byId.remove(id.lowercase())
        revisions.remove(id.lowercase())
        if (removed != null) {
            reindex()
            markDirty()
            rolesChanged(removed.roles, emptyMap())
        }
        return removed != null
    }

    /** 역할이 바뀐 것을 그 역할을 쓰는 플러그인에 알린다(core `ItemRoles`) — 그쪽이 다시 읽는다. */
    private fun rolesChanged(before: Map<String, Map<String, String>>, after: Map<String, Map<String, String>>) {
        for (role in before.keys + after.keys) if (before[role] != after[role]) kr.inmc.core.integration.ItemRoles.changed(role)
    }

    private fun reindex() {
        triggers = byId.values.flatMap { item -> item.abilities.map { it.trigger } }.toSet()
        hasPassive = com.inmc.customitems.ability.Trigger.PASSIVE in triggers
        hasCarried = byId.values.any { it.type.carried }
        hasPeriod = byId.values.any { it.period > 0 }
        hasBackpacks = byId.values.any { it.isBackpack }
        hasBlocks = byId.values.any { it.block != null }
        blockStates = byId.values.mapNotNull { item ->
            item.block?.takeIf { it.kind.usesState && it.state.isNotBlank() }?.let { it.kind.id + "|" + it.state to item.id }
        }.toMap()
    }

    /** 배낭이 하나라도 있는가. 없으면 줍기·판매·열쇠가 배낭을 찾지 않는다. */
    @Volatile
    var hasBackpacks: Boolean = false
        private set

    /** 사용 기간이 있는 아이템이 하나라도 있는가. 없으면 만료 점검이 접속자를 훑지 않는다. */
    @Volatile
    var hasPeriod: Boolean = false
        private set

    /** 블록으로 놓이는 아이템이 하나라도 있는가. 없으면 블록 리스너가 사건마다 곧바로 돌아간다. */
    @Volatile
    var hasBlocks: Boolean = false
        private set

    /** (방식|상태) → 아이템 id. 놓인 블록의 상태로 어느 아이템인지 찾는다. */
    @Volatile
    private var blockStates: Map<String, String> = emptyMap()

    /** 그 상태를 차지한 아이템. */
    fun byBlockState(kind: BlockKind, state: String): CustomItem? = blockStates[kind.id + "|" + state]?.let { byId[it] }

    /** 그 방식에서 우리 아이템이 차지한 상태들. */
    fun blockStatesOf(kind: BlockKind): Set<String> =
        blockStates.keys.filter { it.startsWith(kind.id + "|") }.map { it.substringAfter('|') }.toSet()

    /** 어느 아이템엔가 붙은 발동 조건들. 웅크리기·점프처럼 잦은 사건은 이게 없으면 장비조차 안 본다. */
    @Volatile
    private var triggers: Set<com.inmc.customitems.ability.Trigger> = emptySet()

    fun hasTrigger(trigger: com.inmc.customitems.ability.Trigger): Boolean = trigger in triggers

    // --- 영속화 (둘 다 메인 스레드) ------------------------------------------------

    override fun read(config: YamlConfiguration) {
        byId.clear()
        revisions.clear()
        val root = config.getConfigurationSection("items") ?: return
        for (key in root.getKeys(false)) {
            val section = root.getConfigurationSection(key) ?: continue
            val item = CustomItem.load(key, section)
            if (item == null) {
                plugin.logger.warning("아이템 '$key' 을(를) 읽지 못했습니다 - material 값을 확인해주세요")
                continue
            }
            byId[item.id] = item
            revisions[item.id] = fingerprint(item)
        }
        reindex()
    }

    /**
     * 저장한 모양의 CRC. `hashCode` 는 enum 이 섞여 있어 재시작마다 달라지므로 쓸 수 없다 — 그러면 서버를
     * 켤 때마다 모든 아이템을 다시 그린다.
     */
    private fun fingerprint(item: CustomItem): Long {
        val yaml = YamlConfiguration()
        item.save(yaml.createSection(item.id))
        plugin.sets.get(item.set)?.save(yaml.createSection("__set"))
        if (item.upgrade.own == null) plugin.upgrades.get(item.upgrade.template)?.save(yaml.createSection("__upgrade"))
        // 켜져 있을 때(기본)는 적지 않는다 — 적으면 이 설정이 생긴 것만으로 모든 장신구·부적·유물을 다시 그린다.
        if (!item.worksOutsideSlots(plugin.equipmentSettings.inventoryEffects)) yaml.set("__outside", false)
        // 종류를 손대지 않았으면 적지 않는다 — 적으면 이 기능이 생긴 것만으로 모든 아이템을 다시 그린다.
        plugin.types.of(item).takeIf { it != TypeDef.of(item.type) }?.save(yaml.createSection("__type"))
        // 로어의 종류 줄이 소분류 이름일 때만 적는다 — 소분류를 켜고 끄거나 이름을 바꾸면 그 아이템들이 다시 그려진다.
        plugin.categories.of(item)?.takeIf { item.typeLabel.showsCategory(it) }?.let { yaml.set("__label", it.name) }
        return java.util.zip.CRC32().apply { update(yaml.saveToString().toByteArray(Charsets.UTF_8)) }.value
    }

    override fun write(config: YamlConfiguration) {
        val root = config.createSection("items")
        for (item in byId.values) item.save(root.createSection(item.id))
    }
}
