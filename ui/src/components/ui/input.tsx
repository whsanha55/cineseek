import { forwardRef, type InputHTMLAttributes } from "react";
import { cn } from "../../lib/cn";

/** shadcn Input 축약판 */
export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
	({ className, type = "text", ...props }, ref) => (
		<input
			ref={ref}
			type={type}
			className={cn(
				"flex h-10 w-full rounded-[var(--radius)] border border-input bg-background px-3 py-2 text-base",
				"placeholder:text-muted-foreground disabled:opacity-50",
				className,
			)}
			{...props}
		/>
	),
);
Input.displayName = "Input";
