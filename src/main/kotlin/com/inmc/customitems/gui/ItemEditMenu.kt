package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import com.inmc.customitems.block.ToolGrades
import com.inmc.customitems.item.AttackStyle
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.Tier
import com.inmc.customitems.pack.PackAssets
import com.inmc.customitems.player.BackpackLayout
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.integration.CustomEnchantHook
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * 아이템 한 개의 설정. **모든 문을 여기서 연다.**
 *
 * 한 화면에 다 넣지 않는다 — 능력치 16종, 기능 여러 개, 인챈트 여러 개를 한 창에 펴면
 * 54칸을 넘고 무엇이 무엇인지 알 수 없게 된다. 여기는 **허브**고, 각 덩이는 자기 화면을
 * 갖는다. 허브의 버튼도 **탭 넷**(기본·능력·용도·바닐라)으로 나누고 탭 안에서는 주제마다 한 줄에 둔다
 * (배치는 [ItemEditLayout], 사용자 요청 2026-09-30). 미리보기는 맨 윗줄 가운데 — 어느 탭, 어느 화면에서
 * 돌아와도 결과가 바로 보인다. 세부 화면에서 돌아오면 **보던 탭**으로 온다.
 *
 * **정의가 아니라 id 를 들고 있다.** [CustomItem] 은 불변이라 한 칸을 고칠 때마다 새
 * 객체가 되는데, 화면이 옛 객체를 붙들고 있으면 두 번째 편집이 첫 번째를 지운다.
 */
class ItemEditMenu(
    custom: CustomItems,
    private val viewer: Player,
    private val id: String,
    tab: EditTab? = null,
) : Menu(custom, ItemEditLayout.SIZE, Text.renderFlat("<dark_gray>아이템 — " + id + "</dark_gray>")) {

    /** 지금 보는 탭. 정하지 않고 열면 이 사람이 이 아이템에서 마지막으로 본 탭(세부 화면의 "뒤로"가 그 길로 온다). */
    var tab: EditTab = tab ?: lastTab[viewer.uniqueId]?.takeIf { it.first == id }?.second ?: EditTab.BASIC
        private set

    private fun item(): CustomItem? = custom.items.get(id)

    override fun draw() {
        clear()
        val item = item() ?: run {
            // 다른 관리자가 지웠다. 빈 화면을 보여주느니 목록으로 돌린다.
            ItemTypeMenu(custom, viewer).open(viewer)
            return
        }
        lastTab[viewer.uniqueId] = id to tab
        fillEmpty(Icon.EDGE)
        set(ItemEditLayout.SLOT_PREVIEW, preview(item))

        // 탭 줄 — 빈칸은 지금 탭의 색유리라 어느 탭인지 한눈에 보인다.
        for (slot in 9..17) set(slot, Icon.blank(tab.band))
        for ((index, each) in EditTab.entries.withIndex()) {
            set(ItemEditLayout.TAB_SLOTS[index], tabIcon(each, item)) {
                if (each != tab) {
                    tab = each
                    refresh()
                }
            }
        }

        for ((button, slot) in ItemEditLayout.place(tab) { it.shownFor(item) }) {
            val (icon, onClick) = buttonOf(button, item)
            // 칼·곡괭이·갑옷 모양 아이콘에 바닐라 공격력·방어력 줄이 붙지 않게.
            icon.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
            set(slot, icon, onClick)
        }

        set(ItemEditLayout.SLOT_GIVE, giveIcon()) { event -> give(if (event.isShiftClick) 64 else 1) }
        set(Paging.SLOT_BACK, Icon.back()) { backToList(item) }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun btn(icon: ItemStack, onClick: (InventoryClickEvent) -> Unit) = icon to onClick

    /**
     * 버튼 하나의 모양과 누름. 새 버튼은 [EditButton] 에 더하고 [EditTab.rows] 에 자리를 준 뒤 여기에 — 빠뜨리면
     * 컴파일러가 잡는다.
     */
    private fun buttonOf(button: EditButton, item: CustomItem): Pair<ItemStack, (InventoryClickEvent) -> Unit> = when (button) {
        // --- 기본: 모양 · 분류 ---
        EditButton.MATERIAL -> btn(materialIcon(item)) { event -> if (event.isRightClick) promptMaterial() else materialFromHand() }
        EditButton.NAME -> btn(nameIcon(item)) { promptName() }
        EditButton.LORE -> btn(loreIcon(item)) { LoreMenu(custom, viewer, id).open(viewer) }
        EditButton.TEXTURE -> btn(textureIcon(item)) { event -> editTexture(item, event) }
        EditButton.TYPE -> btn(typeIcon(item)) { chooseType() }
        EditButton.CATEGORY -> btn(categoryIcon(item)) { chooseCategory(custom.types.of(item)) }
        EditButton.TIER -> btn(tierIcon(item)) { event -> mutate { it.copy(tier = Editors.cycle(event, Tier.entries.toList(), it.tier)) } }

        // --- 능력: 무엇을 하나 · 무엇이 붙나 · 언제·어디서 ---
        EditButton.STATS -> btn(statsIcon(item)) { StatsMenu(custom, viewer, id).open(viewer) }
        EditButton.ABILITIES -> btn(abilitiesIcon(item)) { AbilityListMenu(custom, viewer, id).open(viewer) }
        EditButton.STYLE -> btn(styleIcon(item)) { event -> mutate { it.copy(style = Editors.cycle(event, AttackStyle.entries.toList(), it.style)) } }
        EditButton.UPGRADE -> btn(upgradeIcon(item)) { ItemUpgradeMenu(custom, viewer, id).open(viewer) }
        EditButton.MINING_TIER -> {
            val vanilla = ToolGrades.vanillaTier(item.material) ?: 0
            btn(miningTierIcon(item, vanilla)) { event -> editMiningTier(item, vanilla, event) }
        }
        EditButton.ENCHANTS -> btn(enchantsIcon(item)) { EnchantMenu(custom, viewer, id).open(viewer) }
        EditButton.CUSTOM_ENCHANTS -> btn(customEnchantsIcon(item)) { CustomEnchantMenu(custom, viewer, id).open(viewer) }
        EditButton.MODIFIERS -> btn(modifiersIcon(item)) { ModifierListMenu(custom, viewer, id).open(viewer) }
        EditButton.SET -> btn(setIcon(item)) { SetChooseMenu(custom, viewer, id).open(viewer) }
        EditButton.REQUIREMENT -> btn(requirementIcon(item)) { RequirementMenu(custom, viewer, id).open(viewer) }
        EditButton.INVENTORY_EFFECT -> btn(inventoryEffectIcon(item)) {
            // 서버 설정 → 장착 칸에서만 → 가방에서도 → 서버 설정
            mutate { it.copy(inventoryEffect = when (it.inventoryEffect) { null -> false; false -> true; true -> null }) }
        }
        EditButton.NO_DUPLICATE -> btn(toggleIcon(Material.TOTEM_OF_UNDYING, "같은 부적 중복 안 함", item.noDuplicate,
            "가방·장착 칸에 같은 부적이 여럿이면(강화 단계가 달라도) 가장 높은 것 하나만 효과가 납니다.")) {
            mutate { it.copy(noDuplicate = !it.noDuplicate) }
        }

        // --- 용도: 특별한 쓰임 · 얻고 버릴 때 · 다른 플러그인 ---
        EditButton.CONSUME -> btn(consumeIcon(item)) { ConsumeMenu(custom, viewer, id).open(viewer) }
        EditButton.GEM -> btn(gemIcon(item)) { GemMenu(custom, viewer, id).open(viewer) }
        EditButton.SOCKETS -> btn(socketsIcon(item)) { SocketMenu(custom, viewer, id).open(viewer) }
        EditButton.BLOCK -> btn(blockIcon(item)) { BlockMenu(custom, viewer, id).open(viewer) }
        EditButton.BACKPACK -> btn(backpackIcon(item)) { event -> editBackpack(item, event) }
        EditButton.UNIDENTIFIED -> btn(toggleIcon(Material.FILLED_MAP, "미확인으로 나오기", item.unidentified, "만들면 정체가 가려지고 감정서로 밝힙니다.")) {
            mutate { it.copy(unidentified = !it.unidentified) }
        }
        EditButton.SALVAGE -> btn(salvageIcon(item)) {
            PartGridMenu(custom, viewer, "분해물 — $id", load = { item()?.salvage }, save = { parts -> item()?.let { custom.items.put(it.copy(salvage = parts)) } }, back = { open(viewer) }).open(viewer)
        }
        EditButton.ROLES -> btn(rolesIcon(item)) { RoleListMenu(custom, viewer, id).open(viewer) }
        EditButton.DATA -> btn(dataIcon(item)) { DataMenu(custom, viewer, id).open(viewer) }

        // --- 바닐라: 겉 · 속 ---
        EditButton.GLOW -> btn(toggleIcon(Material.GLOWSTONE_DUST, "빛나게", item.glow, "인챈트 없이 반짝입니다.")) { mutate { it.copy(glow = !it.glow) } }
        EditButton.UNBREAKABLE -> btn(toggleIcon(Material.BEDROCK, "무한 내구도", item.unbreakable, "닳지 않습니다.")) {
            mutate { it.copy(unbreakable = !it.unbreakable) }
        }
        EditButton.DURABILITY -> btn(durabilityIcon(item)) { event -> editDurability(item, event) }
        EditButton.FLAGS -> btn(flagsIcon(item)) { FlagMenu(custom, viewer, id).open(viewer) }
        EditButton.COMPONENTS -> btn(componentsIcon(item)) { ComponentMenu(custom, viewer, id).open(viewer) }
        EditButton.VANILLA_USE -> btn(vanillaUseIcon(item)) { mutate { it.copy(preventVanillaUse = !it.preventVanillaUse) } }
    }

    // --- 탭 -------------------------------------------------------------------------

    /**
     * 탭 — 이 탭에 무엇이 있는지(화면의 줄 그대로)와 그중 바꾼 것. 어느 탭을 열어 봐야 할지 열기 전에 안다.
     * 기본 탭은 미리보기가 곧 요약이라 바꾼 것을 따로 적지 않는다.
     */
    private fun tabIcon(each: EditTab, item: CustomItem): ItemStack {
        val active = each == tab
        val lore = buildList {
            for (row in each.rows) {
                val shown = row.filter { it.shownFor(item) }
                if (shown.isNotEmpty()) add("<gray>" + shown.joinToString(" · ") { it.label } + "</gray>")
            }
            if (each != EditTab.BASIC) {
                val done = each.rows.flatten().filter { it.shownFor(item) }.mapNotNull { changed(it, item) }
                add("")
                if (done.isEmpty()) {
                    add("<dark_gray>바꾼 것 없음</dark_gray>")
                } else {
                    done.chunked(3).forEachIndexed { index, chunk ->
                        add((if (index == 0) "<white>설정됨: " else "<white>") + chunk.joinToString(" · ") + "</white>")
                    }
                }
            }
            add("")
            add(if (active) "<green>▶ 지금 보는 탭</green>" else "<yellow>▶ 클릭: 이 탭 보기</yellow>")
        }
        val name = each.color + (if (active) "<bold>▶ " + each.label + "</bold>" else each.label)
        return Icon.of(each.icon, name, lore).apply {
            if (active) editMeta { it.setEnchantmentGlintOverride(true) }
        }
    }

    /** 기본값에서 바꾼 것을 짧게 — 탭 아이콘의 "설정됨". 기본 탭의 버튼은 늘 값이 있어 null. */
    private fun changed(button: EditButton, item: CustomItem): String? = when (button) {
        EditButton.MATERIAL, EditButton.NAME, EditButton.LORE, EditButton.TEXTURE,
        EditButton.TYPE, EditButton.CATEGORY, EditButton.TIER -> null
        EditButton.STATS -> item.stats.size.takeIf { it > 0 }?.let { "능력치 $it" }
        EditButton.ABILITIES -> item.abilities.size.takeIf { it > 0 }?.let { "기능 $it" }
        EditButton.STYLE -> item.style.takeIf { it != AttackStyle.NONE }?.display
        EditButton.UPGRADE -> "강화·진화".takeIf { item.upgrade.table(custom.items.lookup) != null || item.upgrade.evolution != null }
        EditButton.MINING_TIER -> item.miningTier?.let { "채굴 " + ToolGrades.name(it) }
        EditButton.ENCHANTS -> item.enchants.size.takeIf { it > 0 }?.let { "인챈트 $it" }
        EditButton.CUSTOM_ENCHANTS -> item.customEnchants.size.takeIf { it > 0 }?.let { "커스텀 인첸트 $it" }
        EditButton.MODIFIERS -> item.modifiers.size.takeIf { it > 0 }?.let { "수식어 $it" }
        EditButton.SET -> custom.sets.get(item.set)?.let { "세트 " + it.name }
        EditButton.REQUIREMENT -> "요구 조건".takeIf { !item.requirement.isEmpty }
        EditButton.INVENTORY_EFFECT -> item.inventoryEffect?.let { if (it) "가방에서도 효과" else "장착 칸에서만" }
        EditButton.NO_DUPLICATE -> "중복 안 함".takeIf { item.noDuplicate }
        EditButton.CONSUME -> "소모품".takeIf { item.consume != null }
        EditButton.GEM -> "보석".takeIf { item.gem != null }
        EditButton.SOCKETS -> item.sockets.size.takeIf { it > 0 }?.let { "소켓 $it" }
        EditButton.BLOCK -> item.block?.let { "블록" }
        EditButton.BACKPACK -> item.backpack.takeIf { it > 0 }?.let { "배낭 " + it + "칸" }
        EditButton.UNIDENTIFIED -> "미확인".takeIf { item.unidentified }
        EditButton.SALVAGE -> item.salvage.size.takeIf { it > 0 }?.let { "분해물 " + it + "종" }
        EditButton.ROLES -> item.roles.size.takeIf { it > 0 }?.let { "연동 역할 $it" }
        EditButton.DATA -> item.data.size.takeIf { it > 0 }?.let { "연동 값 $it" }
        EditButton.GLOW -> "빛나게".takeIf { item.glow }
        EditButton.UNBREAKABLE -> "무한 내구도".takeIf { item.unbreakable }
        EditButton.DURABILITY -> item.maxDurability.takeIf { it > 0 }?.let { "내구도 $it" }
        EditButton.FLAGS -> item.flags.size.takeIf { it > 0 }?.let { "숨김 $it" }
        EditButton.COMPONENTS -> "바닐라 부품".takeIf { !item.components.isEmpty }
        EditButton.VANILLA_USE -> "설치·소모 막기".takeIf { item.preventVanillaUse }
    }

    // --- 아이콘 ---------------------------------------------------------------------

    /** 미리보기 — 진짜로 만들어본 아이템. 인첸트 칸 수("n/m")는 아이템 로어에 안 적고 여기(관리 화면)에서만 보인다. */
    private fun preview(item: CustomItem): ItemStack {
        val stack = custom.items.preview(item)
        val slots = CustomEnchantHook.slots(stack) ?: return stack
        return Icon.annotate(stack, lore = listOf("<gray>인첸트 칸 <white>" + slots.first + "</white>/" + slots.second + "</gray> <dark_gray>(관리 화면에서만)</dark_gray>"))
    }

    private fun materialIcon(item: CustomItem) = Icon.of(
        item.material,
        "<yellow>재질: <white>" + item.material.name + "</white></yellow>",
        listOf(
            "<gray>아이템의 바탕이 되는 바닐라 아이템입니다.</gray>",
            "",
            "<yellow>▶ 좌클릭: 손에 든 것의 재질로</yellow>",
            "<yellow>▶ 우클릭: 이름으로 입력</yellow>",
        ),
    )

    private fun nameIcon(item: CustomItem) = Icon.of(
        Material.NAME_TAG,
        "<yellow>표시 이름</yellow>",
        listOf(
            if (item.displayName.isBlank()) {
                "<dark_gray>없음 (바닐라 이름 그대로)</dark_gray>"
            } else {
                "<white>" + item.displayName + "</white>"
            },
            "",
            "<gray>등급 색이 앞에 저절로 붙습니다.</gray>",
            "<gray>색코드(&a)와 MiniMessage(&lt;red&gt;) 둘 다 됩니다.</gray>",
            "",
            "<yellow>▶ 클릭: 입력</yellow>",
        ),
    )

    private fun loreIcon(item: CustomItem) = Icon.of(
        Material.WRITTEN_BOOK,
        "<yellow>설명 <white>" + item.lore.size + "</white>줄</yellow>",
        item.lore.take(5).map { "<dark_gray>" + it + "</dark_gray>" } +
            listOf("", "<yellow>▶ 클릭: 줄 단위로 편집</yellow>"),
    )

    /**
     * 겉모습.
     *
     * **여기가 리소스팩과 이어지는 자리다.** 파일 이름을 적으면 빌드할 때 모델까지 만들어
     * 주고 아이템에 `item_model` 이 붙는다. 번호(custom-model-data)를 안 쓰는 이유는
     * 팩 두 개를 합칠 때 번호가 겹치는 것이 가장 흔한 사고였기 때문이다.
     */
    private fun textureIcon(item: CustomItem) = Icon.of(
        Material.PAINTING,
        "<yellow>겉모습 (리소스팩)</yellow>",
        buildList {
            when {
                item.model.isNotBlank() -> {
                    add("<gray>모델: <white>" + item.model + "</white></gray>")
                    add("<dark_gray>pack/models/ 나 sources/ 의 팩에 그 모델이 있어야 합니다</dark_gray>")
                }

                item.texture.isNotBlank() -> {
                    val png = java.io.File(custom.pack.texturesDir, item.texture)
                    add("<gray>텍스처: <white>" + item.texture + "</white></gray>")
                    if (png.isFile) {
                        add("<green>파일을 찾았습니다.</green>")
                    } else {
                        // 오타는 오류를 내지 않고 그 아이템만 바닐라 모양으로 나오게 한다.
                        add("<red>pack/textures/ 에 그 파일이 없습니다!</red>")
                    }
                }

                else -> {
                    add("<dark_gray>없음 (바닐라 모양 그대로)</dark_gray>")
                }
            }
            add("")
            add("<dark_gray>모델 이름: " + PackAssets.modelNameFor(item) + "</dark_gray>")
            add("<yellow>▶ 좌클릭: 팩의 모델에서 고르기(검색)</yellow>")
            add("<yellow>▶ Shift+좌클릭: 텍스처·모델 이름 직접 입력</yellow>")
            add("<red>▶ 우클릭: 없애기</red>")
        },
    )

    private fun typeIcon(item: CustomItem): ItemStack {
        val current = custom.types.of(item)
        return Icon.of(
            current.icon,
            "<yellow>종류: <white>" + current.name + "</white></yellow>",
            listOf(
                if (current.builtin) "<gray>기본 종류</gray>" else "<gray>" + current.base.display + "처럼 동작합니다.</gray>",
                "",
                "<gray>만든 종류는 고른 기본 종류처럼 동작합니다.</gray>",
                "<dark_gray>종류 만들기·이름 바꾸기: 관리 → 종류 관리</dark_gray>",
                "",
                "<yellow>▶ 클릭: 종류 고르기</yellow>",
            ),
        )
    }

    private fun categoryIcon(item: CustomItem): ItemStack {
        val current = custom.categories.of(item)
        val choices = custom.categories.of(custom.types.of(item))
        return Icon.of(
            current?.icon ?: Material.BOOKSHELF,
            "<yellow>소분류: <white>" + (current?.name ?: "없음") + "</white></yellow>",
            buildList {
                if (choices.isEmpty()) {
                    add("<gray>" + custom.types.of(item).name + " 에는 아직 소분류가 없습니다.</gray>")
                    add("<dark_gray>목록 화면의 소분류 버튼에서 만듭니다.</dark_gray>")
                } else {
                    for (choice in choices.take(8)) add(if (choice == current) "<green>▶ " + choice.name + "</green>" else "<dark_gray>  " + choice.name + "</dark_gray>")
                    if (choices.size > 8) add("<dark_gray>  …</dark_gray>")
                }
                add("")
                add("<gray>목록을 나눠 보는 서랍일 뿐, 동작은 바꾸지 않습니다.</gray>")
                add("<yellow>▶ 클릭: 고르기</yellow>")
            },
        )
    }

    /**
     * 종류 고르기 화면(사용자 요청 2026-09-30 — 좌/우클릭으로 돌리는 대신 들어가서 고른다). 종류가 늘수록 돌려서 찾기 어렵다.
     * 소분류는 종류에 딸려 있어 다른 종류로 옮기면 뗀다. 동작은 그 종류의 기준(base)을 따른다.
     */
    private fun chooseType() {
        ChoiceMenu(
            custom, viewer, "종류 — $id",
            options = {
                val items = custom.items.all()
                custom.types.all().map { type ->
                    val count = items.count { custom.types.of(it).id == type.id }
                    type.id to iconOf(type.icon, type.iconItem, "<dark_gray>" + type.symbol + "</dark_gray> <yellow>" + type.name + "</yellow>", listOf(
                        if (type.builtin) "<gray>기본 종류</gray>" else "<gray>" + type.base.display + "처럼 동작하는 종류</gray>",
                        "<dark_gray>이 종류의 아이템 " + count + "개</dark_gray>",
                    ))
                }
            },
            selected = { setOf(item()?.let { custom.types.of(it).id }.orEmpty()) },
            back = { open(viewer) },
        ) { picked ->
            val next = custom.types.get(picked)
            if (next != null) item()?.let {
                custom.items.put(it.copy(
                    type = next.base,
                    customType = if (next.builtin) "" else next.id,
                    category = if (custom.categories.get(it.category)?.type == next.id) it.category else "",
                ))
            }
            open(viewer)
        }.open(viewer)
    }

    private fun chooseCategory(type: com.inmc.customitems.item.TypeDef) {
        ChoiceMenu(
            custom, viewer, "소분류 — $id",
            options = {
                listOf("" to Icon.of(Material.BARRIER, "<gray>분류 없음</gray>", emptyList())) +
                    custom.categories.of(type).map { it.id to Icon.of(it.icon, "<yellow>" + it.name + "</yellow>", listOf("<dark_gray>id " + it.id + "</dark_gray>")) }
            },
            selected = { setOf(item()?.let { custom.categories.of(it)?.id }.orEmpty()) },
            back = { open(viewer) },
        ) { picked ->
            item()?.let { custom.items.put(it.copy(category = picked)) }
            open(viewer)
        }.open(viewer)
    }

    /** 온 곳으로 — 소분류가 있으면 그 서랍, 없고 종류에 소분류가 있으면 "분류 없음", 아니면 종류 목록. */
    private fun backToList(item: CustomItem) {
        val category = custom.categories.of(item)
        val type = custom.types.of(item)
        val sorted = custom.categories.of(type).isNotEmpty()
        ItemListMenu(custom, viewer, type, category = category?.id, unsorted = category == null && sorted).open(viewer)
    }

    private fun tierIcon(item: CustomItem) = Icon.of(
        Material.NETHER_STAR,
        "<yellow>등급: " + item.tier.color + item.tier.display + "</yellow>",
        Editors.optionList(Tier.entries.toList(), item.tier) { it.color + it.display } +
            listOf("", "<gray>이름 색과 로어 첫 줄이 바뀝니다.</gray>") + Editors.cycleHint,
    )

    private fun styleIcon(item: CustomItem) = Icon.of(
        item.style.icon,
        "<yellow>공격 방식: <white>" + item.style.display + "</white></yellow>",
        listOf("<gray>" + item.style.description + "</gray>", "") +
            Editors.optionList(AttackStyle.entries.toList(), item.style) { it.display } +
            listOf("", "<dark_gray>원거리 방식의 피해는 공격력 속성에서, 간격은 공격 속도에서.</dark_gray>") + Editors.cycleHint,
    )

    private fun statsIcon(item: CustomItem) = Icon.of(
        Material.IRON_SWORD,
        "<yellow>능력치 <white>" + item.stats.size + "</white>개</yellow>",
        com.inmc.customitems.item.Stat.entries.filter { item.has(it) }
            .take(6).map { it.line(item.stat(it)) } +
            listOf("", "<yellow>▶ 클릭: 능력치 설정</yellow>"),
    )

    private fun abilitiesIcon(item: CustomItem) = Icon.of(
        Material.BLAZE_POWDER,
        "<yellow>기능 <white>" + item.abilities.size + "</white>개</yellow>",
        item.abilities.take(5).map { it.line() } +
            listOf("", "<yellow>▶ 클릭: 기능 설정</yellow>"),
    )

    private fun upgradeIcon(item: CustomItem): ItemStack {
        val table = item.upgrade.table(custom.items.lookup)
        return Icon.of(Material.ANVIL, "<gold>강화·진화</gold>", listOf(
            "<gray>강화 방식: <white>" + (if (item.upgrade.own != null) "이 아이템 전용" else table?.name ?: "없음") + "</white> · 최대 <white>+" + (table?.maxLevel ?: 0) + "</white></gray>",
            "<gray>진화: <white>" + (item.upgrade.evolution?.let { custom.items.get(it.into)?.label() ?: it.into } ?: "없음") + "</white></gray>",
            "", "<yellow>▶ 클릭: 설정</yellow>",
        ))
    }

    private fun enchantsIcon(item: CustomItem) = Icon.of(
        Material.ENCHANTED_BOOK,
        "<yellow>인챈트 <white>" + item.enchants.size + "</white>개</yellow>",
        item.enchants.entries.take(5).map { "<dark_gray>" + it.key + " " + it.value + "</dark_gray>" } +
            listOf("", "<yellow>▶ 클릭: 인챈트 설정</yellow>"),
    )

    private fun customEnchantsIcon(item: CustomItem) = Icon.of(
        Material.EXPERIENCE_BOTTLE,
        "<light_purple>커스텀 인첸트 <white>" + item.customEnchants.size + "</white>개</light_purple>",
        item.customEnchants.entries.take(5).map { "<dark_gray>" + it.key + " " + it.value + "</dark_gray>" } +
            if (CustomEnchantHook.isEnabled) {
                listOf("", "<yellow>▶ 클릭: 커스텀 인첸트 고르기</yellow>")
            } else {
                listOf("", "<red>인첸트 플러그인이 없어 붙지 않습니다</red>")
            },
    )

    private fun setIcon(item: CustomItem) = Icon.of(
        Material.CHAINMAIL_CHESTPLATE,
        "<green>세트: <white>" + (custom.sets.get(item.set)?.name ?: "없음") + "</white></green>",
        listOf("<gray>같은 세트를 여러 벌 입으면 효과가 붙습니다.</gray>", "", "<yellow>▶ 클릭: 고르기</yellow>"),
    )

    private fun modifiersIcon(item: CustomItem) = Icon.of(
        Material.NAME_TAG,
        "<gold>수식어 <white>" + item.modifiers.size + "</white>개</gold>",
        item.modifiers.take(5).map { "<dark_gray>" + it.name + " " + kr.inmc.core.util.Numbers.chance(it.chance) + "%</dark_gray>" } +
            listOf("<gray>만들 때 확률로 붙어 이름과 능력치를 바꿉니다.</gray>", "", "<yellow>▶ 클릭: 수식어 설정</yellow>"),
    )

    private fun requirementIcon(item: CustomItem) = Icon.of(Material.IRON_BARS, "<red>요구 조건</red>", listOf(
        "<gray>레벨 <white>" + item.requirement.level + "</white> · 권한 <white>" + item.requirement.permission.ifBlank { "없음" } + "</white></gray>",
        "", "<yellow>▶ 클릭: 요구 조건 설정</yellow>",
    ))

    private fun consumeIcon(item: CustomItem) = Icon.of(Material.HONEY_BOTTLE, "<green>소모품: <white>" + (if (item.consume != null) "켜짐" else "아님") + "</white></green>", listOf(
        "<gray>우클릭으로 쓰는 회복·효과, 보석 빼기·수리.</gray>", "", "<yellow>▶ 클릭: 소모품 설정</yellow>",
    ))

    private fun gemIcon(item: CustomItem) = Icon.of(
        Material.EMERALD,
        "<aqua>보석: <white>" + (item.gem?.let { com.inmc.customitems.item.ItemBuilder.socketLabel(it.color) + " · " + kr.inmc.core.util.Numbers.chance(it.chance) + "%" } ?: "아님") + "</white></aqua>",
        listOf("<gray>이 아이템을 다른 아이템의 소켓에 박는 보석으로.</gray>", "", "<yellow>▶ 클릭: 보석 설정</yellow>"),
    )

    private fun socketsIcon(item: CustomItem) = Icon.of(
        Material.AMETHYST_CLUSTER,
        "<aqua>소켓 <white>" + item.sockets.size + "</white>개</aqua>",
        item.sockets.map { "<dark_gray>◇ " + it + "</dark_gray>" } + listOf("", "<yellow>▶ 클릭: 소켓 설정</yellow>"),
    )

    private fun blockIcon(item: CustomItem) = Icon.of(item.block?.kind?.base ?: Material.GRASS_BLOCK, "<green>블록: <white>" + (item.block?.kind?.label ?: "아님") + "</white></green>", listOf(
        "<gray>들고 우클릭하면 이 모양의 블록으로 놓입니다.</gray>",
        "<gray>랜덤박스 상자 모양으로도 고를 수 있습니다.</gray>",
        "", "<yellow>▶ 클릭: 블록 설정</yellow>",
    ))

    private fun salvageIcon(item: CustomItem) = Icon.of(Material.GRINDSTONE, "<gold>분해물 <white>" + item.salvage.size + "</white>종</gold>", listOf(
        "<gray>분해 도구를 이 아이템 위에 놓으면 나오는 것.</gray>",
        "<gray>비우면 분해할 수 없습니다.</gray>", "", "<yellow>▶ 클릭: 칸에 넣어 정하기</yellow>",
    ))

    private fun rolesIcon(item: CustomItem) = Icon.of(Material.COMPARATOR, "<aqua>연동 역할 <white>" + item.roles.size + "</white>개</aqua>", buildList {
        add("<gray>다른 플러그인에서 이 아이템이 맡는 일 —</gray>")
        add("<gray>보호권·상자 캡슐·낚싯대·화폐 …</gray>")
        for (key in item.roles.keys) add("<green>▪ " + (kr.inmc.core.integration.ItemRoles.role(key)?.let { it.owner + " · " + it.label } ?: key) + "</green>")
        add("")
        add("<yellow>▶ 클릭: 역할 보기·붙이기</yellow>")
    })

    private fun dataIcon(item: CustomItem) = Icon.of(
        Material.REPEATER,
        "<aqua>연동 값 <white>" + item.data.size + "</white>개</aqua>",
        listOf(
            "<gray>다른 플러그인이 읽는 값입니다.</gray>",
            "<gray>예: <white>fishing.reel-power</white> 를 적으면</gray>",
            "<gray>낚시가 이 아이템을 낚싯대로 씁니다.</gray>",
            "",
        ) + item.data.entries.take(4).map { "<dark_gray>" + it.key + " = " + it.value + "</dark_gray>" } +
            listOf("", "<yellow>▶ 클릭: 연동 값 설정</yellow>"),
    )

    private fun flagsIcon(item: CustomItem) = Icon.of(
        Material.PAPER,
        "<yellow>숨김 옵션 <white>" + item.flags.size + "</white>개</yellow>",
        listOf("<gray>인챈트·속성·내구도 표시를 숨깁니다.</gray>", "", "<yellow>▶ 클릭: 설정</yellow>"),
    )

    private fun componentsIcon(item: CustomItem) = Icon.of(Material.LEATHER_HELMET, "<yellow>바닐라 부품" + (if (item.components.isEmpty) "" else " <white>(설정됨)</white>") + "</yellow>", listOf(
        "<gray>최대 겹침 · 툴팁 모양 · 입는 칸 · 색 · 갑옷 장식</gray>",
        "<gray>머리 텍스처 · 활공 · 불 저항</gray>", "", "<yellow>▶ 클릭: 설정</yellow>",
    ))

    private fun vanillaUseIcon(item: CustomItem) = Icon.of(
        if (item.preventVanillaUse) Material.BARRIER else Material.GRAY_DYE,
        "<yellow>바닐라 설치·소모 막기: " + Icon.toggle(item.preventVanillaUse) + "</yellow>",
        listOf(
            "<gray>재질이 블록이어도 놓이지 않고,</gray>",
            "<gray>먹거나 마실 수 있는 재질이어도 먹히지 않습니다.</gray>",
            "<dark_gray>우클릭 기능·소모품·커스텀 블록은 그대로 동작합니다.</dark_gray>",
            "", "<yellow>▶ 클릭: 전환</yellow>",
        ),
    )

    private fun durabilityIcon(item: CustomItem) = Editors.intIcon(
        Material.ANVIL,
        "<yellow>최대 내구도</yellow>",
        item.maxDurability,
        extra = listOf(
            "<gray>0 이면 바닐라 기본값입니다.</gray>",
            "<gray>바닐라 상한을 넘겨 적을 수 있습니다.</gray>",
        ),
        stepLabel = "10",
    )

    /** 도구 재질일 때만. 커스텀 블록의 도구 등급과 견준다 — 5 부터는 바닐라 도구로 못 캐는 광석. */
    private fun miningTierIcon(item: CustomItem, vanilla: Int) = Editors.intIcon(
        Material.DIAMOND_PICKAXE,
        "<aqua>채굴 등급: <white>" + ToolGrades.name(item.miningTier ?: vanilla) + "</white></aqua>",
        item.miningTier ?: vanilla,
        extra = listOf(
            "<gray>재질 기본값: <white>" + ToolGrades.name(vanilla) + " (" + vanilla + ")</white>" + (if (item.miningTier == null) " <green>← 지금</green>" else "") + "</gray>",
            "<gray>커스텀 블록의 도구 등급 이상이어야 캡니다.</gray>",
            "<gray>5 부터는 바닐라 도구에 없는 등급입니다.</gray>",
            "<dark_gray>Q: 재질 기본값으로</dark_gray>",
        ),
    )

    private fun toggleIcon(material: Material, label: String, value: Boolean, hint: String) = Icon.of(
        if (value) material else Material.GRAY_DYE,
        "<yellow>" + label + ": " + Icon.toggle(value) + "</yellow>",
        listOf("<gray>" + hint + "</gray>", "", "<yellow>▶ 클릭: 전환</yellow>"),
    )

    /**
     * 배낭 크기 — 장신구·부적·유물만. 한 번 누를 때마다 한 줄(9칸)씩 바뀌고, **몇 줄 화면이 되는지 제목에 바로 보인다**
     * (사용자 요청 2026-09-30). 45칸이 넘으면 6줄 화면(5줄 + 넘기기 줄)에 페이지로 넘긴다([BackpackLayout]).
     */
    private fun backpackIcon(item: CustomItem): ItemStack {
        val size = item.backpack
        val pages = BackpackLayout.pages(size)
        val rows = BackpackLayout.rows(size)
        val shape = when {
            size <= 0 -> "꺼짐"
            pages == 1 -> size.toString() + "칸 · " + rows + "줄"
            else -> size.toString() + "칸 · " + pages + "페이지"
        }
        val detail = when {
            size <= 0 -> emptyList()
            pages == 1 -> {
                val last = size - (rows - 1) * 9
                listOf("<gray>화면: <white>" + rows + "줄</white>" + (if (last < 9) " <dark_gray>(마지막 줄은 " + last + "칸만 씁니다)</dark_gray>" else "") + "</gray>")
            }
            else -> {
                val last = size - (pages - 1) * BackpackLayout.PER_PAGE
                listOf(
                    "<gray>화면: <white>6줄</white> <dark_gray>(한 페이지 5줄 = 45칸 + 넘기기 줄)</dark_gray></gray>",
                    "<gray>마지막 페이지: <white>" + last + "칸 · " + ((last + 8) / 9) + "줄</white></gray>",
                )
            }
        }
        return Icon.of(
            if (size > 0) Material.BUNDLE else Material.GRAY_DYE,
            "<gold>배낭: <white>" + shape + "</white></gold>",
            detail + listOf(
                "",
                "<gray>들고 우클릭하면 이 아이템 한 개만의 창고가 열립니다.</gray>",
                "<gray>내용물은 아이템에 붙습니다 — 주거나 떨어뜨리면 같이 갑니다.</gray>",
                "<gray>장착 칸(/장비)에 끼우면 <white>/배낭</white> 으로 엽니다.</gray>",
                "<dark_gray>최대 " + BackpackLayout.MAX + "칸 · 배낭 안에 배낭은 못 넣습니다</dark_gray>",
                "",
                "<yellow>▶ 좌/우클릭: 한 줄(9칸)씩 · Shift: 열 줄씩</yellow>",
                "<yellow>▶ 숫자키: 직접 입력 · 0 이면 끄기</yellow>",
            ),
        )
    }

    private fun inventoryEffectIcon(item: CustomItem): ItemStack {
        val server = custom.equipmentSettings.inventoryEffects
        val state = when (item.inventoryEffect) {
            null -> "서버 설정 따름 <gray>(지금: " + (if (server) "가방에서도" else "장착 칸에서만") + ")</gray>"
            true -> "가방·손에서도"
            false -> "장착 칸에서만"
        }
        return Icon.of(
            if (item.worksOutsideSlots(server)) Material.CHEST else Material.ENDER_CHEST,
            "<yellow>효과가 나는 곳: <white>" + state + "</white></yellow>",
            listOf(
                "<gray>장착 칸에서만이면 가방·손에 든 것은 능력치·기능이</gray>",
                "<gray>전부 멈춥니다. /장비 칸에 끼워야 효과가 납니다.</gray>",
                "<gray>서버 설정: 관리 → 장착 칸 설정</gray>",
                "", "<yellow>▶ 클릭: 서버 설정 → 장착 칸에서만 → 가방에서도</yellow>",
            ),
        )
    }

    private fun giveIcon() = Icon.of(
        Material.HOPPER,
        "<green>나에게 지급</green>",
        listOf("<yellow>▶ 좌클릭: 1개  /  Shift: 64개</yellow>"),
    )

    // --- 동작 -----------------------------------------------------------------------

    /** 겉모습 — 좌클릭: 팩의 모델에서 고르기(검색), Shift+좌클릭: 텍스처 파일·모델 이름 직접 입력, 우클릭: 없애기. */
    private fun editTexture(item: CustomItem, event: InventoryClickEvent) {
        if (event.isRightClick) {
            mutate { it.copy(texture = "", model = "") }
            return
        }
        if (!event.isShiftClick) {
            ModelListMenu.pick(
                custom, viewer,
                current = item.model.takeIf { it.isNotBlank() }?.let { PackAssets.modelNameFor(item) },
                back = { ItemEditMenu(custom, viewer, id).open(viewer) },
            ) { model ->
                custom.items.get(id)?.let { current -> custom.items.put(current.copy(model = model, texture = "")) }
                ItemEditMenu(custom, viewer, id).open(viewer)
            }
            return
        }
        Editors.promptText(
            custom.prompts,
            viewer,
            "텍스처 파일 이름",
            listOf(
                "<gray>pack/textures/ 에 넣은 png 이름을 적으세요.</gray>",
                "<gray>예: <white>boss_sword.png</white></gray>",
                "<gray>모델을 직접 만들었으면 <white>model:이름</white> 으로 적으세요.</gray>",
            ),
            reopen = { open(viewer) },
        ) { raw ->
            val text = raw.trim()
            mutate(false) {
                if (text.startsWith("model:")) {
                    it.copy(model = text.removePrefix("model:").trim(), texture = "")
                } else {
                    it.copy(texture = text, model = "")
                }
            }
        }
    }

    private fun editDurability(item: CustomItem, event: InventoryClickEvent) {
        if (Editors.isPrompt(event)) {
            Editors.promptInt(custom.prompts, viewer, "최대 내구도", 0, 100_000, { open(viewer) }) {
                mutate(false) { current -> current.copy(maxDurability = it) }
            }
            return
        }
        val next = (item.maxDurability + Editors.step(event, 10)).coerceAtLeast(0)
        mutate { it.copy(maxDurability = next) }
    }

    private fun editBackpack(item: CustomItem, event: InventoryClickEvent) {
        if (Editors.isPrompt(event)) {
            Editors.promptInt(custom.prompts, viewer, "배낭 크기(칸, 0 = 끄기)", 0, BackpackLayout.MAX, { open(viewer) }) {
                mutate(false) { current -> current.copy(backpack = it) }
            }
            return
        }
        val next = (item.backpack + Editors.step(event, 9)).coerceIn(0, BackpackLayout.MAX)
        mutate { it.copy(backpack = next) }
    }

    private fun editMiningTier(item: CustomItem, vanilla: Int, event: InventoryClickEvent) {
        when {
            event.click == ClickType.DROP -> mutate { it.copy(miningTier = null) }
            Editors.isPrompt(event) -> Editors.promptInt(custom.prompts, viewer, "채굴 등급", 0, ToolGrades.MAX_TIER, { open(viewer) }) { value ->
                mutate(false) { it.copy(miningTier = value.takeIf { tier -> tier != vanilla }) }
            }
            else -> {
                val next = ((item.miningTier ?: vanilla) + Editors.step(event, 1)).coerceIn(0, ToolGrades.MAX_TIER)
                mutate { it.copy(miningTier = next.takeIf { tier -> tier != vanilla }) }
            }
        }
    }

    /** 손에 든 것의 재질로. 빈손이면 이름을 묻는다 — 이름을 외워 적는 것보다 쉽다. */
    private fun materialFromHand() {
        val hand = viewer.inventory.itemInMainHand
        if (hand.type.isAir) return promptMaterial()
        mutate { it.copy(material = hand.type) }
    }

    private fun promptMaterial() {
        Editors.promptText(
            custom.prompts, viewer, "재질 이름",
            listOf("<gray>예: <white>DIAMOND_SWORD</white>, <white>netherite_helmet</white></gray>"),
            reopen = { open(viewer) },
        ) { raw ->
            val material = CustomItem.matchMaterial(raw)
            if (material == null) {
                viewer.sendMessage(Text.render("<red>그런 재질이 없습니다: " + raw + "</red>"))
                return@promptText
            }
            mutate(false) { it.copy(material = material) }
        }
    }

    private fun promptName() {
        Editors.promptText(
            custom.prompts, viewer, "표시 이름",
            listOf(
                "<gray>색코드와 MiniMessage 둘 다 됩니다.</gray>",
                "<gray>지우려면 <white>없음</white> 이라고 적으세요.</gray>",
            ),
            reopen = { open(viewer) },
        ) { raw ->
            val text = raw.trim()
            mutate(false) { it.copy(displayName = if (text == "없음") "" else text) }
        }
    }

    private fun give(amount: Int) {
        val stack = custom.items.create(id, amount) ?: return
        for (left in viewer.inventory.addItem(stack).values) {
            viewer.world.dropItem(viewer.location, left)
        }
    }

    /** 정의를 갈아끼운다. 불변이라 한 칸을 고치면 새 객체가 된다. */
    private fun mutate(reopen: Boolean = true, change: (CustomItem) -> CustomItem) {
        val current = item() ?: return
        custom.items.put(change(current))
        if (reopen) refresh() else open(viewer)
    }

    companion object {
        /** 사람마다 마지막으로 본 (아이템 id, 탭). 관리자 몇 명의 두 칸이라 비우지 않는다. */
        private val lastTab = HashMap<UUID, Pair<String, EditTab>>()
    }
}
