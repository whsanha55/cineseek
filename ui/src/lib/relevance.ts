/**
 * 관련도 상대 등급.
 * RRF 점수는 검색마다 분포가 달라 절대값으로 읽히지 않게,
 * 현재 결과 집합 기준 상대 2단계만 계산한다. "낮음"은 표시하지 않는다
 */
export type RelevanceGrade = "high" | "normal";

export const RELEVANCE_LABEL: Record<RelevanceGrade, string> = {
	high: "관련도 높음",
	normal: "관련도 보통",
};

/** 상위권(최고 점수의 75% 이상)은 high, 나머지 normal. 집합이 비면 normal */
export function gradeOf(score: number, allScores: number[]): RelevanceGrade {
	if (allScores.length === 0) return "normal";
	const max = Math.max(...allScores);
	if (max <= 0) return "normal";
	return score >= max * 0.75 ? "high" : "normal";
}
