import { Gem } from "lucide-react";
import { HIDDEN_GEM_PRESET, type HiddenGemFields } from "../lib/urlState";
import { cn } from "../lib/cn";

interface HiddenGemToggleProps {
	active: boolean;
	/** 프리셋 3값을 켜거나(null) 끈다 */
	onChange: (patch: Partial<HiddenGemFields>) => void;
}

/** 숨은 명작 토글 — 평점 7.5+ & 투표 50~500 프리셋 on/off (검색·탐색 공용) */
export function HiddenGemToggle({ active, onChange }: HiddenGemToggleProps) {
	return (
		<button
			type="button"
			aria-pressed={active}
			onClick={() =>
				active
					? onChange({ ratingMin: null, voteCountMin: null, voteCountMax: null })
					: onChange({ ...HIDDEN_GEM_PRESET })
			}
			className={cn(
				"inline-flex h-8 shrink-0 items-center gap-1.5 rounded-[var(--radius-chip)] border px-3 text-xs font-medium transition-colors",
				active
					? "border-primary bg-primary text-primary-foreground"
					: "border-border bg-background text-foreground hover:bg-surface",
			)}
		>
			<Gem aria-hidden className="size-3.5" />
			숨은 명작
		</button>
	);
}
