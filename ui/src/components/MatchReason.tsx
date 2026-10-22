interface MatchReasonProps {
	terms: string[];
}

/** 검색 근거 — 쿼리와 겹치는 단어(sparse). 겹치는 단어가 없으면 의미(dense)로만 찾은 결과다 */
export function MatchReason({ terms }: MatchReasonProps) {
	if (terms.length === 0) {
		return <p className="text-xs text-muted-foreground">의미 유사</p>;
	}
	const quoted = terms.map((t) => `‘${t}’`).join(", ");
	return (
		<p className="line-clamp-1 text-xs text-muted-foreground" title={`${quoted} 일치`}>
			<span className="text-foreground">{quoted}</span> 일치
		</p>
	);
}
