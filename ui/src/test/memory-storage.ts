import { vi } from "vitest";

/**
 * 테스트용 localStorage — Node 25+는 전역 localStorage를 비워 두어 jsdom 것을 가리므로 직접 넣는다.
 * blocked면 Safari 사생활 보호 모드처럼 쓰기가 예외를 던진다
 */
export function stubLocalStorage(initial: Record<string, string> = {}, blocked = false): void {
	const data = new Map(Object.entries(initial));
	const storage = {
		getItem: (key: string) => data.get(key) ?? null,
		setItem: (key: string, value: string) => {
			if (blocked) throw new DOMException("blocked", "SecurityError");
			data.set(key, value);
		},
		removeItem: (key: string) => {
			data.delete(key);
		},
	};
	vi.stubGlobal("localStorage", storage);
}
