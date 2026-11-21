package io.github.mbsantos97.camerasaovivo.camera.events

import io.github.mbsantos97.camerasaovivo.core.logging.AppLogger
import io.github.mbsantos97.camerasaovivo.onvif.OnvifClient
import io.github.mbsantos97.camerasaovivo.onvif.parser.EventsParser
import io.github.mbsantos97.camerasaovivo.onvif.soap.Ns
import io.github.mbsantos97.camerasaovivo.onvif.soap.Requests
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Movimento via ONVIF Events (PullPoint): CreatePullPointSubscription e PullMessages em laço.
 * Chama [onMotion] a cada evento "movimento = verdadeiro". Sem suporte, a função fica oculta.
 */
class MotionEventMonitor(
    private val client: OnvifClient,
    private val eventsUrl: String,
    private val scope: CoroutineScope,
    private val onMotion: () -> Unit,
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { loop() }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun loop() {
        var failures = 0
        while (scope.isActive) {
            val address =
                runCatching {
                    if (!client.clockSynced) client.syncClock()
                    EventsParser.subscriptionAddress(client.authed(eventsUrl, Requests.createPullPointSubscription()))
                }.getOrNull()?.let { client.fixXAddr(it) }
            if (address == null) {
                failures++
                if (failures >= MAX_FAILURES) {
                    AppLogger.i(TAG, "${client.host}: eventos indisponíveis; monitor desligado")
                    return
                }
                delay(RETRY_MS * failures)
                continue
            }
            failures = 0
            val pull = Requests.pullMessages(timeout = "PT3S")
            var pullFailures = 0
            while (scope.isActive && pullFailures < MAX_PULL_FAILURES) {
                val r =
                    runCatching {
                        client.call(address, pull, extraHeader = Requests.addressingHeader(address, "${Ns.EVENTS}/PullPointSubscription/PullMessagesRequest"))
                    }
                r.onSuccess { root ->
                    pullFailures = 0
                    if (EventsParser.motion(root).any { it.active }) onMotion()
                }
                r.onFailure {
                    pullFailures++
                    delay(RETRY_MS)
                }
            }
            runCatching {
                client.call(
                    address,
                    Requests.unsubscribe(),
                    extraHeader = Requests.addressingHeader(address, "${Ns.WSNT}/SubscriptionManager/UnsubscribeRequest"),
                )
            }
        }
    }

    private companion object {
        const val TAG = "Motion"
        const val RETRY_MS = 5_000L
        const val MAX_FAILURES = 3
        const val MAX_PULL_FAILURES = 3
    }
}
