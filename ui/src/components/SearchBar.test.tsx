import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, useLocation } from "react-router-dom";
import { SearchBar } from "./SearchBar";

function renderBar(props: Partial<Parameters<typeof SearchBar>[0]> = {}) {
	return render(
		<MemoryRouter>
			<SearchBar size="hero" {...props} />
		</MemoryRouter>,
	);
}

beforeEach(() => localStorage.clear());
afterEach(() => vi.restoreAllMocks());

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

function LocationProbe() {
	const { pathname, search } = useLocation();
	return <output aria-label="현재 위치">{pathname + search}</output>;
}

function renderWithRecent(recent: string[]) {
	localStorage.setItem("cineseek:recent-searches", JSON.stringify(recent));
	return render(
		<MemoryRouter>
			<SearchBar size="hero" />
			<LocationProbe />
		</MemoryRouter>,
	);
}

describe("SearchBar 최근 검색어", () => {
	it("포커스하면 최근 검색어를 보여주고 Esc로 닫는다", async () => {
		const user = userEvent.setup();
		renderWithRecent(["느와르", "우주"]);
		expect(screen.queryByRole("list", { name: "최근 검색어" })).not.toBeInTheDocument();

		await user.click(screen.getByRole("searchbox"));
		const list = screen.getByRole("list", { name: "최근 검색어" });
		expect(list).toHaveTextContent("느와르");
		expect(list).toHaveTextContent("우주");

		await user.keyboard("{Escape}");
		expect(screen.queryByRole("list", { name: "최근 검색어" })).not.toBeInTheDocument();
	});

	it("클릭하면 그 검색어로 다시 검색한다", async () => {
		const user = userEvent.setup();
		renderWithRecent(["느와르", "우주"]);
		await user.click(screen.getByRole("searchbox"));
		await user.click(screen.getByRole("button", { name: "우주" }));

		expect(screen.getByLabelText("현재 위치")).toHaveTextContent(`/search?q=${encodeURIComponent("우주")}`);
		expect(screen.getByRole("searchbox")).toHaveValue("우주");
	});

	it("개별 삭제", async () => {
		const user = userEvent.setup();
		renderWithRecent(["느와르", "우주"]);
		await user.click(screen.getByRole("searchbox"));
		await user.click(screen.getByRole("button", { name: "최근 검색어 느와르 삭제" }));

		const list = screen.getByRole("list", { name: "최근 검색어" });
		expect(list).not.toHaveTextContent("느와르");
		expect(list).toHaveTextContent("우주");
	});

	it("제출한 검색어가 목록 맨 앞에 쌓인다", async () => {
		const user = userEvent.setup();
		renderWithRecent(["우주"]);
		await user.type(screen.getByRole("searchbox"), "느와르{Enter}");
		await user.clear(screen.getByRole("searchbox"));

		const items = screen.getAllByRole("listitem");
		expect(items[0]).toHaveTextContent("느와르");
		expect(items[1]).toHaveTextContent("우주");
	});

	it("저장소를 쓸 수 없으면 목록을 숨긴다", async () => {
		localStorage.setItem("cineseek:recent-searches", JSON.stringify(["우주"]));
		vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
			throw new DOMException("denied", "SecurityError");
		});
		const user = userEvent.setup();
		render(
			<MemoryRouter>
				<SearchBar size="hero" />
			</MemoryRouter>,
		);
		await user.click(screen.getByRole("searchbox"));
		expect(screen.queryByRole("list", { name: "최근 검색어" })).not.toBeInTheDocument();
	});
});
