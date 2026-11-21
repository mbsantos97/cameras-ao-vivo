# Configurar as câmeras Yoosee

## 1. Colocar a câmera no Wi-Fi (app Yoosee, no celular)
Siga o pareamento normal do app Yoosee. As câmeras usam **Wi-Fi 2,4 GHz**.

## 2. Ativar RTSP/ONVIF ("Conexão NVR") e a senha
No app Yoosee, abra a câmera → **Configurações** → **Conexão NVR** (em algumas versões: "Configurações de rede" → "Conexão NVR"/"RTSP"/"ONVIF"):
1. Ative a conexão.
2. Defina a **senha** (o usuário é `admin`). É essa senha que vai no Câmeras ao Vivo.

Portas usadas: RTSP **554** e ONVIF **5000**.

## 3. Descobrir o IP da câmera
- No app Yoosee: informações da câmera / rede; ou
- No roteador: lista de aparelhos conectados (DHCP).

O **ID do Yoosee** (número de 10 dígitos) **não** serve: o app usa o IP da rede local (ex.: `192.168.1.50`).

## 4. Reservar IP fixo (recomendado)
No roteador, em **DHCP → Reserva de endereço**, associe o MAC de cada câmera a um IP fixo. Assim o IP não muda depois de uma queda de energia. (O app também tenta reencontrar a câmera pelo serial/MAC se o IP mudar.)

## 5. TV Box na mesma rede
- TV Box e câmeras precisam estar no **mesmo roteador e na mesma sub-rede** (ex.: todos em 192.168.1.x).
- **Recomendado:** deixe a **TV Box no Wi-Fi 5 GHz** do mesmo roteador (mais folga de banda para vários vídeos), enquanto as câmeras ficam no 2,4 GHz. Se o roteador separa as redes 2,4/5 GHz com nomes diferentes, confira se as duas estão na mesma sub-rede (normalmente estão).
- Evite "rede de convidados" ou "isolamento de clientes" para a TV Box: eles bloqueiam o acesso às câmeras.

## 6. Ponto de guarda e rastreamento (no app Yoosee)
Recursos do firmware, que funcionam 24 h mesmo com a TV desligada:
1. Na tela ao vivo do Yoosee, aponte a câmera para a entrada e salve a posição como preset/ponto.
2. Em Configurações → Configurações inteligentes: ative **Rastreamento humanoide** e defina o **Ponto de guarda / Posição de vigilância** com retorno automático.

## 7. Atualizar o firmware
App Yoosee → câmera → Configurações → **Atualização de firmware / Versão do dispositivo** → verificar. Não desligue a câmera durante a atualização.

## Segurança
- **Não abra portas** no roteador para as câmeras (nem 554, nem 5000) e **desative o UPnP** do roteador.
- Use senha forte na Conexão NVR e no Wi-Fi.
- O Câmeras ao Vivo só conversa com endereços da rede local e não envia nada para a internet.
