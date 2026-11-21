package io.github.mbsantos97.camerasaovivo.onvif

import io.github.mbsantos97.camerasaovivo.onvif.soap.Requests
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Lê (sem alterar nada) as capacidades de PTZ: posição inicial e presets. Uso: -PptzCam=IP -PptzPass=SENHA */
class RealPtzInfoTest {
    @Test
    fun `capacidades de ptz`() =
        runBlocking {
            val ip = System.getProperty("ptzCam").orEmpty()
            assumeTrue(ip.isNotEmpty())
            TestXml.install()
            val c = OnvifClient(OkHttpClient.Builder().readTimeout(8, TimeUnit.SECONDS).build(), ip, 5000, "admin", System.getProperty("ptzPass").orEmpty())
            c.discoverServices()
            for (req in listOf(Requests.getNodes(), Requests.getPtzConfigurations())) {
                val r = runCatching { c.call(c.deviceUrl, req) }
                r.onSuccess { x ->
                    println(
                        "REALINFO ${req.operation}: home=${x.textOf("HomeSupported")} presets=${x.textOf("MaximumNumberOfPresets")} " +
                            "spaces=${x.findAll("URI").map {
                                it.text.substringAfterLast('/')
                            }.distinct()} timeoutRange=${x.find("PTZTimeout")?.let { it.textOf("Min") + ".." + it.textOf("Max") }}",
                    )
                }
                r.onFailure { println("REALINFO ${req.operation}: FALHOU ${it.message}") }
            }
        }
}
