package ru.idfedorov09.telegram.bot.util

import ru.idfedorov09.telegram.bot.data.GlobalConstants

object MessageUtils {

    fun String.markdownFormat(): String {
        return this
            .replace("_", "\\_")
            .replace("*", "\\*")
            .replace("[", "\\[")
            .replace("]", "\\]")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .replace("~", "\\~")
            .replace("`", "\\`")
            .replace(">", "\\>")
            .replace("#", "\\#")
            .replace("+", "\\+")
            .replace("-", "\\-")
            .replace("=", "\\=")
            .replace("|", "\\|")
            .replace("{", "\\{")
            .replace("}", "\\}")
            .replace(".", "\\.")
            .replace("!", "\\!")
    }

    // нужно чтобы не словить слишком длинное сообщение
    fun String.shortMessage(maxLength: Int = GlobalConstants.MAX_MSG_LENGTH): String {
        return if (this.length > maxLength) {
            this.substring(0, maxLength - 3).plus("...")
        } else {
            this
        }
    }
}