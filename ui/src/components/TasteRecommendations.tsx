import { useRecommendations } from "../api/hooks";
import { RECOMMEND_MIN_LIKED, useMarks } from "../lib/marks";
import { MovieGrid, MovieGridSkeleton } from "./MovieGrid";
import { ErrorState } from "./StateViews";

const RECOMMEND_LIMIT = 10;

/** 홈 "내 취향 추천" — 찜이 기준 수 이상일 때만 조회한다. 찜이 하나도 없거나 저장소를 쓸 수 없으면 숨긴다 */
export function TasteRecommendations() {
	const { available, marks } = useMarks();
	const likedCount = marks.liked.length;
	const ready = available && likedCount >= RECOMMEND_MIN_LIKED;
	const { data, isPending, isError, refetch } = useRecommendations(marks, RECOMMEND_LIMIT, ready);

	if (!available || likedCount === 0) return null;

	return (
		<section className="w-full" aria-label="내 취향 추천">
			<h2 className="mb-4 text-lg font-bold text-foreground">내 취향 추천</h2>
			{!ready ? (
				<p className="text-sm text-muted-foreground">
					찜한 영화가 {RECOMMEND_MIN_LIKED}개 이상이면 취향에 맞는 영화를 추천해 드려요. (지금 {likedCount}개)
				</p>
			) : isError ? (
				<ErrorState onRetry={() => refetch()} />
			) : isPending ? (
				<MovieGridSkeleton count={RECOMMEND_LIMIT} />
			) : data.items.length === 0 ? (
				<p className="text-sm text-muted-foreground">추천할 영화를 아직 찾지 못했습니다.</p>
			) : (
				<MovieGrid movies={data.items} />
			)}
		</section>
	);
}
