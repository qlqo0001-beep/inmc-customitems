package com.inmc.customitems.pack

import org.bukkit.Material

/**
 * 번호 방식(`custom_model_data`) 리소스팩.
 *
 * 우리 팩은 [PackAssets] 의 `item_model` 을 쓴다 — 아이템 id 가 곧 모델 이름이라 번호를 나눠
 * 줄 필요가 없다. 그런데도 번호 방식이 필요한 자리가 있다: **이미 번호로 돌아가는 팩과
 * 섞을 때.** 그런 서버에서는 우리 아이템도 같은 방식으로 나와야 한다.
 *
 * ## 왜 조심해야 하는가
 *
 * 번호 방식은 **바닐라 아이템의 모델 파일을 대체한다.** `assets/minecraft/models/item/
 * diamond_sword.json` 을 팩에 넣으면 서버의 **모든** 다이아몬드 검이 그 파일대로 그려진다.
 * 그 파일에 `overrides` 만 적고 `parent`·`textures` 를 잘못 적으면 평범한 다이아몬드 검이
 * 전부 이상해진다 — 우리가 만든 아이템만이 아니다.
 *
 * 그래서 **바닐라 모델을 확실히 아는 재질에만** 번호를 붙인다. 모르면 붙이지 않고 보고서에
 * 적는다. 짐작해서 쓰면 서버 전체의 아이템 하나가 조용히 망가진다.
 *
 * ## 어떤 재질을 아는가
 *
 * | 갈래 | 바닐라 모델 | 어떻게 아는가 |
 * |---|---|---|
 * | 검·도끼·곡괭이·삽·괭이 | `item/handheld` + 자기 텍스처 | 접미사로 확실하다 |
 * | 아래 [FLAT] 목록 | `item/generated` + 자기 텍스처 | 하나씩 확인한 목록 |
 * | 그 밖에 | **모름** | 붙이지 않는다 |
 *
 * 모르는 재질이라도 `sources/` 의 팩이나 `pack/models/base/` 가 그 파일을 주면 거기에
 * **얹는다** — 그때는 바닐라 모델을 우리가 지어낼 필요가 없기 때문이다.
 *
 * **Bukkit 레지스트리를 쓰지 않는다.** `Material.isBlock` 도 `maxDurability` 도 서버가
 * 있어야 부를 수 있어서 검증할 수가 없고, 버전마다 답이 달라질 수 있다. 이름으로만 본다.
 */
object LegacyModels {

    /** 이 재질의 모델 파일 경로. 이 파일을 팩에 넣으면 그 바닐라 아이템 전체가 바뀐다. */
    fun basePath(material: Material): String =
        "assets/minecraft/models/item/" + material.name.lowercase() + ".json"

    /** 손에 들었을 때 비스듬히 잡히는 것들. 접미사로 확실히 안다. */
    fun isHandheld(material: Material): Boolean {
        val name = material.name
        return name.endsWith("_SWORD") || name.endsWith("_PICKAXE") ||
            name.endsWith("_AXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE")
    }

    /**
     * 바닐라 모델을 확실히 아는 재질인지.
     *
     * 모르면 false. **짐작하지 않는다** — 짐작이 틀리면 서버의 그 아이템 전체가 망가진다.
     */
    fun isKnown(material: Material): Boolean = isHandheld(material) || material.name in FLAT

    /**
     * 바닐라와 같은 모델 파일을 만든다.
     *
     * 바닐라가 실제로 갖고 있는 내용과 **글자 단위로 같아야** 한다. 다르면 우리 아이템이
     * 아니라 평범한 그 아이템이 이상해진다.
     */
    fun baseJson(material: Material): String {
        val name = material.name.lowercase()
        val parent = if (isHandheld(material)) "minecraft:item/handheld" else "minecraft:item/generated"
        return """
            {
              "parent": "$parent",
              "textures": { "layer0": "minecraft:item/$name" }
            }
        """.trimIndent()
    }

    /** 오버라이드 한 줄만 담은 조각. 이미 있는 파일에 [JsonMerge] 가 얹는다. */
    fun overrideJson(number: Int, model: String): String = """
        {
          "overrides": [
            { "predicate": { "custom_model_data": $number }, "model": "$model" }
          ]
        }
    """.trimIndent()

    /**
     * 다음에 쓸 번호를 고른다.
     *
     * **재질마다 따로 센다.** 번호는 그 바닐라 아이템 안에서만 구분되면 되므로, 검의 1번과
     * 종이의 1번은 서로 다른 모델이고 충돌하지 않는다.
     *
     * @param used 그 재질에서 이미 쓰인 번호들.
     * @param from 여기부터 찾는다. 1~999 는 남의 팩이 쓰고 있을 만한 구간이라 비워둔다.
     */
    fun nextNumber(used: Set<Int>, from: Int = FIRST): Int {
        var candidate = from.coerceAtLeast(1)
        while (candidate in used) candidate++
        return candidate
    }

    /** 자동 배정을 시작하는 번호. */
    const val FIRST = 1000

    /**
     * `item/generated` + 자기 텍스처 하나로 그려지는 재질들.
     *
     * 커스텀 아이템의 바탕으로 자주 쓰이는 것만 담았다. **하나씩 확인한 목록이다** — 여기
     * 없는 재질은 "안전한지 모르는" 것이지 "위험한" 것이 아니다. 늘리려면 바닐라의
     * `models/item/<이름>.json` 이 정말로 `generated` + `layer0` 하나인지 확인하고 넣어라.
     *
     * 일부러 뺀 것들과 이유:
     * - 활·석궁·삼지창·방패·낚싯대 — 자기 `overrides` 가 있다 (당기기·던지기·막기·던진 상태)
     * - 나침반·시계 — `angle` 오버라이드가 있다
     * - 물약·가죽 갑옷 — 덧씌우는 층(`layer1`)이 있다
     * - 스폰 알 — 템플릿 모델을 쓴다
     * - 블록 — 블록 모델을 쓴다. 이름으로 가려낼 방법이 없다
     */
    val FLAT: Set<String> = setOf(
        // 광물·재료
        "DIAMOND", "EMERALD", "IRON_INGOT", "GOLD_INGOT", "COPPER_INGOT", "NETHERITE_INGOT",
        "NETHERITE_SCRAP", "IRON_NUGGET", "GOLD_NUGGET", "AMETHYST_SHARD", "ECHO_SHARD",
        "QUARTZ", "COAL", "CHARCOAL", "REDSTONE", "LAPIS_LAZULI", "GLOWSTONE_DUST",
        "BLAZE_POWDER", "GUNPOWDER", "SUGAR", "FLINT", "CLAY_BALL", "BRICK", "NETHER_BRICK",
        "PRISMARINE_SHARD", "PRISMARINE_CRYSTALS", "NAUTILUS_SHELL", "HEART_OF_THE_SEA",
        "PHANTOM_MEMBRANE", "RABBIT_HIDE", "LEATHER", "FEATHER", "STRING", "PAPER", "BOOK",
        "STICK", "BLAZE_ROD", "BONE", "SLIME_BALL", "MAGMA_CREAM", "ENDER_PEARL", "ENDER_EYE",
        "GHAST_TEAR", "NETHER_STAR", "SHULKER_SHELL", "RABBIT_FOOT", "SPIDER_EYE",
        "FERMENTED_SPIDER_EYE", "ROTTEN_FLESH", "INK_SAC", "GLOW_INK_SAC", "SCUTE",
        "TURTLE_SCUTE", "HONEYCOMB", "WHEAT", "SUGAR_CANE", "BAMBOO", "KELP", "DRIED_KELP",
        "NAME_TAG", "LEAD", "CLAY", "SADDLE",

        // 먹을 것
        "APPLE", "GOLDEN_APPLE", "ENCHANTED_GOLDEN_APPLE", "BREAD", "COOKIE", "MELON_SLICE",
        "CARROT", "GOLDEN_CARROT", "POTATO", "BAKED_POTATO", "POISONOUS_POTATO", "BEETROOT",
        "CHORUS_FRUIT", "POPPED_CHORUS_FRUIT", "SWEET_BERRIES", "GLOW_BERRIES",
        "BEEF", "COOKED_BEEF", "PORKCHOP", "COOKED_PORKCHOP", "CHICKEN", "COOKED_CHICKEN",
        "MUTTON", "COOKED_MUTTON", "RABBIT", "COOKED_RABBIT", "COD", "COOKED_COD",
        "SALMON", "COOKED_SALMON", "TROPICAL_FISH", "PUFFERFISH",

        // 방어구 (가죽은 뺐다 — 덧씌우는 층이 있다)
        "IRON_HELMET", "IRON_CHESTPLATE", "IRON_LEGGINGS", "IRON_BOOTS",
        "GOLDEN_HELMET", "GOLDEN_CHESTPLATE", "GOLDEN_LEGGINGS", "GOLDEN_BOOTS",
        "DIAMOND_HELMET", "DIAMOND_CHESTPLATE", "DIAMOND_LEGGINGS", "DIAMOND_BOOTS",
        "NETHERITE_HELMET", "NETHERITE_CHESTPLATE", "NETHERITE_LEGGINGS", "NETHERITE_BOOTS",
        "CHAINMAIL_HELMET", "CHAINMAIL_CHESTPLATE", "CHAINMAIL_LEGGINGS", "CHAINMAIL_BOOTS",
        "TURTLE_HELMET",

        // 그 밖에 자주 쓰이는 것
        "ARROW", "SPECTRAL_ARROW", "SHEARS", "FLINT_AND_STEEL", "BUCKET", "GLASS_BOTTLE",
        "BOWL", "EGG", "SNOWBALL", "FIRE_CHARGE", "BLAZE_POWDER", "EXPERIENCE_BOTTLE",
        "WRITABLE_BOOK", "WRITTEN_BOOK", "KNOWLEDGE_BOOK", "ENCHANTED_BOOK",
        "MUSIC_DISC_13", "MUSIC_DISC_CAT", "DISC_FRAGMENT_5",
    )
}
