import { forwardRef, type ButtonHTMLAttributes } from "react";
import { cn } from "../../lib/cn";

type Variant = "default" | "outline" | "ghost" | "destructive";
type Size = "default" | "sm";

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
	variant?: Variant;
	size?: Size;
}

const variantClass: Record<Variant, string> = {
	default: "bg-primary text-primary-foreground hover:opacity-90",
	outline: "border border-border bg-background text-foreground hover:bg-surface",
	ghost: "text-foreground hover:bg-surface",
	destructive: "bg-destructive text-primary-foreground hover:opacity-90",
};

const sizeClass: Record<Size, string> = {
	default: "h-10 px-4 text-sm",
	sm: "h-8 px-3 text-xs",
};

/** shadcn Button 축약판 — 변형은 토큰 기반 클래스만 */
export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
	({ className, variant = "default", size = "default", type = "button", ...props }, ref) => (
		<button
			ref={ref}
			type={type}
			className={cn(
				"inline-flex items-center justify-center gap-2 rounded-[var(--radius)] font-medium transition-colors",
				"disabled:pointer-events-none disabled:opacity-50",
				variantClass[variant],
				sizeClass[size],
				className,
			)}
			{...props}
		/>
	),
);
Button.displayName = "Button";
