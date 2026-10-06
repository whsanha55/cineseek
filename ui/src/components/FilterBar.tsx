import { useEffect, useState } from "react";
import { SlidersHorizontal, X } from "lucide-react";
import { useGenres, usePerson } from "../api/hooks";
import { isHiddenGem, type SearchState } from "../lib/urlState";
import { cn } from "../lib/cn";
import { Button } from "./ui/button";
import { Select } from "./ui/select";
import { PersonAutocomplete } from "./PersonAutocomplete";
import { HiddenGemToggle } from "./HiddenGemToggle";

interface FilterBarProps {
	state: SearchState;
	/** 필터 한 항목 변경 — URL에 replace로 반영한다 (§3) */
	onChange: (patch: Partial<SearchState>) => void;
	onReset: () => void;
}

const RATING_STEPS = [6, 7, 8] as const;
const RUNTIME_STEPS = [60, 90, 120, 150, 180] as const;

function yearOptions(): number[] {
	const thisYear = new Date().getFullYear();
	return Array.from({ length: thisYear - 1969 }, (_, i) => thisYear - i);
}

/** 활성 필터 칩 */
function Chip({ label, onRemove }: { label: string; onRemove: () => void }) {
	return (
		<span className="inline-flex items-center gap-1 rounded-[var(--radius-chip)] bg-surface py-1 pl-2.5 pr-1 text-xs font-medium text-foreground">
			{label}
			<button
				type="button"
				onClick={onRemove}
				aria-label={`${label} 필터 해제`}
				className="rounded-[var(--radius-chip)] p-0.5 text-muted-foreground hover:text-foreground"
			>
				<X aria-hidden className="size-3" />
			</button>
		</span>
	);
}

/**
 * 필터 바 — 데스크톱 가로 바 / 모바일은 "필터" 버튼 → 하단 시트 (F4).
 * 값 선택 UI는 네이티브 select·자동완성. 상태 원천은 URL (props로만 주고받는다)
 */
export function FilterBar({ state, onChange, onReset }: FilterBarProps) {
	const { data: genres } = useGenres();
	const director = usePerson(state.directorId, "director");
	const cast = usePerson(state.castId, "cast");
	const [sheetOpen, setSheetOpen] = useState(false);

	// 시트 열림 중 배경 스크롤 고정
	useEffect(() => {
		document.body.style.overflow = sheetOpen ? "hidden" : "";
		return () => {
			document.body.style.overflow = "";
		};
	}, [sheetOpen]);

	const genreById = new Map((genres?.items ?? []).map((g) => [g.id, g]));
	const activeCount =
		state.genreIds.length +
		(state.yearMin !== null ? 1 : 0) +
		(state.yearMax !== null ? 1 : 0) +
		(state.ratingMin !== null ? 1 : 0) +
		(state.directorId !== null ? 1 : 0) +
		(state.castId !== null ? 1 : 0) +
		(state.runtimeMin !== null ? 1 : 0) +
		(state.runtimeMax !== null ? 1 : 0) +
		(state.voteCountMin !== null ? 1 : 0) +
		(state.voteCountMax !== null ? 1 : 0);

	const controls = (
		<>
			<div className="flex flex-wrap items-center gap-2">
				{(genres?.items ?? []).map((g) => {
					const active = state.genreIds.includes(g.id);
					return (
						<button
							key={g.id}
							type="button"
							aria-pressed={active}
							onClick={() =>
								onChange({
									genreIds: active
										? state.genreIds.filter((id) => id !== g.id)
										: [...state.genreIds, g.id],
								})
							}
							className={cn(
								"h-8 rounded-[var(--radius-chip)] border px-3 text-xs font-medium transition-colors",
								active
									? "border-primary bg-primary text-primary-foreground"
									: "border-border bg-background text-foreground hover:bg-surface",
							)}
						>
							{/* ponytail-audit #1: nameKo 제거 */}
							{g.name}
						</button>
					);
				})}
			</div>
			<div className="flex flex-wrap items-center gap-2">
				<Select
					aria-label="개봉 연도 시작"
					value={state.yearMin?.toString() ?? ""}
					onChange={(e) => onChange({ yearMin: e.target.value ? Number(e.target.value) : null })}
				>
					<option value="">연도 전체</option>
					{yearOptions().map((y) => (
						<option key={y} value={y}>
							{y}년 이후
						</option>
					))}
				</Select>
				<Select
					aria-label="개봉 연도 끝"
					value={state.yearMax?.toString() ?? ""}
					onChange={(e) => onChange({ yearMax: e.target.value ? Number(e.target.value) : null })}
				>
					<option value="">이전 전체</option>
					{yearOptions().map((y) => (
						<option key={y} value={y}>
							{y}년 이전
						</option>
					))}
				</Select>
				<Select
					aria-label="최소 평점"
					value={state.ratingMin?.toString() ?? ""}
					onChange={(e) => onChange({ ratingMin: e.target.value ? Number(e.target.value) : null })}
				>
					<option value="">평점 전체</option>
					{RATING_STEPS.map((r) => (
						<option key={r} value={r}>
							{r}점 이상
						</option>
					))}
				</Select>
				<Select
					aria-label="최소 러닝타임"
					value={state.runtimeMin?.toString() ?? ""}
					onChange={(e) => onChange({ runtimeMin: e.target.value ? Number(e.target.value) : null })}
				>
					<option value="">러닝타임 전체</option>
					{RUNTIME_STEPS.map((r) => (
						<option key={r} value={r}>
							{r}분 이상
						</option>
					))}
				</Select>
				<Select
					aria-label="최대 러닝타임"
					value={state.runtimeMax?.toString() ?? ""}
					onChange={(e) => onChange({ runtimeMax: e.target.value ? Number(e.target.value) : null })}
				>
					<option value="">이하 전체</option>
					{RUNTIME_STEPS.map((r) => (
						<option key={r} value={r}>
							{r}분 이하
						</option>
					))}
				</Select>
				<HiddenGemToggle active={isHiddenGem(state)} onChange={onChange} />
				<PersonAutocomplete
					role="director"
					label="감독 검색"
					onSelect={(p) => onChange({ directorId: p.personId })}
				/>
				<PersonAutocomplete
					role="cast"
					label="배우 검색"
					onSelect={(p) => onChange({ castId: p.personId })}
				/>
			</div>
		</>
	);

	return (
		<>
			{/* 데스크톱 */}
			<div className="hidden gap-4 md:flex md:flex-col">{controls}</div>

			{/* 모바일 — 필터 버튼 + 하단 시트 */}
			<div className="md:hidden">
				<Button variant="outline" size="sm" onClick={() => setSheetOpen(true)}>
					<SlidersHorizontal aria-hidden className="size-3.5" />
					필터
					{activeCount > 0 && (
						<span className="tnum rounded-[var(--radius-chip)] bg-primary px-1.5 text-xs text-primary-foreground">
							{activeCount}
						</span>
					)}
				</Button>
				{sheetOpen && (
					<div className="fixed inset-0 z-40 flex flex-col justify-end">
						<button
							type="button"
							aria-label="필터 닫기"
							onClick={() => setSheetOpen(false)}
							className="absolute inset-0 bg-[var(--scrim)]"
						/>
						<div
							role="dialog"
							aria-label="검색 필터"
							className="relative max-h-[80vh] space-y-4 overflow-auto rounded-t-2xl bg-background p-4 pb-8"
						>
							<div className="flex items-center justify-between">
								<h2 className="text-sm font-bold text-foreground">필터</h2>
								<Button variant="ghost" size="sm" onClick={() => setSheetOpen(false)}>
									닫기
								</Button>
							</div>
							{controls}
						</div>
					</div>
				)}
			</div>

			{/* 활성 필터 칩 — 데스크톱/모바일 공통 */}
			{activeCount > 0 && (
				<div className="flex flex-wrap items-center gap-1.5">
					{state.genreIds.map((id) => {
						const g = genreById.get(id);
						return (
							<Chip key={id} label={g ? g.name : `장르 ${id}`} /* ponytail-audit #1 */ onRemove={() =>
								onChange({ genreIds: state.genreIds.filter((x) => x !== id) })
							} />
						);
					})}
					{state.yearMin !== null && (
						<Chip label={`${state.yearMin}년 이후`} onRemove={() => onChange({ yearMin: null })} />
					)}
					{state.yearMax !== null && (
						<Chip label={`${state.yearMax}년 이전`} onRemove={() => onChange({ yearMax: null })} />
					)}
					{state.ratingMin !== null && (
						<Chip label={`평점 ${state.ratingMin}+`} onRemove={() => onChange({ ratingMin: null })} />
					)}
					{state.runtimeMin !== null && (
						<Chip label={`${state.runtimeMin}분 이상`} onRemove={() => onChange({ runtimeMin: null })} />
					)}
					{state.runtimeMax !== null && (
						<Chip label={`${state.runtimeMax}분 이하`} onRemove={() => onChange({ runtimeMax: null })} />
					)}
					{state.voteCountMin !== null && (
						<Chip
							label={`${state.voteCountMin}표 이상`}
							onRemove={() => onChange({ voteCountMin: null })}
						/>
					)}
					{state.voteCountMax !== null && (
						<Chip
							label={`${state.voteCountMax}표 이하`}
							onRemove={() => onChange({ voteCountMax: null })}
						/>
					)}
					{state.directorId !== null && (
						<Chip
							label={`감독 ${director.data?.name ?? ""}`}
							onRemove={() => onChange({ directorId: null })}
						/>
					)}
					{state.castId !== null && (
						<Chip label={`출연 ${cast.data?.name ?? ""}`} onRemove={() => onChange({ castId: null })} />
					)}
					<button
						type="button"
						onClick={() => {
							onReset();
							setSheetOpen(false);
						}}
						className="text-xs text-muted-foreground underline hover:text-foreground"
					>
						전체 해제
					</button>
				</div>
			)}
		</>
	);
}
