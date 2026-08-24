package xyz.babyplatipus.ptunnel.data

/**
 * Российские сервисы, которые логично пустить мимо туннеля.
 * Предлагается один раз после первого подключения.
 * Позже список стоит перенести на сервер — пакеты меняются.
 */
object DefaultBypass {
    val PACKAGES = listOf(
        "ru.yandex.searchplugin", "ru.yandex.taxi", "ru.yandex.yandexmaps",
        "ru.yandex.yandexnavi", "ru.yandex.music", "ru.yandex.mail",
        "ru.yandex.disk", "ru.beru.android", "ru.yandex.eda",
        "ru.sberbankmobile", "ru.vtb24.mobilebanking.android",
        "com.idamob.tinkoff.android", "ru.alfabank.mobile.android",
        "ru.raiffeisennews", "ru.gazprombank.android.mobilebank.app",
        "ru.mts.mymts", "ru.megafon.mlk", "ru.beeline.services",
        "ru.wildberries.ru", "ru.ozon.app.android", "ru.dns_shop.app",
        "ru.sbermarket.app", "ru.rzd.pass", "ru.aeroflot",
        "com.vkontakte.android", "ru.ok.android", "com.vk.im",
        "ru.avito.android", "ru.kinopoisk", "ru.ivi.client",
        "ru.rostel"
    )
}