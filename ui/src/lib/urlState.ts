/**
 * URL 쿼리 ↔ 확정 검색·필터 상태 변환 — UI-PLAN §3 상태 원칙.
 * 확정 상태는 URL이 원천. 파싱은 관대하게(잘못된 값은 버림), 직렬화는 기본값을 생략한다
 */

export interface SearchState {
	q: string;
	genreIds: number[];
	yearMin: number | null;
	yearMax: number | null;
	ratingMin: number | null;
	directorId: number | null;
	castId: number | null;
	/** 표시 개수 — URL에 유지해야 뒤로가기 시 복원된다 (UI2) */
	limit: number;
}

export const SEARCH_LIMITS = [20, 40, 50] as const;
export const DEFAULT_SEARCH_LIMIT = 20;

export const DEFAULT_SEARCH_STATE: SearchState = {
	q: "",
	genreIds: [],
	yearMin: null,
	yearMax: null,
	ratingMin: null,
	directorId: null,
	castId: null,
	limit: DEFAULT_SEARCH_LIMIT,
};

/** 올바른 정수만 통과, 나머지 null */
function toInt(value: string | null): number | null {
	if (value === null || value.trim() === "") return null;
	const n = Number(value);
	return Number.isInteger(n) ? n : null;
}

/** 범위 밖은 클램프 */
function clampInt(value: number | null, min: number, max: number, fallback: number | null): number | null {
	if (value === null) return fallback;
	return Math.min(max, Math.max(min, value));
}

export function parseSearchState(params: URLSearchParams): SearchState {
	const genreIds = [
		...new Set(
			params
				.getAll("genreId")
				.map((v) => toInt(v))
				.filter((v): v is number => v !== null && v > 0),
		),
	];
	const yearMin = clampInt(toInt(params.get("yearMin")), 1900, 2100, null);
	const yearMax = clampInt(toInt(params.get("yearMax")), 1900, 2100, null);
	// yearMin > yearMax 라도 그대로 둔다 — 백엔드가 둘 다 만족하는 결과만 내려 빈 결과가 되고, 사용자가 칩으로 해제한다
	const ratingRaw = toInt(params.get("ratingMin"));
	const ratingMin = ratingRaw !== null && ratingRaw >= 0 && ratingRaw <= 10 ? ratingRaw : null;
	const limitRaw = toInt(params.get("limit"));
	const limit =
		limitRaw !== null && SEARCH_LIMITS.includes(limitRaw as (typeof SEARCH_LIMITS)[number])
			? limitRaw
			: DEFAULT_SEARCH_LIMIT;
	const positiveId = (key: string): number | null => {
		const v = toInt(params.get(key));
		return v !== null && v > 0 ? v : null;
	};

	return {
		q: (params.get("q") ?? "").trim(),
		genreIds,
		yearMin,
		yearMax,
		ratingMin,
		directorId: positiveId("directorId"),
		castId: positiveId("castId"),
		limit,
	};
}

/** 기본값은 생략해 URL을 짧게 — 공유·복원에도 동일 결과 */
export function serializeSearchState(state: SearchState): URLSearchParams {
	const out = new URLSearchParams();
	if (state.q) out.set("q", state.q);
	for (const g of state.genreIds) out.append("genreId", String(g));
	if (state.yearMin !== null) out.set("yearMin", String(state.yearMin));
	if (state.yearMax !== null) out.set("yearMax", String(state.yearMax));
	if (state.ratingMin !== null) out.set("ratingMin", String(state.ratingMin));
	if (state.directorId !== null) out.set("directorId", String(state.directorId));
	if (state.castId !== null) out.set("castId", String(state.castId));
	if (state.limit !== DEFAULT_SEARCH_LIMIT) out.set("limit", String(state.limit));
	return out;
}

/** 탐색 상태 — /explore */
export type ExploreSort = "rating" | "release" | "vote_count";

export const EXPLORE_SORT_LABEL: Record<ExploreSort, string> = {
	rating: "평점 높은 순",
	release: "최근 개봉 순",
	vote_count: "투표 많은 순", // 기준(vote_count)을 라벨 그대로 — "인기순" 금지 (U-D5)
};

export interface ExploreState {
	genreId: number | null;
	sort: ExploreSort;
	page: number;
	limit: number;
	/** 상세 인물 클릭 진입용 (F3) — 감독·배우 필터 */
	directorId: number | null;
	castId: number | null;
}

export const DEFAULT_EXPLORE_STATE: ExploreState = {
	genreId: null,
	sort: "rating",
	page: 0,
	limit: 20,
	directorId: null,
	castId: null,
};

export function parseExploreState(params: URLSearchParams): ExploreState {
	const genreId = toInt(params.get("genreId"));
	const sortRaw = params.get("sort");
	const sort: ExploreSort =
		sortRaw === "release" || sortRaw === "vote_count" ? sortRaw : "rating";
	const page = Math.max(0, toInt(params.get("page")) ?? 0);
	const limitRaw = clampInt(toInt(params.get("limit")), 1, 50, null);
	const personId = (key: string) => {
		const v = toInt(params.get(key));
		return v !== null && v > 0 ? v : null;
	};
	return {
		genreId: genreId !== null && genreId > 0 ? genreId : null,
		sort,
		page,
		limit: limitRaw ?? 20,
		directorId: personId("directorId"),
		castId: personId("castId"),
	};
}

export function serializeExploreState(state: ExploreState): URLSearchParams {
	const out = new URLSearchParams();
	if (state.genreId !== null) out.set("genreId", String(state.genreId));
	if (state.sort !== "rating") out.set("sort", state.sort);
	if (state.page > 0) out.set("page", String(state.page));
	if (state.limit !== 20) out.set("limit", String(state.limit));
	if (state.directorId !== null) out.set("directorId", String(state.directorId));
	if (state.castId !== null) out.set("castId", String(state.castId));
	return out;
}

/** SearchState → ExploreState로 조건 유지 이관 (검색어 없는 /search → /explore, §3) */
export function searchToExploreState(state: SearchState): ExploreState {
	return {
		...DEFAULT_EXPLORE_STATE,
		genreId: state.genreIds[0] ?? null,
		directorId: state.directorId,
		castId: state.castId,
	};
}
