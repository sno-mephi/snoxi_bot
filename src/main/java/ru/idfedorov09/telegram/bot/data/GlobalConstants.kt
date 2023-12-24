package ru.idfedorov09.telegram.bot.data

object GlobalConstants {

    const val HEALTH_PATH = "/snoxi/is_alive"
    const val WEBHOOK_PATH = "/snoxi/"

    const val QUALIFIER_FLOW_TG_BOT = "tg_bot_flow_builder"
    const val QUALIFIER_FLOW_HEALTH_STATUS = "health_flow_builder"
    const val QUEUE_PRE_PREFIX = "frjekcs_ewer_idfed09_user_bot_que_"
    const val BASE_ADMIN_ID = "920061911"

    const val MAX_MSG_LENGTH = 2048
    const val REDIS_REALISE_REFPROFILE_NAME = "realise_refprofile"

    /** минимальное количество апрувов для того, чтобы начать катить релиз в прод **/
    const val MIN_APPROVES_COUNT = 1
}
