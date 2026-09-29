package com.inmc.customitems

import com.inmc.customitems.ability.Ability
import com.inmc.customitems.ability.EffectType
import com.inmc.customitems.ability.Target
import com.inmc.customitems.ability.Trigger
import com.inmc.customitems.config.Messages
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.ItemType
import com.inmc.customitems.item.ItemModifier
import com.inmc.customitems.item.Spread
import com.inmc.customitems.item.Stat
import com.inmc.customitems.item.Tier
import kr.inmc.core.store.DefinitionKey
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 아이템 정의가 **디스크를 한 번 왕복하고도 그대로인지** 본다.
 *
 * 이 검사가 없으면 관리자가 화면에서 값을 고치고, 서버를 껐다 켜면 원래대로 돌아가 있는 일이
 * 생긴다. 오류는 하나도 안 난다 — 저장할 때 빠뜨린 필드는 읽을 때도 조용히 기본값이 되기
 * 때문이다. 필드를 새로 더하면 여기가 먼저 빨개져야 한다.
 */
class ItemDefinitionTest {

    private fun roundTrip(item: CustomItem): CustomItem? {
        val written = YamlConfiguration()
        item.save(written.createSection(item.id))
        val reread = YamlConfiguration()
        reread.loadFromString(written.saveToString())
        return CustomItem.load(item.id, reread.getConfigurationSection(item.id)!!)
    }

    // --- 왕복 ----------------------------------------------------------------------

    @Test
    fun `아이템의 모든 칸이 왕복해도 그대로다`() {
        val original = CustomItem(
            id = "blade",
            material = Material.NETHERITE_SWORD,
            type = ItemType.WEAPON,
            tier = Tier.LEGENDARY,
            category = "한손검",
            customType = "보호권",
            roles = mapOf("invkeeper.item" to mapOf("kind" to "TIMED_PROTECTION", "minutes" to "30"), "urb.capsule" to mapOf("box" to "기본상자")),
            itemModel = "ia:coin",
            displayName = "&6섬광검",
            lore = listOf("&7번개를 머금은 검.", ""),
            customModelData = 1234,
            enchants = mapOf("sharpness" to 10, "unbreaking" to 5),
            customEnchants = mapOf("lifesteal" to 3, "frostbite" to 1),
            flags = setOf("HIDE_ENCHANTS"),
            unbreakable = true,
            maxDurability = 5000,
            glow = true,
            stats = mapOf(
                Stat.ATTACK_DAMAGE to 9.0,
                Stat.CRIT_CHANCE to 25.0,
                Stat.LIFESTEAL to 8.0,
            ),
            spreads = mapOf(Stat.ATTACK_DAMAGE to Spread(0.1, 0.3), Stat.CRIT_CHANCE to Spread(0.05)),
            modifiers = listOf(ItemModifier("sharp", "<red>날카로운", chance = 40.0, stats = mapOf(Stat.ATTACK_DAMAGE to 2.0))),
            abilities = listOf(
                Ability.of(Trigger.RIGHT_CLICK, EffectType.TELEPORT).copy(cooldownSeconds = 6.0),
                Ability.of(Trigger.ON_HIT, EffectType.LIGHTNING).copy(chance = 30.0),
            ),
            data = mapOf("fishing.rod" to "1", "fishing.reel-power" to "15"),
            preventVanillaUse = true,
            block = com.inmc.customitems.item.BlockSpec(com.inmc.customitems.item.BlockKind.TRANSPARENT, "down=false,east=false,north=false,south=false,up=false,west=true", drop = false),
        )

        assertEquals(original, roundTrip(original))
    }

    @Test
    fun `엔티티 블록은 상태 없이 왕복하고 블록이 아닌 아이템에는 블록 칸이 안 생긴다`() {
        val entity = CustomItem("chair", Material.PAPER, block = com.inmc.customitems.item.BlockSpec(com.inmc.customitems.item.BlockKind.ENTITY))
        assertEquals(entity, roundTrip(entity))

        val plain = YamlConfiguration()
        CustomItem("stick", Material.STICK).save(plain.createSection("stick"))
        assertNull(plain.getConfigurationSection("stick")!!.get("block"), "적으면 이 칸이 생긴 것만으로 모든 아이템의 지문이 바뀌어 다시 그린다")
    }

    @Test
    fun `재질이 없으면 읽지 않는다`() {
        // 재질 없는 아이템은 만들 수가 없다. 조용히 읽히면 지급했을 때 아무것도 안 나온다.
        val section = YamlConfiguration().createSection("x")
        section.set("display-name", "이름만 있는 것")

        assertNull(CustomItem.load("x", section))
    }

    @Test
    fun `0 인 능력치는 저장되지 않는다`() {
        // 남겨두면 로어에 `+0 흡혈` 같은 줄이 생기고 설정 파일이 안 쓰는 항목으로 채워진다.
        val item = CustomItem(
            id = "x",
            material = Material.STICK,
            stats = mapOf(Stat.ATTACK_DAMAGE to 5.0, Stat.LIFESTEAL to 0.0),
        )

        val loaded = roundTrip(item)!!

        assertEquals(mapOf(Stat.ATTACK_DAMAGE to 5.0), loaded.stats)
    }

    @Test
    fun `기능 순서가 유지된다`() {
        // 목록 순서가 곧 발동 순서다. 맵 순서에 흔들리면 같은 아이템이 서버마다 다르게 돈다.
        val item = CustomItem(
            id = "x",
            material = Material.STICK,
            abilities = listOf(
                Ability.of(Trigger.RIGHT_CLICK, EffectType.SOUND),
                Ability.of(Trigger.RIGHT_CLICK, EffectType.PARTICLE),
                Ability.of(Trigger.RIGHT_CLICK, EffectType.HEAL),
            ),
        )

        val loaded = roundTrip(item)!!

        assertEquals(
            listOf(EffectType.SOUND, EffectType.PARTICLE, EffectType.HEAL),
            loaded.abilities.map { it.effect },
        )
    }

    @Test
    fun `점이 들어간 연동 값 열쇠가 살아남는다`() {
        // `fishing.reel-power` 는 YamlConfiguration 이 계층으로 만들어버린다. 그래도 원래
        // 적은 이름으로 되읽혀야 한다 — 아니면 낚시가 그 값을 영영 못 찾는다.
        val item = CustomItem(
            id = "rod",
            material = Material.FISHING_ROD,
            data = mapOf(
                "fishing.rod" to "1",
                "fishing.grade-bonus.s" to "5",
                "plain" to "value",
            ),
        )

        val loaded = roundTrip(item)!!

        assertEquals("1", loaded.data["fishing.rod"])
        assertEquals("5", loaded.data["fishing.grade-bonus.s"])
        assertEquals("value", loaded.data["plain"])
    }

    // --- 기능 ----------------------------------------------------------------------

    @Test
    fun `모르는 효과는 읽지 않는다`() {
        // 조용히 무시하면 "왜 안 터지지" 를 찾느라 시간을 쓴다.
        val section = YamlConfiguration().createSection("a")
        section.set("trigger", "right-click")
        section.set("effect", "그런건없다")

        assertNull(Ability.load(section))
    }

    @Test
    fun `모르는 발동 조건은 우클릭으로 떨어진다`() {
        // 효과와 달리 조건은 하나로 정해도 안전하다 — 아이템이 죽지 않고, 눌러보면 바로 안다.
        val section = YamlConfiguration().createSection("a")
        section.set("trigger", "그런건없다")
        section.set("effect", "heal")

        assertEquals(Trigger.RIGHT_CLICK, Ability.load(section)!!.trigger)
    }

    @Test
    fun `새 기능은 효과가 요구하는 값을 전부 갖고 시작한다`() {
        // 비워두면 첫 발동에서 0 이나 빈 문자열이 쓰이고, 관리자는 설정한 적 없는 값 때문에
        // 아무 일도 안 일어나는 것을 보게 된다.
        for (effect in EffectType.entries) {
            val ability = Ability.of(Trigger.RIGHT_CLICK, effect)
            for (param in effect.params) {
                assertEquals(param.default, ability.value(param.key), effect.id + "." + param.key)
            }
        }
    }

    @Test
    fun `대상이 필요한 효과는 기본 대상이 정해져 있다`() {
        assertEquals(Target.OTHER, Ability.of(Trigger.ON_HIT, EffectType.DAMAGE).target())
        assertEquals(Target.SELF, Ability.of(Trigger.RIGHT_CLICK, EffectType.HEAL).target())
    }

    @Test
    fun `확률은 0 과 100 사이로 잘린다`() {
        val section = YamlConfiguration().createSection("a")
        section.set("effect", "heal")
        section.set("chance", 500.0)

        assertEquals(100.0, Ability.load(section)!!.chance)
    }

    // --- 능력치 --------------------------------------------------------------------

    @Test
    fun `능력치 id 가 전부 다르다`() {
        val ids = Stat.entries.map { it.id }

        assertEquals(ids.size, ids.toSet().size, "겹치는 id: $ids")
    }

    @Test
    fun `바닐라 능력치에는 속성 열쇠가 있고 나머지에는 없다`() {
        // 이 구분이 틀리면 공격력이 바닐라로 안 내려가거나, 흡혈을 바닐라에 붙이려다 사라진다.
        assertTrue(Stat.ATTACK_DAMAGE.isVanilla)
        assertTrue(Stat.ARMOR.isVanilla)
        assertTrue(!Stat.CRIT_CHANCE.isVanilla)
        assertTrue(!Stat.LIFESTEAL.isVanilla)
        assertTrue(!Stat.THORNS.isVanilla)
    }

    @Test
    fun `능력치를 이름으로 찾을 때 표기를 가리지 않는다`() {
        assertEquals(Stat.ATTACK_DAMAGE, Stat.of("attack-damage"))
        assertEquals(Stat.ATTACK_DAMAGE, Stat.of("attack_damage"))
        assertEquals(Stat.ATTACK_DAMAGE, Stat.of("ATTACK-DAMAGE"))
        assertNull(Stat.of("그런건없다"))
        assertNull(Stat.of(null))
    }

    @Test
    fun `방어구에는 방어 계열을 먼저 권한다`() {
        // 장화 편집 화면 첫 줄이 공격 속도면 관리자가 잘못 만든다.
        val suggested = Stat.suggestedFor(Material.DIAMOND_BOOTS)

        assertEquals(Stat.ARMOR, suggested.first())
        assertTrue(Stat.ATTACK_SPEED !in suggested)
    }

    // --- 분류 ----------------------------------------------------------------------

    @Test
    fun `재질에서 종류를 짐작한다`() {
        assertEquals(ItemType.WEAPON, ItemType.guess(Material.DIAMOND_SWORD))
        assertEquals(ItemType.ARMOR, ItemType.guess(Material.NETHERITE_CHESTPLATE))
        assertEquals(ItemType.TOOL, ItemType.guess(Material.FISHING_ROD))
        assertEquals(ItemType.CONSUMABLE, ItemType.guess(Material.GOLDEN_APPLE))
        assertEquals(ItemType.MATERIAL, ItemType.guess(Material.DIAMOND))
        assertEquals(ItemType.MISC, ItemType.guess(Material.ENDER_PEARL))
    }

    @Test
    fun `모르는 분류는 기본값으로 떨어진다`() {
        assertEquals(ItemType.MISC, ItemType.of("그런건없다"))
        assertEquals(Tier.COMMON, Tier.of(null))
        assertEquals(Tier.MYTHIC, Tier.of("MYTHIC"))
    }

    // --- 배포 리소스 ----------------------------------------------------------------

    private fun load(path: String): YamlConfiguration {
        val stream = javaClass.classLoader.getResourceAsStream(path)
        assertNotNull(stream, "리소스를 찾을 수 없습니다: $path")
        return stream.use {
            YamlConfiguration.loadConfiguration(InputStreamReader(it, StandardCharsets.UTF_8))
        }
    }

    @Test
    fun `배포 예시 아이템이 전부 읽힌다`() {
        val root = load("items.yml").getConfigurationSection("items")
        assertNotNull(root, "items.yml 에 items 절이 없습니다")

        val keys = root.getKeys(false)
        assertTrue(keys.isNotEmpty(), "예시가 하나도 없습니다")

        for (key in keys) {
            val item = CustomItem.load(key, root.getConfigurationSection(key)!!)
            assertNotNull(item, "$key 를 읽지 못했습니다")
            assertTrue(DefinitionKey.isValid(key), "$key 는 이름 규칙에 맞지 않습니다")
        }
    }

    @Test
    fun `배포 예시의 낚싯대가 낚시가 읽는 모양이다`() {
        // 이 예시가 깨지면 "커스텀아이템에서 낚싯대를 만들 수 있다"는 약속이 문서로만 남는다.
        val root = load("items.yml").getConfigurationSection("items")!!
        val rod = CustomItem.load("example_rod", root.getConfigurationSection("example_rod")!!)

        assertNotNull(rod)
        assertTrue(rod.data.containsKey("fishing.rod"), "fishing.rod 표시가 없으면 낚싯대로 안 읽힌다")
        assertEquals("15", rod.data["fishing.reel-power"])
        assertEquals("2", rod.data["fishing.grade-bonus.s"])
    }

    @Test
    fun `배포 메시지와 내장 기본값의 키가 정확히 같다`() {
        val shipped = load("messages.yml").getKeys(false).toSet()
        val builtIn = Messages.DEFAULTS.keys

        assertEquals(emptySet(), builtIn - shipped, "배포 messages.yml 에 빠진 키")
        assertEquals(emptySet(), shipped - builtIn, "코드가 읽지 않는 키")
    }

    @Test
    fun `프롬프트 네 키가 있다`() {
        // core 의 ChatPrompt 가 이 넷을 요구한다. 빠지면 입력 안내가 조용히 무음이 된다.
        val keys = load("messages.yml").getKeys(false)

        for (key in listOf("prompt-enter", "prompt-cancelled", "prompt-timeout", "prompt-invalid-number")) {
            assertTrue(key in keys, "$key 가 없습니다")
        }
    }

    @Test
    fun `한글 id 도 마인크래프트가 받는 열쇠 이름을 갖고 영문 id 는 그대로다`() {
        // 속성 수정자 열쇠·item_model·팩 경로는 [a-z0-9_.-] 만 받는다. 한글이 들어가면 그리기마다 던졌다.
        val safe = Regex("^[a-z0-9_.-]+$")
        assertEquals("golden_rod", CustomItem.resourceId("golden_rod"), "영문 id 는 바뀌면 안 된다 — 나간 아이템의 열쇠가 바뀐다")
        val korean = CustomItem.resourceId("전설의검")
        assertTrue(safe.matches(korean), korean)
        assertEquals(korean, CustomItem.resourceId("전설의검"), "늘 같은 이름이어야 한다")
        assertTrue(korean != CustomItem.resourceId("전설의칼"))
        assertTrue(safe.matches(CustomItem.resourceId("테스트_1")))
    }

    @Test
    fun `효과가 나는 곳은 셋 다 왕복하고 안 정했으면 적지 않는다`() {
        for (value in listOf(true, false, null)) {
            val item = CustomItem("charm", Material.PAPER, type = ItemType.TALISMAN, inventoryEffect = value)
            assertEquals(value, roundTrip(item)!!.inventoryEffect)
        }
        val section = YamlConfiguration().createSection("charm")
        CustomItem("charm", Material.PAPER, type = ItemType.TALISMAN).save(section)
        assertTrue(!section.contains("inventory-effect"), "서버 설정을 따르는 아이템에 값이 적히면 서버 설정을 바꿔도 안 따른다")
    }

    @Test
    fun `장착 칸 밖의 효과는 아이템 설정이 이기고 없으면 서버 설정이다`() {
        val charm = CustomItem("charm", Material.PAPER, type = ItemType.TALISMAN)
        assertTrue(charm.worksOutsideSlots(true))
        assertTrue(!charm.worksOutsideSlots(false))
        assertTrue(charm.copy(inventoryEffect = true).worksOutsideSlots(false))
        assertTrue(!charm.copy(type = ItemType.ACCESSORY, inventoryEffect = false).worksOutsideSlots(true))
        // 장착 칸에 못 끼우는 종류는 이 설정과 상관없다 — 무기가 손에서 멈추면 안 된다.
        assertTrue(CustomItem("blade", Material.IRON_SWORD, type = ItemType.WEAPON, inventoryEffect = false).worksOutsideSlots(false))
    }

    @Test
    fun `로어가 효과가 나는 곳을 말한다`() {
        val off = com.inmc.customitems.item.Lookup(inventoryEffects = { false })
        fun lore(item: CustomItem, lookup: com.inmc.customitems.item.Lookup) =
            com.inmc.customitems.item.ItemBuilder.buildLore(item, emptyMap(), lookup = lookup).joinToString(" ")
        val charm = CustomItem("charm", Material.PAPER, type = ItemType.TALISMAN)
        val ring = CustomItem("ring", Material.GOLD_NUGGET, type = ItemType.ACCESSORY)
        assertTrue("소지 효과" in lore(charm, com.inmc.customitems.item.Lookup.NONE))
        assertTrue("장착 칸(/장비)에 끼워야" in lore(charm, off))
        assertTrue("소지 효과" in lore(charm.copy(inventoryEffect = true), off))
        assertTrue("장착 칸" !in lore(ring, com.inmc.customitems.item.Lookup.NONE), "손에서도 되는 장신구에 없던 줄이 생겼다")
        assertTrue("장착 칸(/장비)에 끼워야" in lore(ring, off))
    }

    @Test
    fun `만든 종류는 기준 종류와 이름·아이콘·기호가 왕복하고 기본 종류는 기준을 적지 않는다`() {
        val made = com.inmc.customitems.item.TypeDef("보호권", ItemType.CONSUMABLE, name = "<aqua>보호권", icon = Material.PAPER, symbol = "✚")
        val section = YamlConfiguration().createSection("보호권")
        made.save(section)
        assertEquals(made, com.inmc.customitems.item.TypeDef.load("보호권", section))
        assertTrue(!made.builtin)

        val renamed = com.inmc.customitems.item.TypeDef("weapon", ItemType.WEAPON, name = "병기")
        val builtin = YamlConfiguration().createSection("weapon")
        renamed.save(builtin)
        assertTrue(!builtin.contains("base"), "기본 종류에 base 가 적히면 안 된다")
        assertEquals(renamed, com.inmc.customitems.item.TypeDef.load("weapon", builtin))
        assertTrue(renamed.builtin)
    }

    @Test
    fun `손에 든 것으로 정한 아이콘(모델째)이 종류·소분류에서 왕복한다`() {
        val look = kr.inmc.core.item.StoredItem(kr.inmc.core.item.ItemRef.Namespaced("inmc", "보호권_아이콘"), Material.PAPER, displayName = "아이콘")
        val type = com.inmc.customitems.item.TypeDef("보호권", ItemType.CONSUMABLE, icon = Material.PAPER, iconItem = look)
        val typeSection = YamlConfiguration().createSection("보호권")
        type.save(typeSection)
        assertEquals(type, com.inmc.customitems.item.TypeDef.load("보호권", typeSection))

        val category = com.inmc.customitems.item.Category("강화석", "consumable", icon = Material.PAPER, iconItem = look)
        val categorySection = YamlConfiguration().createSection("강화석")
        category.save(categorySection)
        assertEquals(category, com.inmc.customitems.item.Category.load("강화석", categorySection))

        val plain = com.inmc.customitems.item.Category("일반", "consumable")
        val plainSection = YamlConfiguration().createSection("일반")
        plain.save(plainSection)
        assertTrue(!plainSection.contains("icon-item"), "재질만 정한 아이콘은 한 줄로 남아야 한다")
    }

    @Test
    fun `기준을 모르는 만든 종류는 읽지 않는다`() {
        val section = YamlConfiguration().createSection("x")
        section.set("base", "없는종류")
        assertNull(com.inmc.customitems.item.TypeDef.load("x", section))
    }
}
