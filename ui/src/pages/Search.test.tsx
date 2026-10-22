import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Layout } from "../components/Layout";
import { Search } from "./Search";
import type { SearchResponse } from "../api/types";

function searchResponse(limit: number): SearchResponse {
	return {
		items: Array.from({ length: limit }, (_, i) => ({
			movieId: i + 1,
			title: `영화 ${i + 1}`,
			originalTitle: null,
			releaseYear: 2000,
			rating: 7,
			voteCount: 100,
			posterPath: null,
			genres: [],
			score: 0.5,
			matchedTerms: [],
		})),
		page: { limit, offset: 0, hasNext: true, total: null },
	};
}

/** 검색은 limit 쿼리만큼 결과를, 장르는 빈 목록을 돌려주는 fetch 흉내 (msw 없이) */
function stubSearchFetch() {
	const fetchMock = vi.fn<(input: RequestInfo | URL) => Promise<Response>>(async (input) => {
		const url = new URL(String(input), "http://localhost");
		const body = url.pathname.endsWith("/genres")
			? { items: [] }
			: searchResponse(Number(url.searchParams.get("limit") ?? 20));
		return { ok: true, json: async () => body } as unknown as Response;
	});
	vi.stubGlobal("fetch", fetchMock);
}

/** jsdom은 스크롤을 구현하지 않아 scrollY·scrollTo를 흉내 낸다 */
function stubScroll() {
	let y = 0;
	Object.defineProperty(window, "scrollY", { configurable: true, get: () => y });
	const scrollTo = vi.fn((_x: number, top: number) => {
		y = top;
	});
	vi.stubGlobal("scrollTo", scrollTo);
	return {
		scrollTo,
		setY: (top: number) => {
			y = top;
		},
	};
}

function renderApp(initialPath: string) {
	const router = createMemoryRouter(
		[
			{
				path: "/",
				element: <Layout />,
				children: [
					{ path: "search", element: <Search /> },
					{ path: "movies/:id", element: <p>상세 화면</p> },
				],
			},
		],
		{ initialEntries: [initialPath] },
	);
	const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
	render(
		<QueryClientProvider client={client}>
			<RouterProvider router={router} />
		</QueryClientProvider>,
	);
	return router;
}

/**
 * 데이터 라우터는 내비게이션마다 Request를 만드는데, jsdom의 AbortSignal을 Node의 Request가 거부한다.
 * 이 테스트에는 loader가 없어 signal을 빼도 동작이 같다
 */
class SignalFreeRequest extends Request {
	constructor(input: RequestInfo | URL, init?: RequestInit) {
		super(input, { ...init, signal: undefined });
	}
}

beforeEach(() => {
	sessionStorage.clear();
	vi.stubGlobal("Request", SignalFreeRequest);
});

afterEach(() => {
	vi.unstubAllGlobals();
});

describe("Search 스크롤 복원", () => {
	it("'더 보기'는 표시 개수를 URL에 싣고 스크롤을 맨 위로 되돌리지 않는다", async () => {
		stubSearchFetch();
		const scroll = stubScroll();
		const router = renderApp("/search?q=우주");
		await screen.findByRole("link", { name: "영화 20 (2000) 상세 보기" });
		scroll.setY(1500);
		scroll.scrollTo.mockClear();

		await userEvent.click(screen.getByRole("button", { name: "더 보기" }));

		expect(await screen.findByRole("link", { name: "영화 40 (2000) 상세 보기" })).toBeInTheDocument();
		expect(router.state.location.search).toContain("limit=40");
		expect(scroll.scrollTo).not.toHaveBeenCalledWith(0, 0);
	});

	it("결과 → 상세 → 뒤로가기 시 필터·표시 개수·스크롤 위치를 복원한다", async () => {
		stubSearchFetch();
		const scroll = stubScroll();
		const router = renderApp("/search?q=우주&genreId=878&limit=40");
		await screen.findByRole("link", { name: "영화 40 (2000) 상세 보기" });
		scroll.setY(1800);

		await userEvent.click(screen.getByRole("link", { name: "영화 33 (2000) 상세 보기" }));
		expect(await screen.findByText("상세 화면")).toBeInTheDocument();
		expect(scroll.scrollTo).toHaveBeenLastCalledWith(0, 0);

		await act(() => router.navigate(-1));

		// 캐시된 결과로 바로 그려지고, 떠날 때의 위치로 돌아온다
		expect(screen.getByRole("link", { name: "영화 40 (2000) 상세 보기" })).toBeInTheDocument();
		expect(router.state.location.search).toBe("?q=우주&genreId=878&limit=40");
		await waitFor(() => expect(scroll.scrollTo).toHaveBeenLastCalledWith(0, 1800));
	});
});
