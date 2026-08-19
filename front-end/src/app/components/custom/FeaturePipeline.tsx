import { useEffect, useMemo, useState } from "react";
import { CheckCircle2, XCircle, Loader2, Clock, Play, MonitorPlay, X, Search, ChevronLeft, ChevronRight } from "lucide-react";
import { cn } from "../ui/utils";
import { GlassCard } from "./GlassCard";
import { Button } from "../ui/button";

export type ScenarioStatus = "pending" | "running" | "passed" | "failed";

export type Scenario = {
  id: string;
  name: string;
  status: ScenarioStatus;
  duration?: string;
  description?: string;
  tags?: string[];
};

export type Feature = {
  id: string;
  name: string;
  status: ScenarioStatus;
  duration?: string;
  scenarios: Scenario[];
  totalScenarios: number;
  passedScenarios: number;
  failedScenarios: number;
};

export type PipelineStage = {
  id: string;
  name: string;
  status: ScenarioStatus;
  detail?: string;
};

export type PipelineRun = {
  id: string;
  featureId?: string;
  featureName: string;
  status: ScenarioStatus;
  startedAt: string;
  finishedAt?: string;
  stages: PipelineStage[];
  message?: string;
};

interface FeaturePipelineProps {
  features: Feature[];
  runs?: PipelineRun[];
  onScenarioClick: (featureId: string, scenarioId: string) => void;
  onRunFeature?: (feature: Feature) => void;
  onDismissRun?: (runId: string) => void;
  runningFeatureIds?: string[];
  className?: string;
}

export function FeaturePipeline({ features, runs = [], onScenarioClick, onRunFeature, onDismissRun, runningFeatureIds = [], className }: FeaturePipelineProps) {
  const [expandedFeature, setExpandedFeature] = useState<string | null>(null);
  const [featureSearch, setFeatureSearch] = useState("");
  const [featurePage, setFeaturePage] = useState(1);
  const featuresPerPage = 6;
  const selectedFeature = expandedFeature ? features.find(f => f.id === expandedFeature) : null;

  const filteredFeatures = useMemo(() => {
    const query = featureSearch.trim().toLowerCase();
    if (!query) return features;
    return features.filter((feature) => {
      const haystack = [
        feature.name,
        feature.status,
        `${feature.passedScenarios}/${feature.totalScenarios}`,
        ...(feature.scenarios || []).map((scenario) => scenario.name),
      ].join(" ").toLowerCase();
      return haystack.includes(query);
    });
  }, [featureSearch, features]);

  const totalFeaturePages = Math.max(1, Math.ceil(filteredFeatures.length / featuresPerPage));
  const currentFeaturePage = Math.min(featurePage, totalFeaturePages);
  const visibleFeatures = filteredFeatures.slice(
    (currentFeaturePage - 1) * featuresPerPage,
    currentFeaturePage * featuresPerPage
  );

  useEffect(() => {
    setFeaturePage(1);
  }, [featureSearch]);

  useEffect(() => {
    if (featurePage > totalFeaturePages) {
      setFeaturePage(totalFeaturePages);
    }
  }, [featurePage, totalFeaturePages]);

  const normalizeStatus = (status: string): ScenarioStatus => {
    switch (status.toLowerCase()) {
      case "passed":
      case "completed":
        return "passed";
      case "failed":
      case "error":
        return "failed";
      case "running":
        return "running";
      default:
        return "pending";
    }
  };

  const getStatusColor = (status: string) => {
    switch (normalizeStatus(status)) {
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
      default:
        return {
          bg: "bg-muted",
          border: "border-muted-foreground",
          text: "text-muted-foreground",
          connector: "bg-border",
          shadow: "",
        };
    }
  };

  const getStatusIcon = (status: string) => {
    switch (normalizeStatus(status)) {
      case "passed":
        return <CheckCircle2 className="w-6 h-6" />;
      case "failed":
        return <XCircle className="w-6 h-6" />;
      case "running":
        return <Loader2 className="w-6 h-6 animate-spin" />;
      default:
        return <Clock className="w-6 h-6" />;
    }
  };

  const toggleFeature = (featureId: string) => {
    setExpandedFeature(expandedFeature === featureId ? null : featureId);
  };

  return (
    <div className={cn("w-full space-y-6", className)}>
      {runs.length > 0 && (
        <GlassCard className="p-6">
          <div className="mb-5 flex items-center justify-between">
            <div>
              <h3 className="text-xl font-bold">Live Pipeline Runs</h3>
              <p className="text-sm text-muted-foreground mt-1">
                Feature saves and agent executions through the gateway
              </p>
            </div>
            <span className="text-xs text-muted-foreground">
              {runs.filter((run) => run.status === "running").length} running
            </span>
          </div>

          <div className="space-y-4">
            {runs.map((run) => {
              const runStatus = normalizeStatus(run.status);
              const completedStages = run.stages.filter((stage) => ["passed", "failed"].includes(normalizeStatus(stage.status))).length;
              const progress = Math.round((completedStages / Math.max(run.stages.length, 1)) * 100);
              const colors = getStatusColor(runStatus);

              return (
                <div key={run.id} className="rounded-lg border border-border bg-card/50 p-4">
                  <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
                    <div className="flex items-center gap-3">
                      <div className={cn("w-10 h-10 rounded-full flex items-center justify-center", colors.bg, colors.text, colors.shadow)}>
                        {getStatusIcon(runStatus)}
                      </div>
                      <div>
                        <h4 className="font-semibold">{run.featureName}</h4>
                        <p className="text-xs text-muted-foreground">
                          Started {run.startedAt}
                          {run.finishedAt ? ` • Finished ${run.finishedAt}` : ""}
                        </p>
                      </div>
                    </div>
                    <div className="flex min-w-[180px] items-center gap-3">
                      <div className="min-w-[180px] flex-1">
                        <div className="mb-1 flex items-center justify-between text-xs text-muted-foreground">
                          <span>{runStatus.toUpperCase()}</span>
                          <span>{progress}%</span>
                        </div>
                        <div className="h-2 rounded-full bg-muted overflow-hidden">
                          <div
                            className={cn(
                              "h-full transition-all duration-500",
                              runStatus === "failed" ? "bg-error" : runStatus === "passed" ? "bg-success" : "bg-info"
                            )}
                            style={{ width: `${progress}%` }}
                          />
                        </div>
                      </div>
                      {runStatus !== "running" && onDismissRun && (
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          onClick={() => onDismissRun(run.id)}
                          className="h-8 gap-1 text-xs"
                          title="Dismiss this completed pipeline run"
                        >
                          <X className="h-3.5 w-3.5" />
                          Dismiss
                        </Button>
                      )}
                    </div>
                  </div>

                  <div className="overflow-x-auto pb-2">
                    <div className="flex min-w-max items-stretch gap-3">
                      {run.stages.map((stage, index) => {
                        const stageStatus = normalizeStatus(stage.status);
                        const stageColors = getStatusColor(stageStatus);
                        const isLast = index === run.stages.length - 1;

                        return (
                          <div key={stage.id} className="flex items-center">
                            <div className={cn("w-[150px] rounded-lg border p-3", stageColors.border, stageStatus === "pending" ? "bg-muted/40" : "bg-card")}>
                              <div className="mb-2 flex items-center justify-between">
                                <span className={cn("flex items-center", stageColors.text)}>
                                  {getStatusIcon(stageStatus)}
                                </span>
                                <span className="text-[11px] text-muted-foreground">#{index + 1}</span>
                              </div>
                              <p className="text-xs font-semibold leading-tight">{stage.name}</p>
                              <p className="mt-2 min-h-[28px] text-[11px] leading-snug text-muted-foreground">
                                {stage.detail || stageStatus}
                              </p>
                            </div>
                            {!isLast && <div className={cn("mx-1 h-[2px] w-6", stageColors.connector)} />}
                          </div>
                        );
                      })}
                    </div>
                  </div>

                  {run.message && (
                    <div className={cn(
                      "mt-3 rounded-md px-3 py-2 text-xs",
                      runStatus === "failed" ? "bg-error/10 text-error" : "bg-success/10 text-success"
                    )}>
                      {run.message}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </GlassCard>
      )}

      {/* Feature Selection */}
      <GlassCard className="p-6">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div>
            <h4 className="text-sm font-semibold text-muted-foreground">Select a Feature</h4>
            <p className="mt-1 text-xs text-muted-foreground">
              {filteredFeatures.length} of {features.length} features
            </p>
          </div>
          <div className="relative w-full sm:w-[320px]">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <input
              type="search"
              value={featureSearch}
              onChange={(event) => setFeatureSearch(event.target.value)}
              placeholder="Search feature..."
              className="h-10 w-full rounded-lg border border-border bg-background/70 pl-9 pr-3 text-sm outline-none transition-colors placeholder:text-muted-foreground focus:border-primary"
            />
          </div>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
          {visibleFeatures.map((feature) => {
            const colors = getStatusColor(feature.status);
            const isSelected = expandedFeature === feature.id;
            const displayStatus = normalizeStatus(feature.status);

            return (
              <button
                key={feature.id}
                onClick={() => toggleFeature(feature.id)}
                className={cn(
                  "p-4 rounded-xl border-2 transition-all duration-300 text-left",
                  isSelected
                    ? "border-primary bg-primary/10 shadow-[0_0_20px_rgba(124,58,237,0.4)] scale-105"
                    : "border-border bg-card/50 hover:border-primary/50 hover:bg-accent/30"
                )}
              >
                <div className="flex items-center gap-3 mb-3">
                  <div className={cn("w-12 h-12 rounded-full flex items-center justify-center", colors.bg, colors.text)}>
                    {getStatusIcon(feature.status)}
                  </div>
                  <div className="flex-1">
                    <p className="font-semibold text-sm">{feature.name}</p>
                    <p className="text-xs text-muted-foreground">
                      {feature.passedScenarios}/{feature.totalScenarios} passed
                    </p>
                  </div>
                </div>
                <div className="flex items-center justify-between">
                  <span
                    className={cn(
                      "px-2 py-0.5 rounded-full text-xs font-medium",
                      displayStatus === "passed" && "bg-success/20 text-success",
                      displayStatus === "failed" && "bg-error/20 text-error",
                      displayStatus === "running" && "bg-info/20 text-info",
                      displayStatus === "pending" && "bg-muted text-muted-foreground"
                    )}
                  >
                    {displayStatus.toUpperCase()}
                  </span>
                  {feature.duration && (
                    <span className="text-xs text-muted-foreground">{feature.duration}</span>
                  )}
                </div>
              </button>
            );
          })}
        </div>
        {filteredFeatures.length === 0 && (
          <div className="rounded-lg border border-dashed border-border bg-muted/30 p-6 text-center text-sm text-muted-foreground">
            No feature matches this search.
          </div>
        )}
        {filteredFeatures.length > featuresPerPage && (
          <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-border pt-4">
            <span className="text-xs text-muted-foreground">
              Page {currentFeaturePage} of {totalFeaturePages}
            </span>
            <div className="flex items-center gap-2">
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => setFeaturePage((page) => Math.max(1, page - 1))}
                disabled={currentFeaturePage <= 1}
                className="h-9 gap-1"
                title="Previous feature page"
              >
                <ChevronLeft className="h-4 w-4" />
                Previous
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => setFeaturePage((page) => Math.min(totalFeaturePages, page + 1))}
                disabled={currentFeaturePage >= totalFeaturePages}
                className="h-9 gap-1"
                title="Next feature page"
              >
                Next
                <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
          </div>
        )}
      </GlassCard>

      {/* Scenario Pipeline for Selected Feature */}
      {expandedFeature && selectedFeature && (
        <GlassCard className="p-6">
          {/* Pipeline Header */}
          <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
            <div>
              <h3 className="text-xl font-bold bg-gradient-to-r from-primary to-accent bg-clip-text text-transparent">
                {selectedFeature.name} Pipeline
              </h3>
              <p className="text-sm text-muted-foreground mt-1">
                {selectedFeature.scenarios.length} scenarios • {selectedFeature.duration}
              </p>
            </div>
            <div className="flex flex-wrap items-center gap-3">
              <span className="text-xs text-muted-foreground">
                {selectedFeature.passedScenarios}/{selectedFeature.totalScenarios} passed
              </span>
              {onRunFeature && (
                <button
                  type="button"
                  onClick={() => onRunFeature(selectedFeature)}
                  disabled={runningFeatureIds.includes(selectedFeature.id)}
                  className={cn(
                    "group inline-flex h-11 items-center gap-2 rounded-full border border-primary/40 px-5 text-sm font-semibold text-white",
                    "bg-gradient-to-r from-primary via-[#8B5CF6] to-accent shadow-[0_0_24px_rgba(124,58,237,0.35)] transition-all duration-300",
                    "hover:-translate-y-0.5 hover:shadow-[0_0_34px_rgba(124,58,237,0.55)] disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:translate-y-0"
                  )}
                  title="Run this feature through the agent with visible Chrome when external Chrome debugging is available"
                >
                  {runningFeatureIds.includes(selectedFeature.id) ? (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  ) : (
                    <MonitorPlay className="h-4 w-4 transition-transform group-hover:scale-110" />
                  )}
                  <span>{runningFeatureIds.includes(selectedFeature.id) ? "Running..." : "Run in Chrome"}</span>
                  {!runningFeatureIds.includes(selectedFeature.id) && <Play className="h-3.5 w-3.5 fill-current" />}
                </button>
              )}
            </div>
          </div>

          {/* Horizontal Scenario Pipeline */}
          <div className="overflow-x-auto pb-4">
            <div className="flex items-center gap-4 min-w-max py-4 pl-4">
              {selectedFeature.scenarios.map((scenario, index, arr) => {
                const isLast = index === arr.length - 1;
                const isFirst = index === 0;
                const displayStatus = normalizeStatus(scenario.status);

                const stageColors = {
                  passed: {
                    bg: "bg-success/10",
                    border: "border-success",
                    text: "text-success",
                    icon: <CheckCircle2 className="w-5 h-5" />,
                    connector: "bg-success/30",
                  },
                  failed: {
                    bg: "bg-error/10",
                    border: "border-error",
                    text: "text-error",
                    icon: <XCircle className="w-5 h-5" />,
                    connector: "bg-error/30",
                  },
                  running: {
                    bg: "bg-info/10",
                    border: "border-info",
                    text: "text-info",
                    icon: <Loader2 className="w-5 h-5 animate-spin" />,
                    connector: "bg-info/30",
                  },
                  pending: {
                    bg: "bg-muted",
                    border: "border-border",
                    text: "text-muted-foreground",
                    icon: <Clock className="w-5 h-5" />,
                    connector: "bg-border",
                  },
                };

                const colors = stageColors[displayStatus];

                return (
                  <div key={scenario.id} className="flex items-center">
                    {/* Stage Box */}
                    <button
                      onClick={() => onScenarioClick(expandedFeature, scenario.id)}
                      className="group relative"
                    >
                      <div
                        className={cn(
                          "relative w-[140px] rounded-lg border-2 p-3 transition-all duration-200",
                          colors.bg,
                          colors.border,
                          "hover:shadow-lg hover:-translate-y-1",
                          "cursor-pointer bg-card"
                        )}
                      >
                        {/* Stage Number */}
                        <div className="absolute -top-2 -left-2 w-6 h-6 rounded-full bg-primary text-white flex items-center justify-center text-xs font-bold">
                          {index + 1}
                        </div>

                        {/* Status Icon and Duration */}
                        <div className="flex items-center justify-between mb-2">
                          <div className={cn("flex items-center justify-center", colors.text)}>
                            {colors.icon}
                          </div>
                          {scenario.duration && (
                            <span className="text-xs text-muted-foreground">
                              {scenario.duration}
                            </span>
                          )}
                        </div>

                        {/* Stage Name */}
                        <h4 className="text-xs font-medium line-clamp-2 min-h-[32px]">
                          {scenario.name}
                        </h4>

                        {/* Status Label */}
                        <div className="mt-2">
                          <span className={cn("text-xs font-medium uppercase", colors.text)}>
                            {displayStatus}
                          </span>
                        </div>
                      </div>
                    </button>

                    {/* Connector */}
                    {!isLast && (
                      <div className={cn("w-8 h-[2px] mx-1", colors.connector)} />
                    )}
                  </div>
                );
              })}
            </div>
          </div>

          {/* Pipeline Summary */}
          <div className="mt-4 pt-4 border-t border-border flex items-center justify-between text-sm">
            <div className="flex items-center gap-4">
              <div className="flex items-center gap-1.5">
                <div className="w-2 h-2 rounded-full bg-success" />
                <span className="text-muted-foreground">
                  {selectedFeature.passedScenarios} passed
                </span>
              </div>
              <div className="flex items-center gap-1.5">
                <div className="w-2 h-2 rounded-full bg-error" />
                <span className="text-muted-foreground">
                  {selectedFeature.failedScenarios} failed
                </span>
              </div>
              <div className="flex items-center gap-1.5">
                <div className="w-2 h-2 rounded-full bg-muted-foreground" />
                <span className="text-muted-foreground">
                  {selectedFeature.totalScenarios - selectedFeature.passedScenarios - selectedFeature.failedScenarios} pending
                </span>
              </div>
            </div>
            <span className="text-muted-foreground text-xs">
              Click any scenario for details
            </span>
          </div>
        </GlassCard>
      )}
    </div>
  );
}
