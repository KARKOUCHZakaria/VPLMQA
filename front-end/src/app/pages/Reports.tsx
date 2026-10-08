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
  section: "execution" | "issues" | "quality";
};

const emptyMetrics: MetricsResponse = {
  mismatchRate: 0,
  testPassRate: 0,
  tokenCoverage: 0,
  healthScore: 0,
};

const pdfText = (value: unknown) => String(value ?? "")
  .normalize("NFD")
  .replace(/[\u0300-\u036f]/g, "")
  .replace(/[^\x20-\x7E]/g, "?")
  .replace(/([\\()])/g, "\\$1");

const splitPdfLines = (value: string, limit = 82) => {
  const words = value.split(/\s+/).filter(Boolean);
  const lines: string[] = [];
  let line = "";
  words.forEach((word) => {
    const candidate = line ? `${line} ${word}` : word;
    if (candidate.length > limit && line) {
      lines.push(line);
      line = word;
    } else {
      line = candidate;
    }
  });
  if (line) lines.push(line);
  return lines;
};

const downloadPdf = (filename: string, lines: string[]) => {
  const pageLines = 45;
  const pages = Array.from({ length: Math.max(1, Math.ceil(lines.length / pageLines)) }, (_, index) =>
    lines.slice(index * pageLines, (index + 1) * pageLines),
  );
  const fontObject = 3 + pages.length * 2;
  const objects = [
    "<< /Type /Catalog /Pages 2 0 R >>",
    `<< /Type /Pages /Kids [${pages.map((_, index) => `${3 + index * 2} 0 R`).join(" ")}] /Count ${pages.length} >>`,
  ];

  pages.forEach((page, index) => {
    const pageObject = 3 + index * 2;
    const contentObject = pageObject + 1;
    const stream = page.map((line, lineIndex) => {
      const y = 790 - lineIndex * 16;
      const fontSize = lineIndex === 0 ? 18 : 10;
      return `BT /F1 ${fontSize} Tf 56 ${y} Td (${pdfText(line)}) Tj ET`;
    }).join("\n");
    objects.push(`<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 ${fontObject} 0 R >> >> /Contents ${contentObject} 0 R >>`);
    objects.push(`<< /Length ${new TextEncoder().encode(stream).length} >>\nstream\n${stream}\nendstream`);
  });
  objects.push("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");

  let pdf = "%PDF-1.4\n";
  const offsets = [0];
  objects.forEach((object, index) => {
    offsets.push(new TextEncoder().encode(pdf).length);
    pdf += `${index + 1} 0 obj\n${object}\nendobj\n`;
  });
  const xref = new TextEncoder().encode(pdf).length;
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  offsets.slice(1).forEach((offset) => {
    pdf += `${String(offset).padStart(10, "0")} 00000 n \n`;
  });
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF`;

  const blob = new Blob([pdf], { type: "application/pdf" });
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

  const reports = useMemo<ReportRow[]>(() => [
    {
      name: "Live Test Execution Report",
      date: formatDate(executions[0]?.createdAt),
      type: "PDF",
      size: `${executions.length} runs`,
      section: "execution",
    },
    {
      name: "Live Issue Summary",
      date: formatDate(tickets[0]?.createdAt),
      type: "PDF",
      size: `${tickets.length} tickets`,
      section: "issues",
    },
    {
      name: "Live Quality Metrics",
      date: formatDate(),
      type: "PDF",
      size: `${Math.round(metrics.healthScore)} health`,
      section: "quality",
    },
  ], [executions, metrics.healthScore, tickets]);

  const buildPdfLines = (title: string, section: "full" | ReportRow["section"]) => {
    const lines = [
      title,
      `Project: ${selectedProject?.name || "Not selected"}`,
      `Generated: ${formatDate()}`,
      "",
    ];
    const includeQuality = section === "full" || section === "quality";
    const includeExecutions = section === "full" || section === "execution";
    const includeIssues = section === "full" || section === "issues";

    if (includeQuality) {
      lines.push("QUALITY METRICS", `Health score: ${Math.round(metrics.healthScore)}%`, `Test pass rate: ${Math.round(metrics.testPassRate)}%`, `Token coverage: ${Math.round(metrics.tokenCoverage)}%`, `Mismatch rate: ${Math.round(metrics.mismatchRate)}%`, "");
    }
    if (includeExecutions) {
      lines.push("RECENT EXECUTIONS");
      if (!executions.length) lines.push("No execution is available for this report.");
      executions.slice(0, 10).forEach((execution) => {
        lines.push(...splitPdfLines(`Execution ${execution.id.slice(0, 8)} | ${execution.status} | ${execution.executionTimeMs} ms | ${formatDate(execution.createdAt)}`));
        if (execution.errorMessage) lines.push(...splitPdfLines(`Error: ${execution.errorMessage}`));
      });
      lines.push("");
    }
    if (includeIssues) {
      lines.push("ISSUES AND TICKETS");
      if (!tickets.length) lines.push("No ticket is available for this report.");
      tickets.slice(0, 10).forEach((ticket) => {
        lines.push(...splitPdfLines(`${ticket.status} | ${ticket.severity} | ${ticket.title}`));
      });
      lines.push("");
    }
    if (section === "full") {
      lines.push("FEATURES");
      if (!features.length) lines.push("No feature is available for this report.");
      features.slice(0, 10).forEach((feature) => lines.push(...splitPdfLines(`${feature.status} | ${feature.name}`)));
    }
    return lines;
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
            <GradientButton variant="primary" onClick={() => downloadPdf("vplmqa-full-live-report.pdf", buildPdfLines("VPLMQA - Quality Assurance Report", "full"))}>
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
                  onClick={() => downloadPdf(`${report.name.toLowerCase().replaceAll(" ", "-")}.pdf`, buildPdfLines(report.name, report.section))}
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
                  <GradientButton variant="ghost" size="sm" onClick={() => downloadPdf(`${report.name.toLowerCase().replaceAll(" ", "-")}.pdf`, buildPdfLines(report.name, report.section))}>
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
