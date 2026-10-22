import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { MatchReason } from "./MatchReason";

describe("MatchReason", () => {
	it("겹치는 단어를 순서대로 보여준다", () => {
		const { container } = render(<MatchReason terms={["탈출", "감옥"]} />);
		expect(container).toHaveTextContent("‘탈출’, ‘감옥’ 일치");
	});

	it("겹치는 단어가 없으면 의미 유사로 표시한다", () => {
		render(<MatchReason terms={[]} />);
		expect(screen.getByText("의미 유사")).toBeInTheDocument();
	});
});
