import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

/** shadcn 관례의 클래스 병합 헬퍼 */
export function cn(...inputs: ClassValue[]) {
	return twMerge(clsx(inputs));
}
