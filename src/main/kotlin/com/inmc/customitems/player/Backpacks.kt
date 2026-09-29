package com.inmc.customitems.player

import com.inmc.customitems.CustomItems
import com.inmc.customitems.gui.BackpackMenu
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.ItemBuilder
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
import java.util.Base64
import java.util.UUID

/**
 * 배낭 — 장신구·부적·유물([CustomItem.isBackpack])을 우클릭하면 열리는, **아이템 한 개만의** 창고(사용자 결정 2026-09-30: 셜커 상자처럼
 * 아이템에 붙는다). 장착 칸에 끼운 것은 `/배낭` 으로 연다.
 *
 * ## 내용물은 아이템이 아니라 서버 파일에
 * 아이템에는 **배낭 번호**(`inmc:backpack` = UUID)만 찍고 내용물은 `backpacks/<번호>.yml` 에 둔다. 크기가 자유라(수백 칸) 내용물을
 * 아이템 NBT 에 넣으면 그 아이템이 오갈 때마다 클라이언트로 가는 패킷이 부푼다. 번호만 들고 다니므로 **아이템이 복사돼도 내용물은
 * 늘지 않는다**(같은 창고를 가리킬 뿐이다). 번호는 처음 열 때 찍는다 — 만들 때 찍으면 겹쳐 나온 묶음이 한 번호를 나눠 갖는다.
 *
 * ## 저장은 바꿀 때마다 즉시, 원자적으로 · 한 번에 한 사람
 * [EquipmentStore] 와 같은 이유로 바꿀 때마다 메인 스레드에서 `.tmp` → rename 으로 쓴다. 닫을 때 플레이어 데이터도 같이 저장해
 * 가방 쪽과 어긋나는 틈을 줄인다. 같은 배낭을 두 사람이 동시에 열면 두 화면에서 같은 물건을 꺼내 복사되므로 **한 번에 한 사람만** 연다.
 */
class Backpacks(private val custom: CustomItems) {

    private val folder: File get() = custom.io.file("backpacks")

    /** 배낭 번호 → 지금 열어 둔 사람. */
    private val viewers = HashMap<UUID, UUID>()

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

    // --- 열기 ---------------------------------------------------------------------------

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
        open(player, id, definition)
        return true
    }

    /** 장착 칸에 끼운 배낭([equipped] 의 한 칸). */
    fun openEquipped(player: Player, group: EquipmentStore.Group, index: Int) {
        val stack = custom.equipment.get(player.uniqueId, group, index)?.clone() ?: return
        val definition = custom.items.usable(stack)?.takeIf { it.isBackpack } ?: return
        val (id, stamped) = stamp(stack)
        if (stamped) custom.equipment.put(player.uniqueId, group, index, stack)
        open(player, id, definition)
    }

    /**
     * 장착 칸의 배낭을 연다 — `/배낭` 과 빠른 동작 키(G)의 [배낭 열기]. 하나면 곧바로, 여럿이면 고르는 화면, 없으면 알린다.
     */
    fun openEquippedAny(player: Player) {
        val bags = equipped(player)
        when (bags.size) {
            0 -> custom.messages.send(player, "backpack-none")
            1 -> bags.single().let { (group, index, _) -> openEquipped(player, group, index) }
            else -> com.inmc.customitems.gui.ChoiceMenu(
                custom, player, "배낭 고르기",
                options = {
                    equipped(player).map { (group, index, stack) ->
                        group.id + ":" + index to kr.inmc.core.gui.Icon.annotate(stack.clone(), lore = listOf("<dark_gray>" + group.display + " " + (index + 1) + "번 칸</dark_gray>"))
                    }
                },
                selected = { emptySet() },
                back = { player.closeInventory() },
            ) { picked ->
                val group = EquipmentStore.Group.entries.firstOrNull { it.id == picked.substringBefore(':') }
                val index = picked.substringAfter(':').toIntOrNull()
                if (group != null && index != null) openEquipped(player, group, index)
            }.open(player)
        }
    }

    /** 장착 칸(열린 칸만)에 끼운 배낭들. */
    fun equipped(player: Player): List<Triple<EquipmentStore.Group, Int, ItemStack>> =
        EquipmentStore.Group.entries.flatMap { group ->
            (0 until custom.equipment.capacity(player, group)).mapNotNull { index ->
                custom.equipment.get(player.uniqueId, group, index)
                    ?.takeIf { custom.items.usable(it)?.isBackpack == true }
                    ?.let { Triple(group, index, it) }
            }
        }

    private fun open(player: Player, id: UUID, definition: CustomItem) {
        val holder = viewers[id]
        if (holder != null && holder != player.uniqueId && isViewing(holder, id)) {
            custom.messages.send(player, "backpack-busy")
            return
        }
        viewers[id] = player.uniqueId
        BackpackMenu(custom, player, id, definition.label(), load(id), definition.backpack).open(player)
    }

    /** 그 사람이 정말 그 배낭을 보고 있나 — 닫기 사건을 놓친 낡은 표시(리로드 등)로 막히지 않게. */
    private fun isViewing(player: UUID, id: UUID): Boolean =
        (Bukkit.getPlayer(player)?.openInventory?.topInventory?.holder as? BackpackMenu)?.id == id

    /**
     * 배낭이 부서진다(분해 · 강화 실패로 파괴) — 안의 것을 그 사람에게 돌려주고 창고를 지운다. 박힌 보석을 돌려주는 것과 같다 —
     * 안 그러면 번호 잃은 파일에 갇혀 아무도 못 꺼낸다. 누가 그 창고를 보고 있으면(복사된 같은 배낭) 그대로 둔다 — 그쪽이 쓰는 중이다.
     */
    fun spill(player: Player, stack: ItemStack) {
        val id = idOf(stack) ?: return
        viewers[id]?.let { if (isViewing(it, id)) return }
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

    // --- 파일 -----------------------------------------------------------------------------

    /** 칸 번호 → 아이템. 빈 칸은 없다. */
    fun load(id: UUID): HashMap<Int, ItemStack> {
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
