# Licenças de terceiros

O Câmeras ao Vivo usa as bibliotecas abaixo. Todas são incluídas como dependências binárias (AAR/JAR), sem alteração.

| Biblioteca | Uso | Licença |
|---|---|---|
| LibVLC for Android (`org.videolan.android:libvlc-all`) | Player RTSP, decodificação H.264/H.265, G.711, gravação por remux | LGPL 2.1 ou posterior. Linkagem dinâmica (bibliotecas `.so` separadas dentro do AAR), o que atende à LGPL. Código-fonte: https://code.videolan.org/videolan/vlc-android |
| AndroidX (Compose, Compose for TV `tv-material`, Activity, Lifecycle, Navigation, DataStore, Core, SplashScreen, ProfileInstaller, Metrics) | Interface e infraestrutura | Apache 2.0 |
| Kotlin, kotlinx.coroutines, kotlinx.serialization | Linguagem e bibliotecas | Apache 2.0 |
| OkHttp | Cliente HTTP do ONVIF | Apache 2.0 |
| NanoHTTPD | Servidor da tela "Configurar pelo celular" | BSD 3-Clause |
| ZXing core | Geração do QR code | Apache 2.0 |
| Material Symbols / Material Icons (traçados dos ícones) | Ícones vetoriais avulsos | Apache 2.0 |

Somente em desenvolvimento (não vão no APK de release): JUnit (EPL 1.0), Robolectric (MIT), Turbine (Apache 2.0), MockWebServer (Apache 2.0), kXML2 (BSD), LeakCanary (Apache 2.0, só no debug), ktlint (MIT).
