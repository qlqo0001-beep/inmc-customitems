package com.inmc.customitems.command

import com.inmc.customitems.CustomItems
import com.inmc.customitems.CustomItemsPlugin
import com.inmc.customitems.gui.ItemTypeMenu
import com.inmc.customitems.gui.PackMenu
import com.inmc.customitems.item.ItemBuilder
import com.inmc.customitems.util.Ph
import com.inmc.customitems.verify.Verifier
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.suggestion.SuggestionProvider
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import kr.inmc.core.util.Text
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/**
 * `/커스텀아이템` 한 트리.
 *
 * 한글 인자는 [StringArgumentType.greedyString] 이어야 한다 — Brigadier 의 `word()` 는
 * 한글 첫 글자에서 멈춘다. greedy 는 마지막 인자여야 해서 지급 명령은 이름을 맨 뒤로 밀었다.
 */
class CustomItemsCommand(private val custom: CustomItems) {

    fun register(plugin: JavaPlugin) {
        plugin.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register(tree().build(), "INMC 커스텀아이템", listOf("customitems", "ci", "아이템"))
            event.registrar().register(craftTree().build(), "제작대 열기", listOf("craft"))
            event.registrar().register(equipmentTree().build(), "장착 칸(장신구·부적·유물)", listOf("장신구", "equipment", "acc"))
        }
    }

    private val itemIds = SuggestionProvider<CommandSourceStack> { _, builder ->
        custom.items.ids()
            .filter { it.startsWith(builder.remainingLowerCase, ignoreCase = true) }
            .forEach { builder.suggest(it) }
        builder.buildFuture()
    }

    private val players = SuggestionProvider<CommandSourceStack> { _, builder ->
        Bukkit.getOnlinePlayers()
            .filter { it.name.startsWith(builder.remainingLowerCase, ignoreCase = true) }
            .forEach { builder.suggest(it.name) }
        builder.buildFuture()
    }

    private val setIds = SuggestionProvider<CommandSourceStack> { _, builder ->
        custom.sets.all().map { it.id }.filter { it.startsWith(builder.remainingLowerCase) }.forEach { builder.suggest(it) }
        builder.buildFuture()
    }

    private val stationIds = SuggestionProvider<CommandSourceStack> { _, builder ->
        custom.stations.all().map { it.id }.filter { it.startsWith(builder.remainingLowerCase) }.forEach { builder.suggest(it) }
        builder.buildFuture()
    }

    /** `/제작 <제작대>` — 플레이어용. `execute as <플레이어> run 제작 …` 로 NPC·명령 블록이 열어 줄 수도 있다. */
    private fun craftTree(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("제작")
            .requires { it.sender.hasPermission(CRAFT) }
            .executes { ctx ->
                custom.messages.send(ctx.source.sender, "craft-usage", Ph.of().item(custom.stations.all().joinToString(", ") { it.id }.ifEmpty { "없음" }))
                1
            }
            .then(
                Commands.argument("제작대", StringArgumentType.word()).suggests(stationIds).executes { ctx ->
                    val player = ctx.source.executor as? Player ?: ctx.source.sender as? Player
                        ?: return@executes 0.also { custom.messages.send(ctx.source.sender, "player-only") }
                    val raw = StringArgumentType.getString(ctx, "제작대")
                    val station = custom.stations.get(raw) ?: return@executes 0.also { custom.messages.send(ctx.source.sender, "craft-unknown", Ph.of().item(raw)) }
                    com.inmc.customitems.gui.StationMenu(custom, player, station.id).open(player)
                    1
                },
            )

    private fun equipmentTree(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("장비")
            .requires { it.sender.hasPermission(EQUIPMENT) }
            .executes { ctx ->
                val player = ctx.source.executor as? Player ?: ctx.source.sender as? Player
                    ?: return@executes 0.also { custom.messages.send(ctx.source.sender, "player-only") }
                if (!custom.ready) return@executes 0.also { custom.messages.send(player, "not-ready") }
                com.inmc.customitems.gui.EquipmentMenu(custom, player).open(player)
                1
            }

    private fun tree(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("커스텀아이템")
            .requires { it.sender.hasPermission(ADMIN) }
            .executes { ctx -> open(ctx.source.sender) }

            .then(Commands.literal("관리").executes { ctx -> open(ctx.source.sender) })
            .then(Commands.literal("리로드").executes { ctx -> reload(ctx.source.sender) })
            .then(Commands.literal("갱신").executes { ctx -> refresh(ctx.source.sender) })
            .then(
                Commands.literal("검증")
                    .executes { ctx -> verify(ctx.source, Verifier.Mode.ALL) }
                    .then(Commands.literal("아이템").executes { ctx -> verify(ctx.source, Verifier.Mode.ITEMS) })
                    .then(Commands.literal("화면").executes { ctx -> verify(ctx.source, Verifier.Mode.MENUS) })
                    .then(Commands.literal("전체").executes { ctx -> verify(ctx.source, Verifier.Mode.ALL) }),
            )
            .then(Commands.literal("제작").executes { ctx ->
                val player = ctx.source.sender as? Player ?: return@executes 0.also { custom.messages.send(ctx.source.sender, "player-only") }
                com.inmc.customitems.gui.StationListMenu(custom, player).open(player)
                1
            })

            .then(
                Commands.literal("리팩")
                    .executes { ctx -> openPack(ctx.source.sender) }
                    .then(Commands.literal("빌드").executes { ctx -> buildPack(ctx.source.sender) })
                    .then(Commands.literal("정보").executes { ctx -> packInfo(ctx.source.sender) }),
            )

            .then(
                Commands.literal("세트지급")
                    .then(
                        Commands.argument("플레이어", StringArgumentType.word()).suggests(players)
                            .then(
                                Commands.argument("세트", StringArgumentType.word()).suggests(setIds).executes { ctx ->
                                    giveSet(ctx.source.sender, StringArgumentType.getString(ctx, "플레이어"), StringArgumentType.getString(ctx, "세트"))
                                },
                            ),
                    ),
            )
            .then(
                Commands.literal("지급")
                    .then(
                        Commands.argument("플레이어", StringArgumentType.word()).suggests(players)
                            .then(
                                Commands.argument("개수", IntegerArgumentType.integer(1, 2304))
                                    .then(
                                        Commands.argument("이름", StringArgumentType.greedyString())
                                            .suggests(itemIds)
                                            .executes { ctx ->
                                                give(
                                                    ctx.source.sender,
                                                    StringArgumentType.getString(ctx, "플레이어"),
                                                    IntegerArgumentType.getInteger(ctx, "개수"),
                                                    StringArgumentType.getString(ctx, "이름"),
                                                )
                                            },
                                    ),
                            ),
                    ),
            )

    private fun open(sender: CommandSender): Int {
        val player = sender as? Player ?: run {
            custom.messages.send(sender, "player-only")
            return 0
        }
        if (!custom.ready) {
            custom.messages.send(player, "not-ready")
            return 0
        }
        ItemTypeMenu(custom, player).open(player)
        return 1
    }

    /** 서버 안에서 아이템·전투·화면을 실제로 돌려 본다. 검사용 정의만 쓰고 끝에 지운다. */
    /** `execute as <플레이어> run …` 로 콘솔에서도 돌릴 수 있게 실행 주체를 먼저 본다. */
    private fun verify(source: CommandSourceStack, mode: Verifier.Mode): Int {
        val player = source.executor as? Player ?: source.sender as? Player ?: return 0.also { custom.messages.send(source.sender, "player-only") }
        if (!custom.ready) return 0.also { custom.messages.send(player, "not-ready") }
        Verifier(custom).run(player, mode)
        return 1
    }

    private fun reload(sender: CommandSender): Int {
        val plugin = custom.plugin as? CustomItemsPlugin ?: return 0
        plugin.reload {
            custom.messages.send(sender, "reloaded", Ph.of().count(custom.items.size))
        }
        return 1
    }

    /**
     * 손에 든 우리 아이템을 지금 정의대로 **바로** 다시 그린다.
     *
     * 옛 정의로 그려진 아이템은 사람 눈에 들어올 때(접속·손에 쥘 때·주울 때·상자를 열 때) 저절로 다시 그려진다
     * ([com.inmc.customitems.listener.StatListener]). 이건 그걸 기다리지 않고 지금 하는 길이다.
     */
    private fun refresh(sender: CommandSender): Int {
        val player = sender as? Player ?: run {
            custom.messages.send(sender, "player-only")
            return 0
        }

        val hand = player.inventory.itemInMainHand
        val id = ItemBuilder.identify(hand) ?: run {
            custom.messages.send(player, "not-ours")
            return 0
        }
        val definition = custom.items.get(id) ?: run {
            custom.messages.send(player, "definition-gone", Ph.of().item(id))
            return 0
        }

        // 지문이 같아도 다시 그린다 — 관리자가 일부러 누른 것이다.
        val instance = com.inmc.customitems.item.ItemInstance.read(hand)
        ItemBuilder.render(hand, definition, instance.copy(revision = custom.items.revision(id)), custom.items.lookup)
        player.inventory.setItemInMainHand(hand)
        custom.messages.send(player, "refreshed")
        return 1
    }

    // --- 리소스팩 -------------------------------------------------------------------

    private fun openPack(sender: CommandSender): Int {
        val player = sender as? Player ?: run {
            custom.messages.send(sender, "player-only")
            return 0
        }
        PackMenu(custom, player).open(player)
        return 1
    }

    /**
     * 콘솔에서도 만들 수 있어야 한다 — 배포 스크립트가 부를 자리다.
     *
     * 만드는 일은 워커에서 돌고 결과만 돌아온다. 팩 하나가 수천 개 파일일 수 있어
     * 메인에서 압축하면 서버가 몇 초씩 멈춘다.
     */
    private fun buildPack(sender: CommandSender): Int {
        sender.sendMessage(Text.render("<gray>리소스팩을 만드는 중…</gray>"))
        custom.pack.build { result ->
            if (!result.ok) {
                sender.sendMessage(Text.render("<red>실패: " + result.error + "</red>"))
                return@build
            }
            sender.sendMessage(
                Text.render(
                    "<green>완료 — 파일 " + result.fileCount + "개 · 합친 팩 " + result.sourceCount +
                        "개 · 섞인 json " + result.mergedJsonCount + "개 · " + result.millis + "ms</green>",
                ),
            )
            if (result.missingTextures.isNotEmpty()) {
                sender.sendMessage(
                    Text.render("<yellow>빠진 텍스처 " + result.missingTextures.size + "개:</yellow>"),
                )
                for (line in result.missingTextures.take(10)) {
                    sender.sendMessage(Text.render("<dark_gray>  " + line + "</dark_gray>"))
                }
            }
            sender.sendMessage(Text.render("<gray>sha1 <white>" + result.sha1 + "</white></gray>"))
        }
        return 1
    }

    private fun packInfo(sender: CommandSender): Int {
        val last = custom.pack.lastReport
        sender.sendMessage(Text.render("<gray>────── <aqua>리소스팩</aqua> ──────</gray>"))
        sender.sendMessage(Text.render("<gray>폴더: <white>" + custom.pack.root.path + "</white></gray>"))

        if (last == null || !last.ok) {
            sender.sendMessage(Text.render("<yellow>아직 만든 적이 없습니다. /커스텀아이템 리팩 빌드</yellow>"))
        } else {
            sender.sendMessage(
                Text.render(
                    "<gray>파일 <white>" + last.fileCount + "</white>개 · 아이템 <white>" +
                        last.itemCount + "</white>개 · 합친 팩 <white>" + last.sourceCount + "</white>개</gray>",
                ),
            )
            sender.sendMessage(Text.render("<gray>sha1 <white>" + last.sha1 + "</white></gray>"))
        }

        val config = custom.packConfig
        if (config.canSend) {
            sender.sendMessage(Text.render("<gray>주소: <white>" + config.url + "</white></gray>"))
        } else {
            sender.sendMessage(Text.render("<dark_gray>config.yml 의 resource-pack.url 이 비어 있습니다.</dark_gray>"))
        }
        return 1
    }

    private fun give(sender: CommandSender, playerName: String, amount: Int, rawId: String): Int {
        val target = Bukkit.getPlayerExact(playerName.trim()) ?: run {
            custom.messages.send(sender, "player-not-found", Ph.of().player(playerName))
            return 0
        }
        val id = rawId.trim().lowercase()
        val stack = custom.items.create(id, amount) ?: run {
            custom.messages.send(sender, "unknown-item", Ph.of().item(rawId))
            return 0
        }

        val left = target.inventory.addItem(stack)
        for (overflow in left.values) target.world.dropItem(target.location, overflow)
        if (left.isNotEmpty()) custom.messages.send(target, "inventory-full")

        val ph = Ph.of().player(target.name).item(id).amount(amount)
        custom.messages.send(sender, "given", ph)
        if (sender !== target) custom.messages.send(target, "received", ph)
        return 1
    }

    /** 세트의 아이템을 하나씩 전부 준다. 보상 명령어(뽑기 상자 …)용 — 인첸트의 옛 `/인첸트 세트지급` 을 대신한다. */
    private fun giveSet(sender: CommandSender, playerName: String, rawId: String): Int {
        val target = Bukkit.getPlayerExact(playerName.trim()) ?: run {
            custom.messages.send(sender, "player-not-found", Ph.of().player(playerName))
            return 0
        }
        val set = custom.sets.get(rawId.trim()) ?: run {
            custom.messages.send(sender, "unknown-set", Ph.of().item(rawId))
            return 0
        }
        val stacks = custom.items.all().filter { it.set == set.id }.mapNotNull { custom.items.create(it.id, 1) }
        var overflowed = false
        for (stack in stacks) for (overflow in target.inventory.addItem(stack).values) {
            target.world.dropItem(target.location, overflow)
            overflowed = true
        }
        if (overflowed) custom.messages.send(target, "inventory-full")
        val ph = Ph.of().player(target.name).item(set.name).amount(stacks.size)
        custom.messages.send(sender, "given", ph)
        if (sender !== target) custom.messages.send(target, "received", ph)
        return 1
    }

    companion object {
        const val ADMIN = "incustomitems.admin"

        /** 제작대를 쓰는 권한. 누구나. */
        const val CRAFT = "incustomitems.craft"
        const val EQUIPMENT = "incustomitems.equipment"
    }
}
