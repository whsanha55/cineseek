package com.whsanha55.cineseek.movie.entity

import com.whsanha55.cineseek.global.base.BaseEntity
import com.whsanha55.cineseek.movie.enums.PersonFilmoStateEnum
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 인물 마스터 — person_id는 TMDB person id를 그대로 쓴다 (자연 PK, 자동 생성 아님).
 * movie_cast·movie_director의 name 중복 저장과 달리 여기가 인물 정보의 원천이고,
 * 필모그래피 수집 진행 상황을 filmo_* 컬럼이 추적한다
 */
@Entity
@Table(name = "person")
class PersonEntity(
    @Id
    val personId: Long,
    name: String,
    profilePath: String? = null,
) : BaseEntity() {

    @Column(nullable = false)
    var name: String = name
        protected set

    var profilePath: String? = profilePath
        protected set

    var filmoCheckedAt: Instant? = null
        protected set

    var filmoExternalCount: Int? = null
        protected set

    var filmoStoredCount: Int? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var filmoState: PersonFilmoStateEnum = PersonFilmoStateEnum.NONE
        protected set

    /** 인물 기본 정보 갱신 — 재수집된 이름·프로필 경로를 반영한다 */
    fun updateProfile(name: String, profilePath: String?) {
        this.name = name
        this.profilePath = profilePath
    }

    /** 필모그래피 수집 결과 반영 — 확인 시각, 외부/저장 출연작 수, 상태를 함께 갱신한다 */
    fun applyFilmography(checkedAt: Instant, externalCount: Int?, storedCount: Int?, state: PersonFilmoStateEnum) {
        filmoCheckedAt = checkedAt
        filmoExternalCount = externalCount
        filmoStoredCount = storedCount
        filmoState = state
    }
}
