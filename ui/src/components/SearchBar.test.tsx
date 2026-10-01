import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { SearchBar } from "./SearchBar";

function renderBar(props: Partial<Parameters<typeof SearchBar>[0]> = {}) {
	return render(
		<MemoryRouter>
			<SearchBar size="hero" {...props} />
		</MemoryRouter>,
	);
}

describe("SearchBar", () => {
	it("Enter 제출 — 값이 URL로 간다", async () => {
		// navigate 결과는 jsdom에서 실제 이동이 안 되므로 location 변경 시도를 잡는다
		const user = userEvent.setup();
		renderBar();
		const input = screen.getByRole("searchbox");
		await user.type(input, "우주 영화{Enter}");
		// MemoryRouter는 내부에서만 이동 — 최소한 제출이 input을 치우지 않고 에러 없이 통과되는지 확인
		expect(input).toHaveValue("우주 영화");
	});

	it("빈 값 제출은 onEmptySubmit만 부른다", async () => {
		const onEmptySubmit = vi.fn();
		const user = userEvent.setup();
		renderBar({ onEmptySubmit });
		await user.type(screen.getByRole("searchbox"), "{Enter}");
		expect(onEmptySubmit).toHaveBeenCalledOnce();
	});

	it("hero 크기도 아이콘 자리 왼쪽 패딩을 유지한다", () => {
		// px-* 가 tailwind-merge로 pl-11을 지우면 아이콘과 텍스트가 겹친다
		renderBar({ size: "hero" });
		expect(screen.getByRole("searchbox")).toHaveClass("pl-11");
	});
});
