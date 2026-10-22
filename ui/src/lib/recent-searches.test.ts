import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
	MAX_RECENT_SEARCHES,
	addRecentSearch,
	readRecentSearches,
	removeRecentSearch,
} from "./recent-searches";

beforeEach(() => localStorage.clear());
afterEach(() => vi.restoreAllMocks());

describe("최근 검색어", () => {
	it("처음에는 빈 목록", () => {
		expect(readRecentSearches()).toEqual([]);
	});

	it("최근 것이 맨 앞에 오고 다시 읽어도 유지된다", () => {
		addRecentSearch("우주");
		addRecentSearch("느와르");
		expect(readRecentSearches()).toEqual(["느와르", "우주"]);
	});

	it("같은 검색어는 중복 없이 맨 앞으로 옮긴다", () => {
		addRecentSearch("우주");
		addRecentSearch("느와르");
		expect(addRecentSearch("우주")).toEqual(["우주", "느와르"]);
	});

	it("최대 개수를 넘으면 가장 오래된 것부터 버린다", () => {
		for (let i = 1; i <= MAX_RECENT_SEARCHES + 2; i++) addRecentSearch(`q${i}`);
		const list = readRecentSearches();
		expect(list).toHaveLength(MAX_RECENT_SEARCHES);
		expect(list?.[0]).toBe(`q${MAX_RECENT_SEARCHES + 2}`);
		expect(list).not.toContain("q1");
		expect(list).not.toContain("q2");
	});

	it("개별 삭제", () => {
		addRecentSearch("우주");
		addRecentSearch("느와르");
		expect(removeRecentSearch("우주")).toEqual(["느와르"]);
		expect(readRecentSearches()).toEqual(["느와르"]);
	});

	it("깨진 값은 빈 목록으로 본다", () => {
		localStorage.setItem("cineseek:recent-searches", "{not json");
		expect(readRecentSearches()).toEqual([]);
		localStorage.setItem("cineseek:recent-searches", JSON.stringify(["우주", 3, null]));
		expect(readRecentSearches()).toEqual(["우주"]);
	});

	it("저장소 접근이 막히면 null — 기능을 숨긴다", () => {
		vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
			throw new DOMException("denied", "SecurityError");
		});
		expect(readRecentSearches()).toBeNull();
		expect(addRecentSearch("우주")).toBeNull();
	});

	it("쓰기가 막히면 null", () => {
		vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
			throw new DOMException("quota", "QuotaExceededError");
		});
		expect(addRecentSearch("우주")).toBeNull();
	});
});
