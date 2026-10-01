/**
 * 개발용 디자인 시스템 카탈로그 (/_ds) — 토큰·컴포넌트·상태(로딩/빈/에러)를 한 장에서 본다.
 * import.meta.env.DEV 조건부 라우트로만 등록 — 운영 번들에서 제외 (§6.3)
 */
import { useState } from "react";
import { Star } from "lucide-react";
import { SearchBar } from "../components/SearchBar";
import { MovieCard } from "../components/MovieCard";
import { MovieGridSkeleton } from "../components/MovieGrid";
import { RelevanceMeter } from "../components/RelevanceMeter";
import { EmptyState, ErrorState } from "../components/StateViews";
import { Button } from "../components/ui/button";
import { Input } from "../components/ui/input";
import { Badge } from "../components/ui/badge";
import { Select } from "../components/ui/select";
import { Skeleton } from "../components/ui/skeleton";

const mockCard = {
	movieId: 1,
	title: "마션",
	originalTitle: "The Martian",
	releaseYear: 2015,
	rating: 8.0,
	voteCount: 21000,
	posterPath: "/94PWwAT8MYL5i5SVplngTryLATS.jpg",
	genres: [
		{ id: 878, name: "SF", nameKo: null },
		{ id: 12, name: "모험", nameKo: null },
	],
};

const mockSearchItem = { ...mockCard, score: 0.75 };

function Section({ title, children }: { title: string; children: React.ReactNode }) {
	return (
		<section className="space-y-3 border-t border-border pt-8">
			<h2 className="text-lg font-bold text-foreground">{title}</h2>
			{children}
		</section>
	);
}

function Component() {
	const [input, setInput] = useState("");

	return (
		<div className="space-y-12 pb-20">
			<header>
				<h1 className="text-2xl font-bold text-foreground">디자인 시스템 — /_ds</h1>
				<p className="mt-1 text-sm text-muted-foreground">
					토큰은 styles/tokens.css가 원천. 이 페이지는 개발에서만 존재한다
				</p>
			</header>

			<Section title="토큰 — 색">
				<div className="flex flex-wrap gap-2">
					{[
						["background", "bg-background"],
						["surface", "bg-surface"],
						["primary", "bg-primary"],
						["rating", "bg-[var(--rating)]"],
						["destructive", "bg-[var(--destructive)]"],
						["border", "bg-border"],
					].map(([name, cls]) => (
						<div key={name} className="flex flex-col items-center gap-1">
							<div className={`size-14 rounded-[var(--radius)] border border-border ${cls}`} />
							<span className="text-xs text-muted-foreground">{name}</span>
						</div>
					))}
				</div>
				<div className="space-y-1 text-sm">
					<p className="text-foreground">foreground — 본문 텍스트</p>
					<p className="text-muted-foreground">muted-foreground — 보조 텍스트</p>
					<p className="tnum text-foreground">tabular-nums 0123456789 — 2026 · 8.0</p>
				</div>
			</Section>

			<Section title="타이포그래피">
				<div className="space-y-1">
					<p className="text-4xl font-bold text-foreground">text-4xl 상세 제목</p>
					<p className="text-2xl font-bold text-foreground">text-2xl 섹션 제목</p>
					<p className="text-lg font-medium text-foreground">text-lg 카드 제목</p>
					<p className="text-base text-foreground">text-base 본문 · 줄거리</p>
					<p className="text-sm text-muted-foreground">text-sm 카드 메타</p>
					<p className="text-xs text-muted-foreground">text-xs 칩 · 보조</p>
				</div>
			</Section>

			<Section title="Button · Input · Badge · Select">
				<div className="flex flex-wrap items-center gap-2">
					<Button>기본</Button>
					<Button variant="outline">아웃라인</Button>
					<Button variant="ghost">고스트</Button>
					<Button variant="destructive">삭제</Button>
					<Button size="sm">작게</Button>
					<Button disabled>비활성</Button>
				</div>
				<Input placeholder="입력" value={input} onChange={(e) => setInput(e.target.value)} className="max-w-xs" />
				<div className="flex flex-wrap gap-2">
					<Badge>장르 칩</Badge>
					<Badge variant="outline">아웃라인 배지</Badge>
				</div>
				<Select className="w-40" defaultValue="a">
					<option value="a">옵션 A</option>
					<option value="b">옵션 B</option>
				</Select>
			</Section>

			<Section title="SearchBar — hero / compact">
				<div className="max-w-xl space-y-3">
					<SearchBar size="hero" />
					<SearchBar size="compact" />
				</div>
			</Section>

			<Section title="MovieCard · PosterImage">
				<div className="grid max-w-xl grid-cols-2 gap-3 sm:grid-cols-3 sm:gap-6">
					<MovieCard movie={mockCard} />
					<MovieCard movie={mockSearchItem} allScores={[0.75, 0.6, 0.5]} />
					<MovieCard movie={{ ...mockCard, title: "포스터 없는 영화", posterPath: null }} />
				</div>
			</Section>

			<Section title="RelevanceMeter">
				<div className="flex items-center gap-6">
					<RelevanceMeter score={0.75} allScores={[0.75, 0.6]} />
					<RelevanceMeter score={0.5} allScores={[0.75, 0.6]} />
				</div>
			</Section>

			<Section title="MovieGrid 스켈레톤 · Skeleton">
				<MovieGridSkeleton count={5} />
				<Skeleton className="h-10 w-64" />
			</Section>

			<Section title="EmptyState · ErrorState">
				<div className="grid gap-4 border border-border p-4 md:grid-cols-2">
					<EmptyState onResetFilters={() => {}} onResetAll={() => {}} exploreTo="/explore" navigate={() => {}} />
					<ErrorState onRetry={() => {}} />
				</div>
			</Section>

			<Section title="기타 — 별 아이콘">
				<p className="inline-flex items-center gap-1 text-sm">
					<Star aria-hidden className="size-4 fill-[var(--rating)] text-[var(--rating)]" />
					<span className="tnum">8.0</span>
				</p>
			</Section>
		</div>
	);
}

// lazy 라우트용 named export — react-router의 lazy는 { Component } 형태를 기대한다
export { Component };
