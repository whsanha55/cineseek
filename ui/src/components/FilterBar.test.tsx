import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { FilterBar } from "./FilterBar";
import { DEFAULT_SEARCH_STATE, type SearchState } from "../lib/urlState";
import type { GenresResponse } from "../api/types";

const GENRES: GenresResponse = {
	items: [
		{ id: 27, name: "공포" },
		{ id: 53, name: "스릴러" },
	],
};

function renderFilterBar(state: SearchState) {
	const client = new QueryClient();
	client.setQueryData(["genres"], GENRES);
	const onChange = vi.fn();
	render(
		<QueryClientProvider client={client}>
			<FilterBar state={state} onChange={onChange} onReset={() => {}} />
		</QueryClientProvider>,
	);
	return onChange;
}

describe("FilterBar 제외 장르", () => {
	it("제외할 장르를 고르면 excludeGenreIds에 추가한다", async () => {
		const onChange = renderFilterBar({ ...DEFAULT_SEARCH_STATE, q: "가족", excludeGenreIds: [53] });

		await userEvent.selectOptions(screen.getByLabelText("제외할 장르"), "27");

		expect(onChange).toHaveBeenCalledWith({ excludeGenreIds: [53, 27] });
	});

	it("이미 제외한 장르는 선택지에서 빠지고 '제외' 칩으로 보인다", () => {
		renderFilterBar({ ...DEFAULT_SEARCH_STATE, q: "가족", excludeGenreIds: [27] });

		expect(screen.queryByRole("option", { name: "공포 제외" })).not.toBeInTheDocument();
		expect(screen.getByText("제외 공포")).toBeInTheDocument();
	});

	it("'제외' 칩을 해제하면 해당 장르만 뺀다", async () => {
		const onChange = renderFilterBar({ ...DEFAULT_SEARCH_STATE, q: "가족", excludeGenreIds: [27, 53] });

		await userEvent.click(screen.getByRole("button", { name: "제외 공포 필터 해제" }));

		expect(onChange).toHaveBeenCalledWith({ excludeGenreIds: [53] });
	});
});
