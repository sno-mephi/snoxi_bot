package ru.idfedorov09.telegram.bot.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.*
import kotlin.collections.ArrayDeque

@Component
class CoroutineManager {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val taskQue: ArrayDeque<() -> Unit> = ArrayDeque(listOf())

    companion object {
        private val log = LoggerFactory.getLogger(CoroutineManager::class.java)
    }

    fun addWaitTask(task: () -> Unit) {
        if (taskQue.isEmpty()) {
            taskQue.add(task)
            runTasks()
        } else {
            taskQue.add(task)
        }
    }

    fun doAsync(task: suspend () -> Unit) {
        coroutineScope.launch {
            runCatching {
                task()
            }.onFailure { e ->
                log.error("ERROR DURING EXECUTE ASYNC TASK: {}", e.stackTraceToString())
            }
        }
    }

    private fun runTasks() {
        coroutineScope.launch {
            while (taskQue.isNotEmpty()) {
                val currentTask = taskQue.first()
                launch {
                    runCatching { currentTask() }
                }.join()
                taskQue.removeFirst()
            }
        }
    }
}
