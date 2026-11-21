package io.github.mbsantos97.camerasaovivo.onvif

import io.github.mbsantos97.camerasaovivo.core.network.IpUtils
import io.github.mbsantos97.camerasaovivo.core.network.SubnetScanner
import io.github.mbsantos97.camerasaovivo.onvif.discovery.WsDiscovery
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Busca câmeras e TV Box na rede local a partir do PC, com o mesmo código do app.
 * Uso: ./gradlew :app:testDebugUnitTest --tests "*LanProbe*" -PlanProbe -i
 */
class LanProbeTest {
    @Test
    fun `procura cameras e TV Box na rede`() =
        runBlocking {
            assumeTrue(System.getProperty("lanProbe") == "true")
            TestXml.install()
            val ip = IpUtils.localIpv4() ?: error("Sem IPv4 local")
            println("LANPROBE PC=$ip")
            WsDiscovery(null).probe(4_000).forEach {
                println("LANPROBE WSD host=${it.host} port=${it.port} name=${it.name} hw=${it.hardware} xaddr=${it.xaddr}")
            }
            SubnetScanner.scan(ip, listOf(5000, 554, 5555, 8000, 80), timeoutMs = 400).forEach {
                println("LANPROBE SCAN host=${it.host} ports=${it.ports.sorted()}")
            }
        }
}
