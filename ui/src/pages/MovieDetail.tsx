import { Link, useParams } from "react-router-dom";
import { Star } from "lucide-react";
import { useMovie } from "../api/hooks";
import { PosterImage } from "../components/PosterImage";
import { SimilarRow } from "../components/SimilarRow";
import { ErrorState } from "../components/StateViews";
import { Skeleton } from "../components/ui/skeleton";

/** 영화 상세 — 메타·줄거리·출연진 + 유사 영화 (F3). 인물 클릭은 /explore로 통일 */
export function MovieDetail() {
	const { id } = useParams();
	const { data: movie, isPending, isError, refetch } = useMovie(id);

	if (isError) return <ErrorState onRetry={() => refetch()} />;
	if (isPending) {
		return (
			<div className="grid gap-6 py-8 sm:grid-cols-[220px_1fr]">
				<Skeleton className="aspect-[2/3] w-36 sm:w-full" />
				<div className="space-y-3">
					<Skeleton className="h-9 w-2/3" />
					<Skeleton className="h-4 w-1/3" />
					<Skeleton className="h-24 w-full" />
				</div>
			</div>
		);
	}

	const m = movie!;

	return (
		<article className="space-y-12 py-8">
			<div className="grid gap-6 sm:grid-cols-[220px_1fr]">
				<div className="aspect-[2/3] w-36 overflow-hidden rounded-[var(--radius)] bg-surface sm:w-full">
					<PosterImage title={m.title} posterPath={m.posterPath} size="w500" />
				</div>

				<div className="space-y-4">
					<div>
						<h1 className="text-2xl font-bold text-foreground sm:text-4xl">{m.title}</h1>
						{m.originalTitle && <p className="mt-1 text-sm text-muted-foreground">{m.originalTitle}</p>}
					</div>

					<div className="tnum flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-muted-foreground">
						{m.releaseYear !== null && <span>{m.releaseYear}</span>}
						{m.runtime !== null && <span>{m.runtime}분</span>}
						{m.rating !== null && (
							<span className="inline-flex items-center gap-1">
								<Star aria-hidden className="size-4 fill-[var(--rating)] text-[var(--rating)]" />
								{m.rating.toFixed(1)}
								{m.voteCount !== null && ` (${m.voteCount.toLocaleString()}표)`}
							</span>
						)}
					</div>

					{m.genres.length > 0 && (
						<ul className="flex flex-wrap gap-1.5">
							{m.genres.map((g) => (
								<li key={g.id}>
									<Link
										to={`/explore?genreId=${g.id}`}
										className="inline-block rounded-[var(--radius-chip)] bg-surface px-3 py-1 text-xs font-medium text-foreground hover:bg-border"
									>
										{g.name}
									</Link>
								</li>
							))}
						</ul>
					)}

					{m.overview && <p className="text-base leading-relaxed text-foreground">{m.overview}</p>}

					{(m.directors.length > 0 || m.cast.length > 0) && (
						<div className="space-y-4 pt-2">
							{m.directors.length > 0 && (
								<section aria-label="감독">
									<h2 className="mb-1.5 text-sm font-medium text-muted-foreground">감독</h2>
									<ul className="flex flex-wrap gap-x-3 gap-y-1">
										{m.directors.map((d) => (
											<li key={d.personId}>
												<Link
													to={`/explore?directorId=${d.personId}`}
													className="text-sm text-foreground underline-offset-4 hover:text-primary hover:underline"
												>
													{d.name}
												</Link>
											</li>
										))}
									</ul>
								</section>
							)}
							{m.cast.length > 0 && (
								<section aria-label="주요 출연진">
									<h2 className="mb-1.5 text-sm font-medium text-muted-foreground">주요 출연</h2>
									<ul className="flex flex-wrap gap-x-4 gap-y-1">
										{m.cast.map((c) => (
											<li key={c.personId} className="text-sm">
												<Link
													to={`/explore?castId=${c.personId}`}
													className="text-foreground underline-offset-4 hover:text-primary hover:underline"
												>
													{c.name}
												</Link>
												{c.character && <span className="text-muted-foreground"> ({c.character})</span>}
											</li>
										))}
									</ul>
								</section>
							)}
						</div>
					)}
				</div>
			</div>

			<section aria-label="이 영화랑 비슷한 영화">
				<h2 className="mb-4 text-lg font-bold text-foreground">이 영화랑 비슷한</h2>
				<SimilarRow movieId={Number(id)} />
			</section>
		</article>
	);
}
