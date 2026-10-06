import { useCallback, useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { Loader2 } from "lucide-react";
import { useFilmography, type FilmographySort } from "../api/hooks";
import { MovieCard } from "../components/MovieCard";
import { MovieGridSkeleton } from "../components/MovieGrid";
import { ErrorState } from "../components/StateViews";
import { Select } from "../components/ui/select";
import { Skeleton } from "../components/ui/skeleton";
import { profileUrl } from "../lib/tmdb";

const SORT_OPTIONS: { value: FilmographySort; label: string }[] = [
	{ value: "release", label: "최신순" },
	{ value: "rating", label: "평점순" },
];

/** 배우 페이지 — 프로필 헤더 + 출연작 그리드 (T7). 정렬·페이지는 URL이 원천 */
export function PersonPage() {
	const { id } = useParams();
	const [searchParams, setSearchParams] = useSearchParams();
	const sortRaw = searchParams.get("sort");
	const sort: FilmographySort = sortRaw === "rating" ? "rating" : "release";
	const page = Math.max(0, Number(searchParams.get("page")) || 0);

	const { data, isPending, isError, refetch } = useFilmography({
		personId: id !== undefined ? Number(id) : null,
		sort,
		page,
		limit: 20,
		excludeMovieId: null,
	});

	const patch = useCallback(
		(p: { sort?: FilmographySort; page?: number }) => {
			const nextSort = p.sort ?? sort;
			// 정렬 변경 시 페이지는 처음으로
			const nextPage = p.page ?? (p.sort !== undefined ? 0 : page);
			const out = new URLSearchParams();
			if (nextSort !== "release") out.set("sort", nextSort);
			if (nextPage > 0) out.set("page", String(nextPage));
			setSearchParams(out, { replace: true });
		},
		[sort, page, setSearchParams],
	);

	if (isError) return <ErrorState onRetry={() => refetch()} />;
	if (isPending) {
		return (
			<div className="space-y-8 py-8">
				<div className="flex items-center gap-5">
					<Skeleton className="aspect-[2/3] w-24 shrink-0" />
					<div className="space-y-3">
						<Skeleton className="h-8 w-48" />
						<Skeleton className="h-4 w-24" />
					</div>
				</div>
				<MovieGridSkeleton />
			</div>
		);
	}

	const person = data!.person;

	return (
		<div className="space-y-8 py-8">
			<header className="flex items-center gap-5">
				<div className="aspect-[2/3] w-24 shrink-0 overflow-hidden rounded-[var(--radius)] bg-surface sm:w-32">
					<ProfileImage name={person.name} profilePath={person.profilePath} />
				</div>
				<div className="space-y-1.5">
					<h1 className="text-2xl font-bold text-foreground">{person.name}</h1>
					<p className="tnum text-sm text-muted-foreground">출연작 {person.totalWorks.toLocaleString()}편</p>
					{data!.collecting && (
						<p
							role="status"
							className="inline-flex items-center gap-1.5 rounded-[var(--radius-chip)] bg-surface px-2.5 py-1 text-xs font-medium text-muted-foreground"
						>
							<Loader2 aria-hidden className="size-3.5 animate-spin" />
							출연작 수집 중
						</p>
					)}
				</div>
			</header>

			<div className="flex items-center justify-between gap-3">
				<h2 className="text-lg font-bold text-foreground">출연작</h2>
				<Select
					aria-label="정렬"
					className="w-32"
					value={sort}
					onChange={(e) => patch({ sort: e.target.value as FilmographySort })}
				>
					{SORT_OPTIONS.map((o) => (
						<option key={o.value} value={o.value}>
							{o.label}
						</option>
					))}
				</Select>
			</div>

			{data!.items.length === 0 ? (
				<p className="text-sm text-muted-foreground">출연작 정보가 아직 없습니다.</p>
			) : (
				<>
					<ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 sm:gap-6 lg:grid-cols-4 xl:grid-cols-5">
						{data!.items.map((item) => (
							<li key={item.movieId}>
								<MovieCard movie={item} />
								{item.character && (
									<p className="mt-0.5 line-clamp-1 text-xs text-muted-foreground">{item.character} 역</p>
								)}
							</li>
						))}
					</ul>
					{data!.page.hasNext && (
						<div className="flex justify-center">
							<button
								type="button"
								onClick={() => patch({ page: page + 1 })}
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

/** 프로필 이미지 — 없음·로드 실패(CDN 에러·404)는 이름 이니셜 플레이스홀더로 (PosterImage 방식) */
function ProfileImage({ name, profilePath }: { name: string; profilePath: string | null }) {
	const [failed, setFailed] = useState(false);
	const url = failed ? null : profileUrl(profilePath, "h632");

	if (!url) {
		return (
			<div
				aria-label={`${name} 프로필 없음`}
				className="flex h-full w-full items-center justify-center bg-surface text-2xl font-bold text-muted-foreground"
			>
				{name.slice(0, 1)}
			</div>
		);
	}

	return (
		<img
			src={url}
			alt={name}
			loading="lazy"
			decoding="async"
			onError={() => setFailed(true)}
			className="h-full w-full object-cover"
		/>
	);
}
