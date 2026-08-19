import { useEffect, useMemo, useState } from "react";
import { TopBar } from "../components/custom/TopBar";
import { GlassCard } from "../components/custom/GlassCard";
import { GradientButton } from "../components/custom/GradientButton";
import { Badge } from "../components/ui/badge";
import { Download, FileText, Calendar, RefreshCw } from "lucide-react";
import { projectApi, Project } from "../utils/projectApi";
import { getFeatures, Feature, TestExecution } from "../utils/e2eApi";
import { ticketApi, Ticket } from "../utils/ticketApi";
import { analyticsApi, MetricsResponse } from "../utils/analyticsApi";
import { api } from "../utils/api";

type ReportRow = {
  name: string;
  date: string;
  type: string;
  size: string;
  payload: unknown;
};

const emptyMetrics: MetricsResponse = {
  mismatchRate: 0,
  testPassRate: 0,
  tokenCoverage: 0,
  healthScore: 0,
};

const downloadJson = (filename: string, payload: unknown) => {
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
};

const formatDate = (value?: string) =>
  value ? new Date(value).toLocaleString() : new Date().toLocaleString();

export function Reports() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState("");
  const [features, setFeatures] = useState<Feature[]>([]);
  const [executions, setExecutions] = useState<TestExecution[]>([]);
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [metrics, setMetrics] = useState<MetricsResponse>(emptyMetrics);
  const [loading, setLoading] = useState(true);

  const loadReports = async () => {
    setLoading(true);
    try {
      const loadedProjects = await projectApi.getProjects();
      const activeProjectId = selectedProjectId || loadedProjects[0]?.id || "";
      setProjects(loadedProjects);
      setSelectedProjectId(activeProjectId);

      const [featurePage, loadedExecutions, loadedTickets, loadedMetrics] = await Promise.all([
        getFeatures(),
        api.get<TestExecution[]>("/api/v1/executions"),
        activeProjectId ? ticketApi.listProjectTickets(activeProjectId).catch(() => []) : Promise.resolve([]),
        activeProjectId ? analyticsApi.getProjectMetrics(activeProjectId).catch(() => emptyMetrics) : Promise.resolve(emptyMetrics),
      ]);

      setFeatures(featurePage.content ?? []);
      setExecutions(loadedExecutions);
      setTickets(loadedTickets);
      setMetrics(loadedMetrics);
    } catch (error) {
      console.error("Failed to load report data", error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadReports();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedProjectId]);

  const selectedProject = projects.find((project) => project.id === selectedProjectId);

  const reports = useMemo<ReportRow[]>(() => {
    const executionPayload = {
      project: selectedProject,
      generatedAt: new Date().toISOString(),
      executions,
      features,
    };
    const ticketPayload = {
      project: selectedProject,
      generatedAt: new Date().toISOString(),
      tickets,
    };
    const qualityPayload = {
      project: selectedProject,
      generatedAt: new Date().toISOString(),
      metrics,
      executionCount: executions.length,
      ticketCount: tickets.length,
    };

    return [
      {
        name: "Live Test Execution Report",
        date: formatDate(executions[0]?.createdAt),
        type: "JSON",
        size: `${executions.length} runs`,
        payload: executionPayload,
      },
      {
        name: "Live Issue Summary",
        date: formatDate(tickets[0]?.createdAt),
        type: "JSON",
        size: `${tickets.length} tickets`,
        payload: ticketPayload,
      },
      {
        name: "Live Quality Metrics",
        date: formatDate(),
        type: "JSON",
        size: `${Math.round(metrics.healthScore)} health`,
        payload: qualityPayload,
      },
    ];
  }, [executions, features, metrics, selectedProject, tickets]);

  const fullPayload = {
    project: selectedProject,
    generatedAt: new Date().toISOString(),
    metrics,
    features,
    executions,
    tickets,
  };

  return (
    <div className="min-h-screen bg-background">
      <TopBar title="Reports" />
      <div className="px-8 py-6 space-y-6 max-w-7xl">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <h2 className="text-2xl font-bold text-foreground">Reports</h2>
            <p className="text-muted-foreground mt-1">Download reports generated from live gateway data</p>
          </div>
          <div className="flex flex-wrap gap-3">
            <select
              value={selectedProjectId}
              onChange={(event) => setSelectedProjectId(event.target.value)}
              className="h-10 rounded-md border border-border bg-card px-3 text-sm text-foreground"
            >
              {projects.map((project) => (
                <option key={project.id} value={project.id}>{project.name}</option>
              ))}
            </select>
            <GradientButton variant="ghost" onClick={loadReports} disabled={loading}>
              <RefreshCw className="w-4 h-4 mr-2" />
              Refresh
            </GradientButton>
            <GradientButton variant="primary" onClick={() => downloadJson("vplmqa-full-live-report.json", fullPayload)}>
              <FileText className="w-4 h-4 mr-2" />
              Generate Report
            </GradientButton>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <GlassCard className="p-6">
            <div className="mb-4">
              <h3 className="text-lg font-semibold">Quick Reports</h3>
            </div>
            <div className="space-y-2">
              {reports.map((report) => (
                <GradientButton
                  key={report.name}
                  variant="ghost"
                  className="w-full justify-start"
                  onClick={() => downloadJson(`${report.name.toLowerCase().replaceAll(" ", "-")}.json`, report.payload)}
                >
                  <Download className="w-4 h-4 mr-2" />
                  {report.name}
                </GradientButton>
              ))}
            </div>
          </GlassCard>

          <GlassCard className="lg:col-span-2 p-6">
            <div className="mb-4">
              <h3 className="text-lg font-semibold">Recent Live Reports</h3>
              <p className="text-sm text-muted-foreground mt-1">
                Generated from current executions, tickets, features, and analytics metrics
              </p>
            </div>
            <div className="space-y-3">
              {reports.map((report) => (
                <div key={report.name} className="flex items-center justify-between p-4 bg-card/50 rounded-lg hover:bg-card transition-colors">
                  <div className="flex items-center gap-4">
                    <div className="w-10 h-10 bg-blue-950 rounded-lg flex items-center justify-center border border-blue-900">
                      <FileText className="w-5 h-5 text-blue-400" />
                    </div>
                    <div>
                      <p className="font-medium text-foreground">{report.name}</p>
                      <div className="flex items-center gap-3 mt-1">
                        <span className="text-xs text-muted-foreground flex items-center gap-1">
                          <Calendar className="w-3 h-3" />
                          {report.date}
                        </span>
                        <Badge variant="outline" className="text-xs border-border text-muted-foreground">{report.type}</Badge>
                        <span className="text-xs text-muted-foreground">{report.size}</span>
                      </div>
                    </div>
                  </div>
                  <GradientButton variant="ghost" size="sm" onClick={() => downloadJson(`${report.name.toLowerCase().replaceAll(" ", "-")}.json`, report.payload)}>
                    <Download className="w-4 h-4" />
                  </GradientButton>
                </div>
              ))}
            </div>
          </GlassCard>
        </div>

        <GlassCard className="p-6">
          <div className="mb-4">
            <h3 className="text-lg font-semibold">Report Data Sources</h3>
            <p className="text-sm text-muted-foreground mt-1">Everything below is loaded from backend services through the gateway</p>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            {[
              ["Projects", projects.length],
              ["Features", features.length],
              ["Executions", executions.length],
              ["Tickets", tickets.length],
            ].map(([label, value]) => (
              <div key={label} className="border border-border rounded-lg p-6 text-center bg-card/40">
                <div className="w-12 h-12 bg-purple-950 rounded-full flex items-center justify-center mx-auto mb-3 border border-purple-900">
                  <FileText className="w-6 h-6 text-purple-400" />
                </div>
                <h3 className="font-semibold text-foreground mb-1">{label}</h3>
                <p className="text-2xl font-bold">{loading ? "..." : value}</p>
              </div>
            ))}
          </div>
        </GlassCard>
      </div>
    </div>
  );
}
