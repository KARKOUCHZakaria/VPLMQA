import { useEffect, useMemo, useState } from "react";
import { TopBar } from "../components/custom/TopBar";
import { GlassCard } from "../components/custom/GlassCard";
import {
  TrendingUp,
  CheckCircle2,
  AlertCircle,
  TestTube,
  Clock,
  Activity,
  Target,
  Zap,
} from "lucide-react";
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  BarChart,
  Bar,
} from "recharts";
import { analyticsApi, MetricsResponse } from "../utils/analyticsApi";
import { getFeatures, Feature, TestExecution } from "../utils/e2eApi";
import { projectApi, Project } from "../utils/projectApi";
import { ticketApi, Ticket } from "../utils/ticketApi";
import { api } from "../utils/api";

type TrendPoint = { date: string; passed: number; failed: number; total: number };
type ModulePoint = { module: string; tests: number; passRate: number; avgTime: number };

const emptyMetrics: MetricsResponse = {
  mismatchRate: 0,
  testPassRate: 0,
  tokenCoverage: 0,
  healthScore: 0,
};

const formatDate = (value: string) =>
  new Date(value).toLocaleDateString(undefined, { month: "short", day: "numeric" });

const normalizeStatus = (status?: string) => (status || "").toUpperCase();

export function DashboardStats() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [features, setFeatures] = useState<Feature[]>([]);
  const [executions, setExecutions] = useState<TestExecution[]>([]);
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [metrics, setMetrics] = useState<MetricsResponse>(emptyMetrics);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadDashboard = async () => {
      try {
        const loadedProjects = await projectApi.getProjects();
        const selectedProject = loadedProjects[0];
        setProjects(loadedProjects);

        const featurePage = await getFeatures();
        const loadedFeatures = featurePage.content ?? [];
        setFeatures(loadedFeatures);

        const loadedExecutions = await api.get<TestExecution[]>("/api/v1/executions");
        setExecutions(loadedExecutions);

        if (selectedProject) {
          const [loadedMetrics, loadedTickets] = await Promise.all([
            analyticsApi.getProjectMetrics(selectedProject.id).catch(() => emptyMetrics),
            ticketApi.listProjectTickets(selectedProject.id).catch(() => []),
          ]);
          setMetrics(loadedMetrics);
          setTickets(loadedTickets);
        } else {
          setMetrics(emptyMetrics);
          setTickets([]);
        }
      } catch (error) {
        console.error("Failed to load dashboard data", error);
      } finally {
        setLoading(false);
      }
    };

    loadDashboard();
  }, []);

  const trendData = useMemo<TrendPoint[]>(() => {
    const buckets = new Map<string, TrendPoint>();
    executions.forEach((execution) => {
      const key = formatDate(execution.createdAt);
      const bucket = buckets.get(key) ?? { date: key, passed: 0, failed: 0, total: 0 };
      const status = normalizeStatus(execution.status);
      if (status === "PASSED") bucket.passed += 1;
      if (status === "FAILED") bucket.failed += 1;
      bucket.total += 1;
      buckets.set(key, bucket);
    });
    return Array.from(buckets.values()).slice(-9);
  }, [executions]);

  const performanceData = useMemo<ModulePoint[]>(() => {
    return features.slice(0, 8).map((feature) => {
      const featureExecutions = executions.filter((execution) => execution.featureId === feature.id);
      const passed = featureExecutions.filter((execution) => normalizeStatus(execution.status) === "PASSED").length;
      const total = featureExecutions.length;
      const avgTime = total
        ? featureExecutions.reduce((sum, execution) => sum + (execution.executionTimeMs || 0), 0) / total / 1000
        : 0;
      return {
        module: feature.name,
        tests: total,
        passRate: total ? Math.round((passed / total) * 100) : 0,
        avgTime: Number(avgTime.toFixed(1)),
      };
    });
  }, [executions, features]);

  const passedCount = executions.filter((execution) => normalizeStatus(execution.status) === "PASSED").length;
  const failedCount = executions.filter((execution) => normalizeStatus(execution.status) === "FAILED").length;
  const totalTests = executions.length;
  const passRate = totalTests ? (passedCount / totalTests) * 100 : metrics.testPassRate * 100;
  const avgDurationSeconds = totalTests
    ? executions.reduce((sum, execution) => sum + (execution.executionTimeMs || 0), 0) / totalTests / 1000
    : 0;
  const activeIssues = tickets.filter((ticket) => !["DONE", "CLOSED", "RESOLVED"].includes(normalizeStatus(ticket.status))).length;
  const criticalIssues = tickets.filter((ticket) => normalizeStatus(ticket.severity) === "CRITICAL").length;

  return (
    <div className="min-h-screen bg-background">
      <TopBar title="Dashboard" />

      <div className="px-8 py-6 space-y-6">
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
          <GlassCard className="p-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-muted-foreground mb-1">Pass Rate</p>
                <p className="text-3xl font-bold">{loading ? "..." : `${passRate.toFixed(1)}%`}</p>
                <p className="text-xs text-success mt-1 flex items-center gap-1">
                  <TrendingUp className="w-3 h-3" />
                  {passedCount} passed
                </p>
              </div>
              <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-[#059669] to-[#22C55E] flex items-center justify-center shadow-lg">
                <CheckCircle2 className="w-6 h-6 text-white" />
              </div>
            </div>
          </GlassCard>

          <GlassCard className="p-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-muted-foreground mb-1">Total Tests</p>
                <p className="text-3xl font-bold">{loading ? "..." : totalTests}</p>
                <p className="text-xs text-info mt-1 flex items-center gap-1">
                  <Activity className="w-3 h-3" />
                  {features.length} features
                </p>
              </div>
              <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-[#7C3AED] to-[#A855F7] flex items-center justify-center shadow-lg">
                <TestTube className="w-6 h-6 text-white" />
              </div>
            </div>
          </GlassCard>

          <GlassCard className="p-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-muted-foreground mb-1">Active Issues</p>
                <p className="text-3xl font-bold">{loading ? "..." : activeIssues}</p>
                <p className="text-xs text-warning mt-1 flex items-center gap-1">
                  <AlertCircle className="w-3 h-3" />
                  {criticalIssues} critical
                </p>
              </div>
              <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-[#DC2626] to-[#EF4444] flex items-center justify-center shadow-lg">
                <Target className="w-6 h-6 text-white" />
              </div>
            </div>
          </GlassCard>

          <GlassCard className="p-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-muted-foreground mb-1">Avg Duration</p>
                <p className="text-3xl font-bold">{loading ? "..." : `${avgDurationSeconds.toFixed(1)}s`}</p>
                <p className="text-xs text-success mt-1 flex items-center gap-1">
                  <Zap className="w-3 h-3" />
                  {projects.length} projects
                </p>
              </div>
              <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-[#0891B2] to-[#38BDF8] flex items-center justify-center shadow-lg">
                <Clock className="w-6 h-6 text-white" />
              </div>
            </div>
          </GlassCard>
        </div>

        <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
          <GlassCard className="p-6">
            <h3 className="text-lg font-semibold mb-4">Test Execution Trend</h3>
            <ResponsiveContainer width="100%" height={300}>
              <AreaChart data={trendData}>
                <defs>
                  <linearGradient id="colorPassed" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#22C55E" stopOpacity={0.3} />
                    <stop offset="95%" stopColor="#22C55E" stopOpacity={0} />
                  </linearGradient>
                  <linearGradient id="colorFailed" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#EF4444" stopOpacity={0.3} />
                    <stop offset="95%" stopColor="#EF4444" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(124, 58, 237, 0.1)" />
                <XAxis dataKey="date" stroke="currentColor" style={{ fontSize: "12px" }} />
                <YAxis stroke="currentColor" style={{ fontSize: "12px" }} />
                <Tooltip contentStyle={{ backgroundColor: "rgba(26, 11, 46, 0.95)", border: "1px solid rgba(168, 85, 247, 0.25)", borderRadius: "8px" }} />
                <Area type="monotone" dataKey="passed" stroke="#22C55E" fillOpacity={1} fill="url(#colorPassed)" />
                <Area type="monotone" dataKey="failed" stroke="#EF4444" fillOpacity={1} fill="url(#colorFailed)" />
              </AreaChart>
            </ResponsiveContainer>
          </GlassCard>

          <GlassCard className="p-6">
            <h3 className="text-lg font-semibold mb-4">Feature Performance</h3>
            <ResponsiveContainer width="100%" height={300}>
              <BarChart data={performanceData}>
                <defs>
                  <linearGradient id="barGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#7C3AED" />
                    <stop offset="100%" stopColor="#A855F7" />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(124, 58, 237, 0.1)" />
                <XAxis dataKey="module" stroke="currentColor" style={{ fontSize: "12px" }} />
                <YAxis stroke="currentColor" style={{ fontSize: "12px" }} />
                <Tooltip contentStyle={{ backgroundColor: "rgba(26, 11, 46, 0.95)", border: "1px solid rgba(168, 85, 247, 0.25)", borderRadius: "8px" }} />
                <Bar dataKey="passRate" fill="url(#barGradient)" radius={[8, 8, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </GlassCard>
        </div>

        <GlassCard className="p-6">
          <h3 className="text-lg font-semibold mb-4">Feature Details</h3>
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-border">
                  <th className="text-left py-3 px-4 text-sm font-medium text-muted-foreground">Feature</th>
                  <th className="text-left py-3 px-4 text-sm font-medium text-muted-foreground">Runs</th>
                  <th className="text-left py-3 px-4 text-sm font-medium text-muted-foreground">Pass Rate</th>
                  <th className="text-left py-3 px-4 text-sm font-medium text-muted-foreground">Avg Time</th>
                  <th className="text-left py-3 px-4 text-sm font-medium text-muted-foreground">Status</th>
                </tr>
              </thead>
              <tbody>
                {performanceData.map((module) => (
                  <tr key={module.module} className="border-b border-border/50 hover:bg-accent/30 transition-colors">
                    <td className="py-3 px-4 font-medium">{module.module}</td>
                    <td className="py-3 px-4">{module.tests}</td>
                    <td className="py-3 px-4">
                      <div className="flex items-center gap-2">
                        <div className="flex-1 h-2 bg-muted rounded-full overflow-hidden max-w-[100px]">
                          <div className="h-full bg-gradient-to-r from-[#22C55E] to-[#059669]" style={{ width: `${module.passRate}%` }} />
                        </div>
                        <span className="text-sm">{module.passRate}%</span>
                      </div>
                    </td>
                    <td className="py-3 px-4 text-sm">{module.avgTime}s</td>
                    <td className="py-3 px-4">
                      {module.passRate >= 95 ? (
                        <span className="px-2 py-1 rounded-full bg-success/20 text-success text-xs font-medium">Good</span>
                      ) : module.tests === 0 ? (
                        <span className="px-2 py-1 rounded-full bg-muted text-muted-foreground text-xs font-medium">No runs</span>
                      ) : (
                        <span className="px-2 py-1 rounded-full bg-warning/20 text-warning text-xs font-medium">Needs Attention</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </GlassCard>
      </div>
    </div>
  );
}
