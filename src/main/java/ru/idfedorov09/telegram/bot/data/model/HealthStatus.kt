package ru.idfedorov09.telegram.bot.data.model

data class HealthStatus(
    val generalStatus: String,
) {
    object Messages {
        const val HEALTH_FLOW_NOT_CONTAINS_STATUS = "Error during flow execution: " +
            "the context does not contain information about the status of the application."
    }
}
