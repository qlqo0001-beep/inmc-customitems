package com.inmc.customitems.block

import com.inmc.customitems.CustomItems
import com.inmc.customitems.item.CustomItem
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkLoadEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * 커스텀 블록의 월드 생성(2026-10-09) — **처음 만들어지는 청크**(`ChunkLoadEvent.isNewChunk`)에 [OreGen] 대로 광맥을 심는다. 메인 스레드.
 *
 * 이미 만들어진(누가 가 본) 땅에는 안 생긴다 — 바닐라 광석과 같다. 월드 생성이 켜진 블록이 하나도 없으면 사건이 곧바로 돌아간다
 * ([com.inmc.customitems.item.ItemRegistry.generators]). 같은 월드·청크·블록이면 늘 같은 자리(월드 시드로 굴린다).
 * 엔티티 방식은 심지 않는다 — 광맥마다 엔티티가 수십 개 생긴다.
 */
class OreGenerator(private val custom: CustomItems) : Listener {

    private val placedBlocks = ConcurrentHashMap<String, AtomicLong>()
    private val placedChunks = ConcurrentHashMap<String, AtomicLong>()

    @Volatile
    private var failed: String? = null

    @EventHandler(priority = EventPriority.MONITOR)
    fun onChunkLoad(event: ChunkLoadEvent) {
        if (!event.isNewChunk) return
        val ids = custom.items.generators
        if (ids.isEmpty()) return
        val chunk = event.chunk
        val world = chunk.world
        for (id in ids) {
            val item = custom.items.get(id) ?: continue
            val rule = item.block?.generation ?: continue
            if (!rule.inWorld(world.name)) continue
            val positions = OreGen.plan(rule, world.minHeight, world.maxHeight, Random(seed(world.seed, chunk.x, chunk.z, id)))
            if (positions.isEmpty()) continue
            val placed = runCatching { custom.blocks.placeGenerated(chunk, item, positions, rule.replace.toSet()) }.getOrElse {
                val why = "월드 생성 '$id' 실패: ${it.message}"
                if (why != failed) custom.logger.warning(why)
                failed = why
                0
            }
            if (placed > 0) {
                placedBlocks.getOrPut(id) { AtomicLong() }.addAndGet(placed.toLong())
                placedChunks.getOrPut(id) { AtomicLong() }.incrementAndGet()
            }
        }
    }

    /** 이번에 켜진 뒤 [id] 를 심은 (칸 수, 청크 수). */
    fun stats(id: String): Pair<Long, Long> = (placedBlocks[id]?.get() ?: 0L) to (placedChunks[id]?.get() ?: 0L)

    /**
     * 관리자 시험 — [player] 가 선 청크에 [item] 의 광맥을 **확률 없이 한 번** 심는다(월드가 바뀐다). 월드 목록은 보지 않는다.
     * @return 심은 칸 수와 가장 가까운 자리(없으면 null).
     */
    fun plantHere(player: Player, item: CustomItem): Pair<Int, Location?> {
        val rule = item.block?.generation ?: return 0 to null
        val chunk = player.location.chunk
        val world = chunk.world
        val positions = OreGen.plan(rule, world.minHeight, world.maxHeight, Random(System.nanoTime()), force = true)
        val replace = rule.replace.toSet()
        val eligible = positions.filter { (x, y, z) -> chunk.getBlock(x, y, z).type in replace }
        val placed = custom.blocks.placeGenerated(chunk, item, positions, replace)
        val nearest = eligible.map { (x, y, z) -> chunk.getBlock(x, y, z).location }.minByOrNull { it.distanceSquared(player.location) }
        return placed to nearest
    }

    companion object {
        /** 월드·청크·블록마다 늘 같은 굴림 — 같은 시드의 월드면 같은 자리에 생긴다. */
        fun seed(worldSeed: Long, chunkX: Int, chunkZ: Int, id: String): Long =
            worldSeed xor (chunkX.toLong() * 341873128712L) xor (chunkZ.toLong() * 132897987541L) xor (id.hashCode().toLong() shl 16)
    }
}
