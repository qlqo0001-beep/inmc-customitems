package com.inmc.customitems.verify

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.ItemSet
import com.inmc.customitems.util.Ph
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Pig
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * `/커스텀아이템 검증` — 아이템·전투·기능·화면을 **서버 안에서 실제로 돌려** 확인한다. 서버 없이 도는 단위
 * 시험이 못 보는 것(아이템 메타·속성·이벤트 배선·화면 클릭)이 대상이다.
 *
 * 검사는 한 틱 안에서 차례로 돈다. 검사마다 가방·체력·레벨을 되돌리고, 검사용 정의(`zz_verify_…`)와 불러낸
 * 몹은 그 검사 끝에 지운다 — 같은 틱이라 저장 틱커가 그 사이의 정의를 디스크에 쓰지 않는다.
 */
class Verifier(private val custom: CustomItems) {

    enum class Mode(val label: String, val checks: () -> List<Check>) {
        ITEMS("아이템", { ItemChecks.ALL }),
        MENUS("화면", { MenuChecks.ALL }),
        ALL("전체", { ItemChecks.ALL + MenuChecks.ALL }),
    }

    data class Result(val name: String, val failure: String?)

    fun run(player: Player, mode: Mode) {
        // 서버의 "가방·손에서도 효과" — 검사는 기본값(켜짐)에서 돈다. 관리자가 꺼 둬도 부적 검사가 틀리지 않게.
        // **검증 전체에서 한 번만** 켜고 되돌린다 — 바꿀 때마다 모든 아이템의 지문을 다시 떠서, 검사마다 켰다 껐다 하면
        // 검사 수 × 아이템 수만큼 메인 스레드가 멈췄다(테섭 2026-10-08, 42개 검사에 15초 넘게).
        val inventoryEffects = custom.equipmentSettings.inventoryEffects
        custom.equipmentSettings.setInventoryEffects(true)
        val results = try {
            mode.checks().map { check -> Result(check.name, runOne(player, check)) }
        } finally {
            custom.equipmentSettings.setInventoryEffects(inventoryEffects)
        }
        val failures = results.filter { it.failure != null }
        custom.messages.send(player, "verify-done", Ph.of().value(mode.label).amount(results.size - failures.size).count(failures.size))
        for (failure in failures) custom.messages.send(player, "verify-failure", Ph.of().item(failure.name).value(failure.failure.orEmpty()))

        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val file = custom.io.file("verify", "${mode.name.lowercase()}-$stamp.txt")
        val text = buildString {
            appendLine("# inmc-customitems 검증 - ${mode.label} - ${LocalDateTime.now()}")
            for (row in results) appendLine((if (row.failure == null) "- 통과 " else "- 실패 ") + row.name + (row.failure?.let { " — $it" } ?: ""))
        }
        custom.io.asyncRun {
            file.parentFile.mkdirs()
            file.writeText(text)
        }
        custom.messages.send(player, "verify-report", Ph.of().value("plugins/${custom.plugin.name}/verify/${file.name}"))
    }

    /** 검사 하나. 사람을 되돌리고 만든 것을 지우는 것까지. 던지면 그 검사만 실패다. */
    private fun runOne(player: Player, check: Check): String? {
        val backup = Backup(custom, player)
        val sandbox = Sandbox(custom, player)
        player.closeInventory()
        player.inventory.clear()
        // 비운 가방·장착 칸으로 다시 맞춘다 — 안 하면 원래 장비가 사람에게 건 속성(최대 체력 등)이 기준값에 남는다.
        custom.stats.sync(player)
        return try {
            check.run(sandbox)
        } catch (t: Throwable) {
            "검증기 오류: " + t.javaClass.simpleName + (t.message?.let { ": $it" } ?: "")
        } finally {
            player.closeInventory()
            sandbox.clean()
            backup.restore()
            custom.abilities.forget(player.uniqueId)
            custom.consumes.forget(player.uniqueId)
            custom.styles.forget(player.uniqueId)
            custom.stats.sync(player)
        }
    }

    /** 검사 전의 사람. */
    private class Backup(private val custom: CustomItems, private val player: Player) {
        private val contents = player.inventory.contents.map { it?.clone() }.toTypedArray()

        /**
         * 장착 칸(`/장비`)도 가방처럼 비워 둔다 — 거기 든 부적·유물의 능력치가 검사에 섞인다(실제로 유물 하나가 치명타 2 를
         * 더해 네 검사가 틀렸다). 검사 하나는 한 틱 안에 끝나고 끝나면 그대로 돌려놓는다.
         */
        private val equipment = com.inmc.customitems.player.EquipmentStore.Group.entries.associateWith { group ->
            custom.equipment.slots(player.uniqueId, group).map { it?.clone() }
        }.also { saved ->
            for ((group, slots) in saved) for ((index, stack) in slots.withIndex()) if (stack != null) custom.equipment.put(player.uniqueId, group, index, null)
        }
        private val health = player.health
        private val food = player.foodLevel
        private val saturation = player.saturation
        private val level = player.level
        private val exp = player.exp
        private val effects: Collection<PotionEffect> = player.activePotionEffects.toList()

        fun restore() {
            player.setItemOnCursor(null)
            player.inventory.contents = contents
            for ((group, slots) in equipment) for ((index, stack) in slots.withIndex()) if (stack != null) custom.equipment.put(player.uniqueId, group, index, stack)
            for (effect in player.activePotionEffects) player.removePotionEffect(effect.type)
            player.addPotionEffects(effects)
            player.health = health.coerceAtMost(player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: health)
            player.foodLevel = food
            player.saturation = saturation
            player.level = level
            player.exp = exp
            player.fireTicks = 0
        }
    }
}

/** 검사 하나. 실패면 이유를, 통과면 null. */
class Check(val name: String, val run: (Sandbox) -> String?)

/** 검사 하나가 쓰는 무대. 만든 정의·세트·조합법·몹은 [clean] 이 지운다. */
class Sandbox(val custom: CustomItems, val player: Player) {

    private val items = LinkedHashSet<String>()
    private val sets = LinkedHashSet<String>()
    private val recipes = LinkedHashSet<String>()
    private val mobs = ArrayList<Entity>()
    private val tables = LinkedHashSet<String>()
    private val categories = LinkedHashSet<String>()
    private val types = LinkedHashSet<String>()
    private val equipped = ArrayList<Pair<com.inmc.customitems.player.EquipmentStore.Group, Int>>()
    private var inventoryEffects: Boolean? = null

    fun item(definition: CustomItem): CustomItem {
        custom.items.put(definition)
        items += definition.id
        return definition
    }

    fun set(set: ItemSet): ItemSet {
        custom.sets.put(set)
        sets += set.id
        return set
    }

    fun category(category: com.inmc.customitems.item.Category): com.inmc.customitems.item.Category {
        custom.categories.put(category)
        categories += category.id
        return category
    }

    fun type(type: com.inmc.customitems.item.TypeDef): com.inmc.customitems.item.TypeDef {
        custom.types.put(type)
        types += type.id
        return type
    }

    fun table(table: com.inmc.customitems.item.UpgradeTable) {
        custom.upgrades.put(table)
        tables += table.id
    }

    /** 장착 칸에 넣는다 — 끝에 비운다. */
    fun equip(group: com.inmc.customitems.player.EquipmentStore.Group, index: Int, stack: ItemStack) {
        custom.equipment.put(player.uniqueId, group, index, stack)
        equipped += group to index
    }

    /** 서버의 "장착 칸 밖에서도 효과" 를 바꾼다 — 끝에 되돌린다. */
    fun inventoryEffects(value: Boolean) {
        if (inventoryEffects == null) inventoryEffects = custom.equipmentSettings.inventoryEffects
        custom.equipmentSettings.setInventoryEffects(value)
    }

    fun recipe(recipe: com.inmc.customitems.craft.RecipeDef) {
        custom.recipes.put(recipe)
        recipes += recipe.id
    }

    fun stack(definition: CustomItem, amount: Int = 1): ItemStack =
        custom.items.create(definition.id, amount) ?: error(definition.id + " 를 못 만들었다")

    /** AI·중력 없는 돼지. 발을 [at] 에. */
    fun pig(at: Location): LivingEntity = at.world.spawn(at, Pig::class.java) {
        it.setAI(false)
        it.setGravity(false)
        it.isSilent = true
        it.isPersistent = false
    }.also { mobs += it }

    /** 눈앞 [distance] 칸, 시선이 몸통을 지나게. 그 사이가 막혀 있으면 null. */
    fun pigAhead(distance: Double): LivingEntity? {
        val eye = player.eyeLocation
        val direction = eye.direction.normalize()
        if (player.world.rayTraceBlocks(eye, direction, distance + 1.0) != null) return null
        return pig(eye.clone().add(direction.multiply(distance)).subtract(0.0, 0.45, 0.0))
    }

    /** 옆에서 [forward] 칸 앞, 발 높이. */
    fun groundAhead(forward: Double): Location {
        val flat = player.location.direction.setY(0).let { if (it.lengthSquared() == 0.0) org.bukkit.util.Vector(1, 0, 0) else it.normalize() }
        return player.location.clone().add(flat.multiply(forward))
    }

    /** 가방 [slot] 칸(9~35)에 [cursor] 를 놓는다 — 플레이어가 끌어다 놓은 것과 같은 사건. 막혔는지. */
    fun dropOnto(slot: Int, cursor: ItemStack?): Boolean {
        player.closeInventory()
        player.setItemOnCursor(cursor)
        val event = InventoryClickEvent(player.openInventory, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR)
        Bukkit.getPluginManager().callEvent(event)
        return event.isCancelled
    }

    /** 열린 화면의 [slot] 을 누른다. 막혔는지. */
    fun click(slot: Int, type: ClickType = ClickType.LEFT): Boolean {
        val action = if (type.isRightClick) InventoryAction.PICKUP_HALF else InventoryAction.PICKUP_ALL
        val event = InventoryClickEvent(player.openInventory, InventoryType.SlotType.CONTAINER, slot, type, action)
        Bukkit.getPluginManager().callEvent(event)
        return event.isCancelled
    }

    /** 지금 열린 화면. */
    fun top(): Any? = player.openInventory.topInventory.holder

    fun topName(): String = top()?.javaClass?.simpleName ?: player.openInventory.topInventory.type.name

    internal fun clean() {
        for ((group, index) in equipped) custom.equipment.put(player.uniqueId, group, index, null)
        inventoryEffects?.let(custom.equipmentSettings::setInventoryEffects)
        for (id in tables) custom.upgrades.remove(id)
        for (mob in mobs) mob.remove()
        for (id in recipes) custom.recipes.remove(id)
        for (id in items) custom.items.remove(id)
        for (id in sets) custom.sets.remove(id)
        for (id in categories) custom.categories.remove(id)
        for (id in types) custom.types.remove(id)
    }
}
