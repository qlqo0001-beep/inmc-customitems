package com.inmc.customitems.hook

import com.inmc.customitems.CustomItems
import com.inmc.customitems.gui.ItemEditMenu
import com.inmc.customitems.gui.RoleEditMenu
import com.inmc.customitems.item.ItemBuilder
import com.inmc.customitems.item.ItemSet
import com.inmc.customitems.item.SetBonus
import kr.inmc.core.integration.ItemRoles
import kr.inmc.core.item.ItemRef
import kr.inmc.core.store.DefinitionKey
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * core [ItemRoles] 의 정의하는 쪽 — 아이템마다 어떤 역할을 어떤 값으로 맡는지는 **이 플러그인의 `items.yml`** 에 적힌다
 * ([com.inmc.customitems.item.CustomItem.roles]). 인벤키퍼·랜덤박스·낚시 … 는 여기서 읽고, 제 화면에서 등록한 것도 여기에 적는다.
 */
class RoleStore(private val custom: CustomItems) : ItemRoles.Store {

    private fun ours(ref: ItemRef.Namespaced) = ref.namespace.equals(ItemBuilder.NAMESPACE, ignoreCase = true)

    override fun holders(role: String): List<ItemRoles.Holder> =
        custom.items.all().mapNotNull { item ->
            item.roles[role]?.let {
                val model = item.itemModel.ifBlank { if (com.inmc.customitems.pack.PackAssets.needsPack(item)) ItemBuilder.NAMESPACE + ":" + item.resourceId else "" }
                ItemRoles.Holder(ItemRef.Namespaced(ItemBuilder.NAMESPACE, item.id), it, item.material, kr.inmc.core.util.Text.plain(item.label()), model, item.customModelData)
            }
        }

    override fun rolesOf(ref: ItemRef.Namespaced): Map<String, Map<String, String>> =
        if (ours(ref)) custom.items.get(ref.id)?.roles.orEmpty() else emptyMap()

    /**
     * 역할을 붙이거나 값을 바꾼다. **종류(type)는 건드리지 않는다**(사용자 결정 2026-09-26) — 전에는 역할이 권하는 종류(보호권 …)로
     * 저절로 바꿨는데, 이미 만든 아이템을 연동 아이템으로 바꿀 때 관리자가 정한 종류가 덮였다.
     */
    override fun assign(ref: ItemRef.Namespaced, role: String, values: Map<String, String>?): Boolean {
        if (!ours(ref)) return false
        val item = custom.items.get(ref.id) ?: return false
        val roles = LinkedHashMap(item.roles)
        if (values == null) roles.remove(role) else roles[role] = LinkedHashMap(values)
        val updated = item.copy(roles = roles)
        // 옮겨 온 아이템이면 기본 겉모습(물고기 그림 …)을 입힌다 — 겉모습이 이미 있으면 그대로.
        custom.items.put(custom.roleAppearance.decorate(updated) ?: updated)
        return true
    }

    /**
     * 손에 든 것(다른 플러그인의 아이템)을 우리 아이템으로. 겉모습 — 재질·이름(색 그대로)·설명·모델 번호·모델·인챈트·
     * 커스텀 인첸트·숨김·가죽 색 — 을 옮긴다. 이미 우리 것이면 그 참조.
     */
    override fun adopt(stack: ItemStack, idHint: String): ItemRef.Namespaced? {
        ItemBuilder.identify(stack)?.let { return ItemRef.Namespaced(ItemBuilder.NAMESPACE, it) }
        if (stack.type.isAir) return null
        val id = freeId(idHint)
        val meta = stack.itemMeta
        val mini = MiniMessage.miniMessage()
        val captured = ItemBuilder.capture(id, stack)
        val dye = (meta as? org.bukkit.inventory.meta.LeatherArmorMeta)?.takeIf { it.isDyed }?.color?.let { "#%06X".format(it.asRGB()) }
        @Suppress("DEPRECATION")
        val item = captured.copy(
            displayName = meta?.displayName()?.let(mini::serialize).orEmpty(),
            lore = meta?.lore()?.map(mini::serialize).orEmpty(),
            customModelData = meta?.takeIf { it.hasCustomModelData() }?.customModelData ?: 0,
            itemModel = meta?.itemModel?.takeIf { !it.namespace.equals(ItemBuilder.NAMESPACE, ignoreCase = true) }?.asString().orEmpty(),
            customEnchants = kr.inmc.core.integration.CustomEnchantHook.levels(stack),
            components = if (dye == null) captured.components else captured.components.copy(color = dye),
        )
        custom.items.put(item)
        return ItemRef.Namespaced(ItemBuilder.NAMESPACE, id)
    }

    /**
     * 다른 플러그인의 옛 세트를 옮겨 온다(인첸트의 방어구 세트). 벌 수마다 인첸트 효과만 담긴 단계를 만들고, [members] 를 이 세트에 넣는다.
     * 이미 그 id 의 세트가 있으면 손대지 않는다 — 관리자가 고친 것을 옮기기가 덮으면 안 된다.
     */
    override fun defineSet(id: String, name: String, effects: Map<Int, Map<String, Any?>>, members: List<ItemRef.Namespaced>): Boolean {
        val key = id.lowercase()
        if (custom.sets.get(key) != null) return false
        val bonuses = effects.filter { (count, tree) -> count in 1..10 && tree.isNotEmpty() }.mapValues { (_, tree) -> SetBonus(effects = tree) }
        custom.sets.put(ItemSet(key, name, bonuses))
        for (ref in members) {
            if (!ours(ref)) continue
            custom.items.get(ref.id)?.let { custom.items.put(it.copy(set = key)) }
        }
        return true
    }

    override fun openEditor(player: Player, ref: ItemRef.Namespaced, role: String?): Boolean {
        if (!ours(ref) || custom.items.get(ref.id) == null) return false
        if (role != null && ItemRoles.role(role) != null) RoleEditMenu(custom, player, ref.id, role).open(player)
        else ItemEditMenu(custom, player, ref.id).open(player)
        return true
    }

    /** [hint] 를 규칙에 맞게 다듬고, 이미 있으면 `_2`, `_3` … */
    private fun freeId(hint: String): String {
        val base = hint.lowercase().replace(Regex("[^a-z0-9_가-힣-]"), "_").trim('_').take(28).ifBlank { "item" }
        if (!custom.items.exists(base) && DefinitionKey.isValid(base)) return base
        var n = 2
        while (custom.items.exists(base + "_" + n)) n++
        return base + "_" + n
    }
}
