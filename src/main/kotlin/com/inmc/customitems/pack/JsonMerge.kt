package com.inmc.customitems.pack

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 리소스팩 안의 json 을 **내용으로** 섞는다.
 *
 * 이게 팩 병합기가 존재할 이유의 전부다. 파일을 통째로 덮으면 두 팩 중 하나가 **조용히
 * 사라진다** — 오류도 안 나고, 게임에 들어가서 텍스처가 없는 것을 보고서야 안다.
 *
 * **어느 파일을 섞고 어느 파일을 덮을지가 규칙이다** ([ruleFor]).
 *
 * | 종류 | 규칙 | 안 지키면 |
 * |---|---|---|
 * | `pack.mcmeta` | 형식 번호는 최댓값 | 낮은 쪽에 맞추면 높은 팩이 통째로 안 읽힌다 |
 * | `lang` 폴더의 json | 키 단위 깊은 병합 | 두 팩의 번역 중 한쪽이 전부 사라진다 |
 * | `sounds.json` | 키 단위 깊은 병합 | 같은 이유 |
 * | `atlases` 폴더의 json | `sources` 배열 합침 | 아틀라스는 목록이라 덮으면 텍스처가 안 붙는다 |
 * | `overrides` 가 있는 모델 | 배열 합치고 정렬 | **가장 흔한 충돌.** 두 팩이 같은 바닐라 아이템을 오버라이드하면 한쪽 아이템이 전부 사라진다 |
 * | 그 외 | 나중 것이 이김 | 통짜 정의라 섞을 수가 없다 |
 *
 * **Bukkit 을 모른다.** 서버 없이 전부 검증할 수 있다.
 */
object JsonMerge {

    /** 이 파일을 어떻게 다룰지. */
    enum class Rule {
        /** 키 단위로 깊게 섞는다. */
        DEEP,

        /** `sources` 배열을 잇는다. */
        ATLAS,

        /** `overrides` 배열을 잇고 predicate 로 정렬한다. */
        OVERRIDES,

        /** 형식 번호는 최댓값, 설명은 잇는다. */
        MCMETA,

        /** 나중 것이 이긴다. */
        REPLACE,
    }

    /**
     * 팩 안의 경로를 보고 규칙을 정한다. 경로는 `/` 구분, 앞에 슬래시 없음.
     *
     * 모델 파일은 경로만으로는 알 수 없다 — `overrides` 가 실제로 들어 있는지 봐야 한다.
     * [merge] 가 내용을 보고 다시 판단한다.
     */
    fun ruleFor(path: String): Rule {
        val lower = path.lowercase()
        return when {
            lower == "pack.mcmeta" -> Rule.MCMETA
            !lower.endsWith(".json") -> Rule.REPLACE
            lower.contains("/lang/") -> Rule.DEEP
            lower.endsWith("/sounds.json") -> Rule.DEEP
            lower.contains("/atlases/") -> Rule.ATLAS
            // 모델과 아이템 정의는 내용을 봐야 안다. 기본은 덮기.
            else -> Rule.REPLACE
        }
    }

    /**
     * 두 json 을 섞는다.
     *
     * @param existing 지금까지 쌓인 것. 먼저 온 팩들의 결과.
     * @param incoming 새로 온 것. **충돌하면 이쪽이 이긴다.**
     * @return 섞인 json 문자열. 어느 쪽이든 못 읽으면 [incoming] 을 그대로 돌려준다 —
     *         깨진 json 하나 때문에 팩 전체를 못 만들면 안 된다.
     */
    fun merge(path: String, existing: String, incoming: String): String {
        val left = parse(existing) ?: return incoming
        val right = parse(incoming) ?: return existing

        val rule = when {
            ruleFor(path) != Rule.REPLACE -> ruleFor(path)

            /*
             * 경로로는 못 가렸지만 **한쪽에라도** overrides 가 있으면 번호 방식 모델이다.
             *
             * 둘 다 있을 때만 섞으면 두 경우를 놓친다.
             *  - 바닐라 모델(overrides 없음) 위에 우리 오버라이드를 얹는 경우 — 우리가 만드는
             *    번호 방식이 정확히 이 모양이다. 덮이면 parent 를 잃어 아이템이 안 보인다.
             *  - 오버라이드가 있는 파일을 통짜 모델이 덮는 경우 — 남의 팩 아이템이 전부 사라진다.
             *
             * 어느 쪽이든 **잃는 쪽으로 틀리면 조용히 망가진다.** 그래서 or 다.
             */
            hasOverrides(left) || hasOverrides(right) -> Rule.OVERRIDES

            else -> Rule.REPLACE
        }

        val merged = when (rule) {
            Rule.REPLACE -> return incoming
            Rule.DEEP -> deep(left, right)
            Rule.ATLAS -> concatArray(left, right, "sources")
            Rule.OVERRIDES -> mergeOverrides(left, right)
            Rule.MCMETA -> mergeMcmeta(left, right)
        }
        return merged.toString()
    }

    // --- 규칙별 구현 ------------------------------------------------------------------

    /**
     * 키 단위 깊은 병합. **객체는 파고들고 나머지는 새 값이 이긴다.**
     *
     * 배열을 잇지 않는 것이 의도다 — `lang` 과 `sounds` 에서 배열은 "이 소리의 후보 목록"
     * 같은 완결된 값이라, 이으면 한쪽이 정의한 소리에 다른 쪽 후보가 섞여 들어간다.
     */
    fun deep(left: JsonElement, right: JsonElement): JsonElement {
        if (left !is JsonObject || right !is JsonObject) return right

        val result = left.deepCopy().asJsonObject
        for ((key, value) in right.entrySet()) {
            val old = result.get(key)
            if (old is JsonObject && value is JsonObject) {
                result.add(key, deep(old, value))
            } else {
                result.add(key, value)
            }
        }
        return result
    }

    /** 한 배열 필드만 잇는다. 나머지는 새 값이 이긴다. */
    private fun concatArray(left: JsonElement, right: JsonElement, field: String): JsonElement {
        if (left !is JsonObject || right !is JsonObject) return right

        val result = right.deepCopy().asJsonObject
        val old = left.getAsJsonArray(field) ?: return result
        val new = right.getAsJsonArray(field) ?: JsonArray()

        val joined = JsonArray()
        // 먼저 온 것을 앞에 둔다. 중복은 한 번만 — 같은 팩을 두 번 넣어도 두 배가 되지 않는다.
        val seen = LinkedHashSet<String>()
        for (entry in old + new) {
            if (seen.add(entry.toString())) joined.add(entry)
        }
        result.add(field, joined)
        return result
    }

    /**
     * 레거시 `custom_model_data` 오버라이드를 잇는다.
     *
     * **팩 병합에서 가장 흔한 충돌이다.** 두 팩이 `models/item/paper.json` 을 각각 갖고
     * 자기 아이템들을 오버라이드해 두는데, 덮으면 한쪽 팩의 아이템이 **전부** 사라진다.
     *
     * 잇고 나서 `custom_model_data` 로 정렬한다 — 마인크래프트는 오버라이드를 **순서대로**
     * 보고 첫 일치를 쓰므로, 뒤섞여 있으면 작은 번호가 큰 번호를 가로챈다.
     */
    private fun mergeOverrides(left: JsonElement, right: JsonElement): JsonElement {
        if (left !is JsonObject || right !is JsonObject) return right

        /*
         * 나머지 칸은 **키 단위로** 섞는다. 새 값이 이기되, 새 쪽에 없는 칸은 남는다.
         *
         * 통째로 새 값을 쓰면 안 된다 — 우리가 만드는 오버라이드 조각에는 `parent` 도
         * `textures` 도 없어서, 그걸 그대로 쓰면 바닐라 모델이 사라지고 그 아이템이 통째로
         * 안 보이게 된다. 우리 아이템만이 아니라 **평범한 그 아이템**까지.
         */
        val result = deep(left, right).asJsonObject
        val joined = JsonArray()
        val seen = LinkedHashSet<String>()
        for (entry in overridesOf(left) + overridesOf(right)) {
            if (seen.add(entry.toString())) joined.add(entry)
        }

        val sorted = joined.sortedBy { cmdOf(it) }
        val out = JsonArray()
        for (entry in sorted) out.add(entry)
        result.add("overrides", out)
        return result
    }

    /**
     * `pack.mcmeta`.
     *
     * **형식 번호는 최댓값**을 쓴다. 낮은 쪽에 맞추면 높은 형식으로 만든 팩이 클라이언트에서
     * 통째로 안 읽히거나 경고가 뜬다.
     *
     * **오버레이 목록(`overlays.entries`)은 잇는다.** 깊은 병합은 배열을 덮으므로 그대로 두면 한 팩의 목록만 남고,
     * 다른 팩의 오버레이 폴더는 zip 에 들어 있는데도 **켜지지 않는다**(버전별 셰이더·모델이 거기 있다). 같은 폴더면
     * 나중 것을 쓰고, 나중 팩의 것을 뒤에 둔다 — 클라이언트는 목록 순서대로 얹어 뒤가 이기므로 "나중 소스가 이긴다" 와 같다.
     */
    private fun mergeMcmeta(left: JsonElement, right: JsonElement): JsonElement {
        if (left !is JsonObject || right !is JsonObject) return right

        val result = deep(left, right).asJsonObject
        val pack = result.getAsJsonObject("pack") ?: return result

        val leftPack = left.getAsJsonObject("pack")
        val rightPack = right.getAsJsonObject("pack")
        val format = maxOf(formatOf(leftPack), formatOf(rightPack))
        if (format > 0) pack.addProperty("pack_format", format)

        val incoming = overlayEntries(right)
        val incomingDirs = incoming.mapNotNull(::overlayDir).toSet()
        val joined = overlayEntries(left).filter { overlayDir(it) !in incomingDirs } + incoming
        if (joined.isNotEmpty()) {
            val overlays = result.get("overlays") as? JsonObject ?: JsonObject().also { result.add("overlays", it) }
            overlays.add("entries", JsonArray().apply { joined.forEach(::add) })
        }

        return result
    }

    private fun overlayEntries(meta: JsonObject): List<JsonElement> =
        ((meta.get("overlays") as? JsonObject)?.get("entries") as? JsonArray)?.toList().orEmpty()

    private fun overlayDir(entry: JsonElement): String? =
        runCatching { (entry as? JsonObject)?.get("directory")?.asString }.getOrNull()

    // --- 도우미 ---------------------------------------------------------------------

    fun parse(text: String): JsonElement? =
        runCatching { JsonParser.parseString(text) }.getOrNull()?.takeIf { !it.isJsonNull }

    private fun hasOverrides(element: JsonElement): Boolean =
        element is JsonObject && element.get("overrides")?.isJsonArray == true

    private fun overridesOf(element: JsonElement): List<JsonElement> =
        (element as? JsonObject)?.getAsJsonArray("overrides")?.toList().orEmpty()

    /** 정렬 열쇠. 못 읽으면 맨 뒤로 — 알 수 없는 것이 알려진 것을 가로채면 안 된다. */
    private fun cmdOf(entry: JsonElement): Double {
        val predicate = (entry as? JsonObject)?.getAsJsonObject("predicate") ?: return Double.MAX_VALUE
        val value = predicate.get("custom_model_data") ?: return Double.MAX_VALUE
        return runCatching { value.asDouble }.getOrDefault(Double.MAX_VALUE)
    }

    private fun formatOf(pack: JsonObject?): Int =
        runCatching { pack?.get("pack_format")?.asInt ?: 0 }.getOrDefault(0)
}
