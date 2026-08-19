import { Loader2, CheckCircle2, XCircle, Clock } from "lucide-react";
import { cn } from "../ui/utils";

export type PipelineStageStatus = "pending" | "running" | "passed" | "failed";

interface PipelineStageChipProps {
  label: string;
  status: PipelineStageStatus;
  icon?: React.ReactNode;
  className?: string;
}

export function PipelineStageChip({ label, status, icon, className }: PipelineStageChipProps) {
  const statusStyles = {
    pending: {
      bg: "bg-gradient-to-r from-[#475569] to-[#64748B]",
      text: "text-white",
      icon: <Clock className="w-4 h-4" />,
    },
    running: {
      bg: "bg-gradient-to-r from-[#0891B2] to-[#38BDF8]",
      text: "text-white",
      icon: <Loader2 className="w-4 h-4 animate-spin" />,
    },
    passed: {
      bg: "bg-gradient-to-r from-[#059669] to-[#22C55E]",
      text: "text-white",
      icon: <CheckCircle2 className="w-4 h-4" />,
    },
    failed: {
      bg: "bg-gradient-to-r from-[#DC2626] to-[#EF4444]",
      text: "text-white",
      icon: <XCircle className="w-4 h-4" />,
    },
  };

  const style = statusStyles[status];

  return (
    <div
      className={cn(
        "inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium shadow-lg transition-all hover:scale-105",
        style.bg,
        style.text,
        className
      )}
    >
      {icon || style.icon}
      <span>{label}</span>
    </div>
  );
}
