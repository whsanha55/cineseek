/** fetch 래퍼 — 같은 출처(/cineseek) 전용. Vite dev는 프록시, compose는 nginx가 전달 */

export class ApiError extends Error {
	constructor(
		readonly status: number,
		message: string,
	) {
		super(message);
		this.name = "ApiError";
	}
}

type QueryParams = Record<string, string | number | (string | number)[] | undefined | null>;

function buildUrl(path: string, params?: QueryParams): string {
	const url = new URL(path, window.location.origin);
	if (params) {
		for (const [key, value] of Object.entries(params)) {
			if (value === undefined || value === null || value === "") continue;
			if (Array.isArray(value)) {
				for (const v of value) url.searchParams.append(key, String(v));
			} else {
				url.searchParams.set(key, String(value));
			}
		}
	}
	return url.pathname + url.search;
}

export async function apiGet<T>(path: string, params?: QueryParams): Promise<T> {
	const response = await fetch(buildUrl(path, params));
	if (!response.ok) {
		throw new ApiError(response.status, `API 요청 실패: ${response.status} ${path}`);
	}
	return (await response.json()) as T;
}
