import { afterEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { CastOtherWorks } from "./CastOtherWorks";
import type { CastMember, FilmographyResponse } from "../api/types";

const CAST: CastMember[] = [
	{ personId: 3895, name: "크리스찬 베일", character: "브루스 웨인" },
	{ personId: 51329, name: "히스 레저", character: "조커" },
	{ personId: 6389, name: "게리 올드만", character: "고든" },
];

function filmographyOf(personId: number, name: string): FilmographyResponse {
	return {
		person: {
			personId,
			name,
			profilePath: null,
			filmoState: "COMPLETE",
			filmoCheckedAt: null,
			totalWorks: 3,
		},
		items: [
			{
				movieId: 1124 + personId,
				title: `${name}의 다른 작품`,
				originalTitle: null,
				releaseYear: 2006,
				rating: 8.1,
				voteCount: 5000,
				posterPath: null,
				genres: [],
				character: null,
			},
		],
		page: { limit: 7, offset: 0, hasNext: false, total: null },
		collecting: false,
	};
}

const EMPTY: FilmographyResponse = {
	person: { personId: 0, name: "", profilePath: null, filmoState: "NONE", filmoCheckedAt: null, totalWorks: 0 },
	items: [],
	page: { limit: 7, offset: 0, hasNext: false, total: null },
	collecting: false,
};

/** personId별 응답을 내려주는 fetch 흉내 (msw 없이) */
function stubFilmographyFetch(responses: Record<number, FilmographyResponse>) {
	const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
		const url = String(input);
		const match = url.match(/\/cineseek\/people\/(\d+)\/filmography/);
		const personId = match ? Number(match[1]) : -1;
		return { ok: true, json: async () => responses[personId] ?? EMPTY } as unknown as Response;
	});
	vi.stubGlobal("fetch", fetchMock);
	return fetchMock;
}

function renderSection(cast: CastMember[], movieId = 155) {
	const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
	return render(
		<QueryClientProvider client={client}>
			<MemoryRouter>
				<CastOtherWorks movieId={movieId} cast={cast} />
			</MemoryRouter>
		</QueryClientProvider>,
	);
}

afterEach(() => {
	vi.unstubAllGlobals();
});

describe("CastOtherWorks", () => {
	it("주연 3명 각각 행을 렌더링하고 전체 보기 링크가 /people/{id}로 향한다", async () => {
		const responses = Object.fromEntries(CAST.map((c) => [c.personId, filmographyOf(c.personId, c.name)]));
		stubFilmographyFetch(responses);
		renderSection(CAST);

		expect(await screen.findByRole("heading", { name: "크리스찬 베일" })).toBeInTheDocument();
		expect(screen.getByRole("heading", { name: "히스 레저" })).toBeInTheDocument();
		expect(screen.getByRole("heading", { name: "게리 올드만" })).toBeInTheDocument();

		const hrefs = screen
			.getAllByRole("link", { name: "전체 보기" })
			.map((a) => a.getAttribute("href"));
		expect(hrefs).toEqual(["/people/3895", "/people/51329", "/people/6389"]);
	});

	it("현재 영화 제외 — 모든 요청에 excludeMovieId를 전달한다", async () => {
		const responses = Object.fromEntries(CAST.map((c) => [c.personId, filmographyOf(c.personId, c.name)]));
		const fetchMock = stubFilmographyFetch(responses);
		renderSection(CAST, 155);

		await screen.findAllByRole("link", { name: "전체 보기" });
		expect(fetchMock.mock.calls.length).toBe(3);
		for (const call of fetchMock.mock.calls) {
			expect(String(call[0])).toContain("excludeMovieId=155");
			expect(String(call[0])).toContain("limit=7");
		}
	});

	it("카드 클릭은 영화 상세로 — MovieCard 링크 동작을 유지한다", async () => {
		const responses = Object.fromEntries(CAST.map((c) => [c.personId, filmographyOf(c.personId, c.name)]));
		stubFilmographyFetch(responses);
		renderSection(CAST);

		const link = await screen.findByRole("link", { name: "크리스찬 베일의 다른 작품 (2006) 상세 보기" });
		expect(link).toHaveAttribute("href", "/movies/5019");
	});

	it("출연진이 없으면 섹션 자체를 렌더링하지 않는다", () => {
		stubFilmographyFetch({});
		renderSection([]);
		expect(screen.queryByRole("region", { name: "주요 배우의 다른 작품" })).not.toBeInTheDocument();
	});
});
