package com.example.mca_project.data.repository

import com.example.mca_project.domain.model.Session
import kotlinx.coroutines.flow.Flow

/**
 * 세션 저장/조회 추상화.
 * 현재는 인메모리 구현(InMemorySessionRepository).
 * TODO(db): Room DB(SessionDao/InferenceResultDao) 기반 구현으로 교체.
 */
interface SessionRepository {
    fun observeSessions(): Flow<List<Session>>
    suspend fun getSession(id: String): Session?
    suspend fun saveSession(session: Session)
}