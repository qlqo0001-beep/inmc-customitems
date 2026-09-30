package com.inmc.customitems.player

import com.inmc.customitems.CustomItems
import com.inmc.customitems.gui.BackpackMenu
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.ItemBuilder
import com.inmc.customitems.item.ItemInstance
import com.inmc.customitems.util.Ph
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.type.DialogType
import kr.inmc.core.integration.CarriedStorage
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.Base64
import java.util.UUID

/**
 * 배낭 — 배낭 종류([CustomItem.isBackpack])를 우클릭하면 열리는, **아이템 한 개만의** 창고(사용자 결정 2026-09-30: 셜커 상자처럼
 * 아이템에 붙는다). 장착 칸의 배낭 줄에 끼운 것은 `/배낭 <번호>` — 번호는 **배낭 칸 순서**(채워진 칸만 앞에서부터), `/배낭` 은 1번.
 *
 * ## 내용물은 아이템이 아니라 서버 파일에
 * 아이템에는 **배낭 번호**(`inmc:backpack` = UUID)만 찍고 내용물은 `backpacks/<번호>.yml` 에 둔다. 크기가 자유라(수백 칸) 내용물을
 * 아이템 NBT 에 넣으면 그 아이템이 오갈 때마다 클라이언트로 가는 패킷이 부푼다. 번호만 들고 다니므로 **아이템이 복사돼도 내용물은
 * 늘지 않는다**(같은 창고를 가리킬 뿐이다). 번호는 처음 열거나 처음 넣을 때 찍는다 — 만들 때 찍으면 겹쳐 나온 묶음이 한 번호를 나눠 갖는다.
 *
 * ## 저장은 바꿀 때마다 즉시, 원자적으로 · 한 번에 한 사람
 * [EquipmentStore] 와 같은 이유로 바꿀 때마다 메인 스레드에서 `.tmp` → rename 으로 쓴다. 같은 배낭을 두 곳에서 고치면 복사되므로
 * **한 번에 한 사람만** 열고, 누가 열어 둔 배낭은 판매·열쇠·자동 수납도 건드리지 않는다.
 *
 * ## 가방처럼 센다
 * 장착한 배낭과 가방에 든 배낭(번호 순서: 장착 칸 → 가방)의 내용물을 core [CarriedStorage] 로 내놓는다 — 상점 판매·랜덤박스 열쇠·
 * 화폐 실물처럼 가방에서 세고 빼는 곳이 배낭까지 본다(사용자 결정 2026-09-30). 드랍 자동 수납이 켜진 배낭은 주운 물건을 가방보다 먼저 받는다.
 */
class Backpacks(private val custom: CustomItems) : CarriedStorage.Provider {

    /** 배낭 하나가 있는 자리 — 번호를 찍으면 그 자리에 다시 넣어야 해서 같이 들고 다닌다. */
    sealed interface Where {
        /** 장착 칸 배낭 줄의 [index] 번 칸. */
        data class Equipped(val index: Int) : Where

        /** 가방의 [slot] 번 칸(단축바·왼손 포함). */
        data class Bag(val slot: Int) : Where
    }

    data class Found(val where: Where, val stack: ItemStack, val definition: CustomItem)

    private val folder: File get() = custom.io.file("backpacks")

    /** 배낭 번호 → 지금 열어 둔 사람. */
    private val viewers = HashMap<UUID, UUID>()

    /** 배낭 번호 → 내용물. 판매·열쇠·줍기가 자주 물어 파일을 매번 읽지 않는다. 쓸 때 같이 고친다. */
    private val cache = object : LinkedHashMap<UUID, Map<Int, ItemStack>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<UUID, Map<Int, ItemStack>>?): Boolean = size > CACHE
    }

    fun idOf(stack: ItemStack?): UUID? {
        val raw = stack?.takeIf { !it.type.isAir && it.hasItemMeta() }?.itemMeta?.persistentDataContainer?.get(KEY, PersistentDataType.STRING) ?: return null
        return runCatching { UUID.fromString(raw) }.getOrNull()
    }

    /** 번호가 없으면 새로 찍는다. 찍었으면 두 번째 값이 true — 부르는 쪽이 그 칸에 다시 넣어야 한다. */
    private fun stamp(stack: ItemStack): Pair<UUID, Boolean> {
        idOf(stack)?.let { return it to false }
        val id = UUID.randomUUID()
        stack.editMeta { it.persistentDataContainer.set(KEY, PersistentDataType.STRING, id.toString()) }
        return id to true
    }

    // --- 크기·자동 수납(강화·보석이 더한다) --------------------------------------------------

    /** 이 배낭 한 개의 칸 수 — 기본 크기 + 강화 단계들이 더한 칸([com.inmc.customitems.item.UpgradeTable.backpackAt]). */
    fun size(definition: CustomItem, stack: ItemStack?): Int {
        val bonus = definition.upgrade.table(custom.items.lookup)?.backpackAt(ItemInstance.read(stack).level) ?: 0
        return (definition.backpack + bonus).coerceIn(1, BackpackLayout.MAX)
    }

    /** 드랍 자동 수납이 켜졌나 — 배낭의 기본 옵션 · 강화 단계 · 박힌 보석 가운데 하나라도(사용자 요청 2026-09-30). */
    fun autoPickup(definition: CustomItem, stack: ItemStack?): Boolean {
        if (definition.autoPickup) return true
        val instance = ItemInstance.read(stack)
        if (definition.upgrade.table(custom.items.lookup)?.autoPickupAt(instance.level) == true) return true
        return instance.gems.any { it.isNotEmpty() && custom.items.get(it)?.autoPickup == true }
    }

    // --- 찾기 ------------------------------------------------------------------------------

    /** 장착 칸 배낭 줄의 배낭들 — 앞 칸부터, 채워진 열린 칸만. 순서가 곧 `/배낭` 번호다. */
    fun equipped(player: Player): List<Found> {
        val group = EquipmentStore.Group.BACKPACK
        return (0 until custom.equipment.capacity(player, group)).mapNotNull { index ->
            val stack = custom.equipment.get(player.uniqueId, group, index) ?: return@mapNotNull null
            custom.items.usable(stack)?.takeIf { it.isBackpack }?.let { Found(Where.Equipped(index), stack, it) }
        }
    }

    /** 이 사람이 가진 배낭 전부 — 장착 칸(번호 순서) 다음 가방. 겹친 옛 묶음은 번호를 나눠 가져 빼놓는다. */
    fun carried(player: Player): List<Found> {
        if (!custom.items.hasBackpacks) return emptyList()
        val bag = player.inventory
        return equipped(player) + BAG_SLOTS.mapNotNull { slot ->
            val stack = bag.getItem(slot)?.takeIf { it.amount == 1 } ?: return@mapNotNull null
            custom.items.usable(stack)?.takeIf { it.isBackpack }?.let { Found(Where.Bag(slot), stack, it) }
        }
    }

    /** 번호를 찍어(없으면) 그 자리에 다시 넣고 번호를 준다. */
    private fun ensureId(player: Player, found: Found): UUID {
        val copy = found.stack.clone()
        val (id, stamped) = stamp(copy)
        if (stamped) {
            when (val where = found.where) {
                is Where.Equipped -> custom.equipment.put(player.uniqueId, EquipmentStore.Group.BACKPACK, where.index, copy)
                is Where.Bag -> player.inventory.setItem(where.slot, copy)
            }
        }
        return id
    }

    // --- 열기 --------------------------------------------------------------------------------

    /** 손에 든 배낭. 배낭이 아니면 false(부르는 쪽이 평소 우클릭으로). */
    fun openHand(player: Player): Boolean {
        val stack = player.inventory.itemInMainHand
        val definition = custom.items.usable(stack)?.takeIf { it.isBackpack } ?: return false
        // 겹친 묶음은 한 번호를 나눠 가진다 — 하나씩 들어야 연다(배낭은 겹치지 않게 그려지지만 옛 묶음이 있을 수 있다).
        if (stack.amount > 1) {
            custom.messages.send(player, "backpack-one")
            return true
        }
        val (id, stamped) = stamp(stack)
        if (stamped) player.inventory.setItemInMainHand(stack)
        open(player, id, definition, stack)
        return true
    }

    /** 장착 칸 배낭 줄의 [index] 번 칸(`/장비` 에서 우클릭). */
    fun openEquipped(player: Player, index: Int) {
        val found = equipped(player).firstOrNull { (it.where as Where.Equipped).index == index } ?: return
        open(player, ensureId(player, found), found.definition, found.stack)
    }

    /** `/배낭 <번호>` — 번호는 1부터. */
    fun openNumber(player: Player, number: Int) {
        val bags = equipped(player)
        if (bags.isEmpty()) return custom.messages.send(player, "backpack-none")
        val found = bags.getOrNull(number - 1)
            ?: return custom.messages.send(player, "backpack-no-number", Ph.of().amount(number).value(bags.size.toString()))
        open(player, ensureId(player, found), found.definition, found.stack)
    }

    /**
     * 빠른 동작 키(G)의 [배낭] — 배낭이 하나면 곧바로 열고, 여럿이면 **맨 배낭 수만큼 버튼이 있는 창을 그때 만들어** 띄운다
     * (사용자 결정 2026-09-30). 첫 창은 응답을 기다리고 있으므로 없을 때도 창을 닫아 줘야 한다.
     */
    fun quickMenu(player: Player) {
        val bags = equipped(player)
        when (bags.size) {
            0 -> {
                player.closeDialog()
                custom.messages.send(player, "backpack-none")
            }
            1 -> {
                // 첫 창이 응답을 기다리고 있다 — 닫고 연다(열다가 막혀도 기다리는 창에 갇히지 않게).
                player.closeDialog()
                openNumber(player, 1)
            }
            else -> player.showDialog(dialog(bags))
        }
    }

    private fun dialog(bags: List<Found>): Dialog = Dialog.create { factory ->
        factory.empty()
            .base(
                DialogBase.builder(Component.text("배낭"))
                    .canCloseWithEscape(true)
                    .pause(false)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .build(),
            )
            .type(
                DialogType.multiAction(
                    bags.mapIndexed { index, found ->
                        val number = index + 1
                        ActionButton.builder(Component.text("$number  ", NamedTextColor.GRAY).append(nameOf(found.stack, found.definition)))
                            .tooltip(Component.text("/배낭 $number"))
                            .width(200)
                            .action(DialogAction.customClick({ _, audience ->
                                val player = audience as? Player ?: return@customClick
                                player.scheduler.run(custom.plugin, { _ -> if (player.isOnline && custom.ready) openNumber(player, number) }, null)
                            }, CALLBACK))
                            .build()
                    },
                ).columns(1).build(),
            )
    }

    /** 아이템에 보이는 이름 그대로(등급 색 포함). */
    private fun nameOf(stack: ItemStack, definition: CustomItem): Component =
        runCatching { stack.effectiveName() }.getOrNull() ?: Component.text(definition.label())

    private fun open(player: Player, id: UUID, definition: CustomItem, stack: ItemStack) {
        if (busy(id, except = player.uniqueId)) {
            custom.messages.send(player, "backpack-busy")
            return
        }
        viewers[id] = player.uniqueId
        BackpackMenu(custom, player, id, nameOf(stack, definition), fresh(id), size(definition, stack)).open(player)
    }

    /** 연 순간 안의 것을 맞춘다 — 기간이 끝나 사라질 것은 치우고 옛 정의로 그려진 것은 다시 그린다(상자를 열 때와 같다). */
    private fun fresh(id: UUID): HashMap<Int, ItemStack> {
        val contents = load(id)
        var changed = false
        val iterator = contents.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (custom.items.vanishes(entry.value)) {
                iterator.remove()
                changed = true
            } else if (custom.items.refresh(entry.value)) {
                changed = true
            }
        }
        if (changed) save(id, contents)
        return contents
    }

    /** 누가 그 배낭을 열어 두었나([except] 는 빼고) — 닫기 사건을 놓친 낡은 표시로 막히지 않게 정말 보고 있는지 본다. */
    private fun busy(id: UUID, except: UUID? = null): Boolean {
        val holder = viewers[id] ?: return false
        return holder != except && isViewing(holder, id)
    }

    private fun isViewing(player: UUID, id: UUID): Boolean =
        (Bukkit.getPlayer(player)?.openInventory?.topInventory?.holder as? BackpackMenu)?.id == id

    // --- 가방처럼 세기(core CarriedStorage) ---------------------------------------------------

    override val name: String = "inmc-customitems:backpacks"

    /** 번호 순서(장착 칸 → 가방)의 배낭 창고. 한 번도 안 연 배낭(비어 있다) · 같은 배낭의 사본 · 누가 열어 둔 것은 뺀다. */
    override fun containers(player: Player): List<CarriedStorage.Container> {
        if (!custom.ready) return emptyList()
        val seen = HashSet<UUID>()
        return carried(player).mapNotNull { found ->
            val id = idOf(found.stack) ?: return@mapNotNull null
            if (!seen.add(id) || busy(id)) return@mapNotNull null
            Container(id, size(found.definition, found.stack))
        }
    }

    private inner class Container(private val id: UUID, private val size: Int) : CarriedStorage.Container {
        override fun contents(): List<ItemStack?> {
            val map = load(id)
            return List(BackpackLayout.capacity(size, map.keys.maxOrNull())) { map[it] }
        }

        override fun write(contents: List<ItemStack?>) {
            save(id, contents.withIndex().mapNotNull { (index, stack) -> stack?.takeIf { !it.type.isAir }?.let { index to it } }.toMap())
        }
    }

    // --- 드랍 자동 수납 -------------------------------------------------------------------------

    /** 사람 → 마지막으로 "넣을 자리 없음" 이었던 때. 줍기 시도는 아이템마다 매 틱 오므로(가방이 찬 채 더미 위에 서 있으면) 잠깐 묻지 않는다. */
    private val misses = HashMap<UUID, Long>()

    /**
     * 자동 수납 배낭에 [stack] 을 넣을 자리가 있나 — 줍기 시도마다 묻는 가벼운 확인(내용물을 복사하지 않고 캐시만 본다). 없으면 그 사람은
     * 0.5초 동안 다시 보지 않는다.
     */
    fun hasRoom(player: Player, stack: ItemStack): Boolean {
        val now = System.currentTimeMillis()
        misses[player.uniqueId]?.let { if (now - it < MISS_MS) return false }
        val room = custom.items.identify(stack)?.isBackpack != true && carried(player).any { found ->
            if (!autoPickup(found.definition, found.stack)) return@any false
            val id = idOf(found.stack) ?: return@any true // 번호가 아직 없는 배낭은 비어 있다
            if (busy(id)) return@any false
            val contents = peek(id)
            contents.values.any { it.isSimilar(stack) && it.amount < it.maxStackSize } ||
                (0 until size(found.definition, found.stack)).any { it !in contents }
        }
        if (room) misses.remove(player.uniqueId) else misses[player.uniqueId] = now
        return room
    }

    /**
     * 주운 [stack] 을 자동 수납 배낭에 넣는다 — 번호 순서(장착 칸 → 가방). 넣고 남은 것(다 들어가면 null, 하나도 못 넣으면 그대로).
     * 배낭 안에 배낭은 넣지 않는다.
     */
    fun store(player: Player, stack: ItemStack): ItemStack? {
        if (custom.items.identify(stack)?.isBackpack == true) return stack
        var left: ItemStack? = stack.clone()
        for (found in carried(player)) {
            val rest = left ?: break
            if (!autoPickup(found.definition, found.stack)) continue
            val id = ensureId(player, found)
            if (busy(id)) continue
            val contents = load(id)
            val after = insert(contents, size(found.definition, found.stack), rest)
            if (after == null || after.amount != rest.amount) save(id, contents)
            left = after
        }
        return left
    }

    /** 같은 것에 먼저 채우고 빈 칸(정한 크기 안)에 넣는다. 남은 것. */
    private fun insert(contents: MutableMap<Int, ItemStack>, size: Int, incoming: ItemStack): ItemStack? {
        var amount = incoming.amount
        for (stack in contents.values) {
            if (!stack.isSimilar(incoming) || stack.amount >= stack.maxStackSize) continue
            val add = minOf(amount, stack.maxStackSize - stack.amount)
            stack.amount += add
            amount -= add
            if (amount <= 0) return null
        }
        for (index in 0 until size) {
            if (contents.containsKey(index)) continue
            val add = minOf(amount, incoming.maxStackSize)
            contents[index] = incoming.clone().apply { this.amount = add }
            amount -= add
            if (amount <= 0) return null
        }
        return incoming.clone().apply { this.amount = amount }
    }

    // --- 부서짐·닫힘 ---------------------------------------------------------------------------

    /**
     * 배낭이 부서진다(분해 · 강화 실패로 파괴 · 사용 기간 끝) — 안의 것을 그 사람에게 돌려주고 창고를 지운다. 박힌 보석을 돌려주는
     * 것과 같다 — 안 그러면 번호 잃은 파일에 갇혀 아무도 못 꺼낸다. 누가 그 창고를 보고 있으면(복사된 같은 배낭) 그대로 둔다.
     */
    fun spill(player: Player, stack: ItemStack) {
        val id = idOf(stack) ?: return
        if (busy(id)) return
        val contents = load(id)
        if (contents.isEmpty()) return
        for (item in contents.toSortedMap().values) {
            for (left in player.inventory.addItem(item).values) player.world.dropItemNaturally(player.location, left)
        }
        save(id, emptyMap())
        custom.messages.send(player, "backpack-spilled")
    }

    /** 화면이 닫혔다. 그 사람이 들고 있던 표시만 푼다. */
    fun release(id: UUID, player: UUID) {
        if (viewers[id] == player) viewers.remove(id)
    }

    /** 끌 때 — 열린 배낭을 닫아 마지막 내용을 쓰게 한다. */
    fun closeAll() {
        for (player in Bukkit.getOnlinePlayers()) {
            if (player.openInventory.topInventory.holder is BackpackMenu) player.closeInventory()
        }
    }

    // --- 파일 ----------------------------------------------------------------------------------

    /** 칸 번호 → 아이템(사본). 빈 칸은 없다. */
    fun load(id: UUID): HashMap<Int, ItemStack> =
        HashMap<Int, ItemStack>().apply { for ((index, stack) in peek(id)) put(index, stack.clone()) }

    /** 캐시 그대로 — **읽기만** 할 것(고치면 파일과 어긋난다). */
    private fun peek(id: UUID): Map<Int, ItemStack> = cache[id] ?: read(id).also { cache[id] = it }

    private fun read(id: UUID): Map<Int, ItemStack> {
        val out = HashMap<Int, ItemStack>()
        val file = File(folder, "$id.yml")
        if (!file.isFile) return out
        val yaml = YamlConfiguration.loadConfiguration(file)
        for (key in yaml.getKeys(false)) {
            val index = key.toIntOrNull()?.takeIf { it >= 0 } ?: continue
            val raw = yaml.getString(key) ?: continue
            runCatching { ItemStack.deserializeBytes(Base64.getDecoder().decode(raw)) }
                .onSuccess { if (!it.type.isAir) out[index] = it }
                .onFailure { custom.logger.warning("배낭을 읽지 못했습니다 ($id $index): ${it.message}") }
        }
        return out
    }

    /** 곧바로, 원자적으로 쓴다. 비었으면 파일을 지운다. */
    fun save(id: UUID, contents: Map<Int, ItemStack>) {
        cache[id] = contents.mapValues { it.value.clone() }
        val target = File(folder, "$id.yml")
        if (contents.isEmpty()) {
            target.delete()
            return
        }
        val yaml = YamlConfiguration()
        for ((index, stack) in contents.toSortedMap()) yaml.set(index.toString(), Base64.getEncoder().encodeToString(stack.serializeAsBytes()))
        folder.mkdirs()
        val temp = File(folder, "$id.yml.tmp")
        temp.writeText(yaml.saveToString(), Charsets.UTF_8)
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        @Suppress("DEPRECATION")
        val KEY = NamespacedKey(ItemBuilder.NAMESPACE, "backpack")

        /** 가방에서 배낭을 찾는 칸 — 단축바·가방(0~35)과 왼손(40). 갑옷 칸은 아니다. */
        private val BAG_SLOTS = (0..35) + 40

        private const val CACHE = 256

        private const val MISS_MS = 500L

        /** G 창의 버튼은 한 번 누르면 끝, 5분 지나면 무효. */
        private val CALLBACK: ClickCallback.Options = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(5)).build()
    }
}

/**
 * 배낭 화면의 칸 계산. 서버 없이 도는 순수 계산이다.
 *
 * 한 페이지는 45칸(5줄), 페이지가 둘 이상이면 맨 아래 줄이 넘기기 줄이다. 45칸 이하면 필요한 줄만큼만 연다.
 * **크기를 줄여도 물건은 사라지지 않는다** — 들어 있는 가장 뒤 칸까지는 늘 보인다([capacity]).
 */
object BackpackLayout {

    const val PER_PAGE = 45

    /** 한 배낭의 최대 크기(20페이지). 파일을 바꿀 때마다 통째로 쓰므로 끝없이 키우지 않는다. */
    const val MAX = PER_PAGE * 20

    /** 보이는 칸 수 — 정한 크기와, 들어 있는 가장 뒤 칸까지 중 큰 것. */
    fun capacity(size: Int, highestUsed: Int?): Int = maxOf(size.coerceIn(1, MAX), (highestUsed ?: -1) + 1)

    fun pages(capacity: Int): Int = maxOf(1, (capacity + PER_PAGE - 1) / PER_PAGE)

    /** 화면 줄 수. 페이지가 여럿이면 6줄(넘기기 줄 포함). */
    fun rows(capacity: Int): Int = if (pages(capacity) > 1) 6 else ((capacity + 8) / 9).coerceIn(1, 5)

    fun index(page: Int, slot: Int): Int = page * PER_PAGE + slot

    /** 그 페이지의 그 칸에 물건을 둘 수 있나. */
    fun usable(capacity: Int, page: Int, slot: Int): Boolean = slot in 0 until PER_PAGE && index(page, slot) < capacity
}
