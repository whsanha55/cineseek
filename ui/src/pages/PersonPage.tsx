import { useCallback, useState } from "react";
import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { ExternalLink, Loader2 } from "lucide-react";
import { useFilmography, type FilmographySort } from "../api/hooks";
import { MovieCard } from "../components/MovieCard";
import { MovieGridSkeleton } from "../components/MovieGrid";
import { ErrorState } from "../components/StateViews";
import { NotFound } from "./NotFound";
import { Select } from "../components/ui/select";
import { Skeleton } from "../components/ui/skeleton";
import { profileUrl } from "../lib/tmdb";
import { cn } from "../lib/cn";
import { formatLifespan, todayYmd } from "../lib/person";
import type { FilmographyPerson } from "../api/types";

const SORT_OPTIONS: { value: FilmographySort; label: string }[] = [
	{ value: "release", label: "최신순" },
	{ value: "rating", label: "평점순" },
];

/** URL 인물 파라미터 — "500-tom-cruise"·"500" 모두 앞의 숫자가 id다. slug는 읽기용 장식 */
function parsePersonId(param: string | undefined): number | null {
	const match = param?.match(/^(\d+)(?:-|$)/);
	return match ? Number(match[1]) : null;
}

/** 배우 페이지 — 프로필 헤더 + 출연작 그리드 (T7). 정렬·페이지는 URL이 원천 */
export function PersonPage() {
	const { id } = useParams();
	const personId = parsePersonId(id);
	const [searchParams, setSearchParams] = useSearchParams();
	const sortRaw = searchParams.get("sort");
	const sort: FilmographySort = sortRaw === "rating" ? "rating" : "release";
	const page = Math.max(0, Number(searchParams.get("page")) || 0);

	const { data, isPending, isError, refetch } = useFilmography({
		personId,
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

	if (personId === null) return <NotFound />;
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
	// 영어 이름 slug를 알면 /people/500-tom-cruise 형태로 주소를 맞춘다 (정렬·페이지 쿼리는 유지)
	const canonical = person.slug ? `${personId}-${person.slug}` : null;

	return (
		<div className="space-y-8 py-8">
			{canonical && canonical !== id && (
				<Navigate replace to={{ pathname: `/people/${canonical}`, search: searchParams.toString() }} />
			)}
			<header className="flex items-center gap-5">
				<div className="aspect-[2/3] w-24 shrink-0 overflow-hidden rounded-[var(--radius)] bg-surface sm:w-32">
					<ProfileImage name={person.name} profilePath={person.profilePath} />
				</div>
				<div className="space-y-1.5">
					<h1 className="text-2xl font-bold text-foreground">{person.name}</h1>
					<p className="tnum text-sm text-muted-foreground">출연작 {person.totalWorks.toLocaleString()}편</p>
					<PersonFacts person={person} />
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

			<PersonBio person={person} />

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

/** 생몰일·출생지·외부 링크 — 값이 있는 항목만. 출생지는 한국어 번역이 없으면 TMDB 원문(영어) */
function PersonFacts({ person }: { person: FilmographyPerson }) {
	const lifespan = formatLifespan(person.birthday, person.deathday, todayYmd());
	const placeOfBirth = person.placeOfBirthKo ?? person.placeOfBirthEn;
	const links = [
		person.imdbId && { label: "IMDb", href: `https://www.imdb.com/name/${person.imdbId}` },
		person.instagramId && { label: "Instagram", href: `https://www.instagram.com/${person.instagramId}` },
		person.homepage && { label: "홈페이지", href: person.homepage },
	].filter((link): link is { label: string; href: string } => Boolean(link));

	return (
		<>
			{lifespan && <p className="tnum text-sm text-muted-foreground">{lifespan}</p>}
			{placeOfBirth && <p className="text-sm text-muted-foreground">출생지 {placeOfBirth}</p>}
			{links.length > 0 && (
				<ul className="flex flex-wrap gap-x-3 gap-y-1 text-sm">
					{links.map((link) => (
						<li key={link.label}>
							<a
								href={link.href}
								target="_blank"
								rel="noopener noreferrer"
								className="inline-flex items-center gap-1 text-muted-foreground underline-offset-2 hover:text-foreground hover:underline"
							>
								{link.label}
								<ExternalLink aria-hidden className="size-3" />
							</a>
						</li>
					))}
				</ul>
			)}
		</>
	);
}

/** 이 길이를 넘는 소개는 접어 두고 '더 보기'로 펼친다 */
const BIO_COLLAPSE_LENGTH = 200;

/** 소개 — 한국어 우선, 없으면 영어 원문. 기계 번역이면 표시한다 */
function PersonBio({ person }: { person: FilmographyPerson }) {
	const [expanded, setExpanded] = useState(false);
	const text = person.biographyKo ?? person.biographyEn;
	if (!text) return null;
	const lang = person.biographyKo ? "ko" : "en";
	const collapsible = text.length > BIO_COLLAPSE_LENGTH;

	return (
		<section aria-labelledby="person-bio" className="space-y-2">
			<div className="flex items-baseline gap-2">
				<h2 id="person-bio" className="text-lg font-bold text-foreground">
					소개
				</h2>
				{person.biographyKo && person.biographyKoTranslated && (
					<span className="text-xs text-muted-foreground">영어 소개를 자동 번역했습니다</span>
				)}
			</div>
			<p
				id="person-bio-text"
				lang={lang}
				className={cn(
					"max-w-3xl whitespace-pre-line text-sm leading-relaxed text-foreground",
					collapsible && !expanded && "line-clamp-4",
				)}
			>
				{text}
			</p>
			{collapsible && (
				<button
					type="button"
					aria-expanded={expanded}
					aria-controls="person-bio-text"
					onClick={() => setExpanded((v) => !v)}
					className="text-sm font-medium text-muted-foreground hover:text-foreground"
				>
					{expanded ? "접기" : "더 보기"}
				</button>
			)}
		</section>
	);
}
