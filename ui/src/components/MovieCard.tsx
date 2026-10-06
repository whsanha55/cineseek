import { Link } from "react-router-dom";
import { Star } from "lucide-react";
import type { MovieCard as MovieCardData, SearchItem } from "../api/types";
import { PosterImage } from "./PosterImage";
import { RelevanceMeter } from "./RelevanceMeter";

interface MovieCardProps {
	movie: MovieCardData | SearchItem;
	/** 검색 결과일 때만 전달 — 관련도 등급 표시 (현재 집합 전체 점수 필요) */
	allScores?: number[];
}

function isSearchItem(m: MovieCardData | SearchItem): m is SearchItem {
	return "score" in m;
}

/** 포스터 카드 — 2:3 포스터 + 제목(2줄 말줄임) + 연도·평점 + 장르 최대 2개 */
export function MovieCard({ movie, allScores }: MovieCardProps) {
	const genres = movie.genres.slice(0, 2);
	return (
		<Link
			to={`/movies/${movie.movieId}`}
			className="group block focus-visible:outline-offset-4"
			aria-label={`${movie.title}${movie.releaseYear ? ` (${movie.releaseYear})` : ""} 상세 보기`}
		>
			<div className="relative aspect-[2/3] overflow-hidden rounded-[var(--radius)] bg-surface">
				<PosterImage
					title={movie.title}
					posterPath={movie.posterPath}
					className="transition-transform duration-150 group-hover:scale-[1.03]"
				/>
			</div>
			<div className="mt-2 space-y-1">
				<h3 className="line-clamp-2 text-sm font-medium leading-snug text-foreground">{movie.title}</h3>
				<div className="tnum flex items-center gap-2 text-xs text-muted-foreground">
					{movie.releaseYear !== null && <span>{movie.releaseYear}</span>}
					{movie.rating !== null && (
						<span className="inline-flex items-center gap-0.5">
							<Star aria-hidden className="size-3 fill-[var(--rating)] text-[var(--rating)]" />
							{movie.rating.toFixed(1)}
						</span>
					)}
				</div>
				{genres.length > 0 && (
					<p className="line-clamp-1 text-xs text-muted-foreground">
						{/* ponytail-audit #1: nameKo 제거 */}
						{genres.map((g) => g.name).join(" · ")}
					</p>
				)}
				{isSearchItem(movie) && allScores && <RelevanceMeter score={movie.score} allScores={allScores} />}
			</div>
		</Link>
	);
}
