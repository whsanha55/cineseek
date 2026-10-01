import type { HTMLAttributes } from "react";
import { cn } from "../../lib/cn";

type Variant = "default" | "outline";

const variantClass: Record<Variant, string> = {
	default: "bg-surface text-foreground",
	outline: "border border-border text-muted-foreground",
};

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
	variant?: Variant;
}

export function Badge({ className, variant = "default", ...props }: BadgeProps) {
	return (
		<span
			className={cn(
				"inline-flex items-center gap-1 rounded-[var(--radius-chip)] px-2.5 py-1 text-xs font-medium",
				variantClass[variant],
				className,
			)}
			{...props}
		/>
	);
}
