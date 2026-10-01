import { Link } from "react-router-dom";

export function NotFound() {
	return (
		<div className="flex flex-col items-center gap-4 py-24 text-center">
			<p className="tnum text-4xl font-bold text-muted-foreground">404</p>
			<p className="text-base text-foreground">찾으시는 페이지가 없습니다.</p>
			<Link
				to="/"
				className="rounded-[var(--radius)] bg-primary px-4 py-2 text-sm font-medium text-primary-foreground"
			>
				홈으로
			</Link>
		</div>
	);
}
