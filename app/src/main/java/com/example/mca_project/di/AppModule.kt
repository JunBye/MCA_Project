package com.example.mca_project.di

import com.example.mca_project.data.repository.InMemorySessionRepository
import com.example.mca_project.data.repository.SessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 인터페이스 → 구현체 바인딩 모듈.
 *
 * ModelManager는 @Inject constructor라 별도 provide가 필요 없고,
 * SessionRepository는 인터페이스라 @Binds로 구현체를 연결한다.
 *
 * TODO(db): Room DB 도입 시 @Provides로 AppDatabase/DAO 제공하는
 *           DatabaseModule을 추가하고, 여기 바인딩을 Room 구현체로 교체.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindSessionRepository(
        impl: InMemorySessionRepository,
    ): SessionRepository
}