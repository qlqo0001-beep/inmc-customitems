package com.inmc.customitems.craft

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.ItemBuilder
import kr.inmc.core.item.ItemRef
import kr.inmc.core.item.StoredItem
import org.bukkit.Bukkit
import org.bukkit.Keyed
import org.bukkit.NamespacedKey
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.inventory.FurnaceSmeltEvent
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.event.inventory.PrepareSmithingEvent
import org.bukkit.inventory.BlastingRecipe
import org.bukkit.inventory.CampfireRecipe
import org.bukkit.inventory.FurnaceRecipe
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.Recipe
import org.bukkit.inventory.RecipeChoice
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.ShapelessRecipe
import org.bukkit.inventory.SmithingTransformRecipe
import org.bukkit.inventory.SmokingRecipe
import org.bukkit.inventory.StonecuttingRecipe

/**
 * [RecipeRegistry] 의 조합법을 서버에 건다.
 *
 * **바닐라는 재질로만 고른다.** 커스텀 재료는 그 재질로 걸고, 실제로 놓인 것이 맞는지는 준비 사건
 * (작업대·대장장이대)과 굽기 사건에서 우리가 본다([CraftService.matches]) — 아니면 철 주괴 아무거나로
 * "보석 주괴가 필요한" 조합이 된다. 거꾸로 바닐라 재료 칸에 우리 아이템을 놓아도 받지 않는다.
 *
 * 결과가 우리 아이템이면 **만들 때마다 새로 굴린다.**
 *
 * ## 조합법 책에는 진짜 아이템으로
 * 재질로만 걸면 조합법 책이 재료를 "토끼 가죽"처럼 맨 재질로 보여 준다. 그래서 바닐라가 아닌 재료는 **그 아이템 그대로**
 * (`ExactChoice`) 건다. 그런데 정확히 같아야만 맞으므로 강화한 것·옛 모습인 것은 작업대가 아예 못 알아본다 — 그래서 같은 모양을
 * 재질로만 건 **쌍둥이**(`recipe_<id>/any`)를 하나 더 걸고, 책에서는 뺀다(접속할 때 잊게 한다). 어느 쪽으로 맞든 확인은 아래
 * 사건에서 같게 한다.
 */
class RecipeService(private val custom: CustomItems) : Listener {

    private val registered = HashSet<NamespacedKey>()

    @Suppress("DEPRECATION")
    private fun key(id: String) = NamespacedKey(ItemBuilder.NAMESPACE, "recipe_$id")

    private fun idOf(recipe: Recipe?): String? {
        val key = (recipe as? Keyed)?.key ?: return null
        if (key.namespace != ItemBuilder.NAMESPACE || !key.key.startsWith("recipe_")) return null
        return key.key.removePrefix("recipe_").removeSuffix(TWIN)
    }

    /** 조합법 책에 보일 것(진짜 아이템으로 건 것)과 뺄 것(재질 쌍둥이). */
    private val shown = HashSet<NamespacedKey>()
    private val hidden = HashSet<NamespacedKey>()

    /** 걸었던 것을 다 떼고 지금 목록을 다시 건다. 못 거는 것(재료·결과 없음)은 건너뛰고 로그. */
    fun apply() {
        clear()
        for (def in custom.recipes.all()) {
            runCatching {
                val exact = build(def, key(def.id), exact = true) ?: return@runCatching
                if (Bukkit.addRecipe(exact, false)) registered += key(def.id).also { shown += it }
                // 바닐라 재료뿐이면 둘이 같다 — 쌍둥이가 필요 없다.
                if (def.grid.any { it != null && it.ref !is ItemRef.Vanilla }) {
                    val twin = NamespacedKey(key(def.id).namespace, key(def.id).key + TWIN)
                    val loose = build(def, twin, exact = false) ?: return@runCatching
                    if (Bukkit.addRecipe(loose, false)) registered += twin.also { hidden += it }
                }
            }.onFailure { custom.logger.warning("조합법 '${def.id}' 을(를) 걸 수 없습니다: ${it.message}") }
        }
        Bukkit.updateRecipes()
        for (player in Bukkit.getOnlinePlayers()) player.scheduler.run(custom.plugin, { showIn(player) }, null)
    }

    fun clear() {
        for (key in registered) Bukkit.removeRecipe(key, false)
        registered.clear()
        shown.clear()
        hidden.clear()
    }

    /** 책에 진짜 아이템 조합법을 올리고 재질 쌍둥이는 뺀다. */
    private fun showIn(player: org.bukkit.entity.Player) {
        if (shown.isNotEmpty()) player.discoverRecipes(shown)
        if (hidden.isNotEmpty()) player.undiscoverRecipes(hidden)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) = showIn(event.player)

    /** 바닐라는 재질로, 나머지는 [exact] 면 그 아이템 그대로(만들 수 없으면 재질로). */
    private fun choice(item: StoredItem, exact: Boolean): RecipeChoice {
        if (exact && item.ref !is ItemRef.Vanilla) custom.crafting.resolver.create(item, 1)?.let { return RecipeChoice.ExactChoice(it) }
        return RecipeChoice.MaterialChoice(item.material)
    }

    private fun build(def: RecipeDef, key: NamespacedKey, exact: Boolean): Recipe? {
        val result = def.result?.let { custom.crafting.resolver.create(it.item, it.amount) } ?: return null
        fun choice(item: StoredItem) = choice(item, exact)
        val first = def.slot(0)
        return when (def.kind) {
            RecipeKind.SHAPED -> {
                val (rows, cols) = bounds(def) ?: return null
                val recipe = ShapedRecipe(key, result)
                val symbols = "abcdefghi"
                recipe.shape(*rows.map { r -> cols.map { c -> if (def.slot(r * 3 + c) == null) ' ' else symbols[r * 3 + c] }.joinToString("") }.toTypedArray())
                for (r in rows) for (c in cols) def.slot(r * 3 + c)?.let { recipe.setIngredient(symbols[r * 3 + c], choice(it)) }
                recipe
            }
            RecipeKind.SHAPELESS -> {
                val items = def.grid.filterNotNull().ifEmpty { return null }
                ShapelessRecipe(key, result).also { r -> items.forEach { r.addIngredient(choice(it)) } }
            }
            RecipeKind.FURNACE -> FurnaceRecipe(key, result, choice(first ?: return null), def.exp.toFloat(), ticks(def))
            RecipeKind.BLASTING -> BlastingRecipe(key, result, choice(first ?: return null), def.exp.toFloat(), ticks(def))
            RecipeKind.SMOKING -> SmokingRecipe(key, result, choice(first ?: return null), def.exp.toFloat(), ticks(def))
            RecipeKind.CAMPFIRE -> CampfireRecipe(key, result, choice(first ?: return null), def.exp.toFloat(), ticks(def))
            RecipeKind.SMITHING -> SmithingTransformRecipe(key, result, choice(def.slot(0) ?: return null), choice(def.slot(1) ?: return null), choice(def.slot(2) ?: return null))
            RecipeKind.STONECUTTING -> StonecuttingRecipe(key, result, choice(first ?: return null))
        }
    }

    private fun ticks(def: RecipeDef): Int = (def.cookSeconds * 20).toInt().coerceAtLeast(1)

    private companion object {
        /** 재질로만 건 쌍둥이의 열쇠 꼬리. 조합법 id 에는 `/` 가 들어갈 수 없어 진짜 조합법과 겹치지 않는다. */
        const val TWIN = "/any"
    }

    /** 3×3 에서 재료가 있는 줄·칸의 범위. 작은 모양은 작업대 어디에 놓아도 되게 잘라 건다. */
    private fun bounds(def: RecipeDef): Pair<IntRange, IntRange>? {
        val filled = (0 until 9).filter { def.slot(it) != null }.ifEmpty { return null }
        return (filled.minOf { it / 3 }..filled.maxOf { it / 3 }) to (filled.minOf { it % 3 }..filled.maxOf { it % 3 })
    }

    private fun matches(stack: ItemStack?, item: StoredItem?): Boolean =
        if (item == null) stack == null || stack.type.isAir else custom.crafting.matches(stack, Part(item, 1))

    /** 결과. 우리 아이템이면 새로 굴린 것. */
    private fun fresh(def: RecipeDef): ItemStack? = def.result?.let { custom.crafting.resolver.create(it.item, it.amount) }

    // --- 확인 -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    fun onPrepareCraft(event: PrepareItemCraftEvent) {
        val def = custom.recipes.get(idOf(event.recipe) ?: return) ?: return
        val matrix = event.inventory.matrix.toList()
        val ok = when (def.kind) {
            RecipeKind.SHAPED -> shapedMatches(def, matrix)
            RecipeKind.SHAPELESS -> shapelessMatches(def, matrix)
            else -> false
        }
        event.inventory.result = if (ok) fresh(def) else null
    }

    /** 놓인 모양을 잘라 정의의 모양과 칸마다 맞춘다. 2×2(가방)와 3×3(작업대) 둘 다. */
    private fun shapedMatches(def: RecipeDef, matrix: List<ItemStack?>): Boolean {
        val width = if (matrix.size == 4) 2 else 3
        val placed = matrix.indices.filter { matrix[it] != null && !matrix[it]!!.type.isAir }
        val (rows, cols) = bounds(def) ?: return false
        if (placed.isEmpty()) return false
        val top = placed.minOf { it / width }
        val left = placed.minOf { it % width }
        for (r in rows) for (c in cols) {
            val index = (r - rows.first + top) * width + (c - cols.first + left)
            if (!matches(matrix.getOrNull(index), def.slot(r * 3 + c))) return false
        }
        return placed.size == (0 until 9).count { def.slot(it) != null }
    }

    /** 모양 없는 조합. 커스텀 재료부터 짝을 지어(더 까다로운 것부터) 바닐라 칸이 먼저 먹지 않게 한다. */
    private fun shapelessMatches(def: RecipeDef, matrix: List<ItemStack?>): Boolean {
        val stacks = matrix.filter { it != null && !it.type.isAir }.toMutableList()
        val wanted = def.grid.filterNotNull().sortedBy { if (it.ref is ItemRef.Vanilla) 1 else 0 }
        if (stacks.size != wanted.size) return false
        for (item in wanted) {
            val index = stacks.indexOfFirst { matches(it, item) }
            if (index < 0) return false
            stacks.removeAt(index)
        }
        return true
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPrepareSmithing(event: PrepareSmithingEvent) {
        val def = custom.recipes.get(idOf(event.inventory.recipe) ?: return) ?: return
        val inventory = event.inventory
        val ok = matches(inventory.inputTemplate, def.slot(0)) && matches(inventory.inputEquipment, def.slot(1)) && matches(inventory.inputMineral, def.slot(2))
        event.result = if (ok) fresh(def) else null
    }

    /**
     * 바닐라 조합법에 우리 아이템이 재료로 들어가면 결과를 막는다.
     *
     * 바닐라는 재질로만 고르므로 — 예: 판자로 만든 우리 아이템을 널빤지 칸에 놓으면 막대기·작업대가
     * 나온다. 우리 조합법이 아니면(`idOf == null`) 매트릭스에 우리 아이템이 하나라도 있으면 빈손이다.
     * 우리 쌍둥이(`recipe_<id>/any`)는 우리 것으로 판별돼 위(`onPrepareCraft`)가 본다.
     */
    @EventHandler(priority = EventPriority.HIGH)
    fun onVanillaCraft(event: PrepareItemCraftEvent) {
        if (idOf(event.recipe) != null) return
        if (event.inventory.matrix.any { custom.items.identify(it) != null }) event.inventory.result = null
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onSmelt(event: FurnaceSmeltEvent) {
        val def = custom.recipes.get(idOf(event.recipe) ?: return) ?: return
        if (!matches(event.source, def.slot(0))) {
            event.isCancelled = true
            return
        }
        fresh(def)?.let { event.result = it }
    }

    /**
     * 바닐라 대장장이법에 우리 아이템이 재료로 들어가면 결과를 막는다. 조합대와 같은 결이다 —
     * 바닐라는 재질로만 고르므로 다이아 재질의 우리 갑옷이 네더라이트 업그레이드에 먹힌다.
     * 우리 대장장이법은 위(`onPrepareSmithing`)가 본다.
     */
    @EventHandler(priority = EventPriority.HIGH)
    fun onVanillaSmithing(event: PrepareSmithingEvent) {
        if (idOf(event.inventory.recipe) != null) return
        val inventory = event.inventory
        if (listOf(inventory.inputTemplate, inventory.inputEquipment, inventory.inputMineral)
                .any { custom.items.identify(it) != null }
        ) {
            event.result = null
        }
    }

    /**
     * 화로에 우리 아이템이 재료·연료로 들어가면 들어가는 길에서 막는다.
     *
     * 시작 사건(`FurnaceStartSmeltEvent`)은 취소가 안 되고, 결과 사건(`FurnaceSmeltEvent`) 때 막으면
     * 재료가 이미 타서 아이템만 사라진다. 그래서 클릭·드래그·호퍼를 막는다. 용광로·훈연기도
     * 화로(`Furnace`)라 같이 막힌다. 우리 굽기법의 재료는 통과, 결과 칸(2번)은 꺼내기라 통과,
     * 바닐라 재료는 손대지 않는다.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onFurnaceClick(event: org.bukkit.event.inventory.InventoryClickEvent) {
        if (event.inventory.holder !is org.bukkit.block.Furnace) return
        val top = event.inventory.size
        val raw = event.rawSlot
        // 아래 가방에서 Shift — 화로로 들어가는 것만 막는다(꺼내는 Shift 는 통과).
        if (raw >= top) {
            if (!event.isShiftClick) return
            val moving = event.currentItem ?: return
            if (custom.items.identify(moving) == null || isOurCookInput(moving)) return
            event.isCancelled = true
            return
        }
        // 위 칸: 0 재료 · 1 연료 · 2 결과(꺼내기만). 놓이는 것만 본다.
        if (raw != 0 && raw != 1) return
        val landing: ItemStack? = when (event.action) {
            org.bukkit.event.inventory.InventoryAction.PLACE_ALL,
            org.bukkit.event.inventory.InventoryAction.PLACE_SOME,
            org.bukkit.event.inventory.InventoryAction.PLACE_ONE,
            org.bukkit.event.inventory.InventoryAction.SWAP_WITH_CURSOR ->
                event.view.cursor.takeUnless { it.type.isAir }
            // 숫자키(0~8 가방, 40 왼손) — F 키도 여기(40)로 온다.
            org.bukkit.event.inventory.InventoryAction.HOTBAR_SWAP ->
                if (event.hotbarButton == 40) {
                    event.view.player.inventory.itemInOffHand.takeUnless { it.type.isAir }
                } else {
                    event.view.bottomInventory.getItem(event.hotbarButton)
                }
            else -> null
        } ?: return
        if (custom.items.identify(landing) == null) return
        if (raw == 0 && isOurCookInput(landing)) return
        event.isCancelled = true
    }

    /** 드래그로 화로 재료·연료 칸에 밀어넣기. 용광로·훈연기도 화로다. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onFurnaceDrag(event: org.bukkit.event.inventory.InventoryDragEvent) {
        if (event.inventory.holder !is org.bukkit.block.Furnace) return
        val cursor = event.oldCursor
        if (custom.items.identify(cursor) == null) return
        val hitsInput = event.rawSlots.any { it == 0 }
        val hitsFuel = event.rawSlots.any { it == 1 }
        if (hitsInput && !isOurCookInput(cursor)) {
            event.isCancelled = true
            return
        }
        if (hitsFuel) event.isCancelled = true
    }

    /** 호퍼로 화로에 밀어넣기 — 클릭을 거치지 않는다. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onHopperToFurnace(event: org.bukkit.event.inventory.InventoryMoveItemEvent) {
        if (event.destination.holder !is org.bukkit.block.Furnace) return
        val stack = event.item
        if (custom.items.identify(stack) == null || isOurCookInput(stack)) return
        event.isCancelled = true
    }

    /**
     * 화로 연료에 우리 아이템이 들어가면 태우지 않는다. 연료로 쓰라고 만든 기능이 없고,
     * 태우면 아이템이 재가 되어 사라진다.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onFurnaceBurn(event: org.bukkit.event.inventory.FurnaceBurnEvent) {
        if (custom.items.identify(event.fuel) != null) event.isCancelled = true
    }

    /** 우리 굽기법(화로·용광로·훈연기·모닥불) 중 이 재료를 쓰는 것이 있는가. */
    private fun isOurCookInput(stack: ItemStack?): Boolean {
        if (stack == null || stack.type.isAir) return false
        return custom.recipes.all().any { def ->
            def.kind.cooking && matches(stack, def.slot(0))
        }
    }
}
