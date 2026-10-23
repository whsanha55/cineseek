/** "YYYY-MM-DD" 날짜 문자열 — 시간대 영향을 받지 않게 Date 대신 숫자로 다룬다 */
type Ymd = { y: number; m: number; d: number };

function parseYmd(value: string): Ymd {
	const [y, m, d] = value.split("-").map(Number);
	return { y, m, d };
}

/** [from]부터 [to]까지 만 나이 */
function fullYears(from: Ymd, to: Ymd): number {
	const beforeBirthday = to.m < from.m || (to.m === from.m && to.d < from.d);
	return to.y - from.y - (beforeBirthday ? 1 : 0);
}

function formatYmd({ y, m, d }: Ymd): string {
	return `${y}년 ${m}월 ${d}일`;
}

/**
 * 생몰일 표시 — 생존: "1962년 7월 3일 (64세)", 사망: "1933년 3월 14일 – 2020년 1월 1일 (향년 86세)".
 * 생년월일이 없으면 null. [today]는 "YYYY-MM-DD" (테스트에서 고정)
 */
export function formatLifespan(birthday: string | null, deathday: string | null, today: string): string | null {
	if (!birthday) return null;
	const birth = parseYmd(birthday);
	if (deathday) {
		const death = parseYmd(deathday);
		return `${formatYmd(birth)} – ${formatYmd(death)} (향년 ${fullYears(birth, death)}세)`;
	}
	return `${formatYmd(birth)} (${fullYears(birth, parseYmd(today))}세)`;
}

/** 오늘 날짜 "YYYY-MM-DD" (브라우저 현지 시각 기준) */
export function todayYmd(): string {
	const now = new Date();
	const pad = (n: number) => String(n).padStart(2, "0");
	return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}
