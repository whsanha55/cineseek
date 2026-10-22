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
	const { data, isPending, isPlaceholderData, isError, refetch } = useSearch(state);

	// 필터 한 항목 변경 — replace (§3). 같은 화면 안의 변경이라 스크롤을 맨 위로 되돌리지 않는다 ("더 보기" 후 제자리)
	const patch = useCallback(
		(p: Partial<SearchState>) => {
			const next = { ...state, ...p };
			// 검색어가 비면 /explore로 조건 이관 (§3 — 검색어 없는 /search는 탐색으로)
			if (!next.q) {
				navigate(`/explore?${serializeExploreState(searchToExploreState(next))}`, { replace: true });
				return;
			}
			setSearchParams(serializeSearchState(next), { replace: true, preventScrollReset: true });
		},
		[state, navigate, setSearchParams],
	);

	const clearFilters = () => patch({ ...DEFAULT_SEARCH_STATE, q: state.q, limit: state.limit });

	const nextLimit = SEARCH_LIMITS[Math.min(SEARCH_LIMITS.findIndex((l) => l === state.limit) + 1, SEARCH_LIMITS.length - 1)];
	// 마지막 단계(50)를 불러오는 동안에도 버튼을 남긴다 — 포커스된 버튼이 빠지면 스크롤이 튄다 (#72)
	const canLoadMore =
		data?.page.hasNext === true && (isPlaceholderData || state.limit < SEARCH_LIMITS[SEARCH_LIMITS.length - 1]);

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
				onReset={clearFilters}
			/>

			{isError ? (
				<ErrorState onRetry={() => refetch()} />
			) : isPending ? (
				<MovieGridSkeleton count={state.limit} />
			) : data!.items.length === 0 ? (
				<EmptyState
					onResetFilters={clearFilters}
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
								disabled={isPlaceholderData}
								className="rounded-[var(--radius)] border border-border bg-background px-6 py-2.5 text-sm font-medium text-foreground transition-colors hover:bg-surface disabled:opacity-50"
							>
								{isPlaceholderData ? "불러오는 중…" : "더 보기"}
							</button>
						</div>
					)}
				</>
			)}
		</div>
	);
}
