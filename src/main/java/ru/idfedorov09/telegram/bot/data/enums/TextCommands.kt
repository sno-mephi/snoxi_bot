package ru.idfedorov09.telegram.bot.data.enums

enum class TextCommands(
    /** текст команды **/
    val commandText: String,
) {

    MANAGE_PROFILES(
        "\uD83D\uDC69\u200D\uD83D\uDCBB Управление профилями",
    ),

    FEATURE_REALISE(
        "⚡\uFE0F Раскатка релиза",
    ),

    HELP(
        "\uD83D\uDC8A Помощь",
    ),

    ;

    /** Проверяет, является ли текст командой **/
    companion object {
        fun isTextCommand(text: String?) = entries.map { it.commandText }.contains(text)
    }
}
