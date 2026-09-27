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
        /** 번호 방식에서 옮긴 것 — 메인 스레드가 정의에 적는다([applyMigration]). */
        val migrated: List<Migrated>,
        /** 공용 강화 방식의 번호인데 재질마다 모양이 달라 한 칸에 적을 수 없어 둔 것. */
        val migrationSkipped: List<String>,
        /** 바닐라 아이템 정의에서 걷어 낸 번호 갈래 — 정의 수 · 낡은 `overrides` 수. */
        val strippedDefinitions: Int,
        val strippedOverrides: Int,
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

    /**
     * 번호 하나를 옮긴 것. [level] 이 0 이면 아이템 자신, 아니면 강화 [level] 단계([table] 이 있으면 공용 강화 방식의 단계).
     * [model] 이 null 이면 바닐라 모양으로 그려지던 번호라 번호만 뗀다.
     */
    data class Migrated(val itemId: String, val table: String?, val level: Int, val number: Int, val model: String?)

    val root: File get() = custom.io.file("pack")
    val sourcesDir: File get() = File(root, "sources")
    val texturesDir: File get() = File(root, "textures")
    val modelsDir: File get() = File(root, "models")

    /** 직접 만든 아이템 정의(`<이름>.json`) — 있으면 텍스처·모델 칸 대신 그대로 쓴다. 번호에서 옮긴 조건 있는 모양(던진 낚싯대 …)이 여기 적힌다. */
    val itemsDir: File get() = File(root, "items")
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
        // 낡은 번호가 남은 것 — 이번 빌드가 최신 방식으로 옮긴다(아이템 자신 · 아이템 전용 강화표 · 공용 강화 방식).
        val numbered = custom.items.all().filter { item -> item.customModelData > 0 || item.upgrade.own?.steps?.any { it.customModelData > 0 } == true }
        val shared = custom.upgrades.all().filter { table -> table.steps.any { it.customModelData > 0 } }.associateWith { table ->
            custom.items.all().filter { it.upgrade.own == null && it.upgrade.template.equals(table.id, ignoreCase = true) }
        }

        custom.io.async({ runCatching { assemble(items, tables, blockItems, numbered, shared) } }) { outcome ->
            building = false
            val result = outcome.getOrElse { failed(it.message ?: it.javaClass.simpleName) }
            if (result.ok) {
                sha1 = result.sha1
                applyMigration(result.migrated)
            }
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
            merger.put(GENERATED, PackAssets.itemPath(id), (customDefinition(id) ?: PackAssets.itemJson(levelModel)).toByteArray())
        }
    }

    private fun assemble(
        items: List<CustomItem>,
        tables: Map<String, com.inmc.customitems.item.UpgradeTable?>,
        blockItems: List<CustomItem>,
        numbered: List<CustomItem>,
        shared: Map<com.inmc.customitems.item.UpgradeTable, List<CustomItem>>,
    ): Result {
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
            val rod = PackAssets.isRod(item)
            val model = if (cube) PackAssets.blockModelFor(item)!! else PackAssets.modelNameFor(item)
            // 낚싯대의 던진 모양. 텍스처 옆에 `_cast.png` 가 있을 때만 — 없으면 던져도 같은 모양이다.
            var castModel: String? = null

            if (item.texture.isNotBlank()) {
                val png = File(texturesDir, item.texture)
                if (png.isFile) {
                    merger.put(GENERATED, PackAssets.texturePath(item.resourceId), png.readBytes())
                    // 모델을 직접 적었으면 우리가 만들지 않는다.
                    if (cube) {
                        merger.put(GENERATED, PackAssets.blockModelPath(item.resourceId), PackAssets.blockModelJson(item.resourceId).toByteArray())
                    } else if (rod) {
                        merger.put(GENERATED, PackAssets.modelPath(item.resourceId), PackAssets.rodModelJson(item.resourceId).toByteArray())
                        val castPng = File(texturesDir, PackAssets.castTexture(item.texture))
                        if (castPng.isFile) {
                            val castId = item.resourceId + "_cast"
                            merger.put(GENERATED, PackAssets.texturePath(castId), castPng.readBytes())
                            merger.put(GENERATED, PackAssets.modelPath(castId), PackAssets.rodModelJson(castId).toByteArray())
                            castModel = PackAssets.NAMESPACE + ":item/" + castId
                        }
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

            val definition = customDefinition(item.resourceId) ?: castModel?.let { PackAssets.rodItemJson(model, it) } ?: PackAssets.itemJson(model)
            merger.put(GENERATED, PackAssets.itemPath(item.resourceId), definition.toByteArray())
        }

        // 2. 관리자가 넣어둔 바닐라 대체 모델.
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

        // 4. 낡은 번호 방식 걷어 내기. **소스를 다 읽은 뒤에** — 옮기기는 소스가 그 번호로 무엇을 그리는지 읽어야 하고,
        //    걷어 내기는 소스가 준 바닐라 정의의 번호 갈래를 지운다([NumberMigration]). 옮기기가 먼저다.
        val migration = migrate(merger, numbered, shared)
        val stripped = NumberMigration.strip(merger.entries())
        for ((path, bytes) in stripped.changes) if (bytes == null) merger.remove(path) else merger.replace(NUMBERS, path, bytes)

        // 5. 모델 목록(관리 화면)의 미리보기 — 팩의 모든 모델에 아이템 정의를 붙여 게임에서 그대로 보이게 한다.
        val previews = merger.entries().keys.mapNotNull { PackAssets.listedModel(it) }
        for (model in previews) merger.replace(PREVIEW, PackAssets.previewPath(model), PackAssets.itemJson(model).toByteArray(Charsets.UTF_8))

        // 6. 압축해서 내보낸다.
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
            migrated = migration.first,
            migrationSkipped = migration.second,
            strippedDefinitions = stripped.definitions,
            strippedOverrides = stripped.overrides,
            blockCount = blocks.first,
            blocksWithoutModel = blocks.second,
            sha1 = hash,
            sizeBytes = outputZip.length(),
            millis = System.currentTimeMillis() - started,
        )
    }

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
     * 낡은 번호를 최신 방식으로 옮긴다(워커). 번호가 지금 그려지는 모양([NumberMigration.resolve])을 우리 아이템 정의
     * (`assets/inmc/items/<이름>.json`)로 적고, 무엇을 옮겼는지 돌려준다 — 정의에 적는 것은 메인 스레드([applyMigration]).
     * 텍스처·모델이 이미 있으면 그것이 모양이라 번호만 뗀다.
     *
     * @return (옮긴 것, 공용 강화 방식이라 옮기지 못한 것)
     */
    private fun migrate(
        merger: PackMerger,
        numbered: List<CustomItem>,
        shared: Map<com.inmc.customitems.item.UpgradeTable, List<CustomItem>>,
    ): Pair<List<Migrated>, List<String>> {
        val files = merger.entries()
        val roots = NumberMigration.activeRoots(files)
        fun resolve(item: CustomItem, number: Int) = NumberMigration.resolve(files, item.material.name.lowercase(), number, roots)
        val out = ArrayList<Migrated>()
        val skipped = ArrayList<String>()

        for (item in numbered) {
            if (item.customModelData > 0) {
                val target = if (PackAssets.needsPack(item)) null else resolve(item, item.customModelData)
                target?.let { writeDefinition(merger, item.resourceId, it) }
                out += Migrated(item.id, null, 0, item.customModelData, target?.model)
            }
            for ((index, step) in item.upgrade.own?.steps.orEmpty().withIndex()) {
                if (step.customModelData <= 0) continue
                val level = index + 1
                val target = if (step.texture.isNotBlank() || step.model.isNotBlank()) null else resolve(item, step.customModelData)
                target?.let { writeDefinition(merger, PackAssets.levelId(item, level), it) }
                out += Migrated(item.id, null, level, step.customModelData, target?.model)
            }
        }

        // 공용 강화 방식의 단계는 쓰는 아이템 모두에게 한 모양이어야 한 칸(모델)에 적을 수 있다.
        for ((table, users) in shared) {
            for ((index, step) in table.steps.withIndex()) {
                if (step.customModelData <= 0) continue
                val level = index + 1
                val targets = if (step.texture.isNotBlank() || step.model.isNotBlank()) emptySet() else users.map { resolve(it, step.customModelData) }.toSet()
                if (targets.size > 1) {
                    skipped += table.id + " +" + level + " (재질마다 모양이 다름)"
                    continue
                }
                val target = targets.singleOrNull()
                if (target != null) for (user in users) writeDefinition(merger, PackAssets.levelId(user, level), target)
                out += Migrated("", table.id, level, step.customModelData, target?.model)
            }
        }
        return out to skipped
    }

    /**
     * 만든 팩(`output/pack.zip`)에 들어 있는 모델 목록 — 미리보기 정의가 붙은 것(빌드 5단계). 워커에서 읽는다(팩이 수십 MB 일 수 있다).
     * 아직 만든 적이 없으면 빈 목록.
     */
    fun models(then: (List<String>) -> Unit) {
        custom.io.async({
            if (!outputZip.isFile) return@async emptyList()
            java.util.zip.ZipFile(outputZip).use { zip -> zip.entries().asSequence().mapNotNull { PackAssets.previewModel(it.name) }.sorted().toList() }
        }, then)
    }

    /** 옮긴 모양을 팩에 적는다. 조건이 있는 정의(던진 낚싯대 …)는 [itemsDir] 에도 남긴다 — 다음 빌드부터는 그 파일이 모양이다. */
    private fun writeDefinition(merger: PackMerger, id: String, target: NumberMigration.Target) {
        val json = target.definition ?: PackAssets.itemJson(target.model)
        merger.replace(GENERATED, PackAssets.itemPath(id), json.toByteArray(Charsets.UTF_8))
        if (target.definition != null) {
            itemsDir.mkdirs()
            File(itemsDir, "$id.json").writeText(target.definition, Charsets.UTF_8)
        }
    }

    /** [itemsDir] 의 `<id>.json` — 관리자가 넣었거나 번호에서 옮긴 아이템 정의. */
    private fun customDefinition(id: String): String? =
        File(itemsDir, "$id.json").takeIf { it.isFile }?.readText(Charsets.UTF_8)

    /**
     * 옮긴 것을 정의에 적는다(메인). 빌드하는 사이 관리자가 그 번호를 고쳤으면 건드리지 않는다. 텍스처·모델이 이미 있으면 그대로 두고
     * 번호만 뗀다. 정의가 바뀌니 이미 나간 아이템도 다음 계기에 번호 없이 다시 그려진다(규칙 6).
     */
    private fun applyMigration(migrated: List<Migrated>) {
        if (migrated.isEmpty()) return
        for (m in migrated) {
            if (m.table != null) {
                val table = custom.upgrades.get(m.table) ?: continue
                custom.upgrades.put(table.copy(steps = fixed(table.steps, m)))
                continue
            }
            val item = custom.items.get(m.itemId) ?: continue
            if (m.level > 0) {
                val own = item.upgrade.own ?: continue
                custom.items.put(item.copy(upgrade = item.upgrade.copy(own = own.copy(steps = fixed(own.steps, m)))))
                continue
            }
            if (item.customModelData != m.number) continue
            val keep = m.model == null || PackAssets.needsPack(item)
            custom.items.put(item.copy(customModelData = 0, model = if (keep) item.model else m.model!!))
        }
        custom.logger.info("낡은 모델 번호 " + migrated.size + "개를 최신 방식(item_model)으로 옮겼습니다 — 새 팩을 올려야 보입니다")
    }

    private fun fixed(steps: List<com.inmc.customitems.item.UpgradeStep>, m: Migrated) = steps.mapIndexed { index, step ->
        if (index + 1 != m.level || step.customModelData != m.number) {
            step
        } else {
            val keep = m.model == null || step.texture.isNotBlank() || step.model.isNotBlank()
            step.copy(customModelData = 0, model = if (keep) step.model else m.model!!)
        }
    }

    private fun description(): String =
        custom.packConfig.description.ifBlank { "INMC 커스텀 아이템" }

    private fun failed(message: String) =
        Result(0, 0, 0, 0, 0, emptyList(), emptyList(), emptyList(), 0, 0, 0, emptyList(), "", 0L, 0L, error = message)

    private companion object {
        const val GENERATED = "(자동 생성)"
        const val BASE = "(pack/models/base)"
        const val NUMBERS = "(번호 걷어 내기)"
        const val PREVIEW = "(모델 목록 미리보기)"
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
              바닐라 모델을 바꿔 그리고 싶을 때만(선택). 팩 안의 경로 그대로 넣습니다.
                assets/minecraft/models/item/<재질>.json

            [items/]
              아이템 정의 json(<이름>.json)을 넣으면 텍스처·모델 칸 대신 그대로 씁니다.
              낡은 모델 번호를 옮길 때 조건이 있는 모양(던진 낚싯대 …)이 여기 적힙니다.
              지우면 텍스처·모델 칸대로 돌아갑니다.

            [모델 번호(custom_model_data)는 쓰지 않습니다]
              번호가 남은 아이템은 팩을 만들 때 팩이 그리던 모양 그대로 최신 방식(item_model)으로
              옮기고, sources/ 의 팩이 바닐라 아이템에 걸어 둔 번호 갈래는 지웁니다.

            [output/]
              만들어진 pack.zip 과 pack.sha1 이 여기 생깁니다. 손대지 마세요.

            만들기:  /커스텀아이템 리팩 빌드
            ============================================================
        """.trimIndent()
    }
}
