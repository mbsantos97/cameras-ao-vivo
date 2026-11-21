package io.github.mbsantos97.camerasaovivo.onvif.parser

import io.github.mbsantos97.camerasaovivo.onvif.model.DeviceInfo
import io.github.mbsantos97.camerasaovivo.onvif.model.MediaProfile
import io.github.mbsantos97.camerasaovivo.onvif.model.MotionEvent
import io.github.mbsantos97.camerasaovivo.onvif.model.OnvifFault
import io.github.mbsantos97.camerasaovivo.onvif.model.PtzPreset
import io.github.mbsantos97.camerasaovivo.onvif.model.RecordingEvent
import io.github.mbsantos97.camerasaovivo.onvif.model.RecordingInfo
import io.github.mbsantos97.camerasaovivo.onvif.model.RecordingSummary
import io.github.mbsantos97.camerasaovivo.onvif.model.RecordingTrack
import io.github.mbsantos97.camerasaovivo.onvif.model.ServiceAddresses
import io.github.mbsantos97.camerasaovivo.onvif.soap.Ns
import java.util.Calendar
import java.util.TimeZone

/** xs:dateTime ("2026-11-20T03:04:05Z", com fração ou fuso) → epoch ms. */
object XsdTime {
    private val re = Regex("""(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})(?:\.(\d+))?(Z|[+-]\d{2}:?\d{2})?""")

    fun parse(text: String?): Long? {
        val m = re.find(text?.trim() ?: return null) ?: return null
        val g = m.groupValues
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(g[1].toInt(), g[2].toInt() - 1, g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].toInt())
        var millis = cal.timeInMillis + (g[7].take(3).padEnd(3, '0').toIntOrNull() ?: 0)
        val tz = g[8]
        if (tz.isNotEmpty() && tz != "Z") {
            val sign = if (tz[0] == '-') -1 else 1
            val digits = tz.drop(1).replace(":", "")
            val offMin = digits.take(2).toInt() * 60 + digits.drop(2).toInt()
            millis -= sign * offMin * 60_000L
        }
        return millis
    }

    fun format(millis: Long): String = io.github.mbsantos97.camerasaovivo.onvif.soap.WsSecurity.created(millis)
}

object FaultParser {
    fun parse(root: XmlNode): OnvifFault? {
        val f = root.find("Fault") ?: return null
        val code = f.path("Code", "Value")?.text?.trim() ?: f.textOf("faultcode")
        val subs = f.find("Subcode")?.findAll("Value")?.map { it.text.trim() }.orEmpty()
        val reason = f.path("Reason", "Text")?.text?.trim() ?: f.textOf("faultstring")
        return OnvifFault(code, subs.lastOrNull(), reason, subs)
    }
}

object DeviceParser {
    /** Hora UTC da câmera em epoch ms (UTCDateTime; se faltar, LocalDateTime no fuso do aparelho). */
    fun systemTime(root: XmlNode): Long? {
        val sdt = root.find("SystemDateAndTime") ?: return null
        val utc = sdt.child("UTCDateTime")
        val node = utc ?: sdt.child("LocalDateTime") ?: return null
        val d = node.child("Date") ?: return null
        val t = node.child("Time") ?: return null

        fun n(
            x: XmlNode,
            name: String,
        ) = x.child(name)?.text?.trim()?.toIntOrNull() ?: 0
        val cal = Calendar.getInstance(if (utc != null) TimeZone.getTimeZone("UTC") else TimeZone.getDefault())
        cal.clear()
        cal.set(n(d, "Year"), n(d, "Month") - 1, n(d, "Day"), n(t, "Hour"), n(t, "Minute"), n(t, "Second"))
        return cal.timeInMillis
    }

    fun deviceInfo(root: XmlNode): DeviceInfo {
        val r = root.find("GetDeviceInformationResponse") ?: root
        return DeviceInfo(
            manufacturer = r.textOf("Manufacturer"),
            model = r.textOf("Model"),
            firmware = r.textOf("FirmwareVersion"),
            serial = r.textOf("SerialNumber"),
            hardwareId = r.textOf("HardwareId"),
        )
    }

    fun capabilities(root: XmlNode): ServiceAddresses {
        val c = root.find("Capabilities") ?: return ServiceAddresses()

        fun x(vararg path: String) = c.path(*path)?.child("XAddr")?.text?.trim()?.ifEmpty { null }
        return ServiceAddresses(
            device = x("Device"),
            media = x("Media"),
            ptz = x("PTZ"),
            events = x("Events"),
            search = x("Extension", "Search") ?: x("Search"),
            replay = x("Extension", "Replay") ?: x("Replay"),
            recording = x("Extension", "Recording") ?: x("Recording"),
        )
    }

    fun services(root: XmlNode): ServiceAddresses {
        val map = root.findAll("Service").mapNotNull { s -> s.textOf("Namespace")?.let { it to s.textOf("XAddr") } }.toMap()
        return ServiceAddresses(
            device = map[Ns.DEVICE],
            media = map[Ns.MEDIA],
            media2 = map[Ns.MEDIA2],
            ptz = map[Ns.PTZ],
            events = map[Ns.EVENTS],
            search = map[Ns.SEARCH],
            replay = map[Ns.REPLAY],
            recording = map[Ns.RECORDING],
        )
    }

    fun mac(root: XmlNode): String? = root.findAll("HwAddress").firstNotNullOfOrNull { it.text.trim().ifEmpty { null } }
}

object MediaParser {
    fun profiles(root: XmlNode): List<MediaProfile> =
        root.findAll("Profiles").mapNotNull { p ->
            val token = p.attr("token") ?: p.textOf("token") ?: return@mapNotNull null
            val media2 = p.child("Configurations") != null
            val cfg = if (media2) p.child("Configurations")!! else p
            val vsc = cfg.child(if (media2) "VideoSource" else "VideoSourceConfiguration")
            val vec = cfg.child(if (media2) "VideoEncoder" else "VideoEncoderConfiguration")
            val res = vec?.child("Resolution")
            val rate = vec?.child("RateControl")
            MediaProfile(
                token = token,
                name = p.child("Name")?.text?.trim(),
                videoSourceToken = vsc?.child("SourceToken")?.text?.trim(),
                encoding = vec?.child("Encoding")?.text?.trim(),
                width = res?.child("Width")?.text?.trim()?.toIntOrNull() ?: 0,
                height = res?.child("Height")?.text?.trim()?.toIntOrNull() ?: 0,
                fps = rate?.child("FrameRateLimit")?.text?.trim()?.toFloatOrNull() ?: rate?.attr("FrameRateLimit")?.toFloatOrNull() ?: 0f,
                hasPtz = cfg.child(if (media2) "PTZ" else "PTZConfiguration") != null,
            )
        }

    /** Media1: MediaUri/Uri; Media2: GetStreamUriResponse/Uri. */
    fun uri(root: XmlNode): String? = (root.find("MediaUri")?.child("Uri") ?: root.find("Uri"))?.text?.trim()?.ifEmpty { null }
}

object PtzParser {
    fun presets(root: XmlNode): List<PtzPreset> = root.findAll("Preset").mapNotNull { p -> p.attr("token")?.let { PtzPreset(it, p.textOf("Name")) } }

    fun maxPresets(root: XmlNode): Int = root.textOf("MaximumNumberOfPresets")?.toIntOrNull() ?: 0
}

object EventsParser {
    fun subscriptionAddress(root: XmlNode): String? = root.find("SubscriptionReference")?.textOf("Address")

    /** Mensagens de movimento: tópico com "Motion" ou item IsMotion/State/MotionActive. */
    fun motion(root: XmlNode): List<MotionEvent> =
        root.findAll("NotificationMessage").mapNotNull { m ->
            val topic = m.child("Topic")?.text?.trim().orEmpty()
            val items = m.findAll("SimpleItem").associate { (it.attr("Name") ?: "") to (it.attr("Value") ?: "") }
            val key = items.keys.firstOrNull { it.equals("IsMotion", true) || it.equals("MotionActive", true) || it.equals("State", true) }
            val looksMotion = topic.contains("Motion", true) || (key != null && key != "State")
            if (!looksMotion || key == null) return@mapNotNull null
            MotionEvent(topic, items[key].equals("true", true) || items[key] == "1")
        }
}

object SearchParser {
    fun summary(root: XmlNode): RecordingSummary? {
        val s = root.find("Summary") ?: return null
        return RecordingSummary(XsdTime.parse(s.textOf("DataFrom")), XsdTime.parse(s.textOf("DataUntil")), s.textOf("NumberRecordings")?.toIntOrNull() ?: 0)
    }

    fun searchToken(root: XmlNode): String? = root.textOf("SearchToken")

    fun searchState(root: XmlNode): String? = root.textOf("SearchState")

    fun recordings(root: XmlNode): List<RecordingInfo> =
        root.findAll("RecordingInformation").mapNotNull { r ->
            val token = r.child("RecordingToken")?.text?.trim() ?: return@mapNotNull null
            RecordingInfo(
                token = token,
                earliestMillis = XsdTime.parse(r.child("EarliestRecording")?.text),
                latestMillis = XsdTime.parse(r.child("LatestRecording")?.text),
                tracks =
                    r.childrenNamed("Track").map { t ->
                        RecordingTrack(
                            t.textOf("TrackToken") ?: "",
                            t.textOf("TrackType"),
                            XsdTime.parse(t.textOf("DataFrom")),
                            XsdTime.parse(t.textOf("DataTo")),
                        )
                    },
            )
        }

    fun events(root: XmlNode): List<RecordingEvent> =
        root.findAll("Result").map { r ->
            val st = r.child("StartStateEvent")?.text?.trim()
            RecordingEvent(
                recordingToken = r.child("RecordingToken")?.text?.trim(),
                timeMillis = XsdTime.parse(r.child("Time")?.text),
                topic = r.find("Topic")?.text?.trim(),
                isStart =
                    r.findAll("SimpleItem").firstOrNull {
                        (it.attr("Name") ?: "").let { n ->
                            n.contains("State", true) || n.contains("Motion", true) ||
                                n.contains("DataPresent", true)
                        }
                    }
                        ?.attr("Value")
                        ?.equals("true", true)
                        ?: st?.equals("true", true),
            )
        }
}

object ReplayParser {
    fun uri(root: XmlNode): String? = root.find("GetReplayUriResponse")?.textOf("Uri") ?: root.textOf("Uri")
}
