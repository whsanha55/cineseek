import { describe, expect, it } from "vitest";
import { gradeOf } from "./relevance";

describe("gradeOf — 상대 2단계", () => {
	it("최고 점수의 75% 이상은 high", () => {
		expect(gradeOf(0.9, [0.9, 0.5])).toBe("high");
		expect(gradeOf(0.7, [0.9, 0.5])).toBe("high"); // 0.7 >= 0.9*0.75
		expect(gradeOf(0.5, [0.9, 0.5])).toBe("normal");
	});

	it("단일 결과는 high", () => {
		expect(gradeOf(0.1, [0.1])).toBe("high");
	});

	it("빈 집합·비점수는 normal", () => {
		expect(gradeOf(0.5, [])).toBe("normal");
		expect(gradeOf(0, [0, 0])).toBe("normal");
	});

	it("전부 같은 점수면 high", () => {
		expect(gradeOf(0.4, [0.4, 0.4, 0.4])).toBe("high");
	});
});
