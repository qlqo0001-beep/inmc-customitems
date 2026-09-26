package com.inmc.customitems.listener

import com.inmc.customitems.CustomItems
import kr.inmc.core.util.Text
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

/**
 * 접속할 때 리소스팩을 보낸다.
 *
 * **주소가 있고 자동 보내기가 켜져 있을 때만** 보낸다. 팩을 올리는 것은 관리자 몫이라
 * 주소를 모르는 동안에는 아무 일도 하지 않는 것이 맞다.
 *
 * sha1 을 같이 넘기는 것이 중요하다 — 클라이언트가 **이미 받은 팩인지 판단하는 근거**라,
 * 안 넘기면 접속할 때마다 몇 MB 를 다시 받는다. 느릴 뿐 고장은 아니라서 아무도 신고하지
 * 않고 계속 그런다.
 */
class ResourcePackListener(private val custom: CustomItems) : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        val config = custom.packConfig
        if (!config.autoSend || !config.canSend) return

        runCatching {
            event.player.setResourcePack(
                config.url,
                custom.pack.sha1,
                config.required,
                Text.render(config.prompt),
            )
        }.onFailure {
            custom.logger.warning("리소스팩을 보내지 못했습니다: " + it.message)
        }
    }
}
