import { useEffect, useMemo, useState } from "react";
import { TopBar } from "../components/custom/TopBar";
import { GlassCard } from "../components/custom/GlassCard";
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, LineChart, Line, PieChart, Pie, Cell } from "recharts";
import { analyticsApi, MetricsResponse } from "../utils/analyticsApi";
import { projectApi, Project } from "../utils/projectApi";
import { ticketApi, Ticket } from "../utils/ticketApi";
import { api } from "../utils/api";
import { TestExecution } from "../utils/e2eApi";

const emptyMetrics: MetricsResponse = {
  mismatchRate: 0,
  testPassRate: 0,
  tokenCoverage: 0,
  healthScore: 0,
};

const status = (value?: string) => (value || "").toUpperCase();
const dayLabel = (date: string) => new Date(date).toLocaleDateString(undefined, { month: "short", day: "numeric" });

export function Analysis() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState("");
  const [executions, setExecutions] = useState<TestExecution[]>([]);
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [metrics, setMetrics] = useState<MetricsResponse>(emptyMetrics);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadProjects = async () => {
      try {
        const loadedProjects = await projectApi.getProjects();
        setProjects(loadedProjects);
        setSelectedProjectId(loadedProjects[0]?.id ?? "");
      } catch (error) {
        console.error("Failed to load projects for analytics", error);
      }
    };
    loadProjects();
  }, []);

  useEffect(() => {
    const loadAnalytics = async () => {
      setLoading(true);
      try {
        const [loadedExecutions, loadedMetrics, loadedTickets] = await Promise.all([
          api.get<TestExecution[]>("/api/v1/executions"),
          selectedProjectId ? analyticsApi.getProjectMetrics(selectedProjectId).catch(() => emptyMetrics) : Promise.resolve(emptyMetrics),
          selectedProjectId ? ticketApi.listProjectTickets(selectedProjectId).catch(() => []) : Promise.resolve([]),
        ]);
        setExecutions(loadedExecutions);
        setMetrics(loadedMetrics);
        setTickets(loadedTickets);
      } catch (error) {
        console.error("Failed to load analytics", error);
      } finally {
        setLoading(false);
      }
    };
    loadAnalytics();
  }, [selectedProjectId]);

  const testTrendsData = useMemo(() => {
    const buckets = new Map<string, { date: string; passed: number; failed: number }>();
    executions.forEach((execution) => {
      const key = dayLabel(execution.createdAt);
      const bucket = buckets.get(key) ?? { date: key, passed: 0, failed: 0 };
      if (status(execution.status) === "PASSED") bucket.passed += 1;
      if (status(execution.status) === "FAILED") bucket.failed += 1;
      buckets.set(key, bucket);
    });
    return Array.from(buckets.values()).slice(-7);
  }, [executions]);

  const designMatchData = useMemo(() => [
    { name: "Pass Rate", value: Math.round(metrics.testPassRate * 100) },
    { name: "Token Coverage", value: Math.round(metrics.tokenCoverage * 100) },
    { name: "Health", value: Math.round(metrics.healthScore) },
    { name: "Match Rate", value: Math.max(0, Math.round((1 - metrics.mismatchRate) * 100)) },
  ], [metrics]);

  const issueTypeData = useMemo(() => {
    const counts = new Map<string, number>();
    tickets.forEach((ticket) => {
      const key = ticket.severity || "UNSPECIFIED";
      counts.set(key, (counts.get(key) ?? 0) + 1);
    });
    const colors = ["#EF4444", "#F59E0B", "#8B5CF6", "#3B82F6", "#6B7280"];
    const rows = Array.from(counts.entries()).map(([name, value], index) => ({
      name,
      value,
      color: colors[index % colors.length],
    }));
    return rows.length ? rows : [{ name: "No Issues", value: 1, color: "#22C55E" }];
  }, [tickets]);

  const aiPerformanceData = useMemo(() => {
    const passedExecutions = executions.filter((execution) => status(execution.status) === "PASSED").length;
    const failedExecutions = executions.filter((execution) => status(execution.status) === "FAILED").length;
    const totalExecutions = passedExecutions + failedExecutions;
    const ticketedExecutionIds = new Set(tickets.map((ticket) => ticket.testExecutionId).filter(Boolean));
    const pendingTesterReview = executions.filter(
      (execution) => status(execution.status) === "FAILED" && !ticketedExecutionIds.has(execution.id)
    ).length;
    const azureSyncedTickets = tickets.filter((ticket) => status(ticket.azureSyncStatus) === "SYNCED").length;

    return [
      {
        label: "AI execution pass rate",
        value: totalExecutions ? `${Math.round((passedExecutions / totalExecutions) * 100)}%` : "0%",
        detail: `${passedExecutions}/${totalExecutions} executions passed`,
      },
      {
        label: "Tester validations",
        value: String(tickets.length),
        detail: "issues confirmed or turned into ticket drafts",
      },
      {
        label: "Azure tickets synced",
        value: String(azureSyncedTickets),
        detail: "validated tickets sent to Azure DevOps",
      },
      {
        label: "Waiting for tester review",
        value: String(pendingTesterReview),
        detail: "failed executions without a linked ticket",
      },
    ];
  }, [executions, tickets]);

  const selectedProject = projects.find((project) => project.id === selectedProjectId);

  return (
    <div className="min-h-screen bg-background">
      <TopBar title="Analytics" />
      <div className="px-8 py-6 space-y-6 max-w-7xl">
        <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
          <div>
            <h2 className="text-2xl font-bold text-foreground">Analysis & Insights</h2>
            <p className="text-muted-foreground mt-1">
              Live metrics from {selectedProject?.name ?? "your backend"}
            </p>
          </div>
          <select
            value={selectedProjectId}
            onChange={(event) => setSelectedProjectId(event.target.value)}
            className="h-10 rounded-md border border-border bg-card px-3 text-sm text-foreground"
          >
            {projects.map((project) => (
              <option key={project.id} value={project.id}>{project.name}</option>
            ))}
          </select>
        </div>

        <GlassCard className="p-6">
          <div className="mb-4">
            <h3 className="text-foreground">AI Performance & Tester Validation</h3>
            <p className="text-muted-foreground">
              Tracks how E2E results become tester decisions, tickets, and Azure DevOps items.
            </p>
          </div>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
            {aiPerformanceData.map((item) => (
              <div key={item.label} className="border border-border bg-card/40 p-4">
                <p className="text-xs uppercase text-muted-foreground">{item.label}</p>
                <p className="mt-2 text-2xl font-bold text-foreground">{loading ? "..." : item.value}</p>
                <p className="mt-1 text-xs text-muted-foreground">{item.detail}</p>
              </div>
            ))}
          </div>
        </GlassCard>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <GlassCard className="p-6">
            <div className="mb-4">
              <h3 className="text-foreground">Test Results Trend</h3>
              <p className="text-muted-foreground">Recent execution history from E2E service</p>
            </div>
            <ResponsiveContainer width="100%" height={300}>
              <BarChart data={testTrendsData}>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(124, 58, 237, 0.1)" />
                <XAxis dataKey="date" stroke="currentColor" fontSize={12} />
                <YAxis stroke="currentColor" fontSize={12} />
                <Tooltip contentStyle={{ backgroundColor: "rgba(26, 11, 46, 0.95)", border: "1px solid rgba(124, 58, 237, 0.1)" }} />
                <Bar dataKey="passed" fill="#22C55E" radius={[4, 4, 0, 0]} />
                <Bar dataKey="failed" fill="#EF4444" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </GlassCard>

          <GlassCard className="p-6">
            <div className="mb-4">
              <h3 className="text-foreground">Quality Signals</h3>
              <p className="text-muted-foreground">Metrics returned by analytics-service</p>
            </div>
            <ResponsiveContainer width="100%" height={300}>
              <LineChart data={designMatchData}>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(124, 58, 237, 0.1)" />
                <XAxis dataKey="name" stroke="currentColor" fontSize={12} />
                <YAxis stroke="currentColor" fontSize={12} domain={[0, 100]} />
                <Tooltip contentStyle={{ backgroundColor: "rgba(26, 11, 46, 0.95)", border: "1px solid rgba(124, 58, 237, 0.1)" }} />
                <Line type="monotone" dataKey="value" stroke="#2563EB" strokeWidth={3} dot={{ fill: "#2563EB", r: 6 }} />
              </LineChart>
            </ResponsiveContainer>
          </GlassCard>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <GlassCard className="p-6">
            <div className="mb-4">
              <h3 className="text-foreground">Issue Distribution</h3>
              <p className="text-muted-foreground">Ticket severity from ticket-service</p>
            </div>
            <ResponsiveContainer width="100%" height={300}>
              <PieChart>
                <Pie data={issueTypeData} cx="50%" cy="50%" outerRadius={100} dataKey="value" label={({ name, value }) => `${name}: ${value}`} labelLine={false}>
                  {issueTypeData.map((entry, index) => <Cell key={`cell-${index}`} fill={entry.color} />)}
                </Pie>
                <Tooltip contentStyle={{ backgroundColor: "rgba(26, 11, 46, 0.95)", border: "1px solid rgba(124, 58, 237, 0.1)" }} />
              </PieChart>
            </ResponsiveContainer>
          </GlassCard>

          <GlassCard className="p-6">
            <div className="mb-4">
              <h3 className="text-foreground">Key Metrics</h3>
              <p className="text-muted-foreground">Live performance indicators</p>
            </div>
            <div className="space-y-4">
              {[
                ["Test Pass Rate", `${(metrics.testPassRate * 100).toFixed(1)}%`, "bg-blue-950/50 border-blue-900", "text-blue-300", "text-blue-400"],
                ["Health Score", metrics.healthScore.toFixed(1), "bg-green-950/50 border-green-900", "text-green-300", "text-green-400"],
                ["Token Coverage", `${(metrics.tokenCoverage * 100).toFixed(1)}%`, "bg-purple-950/50 border-purple-900", "text-purple-300", "text-purple-400"],
                ["Mismatch Rate", `${(metrics.mismatchRate * 100).toFixed(1)}%`, "bg-yellow-950/50 border-yellow-900", "text-yellow-300", "text-yellow-400"],
              ].map(([label, value, shellClass, labelClass, valueClass]) => (
                <div key={label} className={`flex items-center justify-between p-4 rounded-lg border ${shellClass}`}>
                  <div>
                    <p className={`text-sm font-medium ${labelClass}`}>{label}</p>
                    <p className={`text-2xl font-bold mt-1 ${valueClass}`}>{loading ? "..." : value}</p>
                  </div>
                </div>
              ))}
            </div>
          </GlassCard>
        </div>
      </div>
    </div>
  );
}
