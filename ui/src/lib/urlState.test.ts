import { describe, expect, it } from "vitest";
import {
	HIDDEN_GEM_PRESET,
	isHiddenGem,
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
			runtimeMin: null,
			runtimeMax: null,
			voteCountMin: null,
			voteCountMax: null,
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

	it("러닝타임은 0~600으로 클램프한다", () => {
		const s = parseSearchState(new URLSearchParams("runtimeMin=90&runtimeMax=601"));
		expect(s.runtimeMin).toBe(90);
		expect(s.runtimeMax).toBe(600);
		expect(parseSearchState(new URLSearchParams("runtimeMin=-5")).runtimeMin).toBe(0);
	});

	it("voteCount는 양수만 통과한다 — 음수·0은 버린다", () => {
		const s = parseSearchState(new URLSearchParams("voteCountMin=50&voteCountMax=0"));
		expect(s.voteCountMin).toBe(50);
		expect(s.voteCountMax).toBeNull();
		expect(parseSearchState(new URLSearchParams("voteCountMin=-1")).voteCountMin).toBeNull();
	});

	it("ratingMin은 소수(7.5)를 허용한다 — 숨은 명작 프리셋", () => {
		expect(parseSearchState(new URLSearchParams("ratingMin=7.5")).ratingMin).toBe(7.5);
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
			runtimeMin: null,
			runtimeMax: null,
			voteCountMin: null,
			voteCountMax: null,
			limit: 20,
		});
		expect(out.toString()).toBe("q=%EC%9A%B0%EC%A3%BC");
	});

	it("다중 genreId는 반복 파라미터로 직렬화한다", () => {
		const out = serializeSearchState(parseSearchState(new URLSearchParams("genreId=28&genreId=53")));
		expect(out.getAll("genreId")).toEqual(["28", "53"]);
	});

	it("runtime·voteCount는 null이면 생략, 값이 있으면 싣는다", () => {
		const withValues = parseSearchState(
			new URLSearchParams("runtimeMin=60&voteCountMin=50&voteCountMax=500"),
		);
		expect(serializeSearchState(withValues).toString()).toBe(
			"runtimeMin=60&voteCountMin=50&voteCountMax=500",
		);
		const empty = parseSearchState(new URLSearchParams(""));
		expect(serializeSearchState(empty).toString()).toBe("");
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

	it("ratingMin·voteCount를 파싱하고 null이면 생략한다", () => {
		const state = parseExploreState(
			new URLSearchParams("ratingMin=7.5&voteCountMin=50&voteCountMax=500"),
		);
		expect(state).toMatchObject({ ratingMin: 7.5, voteCountMin: 50, voteCountMax: 500 });
		expect(serializeExploreState(state).toString()).toBe(
			"ratingMin=7.5&voteCountMin=50&voteCountMax=500",
		);
		expect(parseExploreState(new URLSearchParams("ratingMin=99&voteCountMin=0")).ratingMin).toBeNull();
	});
});

describe("isHiddenGem", () => {
	it("프리셋 3값과 정확히 일치할 때만 true", () => {
		expect(isHiddenGem(HIDDEN_GEM_PRESET)).toBe(true);
		expect(
			isHiddenGem({ ratingMin: 7.5, voteCountMin: 50, voteCountMax: 500 }),
		).toBe(true);
	});

	it("일부만 겹치면 false — ratingMin만 있을 때·값이 다를 때", () => {
		expect(isHiddenGem({ ratingMin: 7.5, voteCountMin: null, voteCountMax: null })).toBe(false);
		expect(isHiddenGem({ ratingMin: 7, voteCountMin: 50, voteCountMax: 500 })).toBe(false);
		expect(isHiddenGem({ ratingMin: null, voteCountMin: null, voteCountMax: null })).toBe(false);
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
			ratingMin: 7,
			voteCountMin: null,
			voteCountMax: null,
		});
	});

	it("ratingMin·voteCount를 이관한다 — 검색에서 숨은 명작을 켠 채 검색어를 지우면 /explore에서 유지", () => {
		const s = parseSearchState(
			new URLSearchParams("ratingMin=7.5&voteCountMin=50&voteCountMax=500"),
		);
		expect(searchToExploreState(s)).toMatchObject(HIDDEN_GEM_PRESET);
	});
});
