import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { TasteRecommendations } from "./TasteRecommendations";
import type { SearchResponse } from "../api/types";
import { stubLocalStorage } from "../test/memory-storage";

const RESPONSE: SearchResponse = {
	items: [
		{
			movieId: 27205,
			title: "인셉션",
			originalTitle: "Inception",
			releaseYear: 2010,
			rating: 8.4,
			voteCount: 35000,
			posterPath: null,
			genres: [],
			score: 0.91,
		},
	],
	page: { limit: 10, offset: 0, hasNext: false, total: null },
};

function stubFetch(body: unknown) {
	const fetchMock = vi.fn<(input: RequestInfo | URL) => Promise<Response>>(
		async () => ({ ok: true, json: async () => body }) as Response,
	);
	vi.stubGlobal("fetch", fetchMock);
	return fetchMock;
}

function saveMarks(marks: { liked: number[]; watched: number[]; disliked: number[] }, blocked = false) {
	stubLocalStorage({ "cineseek.marks": JSON.stringify(marks) }, blocked);
}

function renderSection() {
	const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
	return render(
		<QueryClientProvider client={client}>
			<MemoryRouter>
				<TasteRecommendations />
			</MemoryRouter>
		</QueryClientProvider>,
	);
}

afterEach(() => {
	vi.unstubAllGlobals();
});

describe("TasteRecommendations", () => {
	it("찜이 기준보다 적으면 안내만 보이고 추천을 조회하지 않는다", () => {
		const fetchMock = stubFetch(RESPONSE);
		saveMarks({ liked: [1, 2], watched: [], disliked: [] });
		renderSection();

		expect(screen.getByRole("region", { name: "내 취향 추천" })).toHaveTextContent("지금 2개");
		expect(fetchMock).not.toHaveBeenCalled();
	});

	it("찜이 3개 이상이면 찜·관심없음·봤어요 id로 추천을 조회해 카드로 보여준다", async () => {
		const fetchMock = stubFetch(RESPONSE);
		saveMarks({ liked: [1, 2, 3], watched: [5], disliked: [4] });
		renderSection();

		expect(await screen.findByRole("link", { name: "인셉션 (2010) 상세 보기" })).toBeInTheDocument();
		const url = new URL(String(fetchMock.mock.calls[0]?.[0]), "http://localhost");
		expect(url.pathname).toBe("/cineseek/recommendations");
		expect(url.searchParams.getAll("likedId")).toEqual(["1", "2", "3"]);
		expect(url.searchParams.getAll("dislikedId")).toEqual(["4"]);
		expect(url.searchParams.getAll("watchedId")).toEqual(["5"]);
	});

	it("찜이 하나도 없으면 영역을 그리지 않는다", () => {
		stubFetch(RESPONSE);
		stubLocalStorage();
		renderSection();

		expect(screen.queryByRole("region", { name: "내 취향 추천" })).not.toBeInTheDocument();
	});

	it("localStorage를 쓸 수 없으면 영역을 그리지 않는다", () => {
		stubFetch(RESPONSE);
		saveMarks({ liked: [1, 2, 3], watched: [], disliked: [] }, true);
		renderSection();

		expect(screen.queryByRole("region", { name: "내 취향 추천" })).not.toBeInTheDocument();
	});
});
