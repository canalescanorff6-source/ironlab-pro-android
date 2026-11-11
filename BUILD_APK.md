# Gerar o APK do IRONLAB PRO Android 1.1.0

## Etapa 1 — GitHub Actions (sem instalar Android Studio)

1. No GitHub, utilize o repositório vazio chamado `ironlab-pro-android` na sua conta. Mantenha-o separado dos demais projetos.
2. Descompacte `IRONLAB-PRO-Android-v1.1.0-Codigo-Fonte.zip` e coloque **todo o conteúdo da pasta** na raiz desse repositório, incluindo `.github/workflows/build-debug-apk.yml` (a pasta `.github` é oculta).
3. Na aba **Actions** do GitHub, abra **IRONLAB Android - Compilar APK**; execute manualmente em `Run workflow` caso não inicie no envio.
4. Verifique que a execução chegou ao estado verde. Em **Artifacts**, baixe `IRONLAB-PRO-1.1.0-debug-APK` e extraia o arquivo `app-debug.apk`.
5. No celular Android, abra o APK e autorize a instalação somente para o gerenciador de arquivos utilizado.
6. Importe as músicas que você possui legalmente, pressione Play e teste os controles com o celular bloqueado e com um fone Bluetooth. A música está em `MediaSessionService`, separado da aba de treinos.

**Importante:** a chave de assinatura *debug* do GitHub Actions costuma mudar entre execuções. Portanto, APKs debug de execuções distintas podem **não atualizar a instalação anterior** sem desinstalar (o que apaga os dados locais). Para manter os dados em atualizações, use a **assinatura release privada fixa**, explicada abaixo.

## Etapa 2 — Assinatura release que preserva instalações

**Antes do primeiro APK definitivo**, crie uma chave privada no seu próprio computador, mantendo a senha fora de chats e repositórios:

```sh
keytool -genkeypair -v -keystore ironlab-release.jks -alias ironlab -keyalg RSA -keysize 3072 -validity 10000
```

Guarde `ironlab-release.jks` e as senhas em local seguro, com backup. **Nunca faça commit desse arquivo.**

No seu repositório GitHub privado, abra `Settings → Secrets and variables → Actions` e adicione os 4 secrets:

- `IRONLAB_KEYSTORE_BASE64`: conteúdo Base64 completo do `.jks` (obtenha com `base64 -w0 ironlab-release.jks` no Linux; em outros sistemas use conversor local, sem enviar o arquivo a um site externo).
- `IRONLAB_KEYSTORE_PASSWORD`: senha do keystore.
- `IRONLAB_KEY_ALIAS`: alias usado na geração (`ironlab`).
- `IRONLAB_KEY_PASSWORD`: senha da chave (pode ser diferente da senha do keystore).

Execute o mesmo workflow. Quando todos os secrets estiverem definidos, ele produz também o artifact **IRONLAB-PRO-1.1.0-release-signed-APK** contendo `app-release.apk`.

Para atualizações futuras sem perder dados: **use sempre a mesma chave**, mantenha `applicationId` e aumente `versionCode`. Um APK release não pode atualizar uma instalação debug assinada com outra chave; escolha a linha release desde a primeira instalação que pretende manter.

## Alternativa — Android Studio

Abra a pasta raiz no Android Studio com JDK 17, Gradle 8.13 e Android SDK 36. O projeto contém `gradle/wrapper/gradle-wrapper.properties`, mas não inclui os binários do wrapper; configure Gradle 8.13 na IDE ou use o workflow GitHub Actions acima. Execute a tarefa `:app:assembleDebug`. O arquivo será `app/build/outputs/apk/debug/app-debug.apk`.

## Limites e segurança

- Esta versão toca somente os **arquivos que você importar** e pesquisar nos serviços musicais oficiais (a busca externa não significa reprodução integral gratuita).
- O player nativo foi preparado para segundo plano e tela bloqueada, mas precisa ser testado no seu Android real. Restrições de bateria ou remoção de arquivos podem interromper a reprodução.
- Os registros do IRONLAB salvos no Chrome permanecem separados do armazenamento do WebView; use `ABRIR CHROME ↗` para acessar os dados originais.
- Esta pasta é **código-fonte**. O APK é gerado somente depois que o workflow/Android Studio conclui uma compilação bem-sucedida.
