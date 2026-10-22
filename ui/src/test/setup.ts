import "@testing-library/jest-dom/vitest";

// Node 25+는 --localstorage-file 없이 실행하면 내장 localStorage가 undefined라 jsdom 것을 가린다
declare global {
	var jsdom: { window: Window } | undefined;
}
if (typeof localStorage === "undefined" && globalThis.jsdom) {
	Object.defineProperty(globalThis, "localStorage", {
		value: globalThis.jsdom.window.localStorage,
		configurable: true,
	});
}
