import { Link } from "react-router-dom";
import { useFilmography } from "../api/hooks";
import type { CastMember } from "../api/types";
import { MovieCard } from "./MovieCard";
import { ErrorState } from "./StateViews";
import { Skeleton } from "./ui/skeleton";

/** 주요 배우의 다른 작품 — 주연 상위 3명 각각 가로 스크롤 행 (T7). 현재 영화는 제외 */
export function CastOtherWorks({ movieId, cast }: { movieId: number; cast: CastMember[] }) {
	if (cast.length === 0) return null;

	return (
		<section aria-label="주요 배우의 다른 작품" className="space-y-8">
			<h2 className="text-lg font-bold text-foreground">주요 배우의 다른 작품</h2>
			{cast.map((c) => (
				<CastWorkRow key={c.personId} movieId={movieId} person={c} />
			))}
		</section>
	);
}

function CastWorkRow({ movieId, person }: { movieId: number; person: CastMember }) {
	const { data, isPending, isError, refetch } = useFilmography({
		personId: person.personId,
		sort: "rating",
		page: 0,
		limit: 7,
		excludeMovieId: movieId,
	});

	return (
		<div>
			<div className="mb-3 flex items-baseline justify-between gap-3">
				<h3 className="text-sm font-medium text-foreground">{person.name}</h3>
				<Link
					to={`/people/${person.personId}`}
					className="text-xs text-muted-foreground underline-offset-4 hover:text-primary hover:underline"
				>
					전체 보기
				</Link>
			</div>
			{isError ? (
				<ErrorState onRetry={() => refetch()} />
			) : isPending ? (
				<div className="flex gap-3 overflow-hidden" aria-hidden>
					{Array.from({ length: 7 }, (_, i) => (
						<Skeleton key={i} className="aspect-[2/3] w-28 shrink-0 sm:w-36" />
					))}
				</div>
			) : !data || data.items.length === 0 ? (
				<p className="text-sm text-muted-foreground">다른 작품 정보가 아직 없습니다.</p>
			) : (
				<ul className="-mx-4 flex snap-x gap-3 overflow-x-auto px-4 pb-1 sm:mx-0 sm:px-0">
					{data.items.map((m) => (
						<li key={m.movieId} className="w-28 shrink-0 snap-start sm:w-36">
							<MovieCard movie={m} />
						</li>
					))}
				</ul>
			)}
		</div>
	);
}
