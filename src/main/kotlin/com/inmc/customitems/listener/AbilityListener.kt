package com.inmc.customitems.listener

import com.destroystokyo.paper.event.player.PlayerJumpEvent
import com.inmc.customitems.CustomItems
import com.inmc.customitems.ability.AbilityEngine
import com.inmc.customitems.ability.Trigger
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.Trident
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * 클릭·전투 밖의 발동 조건 — 웅크리기·점프·쏘기·블록 캐기·죽음.
 *
 * 웅크리기·점프는 아주 잦은 사건이다. 그 발동 조건을 가진 아이템이 하나도 없으면 장비조차 보지 않는다
 * ([com.inmc.customitems.item.ItemRegistry.hasTrigger]).
 */
class AbilityListener(private val custom: CustomItems) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSneak(event: PlayerToggleSneakEvent) {
        if (event.isSneaking) custom.abilities.fireEquipped(event.player, Trigger.SNEAK)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onJump(event: PlayerJumpEvent) = custom.abilities.fireEquipped(event.player, Trigger.JUMP)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDeath(event: PlayerDeathEvent) = custom.abilities.fireEquipped(event.player, Trigger.DEATH, event.player.killer)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) {
        if (!custom.items.hasTrigger(Trigger.BLOCK_BREAK)) return
        val item = custom.items.usable(event.player.inventory.itemInMainHand)?.takeIf(custom.equipmentSettings::worksOutside) ?: return
        if (custom.requirements.meets(event.player, item)) custom.abilities.fire(event.player, item, Trigger.BLOCK_BREAK)
    }

    /** 활·석궁. 발사체에 활의 id 를 적어 두고(맞았을 때 찾으려고) 쏘기 기능을 돌린다. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onShoot(event: EntityShootBowEvent) {
        val player = event.entity as? Player ?: return
        shot(player, event.bow, event.projectile as? Projectile)
    }

    /** 삼지창은 활 사건이 없다. 던진 삼지창이 든 아이템으로 본다. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onLaunch(event: ProjectileLaunchEvent) {
        val trident = event.entity as? Trident ?: return
        val player = trident.shooter as? Player ?: return
        shot(player, trident.itemStack, trident)
    }

    private fun shot(player: Player, weapon: ItemStack?, projectile: Projectile?) {
        val item = custom.items.usable(weapon)?.takeIf(custom.equipmentSettings::worksOutside) ?: return
        projectile?.persistentDataContainer?.set(AbilityEngine.SOURCE, PersistentDataType.STRING, item.id)
        if (custom.requirements.meets(player, item)) custom.abilities.fire(player, item, Trigger.SHOOT)
    }
}
