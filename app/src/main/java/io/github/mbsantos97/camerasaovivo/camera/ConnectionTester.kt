package io.github.mbsantos97.camerasaovivo.camera

import androidx.compose.runtime.Immutable
import io.github.mbsantos97.camerasaovivo.core.network.SubnetScanner
import io.github.mbsantos97.camerasaovivo.data.config.CameraConfig
import io.github.mbsantos97.camerasaovivo.onvif.OnvifClient
import io.github.mbsantos97.camerasaovivo.onvif.OnvifException
import io.github.mbsantos97.camerasaovivo.rtsp.RtspProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.math.abs

enum class StepStatus { RUNNING, OK, FAIL, SKIPPED }

@Immutable
data class TestStep(
    val title: String,
    val status: StepStatus,
    val detail: String? = null,
)

/**
 * Teste de conexão passo a passo (botão "Testar conexão" e tela de Diagnóstico).
 * Emite a lista inteira a cada passo, para a UI mostrar o progresso.
 */
class ConnectionTester(
    private val onvifFactory: OnvifFactory,
    private val resolver: StreamResolver,
) {
    /** Último resultado bem-sucedido (para salvar streams/capacidades na câmera). */
    var lastResult: ResolveResult? = null
        private set

    /** Cliente usado no último teste (respostas cruas para exportar no Diagnóstico). */
    var lastClient: OnvifClient? = null
        private set

    fun run(
        cam: CameraConfig,
        password: String,
    ): Flow<List<TestStep>> =
        flow {
            val steps = mutableListOf<TestStep>()

            suspend fun step(
                title: String,
                block: suspend () -> String?,
            ): Boolean {
                steps += TestStep(title, StepStatus.RUNNING)
                emit(steps.toList())
                val r = runCatching { block() }
                steps[steps.lastIndex] =
                    r.fold(
                        { TestStep(title, StepStatus.OK, it) },
                        { TestStep(title, StepStatus.FAIL, friendly(it)) },
                    )
                emit(steps.toList())
                return r.isSuccess
            }
            lastResult = null
            val rtspOpen =
                step("Porta RTSP ${cam.rtspPort}") {
                    check(SubnetScanner.isOpen(cam.host, cam.rtspPort, 1500)) { "Fechada ou sem resposta" }
                    "Aberta"
                }
            val onvifOpen =
                step("Porta ONVIF ${cam.onvifPort}") {
                    check(SubnetScanner.isOpen(cam.host, cam.onvifPort, 1500)) { "Fechada ou sem resposta" }
                    "Aberta"
                }
            val client = onvifFactory(cam, password)
            lastClient = client
            if (onvifOpen) {
                step("Relógio da câmera") {
                    client.syncClock() ?: error("Sem data/hora")
                    val off = client.clockOffsetMs / 1000
                    if (abs(off) < 5) "Em dia" else "Diferença de ${formatOffset(off)} (compensada)"
                }
                step("Informações do aparelho") {
                    val i = client.deviceInformation()
                    listOfNotNull(i.manufacturer, i.model, i.firmware?.let { "firmware $it" }).joinToString(" · ").ifEmpty { "Sem detalhes" }
                }
                step("Serviços") {
                    val s = client.discoverServices()
                    listOfNotNull(
                        "Media".takeIf { s.media != null || s.media2 != null },
                        "PTZ".takeIf { s.ptz != null },
                        "Eventos".takeIf { s.events != null },
                        "Gravação".takeIf { s.recording != null },
                        "Busca".takeIf { s.search != null },
                        "Replay".takeIf { s.replay != null },
                    ).joinToString(", ").ifEmpty { "Nenhum anunciado" }
                }
                step("Perfis de vídeo") {
                    client.profiles().joinToString("; ") {
                        "${it.name ?: it.token}: ${it.width}x${it.height} ${it.encoding ?: ""}".trim()
                    }.ifEmpty { error("Nenhum perfil") }
                }
            } else {
                steps += TestStep("ONVIF", StepStatus.SKIPPED, "Porta ONVIF fechada: ative a \"Conexão NVR\" no app Yoosee")
                emit(steps.toList())
            }
            var mainUri: String? = null
            step("Endereços dos streams") {
                val r = resolver.resolve(cam, password)
                lastResult = r
                val s = r.streams.sources
                mainUri = s.firstOrNull()?.mainUri
                val stacked = if (s.any { it.stacked }) " · lente dupla empilhada" else ""
                "${s.size} fonte(s)${if (r.viaOnvif) " via ONVIF" else " por caminho conhecido"}$stacked"
            }
            val uri = mainUri
            if (rtspOpen && uri != null) {
                step("RTSP (DESCRIBE)") {
                    val r = RtspProbe.describe(uri, cam.username, password)
                    when (r.status) {
                        200 ->
                            listOfNotNull(
                                r.sdp?.video?.codec?.let {
                                    "vídeo $it"
                                },
                                r.sdp?.audio?.codec?.let { "áudio $it" },
                            ).joinToString(", ").ifEmpty { "OK" }
                        401 -> error("Usuário ou senha incorretos (401)")
                        404 -> error("Caminho não encontrado (404)")
                        else -> error("RTSP respondeu ${r.status}")
                    }
                }
            }
        }.flowOn(Dispatchers.IO)

    companion object {
        fun formatOffset(seconds: Long): String {
            val a = abs(seconds)
            return when {
                a >= 86_400 -> "${a / 86_400} dia(s)"
                a >= 3_600 -> "${a / 3_600} h"
                a >= 60 -> "${a / 60} min"
                else -> "$a s"
            }
        }

        fun friendly(t: Throwable): String =
            when {
                t is OnvifException && t.fault?.notAuthorized == true -> "Usuário ou senha incorretos"
                t is OnvifException && t.fault?.actionNotSupported == true -> "Não suportado por esta câmera"
                t is java.net.SocketTimeoutException -> "Sem resposta (tempo esgotado)"
                t is java.net.ConnectException -> "Conexão recusada"
                t is java.net.UnknownHostException -> "Endereço inválido"
                else -> t.message ?: t.javaClass.simpleName
            }
    }
}
