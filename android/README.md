# PutinaTunnel — Android (MVP, mock-режим)

Лёгкий нативный клиент (Kotlin + Jetpack Compose). На этом этапе туннель
работает в **mock-режиме**: приложение запускается на эмуляторе, все экраны
кликаются, статусы переключаются, VPN-разрешение и split-tunneling по
приложениям работают по-настоящему — но трафик через нативное ядро пока
не гоняется. Это сделано намеренно, чтобы проверить весь UX без собранных
`.aar` ядер.

## Запуск на эмуляторе

1. Открой папку в **Android Studio** (Hedgehog+).
2. Дай Gradle синхронизироваться (скачает зависимости).
3. Создай эмулятор (Pixel, API 30+), нажми Run.
4. На экране входа введи любой ключ длиной ≥6 символов (mock принимает любой).
5. Выбери тариф, нажми большую кнопку — Android покажет системный диалог
   разрешения VPN, дальше статус станет «Подключено» (mock).
6. Иконка «раздельный трафик» — список приложений, тумблеры сохраняются.

> Через терминал: `./gradlew assembleDebug` — соберёт APK в
> `app/build/outputs/apk/debug/`. (Понадобится Android SDK и `local.properties`
> с `sdk.dir=/путь/к/Android/Sdk` — Android Studio создаёт его сам.)

## Что уже реально работает (не mock)

- Экран входа по Telegram-ключу, сохранение сессии (DataStore).
- Выбор тарифа, сохранение выбора.
- Запрос системного разрешения VPN (`VpnService.prepare`).
- Поднятие `VpnService` с foreground-уведомлением.
- **Split-tunneling по приложениям** — `addDisallowedApplication` реально
  исключает выбранные приложения из туннеля.
- Каркас клиента к `vpn-api` (в `data/remote/ApiClient.kt`) с описанным
  контрактом эндпоинтов.

## Что подключается на следующем этапе (нативные ядра)

Туннель оживает, когда подложишь собранные библиотеки ядер и снимешь
mock-заглушку в `vpn/PtunnelVpnService.kt` (там помечено большим комментарием).

### Сборка ядер (делается в твоей среде с Go 1.22+)

**AmneziaWG** (обфусцированный WireGuard):
```
# tunnel-библиотека Amnezia для Android уже собрана в их репозитории:
#   https://github.com/amnezia-vpn/amneziawg-android
# берёшь модуль :tunnel (форк wireguard-android) как AAR/сабмодуль,
# кладёшь в app/libs/ или подключаешь как :tunnel модуль.
```

**xray-core** (VLESS+Reality), через gomobile:
```
go install golang.org/x/mobile/cmd/gomobile@latest
gomobile init
git clone https://github.com/XTLS/Xray-core
cd Xray-core
# написать тонкую обёртку package libxray с функциями RunTun(fd int, cfg string) / Stop()
gomobile bind -target=android -o xray.aar ./libxray
# полученный xray.aar -> app/libs/, добавить в зависимости:
#   implementation(files("libs/xray.aar"))
```

Затем в `PtunnelVpnService.startTunnel()` заменить mock-блок на реальный
вызов ядра (см. закомментированный пример там же), собрав WG-конфиг из
`ClientConfig` или xray-JSON из полей Reality.

## Механизм независимости (remote-config)

`ApiClient.config(token, tariff)` тянет актуальные креды с твоего `vpn-api`
(порт WG/AWG, SNI/shortId для Reality, awg-параметры Jc/Jmin/…). Меняешь
на серверах — клиент подтягивает и переподключается, без обновления в
сторе. Пока метод mock; переключается флагом `USE_MOCK=false` и заданием
`BASE_URL`. Контракт трёх эндпоинтов (`/client/auth`, `/client/tariffs`,
`/client/config`) описан в `ApiClient.kt` — их надо добавить в FastAPI.

## Структура

```
app/src/main/java/xyz/babyplatipus/ptunnel/
  MainActivity.kt            навигация, запрос VPN-разрешения, старт сервиса
  ui/
    MainViewModel.kt         состояние, логин, тарифы, список приложений
    theme/Theme.kt           тёмная брендовая тема
    screens/
      LoginScreen.kt         вход по ключу
      HomeScreen.kt          кнопка подключения + выбор тарифа
      SplitScreen.kt         раздельный трафик по приложениям
  vpn/
    PtunnelVpnService.kt     VpnService (mock туннель + точки для ядер)
    VpnStateHolder.kt        мост сервис<->UI
  data/
    Prefs.kt                 DataStore (токен, тариф, split)
    model/Models.kt          Tariff, ClientConfig, AwgParams, AppEntry
    remote/ApiClient.kt      клиент к vpn-api (+ контракт эндпоинтов)
```
