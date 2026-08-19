import { cn } from "../ui/utils";

export type StatusBadgeVariant = "PASSED" | "FAILED" | "RUNNING" | "PENDING" | "IGNORED" | "REFUSED" | "VALIDATED";

interface StatusBadgeProps {
  variant: StatusBadgeVariant;
  className?: string;
}

export function StatusBadge({ variant, className }: StatusBadgeProps) {
  const variants = {
    PASSED: {
      bg: "bg-gradient-to-r from-[#059669] to-[#22C55E]",
      text: "text-white",
      icon: "✓",
    },
    FAILED: {
      bg: "bg-gradient-to-r from-[#DC2626] to-[#EF4444]",
      text: "text-white",
      icon: "✗",
    },
    RUNNING: {
      bg: "bg-gradient-to-r from-[#0891B2] to-[#38BDF8]",
      text: "text-white",
      icon: "⟳",
      animate: true,
    },
    PENDING: {
      bg: "bg-gradient-to-r from-[#475569] to-[#64748B]",
      text: "text-white",
      icon: "⏳",
    },
    IGNORED: {
      bg: "bg-gradient-to-r from-[#D97706] to-[#F59E0B]",
      text: "text-white",
      icon: "◎",
    },
    REFUSED: {
      bg: "bg-gradient-to-r from-[#DC2626] to-[#EF4444]",
      text: "text-white",
      icon: "✗",
    },
    VALIDATED: {
      bg: "bg-gradient-to-r from-[#7C3AED] to-[#A855F7]",
      text: "text-white",
      icon: "✓",
    },
  };

  const style = variants[variant] ?? variants.PENDING;

  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium",
        style.bg,
        style.text,
        style.animate && "animate-pulse",
        className
      )}
    >
      <span className={style.animate ? "animate-spin" : ""}>{style.icon}</span>
      {variant}
    </span>
  );
}
