package com.inmc.customitems

import com.inmc.customitems.ability.AbilityEngine
import com.inmc.customitems.config.Messages
import com.inmc.customitems.item.ItemRegistry
import com.inmc.customitems.pack.PackConfig
import com.inmc.customitems.pack.PackService
import com.inmc.customitems.util.Ph
import kr.inmc.core.InmcHost
import kr.inmc.core.config.ConfigService
import kr.inmc.core.input.ChatPrompt
import kr.inmc.core.integration.CustomItemHook
import kr.inmc.core.util.Placeholders
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin

/**
 * 플러그인을 엮는 서비스 로케이터.
 *
 * 이 플러그인은 **다른 플러그인들의 공급처**라 표면이 작다. 아이템을 정의하고, 만들고,
 * 되알아보는 것이 전부다. 능력이나 스킬은 여기 없다 — 그건 아이템을 쓰는 쪽이
 * [com.inmc.customitems.item.CustomItem.data] 에서 자기 숫자를 읽어 해석할 일이다.
 */
class CustomItems(override val plugin: JavaPlugin) : InmcHost {

    val logger: java.util.logging.Logger = plugin.logger

    override val io = ConfigService(plugin)

    override fun tell(target: CommandSender, key: String, ph: Placeholders?) =
        messages.send(target, key, ph as? Ph)

    /** core 의 다른 플러그인들이 우리 아이템을 만들 때 지나는 훅. 제작 재료를 알아볼 때도 쓴다. */
    val customItems = CustomItemHook(logger)

    /** 제작 재료·결과에 MMOItems 아이템을 쓸 때. */
    val mmoItems = kr.inmc.core.integration.MMOItemsHook(logger)

    @Volatile
    var messages: Messages = Messages.from(YamlConfiguration())

    val items = ItemRegistry(this)

    val sets = com.inmc.customitems.item.ItemSetRegistry(this)

    /** 종류 아래의 소분류(`categories.yml`). 목록을 나눠 보는 서랍. */
    val categories = com.inmc.customitems.item.CategoryRegistry(this)

    /** 공용 강화 방식(`upgrades.yml`). */
    val upgrades = com.inmc.customitems.item.UpgradeRegistry(this)

    /** 강화·진화를 실제로 한다. */
    val upgrading = com.inmc.customitems.player.UpgradeService(this)

    /** 장착 칸(`/장비`)의 기본 수. */
    val equipmentSettings = com.inmc.customitems.player.EquipmentSettings(this)
    val types = com.inmc.customitems.item.TypeRegistry(this)
    /** core `ItemRoles` 의 정의하는 쪽 — 다른 플러그인의 아이템 역할이 여기(items.yml)에 적힌다. */
    val roleStore = com.inmc.customitems.hook.RoleStore(this)

    /** 장착 칸 — 장신구·부적·유물. 바꿀 때마다 즉시 저장한다. */
    val equipment = com.inmc.customitems.player.EquipmentStore(this)

    val stations = com.inmc.customitems.craft.StationRegistry(this)

    val crafting = com.inmc.customitems.craft.CraftService(this)

    val recipes = com.inmc.customitems.craft.RecipeRegistry(this)

    val recipeService = com.inmc.customitems.craft.RecipeService(this)

    /** 기능을 실제로 터뜨린다. */
    val abilities = AbilityEngine(this)

    /** 한 사람이 입고 든 것 전부의 능력치 합. */
    val stats = com.inmc.customitems.player.StatService(this)

    val requirements = com.inmc.customitems.player.Requirements(this)

    val consumes = com.inmc.customitems.player.ConsumeService(this)

    /** 무기의 공격 방식(단검 뒤치기·창 관통·지팡이 마법탄…). */
    val styles = com.inmc.customitems.player.AttackStyles()

    /** 리소스팩을 만들어 낸다. */
    val pack = PackService(this)

    /** 커스텀 블록 — 놓기·알아보기·치우기(블록 상태 방식 둘 + 엔티티 방식). */
    val blocks = com.inmc.customitems.block.CustomBlocks(this)

    /** 커스텀 블록을 시간 들여 캐기 — 단단함·맞는 도구·등급. */
    val mining = com.inmc.customitems.block.BlockMining(this)

    /** 팩 배포 설정. 리로드로 통째 교체된다. */
    @Volatile
    var packConfig: PackConfig = PackConfig()

    val prompts = ChatPrompt(this)

    @Volatile
    var ready: Boolean = false
        private set

    fun markReady() {
        ready = true
    }
}
