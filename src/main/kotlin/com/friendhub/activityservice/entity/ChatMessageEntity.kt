package com.friendhub.activityservice.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("chat_messages")
data class ChatMessageEntity(
    @Id
    val id: Long? = null,
    @Column("activity_id")
    val activityId: Long,
    @Column("author_id")
    val authorId: Long,
    @Column("text")
    val text: String,
    @Column("created_at")
    val createdAt: Instant,
    @Column("deleted_at")
    val deletedAt: Instant? = null,
)