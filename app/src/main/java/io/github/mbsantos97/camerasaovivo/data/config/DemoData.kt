package io.github.mbsantos97.camerasaovivo.data.config

/** Câmeras falsas do modo demonstração (sem rede): reproduzem o cenário real da casa. */
object DemoData {
    const val PREFIX = "demo-"

    fun cameras(): List<CameraConfig> =
        listOf(
            CameraConfig(
                id = "${PREFIX}externa",
                name = "Câmera Externa",
                host = "127.0.0.1",
                order = 0,
                demo = true,
                streams =
                    CachedStreams(
                        sources = listOf(SourceStreams(mainWidth = 1280, mainHeight = 1440, subWidth = 640, subHeight = 720, stacked = true)),
                        ptzSupported = true,
                        presetsSupported = true,
                    ),
            ),
            CameraConfig(
                id = "${PREFIX}lampada",
                name = "Lâmpada",
                host = "127.0.0.1",
                order = 1,
                demo = true,
                streams =
                    CachedStreams(
                        sources = listOf(SourceStreams(mainWidth = 1920, mainHeight = 1080, subWidth = 640, subHeight = 360)),
                        ptzSupported = true,
                    ),
            ),
        )

    fun config(): AppConfig = AppConfig(cameras = cameras(), settings = AppSettings(showClock = true, demoMode = true, firstRunDone = true))
}
