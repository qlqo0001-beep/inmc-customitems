package com.inmc.customitems.config

import com.inmc.customitems.util.Ph
import kr.inmc.core.config.MessageCatalog
import org.bukkit.configuration.file.YamlConfiguration

/**
 * `messages.yml` 한 벌.
 *
 * 읽고 보내는 부분은 전부 core 의 [MessageCatalog] 가 갖고 있다. 남는 것은 기본값 표뿐이고
 * 그게 이 플러그인의 도메인이다. `ResourceTest` 가 배포 파일과 이 표의 키가 정확히 일치하는지
 * 지킨다 — 한쪽에만 있는 키는 관리자가 고쳐도 안 나오거나, 코드가 찾는데 파일에 없는 키다.
 */
class Messages(values: Map<String, String>) : MessageCatalog<Ph>(values, DEFAULTS) {

    companion object {

        fun from(config: YamlConfiguration): Messages = Messages(merge(DEFAULTS, config))

        val DEFAULTS: Map<String, String> = mapOf(
            PREFIX to "<gradient:#ffd76a:#ffb347>[ 아이템 ]</gradient> ",

            // --- 등록 ---------------------------------------------------------------
            "registered" to "<green>'{item}' 로 등록했습니다.</green>",
            "unregistered" to "<green>'{item}' 등록을 해제했습니다.</green>",
            "already-exists" to "<red>'{item}' 은(는) 이미 있습니다.</red>",
            "invalid-name" to "<red>이름 규칙에 맞지 않습니다: {item}</red>",
            "unknown-item" to "<red>'{item}' 라는 아이템이 없습니다.</red>",
            "unknown-set" to "<red>'{item}' 라는 세트가 없습니다.</red>",
            "hand-empty" to "<red>손에 아이템을 들고 있어야 합니다.</red>",

            // --- 지급 ---------------------------------------------------------------
            "given" to "<green>{player} 에게 {item} {amount}개를 지급했습니다.</green>",
            "received" to "<green>{item} {amount}개를 받았습니다.</green>",
            "player-not-found" to "<red>'{player}' 을(를) 찾을 수 없습니다.</red>",
            "inventory-full" to "<yellow>인벤토리가 가득 차 바닥에 떨어뜨렸습니다.</yellow>",

            // --- 갱신 ---------------------------------------------------------------
            "refreshed" to "<green>손에 든 아이템을 최신 정의로 다시 만들었습니다.</green>",
            "not-ours" to "<red>이 플러그인이 만든 아이템이 아닙니다.</red>",
            "definition-gone" to "<red>'{item}' 정의가 더 이상 없습니다.</red>",

            // --- 보석 ---------------------------------------------------------------
            "gem-socketed" to "<aqua>{item}<aqua> 을(를) 박았습니다.</aqua>",
            "gem-failed" to "<red>{item}<red> 이(가) 깨졌습니다.</red>",
            "gem-no-socket" to "<red>{item}<red> 이(가) 맞는 빈 소켓이 없습니다.</red>",

            // --- 소모품·요구 조건 -----------------------------------------------------
            "consume-cooldown" to "<red>{item}<red> 은(는) {amount}초 뒤에 다시 쓸 수 있습니다.</red>",
            "require-level" to "<red>{item}<red> 은(는) 레벨 {amount} 이상이어야 씁니다.</red>",
            "require-permission" to "<red>{item}<red> 을(를) 쓸 권한이 없습니다.</red>",
            "gem-removed" to "<aqua>{item}<aqua> 을(를) 빼냈습니다.</aqua>",
            "gem-nothing" to "<red>빼낼 보석이 없습니다.</red>",
            "repaired" to "<green>{item}<green> 을(를) {amount} 만큼 고쳤습니다.</green>",
            "repair-nothing" to "<red>고칠 것이 없습니다.</red>",
            "identified" to "<gold>정체가 밝혀졌습니다 — {item}</gold>",
            "deconstructed" to "<yellow>{item}<yellow> 을(를) 분해했습니다.</yellow>",

            // --- 장착 칸 -------------------------------------------------------------
            "equip-locked" to "<red>잠긴 칸입니다 — 권한이 있어야 열립니다.</red>",
            "equip-wrong-type" to "<red>이 줄에는 {value} 만 넣을 수 있습니다.</red>",
            "equip-full" to "<red>{value} 칸이 가득 찼습니다.</red>",
            "equip-bag-full" to "<red>가방이 가득 찼습니다.</red>",

            // --- 강화·진화 -----------------------------------------------------------
            "upgrade-success" to "<gold>강화 성공! {item}<gold> <white>+{amount}</white></gold>",
            "upgrade-fail-keep" to "<gray>강화에 실패했습니다. {item}<gray> 은(는) 그대로입니다.</gray>",
            "upgrade-fail-down" to "<red>강화에 실패해 {item}<red> 이(가) <white>+{amount}</white> 로 내려갔습니다.</red>",
            "upgrade-fail-reset" to "<red>강화에 실패해 {item}<red> 이(가) <white>+0</white> 으로 돌아갔습니다.</red>",
            "upgrade-destroyed" to "<dark_red>강화에 실패해 {item}<dark_red> 이(가) 부서졌습니다.</dark_red>",
            "upgrade-max" to "<yellow>{item}<yellow> 은(는) 이미 최대 강화입니다.</yellow>",
            "upgrade-unidentified" to "<red>감정하지 않은 아이템은 강화할 수 없습니다.</red>",
            "upgrade-wrong-stone" to "<red>이 강화석으로는 {item}<red> 을(를) 강화할 수 없습니다.</red>",
            "upgrade-wrong-level" to "<red>이 강화석은 {item}<red> 의 지금 단계에 쓸 수 없습니다.</red>",
            "evolved" to "<light_purple>진화했습니다 — {item}</light_purple>",
            "evolve-not-ready" to "<red>{item}<red> 은(는) 최대 강화해야 진화합니다.</red>",
            "evolve-wrong-stone" to "<red>이 진화석으로는 {item}<red> 을(를) 진화시킬 수 없습니다.</red>",
            "evolve-missing" to "<red>진화 재료가 모자랍니다.</red>",

            // --- 검증 ---------------------------------------------------------------
            "verify-done" to "<gray>검증 끝 ({value}) — <green>통과 {amount}</green> · <red>실패 {count}</red></gray>",
            "verify-failure" to "<red>✘ {item}</red> <gray>{value}</gray>",
            "verify-report" to "<gray>보고서: <white>{value}</white></gray>",

            // --- 제작 ---------------------------------------------------------------
            "craft-crafted" to "<green>만들었습니다.</green>",
            "craft-queued" to "<yellow>대기열에 넣었습니다. 다 되면 제작대에서 받으세요.</yellow>",
            "craft-claimed" to "<green>받았습니다.</green>",
            "craft-low-level" to "<red>레벨 {amount} 이상이어야 만들 수 있습니다.</red>",
            "craft-no-permission" to "<red>이 조합법을 쓸 권한이 없습니다.</red>",
            "craft-missing" to "<red>재료가 모자랍니다.</red>",
            "craft-queue-full" to "<red>대기열이 가득 찼습니다. 다 된 것을 먼저 받으세요.</red>",
            "craft-empty" to "<red>결과가 정해지지 않은 조합법입니다.</red>",
            "craft-unknown" to "<red>'{item}' 제작대가 없습니다.</red>",
            "craft-usage" to "<gray>/제작 <제작대> — 있는 것: <white>{item}</white></gray>",

            // --- 공용 ---------------------------------------------------------------
            "no-permission" to "<red>권한이 없습니다.</red>",
            "player-only" to "<red>이 명령어는 플레이어만 사용할 수 있습니다.</red>",
            "not-ready" to "<gray>플러그인이 아직 준비 중입니다. 잠시 후 다시 시도해주세요.</gray>",
            "reloaded" to "<green>아이템 {count}개를 다시 불러왔습니다.</green>",

            // --- core ChatPrompt 가 요구하는 넷 -----------------------------------------
            "prompt-enter" to "<yellow>채팅으로 값을 입력하세요. <gray>(취소: 취소)</gray></yellow>",
            "prompt-cancelled" to "<gray>입력을 취소했습니다.</gray>",
            "prompt-timeout" to "<gray>입력 시간이 지났습니다.</gray>",
            "prompt-invalid-number" to "<red>숫자를 입력해주세요.</red>",
        )
    }
}
