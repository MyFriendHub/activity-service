package com.friendhub.activityservice.entity

import com.friendhub.activityservice.entity.util.ActivityStatus
import com.friendhub.activityservice.entity.util.ParticipantStatus
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("activity_participants")
data class ActivityParticipantEntity(
    @Id
    val id: Long? = null,
    @Column("activity_id")
    val activityId: Long,
    @Column("user_id")
    val userId: Long,
    @Column("invited_id")
    val invitedBy: Long,
    @Column("invited_at")
    val invitedAt: Instant,
    @Column("status")
    val status: ParticipantStatus,
) {
}