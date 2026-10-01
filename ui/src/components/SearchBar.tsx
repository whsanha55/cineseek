import { useEffect, useRef, useState, type KeyboardEvent, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { Search } from "lucide-react";
import { cn } from "../lib/cn";

interface SearchBarProps {
	/** hero: 홈 중앙 큰 검색창, compact: 헤더 축소형 */
	size: "hero" | "compact";
	/** 제출 시 초기값 — 기본 현재 URL의 q */
	initialQuery?: string;
	/** 검색어 없이 제출했을 때 — 기본은 무시, /search에서는 /explore로 보낸다 */
	onEmptySubmit?: () => void;
}

/**
 * 검색창 — Enter 제출, `/` 단축키 포커스.
 * 입력 중 문자열은 로컬 상태(임시 UI 상태), 제출 시점에 URL로 반영한다 (§3)
 */
export function SearchBar({ size, initialQuery = "", onEmptySubmit }: SearchBarProps) {
	const [value, setValue] = useState(initialQuery);
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
		navigate(`/search?q=${encodeURIComponent(q)}`); // 검색 제출은 push (§3)
	}

	// 한글 조합 중 Enter는 제출하지 않는다
	function onKeyDown(e: KeyboardEvent<HTMLInputElement>) {
		if (e.key === "Enter" && e.nativeEvent.isComposing) {
			e.preventDefault();
		}
	}

	return (
		<form onSubmit={submit} className="relative w-full" role="search">
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
				onChange={(e) => setValue(e.target.value)}
				onKeyDown={onKeyDown}
				placeholder="어떤 영화를 찾고 있나요? 예: 복수극인데 결말이 허무한 느와르"
				aria-label="영화 검색어"
				className={cn(
					"w-full rounded-[var(--radius-chip)] border border-border bg-background pl-11 text-foreground shadow-[var(--shadow-sm)]",
					"placeholder:text-muted-foreground focus:border-primary",
					size === "hero" ? "h-14 px-5 text-base" : "h-10 pr-4 text-sm",
				)}
			/>
		</form>
	);
}
