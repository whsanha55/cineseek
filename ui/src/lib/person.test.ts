import { describe, expect, it } from "vitest";
import { formatLifespan } from "./person";

describe("formatLifespan", () => {
	it("생존 인물은 생년월일과 만 나이를 표시한다", () => {
		expect(formatLifespan("1962-07-03", null, "2026-10-23")).toBe("1962년 7월 3일 (64세)");
	});

	it("생일 전이면 한 살 적게 센다", () => {
		expect(formatLifespan("1962-07-03", null, "2026-07-02")).toBe("1962년 7월 3일 (63세)");
		expect(formatLifespan("1962-07-03", null, "2026-07-03")).toBe("1962년 7월 3일 (64세)");
	});

	it("사망한 인물은 생몰일과 향년을 표시한다", () => {
		expect(formatLifespan("1933-03-14", "2020-01-01", "2026-10-23")).toBe(
			"1933년 3월 14일 – 2020년 1월 1일 (향년 86세)",
		);
	});

	it("생년월일이 없으면 null", () => {
		expect(formatLifespan(null, null, "2026-10-23")).toBeNull();
		expect(formatLifespan(null, "2020-01-01", "2026-10-23")).toBeNull();
	});
});
