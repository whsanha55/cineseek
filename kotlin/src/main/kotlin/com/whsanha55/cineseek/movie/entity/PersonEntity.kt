package com.whsanha55.cineseek.movie.entity

import com.whsanha55.cineseek.global.base.BaseEntity
import com.whsanha55.cineseek.movie.enums.PersonFilmoStateEnum
import com.whsanha55.cineseek.movie.vo.TmdbPersonInfo
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

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

    /** 영어 이름 — 인물 페이지 URL slug의 원천. TMDB에 영어 번역이 없으면 null */
    var nameEn: String? = null
        protected set

    var birthday: LocalDate? = null
        protected set

    var deathday: LocalDate? = null
        protected set

    /** 출생지 TMDB 원문 (영어) */
    var placeOfBirth: String? = null
        protected set

    /** 출생지 한국어 번역 — 원문이 바뀌면 지우고 다시 번역한다 */
    var placeOfBirthKo: String? = null
        protected set

    var biographyKo: String? = null
        protected set

    /** biographyKo가 TMDB 한국어 소개가 아니라 영어 소개의 기계 번역인지 */
    @Column(nullable = false)
    var biographyKoTranslated: Boolean = false
        protected set

    var biographyEn: String? = null
        protected set

    var imdbId: String? = null
        protected set

    var instagramId: String? = null
        protected set

    var homepage: String? = null
        protected set

    /** 번역이 영구 실패한 번역 버전 — 이 버전에서는 번역 대상에서 뺀다 */
    var translationFailedModel: String? = null
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

    /**
     * TMDB 재수집 반영. 번역은 원문(출생지·영어 소개)이 그대로일 때만 유지한다 — 바뀌면 지우고 실패 기록도 풀어 다시 번역하게 한다.
     * TMDB에 한국어 소개가 생기면 번역 대신 그것을 쓴다
     */
    fun updateFrom(info: TmdbPersonInfo) {
        val sourceChanged = info.placeOfBirth != placeOfBirth || info.biographyEn != biographyEn
        name = info.name
        nameEn = info.nameEn
        profilePath = info.profilePath
        birthday = info.birthday
        deathday = info.deathday
        placeOfBirth = info.placeOfBirth
        biographyEn = info.biographyEn
        imdbId = info.imdbId
        instagramId = info.instagramId
        homepage = info.homepage
        if (sourceChanged) {
            placeOfBirthKo = null
            translationFailedModel = null
        }
        if (info.biographyKo != null || sourceChanged || !biographyKoTranslated) {
            biographyKo = info.biographyKo
            biographyKoTranslated = false
        }
    }

    /** 번역 결과 반영 — 비어 있던 항목만 채운다. 소개 번역은 기계 번역으로 표시한다 */
    fun applyTranslation(placeOfBirthKo: String?, biographyKo: String?) {
        if (this.placeOfBirthKo == null && placeOfBirth != null) this.placeOfBirthKo = placeOfBirthKo
        if (this.biographyKo == null && biographyEn != null && biographyKo != null) {
            this.biographyKo = biographyKo
            biographyKoTranslated = true
        }
    }

    /** 번역 영구 실패 기록 — 같은 번역 버전으로는 다시 시도하지 않는다 */
    fun markTranslationFailed(version: String) {
        translationFailedModel = version
    }

    /** 필모그래피 수집 결과 반영 — 확인 시각, 외부/저장 출연작 수, 상태를 함께 갱신한다 */
    fun applyFilmography(checkedAt: Instant, externalCount: Int?, storedCount: Int?, state: PersonFilmoStateEnum) {
        filmoCheckedAt = checkedAt
        filmoExternalCount = externalCount
        filmoStoredCount = storedCount
        filmoState = state
    }
}
