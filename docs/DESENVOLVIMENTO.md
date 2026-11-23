# Desenvolvimento

## O que precisa

- JDK 17 ou mais novo (o projeto compila com Java 17 como alvo; o JDK 21 funciona).
- Android SDK com a plataforma 36 (Android Studio instala; ou `sdkmanager "platforms;android-36" "build-tools;36.0.0"`).
- Uma chave da API do TMDB (gratuita).

## Chave do TMDB

1. Crie uma conta em [themoviedb.org](https://www.themoviedb.org) e peça uma chave em *Configurações > API* (uso pessoal).
2. Na raiz do projeto, crie (ou edite) o arquivo `local.properties`:

   ```properties
   sdk.dir=/caminho/para/o/android-sdk
   TMDB_API_KEY=sua_chave_v3_aqui
   # Opcional: login e sincronização com o Trakt
   TRAKT_CLIENT_ID=client_id_do_seu_app_no_trakt
   ```

O `local.properties` está no `.gitignore`: a chave nunca vai para o Git. Ela entra no APK pelo `BuildConfig.TMDB_API_KEY`.
Sem a chave o app compila, mas a busca, o Descobrir e a atualização mostram o aviso de chave em falta. Também dá para colar uma chave no próprio app, em *Configurações > Chave do TMDB*.

O `TRAKT_CLIENT_ID` vem de um app criado em [app.trakt.tv/settings/apps](https://app.trakt.tv/settings/apps/api/new) (tipo *Development*, redirect `urn:ietf:wg:oauth:2.0:oob`). O login usa o fluxo de código no aparelho e não precisa do *client secret*. Sem o Client ID, a seção Trakt avisa que o APK foi compilado sem ele.

O app aceita a chave v3 (vai como `api_key`) ou o token de leitura v4 (vai no cabeçalho `Authorization`).

## Compilar e testar

```sh
./gradlew assembleDebug          # APK de teste em app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # testes unitários
```

Instalar no celular com o cabo:

```sh
adb install -r app/build/outputs/apk/debug/SessãoMovies-*-debug.apk
```

## APK de release

```sh
./gradlew assembleRelease
```

O arquivo sai em `app/build/outputs/apk/release/SessãoMovies-<versão>-release.apk`, minificado com o R8.
Como o app é distribuído por APK no GitHub (e não na Play Store), o release é assinado com a chave de debug,
para quem tem o app instalar a versão nova por cima.

### Pelo GitHub Actions

O workflow `.github/workflows/release.yml` compila o release e cria a versão no GitHub quando uma tag `v*` é enviada:

```sh
git tag v1.0.0
git push origin v1.0.0
```

As chaves vêm dos secrets `TMDB_API_KEY` e `TRAKT_CLIENT_ID` do repositório (*Settings > Secrets and variables > Actions*).
Antes de criar a tag, suba o `versionCode` e o `versionName` em `app/build.gradle.kts`.

## Organização do código

```
app/src/main/java/dev/viko/sessao/
  domain/            modelos, regras de progresso e status, notas, "onde assistir" (puros, com teste)
  data/api/          TMDB (Retrofit + kotlinx.serialization), limite de ritmo e erro 429
  data/db/           Room: títulos, itens da lista, episódios marcados, cache de episódios
  data/repo/         lista (regras aplicadas) e títulos (detalhe, temporadas, Descobrir)
  data/letterboxd/   feed RSS, .zip exportado e CSV de importação
  data/trakt/        login por código, token cifrado e sincronização com o Trakt
  data/sync/         atualização diária (WorkManager) e notificações
  data/backup/       backup em JSON
  ui/                tema, componentes, navegação e telas (Jetpack Compose)
```

Os testes ficam em `app/src/test/`. O feed de exemplo do Letterboxd está em `app/src/test/resources/`.
