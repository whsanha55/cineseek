import { afterEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { PersonPage } from "./PersonPage";
import type { FilmographyResponse } from "../api/types";

const RESPONSE: FilmographyResponse = {
	person: {
		personId: 3895,
		name: "크리스찬 베일",
		profilePath: null,
		filmoState: "COMPLETE",
		filmoCheckedAt: "2026-10-01T00:00:00Z",
		totalWorks: 47,
	},
	items: [
		{
			movieId: 155,
			title: "다크 나이트",
			originalTitle: "The Dark Knight",
			releaseYear: 2008,
			rating: 8.5,
			voteCount: 30000,
			posterPath: null,
			genres: [],
			character: "브루스 웨인",
		},
		{
			movieId: 272,
			title: "아메리칸 사이코",
			originalTitle: "American Psycho",
			releaseYear: 2000,
			rating: 7.6,
			voteCount: 12000,
			posterPath: null,
			genres: [],
			character: null,
		},
	],
	page: { limit: 20, offset: 0, hasNext: false, total: null },
	collecting: false,
};

/** client.ts가 쓰는 ok·json만 갖춘 최소 fetch 흉내 (msw 없이) */
function stubFetch(body: unknown, ok = true) {
	const fetchMock = vi.fn(
		async (_input: RequestInfo | URL) => ({ ok, json: async () => body }) as unknown as Response,
	);
	vi.stubGlobal("fetch", fetchMock);
	return fetchMock;
}

function renderPage(path = "/people/3895") {
	const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
	return render(
		<QueryClientProvider client={client}>
			<MemoryRouter initialEntries={[path]}>
				<Routes>
					<Route path="/people/:id" element={<PersonPage />} />
				</Routes>
			</MemoryRouter>
		</QueryClientProvider>,
	);
}

afterEach(() => {
	vi.unstubAllGlobals();
});

describe("PersonPage", () => {
	it("이름·총 작품 수·카드+배역 캡션을 렌더링한다", async () => {
		const fetchMock = stubFetch(RESPONSE);
		renderPage();

		expect(await screen.findByRole("heading", { name: "크리스찬 베일" })).toBeInTheDocument();
		expect(screen.getByText("출연작 47편")).toBeInTheDocument();
		expect(screen.getByRole("link", { name: "다크 나이트 (2008) 상세 보기" })).toBeInTheDocument();
		// 배역 캡션 — character 있는 항목만
		expect(screen.getByText("브루스 웨인 역")).toBeInTheDocument();
		expect(screen.queryByText("null 역")).not.toBeInTheDocument();

		// 요청 파라미터 — 기본 최신순, 배우 페이지는 excludeMovieId 없음
		const url = String(fetchMock.mock.calls[0][0]);
		expect(url).toContain("/cineseek/people/3895/filmography");
		expect(url).toContain("sort=release");
		expect(url).not.toContain("excludeMovieId");
	});

	it("collecting이면 수집 중 배지를 표시한다", async () => {
		stubFetch({ ...RESPONSE, collecting: true });
		renderPage();

		const badge = await screen.findByText("출연작 수집 중");
		expect(badge).toBeInTheDocument();
		expect(badge).toHaveAttribute("role", "status");
	});

	it("정렬 토글 — 평점순 선택 시 sort=rating로 재요청한다", async () => {
		const fetchMock = stubFetch(RESPONSE);
		renderPage();
		await screen.findByRole("heading", { name: "크리스찬 베일" });

		const user = userEvent.setup();
		await user.selectOptions(screen.getByRole("combobox", { name: "정렬" }), "rating");

		await waitFor(() => {
			expect(String(fetchMock.mock.calls.at(-1)?.[0])).toContain("sort=rating");
		});
		// 정렬 변경 시 페이지 리셋 — 재요청은 page=0으로 간다
		expect(String(fetchMock.mock.calls.at(-1)?.[0])).toContain("page=0");
	});

	it("에러 상태 — 안내 문구와 재시도로 재요청한다", async () => {
		const fetchMock = stubFetch({}, false);
		renderPage();

		expect(await screen.findByText("문제가 발생했습니다")).toBeInTheDocument();
		const user = userEvent.setup();
		await user.click(screen.getByRole("button", { name: "재시도" }));
		expect(fetchMock.mock.calls.length).toBeGreaterThanOrEqual(2);
	});
});
