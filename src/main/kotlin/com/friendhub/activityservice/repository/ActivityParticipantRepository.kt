package com.friendhub.activityservice.repository

import com.friendhub.activityservice.entity.ActivityParticipantEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface ParticipantRepository : CoroutineCrudRepository<ActivityParticipantEntity, Long> {
    fun findByActivityId(activityId: Long): Flow<ActivityParticipantEntity>
    suspend fun findByActivityIdAndUserId(activityId: Long, userId: Long): ActivityParticipantEntity?
}