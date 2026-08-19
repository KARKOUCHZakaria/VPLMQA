import { forwardRef } from "react";
import { cn } from "../ui/utils";

export type GradientButtonVariant = "primary" | "ghost" | "danger" | "success" | "amber";

interface GradientButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: GradientButtonVariant;
  children: React.ReactNode;
}

export const GradientButton = forwardRef<HTMLButtonElement, GradientButtonProps>(
  ({ className, variant = "primary", children, ...props }, ref) => {
    const variants = {
      primary: "bg-gradient-to-r from-[#7C3AED] via-[#A855F7] to-[#E879F9] text-white shadow-[0_0_20px_rgba(124,58,237,0.5)] hover:shadow-[0_0_30px_rgba(124,58,237,0.7)] hover:scale-[1.02] transition-all",
      ghost: "border-2 border-[#A855F7] text-[#A855F7] bg-transparent hover:bg-gradient-to-r hover:from-[#7C3AED] hover:via-[#A855F7] hover:to-[#E879F9] hover:text-white transition-all",
      danger: "bg-gradient-to-r from-[#DC2626] to-[#EF4444] text-white shadow-[0_0_20px_rgba(239,68,68,0.5)] hover:shadow-[0_0_30px_rgba(239,68,68,0.7)] hover:scale-[1.02] transition-all",
      success: "bg-gradient-to-r from-[#059669] to-[#22C55E] text-white shadow-[0_0_20px_rgba(34,197,94,0.5)] hover:shadow-[0_0_30px_rgba(34,197,94,0.7)] hover:scale-[1.02] transition-all",
      amber: "bg-gradient-to-r from-[#D97706] to-[#F59E0B] text-white shadow-[0_0_20px_rgba(245,158,11,0.5)] hover:shadow-[0_0_30px_rgba(245,158,11,0.7)] hover:scale-[1.02] transition-all",
    };

    return (
      <button
        ref={ref}
        className={cn(
          "px-4 py-2 rounded-[10px] font-medium text-sm inline-flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed",
          variants[variant],
          className
        )}
        {...props}
      >
        {children}
      </button>
    );
  }
);

GradientButton.displayName = "GradientButton";
