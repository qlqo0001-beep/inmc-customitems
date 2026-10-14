package com.inmc.customitems.pack

import com.inmc.customitems.item.CustomItem
import com.inmc.customitems.item.UpgradeTable
import java.io.File
import java.security.MessageDigest
import java.util.zip.CRC32

/**
 * 만든 팩이 **지금 서버의 아이템·팩 재료로 만든 것인가**(사용자 요청 2026-10-14 "켜질 때 리소스팩과 서버의 아이템이 확실하게 연동되는지
 * 파악해서, 안 되어 있으면 다시 빌드해서 올리기"). 판단은 순수 계산이라 서버 없이 테스트한다 — 파일·아이템을 읽는 것은 [PackService].
 *
 * 어긋남은 넷이다([problems]):
 * 1. 팩 파일이 없거나, 적어 둔 sha1(`pack.sha1` — 클라이언트에 보내는 값)과 실제 파일이 다르다(누가 바꿔 넣음 · 쓰다 멈춤)
 * 2. 아이템의 모양 정의(`assets/inmc/items/<이름>.json`)가 팩에 없다 — 그 아이템은 게임에서 보라·검정
 * 3. 아직 옮기지 않은 낡은 모델 번호가 남았다 — 빌드가 옮긴다
 * 4. 팩을 만든 재료의 지문([inputs])이 지금과 다르다 — 팩에 들어가는 아이템 정의 · 팩 소스/텍스처/모델/정의 폴더의 파일 · 이 플러그인 jar
 *
 * 같은 재료면 zip 이 바이트까지 같게 나오므로(규칙 36) 괜히 다시 만들어도 sha1 이 그대로라 클라이언트가 다시 받지 않는다 — 그래서 4 는 넉넉하게
 * 잡는다(팩에 들어가는 아이템은 정의 통째로 본다 — 능력치만 고쳐도 다시 만든다). 모자라게 잡으면 어긋난 팩이 조용히 남는다.
 */
object PackSync {

    /** 팩에 반드시 있어야 하는 아이템 정의 경로 — 모양이 있는 아이템과, 모양을 바꾸는 강화 단계. */
    fun expectedEntries(items: List<CustomItem>, tables: Map<String, UpgradeTable?>): List<String> {
        val out = ArrayList<String>()
        for (item in items) {
            if (PackAssets.needsPack(item)) out += PackAssets.itemPath(item.resourceId)
            val table = tables[item.id] ?: continue
            for (level in 1..table.maxLevel) {
                val step = table.step(level) ?: continue
                if (step.texture.isNotBlank() || step.model.isNotBlank()) out += PackAssets.itemPath(PackAssets.levelId(item, level))
            }
        }
        return out.distinct()
    }

    /**
     * 폴더들의 파일 목록 지문 — 상대 경로 · 크기 · 내용 CRC32. 수정 시각은 안 본다: 켤 때 같은 그림을 다시 쓰는 플러그인(메뉴 그림)이 있어
     * 시각만 바뀌어도 매번 어긋난 것이 된다. 없는 폴더는 비어 있는 것과 같다.
     */
    fun filesKey(dirs: Map<String, File>): String = buildString {
        for ((label, dir) in dirs.toSortedMap()) {
            append('[').append(label).append(']').append('\n')
            if (!dir.exists()) continue
            val files = if (dir.isFile) listOf(dir) else dir.walkTopDown().filter { it.isFile }.toList()
            for (file in files.sortedBy { it.relativeTo(dir).invariantSeparatorsPath }) {
                append(if (dir.isFile) file.name else file.relativeTo(dir).invariantSeparatorsPath)
                append('|').append(file.length()).append('|').append(crc(file)).append('\n')
            }
        }
    }

    /** 팩 재료 전체의 지문(SHA-256). [itemsText] 는 팩에 들어가는 정의(메인 스레드가 뜬다), [plugin] 은 이 jar 의 크기·시각(코드가 바뀌면 다시). */
    fun inputs(itemsText: String, filesKey: String, plugin: String, description: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (part in listOf(itemsText, filesKey, plugin, description)) {
            digest.update(part.toByteArray(Charsets.UTF_8))
            digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * 어긋난 까닭들(사람에게 보이는 글). 비면 맞다.
     *
     * @param zipEntries 팩 안의 경로들. 팩이 없으면 null.
     * @param recordedSha1 `pack.sha1` 에 적힌 값(클라이언트에 보내는 값).
     * @param actualSha1 팩 파일의 실제 sha1. 팩이 없으면 null.
     * @param numbered 아직 옮기지 않은 모델 번호가 남은 아이템·강화 단계 수.
     * @param recordedInputs 마지막 빌드가 적어 둔 재료 지문(`pack.inputs`). 없으면 null(이 기능 전에 만든 팩).
     */
    fun problems(
        zipEntries: Set<String>?,
        recordedSha1: String,
        actualSha1: String?,
        expected: List<String>,
        numbered: Int,
        recordedInputs: String?,
        currentInputs: String,
    ): List<String> {
        if (zipEntries == null || actualSha1 == null) return listOf("팩 파일(pack.zip)이 없습니다")
        val out = ArrayList<String>()
        if (!recordedSha1.equals(actualSha1, ignoreCase = true)) out += "적어 둔 sha1 과 팩 파일이 다릅니다(" + recordedSha1.ifBlank { "없음" }.take(10) + " ≠ " + actualSha1.take(10) + ")"
        val missing = expected.filter { it !in zipEntries }
        if (missing.isNotEmpty()) {
            out += "팩에 모양이 없는 아이템 " + missing.size + "개: " +
                missing.take(5).joinToString(", ") { it.substringAfterLast('/').removeSuffix(".json") } + (if (missing.size > 5) " …" else "")
        }
        if (numbered > 0) out += "아직 옮기지 않은 모델 번호 " + numbered + "개"
        when (recordedInputs) {
            null -> out += "팩을 만든 재료 기록이 없습니다(이 확인 기능 전에 만든 팩)"
            currentInputs -> Unit
            else -> out += INPUTS_CHANGED
        }
        return out
    }

    /** 재료 지문만 다를 때의 까닭 — 이것 하나뿐이면 모양은 그대로일 수 있다. */
    const val INPUTS_CHANGED = "팩을 만든 뒤 아이템·팩 소스·텍스처가 바뀌었습니다"

    private fun crc(file: File): Long {
        val crc = CRC32()
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                crc.update(buffer, 0, read)
            }
        }
        return crc.value
    }
}
