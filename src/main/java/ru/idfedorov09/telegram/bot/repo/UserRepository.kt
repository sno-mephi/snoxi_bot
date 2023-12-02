package ru.idfedorov09.telegram.bot.repo

import org.springframework.data.jpa.repository.JpaRepository
import ru.idfedorov09.telegram.bot.data.model.User

interface UserRepository : JpaRepository<User, Long> {
    fun findByTui(tui: String): User?
}
