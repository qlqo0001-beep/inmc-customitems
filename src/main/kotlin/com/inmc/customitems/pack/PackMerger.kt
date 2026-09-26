package com.inmc.customitems.pack

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * 여러 리소스팩을 하나로 합친다.
 *
 * **소스는 폴더도 되고 zip 도 된다.** 관리자가 받은 팩을 풀지 않고 그대로 던져 넣을 수
 * 있어야 한다 — 푸는 순간 다음 업데이트 때 뭘 지워야 할지 알 수 없게 된다.
 *
 * **나중 소스가 이긴다.** 이름 순으로 읽으므로 `10-기본.zip` · `20-내것.zip` 처럼 번호를
 * 붙이는 것이 관례다. 다만 **json 은 덮지 않고 섞는다** ([JsonMerge]) — 덮으면 두 팩 중
 * 하나가 조용히 사라진다.
 *
 * 순수 파일 작업이라 **워커 스레드에서 돈다.** Bukkit 을 모른다.
 */
class PackMerger {

    /** 합친 결과. 경로 → 내용. */
    private val files = LinkedHashMap<String, ByteArray>()

    /** 무엇이 무엇을 덮었는지. 관리자에게 그대로 보여준다. */
    private val conflicts = ArrayList<Conflict>()

    private val mergedJson = LinkedHashSet<String>()

    data class Conflict(val path: String, val winner: String, val loser: String, val merged: Boolean)

    data class Report(
        val fileCount: Int,
        val sourceCount: Int,
        val conflicts: List<Conflict>,
        val mergedJsonCount: Int,
    ) {
        val replacedCount: Int get() = conflicts.count { !it.merged }
    }

    /**
     * 소스 하나를 얹는다. 넣는 **순서가 곧 우선순위**다.
     *
     * @param label 보고서에 쓸 이름. 보통 파일 이름.
     */
    fun add(label: String, source: File) {
        when {
            source.isDirectory -> addFolder(label, source)
            source.isFile && source.name.endsWith(".zip", ignoreCase = true) -> addZip(label, source)
            // 그 외는 조용히 건너뛴다 — 관리자가 폴더에 README 를 넣어둘 수 있다.
        }
    }

    /** 메모리에서 만든 파일을 얹는다. 아이템 에셋 생성물이 이 길로 들어온다. */
    fun put(label: String, path: String, bytes: ByteArray) {
        val normalized = normalize(path) ?: return
        val existing = files[normalized]
        if (existing == null) {
            files[normalized] = bytes
            owner[normalized] = label
            return
        }

        val previous = owner[normalized] ?: "?"
        // pack.mcmeta 도 json 이다 — 확장자로만 가리면 나중 팩의 것이 통째로 덮어 앞 팩의 오버레이가 꺼진다.
        if (normalized.endsWith(".json") || normalized == "pack.mcmeta") {
            val merged = JsonMerge.merge(
                normalized,
                existing.toString(Charsets.UTF_8),
                bytes.toString(Charsets.UTF_8),
            )
            files[normalized] = merged.toByteArray(Charsets.UTF_8)
            mergedJson += normalized
            conflicts += Conflict(normalized, label, previous, merged = true)
        } else {
            files[normalized] = bytes
            conflicts += Conflict(normalized, label, previous, merged = false)
        }
        owner[normalized] = label
    }

    /** 섞지 않고 통째로 바꾼다 — 우리가 전부를 쓰는 파일(블록 상태)에만. 앞의 것을 이미 읽어 들인 뒤에 부른다. */
    fun replace(label: String, path: String, bytes: ByteArray) {
        val normalized = normalize(path) ?: return
        owner[normalized]?.let { conflicts += Conflict(normalized, label, it, merged = false) }
        files[normalized] = bytes
        owner[normalized] = label
    }

    fun result(sourceCount: Int): Report =
        Report(files.size, sourceCount, conflicts.toList(), mergedJson.size)

    fun entries(): Map<String, ByteArray> = files

    // --- 읽기 -----------------------------------------------------------------------

    private val owner = HashMap<String, String>()

    private fun addFolder(label: String, root: File) {
        val files = root.walkTopDown().filter { it.isFile }.associateBy { it.relativeTo(root).invariantSeparatorsPath }
        val wrapper = wrapperOf(files.keys)
        for ((path, file) in files) {
            if (!path.startsWith(wrapper)) continue
            put(label, path.removePrefix(wrapper), file.readBytes())
        }
    }

    private fun addZip(label: String, zip: File) {
        ZipFile(zip).use { archive ->
            val entries = archive.entries().toList().filter { !it.isDirectory && isSafe(it) }
            val wrapper = wrapperOf(entries.mapNotNull { normalize(it.name) })
            for (entry in entries) {
                val path = normalize(entry.name) ?: continue
                if (!path.startsWith(wrapper)) continue
                val bytes = archive.getInputStream(entry).use { it.readBytes() }
                put(label, path.removePrefix(wrapper), bytes)
            }
        }
    }

    /**
     * 팩이 폴더 하나에 싸여 있으면 그 폴더(`resourcepack/`), 아니면 빈 글자.
     *
     * 폴더째 압축하면 흔히 이렇게 된다(BetterHud 의 빌드 폴더가 그렇다). 그대로 합치면 `resourcepack/assets/…` 가 되어
     * 클라이언트가 **그 팩을 통째로 무시한다** — 오류는 없다. 맨 위에 `pack.mcmeta` 가 있거나 한 단계 아래에 딱 하나가
     * 아니면 그대로 읽는다(`assets` 만 든 폴더도 소스가 될 수 있다). 싼 폴더 밖의 파일은 팩이 아니라 버린다.
     */
    private fun wrapperOf(paths: Collection<String>): String {
        if ("pack.mcmeta" in paths) return ""
        val nested = paths.filter { it.endsWith("/pack.mcmeta") && it.count { c -> c == '/' } == 1 }
        return if (nested.size == 1) nested.single().removeSuffix("pack.mcmeta") else ""
    }

    /**
     * zip 안의 경로가 바깥으로 나가지 않는지.
     *
     * `../` 가 든 항목을 그대로 쓰면 **압축을 푸는 순간 서버 폴더 아무 데나 파일을 쓸 수
     * 있다** (Zip Slip). 우리는 메모리에 담았다가 다시 zip 으로 쓰기만 하지만, 그렇게 만든
     * 팩을 누가 풀지는 우리가 정하지 않는다.
     */
    private fun isSafe(entry: ZipEntry): Boolean = normalize(entry.name) != null

    /**
     * 경로를 팩 안 경로로 다듬는다. 위험하거나 팩에 들어갈 수 없는 것은 null.
     *
     * macOS 가 zip 에 넣는 `__MACOSX/` 와 `.DS_Store` 도 여기서 거른다 — 안 거르면
     * 클라이언트가 팩을 읽다가 경고를 낸다.
     */
    private fun normalize(raw: String): String? {
        val path = raw.replace('\\', '/').trimStart('/')
        if (path.isBlank()) return null
        if (path.split('/').any { it == ".." || it.isBlank() }) return null
        if (path.startsWith("__MACOSX/")) return null
        if (path.endsWith(".DS_Store") || path.endsWith("Thumbs.db")) return null
        return path
    }
}
