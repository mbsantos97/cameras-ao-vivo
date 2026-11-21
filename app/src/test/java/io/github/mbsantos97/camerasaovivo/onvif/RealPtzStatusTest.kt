package io.github.mbsantos97.camerasaovivo.onvif

import io.github.mbsantos97.camerasaovivo.onvif.soap.Requests
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Lê (sem mover) a posição do PTZ (GetStatus) e os presets. Uso: -PptzCam=IP -PptzPass=SENHA */
class RealPtzStatusTest {
    @Test
    fun `posicao do ptz`() =
        runBlocking {
            val ip = System.getProperty("ptzCam").orEmpty()
            assumeTrue(ip.isNotEmpty())
            TestXml.install()
            val c = OnvifClient(OkHttpClient.Builder().readTimeout(8, TimeUnit.SECONDS).build(), ip, 5000, "admin", System.getProperty("ptzPass").orEmpty())
            c.discoverServices()
            val profile = c.profiles().first().token
            val status = runCatching { c.call(c.deviceUrl, Requests.getStatus(profile)) }
            status.onSuccess { x ->
                val pt = x.find("Position")?.find("PanTilt")
                println("REALSTATUS pan=${pt?.attr("x")} tilt=${pt?.attr("y")} moveStatus=${x.find("MoveStatus")?.textOf("PanTilt")}")
            }
            status.onFailure { println("REALSTATUS FALHOU ${it.javaClass.simpleName} ${it.message}") }
            val presets = runCatching { c.presets(profile) }
            println("REALSTATUS presets=${presets.getOrNull()?.map { it.name } ?: "FALHOU ${presets.exceptionOrNull()?.message}"}")
        }
}
