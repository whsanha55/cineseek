import { Link } from "react-router-dom";
import { useGenres } from "../api/hooks";
import { SearchBar } from "../components/SearchBar";

/** 홈 — 큰 검색창 + 예시 쿼리 칩 + 장르 바로가기 (F1) */
export function Home() {
	const { data: genres } = useGenres();

	return (
		<div className="flex flex-col items-center gap-10 py-16 sm:py-24">
			<div className="text-center">
				<h1 className="text-2xl font-bold text-foreground sm:text-4xl">cineseek</h1>
				<p className="mt-2 text-sm text-muted-foreground sm:text-base">
					분위기로 영화를 찾는 시맨틱 검색
				</p>
			</div>

			<div className="w-full max-w-2xl">
				<SearchBar size="hero" />
				<p className="mt-2 text-center text-xs text-muted-foreground">
					<kbd className="rounded-[var(--radius)] border border-border px-1">/</kbd> 키로 검색창으로 이동
				</p>
			</div>

			<div className="flex max-w-2xl flex-wrap justify-center gap-2">
				{EXAMPLE_QUERIES.map((q) => (
					<Link
						key={q}
						to={`/search?q=${encodeURIComponent(q)}`}
						className="rounded-[var(--radius-chip)] border border-border bg-background px-3 py-1.5 text-xs text-muted-foreground transition-colors hover:bg-surface hover:text-foreground"
					>
						{q}
					</Link>
				))}
			</div>

			{(genres?.items.length ?? 0) > 0 && (
				<section className="w-full max-w-2xl" aria-label="장르 바로가기">
					<h2 className="mb-3 text-sm font-medium text-muted-foreground">장르로 둘러보기</h2>
					<div className="flex flex-wrap gap-2">
						{genres!.items.map((g) => (
							<Link
								key={g.id}
								to={`/explore?genreId=${g.id}`}
								className="rounded-[var(--radius-chip)] bg-surface px-3 py-1.5 text-xs font-medium text-foreground transition-colors hover:bg-border"
							>
								{g.nameKo ?? g.name}
							</Link>
						))}
					</div>
				</section>
			)}
		</div>
	);
}

/** EvalRunner 고정 쿼리에서 고른 예시 — 프론트 상수 (F1) */
const EXAMPLE_QUERIES = [
	"우주에서 혼자 살아남는 잔잔한 영화",
	"연쇄살인범을 쫓는 형사",
	"가족을 지키려는 아버지의 사투",
	"사랑과 배신의 로맨스",
	"시간을 거슬러 가는 모험",
] as const;
