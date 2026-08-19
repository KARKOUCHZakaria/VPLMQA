import { CheckCircle2, XCircle, Loader2, Clock } from "lucide-react";
import { cn } from "../ui/utils";

export type PipelineStage = {
  id: string;
  name: string;
  status: "pending" | "running" | "passed" | "failed" | "skipped";
  duration?: string;
};

interface PipelineFlowProps {
  stages: PipelineStage[];
  className?: string;
}

export function PipelineFlow({ stages, className }: PipelineFlowProps) {
  const getStageColor = (status: PipelineStage["status"]) => {
    switch (status) {
      case "passed":
        return {
          bg: "bg-gradient-to-br from-[#059669] to-[#22C55E]",
          border: "border-success",
          text: "text-white",
          connector: "bg-success",
          shadow: "shadow-[0_0_20px_rgba(34,197,94,0.5)]",
        };
      case "failed":
        return {
          bg: "bg-gradient-to-br from-[#DC2626] to-[#EF4444]",
          border: "border-error",
          text: "text-white",
          connector: "bg-error",
          shadow: "shadow-[0_0_20px_rgba(239,68,68,0.5)]",
        };
      case "running":
        return {
          bg: "bg-gradient-to-br from-[#0891B2] to-[#38BDF8]",
          border: "border-info",
          text: "text-white",
          connector: "bg-info",
          shadow: "shadow-[0_0_20px_rgba(56,189,248,0.5)]",
        };
      case "skipped":
        return {
          bg: "bg-gradient-to-br from-[#64748B] to-[#94A3B8]",
          border: "border-muted",
          text: "text-white",
          connector: "bg-muted-foreground",
          shadow: "",
        };
      default: // pending
        return {
          bg: "bg-muted",
          border: "border-muted-foreground",
          text: "text-muted-foreground",
          connector: "bg-border",
          shadow: "",
        };
    }
  };

  const getStageIcon = (status: PipelineStage["status"]) => {
    switch (status) {
      case "passed":
        return <CheckCircle2 className="w-6 h-6" />;
      case "failed":
        return <XCircle className="w-6 h-6" />;
      case "running":
        return <Loader2 className="w-6 h-6 animate-spin" />;
      case "skipped":
        return <div className="w-6 h-6 flex items-center justify-center text-xs">⊘</div>;
      default: // pending
        return <Clock className="w-6 h-6" />;
    }
  };

  return (
    <div className={cn("w-full overflow-x-auto", className)}>
      <div className="inline-flex items-center gap-0 min-w-max p-8">
        {stages.map((stage, index) => {
          const colors = getStageColor(stage.status);
          const isLast = index === stages.length - 1;

          return (
            <div key={stage.id} className="flex items-center">
              {/* Stage Node */}
              <div className="relative flex flex-col items-center group">
                {/* Circle with Icon */}
                <div
                  className={cn(
                    "w-16 h-16 rounded-full flex items-center justify-center border-4 transition-all duration-300",
                    colors.bg,
                    colors.border,
                    colors.text,
                    colors.shadow,
                    stage.status === "running" && "animate-pulse"
                  )}
                >
                  {getStageIcon(stage.status)}
                </div>

                {/* Stage Name */}
                <div className="mt-3 text-center min-w-[120px] max-w-[120px]">
                  <p className="text-sm font-semibold truncate">{stage.name}</p>
                  {stage.duration && (
                    <p className="text-xs text-muted-foreground mt-1">
                      {stage.duration}
                    </p>
                  )}
                  <span
                    className={cn(
                      "inline-block mt-1 px-2 py-0.5 rounded-full text-xs font-medium",
                      stage.status === "passed" && "bg-success/20 text-success",
                      stage.status === "failed" && "bg-error/20 text-error",
                      stage.status === "running" && "bg-info/20 text-info",
                      stage.status === "pending" && "bg-muted text-muted-foreground",
                      stage.status === "skipped" && "bg-muted text-muted-foreground"
                    )}
                  >
                    {stage.status.toUpperCase()}
                  </span>
                </div>

                {/* Hover Tooltip */}
                <div className="absolute -top-12 left-1/2 -translate-x-1/2 opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none z-10">
                  <div className="bg-popover/95 backdrop-blur-xl border border-border rounded-lg px-3 py-2 shadow-lg whitespace-nowrap">
                    <p className="text-xs font-medium">{stage.name}</p>
                    <p className="text-xs text-muted-foreground">
                      {stage.status} {stage.duration && `• ${stage.duration}`}
                    </p>
                  </div>
                </div>
              </div>

              {/* Connector Line */}
              {!isLast && (
                <div
                  className={cn(
                    "h-1 w-24 mx-2 rounded-full transition-all duration-500",
                    colors.connector,
                    stage.status === "running" && "animate-pulse"
                  )}
                />
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
