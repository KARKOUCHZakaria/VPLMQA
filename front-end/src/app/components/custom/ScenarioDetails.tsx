import { CheckCircle2, XCircle, Loader2, Clock, X, Image as ImageIcon } from "lucide-react";
import { cn } from "../ui/utils";
import { GradientButton } from "./GradientButton";
import { StepKeywordChip, type StepKeyword } from "./StepKeywordChip";

export type StepStatus = "pending" | "running" | "completed" | "error";

export type ScenarioStep = {
  id: string;
  keyword: StepKeyword;
  text: string;
  status: StepStatus;
  duration?: string;
  error?: string;
};

export type Screenshot = {
  id: number | string;
  name: string;
  timestamp: string;
  url?: string;
};

export type ScenarioDetail = {
  id: string;
  featureId?: string;
  featureName?: string;
  projectId?: string;
  executionId?: string;
  name: string;
  status: "pending" | "running" | "completed" | "error";
  steps: ScenarioStep[];
  logs: string;
  screenshots: Screenshot[];
};

interface ScenarioDetailsProps {
  scenario: ScenarioDetail | null;
  onClose: () => void;
  onValidate?: () => void;
  onIgnore?: () => void;
  onReject?: () => void;
}

const normalizeStepStatus = (status: StepStatus | string): StepStatus => {
  switch (status?.toLowerCase()) {
    case "passed":
    case "completed":
      return "completed";
    case "failed":
    case "error":
      return "error";
    case "running":
      return "running";
    default:
      return "pending";
  }
};

const normalizeScenarioStatus = (status: ScenarioDetail["status"] | string): ScenarioDetail["status"] => {
  switch (status?.toLowerCase()) {
    case "passed":
    case "completed":
      return "completed";
    case "failed":
    case "error":
      return "error";
    case "running":
      return "running";
    default:
      return "pending";
  }
};

const stepStatusLabel = (status: StepStatus) => {
  switch (status) {
    case "completed":
      return "EXECUTED";
    case "error":
      return "FAILED";
    case "running":
      return "RUNNING";
    default:
      return "PENDING";
  }
};

export function ScenarioDetails({ scenario, onClose, onValidate, onIgnore, onReject }: ScenarioDetailsProps) {
  if (!scenario) {
    return (
      <div className="flex items-center justify-center h-96 text-muted-foreground">
        <p>Select a scenario to view details</p>
      </div>
    );
  }

  const getStepStatusIcon = (status: StepStatus | string) => {
    switch (normalizeStepStatus(status)) {
      case "completed":
        return <CheckCircle2 className="w-5 h-5 text-success" />;
      case "error":
        return <XCircle className="w-5 h-5 text-error" />;
      case "running":
        return <Loader2 className="w-5 h-5 text-info animate-spin" />;
      default:
        return <Clock className="w-5 h-5 text-muted-foreground" />;
    }
  };

  const scenarioStatus = normalizeScenarioStatus(scenario.status);
  const completedSteps = scenario.steps.filter((step) => normalizeStepStatus(step.status) === "completed").length;
  const failedSteps = scenario.steps.filter((step) => normalizeStepStatus(step.status) === "error").length;
  const isSearchEvidence = (screenshot: Screenshot) =>
    screenshot.name?.toLowerCase().includes("search result evidence") ||
    screenshot.url?.includes("/search-results/") ||
    (scenarioStatus === "completed" && !screenshot.name?.toLowerCase().includes("failed"));
  const evidenceScreenshots = scenario.screenshots.filter(isSearchEvidence);
  const failureScreenshots = scenario.screenshots.filter((screenshot) => !isSearchEvidence(screenshot));

  return (
    <div className="bg-card/80 backdrop-blur-xl border border-border rounded-2xl shadow-[0_0_40px_rgba(124,58,237,0.15)] overflow-hidden">
      <div className="p-6 border-b border-border flex items-center justify-between">
        <div className="flex items-center gap-4">
          {scenarioStatus === "completed" ? (
            <div className="w-12 h-12 rounded-full bg-gradient-to-r from-[#059669] to-[#22C55E] flex items-center justify-center shadow-[0_0_20px_rgba(34,197,94,0.5)]">
              <CheckCircle2 className="w-7 h-7 text-white" />
            </div>
          ) : scenarioStatus === "running" ? (
            <div className="w-12 h-12 rounded-full bg-gradient-to-r from-[#0891B2] to-[#38BDF8] flex items-center justify-center shadow-[0_0_20px_rgba(56,189,248,0.5)]">
              <Loader2 className="w-7 h-7 text-white animate-spin" />
            </div>
          ) : scenarioStatus === "error" ? (
            <div className="w-12 h-12 rounded-full bg-gradient-to-r from-[#DC2626] to-[#EF4444] flex items-center justify-center shadow-[0_0_20px_rgba(239,68,68,0.5)]">
              <XCircle className="w-7 h-7 text-white" />
            </div>
          ) : (
            <div className="w-12 h-12 rounded-full bg-muted flex items-center justify-center">
              <Clock className="w-7 h-7 text-muted-foreground" />
            </div>
          )}
          <div>
            <h3 className="text-xl font-bold">{scenario.name}</h3>
            <p className="text-sm text-muted-foreground mt-1">
              {scenario.steps.length} steps - {completedSteps} executed{failedSteps > 0 ? ` - ${failedSteps} failed` : ""}
            </p>
          </div>
        </div>
        <button
          onClick={onClose}
          className="w-8 h-8 rounded-lg hover:bg-accent flex items-center justify-center transition-colors"
        >
          <X className="w-5 h-5" />
        </button>
      </div>

      <div className="p-6 space-y-6 max-h-[600px] overflow-y-auto">
        <div>
          <h4 className="text-sm font-semibold mb-3">Steps</h4>
          <div className="space-y-2 relative">
            <div className="absolute left-[29px] top-2 bottom-2 w-0.5 bg-border" />

            {scenario.steps.map((step) => {
              const status = normalizeStepStatus(step.status);
              return (
                <div key={step.id} className="relative">
                  <div
                    className={cn(
                      "flex items-start gap-3 p-3 rounded-xl bg-card/50 backdrop-blur-sm border transition-all",
                      status === "error" ? "border-error bg-error/5" : "border-border",
                      "hover:border-primary/50"
                    )}
                  >
                    <div className="flex-shrink-0 relative z-10 bg-card rounded-full">
                      {getStepStatusIcon(status)}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <StepKeywordChip keyword={step.keyword} />
                        <p className="text-sm flex-1 min-w-0">{step.text}</p>
                        <span
                          className={cn(
                            "rounded-full px-2 py-0.5 text-[11px] font-semibold",
                            status === "completed" && "bg-success/10 text-success",
                            status === "error" && "bg-error/10 text-error",
                            status === "running" && "bg-info/10 text-info",
                            status === "pending" && "bg-muted text-muted-foreground"
                          )}
                        >
                          {stepStatusLabel(status)}
                        </span>
                      </div>
                      {step.error && (
                        <div className="mt-2 p-2 rounded bg-error/10 border border-error/20">
                          <p className="text-xs text-error font-mono">{step.error}</p>
                        </div>
                      )}
                    </div>
                    {step.duration && (
                      <div className="text-xs text-muted-foreground flex-shrink-0">{step.duration}</div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        <div>
          <h4 className="text-sm font-semibold mb-3">Output Logs</h4>
          <div className="bg-[#0D0118] border border-primary/30 rounded-xl p-4 font-mono text-sm text-[#F8F4FF] max-h-48 overflow-y-auto shadow-inner">
            <pre className="whitespace-pre-wrap">{scenario.logs}</pre>
          </div>
        </div>

        {evidenceScreenshots.length > 0 && (
          <div>
            <h4 className="text-sm font-semibold mb-3 flex items-center gap-2">
              <ImageIcon className="w-4 h-4" />
              Result Evidence
            </h4>
            <div className="grid grid-cols-1 gap-3">
              {evidenceScreenshots.map((screenshot) => (
                <div
                  key={screenshot.id}
                  className="bg-card/50 backdrop-blur-sm border border-success/50 rounded-xl p-4 space-y-3 transition-all"
                >
                  <div className="flex items-center gap-3">
                    <div className="w-12 h-12 bg-muted rounded flex items-center justify-center">
                      <ImageIcon className="w-6 h-6 text-muted-foreground" />
                    </div>
                    <div>
                      <p className="text-sm font-medium">{screenshot.name}</p>
                      <p className="text-xs text-muted-foreground">Captured at {screenshot.timestamp}</p>
                    </div>
                  </div>
                  {screenshot.url && (
                    <img
                      src={screenshot.url}
                      alt={screenshot.name}
                      className="w-full max-h-[420px] rounded-lg border border-border object-contain bg-black/80"
                    />
                  )}
                </div>
              ))}
            </div>
          </div>
        )}

        {failureScreenshots.length > 0 && (
          <div>
            <h4 className="text-sm font-semibold mb-3 flex items-center gap-2">
              <ImageIcon className="w-4 h-4" />
              Failure Screenshot
            </h4>
            <div className="grid grid-cols-1 gap-3">
              {failureScreenshots.map((screenshot) => (
                <div
                  key={screenshot.id}
                  className="bg-card/50 backdrop-blur-sm border border-error rounded-xl p-4 space-y-3 transition-all"
                >
                  <div className="flex items-center gap-3">
                    <div className="w-12 h-12 bg-muted rounded flex items-center justify-center">
                      <ImageIcon className="w-6 h-6 text-muted-foreground" />
                    </div>
                    <div>
                      <p className="text-sm font-medium">{screenshot.name}</p>
                      <p className="text-xs text-muted-foreground">Captured at {screenshot.timestamp}</p>
                    </div>
                  </div>
                  {screenshot.url && (
                    <img
                      src={screenshot.url}
                      alt={screenshot.name}
                      className="w-full max-h-[420px] rounded-lg border border-border object-contain bg-black/80"
                    />
                  )}
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {(onValidate || onIgnore || onReject) && (
        <div className="p-6 border-t border-border bg-card/30">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-semibold">Tester Action Required</p>
              <p className="text-xs text-muted-foreground mt-1">Review and validate this scenario</p>
            </div>
            <div className="flex gap-2">
              {onValidate && (
                <GradientButton variant="success" onClick={onValidate}>
                  <CheckCircle2 className="w-4 h-4 mr-1" />
                  Validate
                </GradientButton>
              )}
              {onIgnore && (
                <GradientButton variant="amber" onClick={onIgnore}>
                  Ignore
                </GradientButton>
              )}
              {onReject && (
                <GradientButton variant="danger" onClick={onReject}>
                  <XCircle className="w-4 h-4 mr-1" />
                  Refuse
                </GradientButton>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
