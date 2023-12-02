package ru.idfedorov09.telegram.bot.repo

import org.springframework.data.jpa.repository.JpaRepository
import ru.idfedorov09.telegram.bot.data.model.SnoxiUser

interface SnoxiUserRepository : JpaRepository<SnoxiUser, Long> {
    fun findByTui(tui: String): SnoxiUser?
}