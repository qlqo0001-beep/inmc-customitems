package com.inmc.customitems.block

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.BlockKind
import com.inmc.customitems.item.BlockSpec
import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.ItemBuilder
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.io.File
import java.util.zip.ZipFile

/**
 * 커스텀 블록을 놓고, 알아보고, 치운다 — 세 방식([BlockKind]) 모두. 리스너(`BlockListener`)와 core 공급처(`InmcItemProvider` → 랜덤박스
 * 상자 모양)가 여기를 지난다.
 *
 * **알아보는 법이 방식마다 다르다.**
 * - 소리블록([BlockKind.SOLID]): 상태만 본다. 갱신 끄기를 켜면 사람이 놓는 소리블록은 늘 harp 라 겹칠 일이 없고, 옛 IA 가 놓은 블록도 그대로 알아본다.
 * - 후렴초([BlockKind.TRANSPARENT]): 상태 + **우리가 놓았다는 표시**(청크 PDC `inmc:blocks`). 후렴초는 엔드와 후렴초 농장에서 저절로 자라
 *   아무 상태나 되므로 상태만 믿으면 농장이 상자 아이템을 찍어 낸다. 표시가 없으면 **아무것에도 붙지 않은 것**(옛 IA 가 놓은 블록)만 우리 것으로 친다 —
 *   저절로 자란 후렴초는 늘 줄기·꽃·아래의 엔드 돌에 붙어 있다.
 * - 엔티티([BlockKind.ENTITY]): 그 자리의 방벽 + 우리 표시(`inmc:block` = 아이템 id)가 붙은 ItemDisplay.
 */
class CustomBlocks(private val custom: CustomItems) {

    /** `sources/` 의 팩이 이미 쓰는 커스텀 상태(열쇠 → 모델). 새 블록이 피해 가고, 옛 IA 블록을 옮길 때 짝을 찾는다. 워커에서 읽는다. */
    @Volatile
    var sourceStates: Map<BlockKind, Map<String, String>> = emptyMap()
        private set

    /**
     * Paper 의 갱신 끄기(`block-updates.disable-noteblock-updates` · `disable-chorus-plant-updates`). 켜져 있어야 블록 상태 방식이 모양을
     * 지킨다. 서버 설정을 읽지 못하면 null(Paper 계열이 아님).
     */
    val updatesDisabled: Map<BlockKind, Boolean>? by lazy { readPaperFlags() }

    /** 켜질 때·리로드 때(워커). */
    fun load() {
        custom.io.async({ readSources() }) { states ->
            sourceStates = states
            warnIfUnsafe()
        }
    }

    /** 블록 상태 방식 블록이 있는데 Paper 의 갱신 끄기가 꺼져 있으면 한 번 알린다. */
    private fun warnIfUnsafe() {
        val flags = updatesDisabled ?: return
        for (kind in listOf(BlockKind.SOLID, BlockKind.TRANSPARENT)) {
            if (flags[kind] == true || custom.items.blockStatesOf(kind).isEmpty()) continue
            custom.logger.warning(
                kind.label + " 커스텀 블록이 있는데 config/paper-global.yml 의 block-updates." + flagName(kind) +
                    " 가 false 입니다 — 옆 블록이 바뀌면 모양이 풀립니다. true 로 바꾸고 재시작하세요.",
            )
        }
    }

    fun flagName(kind: BlockKind): String = if (kind == BlockKind.SOLID) "disable-noteblock-updates" else "disable-chorus-plant-updates"

    /**
     * [forId] 아이템에 내줄 빈 상태. 이미 그 방식의 상태를 들고 있으면 그대로(바꾸면 놓인 블록이 다른 블록으로 보인다).
     * 우리 아이템과 `sources/` 의 팩이 쓰는 상태를 피한다. 다 찼으면 null.
     */
    fun allocate(kind: BlockKind, forId: String): String? {
        custom.items.get(forId)?.block?.takeIf { it.kind == kind && it.state.isNotBlank() }?.let { return it.state }
        val taken = custom.items.blockStatesOf(kind) + sourceStates[kind]?.keys.orEmpty()
        return BlockStates.firstFree(kind, taken)
    }

    // --- 알아보기 --------------------------------------------------------------------

    /** 이 자리의 우리 블록. */
    fun at(block: Block): CustomItem? {
        if (!custom.items.hasBlocks) return null
        return when (block.type) {
            Material.NOTE_BLOCK -> stateItem(BlockKind.SOLID, block)
            Material.CHORUS_PLANT -> stateItem(BlockKind.TRANSPARENT, block)?.takeIf { isMarked(block) || isIsolated(block) }
            Material.BARRIER -> display(block)?.let { custom.items.get(it.persistentDataContainer.get(DISPLAY_KEY, PersistentDataType.STRING)) }
            else -> null
        }
    }

    private fun stateItem(kind: BlockKind, block: Block): CustomItem? =
        BlockStates.keyOf(kind, block.blockData.asString)?.let { custom.items.byBlockState(kind, it) }

    /** 저절로 자란 후렴초가 붙는 것(줄기·꽃, 아래의 엔드 돌)에 하나도 안 붙어 있는가. */
    private fun isIsolated(block: Block): Boolean = FACES.none { face ->
        val type = block.getRelative(face).type
        type == Material.CHORUS_PLANT || type == Material.CHORUS_FLOWER || (face == BlockFace.DOWN && type == Material.END_STONE)
    }

    private fun display(block: Block): ItemDisplay? = displays(block).firstOrNull()

    private fun displays(block: Block): List<ItemDisplay> =
        block.world.getNearbyEntitiesByType(ItemDisplay::class.java, block.location.add(0.5, 0.5, 0.5), 0.5).filter {
            it.persistentDataContainer.has(DISPLAY_KEY, PersistentDataType.STRING) &&
                it.location.blockX == block.x && it.location.blockY == block.y && it.location.blockZ == block.z
        }

    // --- 놓기·치우기 ------------------------------------------------------------------

    /** [item] 을 [block] 자리에 놓는다. 블록이 아니거나 상태를 못 만들면 false(자리는 그대로). */
    fun place(block: Block, item: CustomItem): Boolean {
        val spec = item.block ?: return false
        when (spec.kind) {
            BlockKind.SOLID, BlockKind.TRANSPARENT -> {
                if (spec.state.isBlank()) return false
                val data = runCatching { Bukkit.createBlockData(BlockStates.blockData(spec.kind, spec.state)) }.getOrElse {
                    custom.logger.warning("블록 '${item.id}' 의 상태를 만들 수 없습니다(${spec.state}): ${it.message}")
                    return false
                }
                clear(block)
                // applyPhysics = false: 이웃 갱신 없이. 갱신 끄기가 꺼진 서버에서도 놓는 순간 모양이 풀리지 않게.
                block.setBlockData(data, false)
                if (spec.kind == BlockKind.TRANSPARENT) mark(block)
            }
            BlockKind.ENTITY -> {
                clear(block)
                block.setType(Material.BARRIER, false)
                val stack = custom.items.create(item.id) ?: ItemStack(item.material)
                block.world.spawn(block.location.add(0.5, 0.5, 0.5), ItemDisplay::class.java) { display ->
                    display.setItemStack(stack)
                    // 변형 없이 — 블록 모델(0~16)이 그 칸을 꼭 채운다.
                    display.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.NONE
                    display.isPersistent = true
                    display.persistentDataContainer.set(DISPLAY_KEY, PersistentDataType.STRING, item.id)
                }
            }
        }
        return true
    }

    /** 이 자리에 우리가 둔 곁것(엔티티 모델·후렴초 표시)을 치운다. 블록은 부르는 쪽이 정한다. 무언가 치웠으면 true. */
    fun clear(block: Block): Boolean {
        val displays = displays(block)
        for (display in displays) display.remove()
        return unmark(block) or displays.isNotEmpty()
    }

    /** 부서진 우리 블록이 떨굴 것 — 부수면 나오게 해 둔 것만. */
    fun dropOf(item: CustomItem): ItemStack? =
        if (item.block?.drop == true) custom.items.create(item.id) else null

    // --- 후렴초 표시 (청크 PDC) --------------------------------------------------------

    private fun packed(block: Block): Int = ((block.y + 2048) shl 8) or ((block.x and 15) shl 4) or (block.z and 15)

    private fun isMarked(block: Block): Boolean =
        block.chunk.persistentDataContainer.get(CHUNK_KEY, PersistentDataType.INTEGER_ARRAY)?.contains(packed(block)) == true

    private fun mark(block: Block) {
        val pdc = block.chunk.persistentDataContainer
        val current = pdc.get(CHUNK_KEY, PersistentDataType.INTEGER_ARRAY) ?: IntArray(0)
        val position = packed(block)
        if (position !in current) pdc.set(CHUNK_KEY, PersistentDataType.INTEGER_ARRAY, current + position)
    }

    private fun unmark(block: Block): Boolean {
        val pdc = block.chunk.persistentDataContainer
        val current = pdc.get(CHUNK_KEY, PersistentDataType.INTEGER_ARRAY) ?: return false
        val position = packed(block)
        if (position !in current) return false
        val rest = current.filter { it != position }.toIntArray()
        if (rest.isEmpty()) pdc.remove(CHUNK_KEY) else pdc.set(CHUNK_KEY, PersistentDataType.INTEGER_ARRAY, rest)
        return true
    }

    // --- 옛 ItemsAdder 블록 옮기기 -------------------------------------------------------

    data class ImportReport(val added: List<String>, val skipped: List<String>, val noState: List<String>, val unsupported: List<String>, val contentsFound: Boolean)

    /**
     * `plugins/ItemsAdder/contents` 의 블록 정의(REAL_NOTE·REAL_TRANSPARENT)를 우리 아이템으로 — 상태는 `sources/` 의 팩이 그 모델에 준 것
     * 그대로라 **이미 놓인 옛 블록이 그 아이템으로 이어진다.** 같은 id 가 있으면 건너뛴다(여러 번 눌러도 된다). 재질은 PAPER — 옛 재질
     * (다이아몬드·에메랄드 …)로 두면 바닐라에서 그 재료로 쓰인다.
     */
    fun importItemsAdder(then: (ImportReport) -> Unit) {
        val contents = File(custom.plugin.dataFolder.parentFile, "ItemsAdder/contents")
        custom.io.async({ readSources().let { it to ItemsAdderBlocks.scan(contents, it) } }) { (states, scan) ->
            sourceStates = states
            val added = ArrayList<String>()
            val skipped = ArrayList<String>()
            for (found in scan.found) {
                when {
                    !kr.inmc.core.store.DefinitionKey.isValid(found.id) -> skipped += found.id + " (이름 규칙)"
                    custom.items.exists(found.id) -> skipped += found.id + " (이미 있음)"
                    custom.items.byBlockState(found.kind, found.state) != null -> skipped += found.id + " (상태를 다른 아이템이 씀)"
                    else -> {
                        custom.items.put(
                            CustomItem(
                                id = found.id,
                                material = Material.PAPER,
                                displayName = found.name,
                                lore = found.lore,
                                model = found.model,
                                block = BlockSpec(found.kind, found.state, found.drop),
                            ),
                        )
                        added += found.id
                    }
                }
            }
            then(ImportReport(added, skipped, scan.noState, scan.unsupported, contents.isDirectory))
        }
    }

    // --- 읽기 (워커) ----------------------------------------------------------------------

    /** `sources/` 의 팩들에서 블록 상태 표를 읽는다. 이름 순, 나중 것이 이긴다(팩 합치기와 같다). */
    private fun readSources(): Map<BlockKind, Map<String, String>> {
        val result = HashMap<BlockKind, MutableMap<String, String>>()
        val sources = custom.pack.sourcesDir.listFiles()
            ?.filter { it.isDirectory || it.name.endsWith(".zip", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
        for (source in sources) for (kind in listOf(BlockKind.SOLID, BlockKind.TRANSPARENT)) {
            val json = runCatching { readEntry(source, BlockStates.path(kind)) }.getOrNull() ?: continue
            result.getOrPut(kind) { LinkedHashMap() }.putAll(BlockStates.mappings(kind, json))
        }
        return result
    }

    /** 팩 안의 한 파일. 폴더 하나에 싸인 팩(`resourcepack/assets/…`)도 읽는다. */
    private fun readEntry(source: File, path: String): String? {
        if (source.isDirectory) {
            File(source, path).takeIf { it.isFile }?.let { return it.readText(Charsets.UTF_8) }
            return source.listFiles()?.filter { it.isDirectory }?.firstNotNullOfOrNull { File(it, path).takeIf(File::isFile) }?.readText(Charsets.UTF_8)
        }
        ZipFile(source).use { zip ->
            val entry = zip.getEntry(path)
                ?: zip.entries().asSequence().firstOrNull { !it.isDirectory && it.name.endsWith("/$path") && it.name.count { c -> c == '/' } == path.count { c -> c == '/' } + 1 }
                ?: return null
            return zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
        }
    }

    /** 실행 중인 Paper 설정에서 읽는다(파일 위치가 서버마다 달라도 맞다). Paper 계열이 아니면 null. */
    private fun readPaperFlags(): Map<BlockKind, Boolean>? = runCatching {
        val global = Class.forName("io.papermc.paper.configuration.GlobalConfiguration").getMethod("get").invoke(null)
        val updates = global.javaClass.getField("blockUpdates").get(global)
        mapOf(
            BlockKind.SOLID to updates.javaClass.getField("disableNoteblockUpdates").getBoolean(updates),
            BlockKind.TRANSPARENT to updates.javaClass.getField("disableChorusPlantUpdates").getBoolean(updates),
        )
    }.getOrNull()

    @Suppress("DEPRECATION")
    companion object {
        /** 엔티티 방식 모델에 찍는 아이템 id. */
        val DISPLAY_KEY = NamespacedKey(ItemBuilder.NAMESPACE, "block")

        /** 청크에 적는 "우리가 놓은 후렴초" 자리들. */
        val CHUNK_KEY = NamespacedKey(ItemBuilder.NAMESPACE, "blocks")

        private val FACES = listOf(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)
    }
}
