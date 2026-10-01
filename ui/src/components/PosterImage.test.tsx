import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { PosterImage } from "./PosterImage";

describe("PosterImage", () => {
	it("posterPath가 없으면 이니셜 플레이스홀더", () => {
		render(<PosterImage title="마션" posterPath={null} />);
		expect(screen.getByLabelText("마션 포스터 없음")).toHaveTextContent("마");
	});

	it("posterPath가 있으면 TMDB 이미지를 렌더링한다", () => {
		render(<PosterImage title="마션" posterPath="/abc.jpg" />);
		const img = screen.getByAltText("마션");
		expect(img).toHaveAttribute("src", "https://image.tmdb.org/t/p/w342/abc.jpg");
		expect(img).toHaveAttribute("loading", "lazy");
	});

	it("로드 실패 시 1회만 플레이스홀더로 전환한다", () => {
		render(<PosterImage title="마션" posterPath="/broken.jpg" />);
		const img = screen.getByAltText("마션");
		fireEvent.error(img);
		expect(screen.getByLabelText("마션 포스터 없음")).toBeInTheDocument();
		// 두 번째 에러는 이미 플레이스홀더 상태라 img가 없다 — 무한 재시도 없음
		expect(screen.queryByAltText("마션")).not.toBeInTheDocument();
	});
});
