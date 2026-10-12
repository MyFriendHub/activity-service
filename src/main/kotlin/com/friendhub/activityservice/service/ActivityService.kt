package com.friendhub.activityservice.service

import com.friendhub.activityservice.domain.ActivityResponse
import com.friendhub.activityservice.domain.CreateActivityRequest
import com.friendhub.activityservice.domain.UpdateActivityRequest
import com.friendhub.activityservice.entity.ActivityEntity
import com.friendhub.activityservice.entity.util.ActivityStatus
import com.friendhub.activityservice.entity.util.ActivityType
import com.friendhub.activityservice.repository.ActivityRepository
import kotlinx.coroutines.flow.toList
import org.springframework.data.crossstore.ChangeSetPersister
import org.springframework.stereotype.Service
import java.time.Instant
import kotlin.String

@Service
class ActivityService(
    private val activityRepository: ActivityRepository,
) {

    suspend fun saveActivity(createActivityRequest: CreateActivityRequest, creatorId: Long) {
        val activityEntity = createActivityRequest.toEntity(creatorId)
        activityRepository.save(activityEntity)
    }

    suspend fun saveActivity(updateActivityRequest: UpdateActivityRequest, activityId: Long) {
        val previousEntity = activityRepository.findActivityById(activityId)

        val updated = previousEntity?.copy(
            title = updateActivityRequest.title ?: previousEntity.title,
            description = updateActivityRequest.description ?: previousEntity.description,
            startAt = updateActivityRequest.startAt ?: previousEntity.startAt,
            location = updateActivityRequest.location ?: previousEntity.location,
            type = updateActivityRequest.type ?: previousEntity.type,
            updatedAt = Instant.now(),
        )!!

        activityRepository.save(updated)
    }

    suspend fun deleteActivity(activityId: Long) {
        activityRepository.deleteById(activityId)
    }

    suspend fun getActivity(activityId: Long): ActivityResponse {
        val foundActivity = activityRepository.findActivityById(activityId) ?: throw ChangeSetPersister.NotFoundException()
        return ActivityResponse(
            creatorId = foundActivity.creatorId,
            title = foundActivity.title,
            description = foundActivity.description,
            location = foundActivity.location,
            type = foundActivity.type,
            startAt = foundActivity.startAt!!,
            status = foundActivity.status,
            finishedAt = foundActivity.finishedAt!!,
            createdAt = foundActivity.createdAt!!,
            updatedAt = foundActivity.updatedAt!!,
        )
    }
}
