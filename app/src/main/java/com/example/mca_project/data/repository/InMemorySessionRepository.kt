package com.example.mca_project.data.repository

import com.example.mca_project.domain.model.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 인메모리 세션 저장소 (Room 연동 전 임시 구현).
 * 앱이 종료되면 데이터는 사라진다.
 */
@Singleton
class InMemorySessionRepository @Inject constructor() : SessionRepository {

    private val sessions = MutableStateFlow<List<Session>>(emptyList())

    override fun observeSessions() = sessions.asStateFlow()

    override suspend fun getSession(id: String): Session? =
        sessions.value.firstOrNull { it.id == id }

    override suspend fun saveSession(session: Session) {
        sessions.value = sessions.value.filterNot { it.id == session.id } + session
    }
}