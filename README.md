# IRONLAB PRO Android 1.6.0

A versão 1.6.0 inclui o treino publicado de terça a sexta no APK, com prioridade
em superiores, 3–4 séries, sugestões de carga baseadas nos registros, diário,
backup e vínculo entre aparelhos por código. O app tenta abrir a versão online;
se a rede ou o servidor falhar, abre a cópia incluída na compilação. As sessões
continuam salvas na mesma origem HTTPS do site no WebView.

A biblioteca musical permanece nativa com Media3. Vídeos, rádio e sincronização
precisam de internet. Para recuperar os dados de outra instalação, use
Evolução → Backup e restauração, por arquivo ou texto.

O APK independente 1.6.0 usa `com.ironlab.pro.safe.v16` e pode ser instalado ao lado
das versões beta anteriores. O identificador foi separado porque as chaves debug
das execuções anteriores não estão disponíveis para assinar uma atualização direta.
A assinatura release permanente ainda depende das quatro secrets descritas em
`BUILD_APK.md`. Não remova a instalação anterior antes de transferir seus dados.

O build confere a versão do site, inclui HTML, JavaScript, CSS e a dependência Axios,
confere os checksums, compila e valida ZIP, assinatura e pacote antes de publicar.
Os arquivos de áudio pessoais continuam na instalação onde foram importados.
O teste no aparelho físico permanece pendente.

[Downloads da versão 1.6.0](https://github.com/canalescanorff6-source/ironlab-pro-android/releases/tag/v1.6.0-beta)

---

# IRONLAB PRO — Android nativo (beta 1.4.1: instalador verificado)

## Correção de instalação 1.4.1

- Novos builds passam por verificação automática de integridade ZIP, assinatura Android `apksigner`, pacote `aapt` e checksum SHA-256 **antes** de aparecerem como download.
- São publicados **dois APKs diferentes**, ambos com o mesmo acesso à Nutrição de Precisão e ao plano de 7 dias do site:
  - `IRONLAB-PRO-Android-1.4.1-debug.apk`: mesmo package name `com.ironlab.pro`. Em caso de assinatura diferente da versão instalada, pode falhar a atualização.
  - `IRONLAB-PRO-Android-1.4.1-instalacao-paralela.apk`: package name `com.ironlab.pro.safe`, instala **separado** do aplicativo antigo para preservar os registros existentes. Isso não migra dados automaticamente.
- **A versão paralela é uma solução de teste:** os próximos builds também precisarão de assinatura estável para atualizar em cima dela. Configure as secrets privadas da chave release seguindo `BUILD_APK.md` para a versão definitiva.
- **Não desinstale a versão antiga sem salvar os dados.** O novo app possui localStorage e biblioteca nativa separados. A versão web pode ser usada diretamente sem APK.

[Baixar versão 1.4.1-beta e validar SHA-256](https://github.com/canalescanorff6-source/ironlab-pro-android/releases/tag/v1.4.1-beta)

## Histórico — 1.4.0

Aplicativo Android em **Kotlin + AndroidX Media3**, com reprodução de arquivos pessoais completos em segundo plano e acesso à versão web atual do IRONLAB PRO.

## Novidades da versão 1.4.0 — treinos e nutrição sincronizados por conteúdo

- O app Android nativo mantém o player Media3 para músicas locais; **Treinos e Nutrição usam a versão online oficial do IRONLAB**, com as mesmas novidades no site e no APK quando há internet.
- A aba **NUTRIÇÃO** abre `#nutricao`, incluindo avaliação com peso e altura reais, metas nutricionais, creatina, whey por scoop e calculadora do hipercalórico caseiro com alerta de lactose.
- Um botão **↻** visível nas abas Treinos e Nutrição atualiza a página pela rede, evitando conteúdo antigo em cache sem excluir os dados do usuário.
- Publicar novas funções no site **não atualiza o código Kotlin automaticamente**: as partes nativas exigem novo APK.
- **Importante:** dados do Chrome e WebView ainda são separados, sem sincronização de histórico entre eles; faça backup antes de reinstalar e não desinstale por problemas de assinatura do APK debug.

## Histórico de mudanças (1.2.0–1.3.0)

- **Treinos:** a aba nativa abre diretamente `#treinos`; ao escolher Superior A/B ou Inferior A/B, a página rola para o treino correspondente, preservando a posição ao abrir exercícios.
- **Spotify:** botão para abrir **Minhas Músicas Curtidas** no aplicativo/site oficial, além de cadastrar, abrir, copiar e remover links de playlists e faixas.
- **Compartilhar:** no Spotify, use **Compartilhar → IRONLAB PRO** (se o app aparecer) para guardar links com confirmação; nenhum dado de login é armazenado.
- **Versão gratuita:** a conexão de dados via Spotify Web API foi deliberadamente evitada. As novas regras para apps em desenvolvimento requerem proprietário Premium. Não há download de músicas comerciais nem bloqueio de anúncios de terceiros.
- **APK beta:** GitHub Actions compila e publica um link de download em `Releases`.

**Download beta:** https://github.com/canalescanorff6-source/ironlab-pro-android/releases/tag/v1.4.1-beta

**Atenção às atualizações:** este é um APK `debug`. A assinatura de debug gerada pelo GitHub Actions pode variar entre execuções e impedir instalar por cima de versões anteriores. Não desinstale uma versão que contém dados importantes sem fazer backup. A solução definitiva é configurar os segredos de assinatura privada de release documentados em `BUILD_APK.md`.

## O que está implementado

- Reprodução local de MP3, M4A, AAC, OGG, FLAC e demais formatos aceitos pelo decoder Android, **sem anúncios adicionados pelo IRONLAB**.
- Importação pelo seletor nativo `ACTION_OPEN_DOCUMENT`, com permissão de leitura persistente para cada URI; não é necessário pedir acesso a toda a biblioteca do aparelho.
- Media3 `ExoPlayer` no `MediaSessionService` de primeiro plano (`mediaPlayback`) em vez de depender de `WebView` ou navegador para tocar.
- `WAKE_MODE_LOCAL` com permissão `WAKE_LOCK` para sustentar áudio em segundo plano durante a tela bloqueada; a trava só é mantida durante reprodução ativa e não mantém a tela acesa.
- Botão **BUSCAR MÚSICAS EM SERVIÇOS OFICIAIS** que abre a busca da faixa/artista em YouTube Music, Spotify, Deezer ou Apple Music (sem desbloquear conteúdos pagos).
- Controle na notificação, tela bloqueada e fones Bluetooth quando o Android/áudio do dispositivo permitir.
- Fila de reprodução, próxima/anterior, pausa, modo aleatório, repetir faixa/fila.
- Playlists criadas pelo usuário, seleção de playlist, favoritos e busca local por título ou artista.
- Metadados de áudio extraídos localmente usando `MediaMetadataRetriever`.
- Reprodução offline após importar; o áudio é lido diretamente da URI original. **Não apagar/mover arquivos depois de importá-los**.
- Visual dark premium e aba **Meus treinos** abrindo `https://ironlab-pro-3r9500.v2.appdeploy.ai/` em `WebView`.
- Botão **ABRIR CHROME ↗** na aba Treinos para acessar a sessão original do site no navegador.
- Pipeline `.github/workflows/build-debug-apk.yml` que roda validação estática, compila APK debug e, se as quatro variáveis secretas estiverem configuradas, também produz **APK release assinado com chave estável** para atualização sem perda de dados.
- `versionCode = 6` e `versionName = 1.4.1`, com APK principal e APK paralelo verificados.

## O que não é possível prometer

Este aplicativo **não busca ou toca gratuitamente todo o catálogo comercial da internet**. Não bloqueia publicidade de YouTube, Spotify ou outros serviços. Para músicas comerciais completas sem anúncios e com a tela bloqueada, é necessário acesso autorizado, normalmente por assinatura, ao serviço de música.

A reprodução em segundo plano usa um serviço Android apropriado; o SO pode interromper serviços em situações de bateria crítica, políticas do fabricante, remoção de permissão ao arquivo ou encerramento forçado do aplicativo. O app não solicita desligamento das restrições de bateria nem tenta contornar políticas do sistema.

## Compilar com Android Studio

1. Instale Android Studio recente, JDK 17 e a plataforma Android SDK 36.
2. Descompacte esta pasta e abra **IRONLAB-PRO-Android** como projeto Gradle.
3. Aguarde o Gradle sincronizar as dependências. Se o IDE pedir a distribuição Gradle, use **Gradle 8.13**, configurada em `gradle/wrapper/gradle-wrapper.properties`. O pacote não inclui o executável `gradlew` ou `gradle-wrapper.jar`; configure o Gradle pelo Android Studio/instalação local ou compile pelo workflow GitHub Actions, que instala a versão correta sem wrapper.
4. Execute o módulo `app` em um Android físico API 26+ ou escolha `Build > Build Bundle(s) / APK(s) > Build APK(s)`.
5. O debug APK aparecerá em `app/build/outputs/apk/debug/app-debug.apk`.

### Gerar APK pelo GitHub Actions (sem Android Studio)

1. Crie um repositório vazio no GitHub.
2. Envie o conteúdo desta pasta para a raiz do repositório (incluindo `.github/workflows/build-debug-apk.yml`).
3. Abra a aba **Actions** e execute **IRONLAB Android - Compilar APK** se a execução automática não tiver iniciado.
4. Ao terminar, abra a execução, procure **Artifacts** e baixe **IRONLAB-PRO-Android-debug-APK**. Dentro estará `app-debug.apk`.
5. Transfira para o celular e instale aceitando a instalação de fonte externa **somente para o arquivo que você mesmo compilou**.

O APK de debug não é adequado para publicação na Play Store. Para distribuição, faça build `release` com chave de assinatura particular protegida.

## Importante sobre os dados dos treinos

**O WebView possui armazenamento próprio, separado do Chrome.** Os registros salvos no site IRONLAB PRO no Chrome **não aparecem automaticamente** dentro do WebView nativo. Para manter a sessão de treinos existente sem migração, toque no indicador **ABRIR CHROME ↗** dentro da aba Treinos. Uma migração de backups pode ser implementada posteriormente, após verificar a ferramenta de exportação existente. O projeto não sobrescreve o site publicado.

A lista de músicas e playlists nativas ficam em SharedPreferences do próprio app. Os arquivos originais permanecem onde estavam; apenas a URI e os metadados são guardados. A desinstalação limpa esta lista, mas não exclui os arquivos originais.

## Segurança e privacidade

- Não faz upload dos arquivos para servidores.
- Não pede senha de plataformas musicais.
- Não solicita permissão de armazenamento irrestrita.
- Só usa `INTERNET` para abrir os treinos do site.
- `MediaSessionService` inclui as permissões Android necessárias a um serviço de reprodução em primeiro plano.
- Links externos no WebView são abertos pelo navegador, sem permitir esquemas `javascript:` ou `intent:`.

## Checklist de testes em dispositivo real

- [ ] Instalação em Android 10, 13, 15 e 16.
- [ ] Importar MP3 e M4A do armazenamento local ou do provedor de arquivos.
- [ ] Reproduzir música completa, ouvir 10+ minutos, bloquear tela e observar continuidade.
- [ ] Pausar/continuar e avançar faixa pela notificação, tela bloqueada e fone Bluetooth.
- [ ] Minimizar e alternar para a aba Treinos sem parar a música.
- [ ] Criar playlist; adicionar músicas; avançar automático para a próxima.
- [ ] Trocar orientação e reabrir app; os favoritos e as playlists devem permanecer.
- [ ] Remover uma música apenas da biblioteca sem excluir o arquivo no celular.
- [ ] Validar o acesso ao IRONLAB na aba Treinos e o botão ABRIR CHROME.
- [ ] Validar perda de acesso a URI (arquivo removido) e restrições de bateria.

## Referências oficiais

- [Android Media3 — reprodução em segundo plano](https://developer.android.com/media/media3/session/background-playback)
- [Android Media3 — ExoPlayer](https://developer.android.com/media/implement/playback-app)
- [Android ACTION_OPEN_DOCUMENT](https://developer.android.com/reference/android/content/Intent#ACTION_OPEN_DOCUMENT)

**Status:** Versão **1.4.0** com pipeline GitHub Actions para validar e compilar. Uma execução bem-sucedida comprova a compilação, mas não substitui testes em dispositivo Android real.
