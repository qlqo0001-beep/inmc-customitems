package com.inmc.customitems.item

import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.enchantments.Enchantment
import org.bukkit.potion.PotionEffectType

/**
 * 관리자가 적은 이름을 실제 레지스트리 값으로 찾는다.
 *
 * **`Registry.ENCHANTMENT` 같은 static 필드를 쓰지 않는다.** 그쪽은 deprecated 이고,
 * 같은 레지스트리에 이름이 여럿이라(`EFFECT` · `MOB_EFFECT` · `POTION_EFFECT_TYPE`)
 * 어느 것이 정식인지도 헷갈린다. `RegistryAccess` 가 정식 경로다.
 *
 * **전부 null 을 돌려줄 수 있다.** 관리자가 오타를 냈거나 그 서버 버전에 없는 이름일 때
 * 부르는 쪽이 그 항목만 건너뛴다 — 오타 하나로 아이템 전체가 죽으면 안 된다.
 *
 * ⚠ **서버가 있어야 동작한다.** `RegistryAccess` 는 서버 없이 부르면
 * `NoClassDefFoundError` 다. 그래서 분류나 판정에는 절대 쓰지 않는다 — 검증할 수 없고
 * 버전마다 답이 달라질 수 있다. 여기 있는 것들은 전부 **아이템을 실제로 만들 때만** 불린다.
 */
object Registries {

    /** 바닐라 id(`sharpness`)와 네임스페이스 표기(`minecraft:sharpness`)를 둘 다 받는다. */
    fun enchantment(raw: String?): Enchantment? = lookup(raw, RegistryKey.ENCHANTMENT)

    /** 서버에 있는 인챈트 전부(데이터팩이 더한 것 포함), 열쇠 순. 고르기 화면용. */
    fun enchantments(): List<Enchantment> =
        runCatching { RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).toList() }
            .getOrDefault(emptyList())
            .sortedBy { it.key.asString() }

    fun attribute(raw: String?): Attribute? = lookup(raw, RegistryKey.ATTRIBUTE)

    fun potionEffect(raw: String?): PotionEffectType? = lookup(raw, RegistryKey.MOB_EFFECT)

    fun sound(raw: String?): Sound? = lookup(raw, RegistryKey.SOUND_EVENT)

    fun trimPattern(raw: String?): org.bukkit.inventory.meta.trim.TrimPattern? = lookup(raw, RegistryKey.TRIM_PATTERN)

    fun trimMaterial(raw: String?): org.bukkit.inventory.meta.trim.TrimMaterial? = lookup(raw, RegistryKey.TRIM_MATERIAL)

    private fun <T : org.bukkit.Keyed> lookup(raw: String?, key: RegistryKey<T>): T? {
        val name = normalize(raw) ?: return null
        val namespaced = NamespacedKey.fromString(name) ?: return null
        // 서버가 없으면 여기서 던진다. 그런 자리에서 부르지 않는 것이 규칙이지만,
        // 던져서 아이템 생성 전체를 막는 것보다는 그 항목만 건너뛰는 편이 낫다.
        return runCatching { RegistryAccess.registryAccess().getRegistry(key).get(namespaced) }
            .getOrNull()
    }

    /**
     * 설정에 적힌 이름을 레지스트리 열쇠 모양으로.
     *
     * 관리자는 `SPEED` 라고도 `minecraft:speed` 라고도 적는다. 점을 밑줄로 바꾸는 것은
     * 소리 이름(`entity.generic.explode`)이 레지스트리에서는 밑줄이기 때문이다.
     */
    private fun normalize(raw: String?): String? {
        val text = raw?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        return if (':' in text) text else "minecraft:" + text.replace('.', '_')
    }
}
