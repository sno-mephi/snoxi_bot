package ru.idfedorov09.telegram.bot.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import ru.idfedorov09.telegram.bot.data.model.ReleaseHistory

interface ReleaseHistoryRepository : JpaRepository<ReleaseHistory, Long> {
    @Query("DELETE FROM ReleaseHistory t WHERE t.profileName = :profileName")
    fun removeWithProfileName(@Param("profileName") profileName: String)
}