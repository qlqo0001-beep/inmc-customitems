package com.inmc.customitems.player

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.util.Ph
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 아이템의 요구 조건([com.inmc.customitems.item.Requirement])을 본다.
 *
 * 모자란 것을 알리는 말은 **3초에 한 번만** — 한 번 휘두를 때마다 채팅이 차면 안 된다.
 */
class Requirements(private val custom: CustomItems) {

    private val warned = ConcurrentHashMap<UUID, Long>()

    fun meets(player: Player, definition: CustomItem): Boolean {
        val requirement = definition.requirement
        if (requirement.level > 0 && player.level < requirement.level) return false
        if (requirement.permission.isNotBlank() && !player.hasPermission(requirement.permission)) return false
        return true
    }

    /** 모자라면 알리고 false. */
    fun check(player: Player, definition: CustomItem): Boolean {
        if (meets(player, definition)) return true
        val now = System.currentTimeMillis()
        if (now - (warned[player.uniqueId] ?: 0L) >= WARN_EVERY) {
            warned[player.uniqueId] = now
            val requirement = definition.requirement
            if (requirement.level > 0 && player.level < requirement.level) {
                custom.messages.send(player, "require-level", Ph.of().item(definition.label()).amount(requirement.level))
            } else {
                custom.messages.send(player, "require-permission", Ph.of().item(definition.label()))
            }
        }
        return false
    }

    fun forget(id: UUID) {
        warned.remove(id)
    }

    private companion object {
        const val WARN_EVERY = 3000L
    }
}
