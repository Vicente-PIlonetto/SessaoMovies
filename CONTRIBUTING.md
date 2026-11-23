# Como contribuir

Toda ajuda conta: relatar um erro, sugerir uma ideia ou mandar código.

## Relatar um erro

1. Veja se já existe uma [issue](https://github.com/Vicente-PIlonetto/SessaoMovies/issues) sobre o mesmo problema. Se existir, comente nela com o que acontece com você.
2. Se não existir, abra uma nova com o modelo **Relatar um erro**.
3. Conte o que você fez, o que esperava e o que aconteceu. Prints ou um vídeo curto ajudam muito.
4. Informe a versão do app (*Configurações > Sobre*), o modelo do celular e a versão do Android.

Nunca coloque chaves de API, senhas ou dados pessoais numa issue.

## Sugerir uma ideia

Abra uma issue com o modelo **Sugerir uma ideia** e explique o problema que ela resolve no seu dia a dia.

## Código

O passo a passo para compilar está no [guia de desenvolvimento](docs/DESENVOLVIMENTO.md).

1. Faça um fork e crie um branch a partir da `main` (ex.: `corrige-agenda`).
2. Mantenha cada pull request focado em uma coisa só.
3. Rode os testes antes de enviar: `./gradlew testDebugUnitTest`.
4. Abra o pull request contra a `main` explicando o que mudou e por quê.

### Estilo

- Kotlin com Jetpack Compose. Siga o estilo dos arquivos vizinhos.
- Comentários e textos da interface em português do Brasil.
- Textos curtos e diretos, sem exclamações, sem emojis e sem tom de propaganda. Mensagens de erro dizem o que aconteceu e o que fazer.
- Todo texto novo da interface passa por `t("...")`, para dar para traduzir depois.
- Regras de progresso, status, notas e Letterboxd ficam em funções puras, com teste.
- Nada de chave do TMDB no código ou nos commits: ela fica em `local.properties`.

## Conduta

Seja gentil e respeitoso. Críticas são sobre o código e as ideias, nunca sobre as pessoas.
