import { useCallback } from "react";
import { useSearchParams } from "react-router-dom";
import { useExplore, useGenres, usePerson } from "../api/hooks";
import { HiddenGemToggle } from "../components/HiddenGemToggle";
import { MovieGrid, MovieGridSkeleton } from "../components/MovieGrid";
import { EmptyState, ErrorState } from "../components/StateViews";
import { Select } from "../components/ui/select";
import {
	isHiddenGem,
	parseExploreState,
	serializeExploreState,
	type ExploreSort,
	type ExploreState,
} from "../lib/urlState";

const SORT_OPTIONS: { value: ExploreSort; label: string }[] = [
	{ value: "rating", label: "평점 높은 순" },
	{ value: "release", label: "최근 개봉 순" },
	{ value: "vote_count", label: "투표 많은 순" },
];

/** 탐색 — 검색어 없이 조건으로 둘러보기 (F5). 카드·상태 컴포넌트는 검색과 공유 */
export function Explore() {
	const [searchParams, setSearchParams] = useSearchParams();
	const state = parseExploreState(searchParams);
	const { data, isPending, isError, refetch } = useExplore(state);
	const { data: genres } = useGenres();
	// 감독·배우 직접 진입(/explore?directorId=) 칩 라벨
	const director = usePerson(state.directorId, "director");
	const cast = usePerson(state.castId, "cast");

	const patch = useCallback(
		(p: Partial<ExploreState>) => {
			const next = { ...state, ...p };
			// 장르·정렬 변경 시 페이지는 처음으로
			setSearchParams(serializeExploreState({ ...next, page: p.page ?? 0 }), { replace: true });
		},
		[state, setSearchParams],
	);

	const personLabel = director.data ? `감독 ${director.data.name}` : cast.data ? `출연 ${cast.data!.name}` : null;

	return (
		<div className="space-y-6 py-8">
			<div className="flex flex-wrap items-center justify-between gap-3">
				<div className="-mx-4 flex gap-2 overflow-x-auto px-4 pb-1 sm:mx-0 sm:flex-wrap sm:px-0">
					<button
						type="button"
						aria-pressed={state.genreId === null}
						onClick={() => patch({ genreId: null })}
						className={
							state.genreId === null
								? "h-8 shrink-0 rounded-[var(--radius-chip)] bg-primary px-3 text-xs font-medium text-primary-foreground"
								: "h-8 shrink-0 rounded-[var(--radius-chip)] bg-surface px-3 text-xs font-medium text-foreground hover:bg-border"
						}
					>
						전체
					</button>
					{(genres?.items ?? []).map((g) => (
						<button
							key={g.id}
							type="button"
							aria-pressed={state.genreId === g.id}
							onClick={() => patch({ genreId: g.id })}
							className={
								state.genreId === g.id
									? "h-8 shrink-0 rounded-[var(--radius-chip)] bg-primary px-3 text-xs font-medium text-primary-foreground"
									: "h-8 shrink-0 rounded-[var(--radius-chip)] bg-surface px-3 text-xs font-medium text-foreground hover:bg-border"
							}
						>
							{g.nameKo ?? g.name}
						</button>
					))}
				</div>
				<div className="flex shrink-0 items-center gap-2">
					<HiddenGemToggle active={isHiddenGem(state)} onChange={patch} />
					<Select
						aria-label="정렬"
						className="w-32"
						value={state.sort}
						onChange={(e) => patch({ sort: e.target.value as ExploreSort })}
					>
						{SORT_OPTIONS.map((o) => (
							<option key={o.value} value={o.value}>
								{o.label}
							</option>
						))}
					</Select>
				</div>
			</div>

			{personLabel && (
				<p className="text-sm text-muted-foreground">
					{personLabel}
					<button
						type="button"
						className="ml-2 underline hover:text-foreground"
						onClick={() => patch({ directorId: null, castId: null })}
					>
						해제
					</button>
				</p>
			)}

			{isHiddenGem(state) && (
				<p className="text-sm text-muted-foreground">
					숨은 명작 — 평점 7.5+ · 투표 50~500
					<button
						type="button"
						className="ml-2 underline hover:text-foreground"
						onClick={() => patch({ ratingMin: null, voteCountMin: null, voteCountMax: null })}
					>
						해제
					</button>
				</p>
			)}

			{isError ? (
				<ErrorState onRetry={() => refetch()} />
			) : isPending ? (
				<MovieGridSkeleton />
			) : data!.items.length === 0 ? (
				<EmptyState onResetAll={() => patch({ genreId: null })} />
			) : (
				<>
					<MovieGrid movies={data!.items} />
					{data!.page.hasNext && (
						<div className="flex justify-center">
							<button
								type="button"
								onClick={() => patch({ page: state.page + 1 })}
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
