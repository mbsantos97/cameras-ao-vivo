package io.github.mbsantos97.camerasaovivo.onvif

import io.github.mbsantos97.camerasaovivo.camera.StreamResolver
import io.github.mbsantos97.camerasaovivo.data.config.CameraConfig
import io.github.mbsantos97.camerasaovivo.onvif.discovery.WsDiscovery
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Ponta a ponta contra a câmera simulada (tools/mock-camera). Só roda com o mock no ar:
 *   tools/mock-camera/build/install/mock-camera/bin/mock-camera --port 5055 --password segredo --variant dual-stacked
 *   ./gradlew :app:testDebugUnitTest --tests "*MockCamera*" -PmockCamera=127.0.0.1:5055
 */
class MockCameraIntegrationTest {
    private val target = System.getProperty("mockCamera").orEmpty()

    @Before
    fun only() {
        assumeTrue(target.isNotEmpty())
        TestXml.install()
    }

    private val host get() = target.substringBefore(':')
    private val port get() = target.substringAfter(':').toInt()

    @Test
    fun `WS-Security com relogio sincronizado e aceito pela camera simulada`() =
        runBlocking {
            val c = OnvifClient(OkHttpClient(), host, port, "admin", "segredo")
            c.syncClock()
            assertEquals("Yoosee-8177", c.deviceInformation().model)
            assertEquals(OnvifClient.AuthMode.WSSE, c.authMode)
        }

    @Test
    fun `resolver completo encontra a lente dupla empilhada`() =
        runBlocking {
            val cam = CameraConfig(id = "m", name = "Mock", host = host, onvifPort = port, rtspPort = 8554)
            val r =
                StreamResolver({ cfg, p ->
                    OnvifClient(OkHttpClient(), cfg.host, cfg.onvifPort, cfg.username, p)
                }, { _, _, _ -> false }).resolve(cam, "segredo")
            val s = r.streams.sources.single()
            assertTrue(s.stacked)
            assertEquals("rtsp://$host:8554/stacked", s.mainUri)
            assertEquals("rtsp://$host:8554/stacked_sub", s.subUri)
        }

    @Test
    fun `senha errada e recusada`() =
        runBlocking {
            val c = OnvifClient(OkHttpClient(), host, port, "admin", "errada")
            c.syncClock()
            val r = runCatching { c.deviceInformation() }
            assertTrue(r.isFailure)
        }

    @Test
    fun `WS-Discovery multicast encontra a camera simulada`() =
        runBlocking {
            val found = WsDiscovery(null).probe(3_000)
            assertTrue("encontrados: $found", found.any { it.port == port })
        }
}
