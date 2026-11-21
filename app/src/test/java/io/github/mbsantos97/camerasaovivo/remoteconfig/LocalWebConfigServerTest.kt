package io.github.mbsantos97.camerasaovivo.remoteconfig

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket

private class MemoryBackend : RemoteConfigBackend {
    val saved = mutableListOf<CameraForm>()

    override fun list() = saved.mapIndexed { i, f -> CameraSummary("id$i", f.name, f.host, f.onvifPort, f.rtspPort, f.username, f.password.isNotEmpty()) }

    override suspend fun save(form: CameraForm): String {
        saved += form
        return "id${saved.lastIndex}"
    }

    override suspend fun delete(id: String) {
        saved.removeAt(id.removePrefix("id").toInt())
    }
}

class LocalWebConfigServerTest {
    private val backend = MemoryBackend()
    private val port = ServerSocket(0).use { it.localPort }
    private val server = LocalWebConfigServer(backend, pin = "123456", port = port)
    private val http = OkHttpClient()
    private val base = "http://127.0.0.1:$port"

    @Before
    fun start() = server.start(5_000, true)

    @After
    fun stop() = server.stop()

    private fun get(
        path: String,
        pin: String? = null,
    ) = http.newCall(Request.Builder().url(base + path).apply { pin?.let { header("X-Pin", it) } }.build()).execute()

    private fun post(
        path: String,
        json: String,
        pin: String,
    ) = http.newCall(Request.Builder().url(base + path).header("X-Pin", pin).post(json.toRequestBody("application/json".toMediaType())).build()).execute()

    @Test
    fun `pagina embutida em pt-BR sem CDN`() {
        get("/").use { r ->
            val body = r.body.string()
            assertEquals(200, r.code)
            assertTrue(body.contains("PIN mostrado na TV"))
            assertTrue(!body.contains("https://"))
        }
    }

    @Test
    fun `API exige o PIN`() {
        get("/api/cameras").use { assertEquals(401, it.code) }
        get("/api/cameras", "000000").use { assertEquals(401, it.code) }
        get("/api/cameras", "123456").use { assertEquals(200, it.code) }
    }

    @Test
    fun `bloqueia apos 5 PINs errados`() {
        repeat(5) { get("/api/cameras", "999999").close() }
        get("/api/cameras", "123456").use { assertTrue(it.code == 429 || it.code == 403) }
    }

    @Test
    fun `salva, lista e remove camera`() {
        post("/api/cameras", """{"name":"Lâmpada","host":"192.168.0.102","password":"s3nha"}""", "123456").use { assertEquals(200, it.code) }
        val list = get("/api/cameras", "123456").use { it.body.string() }
        assertTrue(list.contains("Lâmpada"))
        assertTrue(!list.contains("s3nha"))
        post("/api/cameras/delete", """{"id":"id0"}""", "123456").use { assertEquals(200, it.code) }
        assertTrue(backend.saved.isEmpty())
    }

    @Test
    fun `validacao recusa IP publico e campos vazios`() {
        post("/api/cameras", """{"name":"X","host":"8.8.8.8"}""", "123456").use { assertEquals(400, it.code) }
        assertNull(LocalWebConfigServer.validate(CameraForm(name = "A", host = "10.0.0.5")))
        assertEquals("Informe o nome da câmera", LocalWebConfigServer.validate(CameraForm(host = "10.0.0.5")))
        assertTrue(LocalWebConfigServer.validate(CameraForm(name = "A", host = "1234567890"))!!.contains("ID do app Yoosee"))
    }

    @Test
    fun `PIN tem 6 digitos`() {
        repeat(20) { assertTrue(LocalWebConfigServer.newPin().matches(Regex("\\d{6}"))) }
    }
}
