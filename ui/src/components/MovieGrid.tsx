import type { MovieCard as MovieCardData, SearchItem } from "../api/types";
import { MovieCard } from "./MovieCard";
import { Skeleton } from "./ui/skeleton";

interface MovieGridProps {
	movies: (MovieCardData | SearchItem)[];
	allScores?: number[];
}

/** 반응형 카드 그리드 — 2(<640) / 3(≥640) / 4(≥1024) / 5(≥1280) */
export function MovieGrid({ movies, allScores }: MovieGridProps) {
	return (
		<ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 sm:gap-6 lg:grid-cols-4 xl:grid-cols-5">
			{movies.map((m) => (
				<li key={m.movieId}>
					<MovieCard movie={m} allScores={allScores} />
				</li>
			))}
		</ul>
	);
}

/** 스켈레톤 그리드 — 카드와 같은 열 구조 */
export function MovieGridSkeleton({ count = 20 }: { count?: number }) {
	return (
		<ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 sm:gap-6 lg:grid-cols-4 xl:grid-cols-5" aria-hidden>
			{Array.from({ length: count }, (_, i) => (
				<li key={i}>
					<Skeleton className="aspect-[2/3] w-full" />
					<Skeleton className="mt-2 h-4 w-4/5" />
					<Skeleton className="mt-1 h-3 w-1/2" />
				</li>
			))}
		</ul>
	);
}
