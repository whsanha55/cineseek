/** TanStack Query 훅 — A1~A6 조회. URL 상태가 원천이므로 훅은 파라미터만 받는다 */
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { apiGet } from "./client";
import { recentIds, type Marks } from "../lib/marks";
import type { ExploreState, SearchState } from "../lib/urlState";
import type {
	FilmographyResponse,
	GenresResponse,
	MovieDetail,
	MovieListResponse,
	PeopleResponse,
	PersonItem,
	SearchResponse,
	WatchProviders,
} from "./types";

export function useSearch(state: SearchState, enabled = true) {
	return useQuery({
		queryKey: ["search", state] as const,
		enabled: enabled && state.q !== "",
		queryFn: () =>
			apiGet<SearchResponse>("/cineseek/search", {
				q: state.q,
				genreId: state.genreIds,
				excludeGenreId: state.excludeGenreIds,
				yearMin: state.yearMin,
				yearMax: state.yearMax,
				ratingMin: state.ratingMin,
				directorId: state.directorId,
				castId: state.castId,
				runtimeMin: state.runtimeMin,
				runtimeMax: state.runtimeMax,
				voteCountMin: state.voteCountMin,
				voteCountMax: state.voteCountMax,
				limit: state.limit,
			}),
	});
}

export function useMovie(id: number | string | undefined) {
	return useQuery({
		queryKey: ["movie", id] as const,
		enabled: id !== undefined,
		queryFn: () => apiGet<MovieDetail>(`/cineseek/movies/${id}`),
	});
}

/** KR 시청처 — 실패해도 상세 화면은 그대로라 재시도로 늦추지 않는다 */
export function useWatchProviders(id: number) {
	return useQuery({
		queryKey: ["watchProviders", id] as const,
		retry: false,
		queryFn: () => apiGet<WatchProviders>(`/cineseek/movies/${id}/watch-providers`),
	});
}

export function useSimilar(id: number | string | undefined, limit: number) {
	return useQuery({
		queryKey: ["similar", id, limit] as const,
		enabled: id !== undefined,
		queryFn: () => apiGet<SearchResponse>(`/cineseek/movies/${id}/similar`, { limit }),
	});
}

/** 취향 추천 — 표시를 바꾸면 키가 바뀌어 다시 조회한다. 그동안 이전 결과를 유지해 깜빡이지 않는다 */
export function useRecommendations(marks: Marks, limit: number, enabled: boolean) {
	const params = {
		likedId: recentIds(marks.liked),
		dislikedId: recentIds(marks.disliked),
		watchedId: recentIds(marks.watched),
		limit,
	};
	return useQuery({
		queryKey: ["recommendations", params] as const,
		enabled,
		placeholderData: keepPreviousData,
		queryFn: () => apiGet<SearchResponse>("/cineseek/recommendations", params),
	});
}

export function useExplore(query: ExploreState) {
	return useQuery({
		queryKey: ["explore", query] as const,
		queryFn: () => apiGet<MovieListResponse>("/cineseek/movies", { ...query }),
	});
}

export function useGenres() {
	return useQuery({
		queryKey: ["genres"] as const,
		staleTime: Infinity,
		queryFn: () => apiGet<GenresResponse>("/cineseek/genres"),
	});
}

/** 자동완성 — 2자 이상부터 조회 (임계값 아래는 빈 목록) */
export function usePeople(q: string, role: "director" | "cast") {
	return useQuery({
		queryKey: ["people", q, role] as const,
		enabled: q.trim().length >= 2,
		queryFn: () => apiGet<PeopleResponse>("/cineseek/people", { q: q.trim(), role }),
	});
}

/** 새로고침 후 필터 칩 라벨 복원 — URL의 directorId/castId → 이름 */
export function usePerson(id: number | null, role: "director" | "cast") {
	return useQuery({
		queryKey: ["person", id, role] as const,
		enabled: id !== null,
		retry: false,
		queryFn: () => apiGet<PersonItem>(`/cineseek/people/${id}`, { role }),
	});
}

export type FilmographySort = "release" | "rating";

export interface FilmographyQuery {
	personId: number | null;
	sort: FilmographySort;
	page: number;
	limit: number;
	/** 영화 상세의 '다른 작품' 행에서 현재 영화를 뺄 때 사용 */
	excludeMovieId: number | null;
}

/** 배우 필모그래피 — collecting이면 백그라운드 수집 중이므로 주기 재조회로 갱신된 목록을 받는다 */
export function useFilmography(query: FilmographyQuery, enabled = true) {
	return useQuery({
		queryKey: [
			"filmography",
			query.personId,
			query.sort,
			query.page,
			query.limit,
			query.excludeMovieId,
		] as const,
		enabled: enabled && query.personId !== null,
		queryFn: () =>
			apiGet<FilmographyResponse>(`/cineseek/people/${query.personId}/filmography`, {
				sort: query.sort,
				page: query.page,
				limit: query.limit,
				excludeMovieId: query.excludeMovieId,
			}),
		refetchInterval: (q) => (q.state.data?.collecting ? 10_000 : false),
	});
}
