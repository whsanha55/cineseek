/** TMDB 이미지 CDN URL 조립 — posterPath가 없으면 null (플레이스홀더로) */
export function posterUrl(
	posterPath: string | null | undefined,
	size: "w342" | "w500" = "w342",
): string | null {
	if (!posterPath) return null;
	return `https://image.tmdb.org/t/p/${size}${posterPath}`;
}
