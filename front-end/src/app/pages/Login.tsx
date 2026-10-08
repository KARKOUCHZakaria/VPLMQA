import { useState } from "react";
import { useNavigate } from "react-router";
import { GlassCard } from "../components/custom/GlassCard";
import { GradientButton } from "../components/custom/GradientButton";
import { Input } from "../components/ui/input";
import { Label } from "../components/ui/label";
import { Checkbox } from "../components/ui/checkbox";
import { api } from "../utils/api";
import { toast } from "sonner";
import { Toaster } from "../components/ui/sonner";

export function Login() {
  const navigate = useNavigate();
  const [email, setEmail] = useState("admin@vplmqa.com");
  const [password, setPassword] = useState("admin");
  const [rememberMe, setRememberMe] = useState(true);
  const [loading, setLoading] = useState(false);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      const data = await api.post<{ accessToken: string, requiresPasswordChange: boolean }>("/api/v1/auth/login", {
        email,
        password,
      });
      localStorage.setItem("accessToken", data.accessToken);
      localStorage.setItem("vplmqa.currentUserEmail", email);
      toast.success("Welcome back!", {
        description: "Logged in successfully.",
      });
      setTimeout(() => {
        if (data.requiresPasswordChange) {
          navigate("/change-password");
        } else {
          navigate("/home");
        }
      }, 1000);
    } catch (err: any) {
      toast.error("Login failed", {
        description: err.message || "Invalid credentials",
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-background flex items-center justify-center p-4">
      <Toaster />
      <div className="w-full max-w-md">
        {/* Logo */}
        <div className="text-center mb-8">
          <div className="flex items-baseline justify-center gap-1">
            <span className="text-5xl font-bold bg-gradient-to-r from-[#7C3AED] via-[#A855F7] to-[#E879F9] bg-clip-text text-transparent">VPLM</span>
            <span className="text-2xl font-semibold text-foreground">QA</span>
          </div>
          <p className="text-sm text-muted-foreground mt-2">AI-Powered Testing Platform</p>
        </div>

        {/* Login Card */}
        <GlassCard className="p-6">
          <div className="mb-4">
            <h2 className="text-2xl font-bold">Welcome Back</h2>
            <p className="text-sm text-muted-foreground mt-1">
              Demo account is pre-filled for the first launch
            </p>
          </div>
          <div>
            <form onSubmit={handleLogin} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="email" className="text-foreground">Email</Label>
                <Input
                  id="email"
                  type="email"
                  placeholder="name@company.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="bg-card border-border text-foreground placeholder:text-muted-foreground"
                  required
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="password" className="text-foreground">Password</Label>
                <Input
                  id="password"
                  type="password"
                  placeholder="Enter your password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="bg-card border-border text-foreground placeholder:text-muted-foreground"
                  required
                />
              </div>
              <div className="flex items-center justify-between">
                <div className="flex items-center space-x-2">
                  <Checkbox
                    id="remember"
                    checked={rememberMe}
                    onCheckedChange={(checked) => setRememberMe(checked as boolean)}
                  />
                  <label
                    htmlFor="remember"
                    className="text-sm text-muted-foreground cursor-pointer"
                  >
                    Remember me
                  </label>
                </div>
                <a href="#" className="text-sm text-primary hover:text-primary/80">
                  Forgot password?
                </a>
              </div>
              <GradientButton type="submit" variant="primary" className="w-full" disabled={loading}>
                {loading ? "Signing In..." : "Sign In"}
              </GradientButton>
            </form>

            <div className="mt-6 text-center">
              <p className="text-sm text-muted-foreground">
                Don't have an account?{" "}
                <a href="#" className="text-primary hover:text-primary/80">
                  Contact your admin
                </a>
              </p>
            </div>
          </div>
        </GlassCard>

        {/* Footer */}
        <p className="text-center text-xs text-muted-foreground mt-8">
          © 2026 VPLMQA. All rights reserved.
        </p>
      </div>
    </div>
  );
}
