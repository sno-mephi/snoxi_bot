package ru.idfedorov09.telegram.bot.data.enums

enum class ReleaseStages {
    /** Вообще не раскатан, ничего до этого вообще не было;
     * иными словами - опорный профиль только создан **/
    ABS_EMPTY,

    /** В тестинге, ожидает апрувов **/
    TESTING,

    /** Раскатка отменена **/
    CANCELED,

    /** Обнаружены ошибки, дальше не катится **/
    FAILURE,

    /** Началась выкадка в прод**/
    RELEASE_PROCESSING,

    /** В проде **/
    FINISHED,
    ;

    operator fun invoke() = this.toString()
}
