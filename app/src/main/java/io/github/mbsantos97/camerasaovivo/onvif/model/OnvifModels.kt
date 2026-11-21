package io.github.mbsantos97.camerasaovivo.onvif.model

data class DeviceInfo(
    val manufacturer: String? = null,
    val model: String? = null,
    val firmware: String? = null,
    val serial: String? = null,
    val hardwareId: String? = null,
)

/** Endereços dos serviços (já reescritos para o host:porta configurados). */
data class ServiceAddresses(
    val device: String? = null,
    val media: String? = null,
    val media2: String? = null,
    val ptz: String? = null,
    val events: String? = null,
    val search: String? = null,
    val replay: String? = null,
    val recording: String? = null,
) {
    val profileG: Boolean get() = search != null && replay != null

    fun merge(o: ServiceAddresses) =
        ServiceAddresses(
            device ?: o.device,
            media ?: o.media,
            media2 ?: o.media2,
            ptz ?: o.ptz,
            events ?: o.events,
            search ?: o.search,
            replay ?: o.replay,
            recording ?: o.recording,
        )
}

data class MediaProfile(
    val token: String,
    val name: String? = null,
    val videoSourceToken: String? = null,
    val encoding: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val fps: Float = 0f,
    val hasPtz: Boolean = false,
) {
    val pixels: Int get() = width * height
}

data class PtzPreset(
    val token: String,
    val name: String?,
)

data class OnvifFault(
    val code: String?,
    val subcode: String?,
    val reason: String?,
    val subcodes: List<String> = listOfNotNull(subcode),
) {
    private val all: List<String> get() = listOfNotNull(code, reason) + subcodes

    val notAuthorized: Boolean
        get() = all.any { it.contains("NotAuthorized", true) || it.contains("Not Authorized", true) }
    val actionNotSupported: Boolean
        get() = all.any { it.contains("ActionNotSupported", true) || it.contains("not supported", true) || it.contains("Not Implemented", true) }
}

data class RecordingTrack(
    val token: String,
    val type: String?,
    val fromMillis: Long?,
    val toMillis: Long?,
)

data class RecordingInfo(
    val token: String,
    val earliestMillis: Long?,
    val latestMillis: Long?,
    val tracks: List<RecordingTrack>,
)

data class RecordingSummary(
    val fromMillis: Long?,
    val untilMillis: Long?,
    val count: Int,
)

data class RecordingEvent(
    val recordingToken: String?,
    val timeMillis: Long?,
    val topic: String?,
    val isStart: Boolean?,
)

data class MotionEvent(
    val topic: String,
    val active: Boolean,
)

data class DiscoveredDevice(
    val host: String,
    val port: Int?,
    val xaddr: String?,
    val name: String? = null,
    val hardware: String? = null,
    val viaDiscovery: Boolean = false,
    val openPorts: Set<Int> = emptySet(),
)
