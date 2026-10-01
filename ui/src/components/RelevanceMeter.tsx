import { gradeOf, RELEVANCE_LABEL } from "../lib/relevance";
import { cn } from "../lib/cn";

interface RelevanceMeterProps {
	score: number;
	allScores: number[];
}

/** RRF 점수 → 현재 결과 집합 기준 상대 2단계 (높음/보통). 색만이 아닌 텍스트로 전달 (§6.4) */
export function RelevanceMeter({ score, allScores }: RelevanceMeterProps) {
	const grade = gradeOf(score, allScores);
	return (
		<span className="inline-flex items-center gap-1.5 text-xs text-muted-foreground">
			<span
				aria-hidden
				className={cn("size-2 rounded-[var(--radius-chip)]", grade === "high" ? "bg-primary" : "bg-border")}
			/>
			{RELEVANCE_LABEL[grade]}
		</span>
	);
}
