import { createBrowserRouter, RouterProvider, type RouteObject } from "react-router-dom";
import { Layout } from "./components/Layout";
import { Home } from "./pages/Home";
import { Search } from "./pages/Search";
import { MovieDetail } from "./pages/MovieDetail";
import { Explore } from "./pages/Explore";
import { PersonPage } from "./pages/PersonPage";
import { NotFound } from "./pages/NotFound";

const routes: RouteObject[] = [
	{ path: "/", element: <Home /> },
	{ path: "/search", element: <Search /> },
	{ path: "/movies/:id", element: <MovieDetail /> },
	{ path: "/explore", element: <Explore /> },
	{ path: "/people/:id", element: <PersonPage /> },
	{ path: "*", element: <NotFound /> },
];
if (import.meta.env.DEV) {
	// 개발용 디자인 시스템 카탈로그 — lazy(dynamic import)로 운영 번들에서 제외 (§6.3)
	routes.push({ path: "_ds", lazy: () => import("./pages/_DesignSystem") });
}

const router = createBrowserRouter([
	{
		path: "/",
		element: <Layout />,
		children: routes,
	},
]);

export function App() {
	return <RouterProvider router={router} />;
}
