import { afterEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { PersonPage } from "./PersonPage";
import type { FilmographyResponse } from "../api/types";

const RESPONSE: FilmographyResponse = {
	person: {
		personId: 3895,
		name: "크리스찬 베일",
		profilePath: null,
		slug: null,
		filmoState: "COMPLETE",
		filmoCheckedAt: "2026-10-01T00:00:00Z",
		totalWorks: 47,
		birthday: null,
		deathday: null,
		placeOfBirthKo: null,
		placeOfBirthEn: null,
		biographyKo: null,
		biographyKoTranslated: false,
		biographyEn: null,
		imdbId: null,
		instagramId: null,
		homepage: null,
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
	const fetchMock = vi.fn<(input: RequestInfo | URL) => Promise<Response>>(
		async () => ({ ok, json: async () => body }) as unknown as Response,
	);
	vi.stubGlobal("fetch", fetchMock);
	return fetchMock;
}

/** 현재 주소를 화면에 노출 — 주소 정규화(replace)를 사용자 관점에서 확인한다 */
function LocationProbe() {
	const location = useLocation();
	return <output aria-label="현재 주소">{location.pathname + location.search}</output>;
}

function renderPage(path = "/people/3895") {
	const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
	return render(
		<QueryClientProvider client={client}>
			<MemoryRouter initialEntries={[path]}>
				<Routes>
					<Route
						path="/people/:id"
						element={
							<>
								<PersonPage />
								<LocationProbe />
							</>
						}
					/>
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

	it("slug가 있으면 정렬 쿼리를 유지한 채 /people/{id}-{slug} 주소로 바꾼다", async () => {
		stubFetch({ ...RESPONSE, person: { ...RESPONSE.person, slug: "christian-bale" } });
		renderPage("/people/3895?sort=rating");

		await waitFor(() => {
			expect(screen.getByLabelText("현재 주소")).toHaveTextContent("/people/3895-christian-bale?sort=rating");
		});
	});

	it("slug가 붙은 주소는 앞의 숫자를 id로 조회하고, 이름이 바뀌었으면 새 slug로 맞춘다", async () => {
		const fetchMock = stubFetch({ ...RESPONSE, person: { ...RESPONSE.person, slug: "christian-bale" } });
		renderPage("/people/3895-old-name");

		await waitFor(() => {
			expect(screen.getByLabelText("현재 주소")).toHaveTextContent("/people/3895-christian-bale");
		});
		expect(String(fetchMock.mock.calls[0][0])).toContain("/cineseek/people/3895/filmography");
	});

	it("slug가 없으면 숫자 주소를 그대로 둔다", async () => {
		stubFetch(RESPONSE);
		renderPage();

		await screen.findByRole("heading", { name: "크리스찬 베일" });
		expect(screen.getByLabelText("현재 주소")).toHaveTextContent("/people/3895");
	});

	it("주소에 숫자 id가 없으면 404를 보여주고 조회하지 않는다", () => {
		const fetchMock = stubFetch(RESPONSE);
		renderPage("/people/tom-cruise");

		expect(screen.getByText("찾으시는 페이지가 없습니다.")).toBeInTheDocument();
		expect(fetchMock).not.toHaveBeenCalled();
	});

	it("생몰일·출생지·외부 링크를 표시한다", async () => {
		vi.useFakeTimers({ toFake: ["Date"] });
		vi.setSystemTime(new Date(2026, 9, 23));
		stubFetch({
			...RESPONSE,
			person: {
				...RESPONSE.person,
				birthday: "1974-01-30",
				placeOfBirthKo: "영국 웨일스 하버퍼드웨스트",
				placeOfBirthEn: "Haverfordwest, Wales, UK",
				imdbId: "nm0000288",
			},
		});
		renderPage();

		expect(await screen.findByText("1974년 1월 30일 (52세)")).toBeInTheDocument();
		expect(screen.getByText("출생지 영국 웨일스 하버퍼드웨스트")).toBeInTheDocument();
		expect(screen.getByRole("link", { name: "IMDb" })).toHaveAttribute("href", "https://www.imdb.com/name/nm0000288");
		expect(screen.queryByRole("link", { name: "Instagram" })).not.toBeInTheDocument();
		vi.useRealTimers();
	});

	it("소개 — 기계 번역이면 표시하고, 긴 소개는 접었다가 더 보기로 펼친다", async () => {
		stubFetch({
			...RESPONSE,
			person: {
				...RESPONSE.person,
				biographyKo: "영국의 배우다. ".repeat(40),
				biographyKoTranslated: true,
				biographyEn: "An English actor.",
			},
		});
		renderPage();

		expect(await screen.findByText("영어 소개를 자동 번역했습니다")).toBeInTheDocument();
		const toggle = screen.getByRole("button", { name: "더 보기" });
		expect(toggle).toHaveAttribute("aria-expanded", "false");
		await userEvent.setup().click(toggle);
		expect(screen.getByRole("button", { name: "접기" })).toHaveAttribute("aria-expanded", "true");
	});

	it("소개 — 한국어가 없으면 영어 원문을 보여주고 번역 표시는 하지 않는다", async () => {
		stubFetch({ ...RESPONSE, person: { ...RESPONSE.person, biographyEn: "An English actor." } });
		renderPage();

		const bio = await screen.findByText("An English actor.");
		expect(bio).toHaveAttribute("lang", "en");
		expect(screen.queryByText("영어 소개를 자동 번역했습니다")).not.toBeInTheDocument();
		expect(screen.queryByRole("button", { name: "더 보기" })).not.toBeInTheDocument();
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
