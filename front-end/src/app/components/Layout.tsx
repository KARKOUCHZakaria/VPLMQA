import { Outlet, Link, useLocation } from "react-router";
import {
  LayoutDashboard,
  TestTube,
  Ticket,
  BarChart3,
  FileText,
  Settings,
  FolderKanban,
  Bell,
  User,
  ChevronDown,
  LogOut,
  UserCircle,
  CheckCircle2
} from "lucide-react";
import { Button } from "./ui/button";
import { Avatar, AvatarFallback } from "./ui/avatar";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "./ui/select";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from "./ui/dropdown-menu";
import { Popover, PopoverContent, PopoverTrigger } from "./ui/popover";
import { Badge } from "./ui/badge";
import { Toaster } from "./ui/sonner";

const navigation = [
  { name: "Dashboard", path: "/home", icon: LayoutDashboard },
  { name: "Projects", path: "/projects", icon: FolderKanban },
  { name: "Tests", path: "/tests", icon: TestTube },
  { name: "Tickets", path: "/tickets", icon: Ticket },
  { name: "Feature Builder", path: "/feature-builder", icon: FileText },
  { name: "Analysis", path: "/analysis", icon: BarChart3 },
  { name: "Reports", path: "/reports", icon: FileText },
  { name: "Settings", path: "/settings", icon: Settings },
];

export function Layout() {
  const location = useLocation();

  const notifications = [
    {
      id: 1,
      title: "Test Suite Completed",
      message: "Login & Authentication Tests passed successfully",
      time: "5 min ago",
      type: "success",
      read: false,
    },
    {
      id: 2,
      title: "UI Mismatch Detected",
      message: "3 visual differences found on Dashboard page",
      time: "15 min ago",
      type: "warning",
      read: false,
    },
    {
      id: 3,
      title: "New Ticket Created",
      message: "VPLM-234 - Button alignment issue",
      time: "1 hour ago",
      type: "info",
      read: true,
    },
    {
      id: 4,
      title: "Deploy Successful",
      message: "Staging environment updated to v2.4.1",
      time: "2 hours ago",
      type: "success",
      read: true,
    },
  ];

  const handleLogout = () => {
    // Navigate to login page
    window.location.href = "/login";
  };

  return (
    <>
      <Toaster />
      <div className="flex h-screen bg-background">
      {/* Sidebar */}
      <aside className="w-64 bg-gradient-to-b from-[#0D0118] to-[#1C0538] text-foreground flex flex-col border-r border-border/50 shadow-[0_0_40px_rgba(124,58,237,0.15)]">
        {/* Logo */}
        <div className="p-6 border-b border-border/50">
          <div className="flex items-baseline gap-1">
            <span className="text-5xl font-bold bg-gradient-to-r from-[#7C3AED] via-[#A855F7] to-[#E879F9] bg-clip-text text-transparent">VPLM</span>
            <span className="text-2xl font-semibold">QA</span>
          </div>
          <p className="text-xs text-muted-foreground mt-1">AI-Powered Testing Platform</p>
        </div>

        {/* Navigation */}
        <nav className="flex-1 p-4 space-y-1">
          {navigation.map((item) => {
            const isActive = 
              item.path === "/" 
                ? location.pathname === "/" 
                : location.pathname.startsWith(item.path);
            const Icon = item.icon;

            return (
              <Link
                key={item.path}
                to={item.path}
                className={`flex items-center gap-3 px-4 py-3 rounded-lg transition-all ${
                  isActive
                    ? "bg-gradient-to-r from-[#7C3AED] to-[#A855F7] text-white shadow-[0_0_20px_rgba(124,58,237,0.5)]"
                    : "text-muted-foreground hover:bg-accent hover:text-foreground"
                }`}
              >
                <Icon className="w-5 h-5" />
                <span className="text-sm font-medium">{item.name}</span>
              </Link>
            );
          })}
        </nav>

        {/* User Profile Section */}
        <div className="p-4 border-t border-border/50">
          <div className="flex items-center gap-3 px-3 py-2 rounded-lg hover:bg-accent cursor-pointer transition-all">
            <Avatar className="w-9 h-9">
              <AvatarFallback className="bg-gradient-to-r from-[#7C3AED] to-[#A855F7] text-white text-sm">
                <User className="w-4 h-4" />
              </AvatarFallback>
            </Avatar>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium truncate">Zakaria</p>
              <p className="text-xs text-muted-foreground truncate">QA Engineer</p>
            </div>
            <ChevronDown className="w-4 h-4 text-muted-foreground" />
          </div>
        </div>
      </aside>

      {/* Main Content */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Top Bar */}
        <header className="bg-card/80 backdrop-blur-xl border-b border-border px-8 py-4 flex items-center justify-between shadow-[0_0_40px_rgba(124,58,237,0.15)]">
          <div className="flex items-center gap-4">
            <h1 className="text-2xl font-bold">
              {navigation.find((item) => 
                item.path === "/" 
                  ? location.pathname === "/" 
                  : location.pathname.startsWith(item.path)
              )?.name || "Dashboard"}
            </h1>
          </div>

          <div className="flex items-center gap-4">
            <Select defaultValue="staging">
              <SelectTrigger className="w-[140px] bg-input-background border-input">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="staging">Staging</SelectItem>
                <SelectItem value="production">Production</SelectItem>
              </SelectContent>
            </Select>

            {/* Notifications Popup */}
            <Popover>
              <PopoverTrigger className="relative text-muted-foreground hover:text-foreground hover:bg-accent inline-flex items-center justify-center rounded-md h-10 w-10 transition-all">
                <Bell className="w-5 h-5" />
                <span className="absolute top-1 right-1 w-2 h-2 bg-success rounded-full shadow-[0_0_10px_rgba(34,197,94,0.5)]"></span>
              </PopoverTrigger>
              <PopoverContent className="w-96 bg-popover/95 backdrop-blur-xl border-border p-0 shadow-[0_0_40px_rgba(124,58,237,0.2)]" align="end">
                <div className="p-4 border-b border-border">
                  <div className="flex items-center justify-between">
                    <h3 className="font-semibold">Notifications</h3>
                    <Badge className="bg-gradient-to-r from-[#7C3AED] to-[#A855F7] text-white shadow-[0_0_10px_rgba(124,58,237,0.5)]">
                      {notifications.filter(n => !n.read).length} new
                    </Badge>
                  </div>
                </div>
                <div className="max-h-[400px] overflow-y-auto">
                  {notifications.map((notification) => (
                    <div
                      key={notification.id}
                      className={`p-4 border-b border-border hover:bg-accent/50 cursor-pointer transition-all ${
                        !notification.read ? "bg-accent/30" : ""
                      }`}
                    >
                      <div className="flex items-start gap-3">
                        <div className="mt-1">
                          {notification.type === "success" && (
                            <div className="w-2 h-2 bg-success rounded-full shadow-[0_0_10px_rgba(34,197,94,0.5)]"></div>
                          )}
                          {notification.type === "warning" && (
                            <div className="w-2 h-2 bg-warning rounded-full shadow-[0_0_10px_rgba(245,158,11,0.5)]"></div>
                          )}
                          {notification.type === "info" && (
                            <div className="w-2 h-2 bg-info rounded-full shadow-[0_0_10px_rgba(56,189,248,0.5)]"></div>
                          )}
                        </div>
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-medium mb-1">
                            {notification.title}
                          </p>
                          <p className="text-sm text-muted-foreground mb-2">
                            {notification.message}
                          </p>
                          <p className="text-xs text-muted-foreground">{notification.time}</p>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
                <div className="p-3 border-t border-border">
                  <Button variant="ghost" className="w-full text-sm text-primary hover:text-primary hover:bg-accent">
                    View all notifications
                  </Button>
                </div>
              </PopoverContent>
            </Popover>

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
                    <p className="text-sm font-medium">Zakaria</p>
                    <p className="text-xs text-muted-foreground">zakaria@vplmqa.com</p>
                  </div>
                </DropdownMenuLabel>
                <DropdownMenuSeparator className="bg-border" />
                <DropdownMenuItem className="focus:bg-accent cursor-pointer">
                  <UserCircle className="w-4 h-4 mr-2" />
                  Profile
                </DropdownMenuItem>
                <DropdownMenuItem className="focus:bg-accent cursor-pointer" asChild>
                  <Link to="/settings">
                    <Settings className="w-4 h-4 mr-2" />
                    Settings
                  </Link>
                </DropdownMenuItem>
                <DropdownMenuSeparator className="bg-border" />
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
        </header>

        {/* Page Content */}
        <main className="flex-1 overflow-auto p-8 bg-background">
          <Outlet />
        </main>
      </div>
    </div>
    </>
  );
}

