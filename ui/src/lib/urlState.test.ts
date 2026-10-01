import { describe, expect, it } from "vitest";
import {
	parseExploreState,
	parseSearchState,
	searchToExploreState,
	serializeExploreState,
	serializeSearchState,
} from "./urlState";

describe("parseSearchState", () => {
	it("기본값 — 빈 쿼리", () => {
		expect(parseSearchState(new URLSearchParams())).toEqual({
			q: "",
			genreIds: [],
			yearMin: null,
			yearMax: null,
			ratingMin: null,
			directorId: null,
			castId: null,
			limit: 20,
		});
	});

	it("반복 genreId 파라미터를 다중으로 파싱하고 중복을 제거한다", () => {
		const sp = new URLSearchParams("q=우주&genreId=28&genreId=53&genreId=28");
		expect(parseSearchState(sp).genreIds).toEqual([28, 53]);
	});

	it("잘못된 숫자는 무시된다", () => {
		const sp = new URLSearchParams("q=x&yearMin=abc&ratingMin=99&directorId=-1&limit=77");
		const s = parseSearchState(sp);
		expect(s.yearMin).toBeNull();
		expect(s.ratingMin).toBeNull(); // 0~10 밖
		expect(s.directorId).toBeNull();
		expect(s.limit).toBe(20); // 허용 목록 밖
	});

	it("limit은 허용 목록(20/40/50)만 통과한다", () => {
		expect(parseSearchState(new URLSearchParams("limit=40")).limit).toBe(40);
		expect(parseSearchState(new URLSearchParams("limit=30")).limit).toBe(20);
	});

	it("yearMin이 yearMax보다 커도 그대로 둔다 — 빈 결과와 칩 해제로 대응", () => {
		const s = parseSearchState(new URLSearchParams("yearMin=2020&yearMax=2000"));
		expect(s.yearMin).toBe(2020);
		expect(s.yearMax).toBe(2000);
	});
});

describe("serializeSearchState", () => {
	it("기본값은 생략해 URL이 짧다", () => {
		const out = serializeSearchState({
			q: "우주",
			genreIds: [],
			yearMin: null,
			yearMax: null,
			ratingMin: null,
			directorId: null,
			castId: null,
			limit: 20,
		});
		expect(out.toString()).toBe("q=%EC%9A%B0%EC%A3%BC");
	});

	it("다중 genreId는 반복 파라미터로 직렬화한다", () => {
		const out = serializeSearchState(parseSearchState(new URLSearchParams("genreId=28&genreId=53")));
		expect(out.getAll("genreId")).toEqual(["28", "53"]);
	});
});

describe("explore 상태", () => {
	it("parse + serialize 왕복", () => {
		const sp = new URLSearchParams("genreId=28&sort=vote_count&page=2&directorId=525");
		const state = parseExploreState(sp);
		expect(state).toMatchObject({ genreId: 28, sort: "vote_count", page: 2, directorId: 525 });
		expect(serializeExploreState(state).toString()).toBe(sp.toString());
	});

	it("잘못된 sort는 기본값(rating)으로", () => {
		expect(parseExploreState(new URLSearchParams("sort=popular")).sort).toBe("rating");
	});
});

describe("searchToExploreState", () => {
	it("검색어 없는 /search → /explore 이관 시 첫 장르와 인물 조건만 유지한다", () => {
		const s = parseSearchState(new URLSearchParams("genreId=28&genreId=53&directorId=525&ratingMin=7"));
		expect(searchToExploreState(s)).toEqual({
			genreId: 28,
			sort: "rating",
			page: 0,
			limit: 20,
			directorId: 525,
			castId: null,
		});
	});
});
