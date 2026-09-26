package com.inmc.customitems.scheduler

import com.inmc.customitems.CustomItems
import kr.inmc.core.scheduler.TickerBase
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * [com.inmc.customitems.ability.Trigger.PASSIVE] 기능을 돌린다. 1초에 한 번.
 *
 * 기능마다 자기 주기를 갖고 있고([com.inmc.customitems.ability.Ability.intervalSeconds]),
 * 여기서는 **그 주기가 됐는지만** 본다 — 쿨다운 표를 그대로 쓰므로 따로 시각을 들고 다닐
 * 필요가 없다.
 *
 * **지속 기능을 가진 아이템이 하나도 없으면 접속자 목록조차 훑지 않는다.** 대부분의 서버가
 * 그 상태일 것이고, 매초 도는 것에 공짜인 경로를 두는 편이 낫다.
 */
class PassiveTicker(private val custom: CustomItems) : TickerBase(custom.plugin) {

    override val periodTicks = 20L

    override fun ready(): Boolean = custom.ready

    override fun tick(now: Long) {
        if (!custom.items.hasPassive) return

        for (player in Bukkit.getOnlinePlayers()) {
            step("passive") { run(player, now) }
        }
    }

    private fun run(player: Player, now: Long) {
        val inventory = player.inventory
        // 착용 중인 것과 양손. 가방 안에 넣어둔 것으로는 안 돈다 — 그러면 장비를 바꿀 이유가 없다.
        val held = listOf(inventory.itemInMainHand, inventory.itemInOffHand) + inventory.armorContents.filterNotNull()
        for (stack in held) {
            val item = custom.items.usable(stack) ?: continue
            // 부적·유물은 아래에서 — 손에 든 것도 가방째 센다. 장착 칸에서만 효과가 나는 장신구는 손에서 안 돈다.
            if (item.type.carried || !custom.equipmentSettings.worksOutside(item)) continue
            // 요구 조건이 모자란 것은 조용히 건너뛴다 — 매초 알리면 채팅이 찬다.
            if (custom.requirements.meets(player, item)) custom.abilities.firePassive(player, item, now)
        }
        // 장착 칸과 가방의 부적·유물·장신구. 거기서 효과를 내는 것이 그 종류의 뜻이다. 요구 조건은 이미 걸렀다.
        for (item in custom.stats.of(player).equipped) if (item.slot == null) custom.abilities.firePassive(player, item.definition, now)
    }
}
