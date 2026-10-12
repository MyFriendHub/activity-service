package com.friendhub.activityservice.entity


import com.friendhub.activityservice.entity.util.ActivityStatus
import com.friendhub.activityservice.entity.util.ActivityType
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("activities")
data class ActivityEntity(
    @Id
    val id: Long? = null,
    @Column("creator_id")
    val creatorId: Long,
    @Column("title")
    val title: String,
    @Column("description")
    val description: String,
    @Column("start_at")
    val startAt: Instant? = null,
    @Column("location")
    val location: String,
    @Column("status")
    val status: ActivityStatus,
    @Column("type")
    val type: ActivityType,
    @Column("finished_at")
    val finishedAt: Instant? = null,
    @Column("created_at")
    val createdAt: Instant? = null,
    @Column("updated_at")
    val updatedAt: Instant? = null,
)