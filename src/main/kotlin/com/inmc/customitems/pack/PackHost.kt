package com.inmc.customitems.pack

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

/**
 * 만든 팩을 **직접 내려준다** — `config.yml` 의 `resource-pack.host`. 꺼 두면(기본) 아무 포트도 열지 않는다.
 *
 * - JDK 내장 HTTP 서버(`com.sun.net.httpserver`). `/pack.zip` 하나만 내려준다 — 다른 경로는 404, 쓰기 요청은 405.
 * - **파일을 잡고 있지 않는다.** 크기·수정 시각이 바뀌었을 때만 메모리로 읽어 거기서 보낸다. 윈도우에서는 열려 있는 파일을 지우지 못해,
 *   누가 받는 동안 다시 만들면 [PackZip.write] 가 옛 파일을 못 지우고 새 팩이 써지지 않는다.
 * - 포트를 열지 못하면(다른 프로그램이 쓰는 중) [RETRY_SECONDS] 초마다 다시 해 본다 — 한 번 실패로 재시작까지 팩이 끊기지 않게.
 * - 내려주는 일은 게임 스레드와 상관없다. 자기 스레드 둘에서 돈다.
 */
class PackHost(private val file: () -> File, private val logger: Logger, private val retrySeconds: Long = RETRY_SECONDS) {

    data class Settings(val enabled: Boolean = false, val bind: String = "0.0.0.0", val port: Int = 8765)

    private class Snapshot(val bytes: ByteArray, val length: Long, val modified: Long)

    private val retry: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "inmc-pack-host-retry").apply { isDaemon = true }
    }

    @Volatile
    private var server: HttpServer? = null

    @Volatile
    private var settings = Settings()

    @Volatile
    private var snapshot: Snapshot? = null

    private var retryTask: ScheduledFuture<*>? = null

    /** 마지막으로 포트를 열지 못한 까닭. 열었거나 꺼져 있으면 null. */
    @Volatile
    var problem: String? = null
        private set

    val running: Boolean get() = server != null

    /** 실제로 연 포트(설정이 0 이면 운영체제가 고른 것). 안 열렸으면 null. */
    val boundPort: Int? get() = server?.address?.port

    /** 설정을 반영한다 — 같으면 그대로, 다르면 닫고 다시. 리로드마다 부른다. */
    @Synchronized
    fun apply(next: Settings) {
        if (next == settings && (running || retryTask != null || !next.enabled)) return
        stopServer()
        settings = next
        if (!next.enabled) {
            problem = null
            return
        }
        if (!tryStart()) {
            retryTask = retry.scheduleWithFixedDelay({ synchronized(this) { if (tryStart()) cancelRetry() } }, retrySeconds, retrySeconds, TimeUnit.SECONDS)
        }
    }

    /** 플러그인이 꺼질 때. */
    @Synchronized
    fun shutdown() {
        stopServer()
        retry.shutdownNow()
    }

    private fun tryStart(): Boolean {
        if (running) return true
        val s = settings
        return try {
            val created = HttpServer.create(InetSocketAddress(s.bind, s.port), 0)
            created.createContext("/") { exchange -> exchange.use(::handle) }
            created.executor = Executors.newFixedThreadPool(2) { r -> Thread(r, "inmc-pack-host").apply { isDaemon = true } }
            created.start()
            server = created
            val wasFailing = problem != null
            problem = null
            logger.info("리소스팩을 직접 내려줍니다 — http://" + s.bind + ":" + created.address.port + "/pack.zip" + if (wasFailing) " (다시 시도해 열었습니다)" else "")
            true
        } catch (t: Throwable) {
            val reason = t.javaClass.simpleName + (t.message?.let { ": $it" } ?: "")
            // 같은 까닭이면 30초마다 로그를 채우지 않는다.
            if (problem != reason) logger.warning("리소스팩 포트를 열지 못했습니다(${s.bind}:${s.port}) — $reason. ${retrySeconds}초마다 다시 해 봅니다.")
            problem = reason
            false
        }
    }

    private fun stopServer() {
        cancelRetry()
        val current = server ?: return
        server = null
        current.stop(0)
        (current.executor as? java.util.concurrent.ExecutorService)?.shutdownNow()
    }

    private fun cancelRetry() {
        retryTask?.cancel(false)
        retryTask = null
    }

    private fun handle(exchange: HttpExchange) {
        val method = exchange.requestMethod.uppercase()
        if (method != "GET" && method != "HEAD") {
            exchange.sendResponseHeaders(405, -1)
            return
        }
        if (exchange.requestURI.path != PATH) {
            exchange.sendResponseHeaders(404, -1)
            return
        }
        val pack = current()
        if (pack == null) {
            exchange.sendResponseHeaders(404, -1)
            return
        }
        exchange.responseHeaders.set("Content-Type", "application/zip")
        exchange.responseHeaders.set("Cache-Control", "no-cache")
        if (method == "HEAD") {
            exchange.responseHeaders.set("Content-Length", pack.bytes.size.toString())
            exchange.sendResponseHeaders(200, -1)
            return
        }
        exchange.sendResponseHeaders(200, pack.bytes.size.toLong())
        exchange.responseBody.write(pack.bytes)
    }

    /** 지금 파일. 바뀌었으면 다시 읽는다. 없으면 null. */
    @Synchronized
    private fun current(): Snapshot? {
        val target = file()
        if (!target.isFile) return null
        val length = target.length()
        val modified = target.lastModified()
        snapshot?.let { if (it.length == length && it.modified == modified) return it }
        val bytes = runCatching { target.readBytes() }.getOrNull() ?: return snapshot
        return Snapshot(bytes, length, modified).also { snapshot = it }
    }

    companion object {
        const val PATH = "/pack.zip"
        const val RETRY_SECONDS = 30L
    }
}
