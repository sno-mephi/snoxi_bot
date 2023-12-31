package ru.idfedorov09.telegram.bot.data

object GlobalConstants {

    const val HEALTH_PATH = "/snoxi/is_alive"
    const val WEBHOOK_PATH = "/snoxi/"

    const val QUALIFIER_FLOW_TG_BOT = "tg_bot_flow_builder"
    const val QUALIFIER_FLOW_HEALTH_STATUS = "health_flow_builder"
    const val QUEUE_PRE_PREFIX = "frjekcs_ewer_idfed09_user_bot_que_"
    const val BASE_ADMIN_ID = "920061911"
    const val BASE_DEVELOPERS_CHAT_ID = "-1002057270905"

    const val MAX_MSG_LENGTH = 2048
    const val RR_PROFILE1 = "realise_profile_1"
    const val RR_PROFILE2 = "realise_profile_2"
    const val RR_TEST_PROFILE = "realise_testing"
    const val RR_REALISE_STAGE = "realise_stage"
    /** Текущее количество апрувов **/
    const val RR_APPROVES_COUNT = "realise_approves_count"
    const val RR_NEW_VERSION = "realise_new_version_commit_hash"
    const val RR_IS_FIRST_ACTIVE = "release_is_first_active"

    /** Минимальное количество апрувов для того, чтобы начать катить релиз в прод **/
    const val MIN_APPROVES_COUNT = 1
}
