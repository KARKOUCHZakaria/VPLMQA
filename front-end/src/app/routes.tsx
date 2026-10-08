import type { ComponentType } from "react";
import { createBrowserRouter, Navigate } from "react-router";
import { Login } from "./pages/Login";
import { Home } from "./pages/Home";
import { DashboardStats } from "./pages/DashboardStats";
import { Tests } from "./pages/Tests";
import { Tickets } from "./pages/Tickets";
import { Analysis } from "./pages/Analysis";
import { Reports } from "./pages/Reports";
import { Settings } from "./pages/Settings";
import { ChangePassword } from "./pages/ChangePassword";
import { ProjectManagement } from "./pages/ProjectManagement";
import { FeatureBuilder } from "./components/e2e/FeatureBuilder";

const protectedElement = (Component: ComponentType) => {
  const ProtectedRoute = () => (
    localStorage.getItem("accessToken") ? <Component /> : <Navigate to="/login" replace />
  );
  return <ProtectedRoute />;
};

export const router = createBrowserRouter([
  {
    path: "/login",
    Component: Login,
  },
  {
    path: "/",
    element: <Navigate to="/login" replace />,
  },
  {
    path: "/home",
    element: protectedElement(Home),
  },
  {
    path: "/dashboard",
    element: protectedElement(DashboardStats),
  },
  {
    path: "/projects",
    element: protectedElement(ProjectManagement),
  },
  {
    path: "/tests",
    element: protectedElement(Tests),
  },
  {
    path: "/tickets",
    element: protectedElement(Tickets),
  },
  {
    path: "/analysis",
    element: protectedElement(Analysis),
  },
  {
    path: "/reports",
    element: protectedElement(Reports),
  },
  {
    path: "/settings",
    element: protectedElement(Settings),
  },
  {
    path: "/change-password",
    element: protectedElement(ChangePassword),
  },
  {
    path: "/feature-builder",
    element: protectedElement(FeatureBuilder),
  },
]);




