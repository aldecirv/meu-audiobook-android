# Meu Audiobook (Android)

Player de audiolivros nativo para Android, com **reprodução em segundo plano** e **notificação com controles**.

## Recursos

- **Biblioteca** com dois audiolivros de domínio público (LibriVox) e seus próprios arquivos;
- **Player** com barra de progresso, avançar/voltar configurável (10–60 s; padrão 15 s) e troca de capítulo;
- **Reprodução em segundo plano** via `MediaSessionService` (Media3/ExoPlayer);
- **Notificação** com play/pause, capítulos, barra de progresso e botões ⏪/⏩;
- **Velocidade** de 0,75× a 2×;
- **Timer de sono** (15, 30, 45, 60 ou 90 minutos);
- **Retomada automática** no capítulo e segundo exatos em que você parou;
- **Porcentagem ouvida** do audiolivro na biblioteca e na tela de reprodução;
- **Destaque vermelho** com barra de progresso no livro que está tocando;
- **Marcação automática de “Ouvido”** ao terminar, com sugestão do próximo livro da fila;
- **Cadeado de reprodução**: segure 3 segundos para desbloquear e evitar toques acidentais;
- **Capas** dos audiolivros quando existirem nos arquivos/metadata (ou nas obras de exemplo);
- **Adicionar audiolivros locais** pelo seletor de arquivos do Android (SAF), incluindo **OPUS**, MP3, M4A, OGG, FLAC e WAV;
- Tema escuro com capas geradas a partir do título.

## Requisitos

- Android **7.0** (API 24) ou superior;
- Internet para os audiolivros de exemplo (transmitidos do Archive.org);
- Arquivos locais funcionam offline.

## Instalar

Baixe o APK mais recente em **Releases** e instale (permita "instalar apps desconhecidos" para o navegador/gerenciador de arquivos).

## Adicionar seus próprios audiolivros

1. Toque em **Adicionar**;
2. Selecione **um ou mais arquivos** de áudio (MP3, M4A, OGG…);
3. Os arquivos viram as faixas de um novo livro, na ordem em que foram escolhidos;
4. Para remover, mantenha o livro pressionado na lista.

> O acesso é feito pelo Storage Access Framework: o app guarda apenas a permissão dos arquivos escolhidos, sem varrer o armazenamento.

## Build

O APK é compilado no GitHub Actions (`.github/workflows/build.yml`):

1. `git push` na branch `main`;
2. **Actions → Build APK**;
3. Baixe o artefato `meu-audiobook-debug`.

Localmente, com Android SDK e JDK 17:

```bash
gradle :app:assembleDebug
```

## Estrutura

```
app/src/main/java/com/meuaudiobook/app/
├── MainActivity.kt          biblioteca + importação de arquivos
├── PlayerActivity.kt        player, velocidade, timer e capítulos
├── playback/PlaybackService.kt  MediaSessionService (segundo plano)
├── data/                    livros, progresso e catálogo LibriVox
└── ui/BookAdapter.kt        lista de livros
```

## Créditos

- Áudio de exemplo: [LibriVox](https://librivox.org/) — gravações em domínio público de *Dom Casmurro* e *O Alienista*, de Machado de Assis;
- Player: [Media3 / ExoPlayer](https://developer.android.com/media/media3).

## Limitações

- Os exemplos são **transmitidos** (streaming); não há download para ouvir offline (os arquivos locais que você adicionar funcionam offline);
- A posição é salva ao pausar, trocar de capítulo ou sair do app — e também a cada 5 segundos durante a reprodução;
- O progresso, a porcentagem, o cadeado e o intervalo dos saltos ficam salvos localmente (SharedPreferences) e funcionam sem internet.
