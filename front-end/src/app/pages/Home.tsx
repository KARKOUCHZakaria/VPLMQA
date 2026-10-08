import { useNavigate } from "react-router";
import {
  LayoutDashboard,
  TestTube,
  Ticket,
  BarChart3,
  FileText,
  Settings,
  FolderKanban,
  Sparkles,
  Moon,
  Sun,
  User,
  LogOut,
  KeyRound,
} from "lucide-react";
import { GlassCard } from "../components/custom/GlassCard";
import { useState } from "react";
import { Avatar, AvatarFallback } from "../components/ui/avatar";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from "../components/ui/dropdown-menu";

const apps = [
  {
    id: 1,
    name: "Dashboard",
    path: "/dashboard",
    icon: LayoutDashboard,
    gradient: "from-[#7C3AED] to-[#A855F7]",
    description: "Overview & Analytics",
    active: true,
    badge: null,
  },
  {
    id: 2,
    name: "E2E Tests",
    path: "/tests",
    icon: TestTube,
    gradient: "from-[#059669] to-[#34D399]",
    description: "Test Execution & Results",
    active: true,
    badge: { count: 3, color: "bg-warning", pulse: true },
  },
  {
    id: 3,
    name: "Tickets",
    path: "/tickets",
    icon: Ticket,
    gradient: "from-[#DC2626] to-[#F87171]",
    description: "Bug Tracking & Issues",
    active: true,
    badge: { count: 23, color: "bg-error" },
  },
  {
    id: 4,
    name: "Analytics",
    path: "/analysis",
    icon: BarChart3,
    gradient: "from-[#7C3AED] to-[#E879F9]",
    description: "Trends & Insights",
    active: true,
    badge: null,
  },
  {
    id: 5,
    name: "Reports",
    path: "/reports",
    icon: FileText,
    gradient: "from-[#2563EB] to-[#60A5FA]",
    description: "Generate Reports",
    active: true,
    badge: null,
  },
  {
    id: 6,
    name: "Settings",
    path: "/settings",
    icon: Settings,
    gradient: "from-[#475569] to-[#94A3B8]",
    description: "Configuration",
    active: true,
    badge: null,
  },
  {
    id: 7,
    name: "Projects",
    path: "/projects",
    icon: FolderKanban,
    gradient: "from-[#D97706] to-[#FCD34D]",
    description: "Project Management",
    active: true,
    badge: null,
  },
  {
    id: 8,
    name: "AI Assistant",
    path: "#",
    icon: Sparkles,
    gradient: "from-[#EC4899] to-[#F9A8D4]",
    description: "AI-Powered Help",
    active: false,
    badge: { text: "BETA", color: "bg-primary" },
  },
];

export function Home() {
  const navigate = useNavigate();
  const [isDark, setIsDark] = useState(true);

  const handleAppClick = (app: typeof apps[0]) => {
    if (app.active && app.path !== "#") {
      navigate(app.path);
    }
  };

  const toggleDarkMode = () => {
    setIsDark(!isDark);
    document.documentElement.classList.toggle("dark");
  };

  const handleLogout = () => {
    localStorage.removeItem("accessToken");
    localStorage.removeItem("vplmqa.currentUserEmail");
    navigate("/login");
  };

  return (
    <div className="min-h-screen bg-background">
      {/* Top Bar - Odoo style */}
      <header className="sticky top-0 z-50 bg-card/80 backdrop-blur-xl border-b border-border shadow-[0_0_40px_rgba(124,58,237,0.15)]">
        <div className="flex items-center justify-between px-8 py-4">
          {/* Logo */}
          <div className="flex items-baseline gap-1">
            <span className="text-4xl font-bold bg-gradient-to-r from-[#7C3AED] via-[#A855F7] to-[#E879F9] bg-clip-text text-transparent">
              VPLM
            </span>
            <span className="text-xl font-semibold">QA</span>
          </div>

          {/* Right Side */}
          <div className="flex items-center gap-4">
            {/* Dark Mode Toggle */}
            <button
              onClick={toggleDarkMode}
              className="w-10 h-10 rounded-lg hover:bg-accent flex items-center justify-center transition-colors"
            >
              {isDark ? <Sun className="w-5 h-5" /> : <Moon className="w-5 h-5" />}
            </button>

            {/* User Profile Dropdown */}
            <DropdownMenu>
              <DropdownMenuTrigger className="rounded-full outline-none">
                <Avatar>
                  <AvatarFallback className="bg-gradient-to-r from-[#7C3AED] to-[#A855F7] text-white shadow-[0_0_15px_rgba(124,58,237,0.5)]">
                    <User className="w-5 h-5" />
                  </AvatarFallback>
                </Avatar>
              </DropdownMenuTrigger>
              <DropdownMenuContent className="w-56 bg-popover/95 backdrop-blur-xl border-border shadow-[0_0_40px_rgba(124,58,237,0.2)]" align="end">
                <DropdownMenuLabel>
                  <div className="flex flex-col space-y-1">
                    <p className="text-sm font-medium">Administrator</p>
                    <p className="text-xs text-muted-foreground">
                      {localStorage.getItem("vplmqa.currentUserEmail") || "admin@vplmqa.com"}
                    </p>
                  </div>
                </DropdownMenuLabel>
                <DropdownMenuSeparator className="bg-border" />
                <DropdownMenuItem
                  className="cursor-pointer"
                  onClick={() => navigate("/change-password")}
                >
                  <KeyRound className="w-4 h-4 mr-2" />
                  Change Password
                </DropdownMenuItem>
                <DropdownMenuItem
                  className="text-error focus:bg-error/10 cursor-pointer"
                  onClick={handleLogout}
                >
                  <LogOut className="w-4 h-4 mr-2" />
                  Log out
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <div className="flex flex-col items-center justify-center py-12 px-8">
        <div className="max-w-6xl w-full space-y-8">
          {/* Header */}
          <div className="text-center space-y-2">
            <h1 className="text-4xl font-bold bg-gradient-to-r from-[#7C3AED] via-[#A855F7] to-[#E879F9] bg-clip-text text-transparent">
              Welcome to VPLMQA
            </h1>
            <p className="text-muted-foreground text-lg">
              AI-Powered Quality Assurance Platform
            </p>
            <div className="flex items-center justify-center gap-2 mt-4">
              <span className="text-sm text-muted-foreground">All Modules</span>
              <span className="px-2 py-0.5 rounded-full bg-primary/10 text-primary text-xs font-medium">
                {apps.filter((a) => a.active).length} Active
              </span>
            </div>
          </div>

          {/* App Grid */}
          <div className="grid grid-cols-3 gap-6">
            {apps.map((app) => {
              const Icon = app.icon;
              return (
                <button
                  key={app.id}
                  onClick={() => handleAppClick(app)}
                  disabled={!app.active}
                  className={`group relative ${
                    app.active ? "cursor-pointer" : "cursor-not-allowed opacity-60"
                  }`}
                >
                  <GlassCard
                    className={`p-8 flex flex-col items-center justify-center gap-4 transition-all duration-300 ${
                      app.active
                        ? "hover:scale-105 hover:shadow-[0_0_60px_rgba(124,58,237,0.3)] hover:border-primary/50"
                        : ""
                    }`}
                  >
                    {/* Icon Container */}
                    <div
                      className={`w-20 h-20 rounded-2xl bg-gradient-to-br ${app.gradient} flex items-center justify-center shadow-lg transition-all duration-300 ${
                        app.active ? "group-hover:scale-110 group-hover:shadow-2xl" : ""
                      }`}
                    >
                      <Icon className="w-10 h-10 text-white" />
                    </div>

                    {/* App Name */}
                    <div className="text-center space-y-1">
                      <h3 className="font-semibold text-base">{app.name}</h3>
                      <p className="text-xs text-muted-foreground">{app.description}</p>
                    </div>

                    {/* Status Indicator */}
                    <div className="absolute top-3 right-3">
                      <div
                        className={`w-2 h-2 rounded-full ${
                          app.active
                            ? "bg-success shadow-[0_0_10px_rgba(34,197,94,0.5)]"
                            : "bg-muted-foreground"
                        }`}
                      />
                    </div>

                    {/* Badge Notification */}
                    {app.badge && (
                      <div className="absolute -top-2 -right-2">
                        {app.badge.count !== undefined ? (
                          <div
                            className={`min-w-6 h-6 rounded-full ${app.badge.color} text-white text-xs font-bold flex items-center justify-center px-2 shadow-lg ${
                              app.badge.pulse ? "animate-pulse" : ""
                            }`}
                          >
                            {app.badge.count}
                          </div>
                        ) : (
                          <div
                            className={`px-2 py-1 rounded-full ${app.badge.color} text-white text-[10px] font-bold shadow-lg`}
                          >
                            {app.badge.text}
                          </div>
                        )}
                      </div>
                    )}
                  </GlassCard>
                </button>
              );
            })}
          </div>

          {/* Footer Info */}
          <div className="text-center pt-8">
            <p className="text-sm text-muted-foreground">
              Version 2.0 • Last updated: May 17, 2026
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}


