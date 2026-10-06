/** TMDB 이미지 CDN URL 조립 — posterPath가 없으면 null (플레이스홀더로) */
export function posterUrl(
	posterPath: string | null | undefined,
	size: "w342" | "w500" = "w342",
): string | null {
	if (!posterPath) return null;
	return `https://image.tmdb.org/t/p/${size}${posterPath}`;
}

/** 배우 프로필 CDN URL — 프로필 규격 크기는 w185/h632만 제공된다. 없으면 null */
export function profileUrl(
	profilePath: string | null | undefined,
	size: "w185" | "h632" = "w185",
): string | null {
	if (!profilePath) return null;
	return `https://image.tmdb.org/t/p/${size}${profilePath}`;
}
