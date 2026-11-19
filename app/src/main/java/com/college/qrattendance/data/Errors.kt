package com.college.qrattendance.data

import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.TimeoutCancellationException

fun Throwable.readable(): String {
    var e: Throwable? = this
    while (e != null) {
        when (e) {
            is AppError -> return e.message ?: "Ошибка"
            is TimeoutCancellationException -> return "Сервер не отвечает. Проверьте интернет"
            is FirebaseNetworkException -> return "Нет подключения к интернету"
            is FirebaseFirestoreException -> return when (e.code) {
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> "Сервер недоступен. Проверьте интернет"
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Нет доступа к базе данных. Проверьте правила Firestore"
                else -> "Ошибка базы данных (${e.code.name})"
            }
            is FirebaseAuthException -> return "Не удалось войти в Firebase (${e.errorCode})"
        }
        e = e.cause
    }
    if (this is FirebaseException) return "Firebase: $message"
    return message ?: "Что-то пошло не так"
}
