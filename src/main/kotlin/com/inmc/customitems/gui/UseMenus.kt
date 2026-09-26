package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.ConsumeSpec
import com.inmc.customitems.item.Requirement
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent

/**
 * 소모품 설정. 켜면 우클릭으로 쓰이고(바닐라 사용 대신), 끌어다 놓는 힘(보석 빼기·수리)을 주면 우클릭 대신
 * 아이템 위에 놓아 쓴다. 물약·명령어 같은 효과는 **기능** 화면에서 발동 조건 "사용"으로 붙인다.
 */
class ConsumeMenu(custom: CustomItems, viewer: Player, id: String) :
    DetailMenu(custom, viewer, id, "<dark_gray>소모품 — $id</dark_gray>") {

    private fun change(reopen: Boolean = true, transform: (ConsumeSpec) -> ConsumeSpec) =
        mutate(reopen) { it.copy(consume = transform(it.consume ?: ConsumeSpec())) }

    /** 숫자 칸 하나. 좌/우 넛지, 숫자키 입력. */
    private fun number(event: InventoryClickEvent, label: String, current: Double, step: Double, min: Double, max: Double, apply: (ConsumeSpec, Double) -> ConsumeSpec) {
        if (Editors.isPrompt(event)) {
            Editors.promptDouble(custom.prompts, viewer, label, min, max, reopen = { open(viewer) }) { v -> change(false) { apply(it, v) } }
            return
        }
        change { apply(it, (current + Editors.step(event, step)).coerceIn(min, max)) }
    }

    override fun draw() {
        clear()
        val item = item() ?: return ItemTypeMenu(custom, viewer).open(viewer)
        val spec = item.consume
        set(SLOT_TOGGLE, Icon.of(Icon.toggleMaterial(spec != null), "<yellow>소모품으로 쓰기: " + Icon.toggle(spec != null) + "</yellow>",
            listOf("<gray>켜면 우클릭으로 쓰입니다(먹기·마시기·놓기 대신).</gray>"))) {
            mutate { it.copy(consume = if (it.consume == null) ConsumeSpec() else null) }
        }
        if (spec != null) {
            set(SLOT_HEALTH, Editors.numberIcon(Material.GLISTERING_MELON_SLICE, "<yellow>체력</yellow>", spec.health, extra = listOf("<gray>음수면 피해를 줍니다.</gray>"))) { e ->
                number(e, "체력", spec.health, 1.0, -1000.0, 1000.0) { s, v -> s.copy(health = v) }
            }
            set(SLOT_FOOD, Editors.intIcon(Material.BREAD, "<yellow>배고픔</yellow>", spec.food)) { e ->
                number(e, "배고픔", spec.food.toDouble(), 1.0, -20.0, 20.0) { s, v -> s.copy(food = v.toInt()) }
            }
            set(SLOT_SATURATION, Editors.numberIcon(Material.GOLDEN_CARROT, "<yellow>포만감</yellow>", spec.saturation)) { e ->
                number(e, "포만감", spec.saturation, 1.0, -20.0, 20.0) { s, v -> s.copy(saturation = v) }
            }
            set(SLOT_USES, Editors.intIcon(Material.CLOCK, "<yellow>쓸 수 있는 횟수</yellow>", spec.uses, extra = listOf("<gray>0 이면 닳지 않습니다. 2 이상이면 겹치지 않습니다.</gray>"))) { e ->
                number(e, "횟수", spec.uses.toDouble(), 1.0, 0.0, 10_000.0) { s, v -> s.copy(uses = v.toInt()) }
            }
            set(SLOT_COOLDOWN, Editors.numberIcon(Material.COMPASS, "<yellow>재사용 대기</yellow>", spec.cooldown, unit = "초")) { e ->
                number(e, "재사용 대기(초)", spec.cooldown, 1.0, 0.0, 86_400.0) { s, v -> s.copy(cooldown = v) }
            }
            set(SLOT_UNSOCKET, Icon.of(Icon.toggleMaterial(spec.unsocket), "<aqua>보석 빼기: " + Icon.toggle(spec.unsocket) + "</aqua>",
                listOf("<gray>소켓 아이템 위에 놓으면 마지막 보석을 빼 돌려줍니다.</gray>"))) { change { it.copy(unsocket = !it.unsocket) } }
            set(SLOT_REPAIR, Editors.intIcon(Material.ANVIL, "<aqua>수리량</aqua>", spec.repair, extra = listOf("<gray>아이템 위에 놓으면 내구도를 이만큼.</gray>"))) { e ->
                number(e, "수리량", spec.repair.toDouble(), 10.0, 0.0, 100_000.0) { s, v -> s.copy(repair = v.toInt()) }
            }
            set(SLOT_REPAIR_PERCENT, Editors.numberIcon(Material.SMITHING_TABLE, "<aqua>수리 비율</aqua>", spec.repairPercent, unit = "%", extra = listOf("<gray>최대 내구도의 이만큼.</gray>"))) { e ->
                number(e, "수리 비율(%)", spec.repairPercent, 5.0, 0.0, 100.0) { s, v -> s.copy(repairPercent = v) }
            }
            set(SLOT_IDENTIFY, Icon.of(Icon.toggleMaterial(spec.identify), "<gold>감정서: " + Icon.toggle(spec.identify) + "</gold>",
                listOf("<gray>미확인 아이템 위에 놓으면 정체를 밝힙니다.</gray>"))) { change { it.copy(identify = !it.identify) } }
            set(SLOT_DECONSTRUCT, Icon.of(Icon.toggleMaterial(spec.deconstruct), "<gold>분해 도구: " + Icon.toggle(spec.deconstruct) + "</gold>",
                listOf("<gray>아이템 위에 놓으면 한 개를 부숴 그 아이템의</gray>", "<gray>분해물을 줍니다(분해물이 없는 아이템엔 안 됩니다).</gray>", "<gray>박힌 보석은 돌려줍니다.</gray>"))) { change { it.copy(deconstruct = !it.deconstruct) } }
            set(SLOT_UPGRADE_STONE, Icon.of(Icon.toggleMaterial(spec.upgrade != null), "<gold>강화석: " + Icon.toggle(spec.upgrade != null) + "</gold>", listOf(
                "<gray>강화할 아이템 위에 놓으면 한 단계 강화를 시도합니다.</gray>",
                if (spec.upgrade != null) "<yellow>▶ 좌클릭: 규칙 설정 · 우클릭: 끄기</yellow>" else "<yellow>▶ 클릭: 켜기</yellow>",
            ))) { event ->
                when {
                    spec.upgrade == null -> change { it.copy(upgrade = com.inmc.customitems.item.UpgradeStone()) }
                    event.isRightClick -> change { it.copy(upgrade = null) }
                    else -> UpgradeStoneMenu(custom, viewer, id).open(viewer)
                }
            }
            set(SLOT_EVOLVE_STONE, Icon.of(Icon.toggleMaterial(spec.evolve != null), "<light_purple>진화석: " + Icon.toggle(spec.evolve != null) + "</light_purple>", listOf(
                "<gray>최대 강화한 아이템 위에 놓으면 진화합니다.</gray>",
                "<gray>쓸 수 있는 아이템: <white>" + (spec.evolve?.items?.takeIf { it.isNotEmpty() }?.size?.let { it.toString() + "종" } ?: "전부") + "</white></gray>",
                if (spec.evolve != null) "<yellow>▶ 좌클릭: 아이템 고르기 · 우클릭: 끄기</yellow>" else "<yellow>▶ 클릭: 켜기</yellow>",
            ))) { event ->
                when {
                    spec.evolve == null -> change { it.copy(evolve = com.inmc.customitems.item.EvolveStone()) }
                    event.isRightClick -> change { it.copy(evolve = null) }
                    else -> ChoiceMenu(custom, viewer, "진화석이 쓰이는 아이템",
                        options = { custom.items.all().filter { it.upgrade.evolution != null }.map { itemOption(custom, it) } },
                        selected = { custom.items.get(id)?.consume?.evolve?.items?.toSet().orEmpty() },
                        back = { open(viewer) },
                    ) { target -> change { it.copy(evolve = com.inmc.customitems.item.EvolveStone(toggle(it.evolve?.items.orEmpty(), target))) } }.open(viewer)
                }
            }
            set(SLOT_INFO, Icon.of(Material.BOOK, "<yellow>효과는 기능으로</yellow>", listOf(
                "<gray>물약·명령어·소리 같은 효과는 <white>기능</white> 화면에서</gray>",
                "<gray>발동 조건 <white>사용</white> 으로 붙이세요.</gray>",
                "<gray>끌어다 놓는 힘이 있으면 우클릭으로는 안 쓰입니다.</gray>",
            )))
        }
        backAndClose()
    }

    private companion object {
        const val SLOT_TOGGLE = 4
        const val SLOT_HEALTH = 19
        const val SLOT_FOOD = 20
        const val SLOT_SATURATION = 21
        const val SLOT_USES = 23
        const val SLOT_COOLDOWN = 24
        const val SLOT_UNSOCKET = 29
        const val SLOT_REPAIR = 30
        const val SLOT_REPAIR_PERCENT = 31
        const val SLOT_INFO = 33
        const val SLOT_IDENTIFY = 38
        const val SLOT_DECONSTRUCT = 39
        const val SLOT_UPGRADE_STONE = 40
        const val SLOT_EVOLVE_STONE = 41
    }
}

/** 요구 조건. 모자라면 무기는 못 때리고, 방어구는 벗겨지고, 능력치·기능·소모품이 안 돈다. */
class RequirementMenu(custom: CustomItems, viewer: Player, id: String) :
    DetailMenu(custom, viewer, id, "<dark_gray>요구 조건 — $id</dark_gray>") {

    private fun change(reopen: Boolean = true, transform: (Requirement) -> Requirement) = mutate(reopen) { it.copy(requirement = transform(it.requirement)) }

    override fun draw() {
        clear()
        val item = item() ?: return ItemTypeMenu(custom, viewer).open(viewer)
        val requirement = item.requirement
        set(SLOT_LEVEL, Editors.intIcon(Material.EXPERIENCE_BOTTLE, "<yellow>요구 레벨</yellow>", requirement.level, extra = listOf("<gray>경험치 레벨입니다. 0 이면 없음.</gray>"))) { event ->
            if (Editors.isPrompt(event)) {
                Editors.promptInt(custom.prompts, viewer, "요구 레벨", 0, 10_000, reopen = { open(viewer) }) { v -> change(false) { it.copy(level = v) } }
                return@set
            }
            change { it.copy(level = (it.level + Editors.step(event, 1)).coerceAtLeast(0)) }
        }
        set(SLOT_PERMISSION, Icon.of(Material.IRON_BARS, "<yellow>요구 권한: <white>" + requirement.permission.ifBlank { "없음" } + "</white></yellow>",
            listOf("", "<yellow>▶ 좌클릭: 적기 · 우클릭: 없애기</yellow>"))) { event ->
            if (event.isRightClick) {
                change { it.copy(permission = "") }
                return@set
            }
            Editors.promptText(custom.prompts, viewer, "요구 권한", listOf("<gray>예: <white>items.vip</white></gray>"), reopen = { open(viewer) }) { raw ->
                change(false) { it.copy(permission = raw.trim()) }
            }
        }
        backAndClose()
    }

    private companion object {
        const val SLOT_LEVEL = 11
        const val SLOT_PERMISSION = 15
    }
}
