package com.inmc.customitems.listener

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.ItemBuilder
import com.inmc.customitems.item.ItemInstance
import com.inmc.customitems.item.Tier
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Item
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.ItemMergeEvent
import org.bukkit.event.inventory.InventoryPickupItemEvent
import org.bukkit.event.player.PlayerAttemptPickupItemEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scoreboard.Team

/**
 * 바닥에 떨어진 커스텀 아이템 — 위에 **이름**(2개 이상이면 `x수량`), 둘레에 **등급색 발광**(테섭 요청 2026-10-02).
 *
 * - 월드에 들어올 때마다(떨어짐·청크가 다시 읽힘) `EntityAddToWorldEvent` 에서 입힌다. 합쳐지거나 일부만 주워지면 다음 틱에 수량을 다시 적는다.
 * - 발광 색은 바닐라가 **팀 색**으로만 정한다 — 등급마다 팀 `inmc_tier_<등급>`(색 = 등급색). 팀에 적힌 아이템은 월드를 떠날 때 지운다
 *   (팀 목록은 서버에 저장되므로 안 지우면 쌓인다). 켤 때 팀의 낡은 항목을 비우고 이미 있는 아이템에 다시 입힌다.
 * - 바닐라 아이템은 건드리지 않는다. 우리가 입힌 아이템에는 표시(`inmc:dropped_look`)를 달아 설정을 끌 때 우리 것만 벗긴다.
 */
class DroppedItemListener(private val custom: CustomItems) : Listener {

    /** `config.yml` 의 `dropped-items`. */
    data class Settings(val name: Boolean = true, val glow: Boolean = true) {
        companion object {
            fun from(yaml: YamlConfiguration) = Settings(
                name = yaml.getBoolean("dropped-items.name", true),
                glow = yaml.getBoolean("dropped-items.glow", true),
            )
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onAdd(event: EntityAddToWorldEvent) {
        (event.entity as? Item)?.let(::decorate)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onRemove(event: EntityRemoveFromWorldEvent) {
        val item = event.entity as? Item ?: return
        if (!item.persistentDataContainer.has(MARK)) return
        for (team in teams()) if (team.hasEntity(item)) team.removeEntity(item)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMerge(event: ItemMergeEvent) = later(event.target)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPickup(event: PlayerAttemptPickupItemEvent) {
        if (event.remaining > 0) later(event.item)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onHopper(event: InventoryPickupItemEvent) = later(event.item)

    private fun later(item: Item) {
        item.scheduler.runDelayed(custom.plugin, { if (item.isValid) decorate(item) }, null, 1L)
    }

    fun decorate(item: Item) {
        val stack = item.itemStack
        val definition = custom.items.identify(stack)
        val settings = custom.droppedSettings
        val ours = item.persistentDataContainer.has(MARK)
        if (definition == null) {
            if (ours) strip(item)
            return
        }
        item.persistentDataContainer.set(MARK, PersistentDataType.BYTE, 1)
        if (settings.name) {
            val name = stack.effectiveName()
            item.customName(if (stack.amount > 1) name.append(Component.text(" x" + stack.amount, NamedTextColor.GRAY)) else name)
            item.isCustomNameVisible = true
        } else {
            item.customName(null)
            item.isCustomNameVisible = false
        }
        val tier = ItemBuilder.tierOf(definition, ItemInstance.read(stack), custom.items.lookup)
        val target = if (settings.glow) team(tier) else null
        for (team in teams()) if (team !== target && team.hasEntity(item)) team.removeEntity(item)
        target?.addEntity(item)
        item.isGlowing = settings.glow
    }

    private fun strip(item: Item) {
        item.persistentDataContainer.remove(MARK)
        item.customName(null)
        item.isCustomNameVisible = false
        item.isGlowing = false
        for (team in teams()) if (team.hasEntity(item)) team.removeEntity(item)
    }

    /** 켤 때·설정을 다시 읽었을 때 — 낡은 팀 항목을 비우고 지금 있는 아이템에 다시 입힌다. */
    fun refreshAll() {
        for (team in teams()) for (entry in team.entries.toList()) if (UUID_TEXT.matches(entry)) team.removeEntry(entry)
        for (world in Bukkit.getWorlds()) for (item in world.getEntitiesByClass(Item::class.java)) decorate(item)
    }

    private fun teams(): List<Team> = Tier.entries.map(::team)

    /** 등급 팀 — 없으면 만들고, 색은 늘 등급색으로 맞춘다. */
    private fun team(tier: Tier): Team {
        val board = Bukkit.getScoreboardManager().mainScoreboard
        val team = board.getTeam(TEAM_PREFIX + tier.id) ?: board.registerNewTeam(TEAM_PREFIX + tier.id)
        val color = colorOf(tier)
        // 갓 만든 팀은 색이 없고, 그때 `color()` 는 값을 주지 않고 던진다(2026-10-02 테섭 — 켜질 때 준비가 안 끝났다).
        if (!team.hasColor() || team.color() != color) team.color(color)
        return team
    }

    companion object {
        private const val TEAM_PREFIX = "inmc_tier_"
        private val MARK = NamespacedKey("inmc", "dropped_look")
        private val UUID_TEXT = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

        /** 등급색(`<gold>` 같은 이름 태그) → 발광 색. */
        fun colorOf(tier: Tier): NamedTextColor =
            NamedTextColor.NAMES.value(tier.color.removePrefix("<").removeSuffix(">")) ?: NamedTextColor.WHITE
    }
}
