package com.inmc.customitems.util

import kr.inmc.core.util.TokenBag
import org.bukkit.entity.Player

/**
 * 메시지 한 번 렌더링에 쓰이는 토큰 주머니.
 *
 * 한글/영문 둘 다 받는다 — 다른 INMC 플러그인들과 같은 관례다.
 */
class Ph : TokenBag<Ph>() {

    override val aliases: Map<String, List<String>> get() = ALIASES

    fun player(name: String): Ph = put(PLAYER, name)

    fun player(player: Player): Ph = put(PLAYER, player.name)

    fun item(name: String): Ph = put(ITEM, name)

    fun amount(value: Int): Ph = put(AMOUNT, value.toString())

    fun count(value: Int): Ph = put(COUNT, value.toString())

    fun value(text: String): Ph = put(VALUE, text)

    fun copy(): Ph = copyValuesInto(Ph())

    companion object {

        fun of(): Ph = Ph()

        const val PLAYER = "player"
        const val ITEM = "item"
        const val AMOUNT = "amount"
        const val COUNT = "count"
        const val VALUE = "value"

        private val ALIASES: Map<String, List<String>> = mapOf(
            PLAYER to listOf("{플레이어}", "{player}"),
            ITEM to listOf("{아이템}", "{item}"),
            AMOUNT to listOf("{수량}", "{amount}"),
            COUNT to listOf("{개수}", "{count}"),
            VALUE to listOf("{값}", "{value}"),
        )
    }
}
