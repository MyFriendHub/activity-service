package com.friendhub.activityservice.controller

import com.friendhub.activityservice.domain.ActivityResponse
import com.friendhub.activityservice.domain.CreateActivityRequest
import com.friendhub.activityservice.domain.UpdateActivityRequest
import com.friendhub.activityservice.service.ActivityService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ActivityController(
    private val activityService: ActivityService,
) {

    @RequestMapping(value = ["/activity/get/{activityId}"], method = [RequestMethod.GET])
    suspend fun getActivities(
        @PathVariable activityId: Long
    ): ResponseEntity<ActivityResponse> {
        val activityResponse = activityService.getActivity(activityId)
        return ResponseEntity(activityResponse, HttpStatus.OK)
    }

    @RequestMapping(value = ["/activity/create"], method = [RequestMethod.POST])
    suspend fun createActivity(
        @RequestBody createActivityRequest: CreateActivityRequest,
        @RequestParam creatorId: Long
    ): ResponseEntity<String> {
        activityService.saveActivity(createActivityRequest, creatorId)
        return ResponseEntity( "${createActivityRequest.title} has been created", HttpStatus.CREATED,)
    }

    @RequestMapping(value = ["/activity/update/{activityId}"], method = [RequestMethod.PATCH])
    suspend fun updateActivity(
        @RequestBody updateActivityRequest: UpdateActivityRequest,
        @PathVariable activityId: Long
    ): ResponseEntity<String> {
        activityService.saveActivity(updateActivityRequest, activityId)
        return ResponseEntity("${updateActivityRequest.title} has been updated", HttpStatus.OK)
    }

    @RequestMapping(value = ["/activity/delete/{activityId}"], method = [RequestMethod.DELETE])
    suspend fun deleteActivity(
        @PathVariable activityId: Long
    ): ResponseEntity<Unit> {
        activityService.deleteActivity(activityId)
        return ResponseEntity(HttpStatus.NO_CONTENT)
    }
}