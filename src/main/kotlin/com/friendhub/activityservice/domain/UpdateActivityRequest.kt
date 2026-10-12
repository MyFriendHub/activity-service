package com.friendhub.activityservice.domain

import com.friendhub.activityservice.entity.util.ActivityType
import java.time.Instant

data class UpdateActivityRequest(
    val title: String? = null,
    val description: String? = null,
    val location: String? = null,
    val type: ActivityType? = null,
    val startAt: Instant? = null,
)