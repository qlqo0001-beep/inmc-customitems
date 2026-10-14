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

    private val MODEL_PATH = Regex("^assets/([a-z0-9_.-]+)/models/([a-z0-9_./-]+)\\.json$")

    /**
     * 모델 목록(관리 화면)에 보일 모델인가 — 팩 경로 `assets/<이름공간>/models/<경로>.json` → `<이름공간>:<경로>`. 바닐라(`minecraft`)와
     * 밑줄로 시작하는 내부 이름공간(IA 의 `_iainternal`)은 뺀다. 오버레이 안의 것도 뺀다(같은 모델의 판별 사본이다).
     * **모델 플러그인이 제 모델을 조각조각 그리는 것도 뺀다**([ENGINE_NAMESPACES], 2026-10-10 사용자 요청) — BetterModel 의 뼈 하나하나
     * (`bettermodel:b/…`)는 아이템 모양으로 고를 것이 아니고, 그 수천 개마다 미리보기 정의를 붙이면 팩만 커진다.
     */
    fun listedModel(path: String): String? {
        val match = MODEL_PATH.matchEntire(path) ?: return null
        val (namespace, model) = match.destructured
        if (namespace == "minecraft" || namespace.startsWith("_") || namespace in ENGINE_NAMESPACES) return null
        // 후렴초 파편을 지우는 우리 빈 모델 — 고를 모양이 아니다.
        if ("$namespace:$model" == com.inmc.customitems.block.BlockStates.NO_PARTICLE_MODEL) return null
        return "$namespace:$model"
    }

    /**
     * 파편 그림(`textures.particle`)이 없는 모델이면 첫 텍스처를 파편 그림으로 넣은 JSON, 고칠 것이 없으면 null(2026-10-10).
     * Blockbench 로 만든 모델은 `particle` 을 안 적는 일이 많은데, 그러면 그 아이템 모양 파편이 보라·검정(없는 그림)으로 나온다.
     * 제 요소(`elements`)가 있는 모델만 — 요소 없이 부모를 따르는 모델은 부모의 파편 그림을 쓴다.
     */
    fun withParticle(json: String): String? {
        val root = runCatching { com.google.gson.JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return null
        if (!root.has("elements")) return null
        val textures = root.getAsJsonObject("textures") ?: return null
        if (textures.has("particle")) return null
        var value: String? = null
        for ((_, element) in textures.entrySet()) {
            var candidate = element.takeIf { it.isJsonPrimitive }?.asString ?: continue
            var hops = 0
            while (candidate.startsWith("#") && hops++ < 8) candidate = textures.get(candidate.substring(1))?.takeIf { it.isJsonPrimitive }?.asString ?: break
            if (!candidate.startsWith("#")) {
                value = candidate
                break
            }
        }
        textures.addProperty("particle", value ?: return null)
        return com.google.gson.GsonBuilder().disableHtmlEscaping().create().toJson(root)
    }

    private val BONE_ITEM = Regex("^assets/bettermodel/items/(.+)\\.json$")

    /** BetterModel 이 팩에 넣은 뼈 아이템 정의 → `bettermodel:<경로>`. 그 모델의 텍스처를 쓰는 아이템이라 파편 그림으로 쓴다. */
    fun boneItem(path: String): String? = BONE_ITEM.matchEntire(path)?.let { "bettermodel:" + it.groupValues[1] }

    /**
     * [model] 의 뼈 아이템 하나 — BetterModel 은 `<모델>_<뼈>` 로 이름을 짓는다. 다른 모델 이름이 그 앞부분이면(`fan` 과 `fan_iron`) 더 긴 쪽의
     * 것이므로 [known](BetterModel 이 아는 모델 이름) 가운데 가장 긴 앞부분이 [model] 인 것만 고른다. 없으면 null.
     */
    fun boneItemFor(model: String, items: List<String>, known: Set<String>): String? = items.sorted().firstOrNull { key ->
        val name = key.substringAfterLast('/')
        name.startsWith(model + "_") && known.filter { name.startsWith(it + "_") }.maxByOrNull { it.length } == model
    }

    /** 모델 플러그인(BetterModel · ModelEngine)이 쓰는 이름공간 — 그 플러그인이 엔티티에 붙여 그린다. */
    val ENGINE_NAMESPACES = setOf("bettermodel", "modelengine")

    /**
     * 모델 하나를 게임에서 보려고 붙이는 아이템 정의. 1.21.4+ 는 `item_model` 이 모델이 아니라 **아이템 정의**를 가리키므로, 아이템이
     * 안 쓰는 모델은 이게 있어야 보인다. `<이름공간>:<경로>` → `assets/inmc/items/preview/<이름공간>/<경로>.json`.
     */
    fun previewPath(model: String): String = itemPath(PREVIEW + "/" + model.replace(':', '/'))

    /** 미리보기 정의를 가리키는 `item_model` 값. */
    fun previewKey(model: String): String = itemModelKey(PREVIEW + "/" + model.replace(':', '/'))

    /** 미리보기 정의 경로 → 모델 이름. 미리보기가 아니면 null. */
    fun previewModel(path: String): String? {
        val prefix = "assets/$NAMESPACE/items/$PREVIEW/"
        if (!path.startsWith(prefix) || !path.endsWith(".json")) return null
        val rest = path.removePrefix(prefix).removeSuffix(".json")
        val slash = rest.indexOf('/').takeIf { it > 0 } ?: return null
        return rest.substring(0, slash) + ":" + rest.substring(slash + 1)
    }

    const val PREVIEW = "preview"

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
