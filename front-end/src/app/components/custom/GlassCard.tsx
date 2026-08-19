import { forwardRef } from "react";
import { cn } from "../ui/utils";

interface GlassCardProps extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode;
}

export const GlassCard = forwardRef<HTMLDivElement, GlassCardProps>(
  ({ className, children, ...props }, ref) => {
    return (
      <div
        ref={ref}
        className={cn(
          "rounded-2xl backdrop-blur-[20px] border border-border/20 shadow-[0_0_40px_rgba(124,58,237,0.15)]",
          "bg-gradient-to-br from-card/80 to-card/60",
          className
        )}
        {...props}
      >
        {children}
      </div>
    );
  }
);

GlassCard.displayName = "GlassCard";
