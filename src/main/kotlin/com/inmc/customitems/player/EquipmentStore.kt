package com.inmc.customitems.player

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.ItemType
import kr.inmc.core.integration.ExtraInventory
import kr.inmc.core.store.YamlFileStore
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.EnumMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 장착 칸(`/장비`) — 가방 밖에 장신구·부적·유물을 끼워 두는 곳. 가방에 든 부적·유물과 **함께** 효과가 난다.
 *
 * ## 저장은 바꿀 때마다 즉시, 원자적으로
 * 가방 밖에 아이템을 들고 있는 것이라 복사·증발이 가장 큰 위험이다. `PlayerStore` 는 30초에 한 번 쓰므로
 * 그 사이 서버가 죽으면 꺼낸 아이템이 칸에도 남아 복사된다. 그래서 플레이어별 파일(`equipment/<uuid>.yml`)을
 * 바꿀 때마다 **메인 스레드에서 바로** `.tmp` → rename 으로 쓴다(1~2KB). 워커로 넘기면 순서가 흐트러져 이 장치의
 * 뜻이 없어진다 — 이 워크스페이스에서 메인 스레드 디스크 쓰기를 일부러 하는 자리다(업적 `ClaimStore` 와 같은 이유).
 *
 * 죽을 때는 가방과 같이 다뤄진다 — core [ExtraInventory] 로 인벤키퍼에게 칸을 알린다.
 */
class EquipmentStore(private val custom: CustomItems) : ExtraInventory.Provider {

    enum class Group(val id: String, val display: String, val type: ItemType, val icon: Material) {
        ACCESSORY("accessory", "장신구", ItemType.ACCESSORY, Material.AMETHYST_SHARD),
        TALISMAN("talisman", "부적", ItemType.TALISMAN, Material.PAPER),
        RELIC("relic", "유물", ItemType.RELIC, Material.HEART_OF_THE_SEA),
        /** 배낭 줄(사용자 결정 2026-09-30) — 앞 칸부터 `/배낭 1, 2, 3 …`. 맨 뒤에 둔다 — 죽을 때 칸 번호(종류 순서 × 8)가 밀리지 않게. */
        BACKPACK("backpack", "배낭", ItemType.BACKPACK, Material.BUNDLE),
        ;

        /** 권한 노드. `incustomitems.slots.talisman.6` 이면 부적 칸 6개. */
        fun permission(count: Int): String = "incustomitems.slots.$id.$count"

        companion object {
            fun of(type: ItemType): Group? = entries.firstOrNull { it.type == type }
        }
    }

    private val loaded = ConcurrentHashMap<UUID, EnumMap<Group, Array<ItemStack?>>>()

    private val folder: File get() = custom.io.file("equipment")

    /** 이 사람의 [group] 칸들(길이 [MAX]). 없으면 파일에서 읽는다. **고치지 말 것** — [put] 으로. */
    fun slots(player: UUID, group: Group): Array<ItemStack?> = equipment(player).getValue(group)

    fun get(player: UUID, group: Group, index: Int): ItemStack? = slots(player, group).getOrNull(index)

    /**
     * 보기 전용 사본(남의 장비 보기). 읽어 둔 것이 없으면 파일에서 읽되 **붙들지 않는다** — 오프라인인 사람 것을 볼 때마다
     * 메모리에 쌓이지 않게. 사본이라 고쳐도 칸은 안 바뀐다.
     */
    fun peek(player: UUID): Map<Group, List<ItemStack?>> =
        (loaded[player] ?: read(player)).mapValues { (_, slots) -> slots.map { it?.clone() } }

    /** [index] 칸을 바꾸고 곧바로 파일에 쓴다. */
    fun put(player: UUID, group: Group, index: Int, stack: ItemStack?) {
        if (index !in 0 until MAX) return
        slots(player, group)[index] = stack?.takeIf { !it.type.isAir }?.clone()
        save(player)
    }

    /** 옛 정의로 그려진 것을 다시 그린다(접속할 때·규칙이 바뀔 때). 바뀐 것이 있을 때만 쓴다. */
    fun refresh(player: UUID) {
        var changed = false
        for (group in Group.entries) for (stack in slots(player, group)) if (stack != null && custom.items.refresh(stack)) changed = true
        if (changed) save(player)
    }

    /** 열린 칸 수 — 기본값과 권한 가운데 큰 것. 권한은 `incustomitems.slots.<종류>.<수>`. */
    fun capacity(player: Player, group: Group): Int {
        val granted = (MAX downTo 1).firstOrNull { player.hasPermission(group.permission(it)) } ?: 0
        return maxOf(custom.equipmentSettings.slots(group), granted).coerceIn(0, MAX)
    }

    fun load(player: UUID) {
        equipment(player)
    }

    fun forget(player: UUID) {
        loaded.remove(player)
    }

    private fun equipment(player: UUID): EnumMap<Group, Array<ItemStack?>> = loaded.getOrPut(player) { read(player) }

    private fun read(player: UUID): EnumMap<Group, Array<ItemStack?>> {
        val out = EnumMap<Group, Array<ItemStack?>>(Group::class.java)
        for (group in Group.entries) out[group] = arrayOfNulls(MAX)
        val file = File(folder, "$player.yml")
        if (!file.isFile) return out
        val yaml = YamlConfiguration.loadConfiguration(file)
        for (group in Group.entries) {
            val section = yaml.getConfigurationSection(group.id) ?: continue
            for (key in section.getKeys(false)) {
                val index = key.toIntOrNull()?.takeIf { it in 0 until MAX } ?: continue
                val raw = section.getString(key) ?: continue
                out.getValue(group)[index] = runCatching { ItemStack.deserializeBytes(Base64.getDecoder().decode(raw)) }
                    .onFailure { custom.logger.warning("장착 칸을 읽지 못했습니다 ($player ${group.id} $index): ${it.message}") }
                    .getOrNull()
            }
        }
        return out
    }

    private fun save(player: UUID) {
        val equipment = loaded[player] ?: return
        val yaml = YamlConfiguration()
        for ((group, slots) in equipment) {
            for ((index, stack) in slots.withIndex()) {
                if (stack == null) continue
                yaml.set(group.id + "." + index, Base64.getEncoder().encodeToString(stack.serializeAsBytes()))
            }
        }
        folder.mkdirs()
        val target = File(folder, "$player.yml")
        val temp = File(folder, "$player.yml.tmp")
        temp.writeText(yaml.saveToString(), Charsets.UTF_8)
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    // --- 죽을 때: 가방과 같이 ------------------------------------------------------

    override val name: String = "inmc-customitems:equipment"

    /** 칸 번호 = 종류 순서 × [MAX] + 칸. */
    override fun items(player: Player): List<ItemStack?> = Group.entries.flatMap { slots(player.uniqueId, it).toList() }

    override fun remove(player: Player, index: Int) = put(player.uniqueId, Group.entries[index / MAX], index % MAX, null)

    companion object {
        /** 한 종류의 칸은 장착 화면 한 줄(8칸)이 전부다. */
        const val MAX = 8
    }
}

/** `equipment.yml` — 장착 칸의 기본 수와 "장착 칸 밖에서도 효과". 관리 화면에서 고친다. */
class EquipmentSettings(private val custom: CustomItems) : YamlFileStore(
    io = custom.io,
    path = listOf("equipment.yml"),
    header = """
        장착 칸(/장비)의 기본 수. /커스텀아이템 관리 → 장착 칸 에서 고치는 것을 권장합니다.
        권한 incustomitems.slots.<종류>.<수> 로 더 열 수 있습니다(기본값보다 클 때만). 종류당 최대 8칸.
        inventory-effects: false 면 장신구·부적·유물은 장착 칸에 끼워야만 효과가 납니다(가방·손의 것은 멈춤).
        아이템마다 따로 정할 수 있습니다(아이템 설정 → 효과가 나는 곳).
    """.trimIndent() + "\n",
    what = "장착 칸 설정",
) {

    private val slots = EnumMap<EquipmentStore.Group, Int>(EquipmentStore.Group::class.java)

    fun slots(group: EquipmentStore.Group): Int = slots[group] ?: DEFAULTS.getValue(group)

    /** 장신구·부적·유물이 장착 칸 밖(가방·손)에서도 효과를 내는가. 아이템이 따로 정하지 않았을 때의 값. */
    @Volatile
    var inventoryEffects: Boolean = true
        private set

    fun setInventoryEffects(value: Boolean) {
        if (value == inventoryEffects) return
        inventoryEffects = value
        markDirty()
        custom.items.onEquipmentRuleChanged()
        custom.stats.clear()
        // 접속한 사람의 가방·장착 칸은 곧바로 다시 그린다(로어가 "어디서 효과가 나는지" 말한다). 나머지는 눈에 들어올 때.
        for (player in org.bukkit.Bukkit.getOnlinePlayers()) player.scheduler.run(custom.plugin, {
            custom.items.refresh(player.inventory)
            custom.equipment.refresh(player.uniqueId)
        }, null)
    }

    /** 장착 칸 밖(가방·손)에 있는 [definition] 이 효과를 내는가. */
    fun worksOutside(definition: com.inmc.customitems.item.CustomItem): Boolean = definition.worksOutsideSlots(inventoryEffects)

    fun set(group: EquipmentStore.Group, count: Int) {
        slots[group] = count.coerceIn(0, EquipmentStore.MAX)
        markDirty()
    }

    override fun read(config: YamlConfiguration) {
        slots.clear()
        for (group in EquipmentStore.Group.entries) {
            slots[group] = config.getInt("slots." + group.id, DEFAULTS.getValue(group)).coerceIn(0, EquipmentStore.MAX)
        }
        inventoryEffects = config.getBoolean("inventory-effects", true)
    }

    override fun write(config: YamlConfiguration) {
        for (group in EquipmentStore.Group.entries) config.set("slots." + group.id, slots(group))
        config.set("inventory-effects", inventoryEffects)
    }

    companion object {
        val DEFAULTS = mapOf(
            EquipmentStore.Group.ACCESSORY to 4,
            EquipmentStore.Group.TALISMAN to 4,
            EquipmentStore.Group.RELIC to 1,
            EquipmentStore.Group.BACKPACK to 1,
        )
    }
}
