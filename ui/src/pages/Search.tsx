import { useCallback } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useSearch } from "../api/hooks";
import { FilterBar } from "../components/FilterBar";
import { MovieGrid, MovieGridSkeleton } from "../components/MovieGrid";
import { EmptyState, ErrorState } from "../components/StateViews";
import {
	DEFAULT_SEARCH_STATE,
	parseSearchState,
	SEARCH_LIMITS,
	searchToExploreState,
	serializeExploreState,
	serializeSearchState,
	type SearchState,
} from "../lib/urlState";

/** 검색 결과 — 필터 바 + 결과 그리드. 확정 상태는 전부 URL (F2) */
export function Search() {
	const [searchParams, setSearchParams] = useSearchParams();
	const navigate = useNavigate();
	const state = parseSearchState(searchParams);
	const { data, isPending, isError, refetch } = useSearch(state);

	// 필터 한 항목 변경 — replace (§3)
	const patch = useCallback(
		(p: Partial<SearchState>) => {
			const next = { ...state, ...p };
			// 검색어가 비면 /explore로 조건 이관 (§3 — 검색어 없는 /search는 탐색으로)
			if (!next.q) {
				navigate(`/explore?${serializeExploreState(searchToExploreState(next))}`, { replace: true });
				return;
			}
			setSearchParams(serializeSearchState(next), { replace: true });
		},
		[state, navigate, setSearchParams],
	);

	const nextLimit = SEARCH_LIMITS[Math.min(SEARCH_LIMITS.findIndex((l) => l === state.limit) + 1, SEARCH_LIMITS.length - 1)];
	const canLoadMore = data?.page.hasNext === true && state.limit < SEARCH_LIMITS[SEARCH_LIMITS.length - 1];

	if (!state.q) {
		// 초기 진입 — q 없으면 안내만 (폼 제출은 항상 q를 싣고 온다)
		return (
			<EmptyState
				exploreTo={`/explore?${serializeExploreState(searchToExploreState(state))}`}
				navigate={navigate}
			/>
		);
	}

	return (
		<div className="space-y-6 py-8">
			<FilterBar
				state={state}
				onChange={patch}
				onReset={() =>
					patch({
						genreIds: [],
						yearMin: null,
						yearMax: null,
						ratingMin: null,
						directorId: null,
						castId: null,
					})
				}
			/>

			{isError ? (
				<ErrorState onRetry={() => refetch()} />
			) : isPending ? (
				<MovieGridSkeleton />
			) : data!.items.length === 0 ? (
				<EmptyState
					onResetFilters={() =>
						patch({
							genreIds: [],
							yearMin: null,
							yearMax: null,
							ratingMin: null,
							directorId: null,
							castId: null,
						})
					}
					onResetAll={() => patch({ ...DEFAULT_SEARCH_STATE, q: state.q })}
					exploreTo={`/explore?${serializeExploreState(searchToExploreState(state))}`}
					navigate={navigate}
				/>
			) : (
				<>
					<MovieGrid movies={data!.items} allScores={data!.items.map((i) => i.score)} />
					{canLoadMore && (
						<div className="flex justify-center">
							<button
								type="button"
								onClick={() => patch({ limit: nextLimit })}
								className="rounded-[var(--radius)] border border-border bg-background px-6 py-2.5 text-sm font-medium text-foreground transition-colors hover:bg-surface"
							>
								더 보기
							</button>
						</div>
					)}
				</>
			)}
		</div>
	);
}
