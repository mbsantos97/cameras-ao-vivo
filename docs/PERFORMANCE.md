# Desempenho

Medido em 21/11/2026 na **Xiaomi TV Box S (2ª geração, MiTV-AFKR0, Amlogic "sc2", 4 núcleos, 2 GB, Android 11, userspace armeabi-v7a)**, Wi-Fi, com as câmeras reais (lente dupla 1920x2160 H.265 + lâmpada 1920x1080 H.265), APK release com R8.

| Métrica | Meta | Medido | Como |
|---|---|---|---|
| Cold start até a grade visível | ≤ 1,5 s | **657–935 ms** | `adb shell am start -W` (TotalTime), 5 aberturas |
| Cold start até o primeiro vídeo | ≤ 3 s | **5–12 s** ✗ | captura de tela; limitado pela câmera: o H.265 só começa no quadro-chave (VPS/SPS/PPS vêm só nele) |
| Warm start | ≤ 0,8 s | **165–170 ms** | Home → `am start -W` |
| Grade → tela cheia | imagem instantânea; principal ≤ 1,5 s | imagem **instantânea** (substream ampliado); principal em **~3–10 s** ✗ | captura de tela; mesmo motivo (quadro-chave) |
| Tecla → comando PTZ enviado | ≤ 50 ms | fila serial sem espera; resposta da câmera ~240–700 ms | `PtzController.lastLatencyMs` / teste real |
| Navegação e foco | 60 fps; < 1% acima de 32 ms | p50 **5 ms**, p90 **8 ms**, **4,2%** acima de 32 ms ✗ | `dumpsys gfxinfo` com navegação pelo D-pad |
| RAM (PSS) com as câmeras na grade | ≤ 300 MB | **73–96 MB** | `dumpsys meminfo` |
| CPU com as câmeras na grade | ≤ 40% do total | **~26–31% de um núcleo ≈ 7–8% do total** | `top` (decodificação H.265 por hardware, `OMX.amlogic.hevc.decoder`) |
| Segundo plano | 0% de CPU | players liberados em `onStop` | ciclo de vida |
| APK por ABI | ≤ 35 MB | armeabi-v7a **42 MB**, arm64 57 MB ✗ | `useLegacyPackaging=false` (ver abaixo) |

## Justificativas dos itens fora da meta
- **Primeiro vídeo e principal em tela cheia**: o LibVLC recebe dados em milissegundos, mas as câmeras Yoosee só mandam os parâmetros do H.265 junto do quadro-chave, a cada alguns segundos. Reduzir o intervalo de quadro-chave exigiria mudar a configuração do encoder da câmera, que o firmware não permite pelo ONVIF. A grade nunca fica preta: mostra a última miniatura com "Conectando…".
- **4,2% de quadros lentos**: concentrados na animação de escala do foco sobre o vídeo (TextureView). Próximo passo: aplicar a escala só ao anel/rótulos ou reduzir a duração; medir de novo.
- **APK de 42 MB**: com bibliotecas comprimidas o APK teria ~22 MB, mas instalado ocuparia ~61 MB (o Android extrai as `.so`); sem compressão ocupa ~42 MB e instala/abre mais rápido. A TV Box real tem 4,3 GB e estava com 91 MB livres: o espaço ocupado importa mais.
- **Baseline Profile próprio**: a geração exige root ou Android 13+; a TV Box é Android 11 sem root e não há emulador nesta máquina (Hypervisor Platform desligado). O APK inclui `profileinstaller` e os perfis das bibliotecas (Compose).

## Como medir de novo
```
adb connect IP:5555
adb shell am force-stop io.github.mbsantos97.camerasaovivo && adb shell am start -W -n io.github.mbsantos97.camerasaovivo/.MainActivity
adb shell dumpsys meminfo io.github.mbsantos97.camerasaovivo
adb shell dumpsys gfxinfo io.github.mbsantos97.camerasaovivo reset   # navegue e depois:
adb shell dumpsys gfxinfo io.github.mbsantos97.camerasaovivo
adb shell top -b -n 1 | grep aoVivo
```
No app: Diagnóstico → **Painel de desempenho** (FPS da interface, RAM, streams com decoder HW/SW e latência do PTZ).
