package com.inmc.customitems.verify

import com.inmc.customitems.ability.Trigger
import com.inmc.customitems.gui.AbilityListMenu
import com.inmc.customitems.gui.ComponentMenu
import com.inmc.customitems.gui.ConsumeMenu
import com.inmc.customitems.gui.CustomEnchantMenu
import com.inmc.customitems.gui.DataMenu
import com.inmc.customitems.gui.EnchantMenu
import com.inmc.customitems.gui.EquipmentMenu
import com.inmc.customitems.gui.FlagMenu
import com.inmc.customitems.gui.GemMenu
import com.inmc.customitems.gui.ItemEditMenu
import com.inmc.customitems.gui.CategoryMenu
import com.inmc.customitems.gui.ChoiceMenu
import com.inmc.customitems.gui.ItemListMenu
import com.inmc.customitems.item.Category
import com.inmc.customitems.gui.ItemTypeMenu
import com.inmc.customitems.item.ItemType
import com.inmc.customitems.item.Stat
import com.inmc.customitems.gui.ItemSetListMenu
import com.inmc.customitems.gui.LoreMenu
import com.inmc.customitems.gui.ModifierListMenu
import com.inmc.customitems.gui.PackMenu
import com.inmc.customitems.gui.PartGridMenu
import com.inmc.customitems.gui.RecipeListMenu
import com.inmc.customitems.gui.RequirementMenu
import com.inmc.customitems.gui.SetChooseMenu
import com.inmc.customitems.gui.SocketMenu
import com.inmc.customitems.gui.StationListMenu
import com.inmc.customitems.gui.StatsMenu
import com.inmc.customitems.gui.TriggerPickMenu
import com.inmc.customitems.item.AttackStyle
import com.inmc.customitems.item.CustomItem
import kr.inmc.core.gui.Paging
import kr.inmc.core.util.Text
import org.bukkit.Material
import kotlin.reflect.KClass

/**
 * 화면 검사. 화면을 실제로 열고 **진짜 클릭 사건**을 서버에 쏜다 — core 의 `MenuListener` 가 그걸 받아
 * 화면에 넘기므로 관리자가 누른 것과 같은 길을 지난다. 누른 뒤 어떤 화면이 열렸는지, 정의가 어떻게 바뀌었는지 본다.
 *
 * 되돌릴 수 없는 버튼(지우기·리로드·팩 빌드)은 누르지 않는다. 고치는 버튼은 검사용 아이템에만 누른다.
 */
object MenuChecks {

    private const val ID = "zz_verify_menu"

    private fun ok(condition: Boolean, why: String): String? = if (condition) null else why

    private fun Sandbox.scratch(): CustomItem = item(CustomItem(ID, Material.IRON_SWORD, type = ItemType.WEAPON))

    private fun Sandbox.current(): CustomItem = custom.items.get(ID) ?: error("검사용 아이템이 사라졌다")

    private fun Sandbox.hub() = ItemEditMenu(custom, player, ID).open(player)

    /** [slot] 을 누르면 [kind] 가 열린다. */
    private fun Sandbox.goes(slot: Int, kind: KClass<*>): String? {
        click(slot)
        return ok(kind.isInstance(top()), "$slot 번 → ${kind.simpleName} 대신 ${topName()}")
    }

    val ALL: List<Check> = listOf(
        Check("편집 허브 → 세부 화면 → 뒤로") { s ->
            s.scratch()
            val targets = listOf(
                ItemEditMenu.SLOT_LORE to LoreMenu::class, ItemEditMenu.SLOT_STATS to StatsMenu::class,
                ItemEditMenu.SLOT_ABILITIES to AbilityListMenu::class, ItemEditMenu.SLOT_ENCHANTS to EnchantMenu::class,
                ItemEditMenu.SLOT_DATA to DataMenu::class, ItemEditMenu.SLOT_CUSTOM_ENCHANTS to CustomEnchantMenu::class,
                ItemEditMenu.SLOT_MODIFIERS to ModifierListMenu::class, ItemEditMenu.SLOT_SET to SetChooseMenu::class,
                ItemEditMenu.SLOT_FLAGS to FlagMenu::class, ItemEditMenu.SLOT_SOCKETS to SocketMenu::class,
                ItemEditMenu.SLOT_GEM to GemMenu::class, ItemEditMenu.SLOT_CONSUME to ConsumeMenu::class,
                ItemEditMenu.SLOT_REQUIREMENT to RequirementMenu::class, ItemEditMenu.SLOT_SALVAGE to PartGridMenu::class,
                ItemEditMenu.SLOT_COMPONENTS to ComponentMenu::class,
            )
            targets.firstNotNullOfOrNull { (slot, kind) ->
                s.hub()
                s.goes(slot, kind) ?: s.goes(Paging.SLOT_BACK, ItemEditMenu::class)
            }
        },
        Check("허브의 전환 버튼이 정의를 바꾼다") { s ->
            s.scratch()
            s.hub()
            s.click(ItemEditMenu.SLOT_GLOW)
            s.click(ItemEditMenu.SLOT_STYLE)
            s.click(ItemEditMenu.SLOT_UNIDENTIFIED)
            val item = s.current()
            ok(item.glow, "빛나게가 안 켜졌다") ?: ok(item.style != AttackStyle.NONE, "공격 방식이 안 바뀌었다") ?: ok(item.unidentified, "미확인이 안 켜졌다")
        },
        Check("장식 칸 클릭은 막히고 닫기는 닫는다") { s ->
            s.scratch()
            s.hub()
            ok(s.click(0), "장식 칸 클릭이 막히지 않았다") ?: run {
                s.click(Paging.SLOT_CLOSE)
                ok(s.top() !is com.inmc.customitems.gui.Menu, "닫기 뒤에도 ${s.topName()}")
            }
        },
        Check("첫 화면 버튼: 리소스팩·세트·제작대·조합법") { s ->
            val home = { ItemTypeMenu(s.custom, s.player).open(s.player) }
            home()
            s.goes(ItemTypeMenu.SLOT_PACK, PackMenu::class)
                ?: run { home(); s.goes(ItemTypeMenu.SLOT_SETS, ItemSetListMenu::class) }
                ?: run { home(); s.goes(ItemTypeMenu.SLOT_CRAFT, StationListMenu::class) }
                ?: s.goes(50, RecipeListMenu::class)
        },
        Check("종류 서랍 → 소분류 → 분류 없음만 → 설정 → 뒤로 → 같은 서랍") { s ->
            s.scratch()
            // 소분류를 하나 두어 소분류 서랍을 거치게 한다 — 기본값이 없는 서버에서도 같은 길을 검사한다.
            s.category(Category("zz_verify_cat", ItemType.WEAPON.id, "검사용"))
            val weapons = ItemTypeMenu.TYPE_SLOTS[ItemType.entries.indexOf(ItemType.WEAPON)]
            ItemTypeMenu(s.custom, s.player).open(s.player)
            s.click(weapons)
            ok(s.top() is CategoryMenu, "소분류가 있는 종류를 눌렀는데 ${s.topName()}") ?: run {
                s.click(CategoryMenu.SLOT_UNSORTED)
                val shown = s.player.openInventory.topInventory.contents.take(Paging.PER_PAGE).filterNotNull()
                    .mapNotNull { s.custom.items.identify(it) }
                ok(s.top() is ItemListMenu, "분류 없음을 눌렀는데 ${s.topName()}")
                    ?: ok(shown.isNotEmpty() && shown.all { it.type == ItemType.WEAPON && s.custom.categories.of(it) == null },
                        "분류 없음 서랍에 소분류가 있거나 다른 종류가 섞였다: " + shown.map { it.id + "=" + it.type + "/" + it.category })
                    ?: run {
                        val slot = s.player.openInventory.topInventory.contents.indexOfFirst { s.custom.items.identify(it)?.id == ID }
                        ok(slot >= 0, "분류 없음 서랍에 검사용 칼이 없다") ?: run {
                            s.click(slot)
                            ok(s.top() is ItemEditMenu, "칼을 눌렀는데 ${s.topName()}")
                                ?: s.goes(Paging.SLOT_BACK, ItemListMenu::class)
                                ?: s.goes(Paging.SLOT_BACK, CategoryMenu::class)
                                ?: s.goes(Paging.SLOT_BACK, ItemTypeMenu::class)
                        }
                    }
            }
        },
        Check("종류: 만든 종류는 기준 종류처럼 동작하고 제 이름·제 서랍으로 보인다") { s ->
            val type = s.type(com.inmc.customitems.item.TypeDef("zz_verify_type", ItemType.TALISMAN, name = "검사부적", symbol = "✪"))
            val item = s.item(com.inmc.customitems.item.CustomItem("zz_verify_typed", org.bukkit.Material.PAPER, type = ItemType.TALISMAN, customType = type.id,
                stats = mapOf(com.inmc.customitems.item.Stat.CRIT_CHANCE to 4.0)))
            val header = s.stack(item).lore()?.firstOrNull()?.let { kr.inmc.core.util.Text.plain(it) }.orEmpty()
            s.player.inventory.setItem(9, s.stack(item))
            s.custom.stats.invalidate(s.player)
            val crit = s.custom.stats.of(s.player).stat(com.inmc.customitems.item.Stat.CRIT_CHANCE)
            val index = s.custom.types.custom().indexOfFirst { it.id == type.id }
            ok("검사부적" in header && "✪" in header, "로어 첫 줄: $header")
                ?: ok(crit == 4.0, "부적처럼 가방에서 효과가 안 났다: $crit")
                ?: ok(index in ItemTypeMenu.CUSTOM_SLOTS.indices, "만든 종류가 첫 화면 칸에 안 들어간다(${index})")
                ?: run {
                    ItemTypeMenu(s.custom, s.player).open(s.player)
                    s.click(ItemTypeMenu.CUSTOM_SLOTS[index])
                    val shown = s.player.openInventory.topInventory.contents.take(Paging.PER_PAGE).filterNotNull().mapNotNull { s.custom.items.identify(it)?.id }
                    ok(s.top() is ItemListMenu && shown == listOf(item.id), "만든 종류 서랍: ${s.topName()} $shown")
                }
        },
        Check("소분류: 고른 서랍에만 보이고, 종류를 바꾸면 빠진다") { s ->
            s.scratch()
            val category = s.category(Category("zz_verify_cat", ItemType.WEAPON.id, "검사용"))
            s.hub()
            s.goes(ItemEditMenu.SLOT_CATEGORY, ChoiceMenu::class) ?: run {
                // 0 번은 "분류 없음", 그 뒤로 무기의 소분류가 파일 순서대로.
                s.click(1 + s.custom.categories.of(s.custom.types.builtin(ItemType.WEAPON)).indexOfFirst { it.id == category.id })
                ok(s.current().category == category.id, "고른 소분류가 안 들어갔다: '" + s.current().category + "'")
                    ?: ok(s.top() is ItemEditMenu, "고른 뒤 설정 화면으로 안 돌아왔다: ${s.topName()}")
            } ?: run {
                ItemListMenu(s.custom, s.player, s.custom.types.builtin(ItemType.WEAPON), category = category.id).open(s.player)
                val shown = s.player.openInventory.topInventory.contents.take(Paging.PER_PAGE).filterNotNull()
                    .mapNotNull { s.custom.items.identify(it)?.id }
                ok(shown == listOf(ID), "소분류 서랍에 다른 것이 보인다: $shown")
            } ?: run {
                s.hub()
                s.click(ItemEditMenu.SLOT_TYPE)
                ok(s.current().type != ItemType.WEAPON && s.current().category.isEmpty(), "종류를 바꿨는데 소분류가 남았다: " + s.current().type + "/" + s.current().category)
            }
        },
        Check("한글 id 아이템에 능력치를 넣고 능력치 창에서 뒤로 간다") { s ->
            // 속성 수정자 열쇠에 한글이 들어가 그리기마다 던지던 결함(2026-09-24). 목록 화면째 안 열렸다.
            val id = "zz_검증_한글"
            s.item(CustomItem(id, Material.IRON_SWORD, type = ItemType.WEAPON, stats = mapOf(Stat.ATTACK_DAMAGE to 3.0, Stat.JUMP_STRENGTH to 0.1)))
            StatsMenu(s.custom, s.player, id).open(s.player)
            s.goes(Paging.SLOT_BACK, ItemEditMenu::class)
                ?: run { ItemTypeMenu(s.custom, s.player).open(s.player); s.goes(ItemTypeMenu.SLOT_ALL, ItemListMenu::class) }
        },
        Check("장착 화면: 끌어다 넣고, 다른 종류는 막고, Shift 로 가방에") { s ->
            val charm = s.item(CustomItem("zz_verify_eqmenu", Material.PAPER, type = ItemType.TALISMAN))
            val sword = s.item(CustomItem("zz_verify_eqsword", Material.IRON_SWORD, type = ItemType.WEAPON))
            val talismanRow = 2 * 9 + 1
            EquipmentMenu(s.custom, s.player).open(s.player)
            s.player.openInventory.setCursor(s.stack(sword))
            s.click(talismanRow)
            val refused = s.custom.equipment.get(s.player.uniqueId, com.inmc.customitems.player.EquipmentStore.Group.TALISMAN, 0)
            s.player.openInventory.setCursor(s.stack(charm))
            s.click(talismanRow)
            val placed = s.custom.equipment.get(s.player.uniqueId, com.inmc.customitems.player.EquipmentStore.Group.TALISMAN, 0)
            val cursorAfter = s.player.openInventory.cursor
            s.click(talismanRow, org.bukkit.event.inventory.ClickType.SHIFT_LEFT)
            val removed = s.custom.equipment.get(s.player.uniqueId, com.inmc.customitems.player.EquipmentStore.Group.TALISMAN, 0)
            s.player.openInventory.setCursor(null)
            val back = s.player.inventory.contents.any { s.custom.items.identify(it)?.id == charm.id }
            s.custom.equipment.put(s.player.uniqueId, com.inmc.customitems.player.EquipmentStore.Group.TALISMAN, 0, null)
            ok(refused == null, "부적 줄에 무기가 들어갔다")
                ?: ok(s.custom.items.identify(placed)?.id == charm.id, "부적이 안 들어갔다")
                ?: ok(cursorAfter.type.isAir, "넣은 뒤에도 커서에 남았다(복사)")
                ?: ok(removed == null && back, "Shift 로 가방에 안 돌아왔다")
        },
        Check("기능 추가 → 발동 조건을 전부 고를 수 있다") { s ->
            s.scratch()
            AbilityListMenu(s.custom, s.player, ID).open(s.player)
            s.goes(AbilityListMenu.SLOT_ADD, TriggerPickMenu::class) ?: run {
                val shown = s.player.openInventory.topInventory.contents.filterNotNull().map { Text.plain(it.effectiveName()) }
                val missing = Trigger.entries.filter { trigger -> shown.none { it.contains(trigger.display) } }
                ok(missing.isEmpty(), "고를 수 없는 발동 조건: " + missing.joinToString { it.display })
            }
        },
        Check("소모품 화면: 켜기·감정서·분해 도구") { s ->
            s.scratch()
            ConsumeMenu(s.custom, s.player, ID).open(s.player)
            s.click(4)
            s.click(38)
            s.click(39)
            val consume = s.current().consume ?: return@Check "소모품이 안 켜졌다"
            ok(consume.identify, "감정서가 안 켜졌다") ?: ok(consume.deconstruct, "분해 도구가 안 켜졌다")
        },
        Check("부품 화면: 툴팁 숨기기·입는 칸") { s ->
            s.scratch()
            ComponentMenu(s.custom, s.player, ID).open(s.player)
            s.click(12)
            s.click(19)
            val parts = s.current().components
            ok(parts.hideTooltip, "툴팁 숨기기가 안 켜졌다") ?: ok(parts.equipSlot == "head", "입는 칸이 '${parts.equipSlot}' (첫 칸은 머리)")
        },
    )
}
