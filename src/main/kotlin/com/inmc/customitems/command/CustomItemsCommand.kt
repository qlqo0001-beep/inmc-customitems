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
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import kr.inmc.core.integration.Mail
import kr.inmc.core.integration.Vanish
import kr.inmc.core.util.Text
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.logging.Level

/** 지급 대상 자리의 글자를 꺼내는 길 — 이름 인자에서 읽거나 `*`·`**` 그대로. */
private typealias TargetArg = (CommandContext<CommandSourceStack>) -> String

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
            event.registrar().register(backpackTree().build(), "장착 칸에 끼운 배낭 열기", listOf("backpack"))
        }
    }

    private val itemIds = SuggestionProvider<CommandSourceStack> { _, builder ->
        custom.items.ids()
            .filter { it.startsWith(builder.remainingLowerCase, ignoreCase = true) }
            .forEach { builder.suggest(it) }
        builder.buildFuture()
    }

    /** 투명인 사람은 보는 사람이 볼 수 있을 때만 제안한다(core [Vanish]). */
    private val players = SuggestionProvider<CommandSourceStack> { ctx, builder ->
        Vanish.visibleNames(ctx.source.sender)
            .filter { it.startsWith(builder.remainingLowerCase, ignoreCase = true) }
            .forEach { builder.suggest(it) }
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
            // `/장비 <플레이어>` — 남의 장착 칸 보기(읽기 전용). 오프라인은 서버에 들어온 적 있는 이름만(웹 조회를 하지 않는다).
            .then(
                Commands.argument("플레이어", StringArgumentType.word())
                    .requires { it.sender.hasPermission(EQUIPMENT_OTHER) }
                    .suggests(players)
                    .executes { ctx ->
                        val player = ctx.source.executor as? Player ?: ctx.source.sender as? Player
                            ?: return@executes 0.also { custom.messages.send(ctx.source.sender, "player-only") }
                        if (!custom.ready) return@executes 0.also { custom.messages.send(player, "not-ready") }
                        val raw = StringArgumentType.getString(ctx, "플레이어")
                        val target = Bukkit.getPlayerExact(raw) ?: Bukkit.getOfflinePlayerIfCached(raw)
                            ?: return@executes 0.also { custom.messages.send(player, "player-not-found", Ph.of().player(raw)) }
                        if (target.uniqueId == player.uniqueId) com.inmc.customitems.gui.EquipmentMenu(custom, player).open(player)
                        else com.inmc.customitems.gui.EquipmentViewMenu(custom, player, target.uniqueId, target.name ?: raw).open(player)
                        1
                    },
            )

    /** `/배낭` — 장착 칸에 끼운 배낭. 하나면 곧바로, 여럿이면 고르는 화면. 손에 든 배낭은 우클릭으로 연다. */
    /** `/배낭` = 1번, `/배낭 <번호>` — 번호는 장착 칸 배낭 줄의 앞 칸부터(사용자 결정 2026-09-30). */
    private fun backpackTree(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("배낭")
            .requires { it.sender.hasPermission(BACKPACK) }
            .executes { ctx -> openBackpack(ctx.source, 1) }
            .then(
                Commands.argument("번호", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, com.inmc.customitems.player.EquipmentStore.MAX))
                    .suggests { ctx, builder ->
                        (ctx.source.executor as? Player)?.let { player -> custom.backpacks.equipped(player).indices.forEach { builder.suggest(it + 1) } }
                        builder.buildFuture()
                    }
                    .executes { ctx -> openBackpack(ctx.source, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "번호")) },
            )

    private fun openBackpack(source: CommandSourceStack, number: Int): Int {
        val player = source.executor as? Player ?: source.sender as? Player
            ?: return 0.also { custom.messages.send(source.sender, "player-only") }
        if (!custom.ready) return 0.also { custom.messages.send(player, "not-ready") }
        custom.backpacks.openNumber(player, number)
        return 1
    }

    private fun tree(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("커스텀아이템")
            .requires { it.sender.hasPermission(ADMIN) }
            .executes { ctx -> open(ctx.source.sender) }

            .then(Commands.literal("관리").executes { ctx -> open(ctx.source.sender) })
            .then(Commands.literal("리로드").executes { ctx -> reload(ctx.source.sender) })
            .then(Commands.literal("갱신").executes { ctx -> refresh(ctx.source.sender) })
            // 월드 생성(광맥, 2026-10-09) — 켜진 블록과 이번에 켜진 뒤 심은 수 · 옛 ItemsAdder 광석 생성 옮기기(콘솔에서도).
            .then(
                Commands.literal("광맥")
                    .executes { ctx -> oreStats(ctx.source.sender) }
                    .then(Commands.literal("옮기기").executes { ctx -> oreImport(ctx.source.sender) }),
            )
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
                    .then(Commands.literal("정보").executes { ctx -> packInfo(ctx.source.sender) })
                    .then(Commands.literal("전체적용").executes { ctx ->
                        val sent = custom.pack.sendAll()
                        ctx.source.sender.sendMessage(Text.render(if (sent > 0) "<green>접속한 " + sent + "명에게 리소스팩을 다시 보냈습니다.</green>" else "<red>보내지 못했습니다 — 주소(resource-pack.url)를 보세요.</red>"))
                        1
                    }),
            )

            .then(withTargets(Commands.literal("세트지급"), ::setBranch))
            .then(withTargets(Commands.literal("지급"), ::giveBranch))

    /**
     * 지급 대상 자리 — 플레이어 이름, `*`(접속자 전원), `**`(최근 접속자 전원). `*` 는 Brigadier 의 `word()` 가 못 읽는 글자라
     * 같은 꼬리를 단 글자 그대로의 갈래로 둔다.
     */
    private fun withTargets(
        root: LiteralArgumentBuilder<CommandSourceStack>,
        tail: (TargetArg) -> ArgumentBuilder<CommandSourceStack, *>,
    ): LiteralArgumentBuilder<CommandSourceStack> =
        root
            .then(Commands.literal("*").then(tail { "*" }))
            .then(Commands.literal("**").then(tail { "**" }))
            .then(Commands.argument("플레이어", StringArgumentType.word()).suggests(players).then(tail { StringArgumentType.getString(it, "플레이어") }))

    private fun giveBranch(target: TargetArg): ArgumentBuilder<CommandSourceStack, *> =
        Commands.argument("개수", IntegerArgumentType.integer(1, 2304))
            .then(
                Commands.argument("이름", StringArgumentType.greedyString())
                    .suggests(itemIds)
                    .executes { ctx ->
                        give(ctx.source.sender, target(ctx), IntegerArgumentType.getInteger(ctx, "개수"), StringArgumentType.getString(ctx, "이름"))
                    },
            )

    private fun setBranch(target: TargetArg): ArgumentBuilder<CommandSourceStack, *> =
        Commands.argument("세트", StringArgumentType.word()).suggests(setIds).executes { ctx ->
            giveSet(ctx.source.sender, target(ctx), StringArgumentType.getString(ctx, "세트"))
        }

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

    /** `/커스텀아이템 광맥` — 월드 생성이 켜진 블록마다 규칙과 이번에 켜진 뒤 심은 칸·청크. */
    private fun oreStats(sender: CommandSender): Int {
        val ids = custom.items.generators
        if (ids.isEmpty()) {
            sender.sendMessage(Text.render("<gray>월드 생성이 켜진 블록이 없습니다. 블록 설정 화면의 <white>월드 생성</white>에서 켭니다.</gray>"))
            return 1
        }
        sender.sendMessage(Text.render("<gold>월드 생성(광맥) <white>" + ids.size + "</white>개</gold> <gray>— 처음 만들어지는 청크에만</gray>"))
        for (id in ids) {
            val rule = custom.items.get(id)?.block?.generation ?: continue
            val (blocks, chunks) = custom.oreGenerator.stats(id)
            sender.sendMessage(Text.render(
                "<aqua>$id</aqua> <gray>월드 <white>" + rule.worlds.joinToString(",").ifBlank { "없음" } + "</white> · 높이 <white>" + rule.minY + "~" + rule.maxY +
                    "</white> · <white>" + kr.inmc.core.util.Numbers.chance(rule.chunkChance) + "%</white> · <white>" + rule.veins + "×" + rule.veinSize +
                    "</white> · 심은 것 <white>$blocks</white>칸/<white>$chunks</white>청크</gray>",
            ))
        }
        return 1
    }

    /** `/커스텀아이템 광맥 옮기기` — 옛 ItemsAdder 의 블록과 광석 생성을 옮긴다(블록 화면의 버튼과 같다). */
    private fun oreImport(sender: CommandSender): Int {
        sender.sendMessage(Text.render("<gray>옛 ItemsAdder 블록·광석 생성을 읽는 중…</gray>"))
        custom.blocks.importItemsAdder { report ->
            if (!report.contentsFound) {
                sender.sendMessage(Text.render("<red>plugins/ItemsAdder/contents 폴더가 없습니다.</red>"))
                return@importItemsAdder
            }
            sender.sendMessage(Text.render("<green>블록 <white>" + report.added.size + "</white>개 · 광석 생성 <white>" + report.generation.size + "</white>개를 옮겼습니다.</green>" +
                (if (report.generation.isNotEmpty()) " <gray>(" + report.generation.joinToString(", ") + ")</gray>" else "")))
        }
        return 1
    }

    private fun reload(sender: CommandSender): Int {
        val plugin = custom.plugin as? CustomItemsPlugin ?: return 0
        plugin.reload {
            custom.messages.send(sender, "reloaded", Ph.of().count(custom.items.size))
            // 다시 읽은 아이템·팩 소스로 팩이 어긋났으면 다시 만들어 올린다(2026-10-14). 콘솔에는 서비스가 적는다.
            custom.pack.ensureInSync { problems, result ->
                if (sender is org.bukkit.command.ConsoleCommandSender) return@ensureInSync
                sender.sendMessage(Text.render(when {
                    problems.isEmpty() -> "<gray>리소스팩: 지금 아이템과 맞습니다.</gray>"
                    result?.ok == true -> "<green>리소스팩이 어긋나 다시 만들어 올렸습니다</green> <gray>— " + problems.joinToString(" · ") + "</gray>"
                    else -> "<red>리소스팩이 어긋났는데 다시 만들지 못했습니다: " + (result?.error ?: "?") + "</red>"
                }))
            }
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
        sender.sendMessage(Text.render(if (custom.ready) "<gray>리소스팩을 만드는 중…</gray>" else "<gray>아이템을 다 읽으면 리소스팩을 만듭니다…</gray>"))
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
        for (line in com.inmc.customitems.gui.PackMenu.hostLore(custom)) sender.sendMessage(Text.render(line))
        when (val sync = custom.pack.lastSync) {
            null -> sender.sendMessage(Text.render("<dark_gray>아이템과 맞는지 아직 확인하지 않았습니다(켤 때·리로드 때 확인합니다).</dark_gray>"))
            else -> if (sync.isEmpty()) {
                sender.sendMessage(Text.render("<gray>아이템과 맞음 <dark_gray>(마지막 확인 · 켤 때·리로드 때 확인)</dark_gray></gray>"))
            } else {
                sender.sendMessage(Text.render("<yellow>마지막 확인에서 어긋남:</yellow> <gray>" + sync.joinToString(" · ") + "</gray>"))
            }
        }
        return 1
    }

    private fun give(sender: CommandSender, rawTarget: String, amount: Int, rawId: String): Int {
        val id = rawId.trim().lowercase()
        if (custom.items.get(id) == null) {
            custom.messages.send(sender, "unknown-item", Ph.of().item(rawId))
            return 0
        }
        return deliver(sender, rawTarget, "아이템 지급 · $id ×$amount", Ph.of().item(id).amount(amount)) { makeStacks(id, amount) }
    }

    /** 세트의 아이템을 하나씩 전부 준다. 보상 명령어(뽑기 상자 …)용 — 인첸트의 옛 `/인첸트 세트지급` 을 대신한다. */
    private fun giveSet(sender: CommandSender, rawTarget: String, rawId: String): Int {
        val set = custom.sets.get(rawId.trim()) ?: run {
            custom.messages.send(sender, "unknown-set", Ph.of().item(rawId))
            return 0
        }
        val members = custom.items.all().filter { it.set == set.id }.map { it.id }
        if (members.isEmpty()) {
            // 빈 세트를 그대로 넘기면 빈 우편이 거절돼 "우편에 넣지 못했습니다" 로 보인다.
            custom.messages.send(sender, "set-empty", Ph.of().item(set.name))
            return 0
        }
        return deliver(sender, rawTarget, "세트 지급 · ${set.id}", Ph.of().item(set.name).amount(members.size)) {
            members.mapNotNull { custom.items.create(it, 1) }
        }
    }

    /**
     * [amount] 개를 묶음들로 — 한 번 만들면 한 묶음(칼은 1개)까지라 그대로 주면 개수가 잘렸다([Give.split]).
     * 역할 공급처가 한 묶음보다 크게 만들어도 한 묶음으로 자른다(우편에 넣을 때 바이트로 바뀌는 것이 묶음 하나다).
     */
    private fun makeStacks(id: String, amount: Int): List<ItemStack> =
        Give.split(
            amount,
            make = { left -> custom.items.create(id, left)?.also { if (it.amount > it.maxStackSize) it.amount = it.maxStackSize.coerceAtLeast(1) } },
            sizeOf = { it.amount },
            trim = { stack, size -> stack.amount = size },
        )

    /**
     * 대상마다 [make] 로 새로 만들어 준다(사람마다 제 몫을 굴린다). 접속한 사람은 가방에, 넘친 것은 우편(없으면 발밑),
     * 접속하지 않은 사람은 우편 — 우편이 없으면 거절한다. 한 사람에게 두 길로 주지 않는다.
     *
     * `*`·`**` 는 한 틱에 몰아 만들지 않고 [spread] 로 틱에 나눠 준다. `**` 의 "최근 접속자" 찾기는 사람마다 플레이어 파일을
     * 읽으므로 워커에서 한다(메인에서 디스크를 만지지 않는다). 그래서 둘은 결과 알림이 조금 늦게 온다.
     */
    private fun deliver(sender: CommandSender, rawTarget: String, source: String, ph: Ph, make: () -> List<ItemStack>): Int {
        when (val target = Give.parse(rawTarget)) {
            is Give.Target.Named -> {
                val online = Bukkit.getPlayerExact(target.name)
                if (online != null) {
                    handOver(online, make(), source)
                    val named = ph.copy().player(online.name)
                    custom.messages.send(sender, "given", named)
                    if (sender !== online) custom.messages.send(online, "received", named)
                    return 1
                }
                val offline = Bukkit.getOfflinePlayerIfCached(target.name)?.takeIf { it.hasPlayedBefore() } ?: run {
                    custom.messages.send(sender, "player-not-found", Ph.of().player(target.name))
                    return 0
                }
                val name = offline.name ?: target.name
                if (!Mail.available) {
                    custom.messages.send(sender, "mail-unavailable")
                    return 0
                }
                val sent = mail(offline.uniqueId, source, make())
                if (!sent.ok) {
                    custom.messages.send(sender, "mail-failed", Ph.of().player(name))
                    return 0
                }
                custom.messages.send(sender, "given-mail", ph.copy().player(name))
                if (sent.rest.isNotEmpty()) custom.messages.send(sender, "mail-skipped", Ph.of().player(name).count(sent.rest.size))
                return 1
            }

            Give.Target.Online -> {
                val players = Bukkit.getOnlinePlayers().map { it.uniqueId to it.name }
                spread(sender, players, source, ph, make) { tally ->
                    custom.messages.send(sender, "given-all", ph.copy().count(tally.direct + tally.mailed))
                }
                return 1
            }

            Give.Target.Recent -> {
                // 접속하지 않은 사람은 우편으로만 줄 수 있다 — 우편이 없으면 접속한 사람에게도 주지 않는다(반쪽 지급을 막는다).
                if (!Mail.available) {
                    custom.messages.send(sender, "mail-unavailable")
                    return 0
                }
                val online = Bukkit.getOnlinePlayers().map { it.uniqueId to it.name }
                val onlineIds = online.mapTo(HashSet()) { it.first }
                val days = custom.giveRecentDays
                custom.messages.send(sender, "give-recent-searching", Ph.of().value(days.toString()))
                // getOfflinePlayers 는 playerdata 폴더를, lastSeen 은 사람마다 그 파일을 읽는다 — 워커에서.
                custom.io.async({
                    val now = System.currentTimeMillis()
                    Bukkit.getOfflinePlayers()
                        .filter { it.uniqueId !in onlineIds && Give.seenWithin(it.lastSeen, now, days) }
                        .map { it.uniqueId to (it.name ?: it.uniqueId.toString()) }
                }) { offline ->
                    spread(sender, online + offline, source, ph, make) { tally ->
                        custom.messages.send(sender, "given-recent", ph.copy().count(tally.direct + tally.mailed).value(tally.mailed.toString()))
                    }
                }
                return 1
            }
        }
    }

    /** 여럿에게 나눠 준 결과. */
    private class Tally {
        var direct = 0
        var mailed = 0
        val failed = ArrayList<String>()
        val skipped = ArrayList<String>()
        var skippedStacks = 0
    }

    /**
     * [recipients](uuid, 이름)에게 틱을 나눠 준다. 차례가 왔을 때 접속해 있으면 가방에(넘친 것은 [handOver]), 아니면 우편 — 우편이 없으면
     * 그 사람은 건너뛴다(`*` 를 치는 사이에 나간 사람). 다 돌면 [summary] 뒤에 못 넣은 사람들을 알린다.
     */
    private fun spread(
        sender: CommandSender,
        recipients: List<Pair<UUID, String>>,
        source: String,
        ph: Ph,
        make: () -> List<ItemStack>,
        summary: (Tally) -> Unit,
    ) {
        val tally = Tally()
        val jobs = recipients.map { (id, name) ->
            {
                val player = Bukkit.getPlayer(id)
                when {
                    player != null -> {
                        val stacks = make()
                        handOver(player, stacks, source)
                        if (sender !== player) custom.messages.send(player, "received", ph.copy().player(player.name))
                        tally.direct++
                        stacks.size
                    }

                    Mail.available -> {
                        val stacks = make()
                        val sent = mail(id, source, stacks)
                        if (!sent.ok) {
                            tally.failed += name
                        } else {
                            tally.mailed++
                            if (sent.rest.isNotEmpty()) {
                                tally.skipped += name
                                tally.skippedStacks += sent.rest.size
                            }
                        }
                        stacks.size
                    }

                    else -> 0
                }
            }
        }
        drip(jobs) {
            summary(tally)
            if (tally.failed.isNotEmpty()) custom.messages.send(sender, "mail-failed", Ph.of().player(Give.names(tally.failed)))
            if (tally.skipped.isNotEmpty()) {
                custom.messages.send(sender, "mail-skipped", Ph.of().player(Give.names(tally.skipped)).count(tally.skippedStacks))
            }
        }
    }

    /**
     * [jobs] 를 틱마다 묶음 [Give.TICK_BUDGET] 개 몫씩 돈다(적어도 한 사람) — 칼 2304개 × 300명을 한 틱에 만들면 서버가 멈춘다.
     * 하나가 던져도 나머지는 계속 돈다. 다 돌면 [done]. 도중에 플러그인이 꺼지면 남은 사람은 받지 못한다.
     */
    private fun drip(jobs: List<() -> Int>, done: () -> Unit) {
        val queue = jobs.iterator()
        val next: () -> Int? = {
            if (!queue.hasNext()) {
                null
            } else {
                runCatching(queue.next()).getOrElse {
                    custom.plugin.logger.log(Level.WARNING, "지급 실패", it)
                    0
                }
            }
        }
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(custom.plugin, { task ->
            if (!Give.tick(Give.TICK_BUDGET, next)) {
                task.cancel()
                done()
            }
        }, 1L, 1L)
    }

    /** 가방에 넣고 넘친 것은 우편으로(우편이 없거나 못 넣은 것은 발밑). */
    private fun handOver(player: Player, stacks: List<ItemStack>, source: String) {
        val left = stacks.flatMap { player.inventory.addItem(it).values }
        if (left.isEmpty()) return
        val sent = if (Mail.available) mail(player.uniqueId, source, left) else null
        if (sent?.ok == true) custom.messages.send(player, "inventory-full-mail")
        val ground = sent?.rest ?: left
        if (ground.isEmpty()) return
        for (overflow in ground) player.world.dropItem(player.location, overflow)
        custom.messages.send(player, "inventory-full")
    }

    /** [ok] = 우편 한 통이 들어갔다. [rest] = 우편에 넣지 못한 묶음(못 넣었으면 전부). */
    private class Mailed(val ok: Boolean, val rest: List<ItemStack>)

    /**
     * 우편에 넣는다. [Mail.send] 는 바이트로 못 바꾸는 묶음을 말없이 빼고도 true 를 주므로, 여기서 먼저 바꿔 못 바꾼 것을 돌려준다
     * — 부른 쪽이 발밑에 떨구거나 보낸 사람에게 알린다.
     */
    private fun mail(playerId: UUID, source: String, stacks: List<ItemStack>): Mailed {
        val bytes = ArrayList<ByteArray>()
        val rest = ArrayList<ItemStack>()
        for (stack in stacks) {
            if (stack.type.isAir || stack.amount <= 0) continue
            val data = runCatching { stack.serializeAsBytes() }.getOrNull()
            if (data == null) rest += stack else bytes += data
        }
        if (rest.isNotEmpty()) custom.plugin.logger.warning("우편에 넣을 수 없는 아이템 ${rest.size}묶음 ($source → $playerId)")
        if (bytes.isEmpty()) return Mailed(false, stacks)
        val ok = Mail.deposit(
            playerId,
            Mail.Parcel(kind = Mail.Kind.REWARD, origin = custom.plugin.name, source = source, items = bytes),
        )
        return if (ok) Mailed(true, rest) else Mailed(false, stacks)
    }

    companion object {
        const val ADMIN = "incustomitems.admin"

        /** 제작대를 쓰는 권한. 누구나. */
        const val CRAFT = "incustomitems.craft"
        const val EQUIPMENT = "incustomitems.equipment"
        const val EQUIPMENT_OTHER = "incustomitems.equipment.other"
        const val BACKPACK = "incustomitems.backpack"
    }
}
