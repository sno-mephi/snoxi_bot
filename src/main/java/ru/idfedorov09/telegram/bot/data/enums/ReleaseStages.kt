package ru.idfedorov09.telegram.bot.data.enums

enum class ReleaseStages(
    val description: String,
) {
    ABS_EMPTY("Новый"),

    STARTING_TEST("Запускается на тестинге"),

    TESTING("В тестинге, ожидает апрувов"),

    CANCELED("Раскатка отменена"),

    FAILURE("В тестинге обнаружены ошибки/баги, раскатка отменена"),

    RELEASE_PROCESSING("В процессе выкатки в прод"),

    IN_PRODUCTION("Запущено в продакшне"),
    ;

    operator fun invoke() = this.toString()
}
