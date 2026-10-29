# SrMatta Treino

Aplicativo Android leve para registrar treinos de academia. Funciona 100% offline, sem login, sem anúncios.

- **Linguagem:** Kotlin, apenas o framework do Android (nenhuma biblioteca extra)
- **Compatibilidade:** Android 6.0+ (minSdk 23) — roda no Galaxy J7 Prime 2
- **Dados:** SQLite local no próprio aparelho
- **Permissão:** só `VIBRATE` (aviso de fim do descanso)

## O que o app faz

| Aba | Função |
| --- | --- |
| **Início** | Foto de perfil, logo SrMatta, idade e peso. Sub-abas: **Resumo** (carga total somada, tempo total, treinos, séries e tipo de treino), **Medidas** (peso, IMC e medidas do corpo), **Exercícios** (recorde de cada um), **Calendário** e **Redes** (TikTok, Twitch, Instagram e a banda Valandil). |
| **Treino** | Inicia treino livre ou por rotina (lista com ✓ nos exercícios feitos), registra séries, cronômetro de treino e de descanso com vibração. |
| **Rotinas** | Cria/edita/exclui rotinas e adiciona planos prontos (iniciante A/B, Upper/Lower + PPL, ABC, Full Body, PPL, ombros, bíceps, pernas). |
| **Histórico** | Lista dos treinos com tipo, duração, séries e volume. |
| **Evolução** | Séries por músculo nos últimos 7 dias, gráfico do peso corporal e da carga por exercício. |

A biblioteca tem mais de 60 exercícios, cada um ligado a um grupo muscular. Estrutura dos planos inspirada nos treinos de hipertrofia.org.

## Como gerar o APK

### Opção A — GitHub (não precisa instalar nada)

1. Crie um repositório no GitHub e envie todo o conteúdo desta pasta (inclusive `.github/`).
2. Abra a aba **Actions → Gerar APK → Run workflow** (ou faça um push na branch `main`).
3. Quando terminar (3–6 min), baixe `SrMattaTreino.apk` na aba **Releases** do repositório, direto pelo celular.

### Opção B — Android Studio

1. Abra esta pasta no Android Studio (Koala ou mais recente) e aguarde o Gradle sincronizar.
2. Menu **Build → Build APK(s)** para o debug, ou rode no terminal:
   ```
   ./gradlew assembleRelease
   ```
3. O arquivo sai em `app/build/outputs/apk/release/app-release.apk`.

O build `release` já vem com R8 (encolhe código e recursos) e é assinado com a chave fixa `app/srmatta.jks`, então instala direto e as próximas versões atualizam por cima, sem perder dados.

## Instalando no celular

1. Copie o `.apk` para o aparelho (ou baixe por ele).
2. Toque no arquivo; se pedir, permita **Instalar apps desconhecidos** para o navegador/gerenciador de arquivos usado.
3. Abra **SrMatta Treino**.

## Observações

- O aviso de fim do descanso (vibração) funciona com o app aberto. Por isso a tela fica acesa durante o treino.
- Os dados ficam só no aparelho. Desinstalar o app apaga o histórico.
- Se um dia quiser publicar na Play Store, troque a chave `app/srmatta.jks` (senha pública no código) por uma chave privada sua em `app/build.gradle.kts`.
