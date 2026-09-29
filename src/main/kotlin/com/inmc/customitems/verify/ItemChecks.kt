package com.inmc.customitems.verify

import com.inmc.customitems.ability.Ability
import com.inmc.customitems.ability.EffectType
import com.inmc.customitems.ability.Trigger
import com.inmc.customitems.craft.Part
import com.inmc.customitems.craft.RecipeDef
import com.inmc.customitems.craft.RecipeKind
import com.inmc.customitems.item.AttackStyle
import com.inmc.customitems.item.Components
import com.inmc.customitems.item.Evolution
import com.inmc.customitems.item.EvolveStone
import com.inmc.customitems.item.FailResult
import com.inmc.customitems.item.ItemType
import com.inmc.customitems.item.Tier
import com.inmc.customitems.item.UpgradeMode
import com.inmc.customitems.item.UpgradeSpec
import com.inmc.customitems.item.UpgradeStep
import com.inmc.customitems.item.UpgradeStone
import com.inmc.customitems.item.UpgradeTable
import com.inmc.customitems.item.ConsumeSpec
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.GemSpec
import com.inmc.customitems.item.ItemBuilder
import com.inmc.customitems.item.ItemInstance
import com.inmc.customitems.item.ItemModifier
import com.inmc.customitems.item.ItemSet
import com.inmc.customitems.item.Requirement
import com.inmc.customitems.item.SetBonus
import com.inmc.customitems.item.Spread
import com.inmc.customitems.item.Stat
import com.inmc.customitems.item.StatCalc
import kr.inmc.core.input.Clicks
import kr.inmc.core.item.ItemRef
import kr.inmc.core.item.StoredItem
import kr.inmc.core.util.Text
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.block.BlockFace
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.inventory.meta.LeatherArmorMeta

/** 아이템·전투·기능 검사. 전부 검사용 정의로 한다 — 서버에 있는 정의는 건드리지 않는다. */
object ItemChecks {

    private fun ok(condition: Boolean, why: String): String? = if (condition) null else why

    private fun name(stack: ItemStack?): String = stack?.let { Text.plain(it.effectiveName()) }.orEmpty()

    private fun lore(stack: ItemStack?): String = stack?.lore()?.joinToString(" / ") { Text.plain(it) }.orEmpty()

    private fun vanilla(material: Material) = StoredItem(ItemRef.Vanilla(material), material)

    private fun Sandbox.total(definition: CustomItem, stack: ItemStack?, stat: Stat): Double =
        StatCalc.total(definition, ItemInstance.read(stack), custom.items.lookup)[stat] ?: 0.0

    private fun Sandbox.interact(action: Action) {
        // 검증은 한 틱에 여러 번 누른다 — 앞의 누름이 이것을 유령 좌클릭으로 만들지 않게.
        Clicks.forget(player.uniqueId)
        Bukkit.getPluginManager().callEvent(PlayerInteractEvent(player, action, player.inventory.itemInMainHand, null, BlockFace.SELF, EquipmentSlot.HAND))
    }

    private fun Sandbox.gem(): CustomItem = item(
        CustomItem("zz_verify_gem", Material.EMERALD, stats = mapOf(Stat.CRIT_CHANCE to 7.0), gem = GemSpec(GemSpec.ANY, 100.0)),
    )

    /** 두 단계짜리 전용 표 — +1 은 반드시 성공, +2 는 반드시 실패하고 한 단계 내려간다. */
    private val TWO_STEPS = UpgradeTable("zz", "검증", UpgradeMode.ABSOLUTE, listOf(
        UpgradeStep(chance = 100.0, stats = mapOf(Stat.CRIT_CHANCE to 5.0), tier = Tier.RARE),
        UpgradeStep(chance = 0.0, fail = FailResult.DOWN, stats = mapOf(Stat.CRIT_CHANCE to 9.0)),
    ))

    private fun Sandbox.leveled(definition: CustomItem, level: Int): ItemStack =
        stack(definition).also { ItemBuilder.render(it, definition, ItemInstance.read(it).copy(level = level), custom.items.lookup) }

    val ALL: List<Check> = listOf(
        Check("강화석: 성공하면 +1, 실패하면 정한 대로, 지정 강화석만") { s ->
            val sword = s.item(CustomItem("zz_verify_upg", Material.IRON_SWORD, displayName = "검증검", upgrade = UpgradeSpec(own = TWO_STEPS.copy(id = "zz_verify_upg"))))
            val stone = s.item(CustomItem("zz_verify_ustone", Material.PRISMARINE_SHARD, consume = ConsumeSpec(uses = 0, upgrade = UpgradeStone())))
            s.player.inventory.setItem(9, s.stack(sword))
            s.dropOnto(9, s.stack(stone))
            val first = s.player.inventory.getItem(9)
            ok(ItemInstance.read(first).level == 1, "+1 이 안 됐다 (" + ItemInstance.read(first).level + ")")
                ?: ok(name(first).contains("+1"), "이름에 +1 이 없다: " + name(first))
                ?: ok(s.total(sword, first, Stat.CRIT_CHANCE) == 5.0, "+1 능력치가 " + s.total(sword, first, Stat.CRIT_CHANCE))
                ?: run {
                    s.dropOnto(9, s.stack(stone))
                    ok(ItemInstance.read(s.player.inventory.getItem(9)).level == 0, "0% 단계가 실패해 +0 으로 내려가야 하는데 " + ItemInstance.read(s.player.inventory.getItem(9)).level)
                }
                ?: run {
                    s.item(sword.copy(upgrade = sword.upgrade.copy(stones = listOf("zz_other_stone"))))
                    s.dropOnto(9, s.stack(stone))
                    ok(ItemInstance.read(s.player.inventory.getItem(9)).level == 0, "지정 강화석이 아닌데 강화됐다")
                }
        },
        Check("진화석: 최대 강화면 다음 아이템으로") { s ->
            val next = s.item(CustomItem("zz_verify_evo2", Material.DIAMOND_SWORD, displayName = "진화검"))
            val sword = s.item(CustomItem("zz_verify_evo1", Material.IRON_SWORD, upgrade = UpgradeSpec(
                own = UpgradeTable("zz_verify_evo1", steps = listOf(UpgradeStep())), evolution = Evolution(next.id),
            )))
            val stone = s.item(CustomItem("zz_verify_estone", Material.ECHO_SHARD, consume = ConsumeSpec(uses = 0, evolve = EvolveStone())))
            s.player.inventory.setItem(9, s.stack(sword))
            s.dropOnto(9, s.stack(stone))
            ok(s.custom.items.identify(s.player.inventory.getItem(9))?.id == sword.id, "최대 강화 전인데 진화했다") ?: run {
                s.player.inventory.setItem(9, s.leveled(sword, 1))
                s.dropOnto(9, s.stack(stone))
                ok(s.custom.items.identify(s.player.inventory.getItem(9))?.id == next.id, "최대 강화인데 진화하지 않았다")
            }
        },
        Check("부적: 같은 부적 중복 안 함이면 높은 단계 하나만, 유물은 하나만") { s ->
            val table = UpgradeTable("zz_verify_charm_t", mode = UpgradeMode.ABSOLUTE, steps = listOf(
                UpgradeStep(stats = mapOf(Stat.CRIT_CHANCE to 2.0)), UpgradeStep(stats = mapOf(Stat.CRIT_CHANCE to 7.0)),
            ))
            s.table(table)
            val charm = s.item(CustomItem("zz_verify_charm", Material.PAPER, type = ItemType.TALISMAN, noDuplicate = true, stats = mapOf(Stat.CRIT_CHANCE to 1.0), upgrade = UpgradeSpec(template = table.id)))
            s.player.inventory.setItem(9, s.leveled(charm, 1))
            s.player.inventory.setItem(10, s.leveled(charm, 2))
            s.custom.stats.invalidate(s.player)
            val single = s.custom.stats.of(s.player).stat(Stat.CRIT_CHANCE)
            val a = s.item(CustomItem("zz_verify_relic_a", Material.HEART_OF_THE_SEA, type = ItemType.RELIC, tier = Tier.RARE, stats = mapOf(Stat.LIFESTEAL to 3.0)))
            val b = s.item(CustomItem("zz_verify_relic_b", Material.HEART_OF_THE_SEA, type = ItemType.RELIC, tier = Tier.LEGENDARY, stats = mapOf(Stat.LIFESTEAL to 11.0)))
            s.player.inventory.setItem(11, s.stack(a))
            s.player.inventory.setItem(12, s.stack(b))
            s.custom.stats.invalidate(s.player)
            val lifesteal = s.custom.stats.of(s.player).stat(Stat.LIFESTEAL)
            ok(single == 7.0, "같은 부적 +1·+2 가 둘 다 붙었다(" + single + ", +2 의 7 이어야)") ?: ok(lifesteal == 11.0, "유물 둘이 겹쳤거나 낮은 등급이 붙었다(" + lifesteal + ")")
        },
        Check("장착 칸: 장신구의 바닐라 속성과 부적이 사람에게 붙는다") { s ->
            val ring = s.item(CustomItem("zz_verify_ring", Material.GOLD_NUGGET, type = ItemType.ACCESSORY, stats = mapOf(Stat.MAX_HEALTH to 4.0)))
            val charm = s.item(CustomItem("zz_verify_eqcharm", Material.PAPER, type = ItemType.TALISMAN, stats = mapOf(Stat.CRIT_CHANCE to 6.0)))
            val attribute = s.player.getAttribute(Attribute.MAX_HEALTH)!!
            val before = attribute.value
            s.equip(com.inmc.customitems.player.EquipmentStore.Group.ACCESSORY, 0, s.stack(ring))
            s.equip(com.inmc.customitems.player.EquipmentStore.Group.TALISMAN, 0, s.stack(charm))
            s.custom.stats.sync(s.player)
            val health = attribute.value
            val crit = s.custom.stats.of(s.player).stat(Stat.CRIT_CHANCE)
            s.custom.equipment.put(s.player.uniqueId, com.inmc.customitems.player.EquipmentStore.Group.ACCESSORY, 0, null)
            s.custom.equipment.put(s.player.uniqueId, com.inmc.customitems.player.EquipmentStore.Group.TALISMAN, 0, null)
            s.custom.stats.sync(s.player)
            ok(health == before + 4.0, "최대 체력 " + before + " → " + health + " (+4)")
                ?: ok(crit == 6.0, "장착한 부적 치명타 " + crit)
                ?: ok(attribute.value == before, "빼고 나서도 최대 체력이 " + attribute.value)
        },
        Check("장착 칸에서만: 서버 설정·아이템 설정대로 가방·손의 것은 멈춘다") { s ->
            val charm = s.item(CustomItem("zz_verify_slotonly", Material.PAPER, type = ItemType.TALISMAN, stats = mapOf(Stat.CRIT_CHANCE to 5.0)))
            val free = s.item(CustomItem("zz_verify_anywhere", Material.PAPER, type = ItemType.TALISMAN, inventoryEffect = true, stats = mapOf(Stat.LIFESTEAL to 3.0)))
            val ring = s.item(CustomItem("zz_verify_slotring", Material.GOLD_NUGGET, type = ItemType.ACCESSORY, inventoryEffect = false, stats = mapOf(Stat.MAX_HEALTH to 4.0)))
            val attribute = s.player.getAttribute(Attribute.MAX_HEALTH)!!
            val before = attribute.value
            s.inventoryEffects(false)
            s.player.inventory.setItem(9, s.stack(charm))
            s.player.inventory.setItem(10, s.stack(free))
            s.player.inventory.setItemInMainHand(s.stack(ring))
            s.custom.stats.sync(s.player)
            val bag = s.custom.stats.of(s.player)
            val held = attribute.value
            val lore = s.player.inventory.getItem(9)?.lore()?.joinToString(" ") { Text.plain(it) }.orEmpty()
            s.equip(com.inmc.customitems.player.EquipmentStore.Group.TALISMAN, 0, s.stack(charm))
            s.custom.stats.sync(s.player)
            val slotted = s.custom.stats.of(s.player).stat(Stat.CRIT_CHANCE)
            s.player.inventory.setItemInMainHand(null)
            s.custom.stats.sync(s.player)
            ok(bag.stat(Stat.CRIT_CHANCE) == 0.0, "서버 설정을 껐는데 가방의 부적이 붙었다(" + bag.stat(Stat.CRIT_CHANCE) + ")")
                ?: ok(bag.stat(Stat.LIFESTEAL) == 3.0, "가방에서도로 정한 부적이 안 붙었다(" + bag.stat(Stat.LIFESTEAL) + ")")
                ?: ok(held == before, "장착 칸에서만인 장신구를 손에 들었는데 최대 체력 " + before + " → " + held)
                ?: ok(lore.contains("장착 칸"), "로어가 장착 칸에 끼우라고 말하지 않는다: " + lore)
                ?: ok(slotted == 5.0, "장착 칸에 끼운 부적이 안 붙었다(" + slotted + ")")
        },
        Check("만들기·되알아보기·로어·속성") { s ->
            val def = s.item(CustomItem("zz_verify_blade", Material.IRON_SWORD, displayName = "검증 칼", stats = mapOf(Stat.ATTACK_DAMAGE to 5.0, Stat.CRIT_CHANCE to 10.0)))
            val stack = s.stack(def)
            val attributes = stack.itemMeta.attributeModifiers?.get(Attribute.ATTACK_DAMAGE).orEmpty()
            ok(s.custom.items.identify(stack)?.id == def.id, "만든 것을 되알아보지 못한다")
                ?: ok(name(stack).contains("검증 칼"), "이름이 '${name(stack)}'")
                ?: ok(lore(stack).contains("치명타 확률"), "로어에 치명타 확률 줄이 없다: ${lore(stack)}")
                ?: ok(attributes.any { it.key.namespace == ItemBuilder.NAMESPACE && it.amount == 5.0 }, "공격력 속성이 안 붙었다: $attributes")
        },
        Check("능력치 굴림이 폭 안에서 아이템마다 다르다") { s ->
            val def = s.item(CustomItem("zz_verify_roll", Material.IRON_SWORD, stats = mapOf(Stat.ATTACK_DAMAGE to 10.0), spreads = mapOf(Stat.ATTACK_DAMAGE to Spread(0.2, 0.5))))
            val values = (1..12).map { s.total(def, s.stack(def), Stat.ATTACK_DAMAGE) }
            ok(values.toSet().size > 1, "12개가 전부 같다: $values") ?: ok(values.all { it in 5.0..15.0 }, "폭(최대 50%) 밖: $values")
        },
        Check("수식어가 이름과 능력치를 바꾼다") { s ->
            val def = s.item(CustomItem(
                "zz_verify_mod", Material.IRON_SWORD, displayName = "검", stats = mapOf(Stat.ATTACK_DAMAGE to 4.0),
                modifiers = listOf(ItemModifier("sharp", "날카로운", chance = 100.0, stats = mapOf(Stat.ATTACK_DAMAGE to 3.0))),
            ))
            val stack = s.stack(def)
            ok(name(stack).startsWith("날카로운"), "이름이 '${name(stack)}'") ?: ok(s.total(def, stack, Stat.ATTACK_DAMAGE) == 7.0, "공격력이 ${s.total(def, stack, Stat.ATTACK_DAMAGE)} (4+3)")
        },
        Check("정의를 고치면 갱신이 다시 그린다") { s ->
            val def = s.item(CustomItem("zz_verify_update", Material.IRON_SWORD, stats = mapOf(Stat.CRIT_CHANCE to 5.0)))
            val stack = s.stack(def)
            s.item(def.copy(stats = mapOf(Stat.CRIT_CHANCE to 25.0)))
            ok(s.custom.items.refresh(stack), "정의를 고쳤는데 갱신이 '바뀐 것 없음'이라 한다") ?: ok(lore(stack).contains("25"), "로어가 옛 값이다: ${lore(stack)}")
        },
        Check("보석을 박고 뺀다") { s ->
            val gem = s.gem()
            val socketed = s.item(CustomItem("zz_verify_socket", Material.IRON_SWORD, sockets = listOf(GemSpec.ANY)))
            val chisel = s.item(CustomItem("zz_verify_chisel", Material.SHEARS, consume = ConsumeSpec(uses = 0, unsocket = true)))
            s.player.inventory.setItem(9, s.stack(socketed))
            s.dropOnto(9, s.stack(gem))
            val after = s.player.inventory.getItem(9)
            ok(ItemInstance.read(after).gem(0) == gem.id, "보석이 안 박혔다: ${ItemInstance.read(after).gems}")
                ?: ok(s.total(socketed, after, Stat.CRIT_CHANCE) == 7.0, "보석 능력치가 안 더해졌다")
                ?: run {
                    s.dropOnto(9, s.stack(chisel))
                    ok(ItemInstance.read(s.player.inventory.getItem(9)).gem(0) == null, "보석이 안 빠졌다")
                        ?: ok(s.player.inventory.contents.any { s.custom.items.identify(it)?.id == gem.id }, "빼낸 보석이 가방에 없다")
                }
        },
        Check("숫돌이 내구도를 고치고 닳는다") { s ->
            val tool = s.item(CustomItem("zz_verify_whetstone", Material.FLINT, consume = ConsumeSpec(uses = 1, repair = 100)))
            s.player.inventory.setItem(9, ItemStack(Material.IRON_SWORD).apply { editMeta(Damageable::class.java) { it.damage = 150 } })
            s.dropOnto(9, s.stack(tool))
            val damage = (s.player.inventory.getItem(9)?.itemMeta as? Damageable)?.damage
            ok(damage == 50, "손상이 $damage (150-100)") ?: ok(s.player.itemOnCursor.type.isAir, "1회용 숫돌이 안 사라졌다")
        },
        Check("소모품이 체력을 채우고 횟수가 준다") { s ->
            val potion = s.item(CustomItem("zz_verify_potion", Material.POTION, consume = ConsumeSpec(health = 4.0, uses = 2)))
            s.player.health = 10.0
            val left = s.custom.consumes.use(s.player, s.stack(potion), potion)
            ok(s.player.health == 14.0, "체력이 ${s.player.health} (10+4)") ?: ok(left != null && ItemInstance.read(left).usesLeft == 1, "남은 횟수가 ${left?.let { ItemInstance.read(it).usesLeft }}")
        },
        Check("바닐라 설치·소모 막기: 블록 재질은 안 놓이고 먹을 것은 먹는 부품이 빠진다") { s ->
            val stone = s.item(CustomItem("zz_verify_lock_stone", Material.STONE, preventVanillaUse = true))
            val bread = s.item(CustomItem("zz_verify_lock_bread", Material.BREAD, preventVanillaUse = true))
            val free = s.item(CustomItem("zz_verify_free_bread", Material.BREAD))
            val block = s.player.location.block
            val place = BlockPlaceEvent(block, block.state, block.getRelative(BlockFace.DOWN), s.stack(stone), s.player, true, EquipmentSlot.HAND)
            Bukkit.getPluginManager().callEvent(place)
            val locked = s.stack(bread)
            val eat = PlayerItemConsumeEvent(s.player, locked, EquipmentSlot.HAND)
            Bukkit.getPluginManager().callEvent(eat)
            @Suppress("UnstableApiUsage")
            val consumable = io.papermc.paper.datacomponent.DataComponentTypes.CONSUMABLE
            ok(place.isCancelled, "돌 재질 아이템이 놓였다")
                ?: ok(eat.isCancelled, "빵 재질 아이템이 먹혔다")
                ?: ok(!locked.hasData(consumable), "먹는 부품이 남아 있다 — 먹기가 시작된다")
                ?: ok(s.stack(free).hasData(consumable), "막지 않은 빵에서 먹는 부품이 빠졌다")
        },
        Check("배낭: 우클릭·장착 칸으로 같은 창고가 열리고, 넣은 것이 남고, 배낭 안에 배낭은 못 넣는다") { s ->
            val bag = s.item(CustomItem("zz_verify_bag", Material.PAPER, type = ItemType.TALISMAN, backpack = 50))
            s.player.inventory.setItemInMainHand(s.stack(bag))
            s.interact(Action.RIGHT_CLICK_AIR)
            val menu = s.top() as? com.inmc.customitems.gui.BackpackMenu ?: return@Check "우클릭해도 배낭이 안 열렸다(" + s.topName() + ")"
            val id = s.custom.backpacks.idOf(s.player.inventory.itemInMainHand) ?: return@Check "배낭 번호가 안 찍혔다"
            menu.inventory.setItem(0, ItemStack(Material.DIAMOND, 3))
            s.player.setItemOnCursor(s.stack(bag))
            val nested = s.click(1)
            s.player.setItemOnCursor(null)
            s.player.closeInventory()
            val kept = s.custom.backpacks.load(id)
            // 장착 칸 길 — 같은 배낭을 끼우고 /배낭 · G 와 같은 자리로 연다.
            s.equip(com.inmc.customitems.player.EquipmentStore.Group.TALISMAN, 0, s.player.inventory.itemInMainHand.clone())
            s.player.inventory.setItemInMainHand(null)
            s.custom.backpacks.openEquipped(s.player, com.inmc.customitems.player.EquipmentStore.Group.TALISMAN, 0)
            val again = s.top() as? com.inmc.customitems.gui.BackpackMenu
            s.player.closeInventory()
            s.custom.backpacks.save(id, emptyMap()) // 검사가 만든 창고 파일을 지운다(빈 창고는 파일을 지운다)
            ok(nested, "배낭 안에 배낭이 들어갔다")
                ?: ok(kept[0]?.type == Material.DIAMOND && kept[0]?.amount == 3, "넣은 다이아몬드가 저장되지 않았다: " + kept[0])
                ?: ok(again?.id == id, "장착 칸의 배낭이 같은 창고로 안 열렸다")
        },
        Check("요구 조건이 모자라면 능력치가 안 돈다") { s ->
            val def = s.item(CustomItem("zz_verify_req", Material.IRON_SWORD, stats = mapOf(Stat.CRIT_CHANCE to 10.0), requirement = Requirement(level = 100_000)))
            s.player.inventory.setItemInMainHand(s.stack(def))
            s.custom.stats.invalidate(s.player)
            val locked = s.custom.stats.of(s.player).stat(Stat.CRIT_CHANCE)
            s.item(def.copy(requirement = Requirement()))
            s.custom.stats.invalidate(s.player)
            val open = s.custom.stats.of(s.player).stat(Stat.CRIT_CHANCE)
            ok(locked == 0.0, "요구 레벨이 모자란데 치명타 $locked") ?: ok(open == 10.0, "요구 조건을 없앴는데 치명타 $open")
        },
        Check("세트 효과가 벌 수만큼 붙는다") { s ->
            val set = s.set(ItemSet("zz_verify_set", "검증 세트", mapOf(2 to SetBonus(stats = mapOf(Stat.CRIT_CHANCE to 15.0)))))
            val helmet = s.item(CustomItem("zz_verify_helmet", Material.IRON_HELMET, set = set.id))
            val chest = s.item(CustomItem("zz_verify_chest", Material.IRON_CHESTPLATE, set = set.id))
            s.player.inventory.setHelmet(s.stack(helmet))
            s.custom.stats.invalidate(s.player)
            val one = s.custom.stats.of(s.player).stat(Stat.CRIT_CHANCE)
            s.player.inventory.setChestplate(s.stack(chest))
            s.custom.stats.invalidate(s.player)
            val two = s.custom.stats.of(s.player).stat(Stat.CRIT_CHANCE)
            ok(one == 0.0, "한 벌인데 세트 효과 $one") ?: ok(two == 15.0, "두 벌인데 세트 효과 $two")
        },
        Check("세트의 인첸트 효과 단계가 벌 수만큼 core 로 넘어간다") { s ->
            // 뜻은 인첸트가 안다 — 여기서는 적힌 트리가 그대로(같은 객체로) 넘어가는지만 본다. 엔진이 도는 것은 인첸트 검증기가 본다.
            val effects = mapOf("events" to mapOf("EFFECT_STATIC" to mapOf("effects" to listOf("POTION:FIRE_RESISTANCE:0"))))
            val set = s.set(ItemSet("zz_verify_fx", "검증 효과 세트", mapOf(2 to SetBonus(effects = effects))))
            val helmet = s.item(CustomItem("zz_verify_fx_helmet", Material.IRON_HELMET, set = set.id))
            val chest = s.item(CustomItem("zz_verify_fx_chest", Material.IRON_CHESTPLATE, set = set.id))
            s.player.inventory.setHelmet(s.stack(helmet))
            s.custom.stats.invalidate(s.player)
            val one = kr.inmc.core.integration.CustomItemHook.setEffects(s.player)
            s.player.inventory.setChestplate(s.stack(chest))
            s.custom.stats.invalidate(s.player)
            val two = kr.inmc.core.integration.CustomItemHook.setEffects(s.player)
            val stored = s.custom.sets.get(set.id)?.bonuses?.get(2)?.effects
            ok(one.none { it.set == set.id }, "한 벌인데 넘어갔다: $one")
                ?: ok(two.any { it.set == set.id && it.pieces == 2 && it.effects === stored }, "두 벌인데: $two")
        },
        Check("미확인 아이템을 감정서로 밝힌다") { s ->
            val relic = s.item(CustomItem("zz_verify_relic", Material.GOLDEN_SWORD, displayName = "유물", stats = mapOf(Stat.ATTACK_DAMAGE to 6.0), unidentified = true))
            val scroll = s.item(CustomItem("zz_verify_scroll", Material.PAPER, consume = ConsumeSpec(identify = true)))
            val stack = s.stack(relic)
            ok(name(stack).contains("미확인"), "이름이 '${name(stack)}'")
                ?: ok(s.custom.items.usable(stack) == null, "미확인인데 쓸 수 있다")
                ?: ok(stack.itemMeta.attributeModifiers?.isEmpty ?: true, "미확인인데 속성이 붙었다")
                ?: run {
                    s.player.inventory.setItem(9, stack)
                    s.dropOnto(9, s.stack(scroll))
                    val after = s.player.inventory.getItem(9)
                    ok(name(after).contains("유물"), "감정 뒤 이름이 '${name(after)}'") ?: ok(s.custom.items.usable(after) != null, "감정했는데 못 쓴다")
                }
        },
        Check("분해가 분해물과 박힌 보석을 준다") { s ->
            val gem = s.gem()
            val sword = s.item(CustomItem("zz_verify_scrap", Material.IRON_SWORD, sockets = listOf(GemSpec.ANY), salvage = listOf(Part(vanilla(Material.IRON_INGOT), 3))))
            val tool = s.item(CustomItem("zz_verify_salvager", Material.STICK, consume = ConsumeSpec(uses = 0, deconstruct = true)))
            val stack = s.stack(sword)
            ItemBuilder.render(stack, sword, ItemInstance.read(stack).withGem(0, gem.id), s.custom.items.lookup)
            s.player.inventory.setItem(9, stack)
            s.dropOnto(9, s.stack(tool))
            val ingots = s.player.inventory.all(Material.IRON_INGOT).values.sumOf { it.amount }
            ok(s.player.inventory.getItem(9)?.type?.isAir ?: true, "분해한 칼이 남았다")
                ?: ok(ingots == 3, "철 주괴 $ingots 개 (3)")
                ?: ok(s.player.inventory.contents.any { s.custom.items.identify(it)?.id == gem.id }, "박혀 있던 보석을 안 돌려줬다")
        },
        Check("1.21 부품이 붙는다") { s ->
            val hat = s.item(CustomItem("zz_verify_hat", Material.PAPER, components = Components(maxStack = 16, equipSlot = "head", fireResistant = true)))
            val coat = s.item(CustomItem("zz_verify_coat", Material.LEATHER_CHESTPLATE, components = Components(color = "#FF8800", glider = true)))
            val meta = s.stack(hat).itemMeta
            val coatMeta = s.stack(coat).itemMeta
            ok(meta.maxStackSize == 16, "최대 겹침 ${meta.maxStackSize}")
                ?: ok(meta.hasEquippable() && meta.equippable.slot == EquipmentSlot.HEAD, "머리에 쓸 수 없다")
                ?: ok(meta.isFireResistant, "불 저항이 없다")
                ?: ok((coatMeta as? LeatherArmorMeta)?.color?.asRGB() == 0xFF8800, "색이 ${(coatMeta as? LeatherArmorMeta)?.color}")
                ?: ok(coatMeta.isGlider, "활공이 없다")
        },
        Check("지팡이가 눈앞의 몹을 맞힌다(좌클릭 배선)") { s ->
            val staff = s.item(CustomItem("zz_verify_staff", Material.BLAZE_ROD, style = AttackStyle.STAFF))
            s.player.inventory.setItemInMainHand(s.stack(staff))
            val pig = s.pigAhead(4.0) ?: return@Check "눈앞이 막혀 있다 — 트인 곳을 보고 다시"
            val before = pig.health
            s.interact(Action.LEFT_CLICK_AIR)
            ok(pig.health < before, "눈앞 4칸의 돼지가 안 맞았다 ($before → ${pig.health})")
        },
        Check("망치가 둘레의 몹에 튄다") { s ->
            val hammer = s.item(CustomItem("zz_verify_hammer", Material.IRON_AXE, style = AttackStyle.HAMMER))
            s.player.inventory.setItemInMainHand(s.stack(hammer))
            val at = s.groundAhead(3.0)
            val first = s.pig(at)
            val second = s.pig(at.clone().add(1.0, 0.0, 0.0))
            val before = second.health
            first.damage(6.0, s.player)
            ok(first.health < first.getAttribute(Attribute.MAX_HEALTH)!!.value, "맞은 돼지가 안 다쳤다")
                ?: ok(second.health < before, "옆 돼지에 안 튀었다 ($before → ${second.health})")
        },
        Check("기능: 우클릭 회복·범위 피해(우클릭 배선)") { s ->
            val charm = s.item(CustomItem("zz_verify_charm", Material.GOLD_NUGGET, abilities = listOf(
                Ability(Trigger.RIGHT_CLICK, EffectType.HEAL, values = mapOf("amount" to "5")),
                Ability(Trigger.RIGHT_CLICK, EffectType.AOE_DAMAGE, values = mapOf("radius" to "5", "damage" to "3")),
            )))
            s.player.inventory.setItemInMainHand(s.stack(charm))
            val pig = s.pig(s.groundAhead(2.0))
            val before = pig.health
            s.player.health = 10.0
            s.interact(Action.RIGHT_CLICK_AIR)
            ok(s.player.health == 15.0, "체력이 ${s.player.health} (10+5)") ?: ok(pig.health < before, "둘레의 돼지가 안 다쳤다 ($before → ${pig.health})")
        },
        Check("발동 조건: 입은 것의 웅크리기 기능") { s ->
            val helmet = s.item(CustomItem("zz_verify_sneak", Material.IRON_HELMET, abilities = listOf(Ability(Trigger.SNEAK, EffectType.HEAL, values = mapOf("amount" to "5")))))
            s.player.inventory.setHelmet(s.stack(helmet))
            s.custom.stats.invalidate(s.player)
            s.player.health = 10.0
            Bukkit.getPluginManager().callEvent(PlayerToggleSneakEvent(s.player, true))
            ok(s.player.health == 15.0, "체력이 ${s.player.health} (10+5)")
        },
        Check("바닐라 조합법이 서버에 올라간다") { s ->
            val result = s.item(CustomItem("zz_verify_crafted", Material.IRON_SWORD))
            val grid = MutableList<StoredItem?>(9) { null }.also { it[4] = vanilla(Material.DIRT) }
            s.recipe(RecipeDef("zz_verify_recipe", RecipeKind.SHAPED, Part(StoredItem(ItemRef.Namespaced(ItemBuilder.NAMESPACE, result.id), Material.IRON_SWORD), 1), grid))
            @Suppress("DEPRECATION")
            ok(Bukkit.getRecipe(NamespacedKey(ItemBuilder.NAMESPACE, "recipe_zz_verify_recipe")) != null, "조합법이 서버에 없다")
        },
    )
}
