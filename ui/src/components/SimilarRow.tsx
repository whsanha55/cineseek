import { Link } from "react-router-dom";
import { useSimilar } from "../api/hooks";
import { PosterImage } from "./PosterImage";
import { Skeleton } from "./ui/skeleton";
import { ErrorState } from "./StateViews";

/** 유사 영화 — 모바일 가로 스크롤 / 데스크톱 그리드 (F3) */
export function SimilarRow({ movieId }: { movieId: number }) {
	const { data, isPending, isError, refetch } = useSimilar(movieId, 10);

	if (isError) {
		return <ErrorState onRetry={() => refetch()} />;
	}
	if (isPending) {
		return (
			<div className="flex gap-3 overflow-hidden" aria-hidden>
				{Array.from({ length: 6 }, (_, i) => (
					<Skeleton key={i} className="aspect-[2/3] w-28 shrink-0 sm:w-36" />
				))}
			</div>
		);
	}
	if (!data || data.items.length === 0) {
		return <p className="text-sm text-muted-foreground">비슷한 영화 정보가 아직 없습니다.</p>;
	}

	return (
		<ul className="-mx-4 flex snap-x gap-3 overflow-x-auto px-4 pb-1 sm:mx-0 sm:grid sm:grid-cols-4 sm:overflow-visible sm:px-0 lg:grid-cols-5">
			{data.items.map((m) => (
				<li key={m.movieId} className="w-28 shrink-0 snap-start sm:w-auto">
					<Link to={`/movies/${m.movieId}`} className="group block">
						<div className="aspect-[2/3] overflow-hidden rounded-[var(--radius)] bg-surface">
							<PosterImage
								title={m.title}
								posterPath={m.posterPath}
								className="transition-transform duration-150 group-hover:scale-[1.03]"
							/>
						</div>
						<p className="tnum mt-1.5 line-clamp-1 text-xs font-medium text-foreground">{m.title}</p>
						<p className="tnum text-xs text-muted-foreground">{m.releaseYear ?? ""}</p>
					</Link>
				</li>
			))}
		</ul>
	);
}
