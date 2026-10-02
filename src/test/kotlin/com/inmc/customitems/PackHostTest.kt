package com.inmc.customitems

import com.inmc.customitems.pack.PackConfig
import com.inmc.customitems.pack.PackHost
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.util.logging.Logger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 팩 직접 내려주기 — 진짜 포트를 열어 받아 본다(127.0.0.1, 운영체제가 고른 포트). */
class PackHostTest {

    private val dir: File = Files.createTempDirectory("packhost").toFile()
    private val zip = File(dir, "pack.zip")
    private val host = PackHost({ zip }, Logger.getLogger("PackHostTest"), retrySeconds = 1)
    private val client: HttpClient = HttpClient.newHttpClient()

    @AfterTest
    fun cleanUp() {
        host.shutdown()
        dir.deleteRecursively()
    }

    private fun url(path: String = PackHost.PATH) = URI("http://127.0.0.1:" + host.boundPort + path)

    private fun get(path: String = PackHost.PATH): HttpResponse<ByteArray> =
        client.send(HttpRequest.newBuilder(url(path)).GET().build(), HttpResponse.BodyHandlers.ofByteArray())

    private fun start() {
        host.apply(PackHost.Settings(enabled = true, bind = "127.0.0.1", port = 0))
        assertTrue(host.running, "포트를 못 열었다: ${host.problem}")
    }

    @Test
    fun `팩을 그대로 내려준다 — HEAD 는 크기만`() {
        val bytes = ByteArray(300_000) { (it % 251).toByte() }
        zip.writeBytes(bytes)
        start()
        val response = get()
        assertEquals(200, response.statusCode())
        assertEquals("application/zip", response.headers().firstValue("Content-Type").orElse(""))
        assertContentEquals(bytes, response.body())

        val head = client.send(HttpRequest.newBuilder(url()).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding())
        assertEquals(200, head.statusCode())
        assertEquals(bytes.size.toString(), head.headers().firstValue("Content-Length").orElse(""))
    }

    @Test
    fun `pack_zip 말고는 내주지 않는다`() {
        zip.writeBytes(byteArrayOf(1, 2, 3))
        File(dir, "secret.txt").writeText("x")
        start()
        assertEquals(404, get("/secret.txt").statusCode())
        assertEquals(404, get("/../pack.zip.tmp").statusCode())
        assertEquals(404, get("/").statusCode())
        val post = client.send(HttpRequest.newBuilder(url()).POST(HttpRequest.BodyPublishers.ofString("x")).build(), HttpResponse.BodyHandlers.discarding())
        assertEquals(405, post.statusCode())
    }

    @Test
    fun `다시 만들면 새 팩을 내려주고, 내려준 뒤에도 파일을 지울 수 있다`() {
        zip.writeBytes(byteArrayOf(1, 2, 3))
        start()
        assertContentEquals(byteArrayOf(1, 2, 3), get().body())
        // 윈도우에서는 열린 파일을 못 지운다 — 내려주기가 파일을 쥐고 있으면 다시 만들 때 옛 팩이 남는다.
        assertTrue(zip.delete(), "내려준 뒤에도 파일이 잡혀 있다")
        assertEquals(404, get().statusCode(), "없는 팩")
        zip.writeBytes(byteArrayOf(9, 8, 7, 6))
        assertContentEquals(byteArrayOf(9, 8, 7, 6), get().body())
    }

    @Test
    fun `포트가 막혀 있으면 다시 해 보다가 풀리면 연다`() {
        zip.writeBytes(byteArrayOf(1))
        val blocker = ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
        val port = blocker.localPort
        host.apply(PackHost.Settings(enabled = true, bind = "127.0.0.1", port = port))
        assertFalse(host.running)
        assertNotNull(host.problem)
        blocker.close()
        val deadline = System.currentTimeMillis() + 5_000
        while (!host.running && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertTrue(host.running, "풀린 포트를 다시 열지 않았다")
        assertEquals(port, host.boundPort)
        assertEquals(null, host.problem)
    }

    @Test
    fun `끄면 포트를 놓는다`() {
        zip.writeBytes(byteArrayOf(1))
        start()
        val port = host.boundPort!!
        host.apply(PackHost.Settings(enabled = false))
        assertFalse(host.running)
        ServerSocket(port, 50, java.net.InetAddress.getByName("127.0.0.1")).close()
    }

    @Test
    fun `설정 — 기본은 꺼짐, 적은 값은 그대로`() {
        assertEquals(PackHost.Settings(), PackConfig.from(YamlConfiguration()).host)
        val yaml = YamlConfiguration()
        yaml.set("resource-pack.host.enabled", true)
        yaml.set("resource-pack.host.bind", " ")
        yaml.set("resource-pack.host.port", 70000)
        val host = PackConfig.from(yaml).host
        assertTrue(host.enabled)
        assertEquals("0.0.0.0", host.bind, "빈 주소는 모든 네트워크")
        assertEquals(65535, host.port)
    }

    @Test
    fun `배포 설정은 포트를 열지 않는다`() {
        // 아이템 플러그인이 관리자 모르게 포트를 열면 안 된다 — 켜는 것은 관리자.
        val config = javaClass.classLoader.getResourceAsStream("config.yml")!!.use { YamlConfiguration.loadConfiguration(InputStreamReader(it, Charsets.UTF_8)) }
        assertFalse(PackConfig.from(config).host.enabled)
        assertEquals(8765, PackConfig.from(config).host.port)
    }
}
