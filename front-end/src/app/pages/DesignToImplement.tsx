import { useEffect, useMemo, useState } from "react";
import { TopBar } from "../components/custom/TopBar";
import { GlassCard } from "../components/custom/GlassCard";
import { GradientButton } from "../components/custom/GradientButton";
import { Badge } from "../components/ui/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "../components/ui/table";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "../components/ui/select";
import { Play, FileCode, CheckCircle, XCircle, AlertCircle, Loader2, RefreshCw } from "lucide-react";
import { designApi, type PageComparisonDto, type PageDto, type ProjectDto } from "../utils/designApi";

interface DesignToken {
  name: string;
  value: string;
  type: string;
  preview?: string;
}

interface UIMismatch {
  id: string;
  component: string;
  category: string;
  page: string;
  designValue: string;
  actualValue: string;
  confidence: number;
  status: "pending" | "confirmed" | "ignored";
  screenshot?: string;
}

interface ValidatedToken {
  id: string;
  tokenName: string;
  value: string;
  page: string;
  component: string;
  validatedBy: string;
  date: string;
}

type PageMapping = {
  key: string;
  name: string;
  pageId: string;
  figma?: PageDto;
  web?: PageDto;
};

const tokenPairs = [
  ["Color", "figma_color", "code_color"],
  ["Spacing", "figma_spacing", "code_spacing"],
  ["Font size", "figma_font_size", "code_font_size"],
  ["Font weight", "figma_font_weight", "code_font_weight"],
  ["Border radius", "figma_border_radius", "code_border_radius"],
  ["Width", "figma_width", "code_width"],
  ["Height", "figma_height", "code_height"],
] as const;

const mappingKey = (value?: string | null) =>
  (value || "").toLowerCase().replace(/[^a-z0-9]+/g, "");

const pageSource = (page: PageDto) =>
  page.source?.toUpperCase() === "FIGMA" || page.figmaObjectPath ? "FIGMA" : "WEB";

const normalizeTokenValue = (value: unknown, category: string) => {
  const raw = String(value ?? "").trim();
  if (!raw) return "";
  if (category === "Color") return raw.replace(/\s+/g, "").toUpperCase();
  if (["Spacing", "Font size", "Border radius", "Width", "Height"].includes(category)) {
    const numeric = Number.parseFloat(raw);
    return Number.isFinite(numeric) ? String(Math.round(numeric * 100) / 100) : raw.toLowerCase();
  }
  return raw.toLowerCase().replace(/^(normal|regular)$/, "400").replace(/^medium$/, "500").replace(/^bold$/, "700");
};

const tokenNumber = (value: unknown) => {
  const numeric = Number.parseFloat(String(value ?? "").replace(",", "."));
  return Number.isFinite(numeric) ? numeric : null;
};

const numericTolerance = (category: string, figma: number, web: number) => {
  if (category === "Font size") return 1;
  if (category === "Spacing" || category === "Border radius") return 2;
  if (category === "Width" || category === "Height") return Math.max(6, Math.max(Math.abs(figma), Math.abs(web)) * 0.03);
  return 0;
};

const tokenChanged = (category: string, figma: unknown, web: unknown) => {
  const figmaRaw = String(figma ?? "").trim();
  const webRaw = String(web ?? "").trim();
  if (!figmaRaw || !webRaw) return false;
  if (["Spacing", "Font size", "Border radius", "Width", "Height"].includes(category)) {
    const figmaNumber = tokenNumber(figmaRaw);
    const webNumber = tokenNumber(webRaw);
    if (figmaNumber !== null && webNumber !== null) {
      return Math.abs(figmaNumber - webNumber) > numericTolerance(category, figmaNumber, webNumber);
    }
  }
  return normalizeTokenValue(figmaRaw, category) !== normalizeTokenValue(webRaw, category);
};

const displayTokenValue = (value: unknown, category: string) => {
  const raw = String(value ?? "").trim();
  if (!raw) return "Not extracted";
  return ["Spacing", "Font size", "Border radius", "Width", "Height"].includes(category)
    ? `${normalizeTokenValue(raw, category)}px`
    : raw;
};

function AnnotatedScreenshot({
  src,
  alt,
}: {
  src: string;
  alt: string;
}) {
  return (
    <div className="flex h-full w-full items-center justify-center">
      <div className="relative inline-block max-h-full max-w-full">
      <img src={src} alt={alt} className="block max-h-full max-w-full object-contain" />
      </div>
    </div>
  );
}

export function DesignToImplement() {
  const [projects, setProjects] = useState<ProjectDto[]>([]);
  const [workspacePages, setWorkspacePages] = useState<PageDto[]>([]);
  const [projectId, setProjectId] = useState("");
  const [selectedMappingKey, setSelectedMappingKey] = useState("");
  const [comparison, setComparison] = useState<PageComparisonDto | null>(null);
  const [pipelineError, setPipelineError] = useState("");
  const [loading, setLoading] = useState(true);
  const [comparing, setComparing] = useState(false);
  const [componentFilter, setComponentFilter] = useState("all");
  const [pageFilter, setPageFilter] = useState("all");
  const [screenshotPageFilter, setScreenshotPageFilter] = useState("all");

  const [mismatchStates, setMismatchStates] = useState<Record<string, UIMismatch["status"]>>({});
  const [validatedTokens, setValidatedTokens] = useState<ValidatedToken[]>([]);

  const mappings = useMemo(() => {
    const grouped = new Map<string, PageMapping>();
    workspacePages.filter((page) => page.projectId === projectId).forEach((page) => {
      const key = mappingKey(page.name);
      if (!key) return;
      const entry = grouped.get(key) || { key, name: page.name, pageId: page.id };
      if (pageSource(page) === "FIGMA") entry.figma = page;
      else {
        entry.web = page;
        entry.pageId = page.id;
      }
      grouped.set(key, entry);
    });
    return Array.from(grouped.values()).filter((entry) => entry.figma && entry.web);
  }, [workspacePages, projectId]);

  const selectedMapping = mappings.find((entry) => entry.key === selectedMappingKey);
  const pipelineStatus = selectedMapping?.web?.scanStatus || "WAITING_FOR_EXTRACTION";

  const loadWorkspace = async (quiet = false) => {
    try {
      if (!quiet) setLoading(true);
      const loadedProjects = await designApi.getProjects();
      const loadedPages = (await Promise.all(loadedProjects.map((project) => designApi.getProjectPages(project)))).flat();
      setProjects(loadedProjects);
      setProjectId((current) => current || loadedProjects[0]?.id || "");
      setWorkspacePages(loadedPages);
      setPipelineError("");
    } catch (error: any) {
      setPipelineError(error?.message || "Failed to load comparison workspace.");
    } finally {
      if (!quiet) setLoading(false);
    }
  };

  useEffect(() => {
    loadWorkspace();
    const timer = window.setInterval(() => loadWorkspace(true), 4000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    if (!mappings.length) {
      setSelectedMappingKey("");
      setComparison(null);
      return;
    }
    if (!mappings.some((entry) => entry.key === selectedMappingKey)) {
      setSelectedMappingKey(mappings[0].key);
    }
  }, [mappings, selectedMappingKey]);

  const loadComparison = async (refresh = false) => {
    if (!projectId || !selectedMapping) return;
    try {
      setComparing(true);
      const result = await designApi.comparePage({ projectId, pageId: selectedMapping.pageId, refresh });
      setComparison(result);
      setPipelineError("");
    } catch (error: any) {
      setPipelineError(error?.message || "Comparison is not ready yet.");
      if (refresh) setComparison(null);
    } finally {
      setComparing(false);
    }
  };

  useEffect(() => {
    setComparison(null);
    setPageFilter(selectedMapping?.name || "all");
    setScreenshotPageFilter(selectedMapping?.name || "all");
  }, [projectId, selectedMappingKey, selectedMapping?.pageId]);

  useEffect(() => {
    if (pipelineStatus !== "COMPARISON_READY" && pipelineStatus.startsWith("COMPARISON_")) {
      setComparison(null);
    }
  }, [pipelineStatus]);

  useEffect(() => {
    if (selectedMapping && pipelineStatus === "COMPARISON_READY" && !comparison && !comparing) {
      loadComparison(false);
    }
  }, [pipelineStatus, selectedMappingKey, selectedMapping?.pageId]);

  const designTokens = useMemo<DesignToken[]>(() => {
    if (!comparison) return [];
    const tokens = new Map<string, DesignToken>();
    comparison.rows.forEach((row) => {
      const values: DesignToken[] = [];
      if (row.figma_color) {
        values.push({ name: `${row.component} color`, value: String(row.figma_color), type: "color", preview: row.figma_color });
      }
      if (Number(row.figma_spacing) > 0) {
        values.push({ name: `${row.component} spacing`, value: `${row.figma_spacing}px`, type: "spacing" });
      }
      if (Number(row.figma_font_size) > 0 && row.figma_font_weight) {
        values.push({ name: `${row.component} font`, value: `${row.figma_font_size}px / ${row.figma_font_weight}`, type: "typography" });
      }
      if (Number(row.figma_border_radius) > 0) {
        values.push({ name: `${row.component} radius`, value: `${row.figma_border_radius}px`, type: "border-radius" });
      }
      values.forEach((token) => tokens.set(`${token.name}:${token.value}`, token));
    });
    return Array.from(tokens.values()).slice(0, 12);
  }, [comparison]);

  const mismatches = useMemo<UIMismatch[]>(() => {
    if (!comparison) return [];
    const tokenMismatches = comparison.rows.flatMap((row, index) => {
      return tokenPairs.flatMap(([label, figmaKey, webKey]) => {
        if (!tokenChanged(label, row[figmaKey], row[webKey])) return [];
        const id = `${row.figma_component_id || index}-${row.web_component_id || index}-${label}`;
        return [{
          id,
          component: String(row.uniqueName || row.component || `Component ${index + 1}`),
          category: label,
          page: comparison.pageName,
          designValue: displayTokenValue(row[figmaKey], label),
          actualValue: displayTokenValue(row[webKey], label),
          confidence: Number(row.mapping_confidence ?? 0),
          status: mismatchStates[id] || "pending",
        }];
      });
    });
    const visualMismatches = (comparison.explanation?.visualDifferences || []).map((difference, index) => {
      const id = `visual-${comparison.pageId}-${index}`;
      return {
        id,
        component: difference.component || "Page visual appearance",
        category: difference.category === "visual" ? "Visual appearance" : difference.category || "Visual appearance",
        page: comparison.pageName,
        designValue: difference.figma || "Figma screenshot",
        actualValue: difference.web || "Web screenshot",
        confidence: 1,
        status: mismatchStates[id] || "pending",
      } satisfies UIMismatch;
    });
    return [...visualMismatches, ...tokenMismatches];
  }, [comparison, mismatchStates]);

  const handleConfirmIssue = (mismatchId: string) => {
    setMismatchStates((states) => ({ ...states, [mismatchId]: "confirmed" }));
    
    const mismatch = mismatches.find((m) => m.id === mismatchId);
    if (mismatch) {
      const newToken: ValidatedToken = {
        id: Date.now().toString(),
        tokenName: `${mismatch.component.toLowerCase().replace(/[^a-z0-9]+/g, "-")}-${mismatch.category.toLowerCase().replace(/\s+/g, "-")}`,
        value: mismatch.designValue,
        page: mismatch.page,
        component: mismatch.component,
        validatedBy: "Zakaria",
        date: new Date().toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' }),
      };
      setValidatedTokens([...validatedTokens, newToken]);
    }
  };

  const handleIgnoreIssue = (mismatchId: string) => {
    setMismatchStates((states) => ({ ...states, [mismatchId]: "ignored" }));
  };

  // Get unique components and pages for filters
  const components = ["all", ...Array.from(new Set(mismatches.map(m => m.component)))];
  const pages = ["all", ...mappings.map((entry) => entry.name)];

  // Filter mismatches based on selected filters
  const filteredMismatches = mismatches.filter((mismatch) => {
    const componentMatch = componentFilter === "all" || mismatch.component === componentFilter;
    const pageMatch = pageFilter === "all" || mismatch.page === pageFilter;
    return componentMatch && pageMatch;
  });

  // Filter validated tokens based on page filter
  const filteredValidatedTokens = validatedTokens.filter((token) => {
    return pageFilter === "all" || token.page === pageFilter;
  });
  const comparisonStats = {
    components: comparison?.rows.length || 0,
    matched: comparison?.predictions.filter((prediction) => prediction.match).length || 0,
    mismatches: mismatches.length,
    lowConfidence: comparison?.rows.filter((row) => Number(row.mapping_confidence ?? 0) < 0.7).length || 0,
  };

  return (
    <div className="min-h-screen bg-background">
      <TopBar title="Design Comparison" />

      <div className="px-8 py-6 space-y-6 max-w-7xl">
      <GlassCard className="p-5">
        <div className="flex flex-wrap items-end gap-4">
          <div className="space-y-1">
            <label className="text-xs text-muted-foreground">Project</label>
            <Select value={projectId} onValueChange={setProjectId}>
              <SelectTrigger className="w-[220px] bg-card border-border text-foreground">
                <SelectValue placeholder="Select project" />
              </SelectTrigger>
              <SelectContent>
                {projects.map((project) => <SelectItem key={project.id} value={project.id}>{project.name}</SelectItem>)}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-1">
            <label className="text-xs text-muted-foreground">Mapped page</label>
            <Select value={selectedMappingKey} onValueChange={setSelectedMappingKey}>
              <SelectTrigger className="w-[220px] bg-card border-border text-foreground">
                <SelectValue placeholder="Waiting for mapped pages" />
              </SelectTrigger>
              <SelectContent>
                {mappings.map((entry) => <SelectItem key={entry.key} value={entry.key}>{entry.name}</SelectItem>)}
              </SelectContent>
            </Select>
          </div>
          <Badge variant="outline" className="h-10 px-3 border-border text-foreground">
            {loading || comparing ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : null}
            {pipelineStatus.replaceAll("_", " ")}
          </Badge>
          <GradientButton onClick={() => loadComparison(true)} disabled={!selectedMapping || comparing}>
            {comparing ? <Loader2 className="w-4 h-4 animate-spin" /> : <RefreshCw className="w-4 h-4" />}
            Refresh Comparison
          </GradientButton>
        </div>
        {pipelineError && <p className="mt-3 text-sm text-yellow-400">{pipelineError}</p>}
      </GlassCard>

      {/* Section 1: Design Tokens */}
      <GlassCard className="p-6">
        <div className="mb-4">
          <h3 className="text-lg font-semibold">Design Tokens</h3>
          <p className="text-sm text-muted-foreground mt-1">
            Extracted design tokens from your Figma file
          </p>
        </div>
        <div>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {!designTokens.length && (
              <p className="text-sm text-muted-foreground md:col-span-2 lg:col-span-3">
                Tokens will appear here when the automatic extraction and comparison pipeline is ready.
              </p>
            )}
            {designTokens.map((token, index) => (
              <div
                key={index}
                className="border border-border rounded-lg p-4 bg-card/50 hover:bg-card transition-colors"
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="flex-1">
                    <p className="text-sm font-medium text-foreground mb-1">{token.name}</p>
                    <p className="text-lg font-mono text-foreground">{token.value}</p>
                  </div>
                  {token.preview && (
                    <div
                      className="w-10 h-10 rounded border border-border"
                      style={{ backgroundColor: token.preview }}
                    />
                  )}
                </div>
                <Badge variant="outline" className="mt-2 text-xs border-border text-muted-foreground">
                  {token.type}
                </Badge>
              </div>
            ))}
          </div>
        </div>
      </GlassCard>

      {/* Section 2: UI Comparison Results with Filters */}
      <GlassCard className="p-6">
        <div className="mb-4">
          <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
            <div>
              <h3 className="text-lg font-semibold">UI Comparison Results</h3>
              <p className="text-sm text-muted-foreground mt-1">
                Extracted token differences for the mapped components
              </p>
            </div>
            <div className="flex flex-wrap gap-3">
              <div className="space-y-1">
                <label className="text-xs text-muted-foreground">Filter by Component</label>
                <Select value={componentFilter} onValueChange={setComponentFilter}>
                  <SelectTrigger className="w-[180px] bg-card border-border text-foreground">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {components.map((comp) => (
                      <SelectItem key={comp} value={comp}>
                        {comp === "all" ? "All Components" : comp}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1">
                <label className="text-xs text-muted-foreground">Filter by Page</label>
                <Select value={pageFilter} onValueChange={setPageFilter}>
                  <SelectTrigger className="w-[180px] bg-card border-border text-foreground">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {pages.map((page) => (
                      <SelectItem key={page} value={page}>
                        {page === "all" ? "All Pages" : page}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
          </div>
        </div>
        <div className="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-4">
          {[
            { label: "Mapped components", value: comparisonStats.components, tone: "text-foreground" },
            { label: "ML matches", value: comparisonStats.matched, tone: "text-green-400" },
            { label: "Token differences", value: comparisonStats.mismatches, tone: "text-amber-400" },
            { label: "Review mapping", value: comparisonStats.lowConfidence, tone: "text-red-400" },
          ].map((stat) => (
            <div key={stat.label} className="border border-border bg-card/40 p-3">
              <p className={`text-xl font-semibold ${stat.tone}`}>{stat.value}</p>
              <p className="mt-1 text-xs text-muted-foreground">{stat.label}</p>
            </div>
          ))}
        </div>
        <div>
          <div className="border border-border rounded-lg overflow-hidden">
            <Table>
              <TableHeader>
                <TableRow className="border-border hover:bg-card/50">
                  <TableHead className="text-foreground">Component Name</TableHead>
                  <TableHead className="text-foreground">Token</TableHead>
                  <TableHead className="text-foreground">Design Value (Figma)</TableHead>
                  <TableHead className="text-foreground">Actual Value (Web)</TableHead>
                  <TableHead className="text-foreground">Mapping</TableHead>
                  <TableHead className="text-foreground">Status</TableHead>
                  <TableHead className="text-foreground">Tester Validation</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {filteredMismatches.length === 0 ? (
                  <TableRow className="border-border">
                    <TableCell colSpan={7} className="text-center text-muted-foreground py-8">
                      No mismatches found for the selected filters
                    </TableCell>
                  </TableRow>
                ) : (
                  filteredMismatches.map((mismatch) => (
                    <TableRow key={mismatch.id} className="border-border hover:bg-card/50">
                      <TableCell className="font-medium text-foreground">
                        <p>{mismatch.component}</p>
                        <p className="mt-1 text-xs font-normal text-muted-foreground">{mismatch.page}</p>
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline" className="border-border text-foreground">{mismatch.category}</Badge>
                      </TableCell>
                      <TableCell className="font-mono text-sm text-foreground">
                        {mismatch.designValue}
                      </TableCell>
                      <TableCell className="font-mono text-sm text-foreground">
                        {mismatch.actualValue}
                      </TableCell>
                      <TableCell>
                        <Badge
                          variant="outline"
                          className={mismatch.confidence >= 0.8
                            ? "border-green-500/40 text-green-400"
                            : mismatch.confidence >= 0.7
                              ? "border-yellow-500/40 text-yellow-400"
                              : "border-red-500/40 text-red-400"}
                        >
                          {Math.round(mismatch.confidence * 100)}%
                        </Badge>
                      </TableCell>
                      <TableCell>
                        {mismatch.status === "pending" && (
                          <Badge className="bg-yellow-900 text-yellow-200 hover:bg-yellow-900">
                            <AlertCircle className="w-3 h-3 mr-1" />
                            Mismatch Detected
                          </Badge>
                        )}
                        {mismatch.status === "confirmed" && (
                          <Badge className="bg-green-900 text-green-200 hover:bg-green-900">
                            <CheckCircle className="w-3 h-3 mr-1" />
                            Confirmed
                          </Badge>
                        )}
                        {mismatch.status === "ignored" && (
                          <Badge className="bg-card text-muted-foreground hover:bg-card">
                            <XCircle className="w-3 h-3 mr-1" />
                            Ignored
                          </Badge>
                        )}
                      </TableCell>
                      <TableCell>
                        <div className="flex gap-2">
                          <GradientButton
                            size="sm"
                            variant="success"
                            onClick={() => handleConfirmIssue(mismatch.id)}
                            disabled={mismatch.status !== "pending"}
                          >
                            Confirm Issue
                          </GradientButton>
                          <GradientButton
                            size="sm"
                            variant="ghost"
                            onClick={() => handleIgnoreIssue(mismatch.id)}
                            disabled={mismatch.status !== "pending"}
                          >
                            Ignore
                          </GradientButton>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </div>
        </div>
      </GlassCard>

      {/* Section 3: Validated Tokens */}
      <GlassCard className="p-6">
        <div className="mb-4">
          <h3 className="text-lg font-semibold">Validated Tokens</h3>
          <p className="text-sm text-muted-foreground mt-1">
            Design tokens generated from confirmed issues
          </p>
        </div>
        <div>
          <div className="border border-border rounded-lg overflow-hidden">
            <Table>
              <TableHeader>
                <TableRow className="border-border hover:bg-card/50">
                  <TableHead className="text-foreground">Token Name</TableHead>
                  <TableHead className="text-foreground">Value</TableHead>
                  <TableHead className="text-foreground">Page</TableHead>
                  <TableHead className="text-foreground">Component</TableHead>
                  <TableHead className="text-foreground">Validated By</TableHead>
                  <TableHead className="text-foreground">Validation Date</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {filteredValidatedTokens.map((token) => (
                  <TableRow key={token.id} className="border-border hover:bg-card/50">
                    <TableCell className="font-mono text-sm text-blue-400">
                      {token.tokenName}
                    </TableCell>
                    <TableCell className="font-mono text-sm text-foreground">
                      {token.value}
                    </TableCell>
                    <TableCell className="text-foreground">{token.page}</TableCell>
                    <TableCell className="text-foreground">{token.component}</TableCell>
                    <TableCell className="text-foreground">{token.validatedBy}</TableCell>
                    <TableCell className="text-muted-foreground text-sm">{token.date}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </div>
      </GlassCard>

      {/* Section 4: Execution Actions */}
      <GlassCard className="p-6">
        <div className="mb-4">
          <h3 className="text-lg font-semibold">Execution Actions</h3>
          <p className="text-sm text-muted-foreground mt-1">
            Run tests and generate tokens
          </p>
        </div>
        <div>
          <div className="flex gap-4">
            <GradientButton variant="primary" onClick={() => loadComparison(true)} disabled={!selectedMapping || comparing}>
              <FileCode className="w-4 h-4 mr-2" />
              Refresh Tokens
            </GradientButton>
            <GradientButton variant="success">
              Generate E2E Tests
            </GradientButton>
            <GradientButton variant="primary">
              <Play className="w-4 h-4 mr-2" />
              Run Tests
            </GradientButton>
          </div>
        </div>
      </GlassCard>

      {/* Section 5: AI Analysis Panel with Screenshot Filter */}
      <GlassCard className="p-6">
        <div className="mb-4">
          <div className="flex items-start justify-between">
            <div>
              <h3 className="text-lg font-semibold">Problems Detected by AI</h3>
              <p className="text-sm text-muted-foreground mt-1">
                Visual comparison between Figma design and web application
              </p>
            </div>
            <div className="flex items-end gap-3">
              {comparison?.explanation?.provider && (
                <Badge variant="outline" className="h-10 border-border px-3 text-foreground">
                  {comparison.explanation.provider === "deterministic" ? "Token analysis only" : comparison.explanation.provider}
                </Badge>
              )}
              <div className="space-y-1">
                <label className="text-xs text-muted-foreground">Filter by Page</label>
                <Select value={screenshotPageFilter} onValueChange={setScreenshotPageFilter}>
                  <SelectTrigger className="w-[180px] bg-card border-border text-foreground">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {pages.map((page) => (
                      <SelectItem key={page} value={page}>
                        {page === "all" ? "All Pages" : page}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
          </div>
        </div>
        <div>
          {comparison?.explanation?.warning && (
            <div className="mb-4 flex items-start gap-3 border border-amber-500/30 bg-amber-500/10 p-3 text-sm text-amber-200">
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
              <div>
                <p className="font-medium">Visual AI analysis unavailable</p>
                <p className="mt-1 text-amber-200/80">{comparison.explanation.warning}</p>
              </div>
            </div>
          )}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-medium text-foreground">Figma Design Screenshot</h3>
                <Badge variant="outline" className="border-blue-500 text-blue-400">
                  Reference
                </Badge>
              </div>
              <div className="aspect-video bg-card rounded-lg border-2 border-blue-500/50 flex items-center justify-center relative overflow-hidden">
                {comparison?.figmaImage ? (
                  <AnnotatedScreenshot
                    src={comparison.figmaImage}
                    alt={`${comparison.pageName} Figma design`}
                  />
                ) : <div className="relative z-10 text-center">
                  <FileCode className="w-12 h-12 text-muted-foreground mx-auto mb-2" />
                  <p className="text-sm text-muted-foreground">Figma Design</p>
                  <p className="text-xs text-muted-foreground mt-1">
                    {screenshotPageFilter === "all" ? "All Pages" : screenshotPageFilter}
                  </p>
                </div>}
              </div>
            </div>

            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-medium text-foreground">Web Application Screenshot</h3>
                <Badge variant="outline" className="border-yellow-500 text-yellow-400">
                  Current
                </Badge>
              </div>
              <div className="aspect-video bg-card rounded-lg border-2 border-yellow-500/50 flex items-center justify-center relative overflow-hidden">
                {comparison?.webImage ? (
                  <AnnotatedScreenshot
                    src={comparison.webImage}
                    alt={`${comparison.pageName} web screenshot`}
                  />
                ) : <div className="relative z-10 text-center">
                  <AlertCircle className="w-12 h-12 text-muted-foreground mx-auto mb-2" />
                  <p className="text-sm text-muted-foreground">Web Application</p>
                  <p className="text-xs text-muted-foreground mt-1">
                    {screenshotPageFilter === "all" ? "All Pages" : screenshotPageFilter}
                  </p>
                </div>}
              </div>
            </div>
          </div>

          <div className="mt-6 p-4 bg-card/50 rounded-lg border border-border">
            <div className="flex items-start gap-3">
              <AlertCircle className="w-5 h-5 text-yellow-400 mt-0.5" />
              <div>
                <h4 className="text-sm font-medium text-foreground mb-1">
                  {filteredMismatches.length} Differences Detected
                  {screenshotPageFilter !== "all" && ` on ${screenshotPageFilter} page`}
                </h4>
                <ul className="text-sm text-muted-foreground space-y-1">
                  {filteredMismatches.slice(0, 3).map((mismatch) => (
                    <li key={mismatch.id}>
                      • {mismatch.component} mismatch ({mismatch.designValue} vs {mismatch.actualValue})
                    </li>
                  ))}
                </ul>
                {comparison?.explanation?.summary && (
                  <p className="mt-3 text-sm text-foreground">{comparison.explanation.summary}</p>
                )}
              </div>
            </div>
          </div>
        </div>
      </GlassCard>
      </div>
    </div>
  );
}
