package ru.idfedorov09.telegram.bot.data.enums

enum class UserActionType {
    DEFAULT,

    TYPING_PORT,
    SENDING_PROPERTIES,
    CONFIRM_REMOVE_PROFILE,
    TYPING_PROPERTY_KEY_VALUE,

    /** Действия соответствующие созданию профиля **/
    TYPING_PROFILE_NAME,
    TYPING_PROFILE_REPO_URL,
    TYPING_NEW_PROFILE_PORT,
}
