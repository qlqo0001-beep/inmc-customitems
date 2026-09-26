package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import com.inmc.customitems.craft.CraftService
import com.inmc.customitems.craft.Part
import com.inmc.customitems.craft.Station
import com.inmc.customitems.craft.StationRecipe
import com.inmc.customitems.util.Ph
import kr.inmc.core.gui.ConfirmMenu
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.util.Durations
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack

private val CRAFT_ID = Regex("^[a-z0-9_]{1,32}$")

/** 결과의 겉모습. 결과가 없으면 종이. */
private fun resultIcon(custom: CustomItems, recipe: StationRecipe): ItemStack =
    recipe.results.firstOrNull()?.let { custom.crafting.resolver.icon(it.item).stack.clone().also { s -> s.amount = it.amount.coerceIn(1, 64) } }
        ?: ItemStack(Material.PAPER)

/**
 * 제작대(플레이어). 위 네 줄은 조합법, 다섯째 줄은 대기열.
 *
 * 재료 줄은 **지금 가방에 있는 수**를 같이 보여준다(✔ 12/8 · ✘ 3/8) — 무엇이 모자란지 따로 찾지 않게.
 */
class StationMenu(custom: CustomItems, private val viewer: Player, private val id: String, private val back: (() -> Unit)? = null) :
    Menu(custom, 54, Text.renderFlat("<dark_gray>" + (custom.stations.get(id)?.name ?: id) + "</dark_gray>")) {

    private var page = 0

    override fun draw() {
        clear()
        val station = custom.stations.get(id) ?: return viewer.closeInventory()
        page = Paging.clamp(page, station.recipes.size, RECIPES)
        for ((slot, recipe) in Paging.slice(station.recipes, page, RECIPES).withIndex()) {
            set(slot, Icon.annotate(resultIcon(custom, recipe), lore = recipeLore(recipe))) { craft(station, recipe) }
        }
        val queue = custom.crafting.queue(viewer.uniqueId, station.id)
        val now = System.currentTimeMillis()
        for (index in 0 until CraftService.QUEUE) {
            val entry = queue.firstOrNull { it.index == index }
            val recipe = entry?.let { station.recipe(it.recipe) }
            if (entry == null) {
                set(FIRST_QUEUE + index, Icon.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<gray>빈 대기열</gray>"))
                continue
            }
            val left = ((entry.doneAt - now + 999) / 1000).coerceAtLeast(0)
            val icon = recipe?.let { resultIcon(custom, it) } ?: ItemStack(Material.BARRIER)
            set(FIRST_QUEUE + index, Icon.annotate(icon, lore = listOf("", if (left > 0) "<yellow>남은 시간 " + Durations.formatShort(left) + "</yellow>" else "<green>▶ 클릭: 받기</green>"))) {
                if (custom.crafting.claim(viewer, station, index)) {
                    viewer.playSound(viewer.location, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f)
                    custom.messages.send(viewer, "craft-claimed")
                }
                refresh()
            }
        }
        if (page > 0) set(Paging.SLOT_PREV, Icon.prevPage()) { page--; refresh() }
        if (page < Paging.pageCount(station.recipes.size, RECIPES) - 1) set(Paging.SLOT_NEXT, Icon.nextPage()) { page++; refresh() }
        set(SLOT_REFRESH, Icon.of(Material.CLOCK, "<yellow>새로 보기</yellow>", listOf("<gray>대기열의 남은 시간을 다시 봅니다.</gray>"))) { refresh() }
        set(SLOT_EVOLVE, Icon.of(Material.DRAGON_BREATH, "<light_purple>진화</light_purple>", listOf(
            "<gray>최대 강화한 아이템을 재료를 내고 진화시킵니다.</gray>", "", "<yellow>▶ 클릭</yellow>",
        ))) { EvolveMenu(custom, viewer, id) { open(viewer) }.open(viewer) }
        back?.let { go -> set(Paging.SLOT_BACK, Icon.back()) { go() } }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun recipeLore(recipe: StationRecipe): List<String> = buildList {
        add("")
        add("<yellow>재료</yellow>")
        for (part in recipe.ingredients) {
            val have = custom.crafting.count(viewer, part)
            val mark = if (have >= part.amount) "<green>✔" else "<red>✘"
            add(mark + " <white>" + part.item.label() + "</white> <gray>" + have + "/" + part.amount + "</gray>")
        }
        if (recipe.results.size > 1) {
            add("<yellow>결과</yellow>")
            for (part in recipe.results) add("<gray>· " + part.item.label() + " ×" + part.amount + "</gray>")
        }
        if (recipe.level > 0) add((if (viewer.level >= recipe.level) "<green>" else "<red>") + "요구 레벨 " + recipe.level)
        if (recipe.permission.isNotBlank() && !viewer.hasPermission(recipe.permission)) add("<red>권한이 필요합니다")
        add(if (recipe.seconds > 0) "<gray>걸리는 시간 <white>" + Durations.formatShort(recipe.seconds.toLong()) + "</white></gray>" else "<gray>바로 만들어집니다</gray>")
        add("")
        add("<yellow>▶ 클릭: 만들기</yellow>")
    }

    private fun craft(station: Station, recipe: StationRecipe) {
        val outcome = custom.crafting.craft(viewer, station, recipe)
        when (outcome) {
            CraftService.Outcome.CRAFTED -> viewer.playSound(viewer.location, Sound.BLOCK_ANVIL_USE, 0.6f, 1.2f)
            CraftService.Outcome.QUEUED -> viewer.playSound(viewer.location, Sound.BLOCK_SMITHING_TABLE_USE, 0.8f, 1f)
            else -> viewer.playSound(viewer.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1f)
        }
        custom.messages.send(viewer, "craft-" + outcome.name.lowercase().replace('_', '-'), Ph.of().amount(recipe.level))
        refresh()
    }

    private companion object {
        const val RECIPES = 36
        const val FIRST_QUEUE = 36
        const val SLOT_REFRESH = 49
        const val SLOT_EVOLVE = 51
    }
}

/**
 * 제작대의 진화(플레이어). 가방에서 **이 제작대에서 지금 진화할 수 있는 것**만 보여준다 — 아이템을 창에 올려놓게 하지
 * 않는 것은 창을 닫을 때 올려둔 아이템을 돌려주는 경로가 사고(복사·증발)의 흔한 원인이라서다.
 */
class EvolveMenu(custom: CustomItems, private val viewer: Player, private val stationId: String, private val back: () -> Unit) :
    Menu(custom, 54, Text.renderFlat("<dark_gray>진화 — " + (custom.stations.get(stationId)?.name ?: stationId) + "</dark_gray>")) {

    private fun candidates(): List<Pair<Int, com.inmc.customitems.item.CustomItem>> =
        viewer.inventory.storageContents.withIndex().mapNotNull { (index, stack) ->
            val definition = custom.items.usable(stack) ?: return@mapNotNull null
            val evolution = definition.upgrade.evolution ?: return@mapNotNull null
            if (!evolution.byStation || (evolution.station.isNotBlank() && evolution.station != stationId) || stack!!.amount != 1) return@mapNotNull null
            if (!com.inmc.customitems.item.Upgrades.canEvolve(definition, com.inmc.customitems.item.ItemInstance.read(stack).level, custom.items.lookup)) return@mapNotNull null
            index to definition
        }

    override fun draw() {
        clear()
        val found = candidates()
        for ((slot, entry) in found.take(Paging.PER_PAGE).withIndex()) {
            val (index, definition) = entry
            val evolution = definition.upgrade.evolution ?: continue
            val stack = viewer.inventory.getItem(index) ?: continue
            val lore = buildList {
                add("")
                add("<light_purple>진화 → <white>" + (custom.items.get(evolution.into)?.label() ?: evolution.into) + "</white></light_purple>")
                add("<gray>" + evolution.keep.description + "</gray>")
                if (evolution.materials.isNotEmpty()) add("<yellow>재료</yellow>")
                for (part in evolution.materials) {
                    val have = custom.crafting.count(viewer, part)
                    add((if (have >= part.amount) "<green>✔" else "<red>✘") + " <white>" + part.item.label() + "</white> <gray>" + have + "/" + part.amount + "</gray>")
                }
                add("")
                add("<yellow>▶ 클릭: 진화</yellow>")
            }
            set(slot, Icon.annotate(stack.clone(), lore = lore)) { evolve(index) }
        }
        if (found.isEmpty()) {
            set(SLOT_EMPTY, Icon.of(Material.BARRIER, "<gray>여기서 진화할 수 있는 아이템이 없습니다</gray>", listOf(
                "<gray>최대 강화한 아이템을 가방에 넣고 오세요.</gray>",
            )))
        }
        set(Paging.SLOT_BACK, Icon.back()) { back() }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun evolve(index: Int) {
        // 누르는 사이 가방이 바뀌었을 수 있다 — 다시 확인한다.
        val definition = candidates().firstOrNull { it.first == index }?.second ?: return refresh()
        val evolution = definition.upgrade.evolution ?: return refresh()
        if (evolution.materials.any { custom.crafting.count(viewer, it) < it.amount }) {
            custom.messages.send(viewer, "evolve-missing")
            viewer.playSound(viewer.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1f)
            return
        }
        val stack = viewer.inventory.getItem(index) ?: return refresh()
        val evolved = custom.upgrading.evolve(viewer, stack, definition) ?: return refresh()
        for (part in evolution.materials) custom.crafting.take(viewer, part)
        viewer.inventory.setItem(index, evolved)
        viewer.playSound(viewer.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f)
        custom.messages.send(viewer, "evolved", Ph.of().item(custom.items.get(evolution.into)?.label() ?: evolution.into))
        custom.stats.invalidate(viewer)
        refresh()
    }

    private companion object {
        const val SLOT_EMPTY = 22
    }
}

/**
 * 재료·결과 칸. **칸이 곧 목록이다** — 넣으면 들어가고 빼면 빠진다. 닫을 때(뒤로 포함) 저장한다.
 * 넣은 그대로 알아본다(우리 아이템은 id 로, 바닐라는 재질로, 손으로 만든 것은 통째로).
 */
class PartGridMenu(
    custom: CustomItems,
    private val viewer: Player,
    title: String,
    private val load: () -> List<Part>?,
    private val save: (List<Part>) -> Unit,
    private val back: () -> Unit,
) : Menu(custom, 54, Text.renderFlat("<dark_gray>$title</dark_gray>")) {

    override fun isSlotEditable(slot: Int): Boolean = slot < GRID

    override fun acceptsShiftInsert(): Boolean = true

    override fun draw() {
        clear()
        val parts = load() ?: return viewer.closeInventory()
        for ((slot, part) in parts.take(GRID).withIndex()) {
            inventory.setItem(slot, custom.crafting.resolver.icon(part.item).stack.clone().also { it.amount = part.amount.coerceIn(1, 64) })
        }
        set(SLOT_INFO, Icon.of(Material.PAPER, "<yellow>아이템을 끌어다 넣으세요</yellow>", listOf(
            "<gray>이 칸이 곧 목록입니다. 빼면 빠집니다.</gray>",
            "<gray>개수는 넣은 개수 그대로입니다(64개까지).</gray>",
            "<gray>닫거나 뒤로 가면 저장합니다.</gray>",
        )))
        set(Paging.SLOT_BACK, Icon.back()) { back() }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    override fun onClose(event: InventoryCloseEvent) {
        if (load() == null) return
        save((0 until GRID).mapNotNull { slot ->
            val stack = inventory.getItem(slot)?.takeIf { !it.type.isAir } ?: return@mapNotNull null
            Part(custom.crafting.resolver.capture(stack), stack.amount)
        })
    }

    private companion object {
        const val GRID = 45
        const val SLOT_INFO = 49
    }
}

/** 제작대 목록(관리). */
class StationListMenu(custom: CustomItems, private val viewer: Player) : Menu(custom, 54, Text.renderFlat("<dark_gray>제작대</dark_gray>")) {

    override fun draw() {
        clear()
        for ((index, station) in custom.stations.all().take(LIST).withIndex()) {
            set(index, Icon.of(Material.SMITHING_TABLE, "<yellow>" + station.name + "</yellow>", listOf(
                "<gray>id <white>" + station.id + "</white> · 조합법 <white>" + station.recipes.size + "</white>개 · 연결 블록 <white>" + station.blocks.size + "</white>개</gray>",
                "", "<yellow>▶ 클릭: 편집</yellow>",
            ))) { StationEditMenu(custom, viewer, station.id).open(viewer) }
        }
        set(SLOT_ADD, Icon.of(Material.LIME_DYE, "<green>새 제작대</green>", listOf("<gray>id 를 적으면 만들어집니다.</gray>"))) {
            Editors.promptText(custom.prompts, viewer, "제작대 id", listOf("<gray>소문자 영문·숫자·밑줄. 예: <white>forge</white></gray>"), reopen = { open(viewer) }) { raw ->
                val id = raw.trim().lowercase()
                if (!CRAFT_ID.matches(id) || custom.stations.get(id) != null) {
                    viewer.sendMessage(Text.render("<red>쓸 수 없는 id 입니다: $id</red>"))
                    return@promptText
                }
                custom.stations.put(Station(id, id))
                StationEditMenu(custom, viewer, id).open(viewer)
            }
        }
        set(SLOT_RECIPES, Icon.of(Material.CRAFTING_TABLE, "<yellow>바닐라 조합법 <white>" + custom.recipes.all().size + "</white>개</yellow>",
            listOf("<gray>작업대·화로·대장장이대·석재 절단기에 조합법을 더합니다.</gray>", "", "<yellow>▶ 클릭</yellow>"))) { RecipeListMenu(custom, viewer).open(viewer) }
        set(Paging.SLOT_BACK, Icon.back()) { ItemTypeMenu(custom, viewer).open(viewer) }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private companion object {
        const val LIST = 36
        const val SLOT_ADD = 48
        const val SLOT_RECIPES = 50
    }
}

/** 제작대 하나. 이름·조합법·연결 블록. */
class StationEditMenu(custom: CustomItems, private val viewer: Player, private val id: String) :
    Menu(custom, 54, Text.renderFlat("<dark_gray>제작대 — $id</dark_gray>")) {

    private fun station(): Station? = custom.stations.get(id)

    private fun mutate(change: (Station) -> Station) {
        station()?.let { custom.stations.put(change(it)) }
    }

    override fun draw() {
        clear()
        val station = station() ?: return StationListMenu(custom, viewer).open(viewer)
        for ((index, recipe) in station.recipes.take(LIST).withIndex()) {
            set(index, Icon.annotate(resultIcon(custom, recipe), lore = listOf(
                "", "<gray>id <white>" + recipe.id + "</white> · 재료 <white>" + recipe.ingredients.size + "</white>종</gray>",
                "<yellow>▶ 클릭: 편집</yellow>",
            ))) { StationRecipeMenu(custom, viewer, id, recipe.id).open(viewer) }
        }
        set(SLOT_ADD, Icon.of(Material.LIME_DYE, "<green>조합법 추가</green>", listOf("<gray>id 를 적으면 만들어집니다.</gray>"))) {
            Editors.promptText(custom.prompts, viewer, "조합법 id", listOf("<gray>소문자 영문·숫자·밑줄</gray>"), reopen = { open(viewer) }) { raw ->
                val recipeId = raw.trim().lowercase()
                if (!CRAFT_ID.matches(recipeId) || station()?.recipe(recipeId) != null) {
                    viewer.sendMessage(Text.render("<red>쓸 수 없는 id 입니다: $recipeId</red>"))
                    return@promptText
                }
                mutate { it.copy(recipes = it.recipes + StationRecipe(recipeId)) }
                StationRecipeMenu(custom, viewer, id, recipeId).open(viewer)
            }
        }
        set(SLOT_NAME, Icon.of(Material.NAME_TAG, "<yellow>이름: <white>" + station.name + "</white></yellow>", listOf("", "<yellow>▶ 클릭: 적기</yellow>"))) {
            Editors.promptText(custom.prompts, viewer, "제작대 이름", listOf("<gray>화면 제목이 됩니다.</gray>"), reopen = { open(viewer) }) { raw -> mutate { it.copy(name = raw.trim()) } }
        }
        set(SLOT_BLOCK, Icon.of(Material.LODESTONE, "<yellow>블록 연결 <white>" + station.blocks.size + "</white>개</yellow>", station.blocks.take(5).map { "<gray>· $it</gray>" } +
            listOf("<gray>바라보는 블록을 연결합니다. 그 블록을 우클릭하면 열립니다.</gray>", "", "<yellow>▶ 좌클릭: 바라보는 블록 연결 · 우클릭: 전부 끊기</yellow>"))) { event ->
            if (event.isRightClick) {
                mutate { it.copy(blocks = emptyList()) }
                refresh()
                return@set
            }
            val block = viewer.getTargetBlockExact(6)
            if (block == null || block.type.isAir) {
                viewer.sendMessage(Text.render("<red>6칸 안의 블록을 바라보세요.</red>"))
                return@set
            }
            val key = Station.blockKey(block.world.name, block.x, block.y, block.z)
            mutate { it.copy(blocks = (it.blocks - key) + key) }
            refresh()
        }
        set(SLOT_OPEN, Icon.of(Material.CRAFTING_TABLE, "<green>플레이어 화면으로 열기</green>")) { StationMenu(custom, viewer, id, back = { open(viewer) }).open(viewer) }
        set(SLOT_DELETE, Icon.of(Material.LAVA_BUCKET, "<red>제작대 지우기</red>", listOf("<gray>대기열에 든 것은 받을 수 없게 됩니다.</gray>"))) {
            ConfirmMenu(custom, "<red>제작대 $id 를 지울까요?</red>", onConfirm = {
                custom.stations.remove(id)
                StationListMenu(custom, viewer).open(viewer)
            }, onCancel = { open(viewer) }).open(viewer)
        }
        set(Paging.SLOT_BACK, Icon.back()) { StationListMenu(custom, viewer).open(viewer) }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private companion object {
        const val LIST = 36
        const val SLOT_ADD = 47
        const val SLOT_NAME = 48
        const val SLOT_BLOCK = 49
        const val SLOT_OPEN = 50
        const val SLOT_DELETE = 51
    }
}

/** 조합법 하나. 결과·재료(끌어다 넣기)·요구 레벨·권한·걸리는 시간. */
class StationRecipeMenu(custom: CustomItems, private val viewer: Player, private val stationId: String, private val recipeId: String) :
    Menu(custom, 54, Text.renderFlat("<dark_gray>조합법 — $recipeId</dark_gray>")) {

    private fun recipe(): StationRecipe? = custom.stations.get(stationId)?.recipe(recipeId)

    private fun change(transform: (StationRecipe) -> StationRecipe) {
        val station = custom.stations.get(stationId) ?: return
        custom.stations.put(station.copy(recipes = station.recipes.map { if (it.id == recipeId) transform(it) else it }))
    }

    override fun draw() {
        clear()
        val recipe = recipe() ?: return StationEditMenu(custom, viewer, stationId).open(viewer)
        set(SLOT_PREVIEW, Icon.annotate(resultIcon(custom, recipe), lore = listOf("", "<gray>결과의 첫 칸이 아이콘이 됩니다.</gray>")))
        set(SLOT_RESULTS, Icon.of(Material.CHEST, "<green>결과 " + recipe.results.size + "종</green>", recipe.results.map { "<gray>· " + it.item.label() + " ×" + it.amount + "</gray>" } +
            listOf("", "<yellow>▶ 클릭: 끌어다 넣기</yellow>"))) {
            PartGridMenu(custom, viewer, "결과 — $recipeId", load = { recipe()?.results }, save = { parts -> change { it.copy(results = parts) } }, back = { open(viewer) }).open(viewer)
        }
        set(SLOT_INGREDIENTS, Icon.of(Material.BARREL, "<yellow>재료 " + recipe.ingredients.size + "종</yellow>", recipe.ingredients.map { "<gray>· " + it.item.label() + " ×" + it.amount + "</gray>" } +
            listOf("", "<yellow>▶ 클릭: 끌어다 넣기</yellow>"))) {
            PartGridMenu(custom, viewer, "재료 — $recipeId", load = { recipe()?.ingredients }, save = { parts -> change { it.copy(ingredients = parts) } }, back = { open(viewer) }).open(viewer)
        }
        set(SLOT_LEVEL, Editors.intIcon(Material.EXPERIENCE_BOTTLE, "<yellow>요구 레벨</yellow>", recipe.level)) { event ->
            if (Editors.isPrompt(event)) {
                Editors.promptInt(custom.prompts, viewer, "요구 레벨", 0, 10_000, reopen = { open(viewer) }) { v -> change { it.copy(level = v) } }
                return@set
            }
            change { it.copy(level = (it.level + Editors.step(event, 1)).coerceAtLeast(0)) }
            refresh()
        }
        set(SLOT_PERMISSION, Icon.of(Material.IRON_BARS, "<yellow>요구 권한: <white>" + recipe.permission.ifBlank { "없음" } + "</white></yellow>", listOf("", "<yellow>▶ 좌클릭: 적기 · 우클릭: 없애기</yellow>"))) { event ->
            if (event.isRightClick) {
                change { it.copy(permission = "") }
                refresh()
                return@set
            }
            Editors.promptText(custom.prompts, viewer, "요구 권한", emptyList(), reopen = { open(viewer) }) { raw -> change { it.copy(permission = raw.trim()) } }
        }
        set(SLOT_SECONDS, Icon.of(Material.CLOCK, "<yellow>걸리는 시간: <white>" + (if (recipe.seconds > 0) Durations.formatShort(recipe.seconds.toLong()) else "바로") + "</white></yellow>",
            listOf("<gray>예: <white>30s · 5m · 1h · 0</white></gray>", "", "<yellow>▶ 클릭: 적기</yellow>"))) {
            Editors.promptText(custom.prompts, viewer, "걸리는 시간", listOf("<gray>예: <white>30s · 5m · 1h · 0</white></gray>"), reopen = { open(viewer) }) { raw ->
                change { it.copy(seconds = Durations.parse(raw.trim(), it.seconds.toLong()).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()) }
            }
        }
        set(SLOT_DELETE, Icon.of(Material.LAVA_BUCKET, "<red>조합법 지우기</red>", listOf("<gray>대기열에 든 이 조합법은 받을 수 없게 됩니다.</gray>"))) {
            ConfirmMenu(custom, "<red>조합법 $recipeId 를 지울까요?</red>", onConfirm = {
                custom.stations.get(stationId)?.let { custom.stations.put(it.copy(recipes = it.recipes.filterNot { r -> r.id == recipeId })) }
                StationEditMenu(custom, viewer, stationId).open(viewer)
            }, onCancel = { open(viewer) }).open(viewer)
        }
        set(Paging.SLOT_BACK, Icon.back()) { StationEditMenu(custom, viewer, stationId).open(viewer) }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private companion object {
        const val SLOT_PREVIEW = 4
        const val SLOT_RESULTS = 20
        const val SLOT_INGREDIENTS = 22
        const val SLOT_LEVEL = 29
        const val SLOT_PERMISSION = 31
        const val SLOT_SECONDS = 33
        const val SLOT_DELETE = 51
    }
}
