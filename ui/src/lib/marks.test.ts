import { describe, expect, it } from "vitest";
import { MAX_SENT_IDS, parseMarks, recentIds, toggleMark, type Marks } from "./marks";

const EMPTY: Marks = { liked: [], watched: [], disliked: [] };

describe("parseMarks", () => {
	it("저장값이 없거나 JSON이 아니면 빈 표시", () => {
		expect(parseMarks(null)).toEqual(EMPTY);
		expect(parseMarks("{")).toEqual(EMPTY);
		expect(parseMarks("[1,2]")).toEqual(EMPTY);
	});

	it("양의 정수 id만 남기고 어긋난 종류는 비운다", () => {
		const raw = JSON.stringify({ liked: [1, "2", -3, 4.5, 5], watched: "x", disliked: [7] });
		expect(parseMarks(raw)).toEqual({ liked: [1, 5], watched: [], disliked: [7] });
	});
});

describe("toggleMark", () => {
	it("없으면 끝에 넣고 있으면 뺀다", () => {
		const added = toggleMark({ ...EMPTY, liked: [1] }, "liked", 2);
		expect(added.liked).toEqual([1, 2]);
		expect(toggleMark(added, "liked", 1).liked).toEqual([2]);
	});

	it("찜과 관심없음은 서로를 끈다", () => {
		const disliked = toggleMark({ ...EMPTY, liked: [1] }, "disliked", 1);
		expect(disliked).toEqual({ liked: [], watched: [], disliked: [1] });
		expect(toggleMark(disliked, "liked", 1)).toEqual({ liked: [1], watched: [], disliked: [] });
	});

	it("봤어요는 찜과 함께 켤 수 있다", () => {
		expect(toggleMark({ ...EMPTY, liked: [1] }, "watched", 1)).toEqual({ liked: [1], watched: [1], disliked: [] });
	});
});

describe("recentIds", () => {
	it("API 한도를 넘으면 최근 id만 보낸다", () => {
		const ids = Array.from({ length: MAX_SENT_IDS + 5 }, (_, i) => i + 1);
		const sent = recentIds(ids);
		expect(sent).toHaveLength(MAX_SENT_IDS);
		expect(sent[0]).toBe(6);
		expect(sent.at(-1)).toBe(MAX_SENT_IDS + 5);
	});
});
