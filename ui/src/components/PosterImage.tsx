import { useState } from "react";
import { posterUrl } from "../lib/tmdb";
import { cn } from "../lib/cn";

interface PosterImageProps {
	title: string;
	posterPath: string | null | undefined;
	size?: "w342" | "w500";
	className?: string;
}

/**
 * TMDB 포스터 — lazy load. 없음·로드 실패(CDN 에러·404)는 제목 이니셜 플레이스홀더로.
 * onError 1회 처리 후 플래그를 세워 무한 재시도를 막는다
 */
export function PosterImage({ title, posterPath, size = "w342", className }: PosterImageProps) {
	const [failed, setFailed] = useState(false);
	const url = failed ? null : posterUrl(posterPath, size);

	if (!url) {
		return (
			<div
				aria-label={`${title} 포스터 없음`}
				className={cn(
					"flex h-full w-full items-center justify-center bg-surface text-2xl font-bold text-muted-foreground",
					className,
				)}
			>
				{title.slice(0, 1)}
			</div>
		);
	}

	return (
		<img
			src={url}
			alt={title}
			loading="lazy"
			decoding="async"
			onError={() => setFailed(true)}
			className={cn("h-full w-full object-cover", className)}
		/>
	);
}
