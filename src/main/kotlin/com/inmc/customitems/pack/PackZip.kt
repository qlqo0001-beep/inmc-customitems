package com.inmc.customitems.pack

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 합친 결과를 zip 으로 내보낸다.
 *
 * [PackService] 안에 있던 것을 뽑아냈다. **검증할 것이 있어서**다 — 아래 두 성질은 문서와
 * 규칙에 적어두었지만 확인한 적이 없었고, 둘 다 틀려도 조용히 망가지는 종류다.
 *
 * **Bukkit 을 모른다.** 서버 없이 전부 검증할 수 있다.
 */
object PackZip {

    /**
     * zip 으로 쓴다.
     *
     * **같은 내용이면 바이트까지 같은 파일이 나온다.** 경로 순으로 쓰고 시각을 0 으로
     * 고정하기 때문이다. 안 그러면 내용이 안 바뀌어도 sha1 이 달라지고, 그러면 접속할
     * 때마다 클라이언트가 몇 MB 를 다시 받는다.
     *
     * **다 쓴 뒤에 바꿔 끼운다.** 쓰는 도중에 누가 받아 가면 깨진 팩을 받는다.
     */
    fun write(target: File, entries: Map<String, ByteArray>) {
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.parentFile?.mkdirs()

        ZipOutputStream(FileOutputStream(temp)).use { zip ->
            zip.setLevel(Deflater.BEST_COMPRESSION)
            for (path in entries.keys.sorted()) {
                val entry = ZipEntry(path)
                entry.time = 0L
                zip.putNextEntry(entry)
                zip.write(entries.getValue(path))
                zip.closeEntry()
            }
        }

        if (target.exists()) target.delete()
        temp.renameTo(target)
    }

    /** 클라이언트가 **이미 받은 팩인지 판단하는 근거**. 40자 소문자 16진수. */
    fun sha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { stream ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
