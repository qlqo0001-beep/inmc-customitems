package com.inmc.customitems.item

import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import java.util.Random

/**
 * 아이템 **한 개**가 따로 갖는 것 — 같은 정의로 만든 칼 둘이 다른 이유.
 *
 * **숫자는 적지 않는다. 숫자를 만드는 재료만 적는다.** 굴린 값 대신 굴린 편차(`z`)를, 수식어의 능력치 대신
 * 수식어 id 를 둔다. 능력치는 늘 지금 정의로 다시 계산하므로([StatCalc]) 관리자가 정의를 고치면 이미
 * 나간 아이템도 따라 바뀐다 — 이 플러그인이 처음부터 지킨 약속이다.
 *
 * @param rolls 능력치 → 굴린 표준정규 값.
 * @param modifiers 붙은 수식어 id.
 * @param revision 마지막으로 그린 정의의 지문([ItemRegistry.revision]). 다르면 다시 그린다.
 */
data class ItemInstance(
    val rolls: Map<Stat, Double> = emptyMap(),
    val modifiers: List<String> = emptyList(),
    /** 소켓 순서대로 박힌 보석 id. 빈 소켓은 빈 글. 정의의 소켓 수보다 짧을 수 있다(나중에 늘린 소켓). */
    val gems: List<String> = emptyList(),
    /** 소모품의 남은 횟수. null 이면 한 번도 안 썼다(정의의 횟수 그대로). */
    val usesLeft: Int? = null,
    /** 감정 전이다. 능력치·기능·공격 방식이 돌지 않고 겉에 정체가 안 보인다. */
    val unidentified: Boolean = false,
    /** 강화 단계. 0 이 기본. */
    val level: Int = 0,
    val revision: Long = 0L,
) {
    /** [index] 번 소켓의 보석. 비었으면 null. */
    fun gem(index: Int): String? = gems.getOrNull(index)?.takeIf { it.isNotEmpty() }

    /** [index] 번 소켓에 [gemId] 를 박은 몫. */
    fun withGem(index: Int, gemId: String): ItemInstance {
        val next = gems.toMutableList()
        while (next.size <= index) next += ""
        next[index] = gemId
        return copy(gems = next)
    }

    fun write(pdc: PersistentDataContainer) {
        if (rolls.isEmpty()) pdc.remove(ROLLS) else pdc.set(ROLLS, PersistentDataType.STRING, encodeRolls(rolls))
        if (modifiers.isEmpty()) pdc.remove(MODIFIERS) else pdc.set(MODIFIERS, PersistentDataType.STRING, modifiers.joinToString(","))
        if (gems.none { it.isNotEmpty() }) pdc.remove(GEMS) else pdc.set(GEMS, PersistentDataType.STRING, gems.joinToString(","))
        if (usesLeft == null) pdc.remove(USES) else pdc.set(USES, PersistentDataType.INTEGER, usesLeft)
        if (unidentified) pdc.set(UNIDENTIFIED, PersistentDataType.BYTE, 1) else pdc.remove(UNIDENTIFIED)
        if (level > 0) pdc.set(LEVEL, PersistentDataType.INTEGER, level) else pdc.remove(LEVEL)
        pdc.set(REVISION, PersistentDataType.LONG, revision)
    }

    companion object {

        @Suppress("DEPRECATION")
        private fun key(name: String) = NamespacedKey(ItemBuilder.NAMESPACE, name)

        val ROLLS = key("rolls")
        val MODIFIERS = key("mods")
        val GEMS = key("gems")
        val USES = key("uses")
        val REVISION = key("rev")
        val UNIDENTIFIED = key("unid")
        val LEVEL = key("upg")

        /** 감정 전인가. 전투마다 불리므로 몫 전체를 읽지 않고 이 칸만 본다. */
        fun isUnidentified(stack: ItemStack?): Boolean {
            val meta = stack?.takeIf { !it.type.isAir && it.hasItemMeta() }?.itemMeta ?: return false
            return meta.persistentDataContainer.has(UNIDENTIFIED, PersistentDataType.BYTE)
        }

        fun read(stack: ItemStack?): ItemInstance {
            val meta = stack?.takeIf { !it.type.isAir && it.hasItemMeta() }?.itemMeta ?: return ItemInstance()
            return read(meta.persistentDataContainer)
        }

        fun read(pdc: PersistentDataContainer): ItemInstance = ItemInstance(
            rolls = decodeRolls(pdc.get(ROLLS, PersistentDataType.STRING)),
            modifiers = pdc.get(MODIFIERS, PersistentDataType.STRING)?.split(',')?.filter { it.isNotBlank() }.orEmpty(),
            gems = pdc.get(GEMS, PersistentDataType.STRING)?.split(',').orEmpty(),
            usesLeft = pdc.get(USES, PersistentDataType.INTEGER),
            unidentified = pdc.has(UNIDENTIFIED, PersistentDataType.BYTE),
            level = pdc.get(LEVEL, PersistentDataType.INTEGER)?.coerceAtLeast(0) ?: 0,
            revision = pdc.get(REVISION, PersistentDataType.LONG) ?: 0L,
        )

        /** 새로 만들 아이템의 몫. 폭이 있는 능력치만 굴리고, 수식어는 각자 확률로. */
        fun roll(definition: CustomItem, random: Random): ItemInstance = ItemInstance(
            rolls = definition.spreads.filterValues { it.spread > 0.0 }.mapValues { Spread.roll(random) },
            modifiers = definition.modifiers.filter { random.nextDouble() * 100.0 < it.chance }.map { it.id },
            unidentified = definition.unidentified,
        )

        fun encodeRolls(rolls: Map<Stat, Double>): String = rolls.entries.joinToString(";") { it.key.id + "=" + it.value }

        /** 모르는 능력치·깨진 값은 건너뛴다 — 능력치를 지운 정의로도 옛 아이템이 읽혀야 한다. */
        fun decodeRolls(raw: String?): Map<Stat, Double> {
            if (raw.isNullOrBlank()) return emptyMap()
            val out = LinkedHashMap<Stat, Double>()
            for (part in raw.split(';')) {
                val stat = Stat.of(part.substringBefore('=')) ?: continue
                val z = part.substringAfter('=', "").toDoubleOrNull() ?: continue
                out[stat] = z
            }
            return out
        }
    }
}
