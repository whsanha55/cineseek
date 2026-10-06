/** TanStack Query 훅 — A1~A6 조회. URL 상태가 원천이므로 훅은 파라미터만 받는다 */
import { useQuery } from "@tanstack/react-query";
import { apiGet } from "./client";
import type { SearchState } from "../lib/urlState";
import type {
	FilmographyResponse,
	GenresResponse,
	MovieDetail,
	MovieListResponse,
	PeopleResponse,
	PersonItem,
	SearchResponse,
} from "./types";

export function useSearch(state: SearchState, enabled = true) {
	return useQuery({
		queryKey: ["search", state] as const,
		enabled: enabled && state.q !== "",
		queryFn: () =>
			apiGet<SearchResponse>("/cineseek/search", {
				q: state.q,
				genreId: state.genreIds,
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

export function useSimilar(id: number | string | undefined, limit = 8) {
	return useQuery({
		queryKey: ["similar", id, limit] as const,
		enabled: id !== undefined,
		queryFn: () => apiGet<SearchResponse>(`/cineseek/movies/${id}/similar`, { limit }),
	});
}

export interface ExploreQuery {
	genreId: number | null;
	sort: "rating" | "release" | "vote_count";
	page: number;
	limit: number;
	directorId: number | null;
	castId: number | null;
	ratingMin: number | null;
	voteCountMin: number | null;
	voteCountMax: number | null;
}

export function useExplore(query: ExploreQuery) {
	return useQuery({
		queryKey: ["explore", query] as const,
		queryFn: () =>
			apiGet<MovieListResponse>("/cineseek/movies", {
				genreId: query.genreId,
				sort: query.sort,
				page: query.page,
				limit: query.limit,
				directorId: query.directorId,
				castId: query.castId,
				ratingMin: query.ratingMin,
				voteCountMin: query.voteCountMin,
				voteCountMax: query.voteCountMax,
			}),
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
