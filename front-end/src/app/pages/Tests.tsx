import { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router";
import { TopBar } from "../components/custom/TopBar";
import { ScenarioDetails, type ScenarioDetail } from "../components/custom/ScenarioDetails";
import { IgnoreModal } from "../components/custom/IgnoreModal";
import { StatusBadge } from "../components/custom/StatusBadge";
import { toast } from "sonner";
import { Toaster } from "../components/ui/sonner";
import { GlassCard } from "../components/custom/GlassCard";
import { FeaturePipeline, type Feature, type PipelineRun, type PipelineStage } from "../components/custom/FeaturePipeline";
import { FeatureBuilder } from "../components/e2e/FeatureBuilder";
import { ArrowDown, ArrowUp, Copy, Download, KeyRound, Pencil, Plus, Save, ShieldCheck, Trash2, X } from "lucide-react";
import { api } from "../utils/api";
import { getFeatures, getFeatureWithHierarchy, getFeatureExecutions, updateFeature, deleteFeature, exportFeatureGherkin, createScenario, updateScenario, deleteScenario, createStep, updateStep, deleteStep, runFeatureAgentPipeline, listProjectSecrets, saveProjectSecret, type Feature as ApiFeature, type FeatureWithHierarchy } from "../utils/e2eApi";
import { agentApi } from "../utils/agentApi";
import { cn } from "../components/ui/utils";

const normalizeTestStatus = (status?: string) => {
  switch (status?.toLowerCase()) {
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

const formatPipelineError = (value: unknown, fallback = "Agent run failed") => {
  if (!value) return fallback;
  if (typeof value === "string") return value;
  if (value instanceof Error) return value.message;
  if (typeof value === "object") {
    const record = value as Record<string, unknown>;
    const detail = record.message ?? record.error ?? record.detail ?? record.stderr ?? record.stdout;
    if (typeof detail === "string" && detail.trim()) return detail;
    if (detail && typeof detail === "object") return formatPipelineError(detail, fallback);
    try {
      return JSON.stringify(value, null, 2);
    } catch {
      return fallback;
    }
  }
  return String(value);
};

const getRunFailureMessage = (runResult: unknown) => {
  if (!runResult || typeof runResult !== "object") return formatPipelineError(runResult);
  const record = runResult as Record<string, unknown>;
  return formatPipelineError(record.message ?? record.error ?? record, "Agent run failed");
};

const isRunFailure = (runResult: Record<string, any> | undefined | null) => {
  if (!runResult) return true;
  const status = String(runResult.status ?? runResult.execution_status ?? runResult.executionResult?.status ?? "").toLowerCase();
  return runResult.success === false || ["failed", "error"].includes(status);
};

type EditableStep = {
  id?: string;
  type: string;
  text: string;
  sequenceOrder: number;
};

type EditableScenario = {
  id?: string;
  name: string;
  description?: string;
  sequenceOrder: number;
  steps: EditableStep[];
};

type EditableFeature = {
  id: string;
  name: string;
  description?: string;
  gherkinContent?: string;
  targetMode?: "PROJECT" | "EXTERNAL";
  projectId?: string;
  defaultPageId?: string;
  scenarios: EditableScenario[];
};

type ProjectOption = {
  id: string;
  name: string;
  baseUrl?: string;
};

const emptyEditableStep = (sequenceOrder: number): EditableStep => ({
  type: "Then",
  text: "",
  sequenceOrder,
});

// All scenarios flat list
const allScenarios = [
  { id: "scenario-1", name: "Valid credentials login", status: "passed", feature: "Login & Authentication" },
  { id: "scenario-2", name: "Invalid password attempt", status: "passed", feature: "Login & Authentication" },
  { id: "scenario-3", name: "Empty fields validation", status: "passed", feature: "Login & Authentication" },
  { id: "scenario-4", name: "Remember me functionality", status: "passed", feature: "Login & Authentication" },
  { id: "scenario-5", name: "Logout functionality", status: "passed", feature: "Login & Authentication" },
  { id: "scenario-6", name: "Widget loading", status: "passed", feature: "Dashboard UI" },
  { id: "scenario-7", name: "Chart rendering", status: "failed", feature: "Dashboard UI" },
  { id: "scenario-8", name: "Data refresh", status: "passed", feature: "Dashboard UI" },
  { id: "scenario-9", name: "Responsive layout", status: "failed", feature: "Dashboard UI" },
  { id: "scenario-10", name: "Dark theme display", status: "passed", feature: "Dashboard UI" },
  { id: "scenario-11", name: "Navigation menu", status: "passed", feature: "Dashboard UI" },
  { id: "scenario-12", name: "Required fields check", status: "passed", feature: "Form Validation" },
  { id: "scenario-13", name: "Email format validation", status: "passed", feature: "Form Validation" },
  { id: "scenario-14", name: "Password strength check", status: "passed", feature: "Form Validation" },
  { id: "scenario-15", name: "Submit button state", status: "passed", feature: "Form Validation" },
  { id: "scenario-16", name: "View profile information", status: "passed", feature: "User Profile" },
  { id: "scenario-17", name: "Edit profile details", status: "passed", feature: "User Profile" },
  { id: "scenario-18", name: "Upload profile picture", status: "passed", feature: "User Profile" },
  { id: "scenario-19", name: "Change password", status: "running", feature: "User Profile" },
  { id: "scenario-20", name: "Delete account", status: "pending", feature: "User Profile" },
  { id: "scenario-21", name: "Notification preferences", status: "pending", feature: "Settings" },
  { id: "scenario-22", name: "Privacy settings", status: "pending", feature: "Settings" },
  { id: "scenario-23", name: "Theme selection", status: "pending", feature: "Settings" },
];

// Detailed scenario data
const scenarioDetails: Record<string, ScenarioDetail> = {
  "scenario-1": {
    id: "scenario-1",
    name: "Valid credentials login",
    status: "completed",
    steps: [
      {
        id: "step-1",
        keyword: "Given",
        text: "I am on the login page",
        status: "completed",
        duration: "0.2s",
      },
      {
        id: "step-2",
        keyword: "When",
        text: "I enter valid email \"user@example.com\"",
        status: "completed",
        duration: "0.3s",
      },
      {
        id: "step-3",
        keyword: "And",
        text: "I enter valid password \"SecurePass123!\"",
        status: "completed",
        duration: "0.3s",
      },
      {
        id: "step-4",
        keyword: "And",
        text: "I click the login button",
        status: "completed",
        duration: "0.2s",
      },
      {
        id: "step-5",
        keyword: "Then",
        text: "I should be redirected to the dashboard",
        status: "completed",
        duration: "1.1s",
      },
    ],
    logs: "✓ Page loaded successfully\n✓ Username field found and populated\n✓ Password field found and populated\n✓ Login button clicked\n✓ Authentication successful\n✓ Redirected to /dashboard",
    screenshots: [],
  },
  "scenario-7": {
    id: "scenario-7",
    name: "Chart rendering",
    status: "error",
    steps: [
      {
        id: "step-6",
        keyword: "Given",
        text: "I am on the dashboard page",
        status: "completed",
        duration: "0.8s",
      },
      {
        id: "step-7",
        keyword: "When",
        text: "The chart component loads",
        status: "completed",
        duration: "1.2s",
      },
      {
        id: "step-8",
        keyword: "Then",
        text: "The chart should display correct data points",
        status: "error",
        duration: "1.5s",
        error: "Expected 12 data points but found 10. Missing values for March and April.",
      },
    ],
    logs: "✓ Dashboard page loaded\n✓ Chart component initialized\n✗ Data validation failed\n  Expected: 12 data points\n  Received: 10 data points\n  Missing: March, April",
    screenshots: [
      { id: 1, name: "chart-error-state.png", timestamp: "14:25:33" },
      { id: 2, name: "console-data-mismatch.png", timestamp: "14:25:35" },
    ],
  },
  "scenario-9": {
    id: "scenario-9",
    name: "Responsive layout",
    status: "error",
    steps: [
      {
        id: "step-9",
        keyword: "Given",
        text: "I am on the dashboard page",
        status: "completed",
        duration: "0.5s",
      },
      {
        id: "step-10",
        keyword: "When",
        text: "I resize the browser to mobile width (375px)",
        status: "completed",
        duration: "0.3s",
      },
      {
        id: "step-11",
        keyword: "Then",
        text: "The sidebar should collapse to hamburger menu",
        status: "error",
        duration: "2.0s",
        error: "Sidebar did not collapse. Expected display:none or transform, but sidebar remained visible.",
      },
    ],
    logs: "✓ Page loaded\n✓ Viewport resized to 375px\n✗ Responsive behavior failed\n  Sidebar element still visible (display: block)\n  Expected: Hamburger menu (display: none for sidebar)",
    screenshots: [
      { id: 3, name: "mobile-layout-issue.png", timestamp: "14:28:12" },
    ],
  },
  "scenario-19": {
    id: "scenario-19",
    name: "Change password",
    status: "running",
    steps: [
      {
        id: "step-12",
        keyword: "Given",
        text: "I am on my profile settings page",
        status: "completed",
        duration: "0.6s",
      },
      {
        id: "step-13",
        keyword: "When",
        text: "I click on \"Change Password\" button",
        status: "completed",
        duration: "0.2s",
      },
      {
        id: "step-14",
        keyword: "And",
        text: "I enter current password",
        status: "completed",
        duration: "0.4s",
      },
      {
        id: "step-15",
        keyword: "And",
        text: "I enter new password twice",
        status: "running",
        duration: "...",
      },
      {
        id: "step-16",
        keyword: "Then",
        text: "My password should be updated successfully",
        status: "pending",
        duration: "-",
      },
    ],
    logs: "✓ Profile settings loaded\n✓ Change password modal opened\n✓ Current password validated\n→ Entering new password...",
    screenshots: [],
  },
};

// Group scenarios into features
// Removed mock data array

export function Tests() {
  const navigate = useNavigate();
  const [selectedScenario, setSelectedScenario] = useState<ScenarioDetail | null>(null);
  const [ignoreModalOpen, setIgnoreModalOpen] = useState(false);
  const [activeTab, setActiveTab] = useState<"features" | "pipeline" | "builder" | "secrets">("features");
  const [featureGroups, setFeatureGroups] = useState<Feature[]>([]);
  const [pipelineRuns, setPipelineRuns] = useState<PipelineRun[]>([]);
  const [loading, setLoading] = useState(true);
  const [projects, setProjects] = useState<ProjectOption[]>([]);
  const [secretProjectId, setSecretProjectId] = useState("");
  const [secretAliases, setSecretAliases] = useState<string[]>([]);
  const [secretAlias, setSecretAlias] = useState("");
  const [secretValue, setSecretValue] = useState("");
  const [savingSecret, setSavingSecret] = useState(false);
  const [loadingSecrets, setLoadingSecrets] = useState(false);
  const [editingFeature, setEditingFeature] = useState<EditableFeature | null>(null);
  const [removedScenarioIds, setRemovedScenarioIds] = useState<string[]>([]);
  const [removedStepIds, setRemovedStepIds] = useState<string[]>([]);
  const [savingEdit, setSavingEdit] = useState(false);
  const [deletingFeatureId, setDeletingFeatureId] = useState<string | null>(null);
  const runningFeatureIdsRef = useRef<Set<string>>(new Set());
  const recentFeatureRunAtRef = useRef<Map<string, number>>(new Map());
  const featureRunReplayWindowMs = 30_000;

  const createPipelineStages = (): PipelineStage[] => [
    { id: "feature", name: "Create feature", status: "pending", detail: "Waiting for save" },
    { id: "scenarios", name: "Create scenarios", status: "pending", detail: "Waiting for feature ID" },
    { id: "steps", name: "Create steps", status: "pending", detail: "Waiting for scenarios" },
    { id: "agent", name: "Start agent", status: "pending", detail: "Waiting for Gherkin export" },
    { id: "playwright", name: "Execute Playwright", status: "pending", detail: "Waiting for agent" },
    { id: "result", name: "Publish result", status: "pending", detail: "Waiting for execution" },
  ];

  const createManualRunStages = (): PipelineStage[] => [
    { id: "feature", name: "Load feature", status: "pending", detail: "Waiting for saved feature" },
    { id: "agent", name: "Start agent", status: "pending", detail: "Waiting for gateway request" },
    { id: "playwright", name: "Open Chrome", status: "pending", detail: "Waiting for Playwright" },
    { id: "result", name: "Publish result", status: "pending", detail: "Waiting for execution" },
  ];

  const nowLabel = () => new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });

  const toScenarioDetailStatus = (status?: string): ScenarioDetail["status"] => {
    switch (normalizeTestStatus(status)) {
      case "passed":
        return "completed";
      case "failed":
        return "error";
      case "running":
        return "running";
      default:
        return "pending";
    }
  };

  const buildRunLog = (runResult: Record<string, any>) => {
    const parts = [
      runResult?.message,
      runResult?.error?.message,
      runResult?.error?.stderr,
      runResult?.error?.stdout,
      runResult?.stderr,
      runResult?.stdout,
      Array.isArray(runResult?.attempted_agent_urls) ? `Attempted agent URLs: ${runResult.attempted_agent_urls.join(", ")}` : "",
    ].filter(Boolean);
    return parts.length ? parts.join("\n") : getRunFailureMessage(runResult);
  };

  const extractFailureScreenshot = (runResult: Record<string, any>) => {
    const url =
      runResult?.error?.screenshot ||
      runResult?.error_screenshot_minio_link ||
      runResult?.screenshot ||
      runResult?.local_screenshot_path ||
      runResult?.execution_result?.local_screenshot_path;
    if (!url) return [];
    return [
      {
        id: `failure-${Date.now()}`,
        name: "Failed step screenshot",
        timestamp: nowLabel(),
        url,
      },
    ];
  };

  const applyRunResultToFeature = (featureId: string, runResult: Record<string, any>) => {
    const failedScenarioName = runResult?.stopped_at?.scenario;
    const failedStepIndex = Number(runResult?.stopped_at?.step_index || 0);
    const stepsExecuted = Number(runResult?.steps_executed || (failedStepIndex > 0 ? failedStepIndex : 0));
    const fallbackFailedStepIndex = failedStepIndex || Math.max(1, stepsExecuted || 1);
    const failedLog = buildRunLog(runResult);
    const screenshots = extractFailureScreenshot(runResult);

    setFeatureGroups((groups) =>
      groups.map((feature) => {
        if (feature.id !== featureId) return feature;

        if (!failedScenarioName && stepsExecuted === 0) {
          const runSetupScenario = {
            id: `${feature.id}-run-setup-failure`,
            featureId: feature.id,
            featureName: feature.name,
            name: "Run setup / agent startup",
            status: "failed",
            steps: [
              {
                id: `${feature.id}-run-setup-step`,
                keyword: "Given",
                text: "Start the E2E agent pipeline",
                status: "failed",
                duration: "-",
                error: getRunFailureMessage(runResult),
              },
            ],
            logs: failedLog,
            screenshots,
          };
          const scenarios = [
            runSetupScenario,
            ...feature.scenarios.map((scenario) => ({
              ...scenario,
              status: "pending",
              steps: ((scenario as any).steps || []).map((step: any) => ({
                ...step,
                status: "pending",
                error: undefined,
              })),
            })),
          ];
          return { ...feature, status: "failed", scenarios, passedScenarios: 0, failedScenarios: 1, totalScenarios: scenarios.length };
        }

        let seenSteps = 0;
        let failureAlreadyAssigned = false;
        const failedScenarioIndex = failedScenarioName
          ? feature.scenarios.findIndex((scenario) => scenario.name === failedScenarioName)
          : -1;
        const scenarios = feature.scenarios.map((scenario) => {
          const scenarioSteps = (scenario as any).steps || [];
          const firstScenarioStep = seenSteps + 1;
          const lastScenarioStep = seenSteps + scenarioSteps.length;
          const isNamedFailure = failedScenarioName && scenario.name === failedScenarioName;
          const isIndexedFailure = !failedScenarioName && fallbackFailedStepIndex >= firstScenarioStep && fallbackFailedStepIndex <= Math.max(lastScenarioStep, firstScenarioStep);
          const isFallbackFailure = !failedScenarioName && !failureAlreadyAssigned && stepsExecuted <= 1 && firstScenarioStep === 1;
          const isFailedScenario = Boolean(isNamedFailure || isIndexedFailure || isFallbackFailure);
          const scenarioOrder = feature.scenarios.findIndex((item) => item.id === scenario.id);
          const isBeforeNamedFailure = failedScenarioIndex >= 0 && scenarioOrder >= 0 && scenarioOrder < failedScenarioIndex;
          const isPassedBeforeFailure =
            !isFailedScenario &&
            (
              isBeforeNamedFailure ||
              (stepsExecuted > 0 && lastScenarioStep > 0 && lastScenarioStep < stepsExecuted)
            );
          const scenarioFailedStepIndex = isFailedScenario
            ? Math.max(1, failedScenarioName ? fallbackFailedStepIndex : fallbackFailedStepIndex - firstScenarioStep + 1)
            : 0;

          const steps = scenarioSteps.map((step: any, index: number) => {
            seenSteps += 1;
            const localStepIndex = index + 1;
            const isFailedStep = isFailedScenario && scenarioFailedStepIndex === localStepIndex;
            return {
              ...step,
              status: isFailedStep
                ? "failed"
                : isPassedBeforeFailure || (isFailedScenario && localStepIndex < scenarioFailedStepIndex)
                  ? "passed"
                  : isFailedScenario
                    ? "pending"
                    : normalizeTestStatus(step.status) === "passed"
                      ? "passed"
                      : "pending",
              error: isFailedStep ? getRunFailureMessage(runResult) : step.error,
            };
          }) || [];

          if (!isFailedScenario) {
            return {
              ...scenario,
              status: isPassedBeforeFailure || normalizeTestStatus(scenario.status) === "passed" ? "passed" : "pending",
              steps,
            };
          }
          failureAlreadyAssigned = true;
          return {
            ...scenario,
            status: "failed",
            steps,
            logs: failedLog,
            screenshots,
          };
        });

        const passedScenarios = scenarios.filter((scenario) => scenario.status === "passed").length;
        const failedScenarios = scenarios.filter((scenario) => scenario.status === "failed").length;
        return { ...feature, status: "failed", scenarios, passedScenarios, failedScenarios };
      })
    );

    if (!failedScenarioName && stepsExecuted === 0) {
      setSelectedScenario((current) => {
        if (!current || current.featureId !== featureId) return current;
        return {
          id: `${featureId}-run-setup-failure`,
          featureId,
          featureName: current.featureName,
          name: "Run setup / agent startup",
          status: "error",
          steps: [
            {
              id: `${featureId}-run-setup-step`,
              keyword: "Given",
              text: "Start the E2E agent pipeline",
              status: "error",
              duration: "-",
              error: getRunFailureMessage(runResult),
            },
          ],
          logs: failedLog,
          screenshots,
        };
      });
      return;
    }

    if (selectedScenario && (!failedScenarioName || selectedScenario.name === failedScenarioName)) {
      setSelectedScenario((current) => {
        if (!current) return current;
        return {
          ...current,
          status: "error",
          logs: failedLog,
          screenshots,
          steps: current.steps.map((step, index) => ({
            ...step,
            status: fallbackFailedStepIndex === index + 1 ? "error" : index + 1 < stepsExecuted ? "completed" : step.status,
            error: fallbackFailedStepIndex === index + 1 ? getRunFailureMessage(runResult) : step.error,
          })),
        };
      });
    }
  };

  const runningFeatureIds = pipelineRuns
    .filter((run) => run.status === "running" && run.featureId)
    .map((run) => run.featureId as string);

  const updatePipelineRun = (
    runId: string,
    patch: Partial<PipelineRun>,
    stagePatch?: { id: string; status: PipelineStage["status"]; detail?: string }
  ) => {
    setPipelineRuns((runs) =>
      runs.map((run) => {
        if (run.id !== runId) {
          return run;
        }

        const stages = stagePatch
          ? run.stages.map((stage) =>
              stage.id === stagePatch.id
                ? { ...stage, status: stagePatch.status, detail: stagePatch.detail ?? stage.detail }
                : stage
            )
          : run.stages;

        return { ...run, ...patch, stages };
      })
    );
  };

  const dismissPipelineRun = (runId: string) => {
    setPipelineRuns((runs) => runs.filter((run) => run.id !== runId));
  };

  const normalizeSecretAlias = (value: string) =>
    value.trim().toLowerCase().replace(/[^a-z0-9_-]/g, "-").replace(/-+/g, "-").replace(/^-|-$/g, "");

  const handleSaveSecret = async () => {
    const normalizedAlias = normalizeSecretAlias(secretAlias);
    if (!secretProjectId) {
      toast.error("Select a project before saving a secret.");
      return;
    }
    if (!normalizedAlias) {
      toast.error("Secret alias is required.");
      return;
    }
    if (!secretValue) {
      toast.error("Secret value is required.");
      return;
    }
    try {
      setSavingSecret(true);
      const saved = await saveProjectSecret(secretProjectId, normalizedAlias, secretValue);
      setSecretValue("");
      setSecretAlias(normalizedAlias);
      setSecretAliases((aliases) => Array.from(new Set([...aliases, saved.alias || normalizedAlias])).sort());
      toast.success("Secret stored in Vault", {
        description: `Use \${SECRET.${saved.alias || normalizedAlias}} in feature steps.`,
      });
    } catch (error) {
      toast.error("Secret save failed", {
        description: error instanceof Error ? error.message : "Could not store the secret in Vault.",
      });
    } finally {
      setSavingSecret(false);
    }
  };

  const copySecretPlaceholder = async (alias: string) => {
    const placeholder = `\${SECRET.${alias}}`;
    try {
      await navigator.clipboard.writeText(placeholder);
      toast.success("Secret placeholder copied", { description: placeholder });
    } catch {
      toast.message(placeholder);
    }
  };

  const fetchFeaturesData = async () => {
    try {
      setLoading(true);
      const data = await getFeatures();
      // e2e API returns a page object
      const apiFeatures: ApiFeature[] = data.content || [];
      
      const featuresWithHierarchy = await Promise.all(
        apiFeatures.map(async (f) => {
          try {
            const [fullFeature, executions] = await Promise.all([
              getFeatureWithHierarchy(f.id),
              getFeatureExecutions(f.id).catch(() => []),
            ]);
            const sortedExecutions = [...executions].sort((a, b) => {
              const left = new Date(a.createdAt || 0).getTime();
              const right = new Date(b.createdAt || 0).getTime();
              return right - left;
            });
            const featureStatus = normalizeTestStatus(f.status);
            const featureExecution = sortedExecutions.find((item) => !item.scenarioId);
            let featureScreenshots: string[] = [];
            try {
              const parsed = JSON.parse(featureExecution?.screenshots || "[]");
              featureScreenshots = Array.isArray(parsed) ? parsed.filter(Boolean) : [];
            } catch {
              featureScreenshots = [];
            }
            const scenarios = (fullFeature.scenarios || []).map((s: any) => {
              const execution = sortedExecutions.find((item) => item.scenarioId === s.id);
              const scenarioStatus = execution
                ? normalizeTestStatus(execution.status)
                : featureStatus === "failed"
                  ? "pending"
                  : normalizeTestStatus(s.status);
              let screenshotUrls: string[] = [];
              try {
                const parsed = JSON.parse(execution?.screenshots || "[]");
                screenshotUrls = Array.isArray(parsed) ? parsed.filter(Boolean) : [];
              } catch {
                screenshotUrls = [];
              }
              return {
                id: s.id,
                featureId: f.id,
                featureName: f.name,
                projectId: f.projectId,
                executionId: execution?.id,
                name: s.name,
                status: scenarioStatus,
                steps: (s.steps || []).map((step: any) => ({
                  id: step.id,
                  keyword: step.type,
                  text: step.text,
                  status: normalizeTestStatus(step.status),
                  duration: "-"
                })),
                logs: execution?.outputLog || execution?.errorMessage || "No detailed logs available for this scenario yet.",
                screenshots: screenshotUrls.map((url, index) => ({
                  id: `${execution?.id || s.id}-${index}`,
                  name: url.includes("/search-results/") ? "Search result evidence" : "Failed step screenshot",
                  timestamp: execution?.createdAt ? new Date(execution.createdAt).toLocaleTimeString() : "",
                  url,
                }))
              };
            });
            const shouldShowFeatureExecution =
              featureExecution &&
              normalizeTestStatus(featureExecution.status) === "failed" &&
              featureStatus === "failed";
            const scenariosWithRunFailure = shouldShowFeatureExecution
              ? [
                  {
                    id: `${f.id}-run-setup-failure`,
                    featureId: f.id,
                    featureName: f.name,
                    projectId: f.projectId,
                    executionId: featureExecution.id,
                    name: "Run setup / agent startup",
                    status: "failed",
                    steps: [
                      {
                        id: `${featureExecution.id}-run-setup-step`,
                        keyword: "Given",
                        text: "Start the E2E agent pipeline",
                        status: "failed",
                        duration: "-",
                        error: featureExecution.errorMessage || "The agent run failed before a scenario step was executed.",
                      },
                    ],
                    logs: featureExecution.outputLog || featureExecution.errorMessage || "The agent run failed before a scenario step was executed.",
                    screenshots: featureScreenshots.map((url, index) => ({
                      id: `${featureExecution.id}-${index}`,
                      name: "Failure screenshot",
                      timestamp: featureExecution.createdAt ? new Date(featureExecution.createdAt).toLocaleTimeString() : "",
                      url,
                    })),
                  },
                  ...scenarios,
                ]
              : scenarios;
            const passedScenarios = scenariosWithRunFailure.filter((scenario) => scenario.status === "passed").length;
            const failedScenarios = scenariosWithRunFailure.filter((scenario) => scenario.status === "failed").length;
            return {
              id: f.id,
              name: f.name,
              status: normalizeTestStatus(f.status),
              duration: "-",
              scenarios: scenariosWithRunFailure,
              totalScenarios: scenariosWithRunFailure.length,
              passedScenarios,
              failedScenarios
            };
          } catch {
            return {
              id: f.id,
              name: f.name,
              status: normalizeTestStatus(f.status),
              duration: "-",
              scenarios: [],
              totalScenarios: 0,
              passedScenarios: 0,
              failedScenarios: 0
            };
          }
        })
      );
      setFeatureGroups(featuresWithHierarchy);
    } catch (err) {
      console.error(err);
      toast.error("Failed to load features", {
        description: err instanceof Error ? err.message : "Gateway/API is not reachable. Wait until the launcher says all services are ready, then refresh the app.",
        duration: 10000,
      });
    } finally {
      setLoading(false);
    }
  };

  const fetchProjectsForSecrets = async () => {
    try {
      const loadedProjects = await api.get<ProjectOption[]>("/api/v1/projects");
      setProjects(loadedProjects);
      setSecretProjectId((current) => current || loadedProjects[0]?.id || "");
    } catch (error) {
      toast.error("Failed to load projects for secrets", {
        description: error instanceof Error ? error.message : "Could not fetch projects.",
      });
    }
  };

  const fetchSecretAliases = async (projectId: string) => {
    if (!projectId) {
      setSecretAliases([]);
      return;
    }
    try {
      setLoadingSecrets(true);
      const result = await listProjectSecrets(projectId);
      setSecretAliases(result.aliases || []);
    } catch (error) {
      setSecretAliases([]);
      toast.error("Failed to load Vault secret aliases", {
        description: error instanceof Error ? error.message : "Could not read aliases from Vault.",
      });
    } finally {
      setLoadingSecrets(false);
    }
  };

  useEffect(() => {
    fetchFeaturesData();
    fetchProjectsForSecrets();
  }, []);

  useEffect(() => {
    fetchSecretAliases(secretProjectId);
  }, [secretProjectId]);

  const setFeatureExecutionStatus = (featureId: string, status: Feature["status"]) => {
    setFeatureGroups((groups) =>
      groups.map((feature) =>
        feature.id === featureId
          ? {
              ...feature,
              status,
              scenarios: feature.scenarios.map((scenario, index) => ({
                ...scenario,
                status:
                  status === "passed"
                    ? "passed"
                    : status === "running"
                      ? index === 0
                        ? "running"
                        : normalizeTestStatus(scenario.status) === "passed"
                          ? "passed"
                          : "pending"
                      : normalizeTestStatus(scenario.status),
              })),
              passedScenarios: status === "passed" ? feature.scenarios.length : feature.passedScenarios,
              failedScenarios: status === "passed" ? 0 : feature.failedScenarios,
            }
          : feature
      )
    );
  };

  const handleRunFeature = async (feature: Feature) => {
    const now = Date.now();
    const recentRunAt = recentFeatureRunAtRef.current.get(feature.id);
    if (recentRunAt && now - recentRunAt < featureRunReplayWindowMs) {
      toast.info("Recent run ignored", {
        description: "This feature just ran. Wait a few seconds before launching it again.",
        duration: 5000,
      });
      return;
    }
    if (runningFeatureIdsRef.current.has(feature.id)) {
      toast.info("This feature is already running", {
        description: "Wait for the current Chrome execution to finish before starting it again.",
        duration: 5000,
      });
      return;
    }
    runningFeatureIdsRef.current.add(feature.id);
    recentFeatureRunAtRef.current.set(feature.id, now);
    const runId = `run-${Date.now()}`;
    setActiveTab("pipeline");
    setFeatureExecutionStatus(feature.id, "running");
    setPipelineRuns((runs) => [
      {
        id: runId,
        featureId: feature.id,
        featureName: feature.name,
        status: "running",
        startedAt: nowLabel(),
        stages: createManualRunStages(),
      },
      ...runs,
    ]);

    try {
      toast.loading("Starting feature run in Chrome...");
      updatePipelineRun(runId, { status: "running" }, { id: "feature", status: "running", detail: "Loading saved feature" });
      const fullFeature = await getFeatureWithHierarchy(feature.id);
      updatePipelineRun(
        runId,
        { status: "running" },
        { id: "feature", status: "passed", detail: `${fullFeature.scenarios?.length ?? 0} scenario(s) loaded` }
      );

      updatePipelineRun(runId, { status: "running" }, { id: "agent", status: "running", detail: "POST /api/v1/features/{id}/run-agent" });
      updatePipelineRun(runId, { status: "running" }, { id: "playwright", status: "running", detail: "Playwright is opening visible Chrome when CDP is available" });
      const runResult = await runFeatureAgentPipeline(feature.id);

      toast.dismiss();
      if (runResult?.duplicate_run) {
        setPipelineRuns((runs) => runs.filter((run) => run.id !== runId));
        toast.info("Run already in progress", {
          description: runResult.message || "This feature already has an active execution.",
          duration: 6000,
        });
        return;
      }
      if (isRunFailure(runResult)) {
        const failureMessage = getRunFailureMessage(runResult);
        applyRunResultToFeature(feature.id, runResult);
        updatePipelineRun(runId, { status: "running" }, { id: "agent", status: "passed", detail: "Agent returned a failed execution result" });
        updatePipelineRun(runId, { status: "failed", finishedAt: nowLabel(), message: failureMessage }, { id: "playwright", status: "failed", detail: "Agent execution failed" });
        updatePipelineRun(runId, { status: "failed" }, { id: "result", status: "failed", detail: "Failed result published" });
        const toastId = toast.warning("Agent run failed", {
          description: failureMessage,
          duration: 10000,
          action: {
            label: "Dismiss",
            onClick: () => toast.dismiss(toastId),
          },
        });
        // Keep the detailed in-memory scenario/step result. The persisted feature status is coarse
        // and can mark every scenario as failed, which hides the actual failed scenario.
      } else {
        setFeatureExecutionStatus(feature.id, "passed");
        updatePipelineRun(runId, { status: "running" }, { id: "agent", status: "passed", detail: "Agent accepted the run" });
        updatePipelineRun(
          runId,
          { status: "running" },
          { id: "playwright", status: "passed", detail: `${runResult?.steps_executed ?? "All"} step(s) executed in browser` }
        );
        updatePipelineRun(runId, { status: "passed", finishedAt: nowLabel(), message: "Feature pipeline completed successfully" }, { id: "result", status: "passed", detail: "Passed result published" });
        const toastId = toast.success("Feature run completed", {
          description: `"${feature.name}" finished through the gateway agent pipeline.`,
          duration: 8000,
          action: {
            label: "Dismiss",
            onClick: () => toast.dismiss(toastId),
          },
        });
        await fetchFeaturesData();
        setFeatureExecutionStatus(feature.id, "passed");
      }
    } catch (err) {
      toast.dismiss();
      applyRunResultToFeature(feature.id, {
        success: false,
        message: err instanceof Error ? err.message : "Pipeline failed",
        steps_executed: 0,
      });
      updatePipelineRun(runId, { status: "failed", finishedAt: nowLabel(), message: err instanceof Error ? err.message : "Pipeline failed" }, { id: "result", status: "failed", detail: "Gateway request failed" });
      const toastId = toast.error("Failed to run feature", {
        description: err instanceof Error ? err.message : "Gateway request failed",
        duration: 10000,
        action: {
          label: "Dismiss",
          onClick: () => toast.dismiss(toastId),
        },
      });
      // Do not reload coarse persisted statuses here; gateway/agent reachability errors
      // do not identify a user scenario step and should not repaint every scenario as failed.
    } finally {
      runningFeatureIdsRef.current.delete(feature.id);
      recentFeatureRunAtRef.current.set(feature.id, Date.now());
    }
  };

  const handleSaveFeature = async (savedFeature: any) => {
    try {
      const toastId = toast.success("Feature saved", {
        description: `Select "${savedFeature.name}" in Pipeline and click Run in Chrome to execute it.`,
        duration: 8000,
        action: {
          label: "Dismiss",
          onClick: () => toast.dismiss(toastId),
        },
      });
      await fetchFeaturesData();
      setActiveTab("pipeline");
    } catch (err) {
      toast.dismiss();
      const toastId = toast.error("Failed to save feature", {
        description: err instanceof Error ? err.message : "Gateway request failed",
        duration: 10000,
        action: {
          label: "Dismiss",
          onClick: () => toast.dismiss(toastId),
        },
      });
    }
  };

  const toEditableFeature = (feature: FeatureWithHierarchy): EditableFeature => ({
    id: feature.id,
    name: feature.name || "",
    description: feature.description || "",
    gherkinContent: feature.gherkinContent || "",
    targetMode: feature.targetMode || "PROJECT",
    projectId: feature.projectId,
    defaultPageId: feature.defaultPageId,
    scenarios: (feature.scenarios || [])
      .slice()
      .sort((a, b) => (a.sequenceOrder || 0) - (b.sequenceOrder || 0))
      .map((scenario, scenarioIndex) => ({
        id: scenario.id,
        name: scenario.name || "",
        description: scenario.description || "",
        sequenceOrder: scenario.sequenceOrder || scenarioIndex + 1,
        steps: (scenario.steps || [])
          .slice()
          .sort((a, b) => (a.sequenceOrder || 0) - (b.sequenceOrder || 0))
          .map((step, stepIndex) => ({
            id: step.id,
            type: step.type || "Then",
            text: step.text || "",
            sequenceOrder: step.sequenceOrder || stepIndex + 1,
          })),
      })),
  });

  const validateEditableFeature = (feature: EditableFeature) => {
    const errors: string[] = [];
    if (!feature.name.trim()) errors.push("Feature name is required.");
    if (!feature.scenarios.length) errors.push("At least one scenario is required.");
    feature.scenarios.forEach((scenario, scenarioIndex) => {
      if (!scenario.name.trim()) errors.push(`Scenario ${scenarioIndex + 1} needs a name.`);
      if (!scenario.steps.length) errors.push(`Scenario "${scenario.name || scenarioIndex + 1}" needs at least one step.`);
      scenario.steps.forEach((step, stepIndex) => {
        if (!step.text.trim()) errors.push(`Step ${stepIndex + 1} in "${scenario.name || `scenario ${scenarioIndex + 1}`}" needs text.`);
      });
    });
    return errors;
  };

  const validateFullFeature = (feature: FeatureWithHierarchy) => validateEditableFeature(toEditableFeature(feature));

  const buildGherkinFromFeature = (feature: FeatureWithHierarchy) => {
    const lines = [`Feature: ${feature.name}`];
    if (feature.description?.trim()) {
      lines.push(`  ${feature.description.trim()}`);
    }
    (feature.scenarios || [])
      .slice()
      .sort((a, b) => (a.sequenceOrder || 0) - (b.sequenceOrder || 0))
      .forEach((scenario) => {
        lines.push("", `  Scenario: ${scenario.name}`);
        (scenario.steps || [])
          .slice()
          .sort((a, b) => (a.sequenceOrder || 0) - (b.sequenceOrder || 0))
          .forEach((step) => {
            lines.push(`    ${step.type || "Then"} ${step.text || ""}`.trimEnd());
          });
      });
    return `${lines.join("\n")}\n`;
  };

  const safeFeatureFileName = (name: string) =>
    `${name.trim().replace(/[^a-z0-9-_]+/gi, "-").replace(/^-+|-+$/g, "").toLowerCase() || "feature"}.feature`;

  const openEditFeature = async (feature: Feature) => {
    try {
      toast.loading("Loading feature editor...");
      const fullFeature = await getFeatureWithHierarchy(feature.id);
      setEditingFeature(toEditableFeature(fullFeature));
      setRemovedScenarioIds([]);
      setRemovedStepIds([]);
      toast.dismiss();
    } catch (error) {
      toast.dismiss();
      toast.error("Feature edit failed", {
        description: error instanceof Error ? error.message : "Could not load the feature hierarchy.",
      });
    }
  };

  const updateEditableFeature = (patch: Partial<EditableFeature>) => {
    setEditingFeature((current) => (current ? { ...current, ...patch } : current));
  };

  const updateEditableScenario = (scenarioIndex: number, patch: Partial<EditableScenario>) => {
    setEditingFeature((current) => {
      if (!current) return current;
      return {
        ...current,
        scenarios: current.scenarios.map((scenario, index) =>
          index === scenarioIndex ? { ...scenario, ...patch } : scenario
        ),
      };
    });
  };

  const updateEditableStep = (scenarioIndex: number, stepIndex: number, patch: Partial<EditableStep>) => {
    setEditingFeature((current) => {
      if (!current) return current;
      return {
        ...current,
        scenarios: current.scenarios.map((scenario, index) => {
          if (index !== scenarioIndex) return scenario;
          return {
            ...scenario,
            steps: scenario.steps.map((step, innerIndex) =>
              innerIndex === stepIndex ? { ...step, ...patch } : step
            ),
          };
        }),
      };
    });
  };

  const addEditableScenario = () => {
    setEditingFeature((current) => {
      if (!current) return current;
      const sequenceOrder = current.scenarios.length + 1;
      return {
        ...current,
        scenarios: [
          ...current.scenarios,
          {
            name: "",
            description: "",
            sequenceOrder,
            steps: [emptyEditableStep(1)],
          },
        ],
      };
    });
  };

  const removeEditableScenario = (scenarioIndex: number) => {
    setEditingFeature((current) => {
      if (!current) return current;
      const scenario = current.scenarios[scenarioIndex];
      if (scenario?.id) {
        const existingStepIds = scenario.steps.map((step) => step.id).filter(Boolean) as string[];
        setRemovedScenarioIds((ids) => [...ids, scenario.id as string]);
        setRemovedStepIds((ids) => [...ids, ...existingStepIds]);
      }
      return {
        ...current,
        scenarios: current.scenarios
          .filter((_, index) => index !== scenarioIndex)
          .map((item, index) => ({ ...item, sequenceOrder: index + 1 })),
      };
    });
  };

  const addEditableStep = (scenarioIndex: number) => {
    setEditingFeature((current) => {
      if (!current) return current;
      return {
        ...current,
        scenarios: current.scenarios.map((scenario, index) => {
          if (index !== scenarioIndex) return scenario;
          return {
            ...scenario,
            steps: [...scenario.steps, emptyEditableStep(scenario.steps.length + 1)],
          };
        }),
      };
    });
  };

  const removeEditableStep = (scenarioIndex: number, stepIndex: number) => {
    setEditingFeature((current) => {
      if (!current) return current;
      const scenario = current.scenarios[scenarioIndex];
      const step = scenario?.steps[stepIndex];
      if (step?.id) {
        setRemovedStepIds((ids) => [...ids, step.id as string]);
      }
      return {
        ...current,
        scenarios: current.scenarios.map((item, index) => {
          if (index !== scenarioIndex) return item;
          return {
            ...item,
            steps: item.steps
              .filter((_, innerIndex) => innerIndex !== stepIndex)
              .map((innerStep, innerIndex) => ({ ...innerStep, sequenceOrder: innerIndex + 1 })),
          };
        }),
      };
    });
  };

  const moveEditableStep = (scenarioIndex: number, stepIndex: number, direction: -1 | 1) => {
    setEditingFeature((current) => {
      if (!current) return current;
      return {
        ...current,
        scenarios: current.scenarios.map((scenario, index) => {
          if (index !== scenarioIndex) return scenario;
          const nextIndex = stepIndex + direction;
          if (nextIndex < 0 || nextIndex >= scenario.steps.length) return scenario;
          const steps = [...scenario.steps];
          const [movedStep] = steps.splice(stepIndex, 1);
          steps.splice(nextIndex, 0, movedStep);
          return {
            ...scenario,
            steps: steps.map((step, orderIndex) => ({ ...step, sequenceOrder: orderIndex + 1 })),
          };
        }),
      };
    });
  };

  const saveEditedFeature = async () => {
    if (!editingFeature) return;
    const validationErrors = validateEditableFeature(editingFeature);
    if (validationErrors.length) {
      toast.error("Feature validation failed", {
        description: validationErrors.slice(0, 3).join(" "),
      });
      return;
    }

    try {
      setSavingEdit(true);
      toast.loading("Updating feature...");
      await updateFeature(editingFeature.id, {
        name: editingFeature.name.trim(),
        description: editingFeature.description?.trim(),
        status: "DRAFT",
        targetMode: editingFeature.targetMode || "PROJECT",
        projectId: editingFeature.projectId,
        defaultPageId: editingFeature.defaultPageId,
      });

      for (const stepId of removedStepIds) {
        await deleteStep(stepId);
      }
      for (const scenarioId of removedScenarioIds) {
        await deleteScenario(scenarioId);
      }

      for (let scenarioIndex = 0; scenarioIndex < editingFeature.scenarios.length; scenarioIndex += 1) {
        const scenario = editingFeature.scenarios[scenarioIndex];
        const scenarioPayload = {
          name: scenario.name.trim(),
          description: scenario.description?.trim() || scenario.name.trim(),
          sequenceOrder: scenarioIndex + 1,
          status: "DRAFT",
        };
        const savedScenario = scenario.id
          ? await updateScenario(scenario.id, scenarioPayload)
          : await createScenario(editingFeature.id, scenarioPayload);

        for (let stepIndex = 0; stepIndex < scenario.steps.length; stepIndex += 1) {
          const step = scenario.steps[stepIndex];
          const stepPayload = {
            type: step.type,
            text: step.text.trim(),
            sequenceOrder: stepIndex + 1,
            status: "PENDING",
          };
          if (step.id) {
            await updateStep(step.id, stepPayload);
          } else {
            await createStep(savedScenario.id, stepPayload);
          }
        }
      }

      await exportFeatureGherkin(editingFeature.id);

      toast.dismiss();
      toast.success("Feature updated", {
        description: `"${editingFeature.name}" is ready to run or download.`,
      });
      setEditingFeature(null);
      setRemovedScenarioIds([]);
      setRemovedStepIds([]);
      await fetchFeaturesData();
    } catch (error) {
      toast.dismiss();
      toast.error("Feature update failed", {
        description: error instanceof Error ? error.message : "Gateway request failed.",
      });
    } finally {
      setSavingEdit(false);
    }
  };

  const handleDeleteFeature = async (feature: Feature) => {
    if (!window.confirm(`Delete feature "${feature.name}" and its scenarios?`)) return;
    try {
      setDeletingFeatureId(feature.id);
      await deleteFeature(feature.id);
      toast.success("Feature deleted", {
        description: `"${feature.name}" was removed.`,
      });
      if (selectedScenario?.featureId === feature.id) {
        setSelectedScenario(null);
      }
      await fetchFeaturesData();
    } catch (error) {
      toast.error("Feature delete failed", {
        description: error instanceof Error ? error.message : "Gateway request failed.",
      });
    } finally {
      setDeletingFeatureId(null);
    }
  };

  const handleDownloadFeature = async (feature: Feature) => {
    try {
      toast.loading("Validating feature before download...");
      const fullFeature = await getFeatureWithHierarchy(feature.id);
      const validationErrors = validateFullFeature(fullFeature);
      if (validationErrors.length) {
        toast.dismiss();
        toast.error("Download blocked by validation", {
          description: validationErrors.slice(0, 3).join(" "),
        });
        setEditingFeature(toEditableFeature(fullFeature));
        setRemovedScenarioIds([]);
        setRemovedStepIds([]);
        return;
      }

      const exported = await exportFeatureGherkin(feature.id).catch(() => "");
      const gherkin = typeof exported === "string" && exported.trim()
        ? exported
        : buildGherkinFromFeature(fullFeature);
      const blob = new Blob([gherkin], { type: "text/x-gherkin;charset=utf-8" });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = safeFeatureFileName(fullFeature.name);
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
      toast.dismiss();
      toast.success("Feature downloaded", {
        description: "Validation passed and the Gherkin file was exported.",
      });
    } catch (error) {
      toast.dismiss();
      toast.error("Feature download failed", {
        description: error instanceof Error ? error.message : "Gateway request failed.",
      });
    }
  };

  const handleScenarioClick = (scenarioId: string) => {
    // Search in our dynamically fetched features
    let foundScenario: any = null;
    featureGroups.forEach(f => {
      const match = f.scenarios.find((s: any) => s.id === scenarioId);
      if (match) {
        foundScenario = match;
      }
    });

    if (foundScenario) {
      setSelectedScenario({
        id: foundScenario.id,
        featureId: foundScenario.featureId,
        featureName: foundScenario.featureName,
        projectId: foundScenario.projectId,
        executionId: foundScenario.executionId,
        name: foundScenario.name,
        status: toScenarioDetailStatus(foundScenario.status),
        steps: (foundScenario.steps || []).map((step: any) => ({
          ...step,
          status: toScenarioDetailStatus(step.status),
        })),
        logs: foundScenario.logs || "No detailed logs available for this scenario yet.",
        screenshots: foundScenario.screenshots || [],
      });
    } else {
      const details = scenarioDetails[scenarioId];
      if (details) {
        setSelectedScenario(details);
      }
    }
  };

  const handlePipelineScenarioClick = (featureId: string, scenarioId: string) => {
    handleScenarioClick(scenarioId);
  };

  const humanizeStepText = (text: string) => {
    return text
      .replace(/^I\s+/i, "the tester ")
      .replace(/\s+/g, " ")
      .trim();
  };

  const cleanFailureMessage = (message?: string) => {
    if (!message) return "";
    const lines = message
      .split("\n")
      .map((line) => line.trim())
      .filter((line) => {
        const lower = line.toLowerCase();
        return Boolean(
          line &&
          !lower.startsWith("status:") &&
          !lower.startsWith("feature:") &&
          !lower.startsWith("scenario:") &&
          !lower.startsWith("call log:") &&
          !line.startsWith("at ") &&
          !lower.includes("traceback") &&
          !lower.includes("stack")
        );
      });
    const exceptionLine = lines.find((line) => line.toLowerCase().startsWith("exception:"));
    const failedStepLine = lines.find((line) => line.toLowerCase().startsWith("failed step:"));
    const firstUsefulLine = exceptionLine || failedStepLine || lines[0];
    return (firstUsefulLine || message).replace(/\s+/g, " ").slice(0, 220);
  };

  const formatProblemFromFailure = (failedStepText: string, failureMessage: string) => {
    const exception = failureMessage.replace(/^Exception:\s*/i, "").trim();
    const visibleMatch = failedStepText.match(/(?:should see|see)\s+["']?(.+?)["']?$/i);
    const clickMatch = failedStepText.match(/(?:click|select|press)\s+(?:on\s+)?["']?(.+?)["']?$/i);
    const navigationMatch = failedStepText.match(/(?:go|navigate|open)\s+to\s+["']?(.+?)["']?$/i);
    if (visibleMatch) {
      const expected = visibleMatch[1].replace(/["'.]+$/, "");
      return {
        title: `Page does not display ${expected.slice(0, 70)}`,
        actual: `The expected content "${expected}" is not visible on the page.`,
      };
    }
    if (clickMatch) {
      const control = clickMatch[1].replace(/["'.]+$/, "");
      return {
        title: `${control.slice(0, 70)} cannot be selected`,
        actual: `The workflow stops when the tester tries to select "${control}".`,
      };
    }
    if (navigationMatch) {
      const destination = navigationMatch[1].replace(/["'.]+$/, "");
      return {
        title: `Unable to open ${destination.slice(0, 75)}`,
        actual: `The expected destination "${destination}" does not open.`,
      };
    }
    return {
      title: failedStepText
        ? `User cannot complete ${failedStepText.replace(/^(Given|When|Then|And)\s+/i, "").replace(/^I\s+/i, "").slice(0, 75)}`
        : "User workflow cannot be completed",
      actual: failedStepText
        ? `The workflow stops while the tester attempts to ${humanizeStepText(failedStepText)}.`
        : "The workflow stops before the expected result is reached.",
    };
  };

  const buildProblemTitle = (scenarioName: string, failedStepText: string, failureMessage: string, action: "validated_failure" | "refused_positive") => {
    if (action === "refused_positive") {
      return `Incorrect pass result for ${scenarioName}`;
    }
    if (failureMessage) {
      return failureMessage.length > 90 ? `${failureMessage.slice(0, 87)}...` : failureMessage;
    }
    if (failedStepText) {
      return `Cannot complete: ${failedStepText.replace(/^I\s+/i, "").slice(0, 90)}`;
    }
    return `${scenarioName} does not complete successfully`;
  };

  const buildScenarioTicketDraft = (action: "validated_failure" | "refused_positive") => {
    if (!selectedScenario) return null;
    if (!selectedScenario.projectId) {
      toast.error("Ticket creation failed", {
        description: "This scenario is missing its project id.",
      });
      return null;
    }

    const screenshotLinks = selectedScenario.screenshots
      .map((screenshot) => screenshot.url)
      .filter(Boolean)
      .join("\n");
    const failedSteps = selectedScenario.steps
      .filter((step) => step.status === "error")
      .map((step) => `${step.keyword} ${step.text}${step.error ? `: ${step.error}` : ""}`)
      .join("\n");
    const featureName = selectedScenario.featureName || "the tested feature";
    const scenarioName = selectedScenario.name;
    const failedStep = selectedScenario.steps.find((step) => step.status === "error");
    const failedStepText = failedStep ? `${failedStep.keyword} ${failedStep.text}` : "";
    const failureMessage = cleanFailureMessage(failedStep?.error || selectedScenario.logs);
    const failureProblem = formatProblemFromFailure(failedStepText, failureMessage);
    const title = action === "validated_failure"
      ? failureProblem.title
      : buildProblemTitle(scenarioName, failedStep?.text || "", failureMessage, action);
    const problemSummary = action === "validated_failure"
      ? `A tester cannot complete "${scenarioName}" because the application does not produce the expected result.`
      : `"${scenarioName}" was marked as successful, but the observed application behavior does not match the expected result.`;
    const actualResult = action === "validated_failure"
      ? failureProblem.actual || failedSteps || "The scenario ended with a failed status before the expected workflow was completed."
      : "The automated result was accepted as positive, but the tester refused it after review.";
    const expectedResult = failedStep
      ? `The tester should be able to ${humanizeStepText(failedStep.text)} and continue the workflow.`
      : "The application should complete the expected user workflow.";
    const impact = action === "validated_failure"
      ? "This blocks validation of the user journey and should be checked by the development team."
      : "This may hide a real product issue because the automated result is marked as successful.";

    const description = [
      "Summary",
      problemSummary,
      "",
      "Impact",
      impact,
      "",
      "Expected result",
      expectedResult,
      "",
      "Actual result",
      actualResult,
      "",
      "How to reproduce",
      ...selectedScenario.steps.map((step, index) => `${index + 1}. ${humanizeStepText(`${step.keyword} ${step.text}`)}`),
      "",
      "Context",
      `Feature: ${featureName}`,
      `Scenario: ${scenarioName}`,
      selectedScenario.executionId ? `Execution: ${selectedScenario.executionId}` : "",
      screenshotLinks ? `Screenshots: ${screenshotLinks}` : "",
      selectedScenario.logs ? `Technical evidence: ${selectedScenario.logs.split("\n").slice(0, 6).join(" | ")}` : "",
    ].filter(Boolean).join("\n");

    return {
      projectId: selectedScenario.projectId,
      title,
      description,
      severity: action === "validated_failure" ? "HIGH" : "MEDIUM",
      status: "OPEN",
      testExecutionId: selectedScenario.executionId,
      source: "e2e",
      action,
      evidence: selectedScenario.screenshots.filter((screenshot) => Boolean(screenshot.url)).map((screenshot, index) => ({
        fileName: screenshot.name || `e2e-failure-${index + 1}.png`,
        url: screenshot.url as string,
        contentType: "image/png",
      })),
    };
  };

  const buildScenarioReportPayload = (action: "validated_failure" | "refused_positive") => {
    if (!selectedScenario) return null;
    const failedStep = selectedScenario.steps.find((step) => step.status === "error");
    return {
      featureName: selectedScenario.featureName,
      featureId: selectedScenario.featureId,
      scenarioName: selectedScenario.name,
      scenarioId: selectedScenario.id,
      executionId: selectedScenario.executionId,
      action,
      status: selectedScenario.status,
      steps: selectedScenario.steps.map((step) => ({
        id: step.id,
        keyword: step.keyword,
        text: step.text,
        status: step.status,
        error: step.error,
      })),
      failedStep: failedStep
        ? {
            id: failedStep.id,
            keyword: failedStep.keyword,
            text: failedStep.text,
            status: failedStep.status,
            error: failedStep.error,
          }
        : {},
      logs: selectedScenario.logs || "",
      screenshots: selectedScenario.screenshots.map((screenshot) => screenshot.url).filter(Boolean) as string[],
      errorMessage: failedStep?.error || "",
      severity: action === "validated_failure" ? "HIGH" : "MEDIUM",
    };
  };

  const openTicketDraft = async (action: "validated_failure" | "refused_positive") => {
    if (!selectedScenario) return;
    const localDraft = buildScenarioTicketDraft(action);
    const reportPayload = buildScenarioReportPayload(action);
    if (!localDraft || !reportPayload) return;

    try {
      const report = await agentApi.generateTicketReport(reportPayload);
      navigate("/tickets", {
        state: {
          ticketDraft: {
            ...localDraft,
            title: report.title || localDraft.title,
            description: report.description || localDraft.description,
            severity: report.severity || localDraft.severity,
            reportProvider: report.provider,
            reportModel: report.model,
          },
        },
      });
    } catch (error) {
      toast.warning("Report agent unavailable", {
        description: "Using the local ticket draft instead.",
      });
      navigate("/tickets", { state: { ticketDraft: localDraft } });
    }
  };

  const handleValidateWithTicket = async () => {
    if (!selectedScenario) return;
    await openTicketDraft("validated_failure");
  };

  const handleRejectWithTicket = async () => {
    if (!selectedScenario) return;
    await openTicketDraft("refused_positive");
  };

  const handleValidate = () => {
    if (selectedScenario) {
      toast.success("Result validated ✓", {
        description: `Scenario "${selectedScenario.name}" has been validated`,
        duration: 3000,
      });
    }
  };

  const handleIgnoreClick = () => {
    setIgnoreModalOpen(true);
  };

  const handleIgnoreConfirm = (reason: string, comment: string) => {
    if (selectedScenario) {
      toast.warning("Result ignored", {
        description: `Scenario "${selectedScenario.name}" has been ignored`,
        duration: 3000,
      });
    }
  };

  const handleReject = () => {
    if (selectedScenario) {
      toast.error("Result refused ✗", {
        description: `Scenario "${selectedScenario.name}" has been rejected`,
        duration: 3000,
      });
    }
  };

  return (
    <div className="min-h-screen bg-background">
      <Toaster />
      <TopBar title="E2E Tests" />

      <IgnoreModal
        open={ignoreModalOpen}
        onOpenChange={setIgnoreModalOpen}
        onConfirm={handleIgnoreConfirm}
      />

      {editingFeature && (
        <div className="fixed inset-0 z-50 bg-background/80 backdrop-blur-sm flex items-center justify-center p-6">
          <GlassCard className="w-full max-w-5xl max-h-[88vh] overflow-y-auto p-6 shadow-[0_0_50px_rgba(124,58,237,0.25)]">
            <div className="flex items-start justify-between gap-4 mb-6">
              <div>
                <h2 className="text-2xl font-bold bg-gradient-to-r from-primary to-accent bg-clip-text text-transparent">
                  Update Feature
                </h2>
                <p className="text-sm text-muted-foreground mt-1">
                  Change the feature name, scenarios, and reusable steps before running or downloading it.
                </p>
              </div>
              <button
                type="button"
                onClick={() => setEditingFeature(null)}
                className="w-10 h-10 rounded-full border border-border hover:bg-accent/20 flex items-center justify-center transition-colors"
                title="Close editor"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-5">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <label className="space-y-2">
                  <span className="text-sm font-semibold">Feature name</span>
                  <input
                    value={editingFeature.name}
                    onChange={(event) => updateEditableFeature({ name: event.target.value })}
                    className="w-full rounded-xl border border-border bg-background/70 px-4 py-3 outline-none focus:border-primary"
                    placeholder="Feature name"
                  />
                </label>
                <label className="space-y-2">
                  <span className="text-sm font-semibold">Description</span>
                  <input
                    value={editingFeature.description || ""}
                    onChange={(event) => updateEditableFeature({ description: event.target.value })}
                    className="w-full rounded-xl border border-border bg-background/70 px-4 py-3 outline-none focus:border-primary"
                    placeholder="Short feature description"
                  />
                </label>
              </div>

              <div className="flex items-center justify-between">
                <h3 className="text-lg font-semibold">Scenarios and Steps</h3>
                <button
                  type="button"
                  onClick={addEditableScenario}
                  className="inline-flex items-center gap-2 rounded-xl bg-primary/10 px-4 py-2 text-sm font-semibold text-primary hover:bg-primary/20 transition-colors"
                >
                  <Plus className="w-4 h-4" />
                  Add scenario
                </button>
              </div>

              <div className="space-y-4">
                {editingFeature.scenarios.map((scenario, scenarioIndex) => (
                  <div key={scenario.id || `scenario-${scenarioIndex}`} className="rounded-2xl border border-border bg-card/40 p-4 space-y-4">
                    <div className="flex flex-col md:flex-row gap-3">
                      <label className="flex-1 space-y-2">
                        <span className="text-sm font-semibold">Scenario {scenarioIndex + 1}</span>
                        <input
                          value={scenario.name}
                          onChange={(event) => updateEditableScenario(scenarioIndex, { name: event.target.value })}
                          className="w-full rounded-xl border border-border bg-background/70 px-4 py-3 outline-none focus:border-primary"
                          placeholder="Scenario name"
                        />
                      </label>
                      <label className="flex-1 space-y-2">
                        <span className="text-sm font-semibold">Scenario description</span>
                        <input
                          value={scenario.description || ""}
                          onChange={(event) => updateEditableScenario(scenarioIndex, { description: event.target.value })}
                          className="w-full rounded-xl border border-border bg-background/70 px-4 py-3 outline-none focus:border-primary"
                          placeholder="Optional description"
                        />
                      </label>
                      <button
                        type="button"
                        onClick={() => removeEditableScenario(scenarioIndex)}
                        className="self-end h-12 rounded-xl border border-error/40 px-4 text-error hover:bg-error/10 transition-colors inline-flex items-center gap-2"
                        title="Delete scenario"
                      >
                        <Trash2 className="w-4 h-4" />
                        Delete
                      </button>
                    </div>

                    <div className="space-y-3">
                      {scenario.steps.map((step, stepIndex) => (
                        <div key={step.id || `step-${scenarioIndex}-${stepIndex}`} className="grid grid-cols-1 md:grid-cols-[140px_1fr_auto_auto] gap-3 items-center">
                          <select
                            value={step.type}
                            onChange={(event) => updateEditableStep(scenarioIndex, stepIndex, { type: event.target.value })}
                            className="rounded-xl border border-border bg-background/70 px-3 py-3 outline-none focus:border-primary"
                          >
                            {["Given", "When", "Then", "And", "But"].map((type) => (
                              <option key={type} value={type}>{type}</option>
                            ))}
                          </select>
                          <input
                            value={step.text}
                            onChange={(event) => updateEditableStep(scenarioIndex, stepIndex, { text: event.target.value })}
                            className="rounded-xl border border-border bg-background/70 px-4 py-3 outline-none focus:border-primary"
                            placeholder="Step text"
                          />
                          <div className="flex items-center gap-2">
                            <button
                              type="button"
                              onClick={() => moveEditableStep(scenarioIndex, stepIndex, -1)}
                              disabled={stepIndex === 0}
                              className="h-12 w-12 rounded-xl border border-border hover:border-primary hover:text-primary disabled:opacity-35 disabled:hover:border-border disabled:hover:text-current flex items-center justify-center transition-colors"
                              title="Move step up"
                            >
                              <ArrowUp className="w-4 h-4" />
                            </button>
                            <button
                              type="button"
                              onClick={() => moveEditableStep(scenarioIndex, stepIndex, 1)}
                              disabled={stepIndex === scenario.steps.length - 1}
                              className="h-12 w-12 rounded-xl border border-border hover:border-primary hover:text-primary disabled:opacity-35 disabled:hover:border-border disabled:hover:text-current flex items-center justify-center transition-colors"
                              title="Move step down"
                            >
                              <ArrowDown className="w-4 h-4" />
                            </button>
                          </div>
                          <button
                            type="button"
                            onClick={() => removeEditableStep(scenarioIndex, stepIndex)}
                            className="h-12 w-12 rounded-xl border border-border hover:border-error hover:text-error flex items-center justify-center transition-colors"
                            title="Delete step"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      ))}
                      <button
                        type="button"
                        onClick={() => addEditableStep(scenarioIndex)}
                        className="inline-flex items-center gap-2 rounded-xl border border-border px-4 py-2 text-sm font-semibold hover:bg-accent/15 transition-colors"
                      >
                        <Plus className="w-4 h-4" />
                        Add step
                      </button>
                    </div>
                  </div>
                ))}
              </div>

              <div className="flex justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setEditingFeature(null)}
                  className="rounded-xl border border-border px-5 py-3 font-semibold hover:bg-accent/15 transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={saveEditedFeature}
                  disabled={savingEdit}
                  className="inline-flex items-center gap-2 rounded-xl bg-gradient-to-r from-primary to-accent px-5 py-3 font-semibold text-white shadow-[0_0_20px_rgba(124,58,237,0.35)] hover:shadow-[0_0_28px_rgba(124,58,237,0.5)] disabled:opacity-60 transition-all"
                >
                  <Save className="w-4 h-4" />
                  {savingEdit ? "Saving..." : "Save changes"}
                </button>
              </div>
            </div>
          </GlassCard>
        </div>
      )}

      <div className="px-8 py-6 space-y-6">
        {/* Tabs */}
        <div className="flex items-center gap-2 bg-card/50 backdrop-blur-xl border border-border rounded-2xl p-2 w-fit shadow-[0_0_40px_rgba(124,58,237,0.15)]">
          <button
            onClick={() => setActiveTab("features")}
            className={cn(
              "px-8 py-3 rounded-xl font-semibold text-sm transition-all duration-300",
              activeTab === "features"
                ? "bg-gradient-to-r from-primary to-accent text-white shadow-[0_0_20px_rgba(124,58,237,0.4)]"
                : "text-muted-foreground hover:text-foreground hover:bg-accent/20"
            )}
          >
            All Features
          </button>
          <button
            onClick={() => setActiveTab("pipeline")}
            className={cn(
              "px-8 py-3 rounded-xl font-semibold text-sm transition-all duration-300",
              activeTab === "pipeline"
                ? "bg-gradient-to-r from-primary to-accent text-white shadow-[0_0_20px_rgba(124,58,237,0.4)]"
                : "text-muted-foreground hover:text-foreground hover:bg-accent/20"
            )}
          >
            Pipeline
          </button>
          <button
            onClick={() => setActiveTab("builder")}
            className={cn(
              "px-8 py-3 rounded-xl font-semibold text-sm transition-all duration-300",
              activeTab === "builder"
                ? "bg-gradient-to-r from-primary to-accent text-white shadow-[0_0_20px_rgba(124,58,237,0.4)]"
                : "text-muted-foreground hover:text-foreground hover:bg-accent/20"
            )}
          >
            E2E Builder
          </button>
          <button
            onClick={() => setActiveTab("secrets")}
            className={cn(
              "px-8 py-3 rounded-xl font-semibold text-sm transition-all duration-300 inline-flex items-center gap-2",
              activeTab === "secrets"
                ? "bg-gradient-to-r from-primary to-accent text-white shadow-[0_0_20px_rgba(124,58,237,0.4)]"
                : "text-muted-foreground hover:text-foreground hover:bg-accent/20"
            )}
          >
            <KeyRound className="w-4 h-4" />
            Secrets
          </button>
        </div>

        {/* All Features Tab */}
        {activeTab === "features" && (
          <div className="space-y-4">
            {featureGroups.map((feature) => (
              <GlassCard key={feature.id} className="p-6">
                <div className="flex flex-col gap-4 mb-4 lg:flex-row lg:items-center lg:justify-between">
                  <div className="flex flex-wrap items-center gap-4">
                    <h3 className="text-lg font-semibold">{feature.name}</h3>
                    <StatusBadge
                      variant={feature.status === "passed" ? "PASSED" : feature.status === "failed" ? "FAILED" : feature.status === "running" ? "RUNNING" : "PENDING"}
                    />
                  </div>
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-sm text-muted-foreground mr-2">
                      {feature.passedScenarios}/{feature.totalScenarios} passed - {feature.duration}
                    </span>
                    <button
                      type="button"
                      onClick={() => openEditFeature(feature)}
                      className="inline-flex items-center gap-2 rounded-xl border border-border px-3 py-2 text-sm font-semibold hover:border-primary hover:text-primary hover:bg-primary/10 transition-colors"
                      title="Update feature"
                    >
                      <Pencil className="w-4 h-4" />
                      Update
                    </button>
                    <button
                      type="button"
                      onClick={() => handleDownloadFeature(feature)}
                      className="inline-flex items-center gap-2 rounded-xl border border-success/40 px-3 py-2 text-sm font-semibold text-success hover:bg-success/10 transition-colors"
                      title="Validate and download feature"
                    >
                      <Download className="w-4 h-4" />
                      Download
                    </button>
                    <button
                      type="button"
                      onClick={() => handleDeleteFeature(feature)}
                      disabled={deletingFeatureId === feature.id}
                      className="inline-flex items-center gap-2 rounded-xl border border-error/40 px-3 py-2 text-sm font-semibold text-error hover:bg-error/10 disabled:opacity-60 transition-colors"
                      title="Delete feature"
                    >
                      <Trash2 className="w-4 h-4" />
                      {deletingFeatureId === feature.id ? "Deleting..." : "Delete"}
                    </button>
                  </div>
                </div>

                {/* Scenarios Grid */}
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                  {feature.scenarios.map((scenario) => {
                    const statusColors = {
                      passed: "border-success bg-success/5",
                      failed: "border-error bg-error/5",
                      running: "border-info bg-info/5",
                      pending: "border-muted-foreground bg-muted/5",
                    };
                    const displayStatus = normalizeTestStatus(scenario.status);

                    return (
                      <button
                        key={scenario.id}
                        onClick={() => handleScenarioClick(scenario.id)}
                        className={cn(
                          "p-4 rounded-xl border-l-4 transition-all hover:scale-[1.02] cursor-pointer text-left",
                          statusColors[displayStatus]
                        )}
                      >
                        <div className="flex items-center justify-between gap-2">
                          <span className="text-sm font-medium flex-1">{scenario.name}</span>
                          <StatusBadge
                            variant={displayStatus === "passed" ? "PASSED" : displayStatus === "failed" ? "FAILED" : displayStatus === "running" ? "RUNNING" : "PENDING"}
                            className="scale-75"
                          />
                        </div>
                      </button>
                    );
                  })}
                </div>
              </GlassCard>
            ))}
          </div>
        )}

        {/* Pipeline Tab */}
        {activeTab === "pipeline" && (
          <FeaturePipeline
            features={featureGroups}
            runs={pipelineRuns}
            runningFeatureIds={runningFeatureIds}
            onRunFeature={handleRunFeature}
            onDismissRun={dismissPipelineRun}
            onScenarioClick={handlePipelineScenarioClick}
          />
        )}

        {/* Builder Tab */}
        {activeTab === "builder" && (
          <FeatureBuilder onSave={handleSaveFeature} />
        )}

        {/* Secrets Tab */}
        {activeTab === "secrets" && (
          <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1fr)_420px] gap-6">
            <GlassCard className="p-6 space-y-6">
              <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                <div>
                  <div className="flex items-center gap-3">
                    <div className="h-11 w-11 rounded-2xl bg-primary/10 text-primary flex items-center justify-center">
                      <ShieldCheck className="w-5 h-5" />
                    </div>
                    <div>
                      <h3 className="text-xl font-bold">Vault Secrets</h3>
                      <p className="text-sm text-muted-foreground">
                        Store passwords, tokens, and private test values outside the feature text.
                      </p>
                    </div>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => fetchSecretAliases(secretProjectId)}
                  disabled={loadingSecrets || !secretProjectId}
                  className="rounded-xl border border-border px-4 py-2 text-sm font-semibold hover:bg-accent/15 disabled:opacity-60 transition-colors"
                >
                  {loadingSecrets ? "Refreshing..." : "Refresh aliases"}
                </button>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <label className="space-y-2">
                  <span className="text-sm font-semibold">Project</span>
                  <select
                    value={secretProjectId}
                    onChange={(event) => setSecretProjectId(event.target.value)}
                    className="w-full rounded-xl border border-border bg-background/70 px-4 py-3 outline-none focus:border-primary"
                  >
                    {projects.length === 0 && <option value="">No project available</option>}
                    {projects.map((project) => (
                      <option key={project.id} value={project.id}>
                        {project.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="space-y-2">
                  <span className="text-sm font-semibold">Alias</span>
                  <input
                    value={secretAlias}
                    onChange={(event) => setSecretAlias(event.target.value)}
                    className="w-full rounded-xl border border-border bg-background/70 px-4 py-3 outline-none focus:border-primary"
                    placeholder="password, admin_password, azure_token..."
                    autoComplete="off"
                  />
                </label>
              </div>

              <label className="space-y-2 block">
                <span className="text-sm font-semibold">Secret value</span>
                <input
                  value={secretValue}
                  onChange={(event) => setSecretValue(event.target.value)}
                  className="w-full rounded-xl border border-border bg-background/70 px-4 py-3 outline-none focus:border-primary"
                  placeholder="Value stored in Vault, never shown again"
                  type="password"
                  autoComplete="new-password"
                />
              </label>

              <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-primary/20 bg-primary/5 p-4">
                <div className="text-sm">
                  <p className="font-semibold text-foreground">Use secrets in Gherkin with placeholders</p>
                  <p className="text-muted-foreground mt-1">
                    Example: <span className="font-mono text-primary">{'When I enter password "${SECRET.password}"'}</span>
                  </p>
                </div>
                <button
                  type="button"
                  onClick={handleSaveSecret}
                  disabled={savingSecret || !secretProjectId}
                  className="inline-flex items-center gap-2 rounded-xl bg-gradient-to-r from-primary to-accent px-5 py-3 font-semibold text-white shadow-[0_0_20px_rgba(124,58,237,0.35)] disabled:opacity-60 transition-all"
                >
                  <Save className="w-4 h-4" />
                  {savingSecret ? "Saving..." : "Save to Vault"}
                </button>
              </div>
            </GlassCard>

            <GlassCard className="p-6">
              <div className="flex items-center justify-between gap-3 mb-5">
                <div>
                  <h3 className="text-lg font-bold">Stored aliases</h3>
                  <p className="text-sm text-muted-foreground">Values stay hidden in Vault.</p>
                </div>
                <span className="rounded-full border border-border px-3 py-1 text-sm text-muted-foreground">
                  {secretAliases.length}
                </span>
              </div>

              {secretAliases.length > 0 ? (
                <div className="space-y-3">
                  {secretAliases.map((alias) => (
                    <div key={alias} className="rounded-2xl border border-border bg-card/40 p-4">
                      <div className="flex items-center justify-between gap-3">
                        <div className="min-w-0">
                          <p className="font-semibold truncate">{alias}</p>
                          <p className="font-mono text-xs text-muted-foreground truncate">
                            {`\${SECRET.${alias}}`}
                          </p>
                        </div>
                        <button
                          type="button"
                          onClick={() => copySecretPlaceholder(alias)}
                          className="h-10 w-10 rounded-xl border border-border hover:border-primary hover:text-primary flex items-center justify-center transition-colors"
                          title="Copy placeholder"
                        >
                          <Copy className="w-4 h-4" />
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="rounded-2xl border border-dashed border-border p-6 text-center">
                  <KeyRound className="w-8 h-8 mx-auto text-muted-foreground mb-3" />
                  <p className="font-semibold">No secret aliases yet</p>
                  <p className="text-sm text-muted-foreground mt-1">
                    Add `password` or another alias, then reference it in a feature step.
                  </p>
                </div>
              )}
            </GlassCard>
          </div>
        )}

        {/* Selected Scenario Details */}
        {selectedScenario && (
          <ScenarioDetails
            scenario={selectedScenario}
            onClose={() => setSelectedScenario(null)}
            onValidate={handleValidateWithTicket}
            onIgnore={handleIgnoreClick}
            onReject={handleRejectWithTicket}
          />
        )}

        {/* Placeholder when no scenario selected */}
        {!selectedScenario && activeTab === "pipeline" && (
          <GlassCard className="p-12 text-center">
            <div className="flex flex-col items-center gap-4">
              <div className="w-20 h-20 rounded-full bg-gradient-to-br from-primary/20 to-accent/20 flex items-center justify-center">
                <span className="text-4xl">🔍</span>
              </div>
              <p className="text-muted-foreground text-lg">
                Select a feature above to view its pipeline with individual scenarios
              </p>
            </div>
          </GlassCard>
        )}
      </div>
    </div>
  );
}


