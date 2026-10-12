package com.friendhub.activityservice.domain

import com.friendhub.activityservice.entity.ActivityEntity
import com.friendhub.activityservice.entity.util.ActivityStatus
import com.friendhub.activityservice.entity.util.ActivityType
import java.time.Instant

data class CreateActivityRequest(
    val creatorId: Long,
    val title: String,
    val description: String,
    val location: String,
    val type: ActivityType,
    val startAt: Instant,
) {
    fun toEntity(creatorId: Long): ActivityEntity {
        return ActivityEntity(
            creatorId = creatorId,
            title = title,
            description = description,
            startAt = startAt,
            location = location,
            status = ActivityStatus.DRAFT,
            type = type,
            finishedAt = Instant.now().plusSeconds(3600),
        )
    }
}