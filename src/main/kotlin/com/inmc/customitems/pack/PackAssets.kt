package com.inmc.customitems.pack

import com.inmc.customitems.item.CustomItem

/**
 * 아이템 정의에서 리소스팩 파일을 만든다.
 *
 * **`custom_model_data` 번호를 쓰지 않는다.** 1.21.4 부터 아이템에 `item_model` 컴포넌트를
 * 붙일 수 있고, 그러면 번호를 나눠 줄 필요가 없다 — 아이템 id 가 곧 모델 이름이다.
 * 번호 방식은 팩 두 개를 합칠 때 번호가 겹치는 것이 가장 흔한 사고였고, 그 사고 자체가
 * 없어진다.
 *
 * 만드는 파일은 셋이다.
 *
 * ```
 * assets/inmc/textures/item/<id>.png   ← 관리자가 넣은 png 를 옮긴 것
 * assets/inmc/models/item/<id>.json    ← 그 텍스처를 쓰는 모델
 * assets/inmc/items/<id>.json          ← item_model 이 가리키는 정의 (1.21.4+)
 * ```
 *
 * 관리자가 모델을 직접 만들었으면 가운데 것을 건너뛰고 그 모델을 가리킨다.
 *
 * **Bukkit 을 모른다.** 서버 없이 전부 검증할 수 있다.
 */
object PackAssets {

    const val NAMESPACE = "inmc"

    /** 우리가 만드는 팩의 형식 번호. 소스 팩이 더 높으면 그쪽이 이긴다. */
    const val PACK_FORMAT = 46

    fun texturePath(id: String): String = "assets/$NAMESPACE/textures/item/$id.png"

    fun modelPath(id: String): String = "assets/$NAMESPACE/models/item/$id.json"

    fun itemPath(id: String): String = "assets/$NAMESPACE/items/$id.json"

    /** 강화 [level] 단계의 모양 이름. 그 단계가 텍스처·모델을 바꿨을 때만 쓰인다. */
    fun levelId(item: CustomItem, level: Int): String = item.resourceId + "_lv" + level

    /** `item_model` 컴포넌트가 가리키는 값. */
    fun itemModelKey(id: String): String = "$NAMESPACE:$id"

    /**
     * 텍스처 하나를 쓰는 평범한 모델.
     *
     * `item/generated` 는 **평면 아이콘**이다. 검이나 블록처럼 손에 들었을 때 모양이 달라야
     * 하는 것은 관리자가 모델을 직접 만들어 [CustomItem.model] 에 적는다.
     */
    fun modelJson(id: String): String = """
        {
          "parent": "minecraft:item/generated",
          "textures": { "layer0": "$NAMESPACE:item/$id" }
        }
    """.trimIndent()

    /**
     * `item_model` 이 가리키는 아이템 정의.
     *
     * @param model 이 아이템이 쓸 모델의 전체 이름. 보통 `inmc:item/<id>`.
     */
    fun itemJson(model: String): String = """
        {
          "model": { "type": "minecraft:model", "model": "$model" }
        }
    """.trimIndent()

    fun mcmetaJson(description: String): String = """
        {
          "pack": {
            "pack_format": $PACK_FORMAT,
            "description": "$description"
          }
        }
    """.trimIndent()

    /**
     * 이 아이템이 쓸 모델 이름.
     *
     * 관리자가 [CustomItem.model] 에 적었으면 그것. `:` 이 없으면 우리 네임스페이스로 친다 —
     * `item/boss_sword` 라고만 적어도 `inmc:item/boss_sword` 가 된다.
     */
    fun modelNameFor(item: CustomItem): String {
        val custom = item.model.trim()
        if (custom.isBlank()) return "$NAMESPACE:item/${item.resourceId}"
        return if (':' in custom) custom else "$NAMESPACE:$custom"
    }

    fun blockModelPath(id: String): String = "assets/$NAMESPACE/models/block/$id.json"

    /** 텍스처 하나로 여섯 면을 칠한 정육면체 — 텍스처만 적은 블록 아이템의 모양(놓였을 때도, 가방에서도). */
    fun blockModelJson(id: String): String = """
        {
          "parent": "minecraft:block/cube_all",
          "textures": { "all": "$NAMESPACE:item/$id" }
        }
    """.trimIndent()

    /** 블록 아이템이 텍스처만 적었으면 정육면체로 그린다(평면 아이콘 대신). */
    fun isCube(item: CustomItem): Boolean = item.block != null && item.model.isBlank() && item.texture.isNotBlank()

    /**
     * 블록으로 놓였을 때 그 칸을 그리는 모델. 직접 적은 모델이면 그것(블록 모델이어야 한다 — 옛 IA 상자처럼), 텍스처뿐이면 우리가 만드는
     * 정육면체. 둘 다 없으면 null — 그릴 것이 없다(블록 상태 방식은 그 칸이 비어 보인다).
     */
    fun blockModelFor(item: CustomItem): String? = when {
        item.model.isNotBlank() -> modelNameFor(item)
        item.texture.isNotBlank() -> "$NAMESPACE:block/${item.resourceId}"
        else -> null
    }

    /**
     * 텍스처만 적은 낚싯대. 평면 아이콘(`item/generated`)으로 그리면 손에 든 모양이 막대가 아니라 아이콘이 되고,
     * 던져도 모양이 안 바뀐다 — 바닐라처럼 `handheld_rod` 로 그리고, 옆에 `<이름>_cast.png` 가 있으면 던졌을 때 그것으로 바꾼다.
     */
    fun isRod(item: CustomItem): Boolean = item.material.name == "FISHING_ROD" && item.block == null && item.model.isBlank() && item.texture.isNotBlank()

    /** 낚싯대 텍스처의 던진 모양 파일 이름. `rod/basic.png` → `rod/basic_cast.png`. */
    fun castTexture(texture: String): String = texture.removeSuffix(".png") + "_cast.png"

    fun rodModelJson(id: String): String = """
        {
          "parent": "minecraft:item/handheld_rod",
          "textures": { "layer0": "$NAMESPACE:item/$id" }
        }
    """.trimIndent()

    /** 던졌는지(`fishing_rod/cast`)에 따라 두 모델 중 하나 — 바닐라 `items/fishing_rod.json` 과 같은 모양. */
    fun rodItemJson(model: String, castModel: String): String = """
        {
          "model": {
            "type": "minecraft:condition",
            "property": "minecraft:fishing_rod/cast",
            "on_false": { "type": "minecraft:model", "model": "$model" },
            "on_true": { "type": "minecraft:model", "model": "$castModel" }
          }
        }
    """.trimIndent()

    /**
     * 이 아이템이 팩에 들어가야 하는지.
     *
     * 텍스처도 모델도 없으면 만들 것이 없다 — 바닐라 모양 그대로 쓰는 아이템이고, 그런
     * 아이템까지 팩에 넣으면 빈 정의가 아이템 수만큼 쌓인다.
     */
    fun needsPack(item: CustomItem): Boolean =
        item.texture.isNotBlank() || item.model.isNotBlank()
}
