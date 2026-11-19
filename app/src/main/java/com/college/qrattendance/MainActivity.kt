package com.college.qrattendance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.college.qrattendance.data.AppError
import com.college.qrattendance.data.Prefs
import com.college.qrattendance.data.Repo
import com.college.qrattendance.data.Role
import com.college.qrattendance.data.User
import com.college.qrattendance.ui.AdminScreen
import com.college.qrattendance.ui.AppTheme
import com.college.qrattendance.ui.LoginScreen
import com.college.qrattendance.ui.RequestScreen
import com.college.qrattendance.ui.StudentScreen
import com.college.qrattendance.ui.TeacherScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App()
                }
            }
        }
    }
}

private sealed interface Screen {
    data object Loading : Screen
    data object Login : Screen
    data object Request : Screen
    data object Admin : Screen
    data class Student(val user: User) : Screen
    data class Teacher(val user: User) : Screen
}

private fun screenFor(user: User): Screen = when (user.role) {
    Role.STUDENT -> Screen.Student(user)
    Role.TEACHER -> Screen.Teacher(user)
    Role.ADMIN -> Screen.Admin
}

@Composable
private fun App() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var screen by remember { mutableStateOf<Screen>(Screen.Loading) }

    fun enter(user: User) {
        prefs.save(user)
        screen = screenFor(user)
    }

    fun logout() {
        prefs.clear()
        screen = Screen.Login
    }

    LaunchedEffect(Unit) {
        val cached = prefs.cachedUser()
        screen = when {
            prefs.role == Role.ADMIN -> Screen.Admin
            cached == null -> Screen.Login
            else -> try {
                Repo.login(cached.code, cached.role).also { prefs.save(it) }.let(::screenFor)
            } catch (e: Exception) {
                if (generateSequence<Throwable>(e) { it.cause }.any { it is AppError }) {
                    prefs.clear()
                    Screen.Login
                } else {
                    screenFor(cached)
                }
            }
        }
    }

    when (val s = screen) {
        Screen.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        Screen.Login -> LoginScreen(
            onUser = ::enter,
            onAdmin = {
                prefs.saveAdmin()
                screen = Screen.Admin
            },
            onRequest = { screen = Screen.Request }
        )
        Screen.Request -> RequestScreen(
            onBack = { screen = Screen.Login },
            onUser = ::enter
        )
        Screen.Admin -> AdminScreen(onLogout = ::logout)
        is Screen.Student -> StudentScreen(user = s.user, onLogout = ::logout)
        is Screen.Teacher -> TeacherScreen(user = s.user, onLogout = ::logout)
    }
}
