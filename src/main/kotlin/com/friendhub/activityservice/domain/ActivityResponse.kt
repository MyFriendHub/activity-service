package com.friendhub.activityservice.domain

import com.friendhub.activityservice.entity.util.ActivityStatus
import com.friendhub.activityservice.entity.util.ActivityType
import java.time.Instant

data class ActivityResponse(
    val creatorId: Long,
    val title: String,
    val description: String,
    val location: String,
    val type: ActivityType,
    val startAt: Instant,
    val status: ActivityStatus,
    val finishedAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
)
