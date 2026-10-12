package com.friendhub.activityservice.controller

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ActivityControllerAdvice {

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleException(invalidArgumentException: IllegalArgumentException): ResponseEntity<String> {
        return ResponseEntity(invalidArgumentException.message, HttpStatus.BAD_REQUEST)
    }
}