package com.inmc.customitems.player

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.Carried
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.ItemBuilder
import com.inmc.customitems.item.ItemInstance
import com.inmc.customitems.item.Registries
import com.inmc.customitems.item.SetBonus
import com.inmc.customitems.item.Stat
import com.inmc.customitems.item.StatCalc
import kr.inmc.core.integration.CustomItemHook
import org.bukkit.NamespacedKey
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.EquipmentSlotGroup
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 한 사람이 **지금 입고 든 것 전부**의 능력치 합(MMOItems·MythicLib 의 스탯 맵). 세트 효과도 여기서 더한다.
 *
 * 전투는 사건마다 이걸 묻는다. 매번 여섯 칸의 PDC 를 읽지 않도록 한 번 계산해 두고, 장비가 바뀌는
 * 사건([com.inmc.customitems.listener.StatListener])과 1초 틱이 버린다 — 사건을 하나 놓쳐도 1초 안에 맞는다.
 *
 * **어느 칸에서 세는가.** 방어구는 제 부위에서, 그 밖의 것은 주 손에서, 장신구·방패는 양손에서
 * ([CustomItem.slotGroup]). 투구를 손에 들고 때렸는데 투구의 능력치가 붙으면 안 된다.
 *
 * 세트 효과 가운데 **바닐라 속성과 물약은 사람에게 직접** 건다([sync]) — 아이템에 붙일 수 없는 값이다.
 * 속성은 저장되지 않는 것(transient)으로 달아 접속을 끊으면 사라지고, 물약은 우리가 건 것만 기억해 뗀다.
 */
class StatService(private val custom: CustomItems) {

    /** 세어진 아이템 하나. [slot] 이 null 이면 가방에 든 부적·유물이다. */
    data class Equipped(val slot: EquipmentSlot?, val stack: ItemStack, val definition: CustomItem, val totals: Map<Stat, Double>)

    /**
     * @param sets 세트 id → 입은 벌 수.
     * @param bonuses 지금 붙은 세트 효과들.
     */
    data class Snapshot(
        val equipped: List<Equipped>,
        val totals: Map<Stat, Double>,
        val sets: Map<String, Int> = emptyMap(),
        val bonuses: List<SetBonus> = emptyList(),
        /** 가방의 부적·유물이 주는 바닐라 속성. 아이템에 달 수 없어 사람에게 직접 건다. */
        val carried: Map<Stat, Double> = emptyMap(),
        /** 붙은 세트 단계 가운데 인첸트 효과가 있는 것. 인첸트 엔진이 사건마다 묻는다([com.inmc.customitems.hook.InmcItemProvider.setEffects]). */
        val effects: List<CustomItemHook.SetEffects> = emptyList(),
    ) {
        fun stat(stat: Stat): Double = totals[stat] ?: 0.0
    }

    private val cache = ConcurrentHashMap<UUID, Snapshot>()

    /** 사람 → 우리가 건 세트 물약(종류 → 단계 0부터). 뗄 때 남이 건 것을 건드리지 않으려고 기억한다. */
    private val potions = ConcurrentHashMap<UUID, Map<PotionEffectType, Int>>()

    fun of(player: Player): Snapshot = cache.getOrPut(player.uniqueId) { compute(player) }

    fun invalidate(player: Player) {
        cache.remove(player.uniqueId)
    }

    /** 나갈 때. 건 세트 효과를 떼고(물약은 저장되므로) 잊는다. */
    fun forget(player: Player) {
        cache.remove(player.uniqueId)
        apply(player, emptyList(), emptyMap())
        potions.remove(player.uniqueId)
    }

    /** 1초 틱. 사건을 놓쳤어도 여기서 맞는다. */
    fun clear() = cache.clear()

    /** 새로 계산하고 세트 효과를 사람에게 맞춘다. 장비가 바뀐 다음 틱과 1초 틱이 부른다. */
    fun sync(player: Player) {
        if (!player.isOnline || player.isDead) return
        unequipUnmet(player)
        cache.remove(player.uniqueId)
        val snapshot = of(player)
        apply(player, snapshot.bonuses, snapshot.carried)
    }

    /** 요구 조건이 모자란 방어구는 벗겨 가방으로(가득하면 발밑으로). 입고만 있어도 바닐라 방어력이 먹기 때문이다. */
    private fun unequipUnmet(player: Player) {
        for (slot in ARMOR) {
            val stack = player.inventory.getItem(slot)
            if (stack.type.isAir) continue
            val definition = custom.items.identify(stack) ?: continue
            if (custom.requirements.check(player, definition)) continue
            player.inventory.setItem(slot, null)
            for (left in player.inventory.addItem(stack).values) player.world.dropItemNaturally(player.location, left)
        }
    }

    private fun compute(player: Player): Snapshot {
        val equipped = ArrayList<Equipped>(6)
        for (slot in SLOTS) {
            val stack = player.inventory.getItem(slot)
            if (stack.type.isAir) continue
            val definition = custom.items.usable(stack) ?: continue
            // 부적·유물은 아래에서 가방째 센다 — 손에 든 것을 여기서도 세면 두 번 붙는다.
            if (definition.type.carried || !counts(definition, slot) || !custom.equipmentSettings.worksOutside(definition)) continue
            if (!custom.requirements.meets(player, definition)) continue
            equipped += Equipped(slot, stack, definition, StatCalc.total(definition, ItemInstance.read(stack), custom.items.lookup))
        }
        val carried = extras(player)
        equipped += carried
        val totals = LinkedHashMap<Stat, Double>()
        for (item in equipped) for ((stat, value) in item.totals) totals[stat] = (totals[stat] ?: 0.0) + value

        val sets = equipped.mapNotNull { it.definition.set.takeIf(String::isNotBlank) }.groupingBy { it }.eachCount()
        val bonuses = sets.flatMap { (id, count) -> custom.sets.get(id)?.active(count)?.map { it.second }.orEmpty() }
        val effects = sets.flatMap { (id, count) ->
            val set = custom.sets.get(id) ?: return@flatMap emptyList()
            set.active(count).filter { it.second.effects.isNotEmpty() }.map { (pieces, bonus) -> CustomItemHook.SetEffects(set.id, set.name, pieces, bonus.effects) }
        }
        for (bonus in bonuses) for ((stat, value) in bonus.stats) totals[stat] = (totals[stat] ?: 0.0) + value
        val vanilla = LinkedHashMap<Stat, Double>()
        for (item in carried) for ((stat, value) in item.totals) if (stat.isVanilla) vanilla[stat] = (vanilla[stat] ?: 0.0) + value
        return Snapshot(equipped, totals, sets, bonuses, vanilla, effects)
    }

    /**
     * 장착 칸([EquipmentStore])의 장신구·부적·유물과 가방(단축바·왼손 포함)의 부적·유물. 요구 조건이 모자란 것과
     * 잠긴 장착 칸의 것은 뺀다. 부적·유물은 두 곳을 합쳐 [Carried.select] 규칙을 탄다 — 장착 칸이 앞 칸으로 친다.
     * 장착 칸에서만 효과가 나는 것([EquipmentSettings.worksOutside])은 가방에서 세지 않는다.
     * 부적·유물이 하나도 정의돼 있지 않으면 가방은 훑지 않는다 — 대부분의 서버에서 그럴 이유가 없다.
     */
    private fun extras(player: Player): List<Equipped> {
        val accessories = ArrayList<Equipped>()
        val found = ArrayList<Pair<Carried.Candidate, ItemStack>>()
        fun consider(index: Int, stack: ItemStack, definition: CustomItem) {
            val instance = ItemInstance.read(stack)
            found += Carried.Candidate(index, definition, instance.level, ItemBuilder.tierOf(definition, instance, custom.items.lookup)) to stack
        }
        for (group in EquipmentStore.Group.entries) {
            val capacity = custom.equipment.capacity(player, group)
            for ((index, stack) in custom.equipment.slots(player.uniqueId, group).withIndex()) {
                if (stack == null || index >= capacity) continue
                val definition = custom.items.usable(stack) ?: continue
                if (definition.type != group.type || !custom.requirements.meets(player, definition)) continue
                // 배낭 줄의 배낭도 장신구처럼 — 끼운 것마다 붙는다.
                if (group == EquipmentStore.Group.ACCESSORY || group == EquipmentStore.Group.BACKPACK) {
                    accessories += Equipped(null, stack, definition, StatCalc.total(definition, ItemInstance.read(stack), custom.items.lookup))
                } else {
                    consider(EQUIPMENT_FIRST + group.ordinal * EquipmentStore.MAX + index, stack, definition)
                }
            }
        }
        if (custom.items.hasCarried) {
            val inventory = player.inventory
            for (index in 0 until CARRIED_SLOTS) {
                if (index in 36..39) continue
                val stack = inventory.getItem(index) ?: continue
                if (stack.type.isAir) continue
                val definition = custom.items.usable(stack) ?: continue
                if (!definition.type.carried || !custom.equipmentSettings.worksOutside(definition)) continue
                if (!custom.requirements.meets(player, definition)) continue
                consider(index, stack, definition)
            }
        }
        val chosen = Carried.select(found.map { it.first }).map { it.index }.toSet()
        return accessories + found.filter { it.first.index in chosen }.map { (candidate, stack) ->
            Equipped(null, stack, candidate.definition, StatCalc.total(candidate.definition, ItemInstance.read(stack), custom.items.lookup))
        }
    }

    /** 세트 효과·부적의 바닐라 속성과 세트 물약을 사람에게 맞춘다. 달라진 것만 고친다. */
    private fun apply(player: Player, bonuses: List<SetBonus>, carried: Map<Stat, Double>) {
        val wanted = LinkedHashMap<Stat, Double>(carried)
        for (bonus in bonuses) for ((stat, value) in bonus.stats) if (stat.isVanilla) wanted[stat] = (wanted[stat] ?: 0.0) + value
        for (stat in Stat.entries) {
            val attribute = stat.attribute() ?: continue
            val instance = player.getAttribute(attribute) ?: continue
            val key = setKey(stat)
            val current = instance.getModifier(key)
            val value = wanted[stat]
            if (current != null && current.amount == value) continue
            if (current != null) instance.removeModifier(key)
            if (value != null) instance.addTransientModifier(AttributeModifier(key, value, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY))
        }

        val desired = LinkedHashMap<PotionEffectType, Int>()
        for (bonus in bonuses) for ((name, level) in bonus.potions) {
            val type = Registries.potionEffect(name) ?: continue
            desired[type] = maxOf(desired[type] ?: 0, level - 1)
        }
        val before = potions[player.uniqueId].orEmpty()
        for ((type, amplifier) in before) {
            if (desired[type] == amplifier) continue
            // 우리가 건 그대로(무한·같은 단계)일 때만 뗀다. 그 사이 물약을 마셨으면 그건 그 사람 것이다.
            val active = player.getPotionEffect(type)
            if (active != null && active.isInfinite && active.amplifier == amplifier) player.removePotionEffect(type)
        }
        for ((type, amplifier) in desired) {
            if (before[type] == amplifier && player.hasPotionEffect(type)) continue
            player.addPotionEffect(PotionEffect(type, PotionEffect.INFINITE_DURATION, amplifier, true, false, true))
        }
        if (desired.isEmpty()) potions.remove(player.uniqueId) else potions[player.uniqueId] = desired
    }

    companion object {
        private val SLOTS = listOf(
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
        )

        private val ARMOR = listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)

        /** 가방 칸 0~35(단축바 포함)와 40(왼손). 방어구 칸(36~39)은 뺀다. */
        private const val CARRIED_SLOTS = 41

        /** 장착 칸의 부적·유물을 가방보다 "앞 칸"으로 친다(유물 동점일 때 장착한 것이 이긴다). */
        private const val EQUIPMENT_FIRST = -1000

        /** 이 칸에 있을 때 능력치를 세는가. */
        fun counts(definition: CustomItem, slot: EquipmentSlot): Boolean = definition.slotGroup().test(slot)

        @Suppress("DEPRECATION")
        fun setKey(stat: Stat) = NamespacedKey(ItemBuilder.NAMESPACE, "set_" + stat.id)
    }
}
