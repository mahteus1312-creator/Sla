# Hibernador

App Android nativo (Kotlin + Jetpack Compose) que **hiberna apps**: para os que estão em segundo plano (`am force-stop`), marca como inativos (`am set-inactive`) e limpa o cache deles (`pm clear --cache-only`), mostrando a RAM liberada.

## Funcionalidades

- **Tela principal**: RAM total, em uso, livre e liberada na última hibernação (barra + números), atualizada a cada segundo (`ActivityManager.MemoryInfo`).
- **Botão "Hibernar"**: abre a tela de hibernação (ou o guia do Shizuku, se ele não estiver ativo).
- **Tela de hibernação**: anel de progresso animado com %, app atual (ícone + nome + etapa), lista dos concluídos (RAM/cache liberados), botão **Cancelar** que interrompe na hora, e o total de RAM liberada no final (`antes − depois` da memória disponível).
- **Regras de proteção** (ícone de engrenagem): editor do arquivo de configuração JSON.

## Quais apps nunca são tocados

| Categoria | Como é detectado |
|---|---|
| Jogos | `ApplicationInfo.category == CATEGORY_GAME` (e `FLAG_IS_GAME`) |
| Bancos / carteiras digitais | lista de pacotes + palavras-chave no JSON |
| Segurança (antivírus, autenticadores, senhas, localizar dispositivo) | lista de pacotes + palavras-chave no JSON |
| O próprio Hibernador e o Shizuku | nome do pacote |
| Launcher | apps que respondem a `CATEGORY_HOME` |
| Teclado | todos os métodos de entrada instalados |
| Telefone / SMS | discador e SMS padrão, apps de `ACTION_DIAL`/`smsto:` e lista fixa |
| Sistema essencial | SystemUI, Play Services/Play Store, provedores, telefonia, Wi‑Fi, Bluetooth, apps com UID de sistema (< 10000) ou `persistent` |
| Serviços ativos | acessibilidade, ouvintes de notificação, administradores do dispositivo, papel de parede animado |

### Arquivo de configuração

- Padrão: [`app/src/main/assets/protection_rules.json`](app/src/main/assets/protection_rules.json) — edite antes de compilar.
- No celular: **engrenagem → Regras de proteção** edita uma cópia salva em `files/protection_rules.json` (com validação e "Restaurar padrão").

Campos: `protectGames`, `bankPackages`, `bankKeywords`, `securityPackages`, `securityKeywords`, `extraProtectedPackages`.
Pacotes casam exatamente (incluindo subpacotes `pacote.*`) ou por prefixo quando terminam em `*` (ex.: `com.itau*`). Palavras-chave são comparadas, sem acento e em minúsculas, com o nome do pacote e o nome do app.

## Limpeza de cache

`pm clear --cache-only` só existe de forma confiável no **Android 14+**. Em versões anteriores a flag é ignorada e o comando **apagaria todos os dados do app**, por isso o Hibernador **só limpa cache no Android 14 ou superior**; abaixo disso ele apenas para os apps.

## Ativar o Shizuku

Parar outros apps e limpar o cache deles exige privilégio de shell. O Hibernador usa o [Shizuku](https://shizuku.rikka.app/), que funciona via **Depuração sem fio** (sem root, Android 11+) ou **root**.

1. Instale o **Shizuku** (Play Store ou [GitHub](https://github.com/RikkaApps/Shizuku/releases)).
2. Ative as **Opções do desenvolvedor**: Configurações → Sobre o telefone → toque 7× em **Número da versão**.
3. Em Opções do desenvolvedor, ligue **Depuração sem fio**.
4. Abra o Shizuku → **Iniciar via Depuração sem fio** → **Parear**: abra "Depuração sem fio → Parear dispositivo com código" e digite o código na notificação do Shizuku.
5. No Shizuku toque em **Iniciar**. (Com root: só **Iniciar**.)
6. Abra o Hibernador → **Hibernar** → **Conceder permissão**.

> Após reiniciar o celular é preciso **Iniciar** o Shizuku de novo (com depuração sem fio).
> Android 8–10 sem root: no Shizuku escolha **Iniciar conectando a um computador** e rode no PC o comando `adb shell …` que o app mostra.

## Gerar o APK

### Pelo GitHub Actions (sem instalar nada)

Cada push em `Hibernador/**` roda o workflow **Hibernador APK** (`.github/workflows/hibernador-apk.yml`). Baixe o APK em **Actions → execução → Artifacts → `hibernador-debug-apk`**. Também dá para rodar manualmente em *Run workflow*.

### Localmente

Requisitos: JDK 17+ e Android SDK (API 35) — o jeito mais fácil é abrir a pasta `Hibernador/` no **Android Studio**.

```bash
cd Hibernador
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/debug/app-debug.apk
```

Release assinado: crie `Hibernador/keystore.properties` (não versionado):

```properties
storeFile=hibernador.jks
storePassword=...
keyAlias=hibernador
keyPassword=...
```

```bash
keytool -genkey -v -keystore hibernador.jks -keyalg RSA -keysize 2048 -validity 10000 -alias hibernador
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

## Estrutura

```
app/src/main/java/com/hibernador/app/
├── MainActivity.kt            navegação (Home, Hibernar, Shizuku, Regras)
├── HibernadorApp.kt
├── shizuku/
│   ├── ShellService.kt        UserService do Shizuku (processo com uid shell/root)
│   ├── ShellClient.kt         chamada Binder para executar comandos
│   └── ShizukuManager.kt      estado, permissão e conexão
├── data/
│   ├── Memory.kt              leitura de RAM (Flow em tempo real)
│   ├── AppClassifier.kt       regras de proteção
│   ├── ProtectionRules.kt     JSON editável
│   └── LastResultStore.kt
├── engine/HibernationEngine.kt  corotina + StateFlow de progresso
└── ui/                         telas Compose + anel/barra animados
```
