package com.friendhub.activityservice.repository

import com.friendhub.activityservice.entity.ActivityEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface ActivityRepository : CoroutineCrudRepository<ActivityEntity, Long> {

    suspend fun findActivityByTitle(name: String): ActivityEntity?

    suspend fun findActivityById(id: Long): ActivityEntity?

    suspend fun findActivityByTitleAndCreatorId(title: String, creatorId: Long): ActivityEntity?
}