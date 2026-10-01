import type { ReactNode } from "react";
import { SearchX, TriangleAlert } from "lucide-react";
import { Button } from "./ui/button";

interface StateShellProps {
	icon: ReactNode;
	title: string;
	description?: string;
	actions?: ReactNode;
}

function StateShell({ icon, title, description, actions }: StateShellProps) {
	return (
		<div className="flex flex-col items-center gap-3 py-16 text-center">
			<span aria-hidden className="text-muted-foreground">{icon}</span>
			<p className="text-base font-medium text-foreground">{title}</p>
			{description && <p className="max-w-sm text-sm text-muted-foreground">{description}</p>}
			{actions && <div className="mt-2 flex flex-wrap justify-center gap-2">{actions}</div>}
		</div>
	);
}

interface EmptyStateProps {
	/** 빈 결과 액션 — 단계별 해제 (F2): 필터 초기화(검색어 유지) / 전체 해제 / 탐색으로 이동 */
	onResetFilters?: () => void;
	onResetAll?: () => void;
	exploreTo?: string;
	navigate?: (to: string) => void;
}

export function EmptyState({ onResetFilters, onResetAll, exploreTo, navigate }: EmptyStateProps) {
	return (
		<StateShell
			icon={<SearchX className="size-10" />}
			title="결과가 없습니다"
			description="검색어는 유지하고 필터를 모두 해제해 보세요."
			actions={
				<>
					{onResetFilters && (
						<Button variant="default" size="sm" onClick={onResetFilters}>
							필터 초기화
						</Button>
					)}
					{onResetAll && (
						<Button variant="outline" size="sm" onClick={onResetAll}>
							전체 해제
						</Button>
					)}
					{exploreTo && navigate && (
						<Button variant="ghost" size="sm" onClick={() => navigate(exploreTo)}>
							탐색으로 이동
						</Button>
					)}
				</>
			}
		/>
	);
}

interface ErrorStateProps {
	onRetry?: () => void;
}

export function ErrorState({ onRetry }: ErrorStateProps) {
	return (
		<StateShell
			icon={<TriangleAlert className="size-10" />}
			title="문제가 발생했습니다"
			description="일시적인 오류일 수 있습니다. 다시 시도해 주세요."
			actions={onRetry && <Button variant="outline" size="sm" onClick={onRetry}>재시도</Button>}
		/>
	);
}
