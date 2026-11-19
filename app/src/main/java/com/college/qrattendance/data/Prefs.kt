package com.college.qrattendance.data

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    val role: Role? get() = Role.of(sp.getString("role", null))

    fun cachedUser(): User? {
        val role = role ?: return null
        val code = sp.getString("code", null) ?: return null
        return User(
            code = code,
            name = sp.getString("name", "").orEmpty(),
            group = sp.getString("group", "").orEmpty(),
            role = role,
            subjects = sp.getString("subjects", "").orEmpty().split("\n").filter { it.isNotBlank() }
        )
    }

    fun save(user: User) {
        sp.edit()
            .putString("role", user.role.key)
            .putString("code", user.code)
            .putString("name", user.name)
            .putString("group", user.group)
            .putString("subjects", user.subjects.joinToString("\n"))
            .apply()
    }

    fun saveAdmin() {
        sp.edit().clear().putString("role", Role.ADMIN.key).apply()
    }

    fun clear() {
        sp.edit().clear().apply()
    }
}
