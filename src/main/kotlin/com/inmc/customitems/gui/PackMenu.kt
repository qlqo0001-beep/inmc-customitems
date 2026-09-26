package com.inmc.customitems.gui

import com.inmc.customitems.CustomItems
import com.inmc.customitems.pack.PackAssets
import com.inmc.customitems.pack.PackService
import kr.inmc.core.gui.Icon
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player

/**
 * 리소스팩 화면.
 *
 * 관리자가 여기서 알고 싶은 것은 셋이다 — **지금 상태가 어떤가, 만들면 무엇이 들어가나,
 * 무엇이 빠져 있나.** 특히 세 번째가 중요하다: 텍스처 파일 이름을 오타로 적으면 그 아이템만
 * 조용히 바닐라 모양으로 나오는데, 그걸 게임에서 찾으려면 아이템을 하나씩 꺼내 봐야 한다.
 */
class PackMenu(
    custom: CustomItems,
    private val viewer: Player,
) : Menu(custom, SIZE, Text.renderFlat("<dark_gray>커스텀아이템 — 리소스팩</dark_gray>")) {

    override fun draw() {
        clear()
        fillEmpty(Icon.EDGE)

        set(SLOT_BUILD, buildIcon()) { build() }
        set(SLOT_STATUS, statusIcon())
        set(SLOT_SOURCES, sourcesIcon())
        set(SLOT_MISSING, missingIcon())
        set(SLOT_LEGACY, legacyIcon())
        set(SLOT_SEND, sendIcon()) { send() }
        set(SLOT_MODELS, Icon.of(Material.ITEM_FRAME, "<yellow>번호 모델 목록</yellow>", listOf(
            "<gray>리소스팩이 번호(custom_model_data)로 바꿔 그리는</gray>",
            "<gray>모델을 재질·번호·팩별로 봅니다. 겹친 번호도 보입니다.</gray>",
            "", "<yellow>▶ 클릭</yellow>",
        ))) { ModelNumberMenu.open(custom, viewer) }
        set(SLOT_IA_BLOCKS, Icon.of(Material.NOTE_BLOCK, "<gold>옛 ItemsAdder 블록 옮기기</gold>", listOf(
            "<gray>plugins/ItemsAdder/contents 의 블록 정의를</gray>",
            "<gray>커스텀아이템 블록으로 옮깁니다(꽉 찬 · 투명).</gray>",
            "<gray>상태는 팩(sources/)이 쓰던 그대로라 월드에 놓인</gray>",
            "<gray>옛 블록이 새 아이템으로 이어집니다.</gray>",
            "<dark_gray>같은 이름이 있으면 건너뜁니다 — 여러 번 눌러도 됩니다.</dark_gray>",
            "<dark_gray>단단함·도구 제한·소리·부술 때 명령어·광석 생성은 안 옮깁니다.</dark_gray>",
            "", "<yellow>▶ 클릭: 옮기기</yellow>",
        ))) { importItemsAdder() }

        set(SLOT_BACK, Icon.back()) { ItemTypeMenu(custom, viewer).open(viewer) }
        set(SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun buildIcon() = Icon.of(
        if (custom.pack.isBuilding) Material.CLOCK else Material.ANVIL,
        if (custom.pack.isBuilding) "<gray>만드는 중…</gray>" else "<green>리소스팩 만들기</green>",
        listOf(
            "<gray>아이템 에셋을 만들고 <white>sources/</white> 의 팩들을</gray>",
            "<gray>하나로 합쳐 <white>output/pack.zip</white> 을 냅니다.</gray>",
            "",
            "<gray>json 은 덮지 않고 <white>섞습니다</white> — 덮으면 두 팩 중</gray>",
            "<gray>하나가 조용히 사라집니다.</gray>",
            "",
            "<yellow>▶ 클릭: 만들기</yellow>",
        ),
    )

    private fun statusIcon(): org.bukkit.inventory.ItemStack {
        val last = custom.pack.lastReport
        return Icon.of(
            Material.PAPER,
            "<aqua>마지막 결과</aqua>",
            buildList {
                if (last == null) {
                    add("<dark_gray>아직 만든 적이 없습니다.</dark_gray>")
                    return@buildList
                }
                if (!last.ok) {
                    add("<red>실패: " + last.error + "</red>")
                    return@buildList
                }
                add("<gray>파일 <white>" + last.fileCount + "</white>개 · " + size(last.sizeBytes) + "</gray>")
                add("<gray>아이템 <white>" + last.itemCount + "</white>개</gray>")
                add("<gray>합친 팩 <white>" + last.sourceCount + "</white>개</gray>")
                add("<gray>섞인 json <white>" + last.mergedJsonCount + "</white>개</gray>")
                add("<gray>덮인 파일 <white>" + last.replacedCount + "</white>개</gray>")
                if (last.legacyCount > 0) {
                    add("<gray>번호 방식 <white>" + last.legacyCount + "</white>개</gray>")
                }
                if (last.blockCount > 0) add("<gray>블록 상태 <white>" + last.blockCount + "</white>개</gray>")
                if (last.blocksWithoutModel.isNotEmpty()) {
                    add("<red>모양 없는 블록 <white>" + last.blocksWithoutModel.size + "</white>개</red> <dark_gray>(모델·텍스처를 적으세요)</dark_gray>")
                    for (id in last.blocksWithoutModel.take(5)) add("<dark_gray>" + id + "</dark_gray>")
                }
                add("<gray>걸린 시간 <white>" + last.millis + "ms</white></gray>")
                add("")
                add("<dark_gray>sha1 " + last.sha1.take(16) + "…</dark_gray>")
            },
        )
    }

    private fun sourcesIcon(): org.bukkit.inventory.ItemStack {
        val sources = custom.pack.sourcesDir.listFiles()
            ?.filter { it.isDirectory || it.name.endsWith(".zip", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()

        return Icon.of(
            Material.CHEST,
            "<yellow>합칠 팩 <white>" + sources.size + "</white>개</yellow>",
            buildList {
                if (sources.isEmpty()) {
                    add("<dark_gray>pack/sources/ 가 비어 있습니다.</dark_gray>")
                } else {
                    // 읽는 순서 그대로. 아래쪽이 이긴다는 것을 눈으로 보게 한다.
                    for (source in sources.take(8)) {
                        add("<dark_gray>" + source.name + "</dark_gray>")
                    }
                    if (sources.size > 8) add("<dark_gray>…</dark_gray>")
                }
                add("")
                add("<gray>위에서 아래 순으로 읽고 <white>아래가 이깁니다.</white></gray>")
                add("<gray>10- 20- 처럼 번호를 붙이세요.</gray>")
            },
        )
    }

    /**
     * 빠진 텍스처.
     *
     * **이 화면에서 가장 중요한 칸이다.** 파일 이름 오타는 오류를 내지 않고 그 아이템만
     * 바닐라 모양으로 나오게 하는데, 게임에서 그걸 찾으려면 아이템을 하나씩 꺼내 봐야 한다.
     */
    private fun missingIcon(): org.bukkit.inventory.ItemStack {
        val missing = custom.pack.lastReport?.missingTextures.orEmpty()
        val pending = custom.items.all()
            .filter { PackAssets.needsPack(it) }
            .count { it.texture.isNotBlank() }

        return Icon.of(
            if (missing.isEmpty()) Material.LIME_DYE else Material.BARRIER,
            if (missing.isEmpty()) {
                "<green>빠진 텍스처 없음</green>"
            } else {
                "<red>빠진 텍스처 <white>" + missing.size + "</white>개</red>"
            },
            buildList {
                if (missing.isEmpty()) {
                    add("<gray>텍스처를 쓰는 아이템 <white>" + pending + "</white>개가</gray>")
                    add("<gray>전부 파일을 찾았습니다.</gray>")
                } else {
                    add("<gray>이 아이템들은 바닐라 모양으로 나옵니다:</gray>")
                    for (line in missing.take(8)) add("<dark_gray>" + line + "</dark_gray>")
                    if (missing.size > 8) add("<dark_gray>…</dark_gray>")
                    add("")
                    add("<gray>pack/textures/ 에 png 를 넣으세요.</gray>")
                }
            },
        )
    }

    /**
     * 번호 방식 상태.
     *
     * 번호를 적었는데 바닐라 모델을 몰라 건너뛴 것이 **가장 알려주기 어려운 실패**다 —
     * 번호는 아이템에 찍혀 있고 팩도 만들어졌는데 그 아이템만 모양이 안 바뀐다.
     */
    private fun legacyIcon(): org.bukkit.inventory.ItemStack {
        val last = custom.pack.lastReport
        val numbered = custom.items.all().count { it.customModelData > 0 }
        val skipped = last?.legacySkipped.orEmpty()

        return Icon.of(
            if (skipped.isEmpty()) Material.ITEM_FRAME else Material.BARRIER,
            if (skipped.isEmpty()) {
                "<yellow>번호 방식 <white>" + numbered + "</white>개</yellow>"
            } else {
                "<red>번호를 못 붙인 것 <white>" + skipped.size + "</white>개</red>"
            },
            buildList {
                if (numbered == 0) {
                    add("<dark_gray>번호를 적은 아이템이 없습니다.</dark_gray>")
                    add("")
                    add("<gray>번호는 <white>낡은 팩과 섞을 때만</white> 씁니다.</gray>")
                    add("<gray>그 밖에는 텍스처만으로 충분합니다 —</gray>")
                    add("<gray>번호 없이 아이템 id 로 모델을 찾습니다.</gray>")
                    return@buildList
                }
                if (skipped.isEmpty()) {
                    add("<gray>번호를 적은 아이템이 전부 나갑니다.</gray>")
                    add("<gray>텍스처 방식과 <white>둘 다</white> 나갑니다.</gray>")
                } else {
                    add("<gray>이 아이템들은 번호가 무시됩니다:</gray>")
                    for (line in skipped.take(8)) add("<dark_gray>" + line + "</dark_gray>")
                    if (skipped.size > 8) add("<dark_gray>…</dark_gray>")
                    add("")
                    add("<gray>그 재질의 바닐라 모델을 모릅니다. 짐작해서 쓰면</gray>")
                    add("<gray>우리 아이템이 아니라 <white>평범한 그 아이템</white>이 망가집니다.</gray>")
                    add("")
                    add("<gray>pack/models/base/ 에 바닐라 모델을 넣거나,</gray>")
                    add("<gray>sources/ 의 팩이 그 파일을 주면 됩니다.</gray>")
                }
            },
        )
    }

    private fun sendIcon(): org.bukkit.inventory.ItemStack {
        val config = custom.packConfig
        return Icon.of(
            if (config.canSend) Material.ENDER_CHEST else Material.GRAY_DYE,
            "<yellow>나에게 보내기</yellow>",
            buildList {
                if (!config.canSend) {
                    add("<red>config.yml 의 resource-pack.url 이 비어 있습니다.</red>")
                    add("")
                    add("<gray>이 플러그인은 팩을 <white>만들기만</white> 합니다.</gray>")
                    add("<gray>올리는 것은 관리자 몫입니다 — 포트를 열고</gray>")
                    add("<gray>대역폭을 쓰는 일은 아이템 플러그인이</gray>")
                    add("<gray>조용히 해도 되는 일이 아닙니다.</gray>")
                    return@buildList
                }
                add("<gray>" + config.url + "</gray>")
                add("<gray>자동 보내기: " + Icon.toggle(config.autoSend) + "</gray>")
                add("<gray>필수: " + Icon.toggle(config.required) + "</gray>")
                if (custom.pack.sha1.isBlank()) {
                    add("")
                    add("<yellow>sha1 이 없습니다 — 먼저 만드세요.</yellow>")
                    add("<gray>없으면 접속할 때마다 다시 받습니다.</gray>")
                }
                add("")
                add("<yellow>▶ 클릭: 지금 받아보기</yellow>")
            },
        )
    }

    private fun size(bytes: Long): String = when {
        bytes >= 1 shl 20 -> String.format("%.1fMB", bytes / 1048576.0)
        bytes >= 1 shl 10 -> String.format("%.1fKB", bytes / 1024.0)
        else -> bytes.toString() + "B"
    }

    private fun build() {
        if (custom.pack.isBuilding) return
        viewer.sendMessage(Text.render("<gray>리소스팩을 만드는 중…</gray>"))
        refresh()

        custom.pack.build { result ->
            if (result.ok) {
                viewer.playSound(viewer.location, Sound.BLOCK_ANVIL_USE, 1f, 1.4f)
                viewer.sendMessage(
                    Text.render(
                        "<green>리소스팩을 만들었습니다 — 파일 " + result.fileCount +
                            "개 · " + size(result.sizeBytes) + " · " + result.millis + "ms</green>",
                    ),
                )
                if (result.missingTextures.isNotEmpty()) {
                    viewer.sendMessage(
                        Text.render("<yellow>빠진 텍스처 " + result.missingTextures.size + "개 — 화면에서 확인하세요.</yellow>"),
                    )
                }
            } else {
                viewer.playSound(viewer.location, Sound.ENTITY_ITEM_BREAK, 1f, 0.8f)
                viewer.sendMessage(Text.render("<red>실패: " + result.error + "</red>"))
            }
            // 화면이 아직 열려 있으면 갱신한다. 닫았으면 아무 일도 안 일어난다.
            refresh()
        }
    }

    private fun importItemsAdder() {
        viewer.sendMessage(Text.render("<gray>옛 ItemsAdder 블록을 읽는 중…</gray>"))
        custom.blocks.importItemsAdder { report ->
            if (!report.contentsFound) {
                viewer.sendMessage(Text.render("<red>plugins/ItemsAdder/contents 폴더가 없습니다.</red>"))
                return@importItemsAdder
            }
            viewer.sendMessage(Text.render("<green>블록 <white>" + report.added.size + "</white>개를 옮겼습니다.</green> <gray>리소스팩을 다시 만드세요.</gray>"))
            if (report.skipped.isNotEmpty()) viewer.sendMessage(Text.render("<gray>건너뜀 " + report.skipped.size + "개: <white>" + report.skipped.take(6).joinToString(", ") + (if (report.skipped.size > 6) " …" else "") + "</white></gray>"))
            if (report.noState.isNotEmpty()) viewer.sendMessage(Text.render("<yellow>팩에서 상태를 못 찾음 " + report.noState.size + "개: <white>" + report.noState.take(6).joinToString(", ") + "</white></yellow>"))
            if (report.unsupported.isNotEmpty()) viewer.sendMessage(Text.render("<yellow>이 방식은 없음 " + report.unsupported.size + "개: <white>" + report.unsupported.take(6).joinToString(", ") + "</white></yellow>"))
            refresh()
        }
    }

    private fun send() {
        val config = custom.packConfig
        if (!config.canSend) return
        viewer.closeInventory()
        runCatching {
            // sha1 을 같이 넘긴다. 안 넘기면 이미 받은 팩이어도 다시 받는다.
            viewer.setResourcePack(
                config.url,
                custom.pack.sha1,
                false,
                Text.render(config.prompt),
            )
        }.onFailure {
            viewer.sendMessage(Text.render("<red>보내지 못했습니다: " + it.message + "</red>"))
        }
    }

    companion object {
        const val SIZE = 27
        const val SLOT_BUILD = 10
        const val SLOT_STATUS = 12
        const val SLOT_SOURCES = 14
        const val SLOT_MISSING = 16
        const val SLOT_LEGACY = 20
        const val SLOT_SEND = 22
        const val SLOT_MODELS = 24
        const val SLOT_IA_BLOCKS = 4

        /**
         * 뒤로·닫기.
         *
         * **공용 페이지 슬롯(45·53번)을 쓰지 않는다.** 이 화면은 27칸이라 그 자리가 없고,
         * [kr.inmc.core.gui.Menu.set] 은 범위 밖 슬롯을 **조용히 무시한다** —
         * 버튼이 그냥 안 그려진다. 낚시의 손질대에서 실제로 그랬다.
         */
        const val SLOT_BACK = 18
        const val SLOT_CLOSE = 26
    }
}
