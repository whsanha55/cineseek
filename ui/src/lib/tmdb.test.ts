import { describe, expect, it } from "vitest";
import { posterUrl } from "./tmdb";

describe("posterUrl", () => {
	it("경로와 크기를 조립한다", () => {
		expect(posterUrl("/abc.jpg")).toBe("https://image.tmdb.org/t/p/w342/abc.jpg");
		expect(posterUrl("/abc.jpg", "w500")).toBe("https://image.tmdb.org/t/p/w500/abc.jpg");
	});

	it("없으면 null — 플레이스홀더로", () => {
		expect(posterUrl(null)).toBeNull();
		expect(posterUrl(undefined)).toBeNull();
		expect(posterUrl("")).toBeNull();
	});
});
