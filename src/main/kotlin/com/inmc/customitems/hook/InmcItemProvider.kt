package com.inmc.customitems.hook

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.ItemBuilder
import kr.inmc.core.integration.CustomItemHook
import kr.inmc.core.item.ItemRef
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 이 플러그인을 core 의 아이템 계층에 꽂는다.
 *
 * **이게 이 플러그인의 존재 이유다.** 이걸 등록하는 순간 낚시·인벤키퍼·랜덤박스·몬스터가
 * 전부 `inmc:내아이템` 을 알아보고 만들어낸다. 그 플러그인들은 이 플러그인이 있는지조차
 * 모르고, 한 줄도 바뀌지 않는다 — core 의 [CustomItemHook] 이 그 사이를 잇는다.
 *
 * ItemsAdder·Nexo·Oraxen 어댑터와 **같은 인터페이스**를 구현한다. 우리 것이라고 특별 대우를
 * 받지 않으니, 서드파티 플러그인으로 옮겨가더라도 소비자 쪽은 그대로다.
 */
class InmcItemProvider(private val plugin: CustomItems) : CustomItemHook.Provider {

    override val namespace: String get() = ItemBuilder.NAMESPACE

    override fun identify(stack: ItemStack): String? = ItemBuilder.identify(stack)

    /**
     * 참조를 아이템으로 만든다.
     *
     * **네임스페이스가 우리 것일 때만 답한다.** 아니면 null 을 줘서 다른 공급처가 볼 기회를
     * 남긴다 — 여기서 아무거나 만들어 돌려주면 ItemsAdder 아이템이 우리 것으로 바뀐다.
     */
    override fun create(ref: ItemRef.Namespaced): ItemStack? {
        if (!ref.namespace.equals(namespace, ignoreCase = true)) return null
        return plugin.items.create(ref.id)
    }

    /**
     * 다른 플러그인이 읽을 값들.
     *
     * 이 플러그인은 `fishing.reel-power` 가 무슨 뜻인지 **모른다.** 적힌 대로 넘겨줄 뿐이고,
     * 뜻은 읽는 쪽이 정한다.
     */
    override fun data(ref: ItemRef.Namespaced): Map<String, String> {
        if (!ref.namespace.equals(namespace, ignoreCase = true)) return emptyMap()
        return plugin.items.get(ref.id)?.data.orEmpty()
    }

    /**
     * 우리 아이템의 로어는 우리가 통째로 쥔다(이름 → 종류 → 인첸트 → 능력치 …). 인첸트 플러그인이 부여서를 붙이면
     * 자기 줄을 얹는 대신 이걸 불러 다시 그리게 하고, 인첸트 줄은 [ItemBuilder.render] 가 core 에서 받아 제자리에 넣는다.
     */
    override fun redraw(stack: ItemStack): Boolean {
        val definition = plugin.items.identify(stack) ?: return false
        val instance = com.inmc.customitems.item.ItemInstance.read(stack)
        ItemBuilder.render(stack, definition, instance.copy(revision = plugin.items.revision(definition.id)), plugin.items.lookup)
        return true
    }

    /**
     * 입고 든 세트 가운데 인첸트 효과가 붙은 단계. 세는 것은 우리(능력치 합과 같은 캐시 — 장비가 바뀌면 버린다),
     * 돌리는 것은 인첸트 엔진이다. 전투 사건마다 불리므로 캐시에서만 답한다.
     */
    override fun setEffects(player: Player): List<CustomItemHook.SetEffects> = plugin.stats.of(player).effects

    /** 블록으로 놓이는 우리 아이템인가 — 랜덤박스가 상자 모양으로 고를 때 묻는다. */
    override fun isBlock(ref: ItemRef.Namespaced): Boolean =
        ref.namespace.equals(namespace, ignoreCase = true) && plugin.items.get(ref.id)?.block != null

    override fun placeBlock(block: org.bukkit.block.Block, ref: ItemRef.Namespaced): Boolean {
        if (!ref.namespace.equals(namespace, ignoreCase = true)) return false
        val item = plugin.items.get(ref.id) ?: return false
        return plugin.blocks.place(block, item)
    }

    /** 엔티티 모델·후렴초 표시를 치운다. 블록 자체는 부른 쪽(랜덤박스의 원래 블록 되돌리기)이 정한다. */
    override fun removeBlock(block: org.bukkit.block.Block): Boolean = plugin.blocks.clear(block)
}
