# Limitações conhecidas

## Resultado com as câmeras reais (21/11/2026)

Testadas a partir do PC, na rede da casa, com o código do próprio app (`RealCameraTest`, `RealPtzTest`) e capturas com ffmpeg.

| | Lâmpada (8177) | Câmera Externa (lente dupla) |
|---|---|---|
| Fabricante / modelo (ONVIF) | "Technology" / "IPC" | "Technology" / "IPC" |
| Firmware | 40.01.07 | 46.00.50 |
| Perfil principal | 1920x1080 | **1920x2160 (duas lentes empilhadas)** |
| Perfil sub | 640x360 | 864x976 real, mas o ONVIF informa **864x488** |
| Codec | ONVIF diz H.264; o SDP e o stream são **H.265** | idem |
| Áudio | PCMA (G.711 A-law) 16 kHz no principal | idem |
| RTSP | `/onvif1` e `/onvif2`, autenticação Digest, **só UDP** (TCP falha) | idem |
| PTZ | ContinuousMove funciona; **Stop é recusado** (a câmera derruba a conexão); parada com velocidade zero funciona | idem; a **lente de baixo** é a motorizada |
| Presets | não suportados | não suportados |
| Posição do PTZ (GetStatus) e GetPresets | derrubam a conexão: o app não sabe onde a câmera está | idem |
| Eventos (movimento) | anunciados, mas CreatePullPointSubscription derruba a conexão: função fica oculta | idem |
| Cartão SD (Profile G) | **não disponível** | **não disponível** |

### Quirks tratados no app
- **XAddrs deslocados** no GetCapabilities (Events → media_service, Media → ptz_service, PTZ → deviceio_service, DeviceIO → IP antigo). O app descarta XAddr que aponta para o caminho de outro serviço e usa o `device_service`, que atende todas as operações.
- **GetSystemDateAndTime sem autenticação derruba a conexão**: o app repete autenticado.
- **Sem keep-alive HTTP**: a câmera fecha a conexão após cada resposta; o app manda `Connection: close` e o OkHttp refaz conexões encerradas.
- **Stop recusado**: o app para com `ContinuousMove(0, 0)` e lembra disso por câmera. O ContinuousMove já leva `Timeout` (a câmera também para sozinha).
- **Resolução do sub informada pela metade**: a detecção da lente empilhada usa o perfil principal e o tamanho real reportado pelo player.
- **RTSP só por UDP**: o modo Auto começa em UDP (o app não força TCP).

## Cartão SD
As duas câmeras não oferecem ONVIF Profile G. As gravações do cartão continuam acessíveis só pelo app Yoosee no celular (protocolo P2P proprietário, que este projeto não implementa nem faz engenharia reversa). Para ver gravações na TV, use a **Gravação na TV Box** (recomendado com pendrive).

Quando uma câmera oferecer Profile G, o app abre o URI de replay e posiciona com `:start-time`; o LibVLC não envia os cabeçalhos `Range: clock=` e `Require: onvif-replay`, então algumas câmeras podem não aceitar.

## Gravação na TV Box
- Entre um segmento de 10 min e o seguinte há um intervalo de 1 a 2 s (o MediaPlayer é reiniciado para trocar de arquivo).
- A gravação para se a TV Box desligar ou entrar em standby profundo; volta quando o aparelho liga (o serviço é religado com o app).
- Gravar enquanto assiste abre uma segunda sessão RTSP na câmera; câmeras baratas limitam sessões simultâneas.

## Rede e sistema
- Funciona só na rede local (o app bloqueia qualquer destino fora das faixas privadas). Não há acesso de fora de casa.
- As câmeras usam Wi-Fi 2,4 GHz; TV Box e câmeras precisam estar na mesma rede/sub-rede.
- "Iniciar com a TV Box": em versões recentes do Android TV o sistema pode bloquear a abertura de apps em segundo plano no boot. Nesse caso o app não abre sozinho (a gravação local, se ligada, volta quando o app for aberto).
- Sem `POST_NOTIFICATIONS`, a notificação da gravação pode ficar oculta no Android 13+; a gravação funciona normalmente.
- Movimento via ONVIF Events depende da câmera; nas câmeras atuais não está disponível.
