const STORAGE_KEY = "cineseek:recent-searches";
export const MAX_RECENT_SEARCHES = 10;

/** 최근 검색어 목록 — 저장소를 쓸 수 없으면(사파리 프라이빗 등) null, 기능을 숨긴다 */
export function readRecentSearches(): string[] | null {
	try {
		const raw = localStorage.getItem(STORAGE_KEY);
		if (raw === null) return [];
		const parsed: unknown = JSON.parse(raw);
		if (!Array.isArray(parsed)) return [];
		return parsed.filter((q): q is string => typeof q === "string").slice(0, MAX_RECENT_SEARCHES);
	} catch (e) {
		// 깨진 JSON은 빈 목록으로 다시 시작, 저장소 접근 실패는 숨김
		return e instanceof SyntaxError ? [] : null;
	}
}

/** 맨 앞에 추가 — 같은 검색어는 앞으로 옮기고 최대 개수를 넘으면 오래된 것부터 버린다 */
export function addRecentSearch(q: string): string[] | null {
	const list = readRecentSearches();
	if (list === null) return null;
	return write([q, ...list.filter((x) => x !== q)].slice(0, MAX_RECENT_SEARCHES));
}

export function removeRecentSearch(q: string): string[] | null {
	const list = readRecentSearches();
	if (list === null) return null;
	return write(list.filter((x) => x !== q));
}

function write(list: string[]): string[] | null {
	try {
		localStorage.setItem(STORAGE_KEY, JSON.stringify(list));
		return list;
	} catch {
		return null;
	}
}
