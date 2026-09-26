package com.inmc.customitems.listener

import com.inmc.customitems.CustomItems
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/**
 * 관리 화면의 채팅 입력을 core 의 [kr.inmc.core.input.ChatPrompt] 로 넘긴다.
 *
 * 이게 없으면 화면이 이름이나 값을 물어본 뒤 **영영 기다린다.** 오류는 나지 않고 입력만
 * 채팅에 그대로 찍힌다.
 */
class ChatInputListener(private val custom: CustomItems) : Listener {

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) {
        val player = event.player
        if (!custom.prompts.isWaiting(player.uniqueId)) return
        val text = PlainTextComponentSerializer.plainText().serialize(event.message())
        if (custom.prompts.submit(player, text)) event.isCancelled = true
    }
}
