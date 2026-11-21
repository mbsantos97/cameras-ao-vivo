# Changelog

Mudanças relevantes do Câmeras ao Vivo. Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/).

## [1.0.0] - 2026-11-21

Primeira versão aberta.

### Ao vivo
- Grade automática (1, 2, 2x2, 3x2, 3x3 e páginas), "1 grande + 2", "1 grande + 3" e Painel com relógio e status.
- Câmera de lente dupla (imagem empilhada) em dois quadrados, "Fixa" e "Móvel", com um único decodificador.
- Tela cheia instantânea: o substream é ampliado e troca para o principal sem tela preta.
- PTZ pelas setas do controle (segurar = mover, soltar = parar, diagonais, inversão, velocidade, modo passo), zoom digital, foto, áudio, informações do stream e ronda automática.
- Reconexão automática (2–30 s), watchdog, decodificação por software como reserva e alternância UDP/TCP.

### Gravações
- Cartão SD via ONVIF Profile G (quando a câmera oferece).
- Gravação na TV Box em segundo plano (MPEG-TS de 10 min, pendrive se houver, retenção automática).
- Fotos, linha do tempo de 24 h e player com ±10 s/60 s e velocidade de 0,5x a 4x.

### Configuração
- Configurar pelo celular: QR code e PIN na TV, página só na rede de casa.
- Assistente de primeira execução, busca das câmeras na rede e teste passo a passo.
- PIN das Configurações e exportar/importar a configuração cifrada.
- Diagnóstico completo e painel de desempenho.

### Compatibilidade com câmeras Yoosee
- XAddrs deslocados, relógio sem autenticação, conexões sem keep-alive e Stop do PTZ recusado (usa velocidade zero).
- Recusa o ID do app Yoosee no lugar do IP.

### Instalação
- Scripts de instalação pela rede (`scripts/install-tv.sh`, `scripts/install-tv.ps1`), sem cabos.
