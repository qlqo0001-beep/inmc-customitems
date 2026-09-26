package com.inmc.customitems.pack

import com.inmc.customitems.CustomItems
import com.inmc.customitems.block.BlockStates
import com.inmc.customitems.item.BlockKind
import com.inmc.customitems.item.CustomItem
import java.io.File

/**
 * 리소스팩을 만들어 낸다.
 *
 * ```
 * plugins/inmc-customitems/pack/
 *   sources/          ← 합칠 팩들. zip 이든 폴더든
 *     10-base.zip
 *     20-extra/
 *   textures/         ← 아이템에 붙일 png
 *     boss_sword.png
 *   models/           ← 직접 만든 모델 json (선택)
 *   output/
 *     pack.zip
 *     pack.sha1
 * ```
 *
 * **우선순위: 우리가 만든 것이 맨 앞, `sources/` 는 이름 순으로 그 뒤.** 나중이 이기므로
 * 관리자가 팩을 통째로 덮어쓰고 싶으면 `sources/` 에 넣으면 된다. `10-` `20-` 처럼 번호를
 * 붙이는 것이 관례다.
 *
 * **json 은 덮지 않고 섞는다** ([JsonMerge]). 이게 이 기능의 핵심이다 — 덮으면 두 팩 중
 * 하나가 조용히 사라진다.
 *
 * 파일 작업은 전부 **워커 스레드**에서 돈다. 팩 하나가 수천 개 파일일 수 있고, 그걸 메인에서
 * 압축하면 서버가 몇 초씩 멈춘다.
 */
class PackService(private val custom: CustomItems) {

    /** 마지막으로 만든 팩의 sha1. 배포할 때 클라이언트에 넘긴다. */
    @Volatile
    var sha1: String = ""
        private set

    @Volatile
    var lastReport: Result? = null
        private set

    /** 지금 만드는 중인지. 두 번 겹쳐 돌면 출력 파일이 반쯤 쓰인 상태가 된다. */
    @Volatile
    private var building = false

    val isBuilding: Boolean get() = building

    data class Result(
        val fileCount: Int,
        val sourceCount: Int,
        val itemCount: Int,
        val mergedJsonCount: Int,
        val replacedCount: Int,
        val missingTextures: List<String>,
        /** 번호 방식으로도 나간 아이템 수. */
        val legacyCount: Int,
        /** 번호를 적었지만 붙이지 못한 것들. 바닐라 모델을 모르는 재질이다. */
        val legacySkipped: List<String>,
        /** 블록 상태 방식으로 팩에 적은 우리 블록 수. */
        val blockCount: Int,
        /** 블록 상태 방식인데 모델도 텍스처도 없어 그릴 것이 없는 것. */
        val blocksWithoutModel: List<String>,
        val sha1: String,
        val sizeBytes: Long,
        val millis: Long,
        val error: String? = null,
    ) {
        val ok: Boolean get() = error == null
    }

    val root: File get() = custom.io.file("pack")
    val sourcesDir: File get() = File(root, "sources")
    val texturesDir: File get() = File(root, "textures")
    val modelsDir: File get() = File(root, "models")
    val outputDir: File get() = File(root, "output")
    val outputZip: File get() = File(outputDir, "pack.zip")

    /**
     * 폴더 구조를 만들어 둔다. 관리자가 어디에 넣어야 하는지 알 수 있어야 한다.
     *
     * **처음 깔릴 때만** 서버 기본 팩을 `sources/` 에 넣는다 — 관리자가 지운 것이 되살아나면 안 된다.
     */
    fun prepare() {
        val fresh = !root.exists()
        for (dir in listOf(sourcesDir, texturesDir, modelsDir, outputDir)) dir.mkdirs()
        if (fresh) {
            custom.plugin.getResource(DEFAULT_PACK)?.use { input ->
                File(sourcesDir, DEFAULT_PACK_NAME).outputStream().use { input.copyTo(it) }
            }
        }
        val readme = File(root, "읽어보세요.txt")
        if (!readme.exists()) readme.writeText(README, Charsets.UTF_8)
    }

    /**
     * 팩을 만든다. **워커에서 돌고 결과만 메인으로 돌아온다.**
     *
     * @param then 메인 스레드에서 불린다.
     */
    /**
     * 번호(custom_model_data)로 그려지는 모델 목록 — `sources/` 의 팩마다 따로 읽어 어느 팩 것인지 적고, 만든 팩(`output`)에만 있는
     * 것은 [GENERATED](우리가 만든 번호 방식)로. 워커에서 읽는다(팩이 수십 MB 일 수 있다).
     */
    fun modelNumbers(then: (List<ModelNumbers.Entry>) -> Unit) {
        custom.io.async({
            val found = ArrayList<ModelNumbers.Entry>()
            val sources = sourcesDir.listFiles()
                ?.filter { it.isDirectory || it.name.endsWith(".zip", ignoreCase = true) }
                ?.sortedBy { it.name.lowercase() }
                .orEmpty()
            for (source in sources) {
                val merger = PackMerger()
                runCatching { merger.add(source.name, source) }.onFailure { custom.logger.warning("팩 '${source.name}' 을(를) 읽지 못했습니다: ${it.message}") }
                found += ModelNumbers.scan(source.name, merger.entries())
            }
            if (outputZip.isFile) {
                val merger = PackMerger()
                merger.add(GENERATED, outputZip)
                val seen = found.map { it.material to it.number }.toSet()
                found += ModelNumbers.scan(GENERATED, merger.entries()).filter { (it.material to it.number) !in seen }
            }
            found.sortedWith(compareBy({ it.material }, { it.number }))
        }, then)
    }

    fun build(then: (Result) -> Unit) {
        if (building) {
            then(failed("이미 만드는 중입니다"))
            return
        }
        building = true

        // 아이템 목록은 **메인에서** 스냅샷으로 뜬다. 워커에서 레지스트리를 훑으면 그 사이
        // 관리자가 GUI 로 고친 것과 섞인다.
        // 강화표도 같이 뜬다 — 단계별 모양이 있는 아이템은 기본 모양이 없어도 넣는다.
        val tables = custom.items.all().associate { it.id to it.upgrade.table(custom.items.lookup)?.takeIf { table -> table.hasLevelModels } }
        val items = custom.items.all().filter { PackAssets.needsPack(it) || tables[it.id] != null }
        val blockItems = custom.items.all().filter { it.block?.kind?.usesState == true }

        custom.io.async({ runCatching { assemble(items, tables, blockItems) } }) { outcome ->
            building = false
            val result = outcome.getOrElse { failed(it.message ?: it.javaClass.simpleName) }
            if (result.ok) sha1 = result.sha1
            lastReport = result
            then(result)
        }
    }

    /** 저장된 sha1 을 읽어 온다. 재시작해도 배포가 이어지게. */
    fun loadSha1() {
        val file = File(outputDir, "pack.sha1")
        if (file.isFile) sha1 = file.readText(Charsets.UTF_8).trim()
    }

    // --- 실제 작업 (워커 스레드) ---------------------------------------------------------

    /** 강화 단계가 모양을 바꾸면 그 단계의 모델(`<이름>_lv<단계>`). 공용 방식이면 그 방식을 쓰는 아이템마다 만든다. */
    private fun levelModels(merger: PackMerger, missing: MutableList<String>, item: CustomItem, table: com.inmc.customitems.item.UpgradeTable?) {
        if (table == null) return
        for (level in 1..table.maxLevel) {
            val step = table.step(level) ?: continue
            if (step.texture.isBlank() && step.model.isBlank()) continue
            val id = PackAssets.levelId(item, level)
            val levelModel = if (step.model.isNotBlank()) {
                if (':' in step.model) step.model else PackAssets.NAMESPACE + ":" + step.model
            } else {
                val png = File(texturesDir, step.texture)
                if (!png.isFile) {
                    missing += item.id + " +" + level + " → " + step.texture
                    continue
                }
                merger.put(GENERATED, PackAssets.texturePath(id), png.readBytes())
                merger.put(GENERATED, PackAssets.modelPath(id), PackAssets.modelJson(id).toByteArray())
                PackAssets.NAMESPACE + ":item/" + id
            }
            merger.put(GENERATED, PackAssets.itemPath(id), PackAssets.itemJson(levelModel).toByteArray())
        }
    }

    private fun assemble(items: List<CustomItem>, tables: Map<String, com.inmc.customitems.item.UpgradeTable?>, blockItems: List<CustomItem>): Result {
        val started = System.currentTimeMillis()
        prepare()

        val merger = PackMerger()
        val missing = ArrayList<String>()

        // 1. 우리가 만드는 것이 **맨 앞**이다 — 우선순위가 가장 낮다.
        //    관리자가 sources/ 에 넣은 팩이 이길 수 있어야 한다.
        merger.put(GENERATED, "pack.mcmeta", PackAssets.mcmetaJson(description()).toByteArray())

        for (item in items) {
            levelModels(merger, missing, item, tables[item.id])
            if (!PackAssets.needsPack(item)) continue
            // 텍스처만 적은 블록은 평면 아이콘이 아니라 정육면체 — 놓인 모습과 가방 속 모습이 같다.
            val cube = PackAssets.isCube(item)
            val model = if (cube) PackAssets.blockModelFor(item)!! else PackAssets.modelNameFor(item)

            if (item.texture.isNotBlank()) {
                val png = File(texturesDir, item.texture)
                if (png.isFile) {
                    merger.put(GENERATED, PackAssets.texturePath(item.resourceId), png.readBytes())
                    // 모델을 직접 적었으면 우리가 만들지 않는다.
                    if (cube) {
                        merger.put(GENERATED, PackAssets.blockModelPath(item.resourceId), PackAssets.blockModelJson(item.resourceId).toByteArray())
                    } else if (item.model.isBlank()) {
                        merger.put(
                            GENERATED,
                            PackAssets.modelPath(item.resourceId),
                            PackAssets.modelJson(item.resourceId).toByteArray(),
                        )
                    }
                } else {
                    missing += item.id + " → " + item.texture
                }
            }

            // 직접 만든 모델 json 이 models/ 에 있으면 같이 넣는다.
            if (item.model.isNotBlank()) {
                val json = File(modelsDir, item.model.substringAfterLast('/') + ".json")
                if (json.isFile) {
                    merger.put(
                        GENERATED,
                        "assets/${PackAssets.NAMESPACE}/models/${item.model}.json",
                        json.readBytes(),
                    )
                }
            }

            merger.put(GENERATED, PackAssets.itemPath(item.resourceId), PackAssets.itemJson(model).toByteArray())
        }

        // 2. 관리자가 넣어둔 바닐라 대체 모델. 번호 방식이 여기에 얹힌다.
        val baseDir = File(modelsDir, "base")
        if (baseDir.isDirectory) merger.add(BASE, baseDir)

        // 3. sources/ 를 이름 순으로. 나중이 이긴다.
        val sources = sourcesDir.listFiles()
            ?.filter { it.isDirectory || it.name.endsWith(".zip", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
        for (source in sources) merger.add(source.name, source)

        // 3-1. 블록 상태. 소스를 다 읽은 뒤에 **통째로** 쓴다 — 소스가 쓰던 커스텀 상태도 읽어 와 같이 적는다([BlockStates]).
        val blocks = addBlockStates(merger, blockItems)

        // 4. 번호 방식. **소스를 다 읽은 뒤에** 얹는다 — 남의 팩이 준 바닐라 모델 위에
        //    우리 오버라이드가 얹혀야 하고, 순서가 반대면 우리 것이 덮인다.
        val legacy = addLegacyOverrides(merger, items)

        // 5. 압축해서 내보낸다.
        val report = merger.result(sources.size)
        outputDir.mkdirs()
        PackZip.write(outputZip, merger.entries())

        val hash = PackZip.sha1(outputZip)
        File(outputDir, "pack.sha1").writeText(hash, Charsets.UTF_8)

        return Result(
            fileCount = report.fileCount,
            sourceCount = sources.size,
            itemCount = items.size,
            mergedJsonCount = report.mergedJsonCount,
            replacedCount = report.replacedCount,
            missingTextures = missing,
            legacyCount = legacy.added,
            legacySkipped = legacy.skipped,
            blockCount = blocks.first,
            blocksWithoutModel = blocks.second,
            sha1 = hash,
            sizeBytes = outputZip.length(),
            millis = System.currentTimeMillis() - started,
        )
    }

    private class LegacyResult(val added: Int, val skipped: List<String>)

    /**
     * 소리블록·후렴초의 blockstates 를 새로 쓴다. 커스텀 상태가 하나도 없으면(우리 것도 소스 것도) 손대지 않는다 — 바닐라 파일 그대로.
     * @return (적은 우리 블록 수, 그릴 모델이 없는 블록)
     */
    private fun addBlockStates(merger: PackMerger, blockItems: List<CustomItem>): Pair<Int, List<String>> {
        var count = 0
        val missing = ArrayList<String>()
        for (kind in listOf(BlockKind.SOLID, BlockKind.TRANSPARENT)) {
            val ours = LinkedHashMap<String, String>()
            for (item in blockItems) {
                val spec = item.block ?: continue
                if (spec.kind != kind || spec.state.isBlank()) continue
                val model = PackAssets.blockModelFor(item)
                if (model == null) missing += item.id else ours[spec.state] = model
            }
            val path = BlockStates.path(kind)
            val all = BlockStates.mappings(kind, merger.entries()[path]?.toString(Charsets.UTF_8)) + ours
            if (all.isEmpty()) continue
            merger.replace(BLOCKS, path, BlockStates.json(kind, all).toByteArray(Charsets.UTF_8))
            count += ours.size
        }
        return count to missing
    }

    /**
     * 번호(`custom_model_data`)를 적은 아이템에 오버라이드를 붙인다.
     *
     * **번호를 적는 것 자체가 opt-in 이다.** 번호 방식은 바닐라 아이템의 모델 파일을
     * 대체하므로 — `models/item/diamond_sword.json` 을 넣으면 서버의 **모든** 다이아몬드
     * 검이 그 파일대로 그려진다 — 아무도 안 부탁했는데 건드리면 안 된다.
     *
     * 바닐라 모델이 필요한데 없으면 **붙이지 않고 보고한다.** 짐작해서 쓰면 우리 아이템이
     * 아니라 평범한 그 아이템이 망가진다.
     */
    private fun addLegacyOverrides(merger: PackMerger, items: List<CustomItem>): LegacyResult {
        var added = 0
        val skipped = ArrayList<String>()

        for (item in items) {
            if (item.customModelData <= 0) continue

            val basePath = LegacyModels.basePath(item.material)
            val hasBase = merger.entries().containsKey(basePath)

            // 남이 준 바닐라 모델이 있으면 그 위에 얹는다. 없으면 우리가 아는 재질일 때만 만든다.
            if (!hasBase) {
                if (!LegacyModels.isKnown(item.material)) {
                    skipped += item.id + " (" + item.material.name + ")"
                    continue
                }
                merger.put(LEGACY, basePath, LegacyModels.baseJson(item.material).toByteArray())
            }

            merger.put(
                LEGACY,
                basePath,
                LegacyModels.overrideJson(
                    item.customModelData,
                    PackAssets.modelNameFor(item),
                ).toByteArray(),
            )
            added++
        }
        return LegacyResult(added, skipped)
    }

    /**
     * 그 재질에서 이미 쓰인 번호들. 자동 배정이 피해 가야 하는 것들이다.
     *
     * **메인 스레드에서 부른다** — 레지스트리를 훑는다.
     */
    fun usedNumbers(material: org.bukkit.Material): Set<Int> =
        custom.items.all()
            .filter { it.material == material && it.customModelData > 0 }
            .map { it.customModelData }
            .toSet()

    private fun description(): String =
        custom.packConfig.description.ifBlank { "INMC 커스텀 아이템" }

    private fun failed(message: String) =
        Result(0, 0, 0, 0, 0, emptyList(), 0, emptyList(), 0, emptyList(), "", 0L, 0L, error = message)

    private companion object {
        const val GENERATED = "(자동 생성)"
        const val BASE = "(pack/models/base)"
        const val LEGACY = "(번호 방식)"
        const val BLOCKS = "(블록 상태)"

        /** jar 안의 서버 기본 팩(ItemsAdder 가 만든 것 — 옮겨 온 MMOItems 아이템의 모델 번호가 여기 있다). */
        const val DEFAULT_PACK = "pack/default-pack.zip"
        const val DEFAULT_PACK_NAME = "10-inmc.zip"

        val README = """
            INMC 커스텀아이템 — 리소스팩
            ============================================================

            [sources/]
              합칠 리소스팩을 넣습니다. zip 그대로 넣어도 되고 푼 폴더도 됩니다.
              이름 순으로 읽고 **나중 것이 이깁니다.** 10- 20- 처럼 번호를 붙이세요.
              처음 설치할 때 서버 기본 팩을 10-inmc.zip 으로 넣어 둡니다. 필요 없으면 지우세요.

              다만 json 은 덮지 않고 **섞습니다**:
                - lang/*.json, sounds.json  → 키 단위로 합칩니다
                - atlases/*.json            → sources 배열을 잇습니다
                - overrides 가 있는 모델     → 배열을 잇고 번호 순으로 정렬합니다
                - pack.mcmeta               → 형식 번호는 가장 높은 것, 오버레이 목록은 잇습니다
              덮어버리면 두 팩 중 하나가 조용히 사라지기 때문입니다.
              폴더째 압축한 팩(맨 위 폴더 안에 pack.mcmeta)은 그 폴더를 벗겨 읽습니다.
              셰이더는 섞을 수 없습니다 — 셰이더를 쓰는 팩(BetterHud 등)은 뒤 번호로 두세요.

            [textures/]
              아이템에 붙일 png 를 넣습니다. 예: boss_sword.png
              아이템 설정 화면의 "텍스처" 칸에 그 파일 이름을 적으면 됩니다.

            [models/]
              직접 만든 모델 json 을 넣습니다 (선택).
              평면 아이콘이 아니라 손에 든 모양이 따로 있어야 할 때 씁니다.

            [models/base/]
              번호 방식(custom_model_data)을 쓸 때만 필요합니다.
              번호는 바닐라 아이템의 모델 파일을 대체하므로, 그 바닐라 모델을
              우리가 모르는 재질이면 여기에 넣어 주세요.
                assets/minecraft/models/item/<재질>.json
              sources/ 의 팩이 그 파일을 준다면 안 넣어도 됩니다.

            [output/]
              만들어진 pack.zip 과 pack.sha1 이 여기 생깁니다. 손대지 마세요.

            만들기:  /커스텀아이템 리팩 빌드
            ============================================================
        """.trimIndent()
    }
}
