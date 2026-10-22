import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { WatchProviders } from "./WatchProviders";
import type { WatchProviders as WatchProvidersData } from "../api/types";

const PROVIDERS: WatchProvidersData = {
	link: "https://www.themoviedb.org/movie/155/watch?locale=KR",
	flatrate: [{ providerId: 8, name: "Netflix", logoPath: "/n.jpg" }],
	rent: [],
	buy: [{ providerId: 3, name: "Google Play Movies", logoPath: null }],
};

const EMPTY: WatchProvidersData = { link: null, flatrate: [], rent: [], buy: [] };

/** client.ts가 쓰는 ok·status·json만 갖춘 최소 fetch 흉내 (msw 없이) */
function stubFetch(body: unknown, ok = true) {
	const fetchMock = vi.fn<(input: RequestInfo | URL) => Promise<Response>>(
		async () => ({ ok, status: ok ? 200 : 503, json: async () => body }) as unknown as Response,
	);
	vi.stubGlobal("fetch", fetchMock);
	return fetchMock;
}

function renderSection(movieId = 1) {
	const client = new QueryClient();
	return render(
		<QueryClientProvider client={client}>
			<WatchProviders movieId={movieId} />
		</QueryClientProvider>,
	);
}

afterEach(() => {
	vi.unstubAllGlobals();
});

describe("WatchProviders", () => {
	it("구분별 시청처와 JustWatch 출처를 표시한다", async () => {
		const fetchMock = stubFetch(PROVIDERS);
		renderSection(1);

		expect(await screen.findByRole("img", { name: "Netflix" })).toHaveAttribute(
			"src",
			"https://image.tmdb.org/t/p/w92/n.jpg",
		);
		expect(screen.getByText("정액제")).toBeInTheDocument();
		expect(screen.getByText("구매")).toBeInTheDocument();
		expect(screen.queryByText("대여")).not.toBeInTheDocument();
		expect(screen.getByText("Google Play Movies")).toBeInTheDocument();
		expect(screen.getByText(/JustWatch/)).toBeInTheDocument();
		expect(screen.getByRole("link", { name: "자세히" })).toHaveAttribute("href", PROVIDERS.link);
		expect(String(fetchMock.mock.calls[0]?.[0])).toBe("/cineseek/movies/1/watch-providers");
	});

	it("TMDB 장애(503)면 재시도 없이 영역을 숨긴다", async () => {
		const fetchMock = stubFetch({ code: "EXTERNAL_API_ERROR" }, false);
		const { container } = renderSection();

		await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
		expect(container).toBeEmptyDOMElement();
		expect(screen.queryByRole("region", { name: "시청처" })).not.toBeInTheDocument();
	});

	it("시청처가 없으면 영역을 숨긴다", async () => {
		const fetchMock = stubFetch(EMPTY);
		const { container } = renderSection();

		await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
		expect(container).toBeEmptyDOMElement();
	});
});
