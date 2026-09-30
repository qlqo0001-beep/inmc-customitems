package com.inmc.customitems.craft

import com.inmc.customitems.CustomItems
import kr.inmc.core.CorePlugin
import kr.inmc.core.item.ItemMatcher
import kr.inmc.core.item.ItemRef
import kr.inmc.core.item.ItemResolver
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * 제작대에서 만든다 — 조건을 보고, 재료를 거두고, 결과를 주거나 대기열에 넣는다.
 *
 * **바닐라 재료는 우리 아이템을 받지 않는다.** core 의 바닐라 비교는 재질(과 이름)만 보므로 그대로 쓰면
 * 철 주괴로 만든 전설 아이템이 "철 주괴 1개"로 녹아 사라진다.
 *
 * 대기열은 core `PlayerStore` 의 `inmc_craft` 에 둔다(`제작대#번호` → 조합법·다 되는 시각). 접속을 끊어도
 * 시간은 흐르고, 한 제작대에 [QUEUE] 개까지.
 */
class CraftService(private val custom: CustomItems) {

    private val matcher = ItemMatcher(custom.mmoItems, custom.customItems)
    val resolver = ItemResolver(custom.mmoItems, custom.customItems, custom.logger)

    private val players get() = CorePlugin.get().players

    enum class Outcome { CRAFTED, QUEUED, LOW_LEVEL, NO_PERMISSION, MISSING, QUEUE_FULL, EMPTY }

    data class Queued(val index: Int, val recipe: String, val doneAt: Long)

    fun matches(stack: ItemStack?, part: Part): Boolean {
        if (stack == null || stack.type.isAir) return false
        if (part.item.ref is ItemRef.Vanilla && custom.items.identify(stack) != null) return false
        return matcher.matches(stack, part.item)
    }

    /** 가방(핫바 포함, 방어구·왼손 제외)에 있는 이 재료의 개수. */
    /** 가방과 배낭(core `CarriedStorage`, 사용자 결정 2026-09-30 — 배낭 안 물건도 가방처럼)에 든 [part] 의 개수. */
    fun count(player: Player, part: Part): Int =
        player.inventory.storageContents.sumOf { if (matches(it, part)) it!!.amount else 0 } +
            kr.inmc.core.integration.CarriedStorage.count(player) { matches(it, part) }

    fun meets(player: Player, recipe: StationRecipe): Boolean =
        player.level >= recipe.level && (recipe.permission.isBlank() || player.hasPermission(recipe.permission))

    /**
     * 만든다. 바로 되는 것은 결과를 주고, 시간이 걸리는 것은 대기열에 넣는다. 재료는 둘 다 **지금** 거둔다.
     */
    fun craft(player: Player, station: Station, recipe: StationRecipe, now: Long = System.currentTimeMillis()): Outcome {
        if (recipe.results.isEmpty()) return Outcome.EMPTY
        if (player.level < recipe.level) return Outcome.LOW_LEVEL
        if (recipe.permission.isNotBlank() && !player.hasPermission(recipe.permission)) return Outcome.NO_PERMISSION
        if (recipe.ingredients.any { count(player, it) < it.amount }) return Outcome.MISSING
        val slot = if (recipe.seconds > 0) freeSlot(player.uniqueId, station.id) ?: return Outcome.QUEUE_FULL else -1

        for (part in recipe.ingredients) take(player, part)
        if (recipe.seconds <= 0) {
            give(player, recipe.results)
            return Outcome.CRAFTED
        }
        val subject = station.id + "#" + slot
        players.set(player.uniqueId, NAMESPACE, subject, "recipe", recipe.id)
        players.set(player.uniqueId, NAMESPACE, subject, "done", now + recipe.seconds * 1000L)
        return Outcome.QUEUED
    }

    /** 이 제작대의 대기열. 번호 순. */
    fun queue(player: UUID, station: String): List<Queued> =
        players.subjects(player, NAMESPACE).mapNotNull { subject ->
            if (!subject.startsWith("$station#")) return@mapNotNull null
            val index = subject.substringAfter('#').toIntOrNull() ?: return@mapNotNull null
            val recipe = players.getString(player, NAMESPACE, subject, "recipe") ?: return@mapNotNull null
            Queued(index, recipe, players.getLong(player, NAMESPACE, subject, "done", 0L))
        }.sortedBy { it.index }

    /**
     * 다 된 것을 받는다. 조합법이 그 사이 지워졌으면 결과를 줄 수 없으니 **재료를 돌려주지도 못한다** —
     * 관리자가 조합법을 지울 때 알아야 할 일이라 편집 화면이 경고한다. 받았으면 true.
     */
    fun claim(player: Player, station: Station, index: Int, now: Long = System.currentTimeMillis()): Boolean {
        val entry = queue(player.uniqueId, station.id).firstOrNull { it.index == index } ?: return false
        if (entry.doneAt > now) return false
        station.recipe(entry.recipe)?.let { give(player, it.results) }
        players.clearSubject(player.uniqueId, NAMESPACE, station.id + "#" + index)
        return true
    }

    private fun freeSlot(player: UUID, station: String): Int? {
        val used = queue(player, station).map { it.index }.toSet()
        return (0 until QUEUE).firstOrNull { it !in used }
    }

    /** 가방에서, 모자라면 배낭에서 [part] 만큼 거둔다. 모자라는지는 [count] 로 먼저 본다. */
    fun take(player: Player, part: Part) {
        var left = part.amount
        val inventory = player.inventory
        for (index in inventory.storageContents.indices) {
            if (left <= 0) return
            val stack = inventory.getItem(index) ?: continue
            if (!matches(stack, part)) continue
            val taken = minOf(left, stack.amount)
            left -= taken
            if (taken >= stack.amount) inventory.setItem(index, null) else stack.amount -= taken
        }
        if (left > 0) kr.inmc.core.integration.CarriedStorage.take(player, left) { matches(it, part) }
    }

    /** 결과를 새로 만들어 준다(우리 아이템은 이때 굴린다). 가방이 차면 발밑으로. */
    fun give(player: Player, parts: List<Part>) {
        for (part in parts) {
            var left = part.amount
            while (left > 0) {
                val stack = resolver.create(part.item, left) ?: break
                val given = stack.amount.coerceAtLeast(1)
                for (overflow in player.inventory.addItem(stack).values) player.world.dropItemNaturally(player.location, overflow)
                left -= given
            }
        }
    }

    companion object {
        const val NAMESPACE = "inmc_craft"
        const val QUEUE = 9
    }
}
