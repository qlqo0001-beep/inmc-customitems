package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.AttackStyle
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.ItemType
import com.inmc.customitems.item.Tier
import com.inmc.customitems.pack.PackAssets
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.integration.CustomEnchantHook
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * 아이템 한 개의 설정. **모든 문을 여기서 연다.**
 *
 * 한 화면에 다 넣지 않는다 — 능력치 16종, 기능 여러 개, 인챈트 여러 개를 한 창에 펴면
 * 54칸을 넘고 무엇이 무엇인지 알 수 없게 된다. 여기는 **허브**고, 각 덩이는 자기 화면을
 * 갖는다. 대신 미리보기를 가운데 크게 둬서 어느 화면에서 돌아와도 결과가 바로 보인다.
 *
 * **정의가 아니라 id 를 들고 있다.** [CustomItem] 은 불변이라 한 칸을 고칠 때마다 새
 * 객체가 되는데, 화면이 옛 객체를 붙들고 있으면 두 번째 편집이 첫 번째를 지운다.
 */
class ItemEditMenu(
    custom: CustomItems,
    private val viewer: Player,
    private val id: String,
) : Menu(custom, SIZE, Text.renderFlat("<dark_gray>아이템 — " + id + "</dark_gray>")) {

    private fun item(): CustomItem? = custom.items.get(id)

    override fun draw() {
        clear()
        val item = item() ?: run {
            // 다른 관리자가 지웠다. 빈 화면을 보여주느니 목록으로 돌린다.
            ItemTypeMenu(custom, viewer).open(viewer)
            return
        }
        fillEmpty(Icon.EDGE)

        set(SLOT_PREVIEW, preview(item))

        // --- 모양 ---
        set(SLOT_MATERIAL, materialIcon(item)) { event -> if (event.isRightClick) promptMaterial() else materialFromHand() }
        set(SLOT_NAME, nameIcon(item)) { promptName() }
        set(SLOT_LORE, loreIcon(item)) { LoreMenu(custom, viewer, id).open(viewer) }

        // --- 분류 ---
        set(SLOT_TEXTURE, textureIcon(item)) { event ->
            if (event.isRightClick) {
                mutate { it.copy(texture = "", model = "") }
                return@set
            }
            // 좌클릭: 팩의 모델에서 고른다(검색 가능). Shift+좌클릭: 텍스처 파일 이름·모델 이름을 직접 적는다.
            if (!event.isShiftClick) {
                ModelListMenu.pick(
                    custom, viewer,
                    current = item.model.takeIf { it.isNotBlank() }?.let { PackAssets.modelNameFor(item) },
                    back = { ItemEditMenu(custom, viewer, id).open(viewer) },
                ) { model ->
                    custom.items.get(id)?.let { current -> custom.items.put(current.copy(model = model, texture = "")) }
                    ItemEditMenu(custom, viewer, id).open(viewer)
                }
                return@set
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

        set(SLOT_TYPE, typeIcon(item)) { event ->
            mutate {
                val next = Editors.cycle(event, custom.types.all(), custom.types.of(it))
                // 소분류는 종류에 딸려 있다 — 다른 종류로 옮기면 뗀다. 동작은 그 종류의 기준(base)을 따른다.
                it.copy(
                    type = next.base,
                    customType = if (next.builtin) "" else next.id,
                    category = if (custom.categories.get(it.category)?.type == next.id) it.category else "",
                )
            }
        }
        set(SLOT_CATEGORY, categoryIcon(item)) { chooseCategory(custom.types.of(item)) }
        set(SLOT_TIER, tierIcon(item)) { event ->
            mutate { it.copy(tier = Editors.cycle(event, Tier.entries.toList(), it.tier)) }
        }
        set(SLOT_STYLE, styleIcon(item)) { event ->
            mutate { it.copy(style = Editors.cycle(event, AttackStyle.entries.toList(), it.style)) }
        }

        // --- 속살 ---
        set(SLOT_STATS, statsIcon(item)) { StatsMenu(custom, viewer, id).open(viewer) }
        set(SLOT_ABILITIES, abilitiesIcon(item)) { AbilityListMenu(custom, viewer, id).open(viewer) }
        set(SLOT_ENCHANTS, enchantsIcon(item)) { EnchantMenu(custom, viewer, id).open(viewer) }
        set(SLOT_CUSTOM_ENCHANTS, customEnchantsIcon(item)) { CustomEnchantMenu(custom, viewer, id).open(viewer) }
        set(SLOT_MODIFIERS, modifiersIcon(item)) { ModifierListMenu(custom, viewer, id).open(viewer) }
        set(SLOT_SET, setIcon(item)) { SetChooseMenu(custom, viewer, id).open(viewer) }
        set(SLOT_DATA, dataIcon(item)) { DataMenu(custom, viewer, id).open(viewer) }

        // --- 잡다 ---
        set(SLOT_GLOW, toggleIcon(Material.GLOWSTONE_DUST, "빛나게", item.glow, "인챈트 없이 반짝입니다.")) {
            mutate { it.copy(glow = !it.glow) }
        }
        set(
            SLOT_UNBREAKABLE,
            toggleIcon(Material.ANVIL, "무한 내구도", item.unbreakable, "닳지 않습니다."),
        ) { mutate { it.copy(unbreakable = !it.unbreakable) } }
        set(SLOT_DURABILITY, durabilityIcon(item)) { event ->
            if (Editors.isPrompt(event)) {
                Editors.promptInt(custom.prompts, viewer, "최대 내구도", 0, 100_000, { open(viewer) }) {
                    mutate(false) { current -> current.copy(maxDurability = it) }
                }
                return@set
            }
            val next = (item.maxDurability + Editors.step(event, 10)).coerceAtLeast(0)
            mutate { it.copy(maxDurability = next) }
        }
        set(SLOT_FLAGS, flagsIcon(item)) { FlagMenu(custom, viewer, id).open(viewer) }
        set(SLOT_SOCKETS, Icon.of(Material.AMETHYST_CLUSTER, "<aqua>소켓 <white>" + item.sockets.size + "</white>개</aqua>",
            item.sockets.map { "<dark_gray>◇ " + it + "</dark_gray>" } + listOf("", "<yellow>▶ 클릭: 소켓 설정</yellow>"))) { SocketMenu(custom, viewer, id).open(viewer) }
        set(SLOT_GEM, Icon.of(Material.EMERALD, "<aqua>보석: <white>" + (item.gem?.let { com.inmc.customitems.item.ItemBuilder.socketLabel(it.color) + " · " + kr.inmc.core.util.Numbers.chance(it.chance) + "%" } ?: "아님") + "</white></aqua>",
            listOf("<gray>이 아이템을 다른 아이템의 소켓에 박는 보석으로.</gray>", "", "<yellow>▶ 클릭: 보석 설정</yellow>"))) { GemMenu(custom, viewer, id).open(viewer) }
        set(SLOT_CONSUME, Icon.of(Material.HONEY_BOTTLE, "<green>소모품: <white>" + (if (item.consume != null) "켜짐" else "아님") + "</white></green>",
            listOf("<gray>우클릭으로 쓰는 회복·효과, 보석 빼기·수리.</gray>", "", "<yellow>▶ 클릭: 소모품 설정</yellow>"))) { ConsumeMenu(custom, viewer, id).open(viewer) }
        set(SLOT_REQUIREMENT, Icon.of(Material.IRON_BARS, "<red>요구 조건</red>", listOf(
            "<gray>레벨 <white>" + item.requirement.level + "</white> · 권한 <white>" + item.requirement.permission.ifBlank { "없음" } + "</white></gray>",
            "", "<yellow>▶ 클릭: 요구 조건 설정</yellow>"))) { RequirementMenu(custom, viewer, id).open(viewer) }

        set(SLOT_UNIDENTIFIED, toggleIcon(Material.FILLED_MAP, "미확인으로 나오기", item.unidentified, "만들면 정체가 가려지고 감정서로 밝힙니다.")) {
            mutate { it.copy(unidentified = !it.unidentified) }
        }
        set(SLOT_SALVAGE, Icon.of(Material.GRINDSTONE, "<gold>분해물 <white>" + item.salvage.size + "</white>종</gold>", listOf(
            "<gray>분해 도구를 이 아이템 위에 놓으면 나오는 것.</gray>",
            "<gray>비우면 분해할 수 없습니다.</gray>", "", "<yellow>▶ 클릭: 칸에 넣어 정하기</yellow>",
        ))) {
            PartGridMenu(custom, viewer, "분해물 — $id", load = { item()?.salvage }, save = { parts -> item()?.let { custom.items.put(it.copy(salvage = parts)) } }, back = { open(viewer) }).open(viewer)
        }

        set(SLOT_COMPONENTS, Icon.of(Material.LEATHER_HELMET, "<yellow>바닐라 부품" + (if (item.components.isEmpty) "" else " <white>(설정됨)</white>") + "</yellow>", listOf(
            "<gray>최대 겹침 · 툴팁 모양 · 입는 칸 · 색 · 갑옷 장식</gray>",
            "<gray>머리 텍스처 · 활공 · 불 저항</gray>", "", "<yellow>▶ 클릭: 설정</yellow>",
        ))) { ComponentMenu(custom, viewer, id).open(viewer) }

        val table = item.upgrade.table(custom.items.lookup)
        set(SLOT_UPGRADE, Icon.of(Material.ANVIL, "<gold>강화·진화</gold>", listOf(
            "<gray>강화 방식: <white>" + (if (item.upgrade.own != null) "이 아이템 전용" else table?.name ?: "없음") + "</white> · 최대 <white>+" + (table?.maxLevel ?: 0) + "</white></gray>",
            "<gray>진화: <white>" + (item.upgrade.evolution?.let { custom.items.get(it.into)?.label() ?: it.into } ?: "없음") + "</white></gray>",
            "", "<yellow>▶ 클릭: 설정</yellow>",
        ))) { ItemUpgradeMenu(custom, viewer, id).open(viewer) }
        set(SLOT_NO_DUPLICATE, toggleIcon(Material.TOTEM_OF_UNDYING, "같은 부적 중복 안 함", item.noDuplicate,
            "부적: 가방·장착 칸에 같은 부적이 여럿이면(강화 단계가 달라도) 가장 높은 것 하나만 효과가 납니다.")) {
            mutate { it.copy(noDuplicate = !it.noDuplicate) }
        }
        if (item.type == ItemType.ACCESSORY || item.type.carried) {
            set(SLOT_INVENTORY_EFFECT, inventoryEffectIcon(item)) {
                // 서버 설정 → 장착 칸에서만 → 가방에서도 → 서버 설정
                mutate { it.copy(inventoryEffect = when (it.inventoryEffect) { null -> false; false -> true; true -> null }) }
            }
        }

        set(SLOT_ROLES, Icon.of(Material.COMPARATOR, "<aqua>연동 역할 <white>" + item.roles.size + "</white>개</aqua>", buildList {
            add("<gray>다른 플러그인에서 이 아이템이 맡는 일 —</gray>")
            add("<gray>보호권·상자 캡슐·낚싯대·화폐 …</gray>")
            for (key in item.roles.keys) add("<green>▪ " + (kr.inmc.core.integration.ItemRoles.role(key)?.let { it.owner + " · " + it.label } ?: key) + "</green>")
            add("")
            add("<yellow>▶ 클릭: 역할 보기·붙이기</yellow>")
        })) { RoleListMenu(custom, viewer, id).open(viewer) }
        set(SLOT_BLOCK, Icon.of(item.block?.kind?.base ?: Material.GRASS_BLOCK, "<green>블록: <white>" + (item.block?.kind?.label ?: "아님") + "</white></green>", listOf(
            "<gray>들고 우클릭하면 이 모양의 블록으로 놓입니다.</gray>",
            "<gray>랜덤박스 상자 모양으로도 고를 수 있습니다.</gray>",
            "", "<yellow>▶ 클릭: 블록 설정</yellow>",
        ))) { BlockMenu(custom, viewer, id).open(viewer) }
        com.inmc.customitems.block.ToolGrades.vanillaTier(item.material)?.let { vanilla -> set(SLOT_MINING_TIER, miningTierIcon(item, vanilla)) { event ->
            when {
                event.click == org.bukkit.event.inventory.ClickType.DROP -> mutate { it.copy(miningTier = null) }
                Editors.isPrompt(event) -> Editors.promptInt(custom.prompts, viewer, "채굴 등급", 0, com.inmc.customitems.block.ToolGrades.MAX_TIER, { open(viewer) }) { value ->
                    mutate(false) { it.copy(miningTier = value.takeIf { tier -> tier != vanilla }) }
                }
                else -> {
                    val next = ((item.miningTier ?: vanilla) + Editors.step(event, 1)).coerceIn(0, com.inmc.customitems.block.ToolGrades.MAX_TIER)
                    mutate { it.copy(miningTier = next.takeIf { tier -> tier != vanilla }) }
                }
            }
        } }
        set(SLOT_GIVE, giveIcon()) { event -> give(if (event.isShiftClick) 64 else 1) }
        set(Paging.SLOT_BACK, Icon.back()) { backToList(item) }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    // --- 아이콘 ---------------------------------------------------------------------

    /** 미리보기 — 진짜로 만들어본 아이템. 인첸트 칸 수("n/m")는 아이템 로어에 안 적고 여기(관리 화면)에서만 보인다. */
    private fun preview(item: CustomItem): org.bukkit.inventory.ItemStack {
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

    private fun typeIcon(item: CustomItem): org.bukkit.inventory.ItemStack {
        val current = custom.types.of(item)
        return Icon.of(
            current.icon,
            "<yellow>종류: <white>" + current.name + "</white></yellow>",
            Editors.optionList(custom.types.all(), current) { it.name + if (it.builtin) "" else " <dark_gray>(" + it.base.display + "처럼)</dark_gray>" } +
                listOf("", "<gray>만든 종류는 고른 기본 종류처럼 동작합니다.</gray>", "<dark_gray>종류 만들기·이름 바꾸기: 관리 → 종류 관리</dark_gray>") + Editors.cycleHint,
        )
    }

    private fun categoryIcon(item: CustomItem): org.bukkit.inventory.ItemStack {
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
        "<aqua>채굴 등급: <white>" + com.inmc.customitems.block.ToolGrades.name(item.miningTier ?: vanilla) + "</white></aqua>",
        item.miningTier ?: vanilla,
        extra = listOf(
            "<gray>재질 기본값: <white>" + com.inmc.customitems.block.ToolGrades.name(vanilla) + " (" + vanilla + ")</white>" + (if (item.miningTier == null) " <green>← 지금</green>" else "") + "</gray>",
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

    private fun inventoryEffectIcon(item: CustomItem): org.bukkit.inventory.ItemStack {
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

    /**
     * 재질을 바꾼다. **손에 든 것이 있으면 그걸 쓴다** — 이름을 외워 적는 것보다 쉽다.
     */
    /** 손에 든 것의 재질로. 빈손이면 이름을 묻는다. */
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
        const val SIZE = 54

        const val SLOT_PREVIEW = 4

        const val SLOT_MATERIAL = 10
        const val SLOT_NAME = 11
        const val SLOT_LORE = 12
        const val SLOT_TEXTURE = 14

        const val SLOT_TYPE = 15
        const val SLOT_TIER = 16
        const val SLOT_CATEGORY = 17

        const val SLOT_STATS = 19
        const val SLOT_ABILITIES = 20
        const val SLOT_ENCHANTS = 21
        const val SLOT_DATA = 22
        const val SLOT_CUSTOM_ENCHANTS = 23
        const val SLOT_MODIFIERS = 24
        const val SLOT_SET = 25

        const val SLOT_GLOW = 29
        const val SLOT_UNBREAKABLE = 30
        const val SLOT_DURABILITY = 31
        const val SLOT_FLAGS = 32
        const val SLOT_SOCKETS = 33
        const val SLOT_GEM = 34
        const val SLOT_CONSUME = 37
        const val SLOT_REQUIREMENT = 38
        const val SLOT_STYLE = 39
        const val SLOT_UNIDENTIFIED = 40
        const val SLOT_SALVAGE = 41
        const val SLOT_COMPONENTS = 42
        const val SLOT_UPGRADE = 43
        const val SLOT_NO_DUPLICATE = 44
        const val SLOT_INVENTORY_EFFECT = 35
        const val SLOT_ROLES = 26
        const val SLOT_BLOCK = 28
        const val SLOT_MINING_TIER = 27

        const val SLOT_GIVE = 49
    }
}
