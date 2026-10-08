import { useNavigate } from "react-router";
import { Moon, Sun, User, LogOut, Home } from "lucide-react";
import { Avatar, AvatarFallback } from "../ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "../ui/dropdown-menu";
import { useState, useEffect } from "react";

interface TopBarProps {
  title?: string;
}

export function TopBar({ title }: TopBarProps) {
  const navigate = useNavigate();
  const [isDark, setIsDark] = useState(
    document.documentElement.classList.contains("dark")
  );

  useEffect(() => {
    const observer = new MutationObserver(() => {
      setIsDark(document.documentElement.classList.contains("dark"));
    });
    observer.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ["class"],
    });
    return () => observer.disconnect();
  }, []);

  const toggleDarkMode = () => {
    document.documentElement.classList.toggle("dark");
  };

  const handleLogout = () => {
    navigate("/login");
  };

  const handleHome = () => {
    navigate("/home");
  };

  return (
    <header className="sticky top-0 z-50 bg-card/80 backdrop-blur-xl border-b border-border shadow-[0_0_40px_rgba(124,58,237,0.15)]">
      <div className="flex items-center justify-between px-8 py-4">
        {/* Left Side - Logo and Title */}
        <div className="flex items-center gap-6">
          <div className="flex items-baseline gap-1">
            <span className="text-4xl font-bold bg-gradient-to-r from-[#7C3AED] via-[#A855F7] to-[#E879F9] bg-clip-text text-transparent">
              VPLM
            </span>
            <span className="text-xl font-semibold">QA</span>
          </div>
          {title && (
            <>
              <div className="w-px h-8 bg-border" />
              <h1 className="text-2xl font-semibold">{title}</h1>
            </>
          )}
        </div>

        {/* Right Side */}
        <div className="flex items-center gap-4">
          {/* Home Button */}
          <button
            onClick={handleHome}
            className="w-10 h-10 rounded-lg hover:bg-accent flex items-center justify-center transition-colors"
            title="Return to Home"
          >
            <Home className="w-5 h-5" />
          </button>

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
            <DropdownMenuContent
              className="w-56 bg-popover/95 backdrop-blur-xl border-border shadow-[0_0_40px_rgba(124,58,237,0.2)]"
              align="end"
            >
              <DropdownMenuLabel>
                <div className="flex flex-col space-y-1">
                  <p className="text-sm font-medium">Zakaria</p>
                  <p className="text-xs text-muted-foreground">
                    zakaria@vplmqa.com
                  </p>
                </div>
              </DropdownMenuLabel>
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
      </div>
    </header>
  );
}
