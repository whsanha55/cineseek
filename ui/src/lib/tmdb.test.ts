import { describe, expect, it } from "vitest";
import { posterUrl, profileUrl } from "./tmdb";

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

describe("profileUrl", () => {
	it("경로와 프로필 규격 크기를 조립한다", () => {
		expect(profileUrl("/p.jpg")).toBe("https://image.tmdb.org/t/p/w185/p.jpg");
		expect(profileUrl("/p.jpg", "h632")).toBe("https://image.tmdb.org/t/p/h632/p.jpg");
	});

	it("없으면 null — 이니셜 플레이스홀더로", () => {
		expect(profileUrl(null)).toBeNull();
		expect(profileUrl(undefined)).toBeNull();
		expect(profileUrl("")).toBeNull();
	});
});
