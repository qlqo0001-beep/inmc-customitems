package com.inmc.customitems.pack

import org.bukkit.configuration.file.YamlConfiguration

/**
 * 리소스팩 배포 설정. `config.yml` 의 `resource-pack` 절에서 온다.
 *
 * 팩을 내려주는 것은 **관리자가 켤 때만**([host] — 기본 꺼짐) 이 플러그인이 한다([PackHost]). 포트를 열고 대역폭을 쓰는 일이라
 * 조용히 켜지 않는다(사용자 결정 2026-10-02: 세션마다 띄우던 임시 파일 서버가 꺼져 다운로드가 실패해 넣었다).
 * 플레이어에게 보내는 주소는 언제나 [url] 하나다 — 내려주기를 켜도 바깥에서 닿는 주소(도메인·공인 IP)는 관리자가 적는다.
 */
data class PackConfig(
    /** 만들어진 팩을 올려둔 주소. 비우면 아무에게도 안 보낸다. */
    val url: String = "",
    /** 접속할 때 자동으로 보낼지. */
    val autoSend: Boolean = false,
    /** 거부하면 서버에서 내보낼지. */
    val required: Boolean = false,
    val prompt: String = "<yellow>서버 리소스팩을 받아주세요.</yellow>",
    /** `pack.mcmeta` 에 적을 설명. */
    val description: String = "INMC 커스텀 아이템",
    /** 팩을 직접 내려줄지(`resource-pack.host`). */
    val host: PackHost.Settings = PackHost.Settings(),
) {

    /** 보낼 수 있는 상태인지. 주소가 없으면 보낼 곳이 없다. */
    val canSend: Boolean get() = url.isNotBlank()

    companion object {

        fun from(config: YamlConfiguration): PackConfig {
            val section = config.getConfigurationSection("resource-pack") ?: return PackConfig()
            return PackConfig(
                url = section.getString("url").orEmpty().trim(),
                autoSend = section.getBoolean("auto-send", false),
                required = section.getBoolean("required", false),
                prompt = section.getString("prompt").orEmpty()
                    .ifBlank { "<yellow>서버 리소스팩을 받아주세요.</yellow>" },
                description = section.getString("description").orEmpty()
                    .ifBlank { "INMC 커스텀 아이템" },
                host = PackHost.Settings(
                    enabled = section.getBoolean("host.enabled", false),
                    bind = section.getString("host.bind").orEmpty().trim().ifBlank { "0.0.0.0" },
                    port = section.getInt("host.port", 8765).coerceIn(0, 65535),
                ),
            )
        }
    }
}
