/**
 * 찜·봤어요·관심없음 — 로그인이 없어 브라우저 localStorage에 영화 id만 둔다.
 * 종류별 id 배열(추가 순)이라 로그인 도입 시 (user, movie, kind) 행으로 그대로 옮길 수 있다
 */
import { useState, useSyncExternalStore } from "react";

export type MarkKind = "liked" | "watched" | "disliked";
export type Marks = Record<MarkKind, number[]>;

const STORAGE_KEY = "cineseek.marks";
const EMPTY: Marks = { liked: [], watched: [], disliked: [] };

/** 취향 추천을 보여주는 최소 찜 수 */
export const RECOMMEND_MIN_LIKED = 3;
/** 추천 API가 종류별로 받는 최대 id 수 — 넘으면 최근 것만 보낸다 */
export const MAX_SENT_IDS = 50;

function idList(value: unknown): number[] {
	return Array.isArray(value) ? value.filter((v): v is number => Number.isSafeInteger(v) && v > 0) : [];
}

/** 저장값은 외부 입력 — 형태가 어긋나면 해당 종류만 비운다 */
export function parseMarks(raw: string | null): Marks {
	if (raw === null) return EMPTY;
	let data: unknown;
	try {
		data = JSON.parse(raw);
	} catch {
		return EMPTY;
	}
	if (typeof data !== "object" || data === null) return EMPTY;
	return {
		liked: idList("liked" in data ? data.liked : null),
		watched: idList("watched" in data ? data.watched : null),
		disliked: idList("disliked" in data ? data.disliked : null),
	};
}

/** 있으면 빼고 없으면 끝에 넣는다. 찜과 관심없음은 함께 켤 수 없다 */
export function toggleMark(marks: Marks, kind: MarkKind, movieId: number): Marks {
	const next = { ...marks };
	if (marks[kind].includes(movieId)) {
		next[kind] = marks[kind].filter((id) => id !== movieId);
		return next;
	}
	next[kind] = [...marks[kind], movieId];
	const opposite = kind === "liked" ? "disliked" : kind === "disliked" ? "liked" : null;
	if (opposite) next[opposite] = marks[opposite].filter((id) => id !== movieId);
	return next;
}

export function recentIds(ids: number[]): number[] {
	return ids.slice(-MAX_SENT_IDS);
}

const listeners = new Set<() => void>();
let cachedRaw: string | null = null;
let cached: Marks = EMPTY;

/** Safari 사생활 보호 모드 등에서는 접근·쓰기가 예외를 던진다 */
function isStorageAvailable(): boolean {
	try {
		const probe = `${STORAGE_KEY}.probe`;
		window.localStorage.setItem(probe, probe);
		window.localStorage.removeItem(probe);
		return true;
	} catch {
		return false;
	}
}

function readMarks(): Marks {
	let raw: string | null;
	try {
		raw = window.localStorage.getItem(STORAGE_KEY);
	} catch {
		return EMPTY;
	}
	if (raw !== cachedRaw) {
		cachedRaw = raw;
		cached = parseMarks(raw);
	}
	return cached;
}

function subscribe(listener: () => void): () => void {
	listeners.add(listener);
	window.addEventListener("storage", listener);
	return () => {
		listeners.delete(listener);
		window.removeEventListener("storage", listener);
	};
}

function writeMarks(marks: Marks): void {
	try {
		window.localStorage.setItem(STORAGE_KEY, JSON.stringify(marks));
	} catch {
		return;
	}
	listeners.forEach((listener) => listener());
}

interface MarksState {
	/** false면 저장할 수 없으므로 화면에서 기능을 숨긴다 */
	available: boolean;
	marks: Marks;
	toggle: (kind: MarkKind, movieId: number) => void;
}

export function useMarks(): MarksState {
	const [available] = useState(isStorageAvailable);
	const marks = useSyncExternalStore(subscribe, readMarks, () => EMPTY);
	const toggle = (kind: MarkKind, movieId: number) => writeMarks(toggleMark(readMarks(), kind, movieId));
	return { available, marks, toggle };
}
