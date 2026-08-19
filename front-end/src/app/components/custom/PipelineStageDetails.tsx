import { useState } from "react";
import { ChevronDown, ChevronRight, CheckCircle2, XCircle, Loader2, Clock, Terminal } from "lucide-react";
import { cn } from "../ui/utils";

export type StepStatus = "pending" | "running" | "passed" | "failed";

export type PipelineStep = {
  id: string;
  name: string;
  status: StepStatus;
  duration?: string;
  logs?: string;
};

export type PipelineStageDetail = {
  id: string;
  name: string;
  status: StepStatus;
  duration?: string;
  steps: PipelineStep[];
};

interface PipelineStageDetailsProps {
  stage: PipelineStageDetail;
  className?: string;
}

export function PipelineStageDetails({ stage, className }: PipelineStageDetailsProps) {
  const [expanded, setExpanded] = useState(stage.status === "running" || stage.status === "failed");
  const [expandedSteps, setExpandedSteps] = useState<Set<string>>(new Set());

  const toggleStep = (stepId: string) => {
    const newExpanded = new Set(expandedSteps);
    if (newExpanded.has(stepId)) {
      newExpanded.delete(stepId);
    } else {
      newExpanded.add(stepId);
    }
    setExpandedSteps(newExpanded);
  };

  const getStatusIcon = (status: StepStatus) => {
    switch (status) {
      case "passed":
        return <CheckCircle2 className="w-5 h-5 text-success" />;
      case "failed":
        return <XCircle className="w-5 h-5 text-error" />;
      case "running":
        return <Loader2 className="w-5 h-5 text-info animate-spin" />;
      default:
        return <Clock className="w-5 h-5 text-muted-foreground" />;
    }
  };

  const getStatusColor = (status: StepStatus) => {
    switch (status) {
      case "passed":
        return "border-l-success bg-success/5";
      case "failed":
        return "border-l-error bg-error/5";
      case "running":
        return "border-l-info bg-info/5";
      default:
        return "border-l-muted bg-muted/5";
    }
  };

  return (
    <div className={cn("rounded-xl border border-border bg-card/50 backdrop-blur-sm overflow-hidden", className)}>
      {/* Stage Header */}
      <button
        onClick={() => setExpanded(!expanded)}
        className="w-full flex items-center justify-between p-4 hover:bg-accent/50 transition-colors"
      >
        <div className="flex items-center gap-3">
          {expanded ? <ChevronDown className="w-5 h-5" /> : <ChevronRight className="w-5 h-5" />}
          {getStatusIcon(stage.status)}
          <div className="text-left">
            <h4 className="font-semibold">{stage.name}</h4>
            {stage.duration && (
              <p className="text-xs text-muted-foreground">Duration: {stage.duration}</p>
            )}
          </div>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-xs text-muted-foreground">
            {stage.steps.filter(s => s.status === "passed").length}/{stage.steps.length} steps
          </span>
        </div>
      </button>

      {/* Stage Steps */}
      {expanded && (
        <div className="border-t border-border">
          {stage.steps.map((step, index) => (
            <div key={step.id}>
              <button
                onClick={() => step.logs && toggleStep(step.id)}
                className={cn(
                  "w-full flex items-center justify-between p-3 pl-12 border-l-4 transition-colors",
                  getStatusColor(step.status),
                  step.logs && "hover:bg-accent/30 cursor-pointer"
                )}
              >
                <div className="flex items-center gap-3 flex-1">
                  {getStatusIcon(step.status)}
                  <span className="text-sm font-medium">{step.name}</span>
                </div>
                <div className="flex items-center gap-3">
                  {step.duration && (
                    <span className="text-xs text-muted-foreground">{step.duration}</span>
                  )}
                  {step.logs && (
                    expandedSteps.has(step.id) ?
                    <ChevronDown className="w-4 h-4 text-muted-foreground" /> :
                    <ChevronRight className="w-4 h-4 text-muted-foreground" />
                  )}
                </div>
              </button>

              {/* Step Logs */}
              {step.logs && expandedSteps.has(step.id) && (
                <div className="bg-[#0D0118] border-t border-border/50 p-4 mx-4 mb-2 rounded-lg">
                  <div className="flex items-center gap-2 mb-2">
                    <Terminal className="w-4 h-4 text-muted-foreground" />
                    <span className="text-xs font-medium text-muted-foreground">Console Output</span>
                  </div>
                  <pre className="text-xs font-mono text-foreground/90 whitespace-pre-wrap">
                    {step.logs}
                  </pre>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
