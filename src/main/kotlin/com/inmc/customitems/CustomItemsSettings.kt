package com.inmc.customitems

import kr.inmc.core.integration.PlayerSettings
import org.bukkit.Material

/**
 * 커스텀아이템이 core 개인 설정 창구([PlayerSettings])에 올리는 것 — 플레이어 메뉴의 개인 설정 화면에 보인다(2026-10-02).
 * 정의가 없을 때(core 가 옛 판) 기본은 켜짐이라 지금과 같다.
 */
internal object CustomItemsSettings {

    const val OWNER = "커스텀아이템"

    /** 주운 것을 배낭이 자동으로 담기(`EquipmentListener.onAttemptPickup`). */
    const val AUTO_PICKUP = "customitems.auto-pickup"

    fun register() {
        PlayerSettings.register(
            PlayerSettings.Setting(
                AUTO_PICKUP, OWNER, "배낭 드랍 자동 수납", Material.BUNDLE,
                listOf("자동 수납이 붙은 배낭이 주운 것을 바로 담습니다.", "끄면 주운 것은 가방으로 — 배낭에 넣고 싶을 때만 직접."),
                PlayerSettings.Toggle(true),
            ),
        )
    }

    fun unregister() = PlayerSettings.unregisterAll(OWNER)
}
