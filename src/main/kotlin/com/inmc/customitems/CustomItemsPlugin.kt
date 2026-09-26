package com.inmc.customitems

import com.inmc.customitems.command.CustomItemsCommand
import com.inmc.customitems.config.Messages
import com.inmc.customitems.hook.InmcItemProvider
import com.inmc.customitems.hook.MetricsHook
import com.inmc.customitems.item.ItemBuilder
import com.inmc.customitems.scheduler.Ticker
import kr.inmc.core.integration.CustomItemHook
import org.bukkit.plugin.java.JavaPlugin

/**
 * 진입점. **배선만** 한다.
 *
 * 다른 플러그인들과 달리 여기는 **등록 순서가 중요하다.** core 에 공급처를 꽂는 일이
 * 소비자 플러그인들이 아이템을 찾기 시작하기 전에 끝나야 한다.
 *
 * Paper 는 `inmc-core` 를 `load: BEFORE` 로 먼저 올리지만, **우리와 낚시·인벤키퍼 사이에는
 * 순서가 없다.** 그래서 공급처는 정의를 다 읽기 전에 미리 꽂는다 — 아직 목록이 비어 있으면
 * `create` 가 null 을 주고, 그건 "아이템 없음"으로 이미 잘 다뤄지는 경우다. 반대로 늦게
 * 꽂으면 그 사이의 조회가 조용히 스냅샷으로 떨어진다.
 */
class CustomItemsPlugin : JavaPlugin() {

    private lateinit var custom: CustomItems
    private lateinit var ticker: Ticker
    private lateinit var passiveTicker: com.inmc.customitems.scheduler.PassiveTicker
    private lateinit var metrics: MetricsHook

    override fun onEnable() {
        custom = CustomItems(this)
        ticker = Ticker(custom)
        passiveTicker = com.inmc.customitems.scheduler.PassiveTicker(custom)
        metrics = MetricsHook(custom)

        custom.customItems.setup()
        custom.mmoItems.setup()
        custom.pack.prepare()
        custom.pack.loadSha1()

        // 목록보다 먼저 꽂는다. 위 KDoc 참조.
        CustomItemHook.register(InmcItemProvider(custom))
        // 죽을 때 장착 칸도 가방과 같이 — 인벤키퍼가 이 창구를 본다.
        kr.inmc.core.integration.ExtraInventory.register(custom.equipment)

        registerListeners()
        CustomItemsCommand(custom).register(this)

        reload {
            custom.markReady()
            ticker.start()
            passiveTicker.start()
            metrics.start()
            logger.info("inmc-customitems 활성화 완료 - 아이템 " + custom.items.size + "개")
        }
    }

    override fun onDisable() {
        if (!::custom.isInitialized) return
        ticker.stop()
        passiveTicker.stop()
        metrics.stop()
        // 우리가 내려가면 우리 아이템도 못 만든다. 남겨두면 다른 플러그인이 죽은 공급처에
        // 계속 묻게 되고, 그쪽 로그에 우리 이름이 안 나와 원인을 찾기 어렵다.
        CustomItemHook.unregister(ItemBuilder.NAMESPACE)
        kr.inmc.core.integration.ExtraInventory.unregister(custom.equipment)
        // 빠지면 역할을 쓰는 플러그인들이 제 파일로 돌아간다(옮긴 뒤라면 빈 목록 — 우리 없이는 우리 아이템도 없다).
        kr.inmc.core.integration.ItemRoles.detach(custom.roleStore)
        custom.items.flushBlocking()
        custom.sets.flushBlocking()
        custom.categories.flushBlocking()
        custom.upgrades.flushBlocking()
        custom.equipmentSettings.flushBlocking()
        custom.types.flushBlocking()
        custom.stations.flushBlocking()
        custom.recipes.flushBlocking()
        custom.recipeService.clear()
        custom.io.shutdown()
    }

    private fun registerListeners() {
        val manager = server.pluginManager
        manager.registerEvents(com.inmc.customitems.listener.ChatInputListener(custom), this)
        manager.registerEvents(com.inmc.customitems.listener.CombatListener(custom), this)
        manager.registerEvents(com.inmc.customitems.listener.StatListener(custom), this)
        manager.registerEvents(com.inmc.customitems.listener.ApplyListener(custom), this)
        manager.registerEvents(custom.recipeService, this)
        manager.registerEvents(com.inmc.customitems.listener.StationListener(custom), this)
        manager.registerEvents(com.inmc.customitems.listener.AbilityListener(custom), this)
        manager.registerEvents(com.inmc.customitems.listener.InteractListener(custom), this)
        manager.registerEvents(com.inmc.customitems.listener.ResourcePackListener(custom), this)
        manager.registerEvents(com.inmc.customitems.listener.EquipmentListener(custom), this)
        manager.registerEvents(com.inmc.customitems.listener.BlockListener(custom), this)
        manager.registerEvents(kr.inmc.core.listener.MenuListener(custom), this)
    }

    /**
     * `messages.yml` 과 `items.yml` 을 다시 읽는다.
     *
     * 파일 읽기는 워커에서, 파싱과 반영은 메인에서 한다 — core 의
     * [kr.inmc.core.config.ConfigService] 가 지키는 계약이다.
     */
    fun reload(then: () -> Unit = {}) {
        for (name in RESOURCES) custom.io.copyDefault(name, custom.io.file(name))

        custom.io.async({
            custom.io.load(custom.io.file("config.yml")) to custom.io.load(custom.io.file("messages.yml"))
        }) { (configYaml, messagesYaml) ->
            custom.packConfig = com.inmc.customitems.pack.PackConfig.from(configYaml)
            custom.messages = Messages.from(messagesYaml)

            // 정의 객체가 교체되므로 열린 화면은 닫는다 - 그대로 두면 버려진 객체를 계속
            // 편집하게 되고, 저장은 되는데 반영이 안 된다.
            closeOpenMenus()

            // 세트·강화 방식이 먼저다 — 아이템의 지문이 그 정의까지 담는다. 장착 칸 설정도("장착 칸 밖에서도 효과") —
            // 한 줄로 도는 일꾼이라 아래 stations 보다 먼저 읽히고, 아이템은 stations 다음이다.
            custom.sets.load {
                custom.upgrades.load {
                    custom.equipmentSettings.load {}
                    custom.types.load {}
                    custom.categories.load {}
                    custom.stations.load {
                        custom.items.load {
                            // 조합법은 결과를 만들어 보므로 아이템 다음에.
                            custom.recipes.load {
                                custom.recipeService.apply()
                                // 아이템을 다 읽은 뒤 역할 창구를 꽂는다 — 꽂는 순간 인벤키퍼·낚시 … 가 다시 읽고(처음이면 옮긴다).
                                kr.inmc.core.integration.ItemRoles.attach(custom.roleStore)
                                // 팩이 이미 쓰는 블록 상태(새 블록이 피해 간다)와 서버의 갱신 끄기 확인 — 아이템을 다 읽은 뒤에.
                                custom.blocks.load()
                                then()
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 이 플러그인이 띄운 화면을 전부 닫는다.
     *
     * core 가 소유한 화면(공용 확인창)도 잡아야 하므로 클래스가 아니라
     * [kr.inmc.core.gui.Menu.owner] 로 가려낸다.
     */
    private fun closeOpenMenus() {
        for (player in server.onlinePlayers) {
            val holder = player.openInventory.topInventory.holder
            if (holder !is kr.inmc.core.gui.Menu || holder.owner !== custom) continue
            player.closeInventory()
        }
    }

    private companion object {
        /** 처음 한 번 깔아주는 배포 파일들. */
        val RESOURCES = listOf("config.yml", "messages.yml", "items.yml", "sets.yml", "upgrades.yml", "stations.yml", "recipes.yml", "categories.yml")
    }
}
