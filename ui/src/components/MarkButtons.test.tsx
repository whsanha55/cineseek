import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MarkButtons } from "./MarkButtons";
import { stubLocalStorage } from "../test/memory-storage";

beforeEach(() => {
	stubLocalStorage();
});

afterEach(() => {
	vi.unstubAllGlobals();
});

describe("MarkButtons", () => {
	it("누르면 표시가 켜지고 영화 id만 localStorage에 저장된다", async () => {
		const user = userEvent.setup();
		render(<MarkButtons movieId={27205} variant="overlay" />);

		await user.click(screen.getByRole("button", { name: "찜" }));

		expect(screen.getByRole("button", { name: "찜" })).toHaveAttribute("aria-pressed", "true");
		expect(JSON.parse(localStorage.getItem("cineseek.marks") ?? "{}")).toEqual({
			liked: [27205],
			watched: [],
			disliked: [],
		});
	});

	it("localStorage를 쓸 수 없으면 버튼을 그리지 않는다", () => {
		stubLocalStorage({}, true);
		render(<MarkButtons movieId={27205} variant="overlay" />);

		expect(screen.queryByRole("button", { name: "찜" })).not.toBeInTheDocument();
	});

	it("관심없음을 누르면 찜이 꺼진다", async () => {
		const user = userEvent.setup();
		render(<MarkButtons movieId={27205} variant="labeled" />);

		await user.click(screen.getByRole("button", { name: "찜" }));
		await user.click(screen.getByRole("button", { name: "관심없음" }));

		expect(screen.getByRole("button", { name: "찜" })).toHaveAttribute("aria-pressed", "false");
		expect(screen.getByRole("button", { name: "관심없음" })).toHaveAttribute("aria-pressed", "true");
	});
});
