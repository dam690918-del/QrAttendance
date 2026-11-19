package com.college.qrattendance.data

import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.security.MessageDigest
import java.util.UUID
import kotlin.random.Random

object Repo {
    const val ROTATE_SECONDS = 20
    private const val QR_PREFIX = "QRATT"
    private const val TOKEN_TTL_MS = 5 * 60 * 1000L
    private const val TIMEOUT = 15_000L

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private val users get() = db.collection("users")
    private val requests get() = db.collection("requests")
    private val subjects get() = db.collection("subjects")
    private val sessions get() = db.collection("sessions")
    private val marks get() = db.collection("marks")

    suspend fun uid(): String {
        auth.currentUser?.let { return it.uid }
        val result = withTimeout(TIMEOUT) { auth.signInAnonymously().await() }
        return result.user?.uid ?: throw AppError("Не удалось подключиться к серверу")
    }

    suspend fun login(code: String, role: Role): User {
        val myId = uid()
        val ref = users.document(code)
        return withTimeout(TIMEOUT) {
            db.runTransaction { tr ->
                val user = tr.get(ref).toUser() ?: throw AppError("Код не найден")
                if (user.role != role) throw AppError("Этот код не подходит для роли «${role.title}»")
                if (role == Role.STUDENT) {
                    if (user.deviceId.isEmpty()) {
                        tr.update(ref, "deviceId", myId)
                    } else if (user.deviceId != myId) {
                        throw AppError("Этот код уже используется на другом телефоне. Обратитесь к администратору")
                    }
                }
                user
            }.await()
        }
    }

    suspend fun loginAdmin(password: String) {
        uid()
        val ref = db.collection("config").document("admin")
        val hash = sha256(password)
        val saved = withTimeout(TIMEOUT) { ref.get().await() }.getString("hash")
        when {
            saved == null -> withTimeout(TIMEOUT) { ref.set(mapOf("hash" to hash)).await() }
            saved != hash -> throw AppError("Неверный пароль")
        }
    }

    suspend fun sendRequest(firstName: String, lastName: String, group: String) {
        val id = uid()
        val data = mapOf(
            "firstName" to firstName,
            "lastName" to lastName,
            "group" to group,
            "status" to "pending",
            "code" to "",
            "createdAt" to FieldValue.serverTimestamp()
        )
        withTimeout(TIMEOUT) { requests.document(id).set(data).await() }
    }

    fun myRequest(): Flow<Request?> = callbackFlow {
        val reg = requests.document(uid()).addSnapshotListener { snap, error ->
            if (error != null) close(error)
            else trySend(snap?.toRequest())
        }
        awaitClose { reg.remove() }
    }

    fun pendingRequests(): Flow<List<Request>> =
        requests.whereEqualTo("status", "pending").live { it.toRequest() }
            .map { list -> list.sortedBy { it.fullName.lowercase() } }

    suspend fun approve(request: Request): String {
        val code = freeCode()
        val batch = db.batch()
        batch.set(
            users.document(code), mapOf(
                "name" to request.fullName,
                "group" to request.group,
                "role" to Role.STUDENT.key,
                "deviceId" to request.id,
                "subjects" to emptyList<String>()
            )
        )
        batch.update(requests.document(request.id), mapOf("status" to "approved", "code" to code))
        withTimeout(TIMEOUT) { batch.commit().await() }
        return code
    }

    suspend fun reject(request: Request) {
        withTimeout(TIMEOUT) { requests.document(request.id).update("status", "rejected").await() }
    }

    fun users(role: Role): Flow<List<User>> =
        users.whereEqualTo("role", role.key).live { it.toUser() }
            .map { list -> list.sortedBy { it.name.lowercase() } }

    fun watchUser(code: String): Flow<User?> = callbackFlow {
        uid()
        val reg = users.document(code).addSnapshotListener { snap, error ->
            if (error != null) close(error)
            else trySend(snap?.toUser())
        }
        awaitClose { reg.remove() }
    }

    suspend fun addTeacher(name: String, subjects: List<String>): String {
        val code = freeCode()
        val data = mapOf(
            "name" to name,
            "group" to "",
            "role" to Role.TEACHER.key,
            "deviceId" to "",
            "subjects" to subjects
        )
        withTimeout(TIMEOUT) { users.document(code).set(data).await() }
        return code
    }

    suspend fun updateTeacher(code: String, name: String, subjects: List<String>) {
        withTimeout(TIMEOUT) { users.document(code).update(mapOf("name" to name, "subjects" to subjects)).await() }
    }

    suspend fun resetDevice(code: String) {
        withTimeout(TIMEOUT) { users.document(code).update("deviceId", "").await() }
    }

    suspend fun deleteUser(code: String) {
        withTimeout(TIMEOUT) { users.document(code).delete().await() }
    }

    fun subjects(): Flow<List<Subject>> =
        subjects.live { doc -> doc.getString("name")?.let { Subject(doc.id, it) } }
            .map { list -> list.sortedBy { it.name.lowercase() } }

    suspend fun addSubject(name: String) {
        withTimeout(TIMEOUT) { subjects.add(mapOf("name" to name)).await() }
    }

    suspend fun deleteSubject(subject: Subject) {
        withTimeout(TIMEOUT) { subjects.document(subject.id).delete().await() }
    }

    suspend fun startSession(teacher: User, subject: String): Session {
        val ref = sessions.document()
        val token = newToken()
        val now = Timestamp.now()
        val data = mapOf(
            "subject" to subject,
            "teacherCode" to teacher.code,
            "teacherName" to teacher.name,
            "active" to true,
            "token" to token,
            "prevToken" to "",
            "tokenAt" to now,
            "startedAt" to now,
            "endedAt" to null
        )
        withTimeout(TIMEOUT) { ref.set(data).await() }
        return Session(ref.id, subject, teacher.code, teacher.name, true, token, now.toDate(), null)
    }

    fun rotate(sessionId: String, token: String, previous: String): Task<Void> =
        sessions.document(sessionId).update(
            mapOf("token" to token, "prevToken" to previous, "tokenAt" to Timestamp.now())
        )

    suspend fun endSession(id: String) {
        withTimeout(TIMEOUT) {
            sessions.document(id).update(mapOf("active" to false, "endedAt" to Timestamp.now())).await()
        }
    }

    fun teacherSessions(code: String): Flow<List<Session>> =
        sessions.whereEqualTo("teacherCode", code).live { it.toSession() }
            .map { list -> list.sortedByDescending { it.startedAt } }

    fun sessionMarks(sessionId: String): Flow<List<Mark>> =
        marks.whereEqualTo("sessionId", sessionId).live { it.toMark() }
            .map { list -> list.sortedBy { it.time } }

    fun studentMarks(code: String): Flow<List<Mark>> =
        marks.whereEqualTo("studentCode", code).live { it.toMark() }
            .map { list -> list.sortedByDescending { it.time } }

    fun newToken() = UUID.randomUUID().toString().replace("-", "").take(12)

    fun qrText(sessionId: String, token: String) = "$QR_PREFIX:$sessionId:$token"

    suspend fun checkIn(student: User, qr: String): String {
        val parts = qr.trim().split(":")
        if (parts.size != 3 || parts[0] != QR_PREFIX || parts[1].isBlank() || parts[2].isBlank()) {
            throw AppError("Это не кьюар-код посещаемости")
        }
        val sessionId = parts[1]
        val token = parts[2]
        val myId = uid()
        val sessionRef = sessions.document(sessionId)
        val userRef = users.document(student.code)
        val markRef = marks.document("${sessionId}_${student.code}")

        return withTimeout(TIMEOUT) {
            db.runTransaction { tr ->
                val session = tr.get(sessionRef)
                val me = tr.get(userRef).toUser()
                val alreadyMarked = tr.get(markRef).exists()

                if (me == null) throw AppError("Ваш аккаунт удалён администратором")
                if (me.deviceId != myId) throw AppError("Ваш код привязан к другому телефону")
                if (!session.exists()) throw AppError("Занятие не найдено")
                if (session.getBoolean("active") != true) throw AppError("Пара уже завершена")
                if (token != session.getString("token") && token != session.getString("prevToken")) {
                    throw AppError("Кьюар-код устарел. Отсканируйте код с экрана ещё раз")
                }
                val tokenAt = session.getTimestamp("tokenAt")?.toDate()?.time ?: 0L
                if (System.currentTimeMillis() - tokenAt > TOKEN_TTL_MS) {
                    throw AppError("Кьюар-код больше не обновляется. Попросите преподавателя открыть пару заново")
                }
                if (alreadyMarked) throw AppError("Вы уже отмечены на этой паре")

                val subject = session.getString("subject").orEmpty()
                tr.set(
                    markRef, mapOf(
                        "sessionId" to sessionId,
                        "studentCode" to me.code,
                        "studentName" to me.name,
                        "group" to me.group,
                        "subject" to subject,
                        "teacherName" to session.getString("teacherName").orEmpty(),
                        "time" to Timestamp.now()
                    )
                )
                subject
            }.await()
        }
    }

    private suspend fun freeCode(): String {
        repeat(20) {
            val code = Random.nextInt(100_000, 1_000_000).toString()
            val taken = withTimeout(TIMEOUT) { users.document(code).get().await() }.exists()
            if (!taken) return code
        }
        throw AppError("Не удалось подобрать свободный код, попробуйте ещё раз")
    }

    private fun <T> Query.live(map: (DocumentSnapshot) -> T?): Flow<List<T>> = callbackFlow {
        uid()
        val reg = addSnapshotListener { snap, error ->
            if (error != null) close(error)
            else if (snap != null) trySend(snap.documents.mapNotNull(map))
        }
        awaitClose { reg.remove() }
    }

    private fun sha256(text: String) =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}

private fun DocumentSnapshot.toUser(): User? {
    if (!exists()) return null
    val role = Role.of(getString("role")) ?: return null
    return User(
        code = id,
        name = getString("name").orEmpty(),
        group = getString("group").orEmpty(),
        role = role,
        subjects = (get("subjects") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        deviceId = getString("deviceId").orEmpty()
    )
}

private fun DocumentSnapshot.toRequest(): Request? {
    if (!exists()) return null
    return Request(
        id = id,
        firstName = getString("firstName").orEmpty(),
        lastName = getString("lastName").orEmpty(),
        group = getString("group").orEmpty(),
        status = getString("status").orEmpty(),
        code = getString("code").orEmpty()
    )
}

private fun DocumentSnapshot.toSession(): Session? {
    if (!exists()) return null
    return Session(
        id = id,
        subject = getString("subject").orEmpty(),
        teacherCode = getString("teacherCode").orEmpty(),
        teacherName = getString("teacherName").orEmpty(),
        active = getBoolean("active") == true,
        token = getString("token").orEmpty(),
        startedAt = getTimestamp("startedAt")?.toDate(),
        endedAt = getTimestamp("endedAt")?.toDate()
    )
}

private fun DocumentSnapshot.toMark(): Mark? {
    if (!exists()) return null
    return Mark(
        id = id,
        sessionId = getString("sessionId").orEmpty(),
        studentCode = getString("studentCode").orEmpty(),
        studentName = getString("studentName").orEmpty(),
        group = getString("group").orEmpty(),
        subject = getString("subject").orEmpty(),
        teacherName = getString("teacherName").orEmpty(),
        time = getTimestamp("time")?.toDate()
    )
}
