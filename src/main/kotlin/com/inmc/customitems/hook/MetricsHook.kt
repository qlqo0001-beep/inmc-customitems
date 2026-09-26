package com.inmc.customitems.hook

import com.inmc.customitems.CustomItems
import org.bstats.bukkit.Metrics
import org.bstats.charts.SimplePie

/**
 * bStats 집계.
 *
 * **개인 식별 값은 보내지 않는다.** 아이템 이름도 보내지 않는다 — 서버가 만든 아이템 이름은
 * 그 서버의 콘텐츠라 우리가 가져갈 것이 아니다. 개수만 구간으로 뭉쳐 센다.
 *
 * 차트 콜백은 **비메인 스레드에서 돈다.** Bukkit API 를 건드리면 안 된다.
 */
class MetricsHook(private val custom: CustomItems) {

    private var metrics: Metrics? = null

    fun start() {
        val instance = Metrics(custom.plugin, PLUGIN_ID)

        instance.addCustomChart(SimplePie("item_count") { bucket(custom.items.size) })
        instance.addCustomChart(
            SimplePie("items_with_data") { bucket(custom.items.all().count { it.data.isNotEmpty() }) },
        )
        instance.addCustomChart(
            SimplePie("items_with_stats") {
                bucket(custom.items.all().count { it.stats.isNotEmpty() })
            },
        )
        instance.addCustomChart(
            SimplePie("uses_model_data") {
                if (custom.items.all().any { it.customModelData > 0 }) "yes" else "no"
            },
        )
        // 다른 커스텀아이템 플러그인과 같이 쓰는 서버가 얼마나 되는지.
        instance.addCustomChart(SimplePie("with_third_party") { onOff(custom.customItems.isEnabled) })

        metrics = instance
    }

    fun stop() {
        metrics?.shutdown()
        metrics = null
    }

    private fun onOff(value: Boolean): String = if (value) "on" else "off"

    private fun bucket(count: Int): String = when {
        count == 0 -> "0"
        count <= 5 -> "1-5"
        count <= 20 -> "6-20"
        count <= 50 -> "21-50"
        count <= 200 -> "51-200"
        else -> "201+"
    }

    private companion object {
        /** ⚠ 임시 번호다. **배포 전에 bStats 에 등록하고 바꿔야** 통계가 남의 것과 섞이지 않는다. */
        const val PLUGIN_ID = 0
    }
}
