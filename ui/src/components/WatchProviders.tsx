import { useWatchProviders } from "../api/hooks";
import type { WatchProvider } from "../api/types";
import { logoUrl } from "../lib/tmdb";

const GROUPS = [
	["flatrate", "정액제"],
	["rent", "대여"],
	["buy", "구매"],
] as const;

function ProviderLogo({ provider }: { provider: WatchProvider }) {
	const src = logoUrl(provider.logoPath);
	if (!src) return <span className="text-xs text-foreground">{provider.name}</span>;
	return (
		<img
			src={src}
			alt={provider.name}
			title={provider.name}
			loading="lazy"
			className="size-9 rounded-[var(--radius)] bg-surface"
		/>
	);
}

/**
 * KR 시청처 — TMDB(JustWatch 제공) 데이터라 출처를 함께 표시한다 (TMDB 약관).
 * 로딩·실패·시청처 없음이면 영역 자체를 숨긴다 — 상세 화면의 나머지는 영향받지 않는다
 */
export function WatchProviders({ movieId }: { movieId: number }) {
	const { data } = useWatchProviders(movieId);
	const groups = data ? GROUPS.filter(([key]) => data[key].length > 0) : [];
	if (!data || groups.length === 0) return null;

	return (
		<section aria-label="시청처" className="space-y-2 pt-2">
			<h2 className="text-sm font-medium text-muted-foreground">시청처</h2>
			<dl className="space-y-2">
				{groups.map(([key, label]) => (
					<div key={key} className="flex items-center gap-3">
						<dt className="w-12 shrink-0 text-xs text-muted-foreground">{label}</dt>
						<dd>
							<ul className="flex flex-wrap items-center gap-2">
								{data[key].map((p) => (
									<li key={p.providerId}>
										<ProviderLogo provider={p} />
									</li>
								))}
							</ul>
						</dd>
					</div>
				))}
			</dl>
			<p className="text-xs text-muted-foreground">
				시청처 정보 제공: JustWatch
				{data.link && (
					<>
						{" · "}
						<a
							href={data.link}
							target="_blank"
							rel="noopener noreferrer"
							className="underline underline-offset-2 hover:text-foreground"
						>
							자세히
						</a>
					</>
				)}
			</p>
		</section>
	);
}
