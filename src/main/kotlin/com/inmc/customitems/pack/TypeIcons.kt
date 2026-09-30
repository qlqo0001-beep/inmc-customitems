package com.inmc.customitems.pack

import com.inmc.customitems.item.ItemType
import org.bukkit.NamespacedKey

/**
 * 관리 화면의 종류 아이콘 — 기본 종류마다 우리가 그린 16×16 그림(플러그인 안 `pack/gui/type/<id>.png`, 사용자 요청 2026-10-01 —
 * 종류마다 맞는 아이콘을 만들어 자동으로). 팩을 만들 때 `inmc:gui/type/<id>` 로 넣고, 관리자가 아이콘을 따로 정하지 않은 종류 서랍이
 * 이 모양으로 보인다. 관리자가 `sources/` 의 팩으로 같은 경로를 덮으면 그쪽이 이긴다(우리 것이 우선순위가 가장 낮다).
 */
object TypeIcons {

    /** 플러그인 안의 그림 폴더. */
    const val RESOURCE = "pack/gui/type/"

    fun id(type: ItemType): String = "gui/type/" + type.id

    fun key(type: ItemType): NamespacedKey? = NamespacedKey.fromString(PackAssets.NAMESPACE + ":" + id(type))
}
