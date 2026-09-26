package com.inmc.customitems

import com.inmc.customitems.item.Components
import com.inmc.customitems.item.CustomItem
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.EquipmentSlotGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** 1.21 부품. */
class ComponentsTest {

    private fun roundTrip(item: CustomItem): CustomItem? {
        val yaml = YamlConfiguration()
        item.save(yaml.createSection(item.id))
        val reread = YamlConfiguration().apply { loadFromString(yaml.saveToString()) }
        return CustomItem.load(item.id, reread.getConfigurationSection(item.id)!!)
    }

    @Test
    fun `부품이 저장해도 그대로 읽히고 비어 있으면 적지 않는다`() {
        val hat = CustomItem(
            "hat", Material.PAPER,
            components = Components(
                maxStack = 16, tooltipStyle = "inmc:legend", hideTooltip = true, glider = true, fireResistant = true,
                equipSlot = "head", color = "#FF8800", trimPattern = "sentry", trimMaterial = "gold", skull = "eyJ0ZXh0dXJlcyI6e319",
            ),
        )
        assertEquals(hat, roundTrip(hat))

        val plain = YamlConfiguration()
        CustomItem("stick", Material.STICK).save(plain.createSection("stick"))
        assertFalse(plain.contains("stick.components"))
    }

    @Test
    fun `모르는 입는 칸과 범위 밖 겹침은 읽을 때 바로잡는다`() {
        val yaml = YamlConfiguration().apply { loadFromString("components:\n  equip-slot: tail\n  max-stack: 500\n") }
        val parts = Components.load(yaml.getConfigurationSection("components"))
        assertEquals("", parts.equipSlot)
        assertEquals(Components.MAX_STACK, parts.maxStack)
    }

    @Test
    fun `입는 칸을 정하면 능력치도 그 칸에서 돈다`() {
        // 종이 모자 — 재질만 보면 손에 든 것이지만 머리에 쓴다.
        assertEquals(EquipmentSlotGroup.HEAD, CustomItem("hat", Material.PAPER, components = Components(equipSlot = "head")).slotGroup())
        assertEquals(EquipmentSlotGroup.MAINHAND, CustomItem("paper", Material.PAPER).slotGroup())
    }

    @Test
    fun `색은 샵이 있어도 없어도 읽고 모양이 틀리면 null`() {
        assertEquals(0xFF8800, Components.parseColor("#FF8800"))
        assertEquals(0x00AA11, Components.parseColor("00aa11"))
        assertNull(Components.parseColor("#FFF"))
        assertNull(Components.parseColor("빨강"))
    }
}
