package com.inmc.customitems.player

import com.inmc.customitems.CustomItems
import com.inmc.customitems.ability.Trigger
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.ItemBuilder
import com.inmc.customitems.item.ItemInstance
import com.inmc.customitems.util.Ph
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 소모품을 쓴다. 우클릭([use])과 끌어다 놓는 힘(보석 빼기·수리)이 같은 [spend] 로 한 번을 치른다.
 *
 * 재사용 대기는 **저장하지 않는다**(기능 쿨다운과 같은 이유 — 재시작에 풀리는 것이 표준 동작).
 */
class ConsumeService(private val custom: CustomItems) {

    /** "사람|아이템" → 다시 쓸 수 있는 시각. */
    private val ready = ConcurrentHashMap<String, Long>()

    /**
     * 우클릭으로 쓴다. 쓰였으면 남은 스택(다 쓰면 null)을, 대기 중이면 [stack] 을 그대로 돌려준다.
     */
    fun use(player: Player, stack: ItemStack, definition: CustomItem): ItemStack? {
        val spec = definition.consume ?: return stack
        if (!cooldownPassed(player, definition)) return stack

        if (spec.health > 0.0) {
            val max = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
            player.health = (player.health + spec.health).coerceAtMost(max)
        } else if (spec.health < 0.0) {
            player.damage(-spec.health)
        }
        if (spec.food != 0) player.foodLevel = (player.foodLevel + spec.food).coerceIn(0, 20)
        if (spec.saturation != 0.0) player.saturation = (player.saturation + spec.saturation.toFloat()).coerceIn(0f, player.foodLevel.toFloat())
        custom.abilities.fire(player, definition, Trigger.CONSUME)
        player.playSound(player.location, Sound.ENTITY_GENERIC_DRINK, 1f, 1f)
        return spend(stack, definition)
    }

    /** 대기가 끝났으면 새로 걸고 true. 아니면 알리고 false. */
    fun cooldownPassed(player: Player, definition: CustomItem): Boolean {
        val seconds = definition.consume?.cooldown ?: 0.0
        if (seconds <= 0.0) return true
        val key = player.uniqueId.toString() + "|" + definition.id
        val now = System.currentTimeMillis()
        val until = ready[key] ?: 0L
        if (now < until) {
            custom.messages.send(player, "consume-cooldown", Ph.of().item(definition.label()).amount(((until - now + 999) / 1000).toInt()))
            return false
        }
        ready[key] = now + (seconds * 1000).toLong()
        return true
    }

    /**
     * 한 번을 치른다. 닳지 않는 것(0)은 그대로, 여러 번 쓰는 것은 남은 횟수를 줄여 다시 그리고, 다 쓰면 하나를 없앤다.
     * @return 남은 스택. 다 쓰였으면 null.
     */
    fun spend(stack: ItemStack, definition: CustomItem): ItemStack? {
        val uses = definition.consume?.uses ?: 1
        if (uses == 0) return stack
        val instance = ItemInstance.read(stack)
        val left = (instance.usesLeft ?: uses) - 1
        if (left > 0) {
            ItemBuilder.render(stack, definition, instance.copy(usesLeft = left), custom.items.lookup)
            return stack
        }
        if (stack.amount <= 1) return null
        stack.amount -= 1
        // 남은 것은 새것이다 — 여러 번 쓰는 소모품은 겹치지 않으니 여기 오는 것은 한 번짜리뿐이다.
        return stack
    }

    fun forget(id: UUID) {
        val prefix = "$id|"
        ready.keys.removeIf { it.startsWith(prefix) }
    }
}
