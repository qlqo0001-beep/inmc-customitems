package com.inmc.customitems.scheduler

import com.inmc.customitems.CustomItems
import kr.inmc.core.scheduler.TickerBase

/**
 * 이 플러그인의 반복 작업, 1Hz. 프롬프트 만료·디스크 플러시·능력치 합 버리기·체력 회복.
 *
 * 지속 기능은 따로 돈다([PassiveTicker]) — 그쪽은 지속 기능을 가진 아이템이 없으면 접속자조차 안 훑는다.
 */
class Ticker(private val custom: CustomItems) : TickerBase(custom.plugin) {

    override val periodTicks = 20L

    override fun ready(): Boolean = custom.ready

    override fun tick(now: Long) {
        step("prompts") { custom.prompts.tick(now) }
        step("flush") {
            custom.items.flush()
            custom.sets.flush()
            custom.categories.flush()
            custom.upgrades.flush()
            custom.equipmentSettings.flush()
            custom.types.flush()
            custom.stations.flush()
            custom.recipes.flush()
        }
        // 사건을 놓쳤어도 1초 안에 맞게. 다시 계산은 누가 물을 때만 한다.
        step("stats") {
            custom.stats.clear()
            for (player in org.bukkit.Bukkit.getOnlinePlayers()) custom.stats.sync(player)
        }
        step("regen") { regen() }
    }

    /** 초당 체력 회복. 죽었거나 가득 찬 사람은 건너뛴다. */
    private fun regen() {
        for (player in org.bukkit.Bukkit.getOnlinePlayers()) {
            if (player.isDead) continue
            val amount = custom.stats.of(player).stat(com.inmc.customitems.item.Stat.HEALTH_REGEN)
            if (amount <= 0.0) continue
            val max = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0
            if (player.health < max) player.health = (player.health + amount).coerceAtMost(max)
        }
    }
}
