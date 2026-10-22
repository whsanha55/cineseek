import { Eye, Heart, ThumbsDown, type LucideIcon } from "lucide-react";
import { useMarks, type MarkKind } from "../lib/marks";
import { cn } from "../lib/cn";
import { Button } from "./ui/button";

const MARKS: { kind: MarkKind; label: string; icon: LucideIcon }[] = [
	{ kind: "liked", label: "찜", icon: Heart },
	{ kind: "watched", label: "봤어요", icon: Eye },
	{ kind: "disliked", label: "관심없음", icon: ThumbsDown },
];

interface MarkButtonsProps {
	movieId: number;
	/** overlay: 카드 포스터 위 아이콘만 / labeled: 상세 화면 글자 버튼 */
	variant: "overlay" | "labeled";
	className?: string;
}

/** 찜·봤어요·관심없음 토글 — 저장소를 쓸 수 없는 브라우저에서는 그리지 않는다 */
export function MarkButtons({ movieId, variant, className }: MarkButtonsProps) {
	const { available, marks, toggle } = useMarks();
	if (!available) return null;

	return (
		<div role="group" aria-label="내 표시" className={cn("flex gap-1.5", className)}>
			{MARKS.map(({ kind, label, icon: Icon }) => {
				const pressed = marks[kind].includes(movieId);
				const iconClass = cn("size-4", pressed && kind === "liked" && "fill-current");
				if (variant === "labeled") {
					return (
						<Button
							key={kind}
							variant={pressed ? "default" : "outline"}
							size="sm"
							aria-pressed={pressed}
							onClick={() => toggle(kind, movieId)}
						>
							<Icon aria-hidden className={iconClass} />
							{label}
						</Button>
					);
				}
				return (
					<button
						key={kind}
						type="button"
						aria-label={label}
						title={label}
						aria-pressed={pressed}
						onClick={() => toggle(kind, movieId)}
						className={cn(
							"inline-flex size-8 items-center justify-center rounded-[var(--radius-full)] shadow-[var(--shadow-sm)] transition-colors",
							pressed
								? "bg-primary text-primary-foreground"
								: "bg-background/90 text-foreground hover:bg-background",
						)}
					>
						<Icon aria-hidden className={iconClass} />
					</button>
				);
			})}
		</div>
	);
}
