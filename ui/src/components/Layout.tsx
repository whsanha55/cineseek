import { Link, Outlet, ScrollRestoration, useLocation } from "react-router-dom";
import { SearchBar } from "./SearchBar";

/** 상단 헤더(로고 + 축소 검색창)는 홈을 제외한 모든 화면에 고정 (§3) */
export function Layout() {
	const { pathname, search } = useLocation();
	const params = new URLSearchParams(search);
	const isHome = pathname === "/";

	return (
		<div className="flex min-h-screen flex-col">
			{!isHome && (
				<header className="sticky top-0 z-30 border-b border-border bg-background/95 backdrop-blur">
					<div className="mx-auto flex h-14 max-w-[var(--content-max)] items-center gap-4 px-4 sm:px-8">
						<Link to="/" className="text-lg font-bold text-foreground" aria-label="cineseek 홈">
							cineseek
						</Link>
						<div className="max-w-md flex-1">
							<SearchBar size="compact" initialQuery={params.get("q") ?? ""} key={pathname} />
						</div>
					</div>
				</header>
			)}
			<main className="mx-auto w-full max-w-[var(--content-max)] flex-1 px-4 sm:px-8">
				<Outlet />
			</main>
			<footer className="mt-12 border-t border-border py-6">
				<p className="mx-auto max-w-[var(--content-max)] px-4 text-xs text-muted-foreground sm:px-8">
					This product uses the TMDB API but is not endorsed or certified by TMDB.
				</p>
			</footer>
			<ScrollRestoration />
		</div>
	);
}
