package io.github.mbsantos97.camerasaovivo.onvif.soap

import java.util.Locale

/** Corpo SOAP de uma operação ONVIF + a action (para o cabeçalho). */
data class OnvifRequest(
    val ns: String,
    val operation: String,
    val body: String,
) {
    val action: String get() = "$ns/$operation"
}

/** Templates XML escritos à mão (sem bibliotecas ONVIF pesadas). */
object Requests {
    private fun op(
        ns: String,
        prefix: String,
        name: String,
        inner: String = "",
    ) = OnvifRequest(
        ns,
        name,
        if (inner.isEmpty()) {
            "<$prefix:$name xmlns:$prefix=\"$ns\"/>"
        } else {
            "<$prefix:$name xmlns:$prefix=\"$ns\" xmlns:tt=\"${Ns.SCHEMA}\">$inner</$prefix:$name>"
        },
    )

    private fun f(v: Float) = String.format(Locale.ROOT, "%.3f", v)

    // Device
    fun getSystemDateAndTime() = op(Ns.DEVICE, "tds", "GetSystemDateAndTime")

    fun getDeviceInformation() = op(Ns.DEVICE, "tds", "GetDeviceInformation")

    fun getCapabilities() = op(Ns.DEVICE, "tds", "GetCapabilities", "<tds:Category>All</tds:Category>")

    fun getServices() = op(Ns.DEVICE, "tds", "GetServices", "<tds:IncludeCapability>false</tds:IncludeCapability>")

    fun getNetworkInterfaces() = op(Ns.DEVICE, "tds", "GetNetworkInterfaces")

    // Media 1
    fun getProfiles() = op(Ns.MEDIA, "trt", "GetProfiles")

    fun getStreamUri(profileToken: String) =
        op(
            Ns.MEDIA,
            "trt",
            "GetStreamUri",
            "<trt:StreamSetup><tt:Stream>RTP-Unicast</tt:Stream><tt:Transport><tt:Protocol>RTSP</tt:Protocol></tt:Transport></trt:StreamSetup>" +
                "<trt:ProfileToken>${escape(profileToken)}</trt:ProfileToken>",
        )

    fun getSnapshotUri(profileToken: String) = op(Ns.MEDIA, "trt", "GetSnapshotUri", "<trt:ProfileToken>${escape(profileToken)}</trt:ProfileToken>")

    // Media 2
    fun getProfiles2() = op(Ns.MEDIA2, "tr2", "GetProfiles", "<tr2:Type>All</tr2:Type>")

    fun getStreamUri2(profileToken: String) =
        op(Ns.MEDIA2, "tr2", "GetStreamUri", "<tr2:Protocol>RTSP</tr2:Protocol><tr2:ProfileToken>${escape(profileToken)}</tr2:ProfileToken>")

    // PTZ
    fun getNodes() = op(Ns.PTZ, "tptz", "GetNodes")

    fun getPtzConfigurations() = op(Ns.PTZ, "tptz", "GetConfigurations")

    fun getStatus(profileToken: String) = op(Ns.PTZ, "tptz", "GetStatus", "<tptz:ProfileToken>${escape(profileToken)}</tptz:ProfileToken>")

    fun continuousMove(
        profileToken: String,
        x: Float,
        y: Float,
        timeout: String?,
    ) = op(
        Ns.PTZ,
        "tptz",
        "ContinuousMove",
        "<tptz:ProfileToken>${escape(profileToken)}</tptz:ProfileToken>" +
            "<tptz:Velocity><tt:PanTilt x=\"${f(x)}\" y=\"${f(y)}\"/></tptz:Velocity>" +
            (timeout?.let { "<tptz:Timeout>$it</tptz:Timeout>" } ?: ""),
    )

    fun stop(profileToken: String) =
        op(
            Ns.PTZ,
            "tptz",
            "Stop",
            "<tptz:ProfileToken>${escape(profileToken)}</tptz:ProfileToken><tptz:PanTilt>true</tptz:PanTilt><tptz:Zoom>false</tptz:Zoom>",
        )

    fun relativeMove(
        profileToken: String,
        x: Float,
        y: Float,
    ) = op(
        Ns.PTZ,
        "tptz",
        "RelativeMove",
        "<tptz:ProfileToken>${escape(profileToken)}</tptz:ProfileToken><tptz:Translation><tt:PanTilt x=\"${f(x)}\" y=\"${f(y)}\"/></tptz:Translation>",
    )

    fun getPresets(profileToken: String) = op(Ns.PTZ, "tptz", "GetPresets", "<tptz:ProfileToken>${escape(profileToken)}</tptz:ProfileToken>")

    fun gotoPreset(
        profileToken: String,
        presetToken: String,
    ) = op(
        Ns.PTZ,
        "tptz",
        "GotoPreset",
        "<tptz:ProfileToken>${escape(profileToken)}</tptz:ProfileToken><tptz:PresetToken>${escape(presetToken)}</tptz:PresetToken>",
    )

    fun setPreset(
        profileToken: String,
        name: String,
        presetToken: String? = null,
    ) = op(
        Ns.PTZ,
        "tptz",
        "SetPreset",
        "<tptz:ProfileToken>${escape(profileToken)}</tptz:ProfileToken><tptz:PresetName>${escape(name)}</tptz:PresetName>" +
            (presetToken?.let { "<tptz:PresetToken>${escape(it)}</tptz:PresetToken>" } ?: ""),
    )

    // Events
    fun createPullPointSubscription(termination: String = "PT120S") =
        op(Ns.EVENTS, "tev", "CreatePullPointSubscription", "<tev:InitialTerminationTime>$termination</tev:InitialTerminationTime>")

    fun pullMessages(
        timeout: String = "PT5S",
        limit: Int = 10,
    ) = op(Ns.EVENTS, "tev", "PullMessages", "<tev:Timeout>$timeout</tev:Timeout><tev:MessageLimit>$limit</tev:MessageLimit>")

    fun unsubscribe() = OnvifRequest(Ns.WSNT, "Unsubscribe", "<wsnt:Unsubscribe xmlns:wsnt=\"${Ns.WSNT}\"/>")

    /** Cabeçalho WS-Addressing exigido pelo PullMessages/Unsubscribe em várias câmeras. */
    fun addressingHeader(
        to: String,
        action: String,
    ) = "<wsa:Action xmlns:wsa=\"${Ns.WSA}\">$action</wsa:Action><wsa:To xmlns:wsa=\"${Ns.WSA}\">${escape(to)}</wsa:To>"

    // Search (Profile G)
    fun getRecordingSummary() = op(Ns.SEARCH, "tse", "GetRecordingSummary")

    fun findRecordings(keepAlive: String = "PT60S") = op(Ns.SEARCH, "tse", "FindRecordings", "<tse:Scope/><tse:KeepAliveTime>$keepAlive</tse:KeepAliveTime>")

    fun getRecordingSearchResults(
        searchToken: String,
        wait: String = "PT5S",
    ) = op(
        Ns.SEARCH,
        "tse",
        "GetRecordingSearchResults",
        "<tse:SearchToken>${escape(searchToken)}</tse:SearchToken><tse:MinResults>1</tse:MinResults><tse:WaitTime>$wait</tse:WaitTime>",
    )

    fun getRecordingInformation(recordingToken: String) =
        op(Ns.SEARCH, "tse", "GetRecordingInformation", "<tse:RecordingToken>${escape(recordingToken)}</tse:RecordingToken>")

    fun findEvents(
        startUtc: String,
        endUtc: String,
        recordingToken: String?,
        keepAlive: String = "PT60S",
    ) = op(
        Ns.SEARCH,
        "tse",
        "FindEvents",
        "<tse:StartPoint>$startUtc</tse:StartPoint><tse:EndPoint>$endUtc</tse:EndPoint>" +
            "<tse:Scope>${recordingToken?.let { "<tt:IncludedRecordings>${escape(it)}</tt:IncludedRecordings>" } ?: ""}</tse:Scope>" +
            "<tse:SearchFilter/><tse:IncludeStartState>false</tse:IncludeStartState><tse:KeepAliveTime>$keepAlive</tse:KeepAliveTime>",
    )

    fun getEventSearchResults(
        searchToken: String,
        wait: String = "PT5S",
    ) = op(
        Ns.SEARCH,
        "tse",
        "GetEventSearchResults",
        "<tse:SearchToken>${escape(searchToken)}</tse:SearchToken><tse:MinResults>1</tse:MinResults><tse:WaitTime>$wait</tse:WaitTime>",
    )

    // Replay
    fun getReplayUri(recordingToken: String) =
        op(
            Ns.REPLAY,
            "trp",
            "GetReplayUri",
            "<trp:StreamSetup><tt:Stream>RTP-Unicast</tt:Stream><tt:Transport><tt:Protocol>RTSP</tt:Protocol></tt:Transport></trp:StreamSetup>" +
                "<trp:RecordingToken>${escape(recordingToken)}</trp:RecordingToken>",
        )
}
