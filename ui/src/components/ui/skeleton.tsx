import { cn } from "../../lib/cn";

/** 로딩 스켈레톤 — shadcn Skeleton 축약판 */
export function Skeleton({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
	return <div className={cn("animate-pulse rounded-[var(--radius)] bg-surface", className)} {...props} />;
}
