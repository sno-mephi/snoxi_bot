package ru.idfedorov09.telegram.bot.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.springframework.stereotype.Component
import java.util.*
import kotlin.collections.ArrayDeque

@Component
class CoroutineManager {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val taskQue: ArrayDeque<() -> Unit> = ArrayDeque(listOf())

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
            task()
        }
    }

    private fun runTasks() {
        coroutineScope.launch {
            while (taskQue.isNotEmpty()) {
                val currentTask = taskQue.first()
                launch { currentTask() }.join()
                taskQue.removeFirst()
            }
        }
    }
}
