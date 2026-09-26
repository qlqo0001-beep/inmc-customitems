package com.inmc.customitems.pack

import org.bukkit.configuration.file.YamlConfiguration

/**
 * 리소스팩 배포 설정. `config.yml` 의 `resource-pack` 절에서 온다.
 *
 * **이 플러그인은 팩을 호스팅하지 않는다.** 만들어 주기만 하고, 올리는 것은 관리자 몫이다.
 * 서버 안에 HTTP 서버를 띄우는 방법도 있지만 그건 포트를 열고 방화벽을 손대고 대역폭을
 * 쓰는 일이라, 아이템 플러그인이 조용히 해도 되는 일이 아니다.
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
            )
        }
    }
}
