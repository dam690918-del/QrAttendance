package com.college.qrattendance.data

import java.util.Date

enum class Role(val key: String, val title: String) {
    STUDENT("student", "Студент"),
    TEACHER("teacher", "Преподаватель"),
    ADMIN("admin", "Администратор");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key }
    }
}

data class User(
    val code: String,
    val name: String,
    val group: String,
    val role: Role,
    val subjects: List<String> = emptyList(),
    val deviceId: String = ""
)

data class Request(
    val id: String,
    val firstName: String,
    val lastName: String,
    val group: String,
    val status: String,
    val code: String
) {
    val fullName get() = "$lastName $firstName".trim()
}

data class Subject(val id: String, val name: String)

data class Session(
    val id: String,
    val subject: String,
    val teacherCode: String,
    val teacherName: String,
    val active: Boolean,
    val token: String,
    val startedAt: Date?,
    val endedAt: Date?
)

data class Mark(
    val id: String,
    val sessionId: String,
    val studentCode: String,
    val studentName: String,
    val group: String,
    val subject: String,
    val teacherName: String,
    val time: Date?
)

class AppError(message: String) : Exception(message)
