package com.inmc.customitems.item

import com.inmc.customitems.CustomItems
import kr.inmc.core.store.YamlFileStore
import org.bukkit.configuration.file.YamlConfiguration

/** `upgrades.yml` — 공용 강화 방식(무기용·방어구용…). 한 아이템 전용 표는 그 아이템 정의 안에 있다. */
class UpgradeRegistry(private val custom: CustomItems) : YamlFileStore(
    io = custom.io,
    path = listOf("upgrades.yml"),
    header = """
        공용 강화 방식. /커스텀아이템 관리 → 강화 방식 에서 GUI 로 고치는 것을 권장합니다.
        아이템 설정 화면의 "강화" 에서 이 방식을 고르거나, 그 아이템만의 표를 따로 만듭니다.

        mode: add(증가량 — 단계마다 더하는 값과 기본의 %) · absolute(단계별 능력치 전체)
        steps 의 숫자는 강화 단계(1 = +1 로 가는 단계)입니다.
          chance: 성공 확률(%)   fail: keep · down · reset · destroy
          stats: { 능력치 id: 값 }   percent: 기본의 %(add 만)   tier: 이 단계부터 등급
          custom-model-data · texture · model: 이 단계부터 모양
    """.trimIndent() + "\n",
    what = "강화 방식",
) {

    private val tables = LinkedHashMap<String, UpgradeTable>()

    fun all(): List<UpgradeTable> = tables.values.toList()

    fun get(id: String?): UpgradeTable? = id?.takeIf { it.isNotBlank() }?.let { tables[it.lowercase()] }

    fun put(table: UpgradeTable) {
        tables[table.id] = table
        markDirty()
        custom.items.onUpgradeChanged(table.id)
    }

    fun remove(id: String): Boolean = (tables.remove(id.lowercase()) != null).also {
        if (it) {
            markDirty()
            custom.items.onUpgradeChanged(id.lowercase())
        }
    }

    override fun read(config: YamlConfiguration) {
        tables.clear()
        for (key in config.getKeys(false)) {
            config.getConfigurationSection(key)?.let { tables[key.lowercase()] = UpgradeTable.load(key.lowercase(), it) }
        }
    }

    override fun write(config: YamlConfiguration) {
        for (table in tables.values) table.save(config.createSection(table.id))
    }
}
