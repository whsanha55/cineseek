/**
 * 백엔드 응답 타입 재수출 — 형태는 schema.d.ts(openapi-typescript 생성)가 원천.
 * 수기 도형 선언 금지 (UI-PLAN §5.4). springdoc이 required를 표시하지 않아
 * 전 필드가 옵셔널로 생성되므로 RequiredDeep으로 undefined만 제거한다
 * (null은 그대로 — rating: null 계약 유지)
 */
import type { components } from "./schema";

type RequiredDeep<T> = T extends (infer U)[]
	? RequiredDeep<U>[]
	: T extends object
		? { [K in keyof T]-?: RequiredDeep<T[K]> }
		: T;

type Api<S extends keyof components["schemas"]> = RequiredDeep<components["schemas"][S]>;

export type Genre = Api<"GenreResponse">;
export type MovieCard = Api<"MovieCardResponse">;
export type SearchItem = Api<"SearchItemResponse">;
export type Page = Api<"PageResponse">;
export type SearchResponse = Api<"SearchResponse">;
export type MovieListResponse = Api<"MovieListResponse">;
export type MovieDetail = Api<"MovieDetailResponse">;
export type PersonResponse = Api<"PersonResponse">;
export type CastMember = Api<"CastMemberResponse">;
export type GenresResponse = Api<"GenresResponse">;
export type PersonItem = Api<"PersonItemResponse">;
export type PeopleResponse = Api<"PeopleResponse">;
