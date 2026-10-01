import { useEffect, useRef, useState } from "react";
import { usePeople } from "../api/hooks";
import type { PersonItem } from "../api/types";
import { cn } from "../lib/cn";

interface PersonAutocompleteProps {
	role: "director" | "cast";
	label: string;
	/** 선택 완료 — URL 반영은 호출자가 */
	onSelect: (person: PersonItem) => void;
}

/**
 * 감독·배우 자동완성 — 임의 문자열 제출 불가, 목록에서 선택해야 personId가 생긴다 (F4).
 * 2자 이상 입력 시 A6 조회. 키보드 내비게이션 지원
 */
export function PersonAutocomplete({ role, label, onSelect }: PersonAutocompleteProps) {
	const [text, setText] = useState("");
	const [open, setOpen] = useState(false);
	const [highlight, setHighlight] = useState(0);
	const rootRef = useRef<HTMLDivElement>(null);
	const { data, isFetching } = usePeople(text, role);
	const items = data?.items ?? [];

	// 바깥 클릭 닫기
	useEffect(() => {
		function onPointerDown(e: PointerEvent) {
			if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
		}
		document.addEventListener("pointerdown", onPointerDown);
		return () => document.removeEventListener("pointerdown", onPointerDown);
	}, []);

	function choose(person: PersonItem) {
		setText("");
		setOpen(false);
		onSelect(person);
	}

	return (
		<div ref={rootRef} className="relative w-40">
			<label className="sr-only" htmlFor={`person-${role}`}>
				{label}
			</label>
			<input
				id={`person-${role}`}
				type="text"
				role="combobox"
				aria-expanded={open && items.length > 0}
				aria-autocomplete="list"
				aria-controls={`person-list-${role}`}
				value={text}
				placeholder={label}
				onChange={(e) => {
					setText(e.target.value);
					setOpen(true);
					setHighlight(0);
				}}
				onFocus={() => setOpen(true)}
				onKeyDown={(e) => {
					if (e.nativeEvent.isComposing) return;
					if (!open || items.length === 0) return;
					if (e.key === "ArrowDown") {
						e.preventDefault();
						setHighlight((h) => Math.min(h + 1, items.length - 1));
					} else if (e.key === "ArrowUp") {
						e.preventDefault();
						setHighlight((h) => Math.max(h - 1, 0));
					} else if (e.key === "Enter") {
						e.preventDefault();
						choose(items[highlight]);
					} else if (e.key === "Escape") {
						setOpen(false);
					}
				}}
				className="h-8 w-full rounded-[var(--radius)] border border-input bg-background px-2.5 text-sm text-foreground placeholder:text-muted-foreground"
			/>
			{open && (items.length > 0 || isFetching) && (
				<ul
					id={`person-list-${role}`}
					role="listbox"
					className="absolute z-20 mt-1 max-h-64 w-full min-w-56 overflow-auto rounded-[var(--radius)] border border-border bg-popover p-1 shadow-[var(--shadow-sm)]"
				>
					{isFetching && items.length === 0 && (
						<li className="px-2 py-1.5 text-xs text-muted-foreground">검색 중…</li>
					)}
					{items.map((p, i) => (
						<li key={p.personId} role="option" aria-selected={i === highlight}>
							<button
								type="button"
								onMouseEnter={() => setHighlight(i)}
								onClick={() => choose(p)}
								className={cn(
									"w-full rounded-[calc(var(--radius)-2px)] px-2 py-1.5 text-left text-sm",
									i === highlight ? "bg-surface text-foreground" : "text-foreground",
								)}
							>
								<span className="block truncate">{p.name}</span>
								{p.knownFor.length > 0 && (
									<span className="block truncate text-xs text-muted-foreground">
										{p.knownFor.join(", ")}
									</span>
								)}
							</button>
						</li>
					))}
				</ul>
			)}
		</div>
	);
}
