import { forwardRef, type SelectHTMLAttributes } from "react";
import { ChevronDown } from "lucide-react";
import { cn } from "../../lib/cn";

/**
 * 네이티브 select 래퍼 — 접근성·모바일 동작은 플랫폼에 맡긴다 (ponytail: 라이브러리 없이).
 * 필터의 값 선택은 이걸로 충분하다
 */
export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(
	({ className, children, ...props }, ref) => (
		<div className={cn("relative", className)}>
			<select
				ref={ref}
				className="h-8 w-full appearance-none rounded-[var(--radius)] border border-input bg-background pl-2.5 pr-7 text-sm text-foreground"
				{...props}
			>
				{children}
			</select>
			<ChevronDown
				aria-hidden
				className="pointer-events-none absolute right-2 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground"
			/>
		</div>
	),
);
Select.displayName = "Select";
