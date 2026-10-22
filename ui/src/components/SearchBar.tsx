import { useEffect, useId, useRef, useState, type KeyboardEvent, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { History, Search, X } from "lucide-react";
import { cn } from "../lib/cn";
import { addRecentSearch, readRecentSearches, removeRecentSearch } from "../lib/recent-searches";

interface SearchBarProps {
	/** hero: 홈 중앙 큰 검색창, compact: 헤더 축소형 */
	size: "hero" | "compact";
	/** 제출 시 초기값 — 기본 현재 URL의 q */
	initialQuery?: string;
	/** 검색어 없이 제출했을 때 — 기본은 무시, /search에서는 /explore로 보낸다 */
	onEmptySubmit?: () => void;
}

/**
 * 검색창 — Enter 제출, `/` 단축키 포커스, 포커스 시 최근 검색어.
 * 입력 중 문자열은 로컬 상태(임시 UI 상태), 제출 시점에 URL로 반영한다 (§3)
 */
export function SearchBar({ size, initialQuery = "", onEmptySubmit }: SearchBarProps) {
	const [value, setValue] = useState(initialQuery);
	// null이면 저장소를 쓸 수 없는 환경 — 최근 검색어를 숨긴다
	const [recent, setRecent] = useState(readRecentSearches);
	const [recentOpen, setRecentOpen] = useState(false);
	const recentLabelId = useId();
	const inputRef = useRef<HTMLInputElement>(null);
	const navigate = useNavigate();

	// `/` 단축키 — 입력 중이 아닐 때만
	useEffect(() => {
		function onKeydown(e: globalThis.KeyboardEvent) {
			if (e.key === "/" && document.activeElement?.tagName !== "INPUT") {
				e.preventDefault();
				inputRef.current?.focus();
			}
		}
		window.addEventListener("keydown", onKeydown);
		return () => window.removeEventListener("keydown", onKeydown);
	}, []);

	function submit(e: FormEvent) {
		e.preventDefault();
		const q = value.trim();
		if (!q) {
			onEmptySubmit?.();
			return;
		}
		search(q);
	}

	function search(q: string) {
		setValue(q);
		setRecent(addRecentSearch(q));
		setRecentOpen(false);
		navigate(`/search?q=${encodeURIComponent(q)}`); // 검색 제출은 push (§3)
	}

	function removeRecent(q: string) {
		setRecent(removeRecentSearch(q));
		inputRef.current?.focus(); // 지운 버튼이 사라져도 포커스를 잃지 않게
	}

	// 한글 조합 중 Enter는 제출하지 않는다
	function onKeyDown(e: KeyboardEvent<HTMLInputElement>) {
		if (e.key === "Enter" && e.nativeEvent.isComposing) {
			e.preventDefault();
		}
	}

	const showRecent = recentOpen && recent !== null && recent.length > 0;

	return (
		<form
			onSubmit={submit}
			className="relative w-full"
			role="search"
			onKeyDown={(e) => {
				// 목록이 열려 있으면 Esc는 목록만 닫는다 (검색어는 지우지 않음)
				if (e.key === "Escape" && showRecent) {
					e.preventDefault();
					setRecentOpen(false);
					inputRef.current?.focus();
				}
			}}
			onBlur={(e) => {
				// 포커스가 검색창·목록 밖으로 나가면 닫는다
				if (!e.currentTarget.contains(e.relatedTarget)) setRecentOpen(false);
			}}
		>
			<Search
				aria-hidden
				className={cn(
					"pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-muted-foreground",
					size === "hero" ? "size-5" : "size-4",
				)}
			/>
			<input
				ref={inputRef}
				type="search"
				value={value}
				onChange={(e) => {
					setValue(e.target.value);
					setRecentOpen(e.target.value === "");
				}}
				onFocus={() => setRecentOpen(true)}
				onKeyDown={onKeyDown}
				placeholder="어떤 영화를 찾고 있나요? 예: 복수극인데 결말이 허무한 느와르"
				aria-label="영화 검색어"
				className={cn(
					"w-full rounded-[var(--radius-chip)] border border-border bg-background pl-11 text-foreground shadow-[var(--shadow-sm)]",
					"placeholder:text-muted-foreground focus:border-primary",
					size === "hero" ? "h-14 pr-5 text-base" : "h-10 pr-4 text-sm",
				)}
			/>
			{showRecent && (
				<div className="absolute z-20 mt-1 w-full rounded-[var(--radius)] border border-border bg-popover p-1 shadow-[var(--shadow-sm)]">
					<p id={recentLabelId} className="px-2 py-1.5 text-xs text-muted-foreground">
						최근 검색어
					</p>
					{/* 마우스다운 기본 동작을 막아 클릭 중에 입력창 포커스가 빠지며 목록이 닫히지 않게 한다 */}
					<ul aria-labelledby={recentLabelId} onMouseDown={(e) => e.preventDefault()}>
						{recent.map((q) => (
							<li key={q} className="flex items-center rounded-[calc(var(--radius)-2px)] hover:bg-surface">
								<button
									type="button"
									onClick={() => search(q)}
									className="flex min-w-0 flex-1 items-center gap-2 px-2 py-1.5 text-left text-sm text-foreground"
								>
									<History aria-hidden className="size-4 shrink-0 text-muted-foreground" />
									<span className="truncate">{q}</span>
								</button>
								<button
									type="button"
									onClick={() => removeRecent(q)}
									aria-label={`최근 검색어 ${q} 삭제`}
									className="grid size-8 shrink-0 place-items-center rounded-[calc(var(--radius)-2px)] text-muted-foreground hover:text-foreground"
								>
									<X aria-hidden className="size-4" />
								</button>
							</li>
						))}
					</ul>
				</div>
			)}
		</form>
	);
}
