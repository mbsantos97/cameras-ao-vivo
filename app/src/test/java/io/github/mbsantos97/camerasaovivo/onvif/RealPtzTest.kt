package io.github.mbsantos97.camerasaovivo.onvif

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Move uma câmera real por 1 s e para (pelo mesmo caminho do app, com os XAddrs da câmera).
 * Uso: -PptzCam=IP -PptzPass=SENHA -PptzX=0.5 (negativo = esquerda)
 */
class RealPtzTest {
    @Test
    fun `move e para`() =
        runBlocking {
            val ip = System.getProperty("ptzCam").orEmpty()
            assumeTrue(ip.isNotEmpty())
            TestXml.install()
            val http = OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
            val c = OnvifClient(http, ip, 5000, "admin", System.getProperty("ptzPass").orEmpty())
            runCatching { c.syncClock() }
            val s = c.discoverServices()
            println("REALPTZ serviços saneados: media=${s.media} ptz=${s.ptz}")
            val profile = c.profiles().first { it.hasPtz }.token
            val x = System.getProperty("ptzX")?.toFloatOrNull() ?: 0.5f
            val t0 = System.currentTimeMillis()
            c.continuousMove(profile, x, 0f, "PT2S")
            println("REALPTZ ContinuousMove ok em ${System.currentTimeMillis() - t0} ms")
            delay(System.getProperty("ptzMs")?.toLongOrNull() ?: 1_000L)
            c.stop(profile)
            println("REALPTZ Stop ok")
            println("REALPTZ presets: ${runCatching { c.presets(profile).size }.getOrElse { "falhou: ${it.message}" }}")
        }
}
