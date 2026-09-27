package com.inmc.customitems.hook

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.Tier
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * 다른 플러그인이 역할을 붙인 아이템에 입히는 **기본 겉모습**(`role-appearance.yml` + `role-appearance/` 의 png).
 *
 * 낚시의 물고기·생선살·미끼·낚싯대는 낚시가 제 파일에서 옮겨 와 우리 아이템이 되는데(core `ItemRoles.adopt`), 그때는 바닐라 모양이다.
 * 여기 적힌 그림을 `pack/textures/` 에 깔고, 역할 값이 맞는 아이템에 텍스처·등급·반짝임을 붙인다 — 관리자가 아이템마다 겉모습 칸을
 * 채울 필요가 없다(사용자 결정 2026-09-27).
 *
 * - **겉모습이 없는 아이템에만** 입힌다(텍스처·모델·item_model·모델 번호가 전부 비었을 때). 관리자가 한 번 고친 것은 다시 안 덮는다.
 * - 이름은 글자만 남기고 색을 뗀다 — 옮겨 온 이름의 색(`&3생대구`)이 등급 색을 가리지 않게.
 * - 역할 값의 **뜻은 모른다**(규칙 3). 적힌 값이 같은지만 본다.
 * - png 는 없을 때만 깐다 — 관리자가 같은 이름으로 바꿔 넣은 그림을 덮지 않는다.
 */
class RoleAppearance(private val custom: CustomItems) {

    val entries: List<Entry> by lazy {
        custom.io.readResource(RESOURCE)?.let { load(it) }.orEmpty()
    }

    /** png 를 `pack/textures/` 에 깐다(없는 것만). 낚싯대는 던진 모양(`_cast`)도 있으면. */
    fun installTextures() {
        val dir = custom.pack.texturesDir
        for (texture in entries.map { it.texture }.toSet()) {
            for (name in listOf(texture, com.inmc.customitems.pack.PackAssets.castTexture(texture))) {
                val target = File(dir, name)
                if (target.exists() || custom.plugin.getResource("$FOLDER/$name")?.use { true } != true) continue
                custom.io.copyDefault("$FOLDER/$name", target)
            }
        }
    }

    /** 한 번만 알린다 — 팩을 다시 만들어야 그림이 보인다. */
    private var announced = false

    /** 이 아이템에 입힐 겉모습. 입힐 것이 없으면 null. */
    fun decorate(item: CustomItem): CustomItem? = decorate(item, entries)?.also { announce() }

    private fun announce() {
        if (announced) return
        announced = true
        custom.logger.info("역할 아이템(낚시의 물고기·생선살·미끼·낚싯대 …)에 기본 겉모습을 입혔습니다 — /커스텀아이템 리팩 빌드 로 팩을 다시 만들어 올려야 보입니다")
    }

    /**
     * 이미 있는 아이템 전부에 입힌다. 역할 창구를 꽂은 뒤에 부른다 — 그 순간 다른 플러그인이 옮겨 오는 것은
     * [RoleStore.assign] 이 입히고, 여기는 **예전에 옮겨 온 것**을 맡는다.
     *
     * @return 입힌 개수
     */
    fun sweep(): Int {
        var count = 0
        for (item in custom.items.all()) {
            val decorated = decorate(item) ?: continue
            custom.items.put(decorated)
            count++
        }
        return count
    }

    /**
     * @param role 역할 열쇠(`fishing.fish`)
     * @param match 역할 값 중 이것이 전부 같아야 한다(`fish: cod`)
     * @param texture `pack/textures/` 아래 경로
     */
    data class Entry(val id: String, val role: String, val match: Map<String, String>, val texture: String, val tier: Tier, val glow: Boolean) {
        fun matches(item: CustomItem): Boolean {
            val values = item.roles[role] ?: return false
            return match.all { (key, value) -> values[key]?.trim().equals(value, ignoreCase = true) }
        }
    }

    companion object {
        const val RESOURCE = "role-appearance.yml"
        const val FOLDER = "role-appearance"

        fun load(yaml: YamlConfiguration): List<Entry> {
            val root = yaml.getConfigurationSection("entries") ?: return emptyList()
            return root.getKeys(false).mapNotNull { key -> root.getConfigurationSection(key)?.let { entry(key, it) } }
        }

        private fun entry(id: String, section: ConfigurationSection): Entry? {
            val role = section.getString("role")?.trim().orEmpty()
            val texture = section.getString("texture")?.trim().orEmpty()
            val match = section.getConfigurationSection("match")?.let { m -> m.getKeys(false).associateWith { m.getString(it).orEmpty() } }.orEmpty()
            if (role.isBlank() || texture.isBlank() || match.isEmpty()) return null
            return Entry(id, role, match, texture, Tier.of(section.getString("tier")), section.getBoolean("glow"))
        }

        /** 겉모습이 하나라도 있는가 — 있으면 관리자(또는 옮겨 온 원본)의 것이라 건드리지 않는다. */
        fun hasAppearance(item: CustomItem): Boolean =
            item.texture.isNotBlank() || item.model.isNotBlank() || item.itemModel.isNotBlank() || item.customModelData > 0

        fun decorate(item: CustomItem, entries: List<Entry>): CustomItem? {
            if (item.roles.isEmpty() || hasAppearance(item)) return null
            val entry = entries.firstOrNull { it.matches(item) } ?: return null
            val name = kr.inmc.core.util.Text.plain(item.displayName).trim()
            return item.copy(
                texture = entry.texture,
                tier = entry.tier,
                glow = item.glow || entry.glow,
                displayName = if (name.isBlank()) item.displayName else name,
            )
        }
    }
}
