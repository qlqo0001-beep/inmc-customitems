package com.inmc.customitems

import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.UpgradeMode
import com.inmc.customitems.item.UpgradeStep
import com.inmc.customitems.item.UpgradeTable
import com.inmc.customitems.pack.PackSync
import org.bukkit.Material
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** 켜질 때 팩이 아이템과 맞는지 보는 판단(사용자 요청 2026-10-14). */
class PackSyncTest {

    private val sword = CustomItem("sword", Material.IRON_SWORD, model = "weapon:weapon/isword")
    private val plain = CustomItem("plain", Material.STONE)
    private val relic = CustomItem("relic", Material.HEART_OF_THE_SEA)
    private val relicTable = UpgradeTable("relic", mode = UpgradeMode.ABSOLUTE, steps = listOf(UpgradeStep(), UpgradeStep(texture = "relic_2.png")))

    @Test
    fun `모양이 있는 아이템과 모양을 바꾸는 강화 단계만 팩에 있어야 한다`() {
        val expected = PackSync.expectedEntries(listOf(sword, plain, relic), mapOf("relic" to relicTable))
        assertEquals(listOf("assets/inmc/items/sword.json", "assets/inmc/items/relic_lv2.json"), expected)
    }

    @Test
    fun `맞으면 까닭이 없다`() {
        val entries = setOf("assets/inmc/items/sword.json")
        assertEquals(emptyList(), PackSync.problems(entries, "abc", "ABC", listOf("assets/inmc/items/sword.json"), 0, "k", "k"))
    }

    @Test
    fun `팩이 없거나 · sha1 이 다르거나 · 모양이 빠졌거나 · 번호가 남았거나 · 재료가 바뀌면 어긋남`() {
        assertEquals(1, PackSync.problems(null, "abc", null, emptyList(), 0, "k", "k").size)
        val problems = PackSync.problems(setOf("x"), "abc", "def", listOf("assets/inmc/items/sword.json"), 3, "old", "new")
        assertEquals(4, problems.size, problems.toString())
        assertTrue(problems.any { "sword" in it }, "빠진 아이템 이름이 보여야 한다")
        assertTrue(PackSync.problems(setOf("x"), "a", "a", emptyList(), 0, null, "k").single().contains("기록이 없"), "이 기능 전에 만든 팩은 한 번 다시 만든다")
    }

    @Test
    fun `파일 지문은 내용으로 — 시각만 바뀌면 같고, 내용이 바뀌면 다르다`() {
        val dir = Files.createTempDirectory("packsync").toFile()
        try {
            val png = dir.resolve("a/b.png").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
            val first = PackSync.filesKey(mapOf("textures" to dir, "missing" to dir.resolve("none")))
            png.setLastModified(png.lastModified() - 100_000)
            assertEquals(first, PackSync.filesKey(mapOf("missing" to dir.resolve("none"), "textures" to dir)), "시각·적은 순서와 상관없이 같아야 한다")
            png.writeBytes(byteArrayOf(1, 2, 4))
            assertNotEquals(first, PackSync.filesKey(mapOf("textures" to dir, "missing" to dir.resolve("none"))))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `재료 지문은 넷 중 하나만 달라도 다르다`() {
        val base = PackSync.inputs("items", "files", "jar", "desc")
        assertEquals(base, PackSync.inputs("items", "files", "jar", "desc"))
        assertNotEquals(base, PackSync.inputs("items2", "files", "jar", "desc"))
        assertNotEquals(base, PackSync.inputs("items", "files", "jar2", "desc"))
        assertNotEquals(PackSync.inputs("ab", "c", "", ""), PackSync.inputs("a", "bc", "", ""), "경계가 섞이면 안 된다")
    }
}
