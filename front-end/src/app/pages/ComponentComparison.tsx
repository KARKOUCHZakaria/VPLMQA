import { useEffect, useMemo, useState } from "react";
import {
  AlertTriangle,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  Columns2,
  ImageOff,
  Layers,
  Loader2,
  RefreshCw,
  Search,
  Sparkles,
  XCircle,
  ZoomIn,
} from "lucide-react";
import { toast, Toaster } from "sonner";
import { TopBar } from "../components/custom/TopBar";
import { GradientButton } from "../components/custom/GradientButton";
import { Badge } from "../components/ui/badge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "../components/ui/select";
import { Input } from "../components/ui/input";
import {
  designApi,
  type PageComparisonDto,
  type PageDto,
  type ProjectDto,
} from "../utils/designApi";

// ─── helpers ────────────────────────────────────────────────────────────────

const keyOf = (v?: string | null) =>
  (v || "").toLowerCase().replace(/[^a-z0-9]+/g, "");
const sourceOf = (p: PageDto) =>
  p.source?.toUpperCase() === "FIGMA" || p.figmaObjectPath ? "FIGMA" : "WEB";

type PageMapping = {
  key: string;
  name: string;
  pageId: string;
  figma?: PageDto;
  web?: PageDto;
};

const TOKEN_PAIRS = [
  ["Color", "figma_color", "code_color"],
  ["Spacing", "figma_spacing", "code_spacing"],
  ["Font size", "figma_font_size", "code_font_size"],
  ["Font weight", "figma_font_weight", "code_font_weight"],
  ["Radius", "figma_border_radius", "code_border_radius"],
  ["Width", "figma_width", "code_width"],
  ["Height", "figma_height", "code_height"],
] as const;

type TokenDiff = { label: string; figma: string; web: string };

const numericTolerance = (label: string, figma: number) => {
  if (label === "Width" || label === "Height") return Math.max(6, Math.abs(figma) * 0.03);
  if (label === "Spacing" || label === "Radius") return 2;
  if (label === "Font size") return 1;
  return 0;
};

const tokenNumber = (value: unknown) => {
  const match = String(value ?? "").match(/-?\d+(\.\d+)?/);
  return match ? Number.parseFloat(match[0]) : null;
};

const normalizeColorToken = (value: unknown) => {
  const raw = String(value ?? "").trim();
  if (!raw) return "";
  if (raw.startsWith("#")) {
    let hex = raw.slice(1).replace(/[^0-9a-f]/gi, "");
    if (hex.length === 3) hex = hex.split("").map((c) => c + c).join("");
    return hex.length >= 6 ? `#${hex.slice(0, 6).toUpperCase()}` : raw.toUpperCase();
  }
  return raw.toLowerCase();
};

function tokenChanged(label: string, figma: unknown, web: unknown) {
  const figmaRaw = String(figma ?? "").trim();
  const webRaw = String(web ?? "").trim();
  if (!figmaRaw || !webRaw) return false;
  if (label === "Color") return normalizeColorToken(figmaRaw) !== normalizeColorToken(webRaw);
  if (label === "Font weight") {
    const aliases: Record<string, string> = { normal: "400", regular: "400", medium: "500", bold: "700" };
    const left = aliases[figmaRaw.toLowerCase()] || figmaRaw.toLowerCase();
    const right = aliases[webRaw.toLowerCase()] || webRaw.toLowerCase();
    return left !== right;
  }
  const left = tokenNumber(figmaRaw);
  const right = tokenNumber(webRaw);
  if (left !== null && right !== null) return Math.abs(right - left) > numericTolerance(label, left);
  return figmaRaw.toLowerCase() !== webRaw.toLowerCase();
}

function getTokenDiffs(row: Record<string, any>): TokenDiff[] {
  return TOKEN_PAIRS.filter(([label, f, w]) => tokenChanged(label, row[f], row[w])).map(([label, f, w]) => ({
    label,
    figma: String(row[f] ?? "—"),
    web: String(row[w] ?? "—"),
  }));
}

function matchColor(prob: number) {
  if (prob >= 0.8) return "text-emerald-400";
  if (prob >= 0.5) return "text-amber-400";
  return "text-red-400";
}
function matchBg(prob: number) {
  if (prob >= 0.8) return "bg-emerald-500/10 border-emerald-500/30";
  if (prob >= 0.5) return "bg-amber-500/10 border-amber-500/30";
  return "bg-red-500/10 border-red-500/30";
}

// ─── sub-components ──────────────────────────────────────────────────────────

function EmptyState({ message }: { message: string }) {
  return (
    <div className="flex flex-col items-center justify-center min-h-72 gap-3 text-muted-foreground">
      <Columns2 className="w-10 h-10 opacity-30" />
      <p className="text-sm">{message}</p>
    </div>
  );
}

function LoadingState({ label }: { label: string }) {
  return (
    <div className="flex items-center justify-center min-h-72 gap-2 text-muted-foreground">
      <Loader2 className="w-5 h-5 animate-spin" />
      <span className="text-sm">{label}</span>
    </div>
  );
}

function AnnotatedImage({
  image,
  title,
  onZoom,
}: {
  image: string;
  title: string;
  onZoom: () => void;
}) {
  return (
    <>
      <div className="relative flex h-full w-full items-center justify-center">
        <div className="relative inline-block max-w-full">
        <img
          src={image}
          alt={title}
          className="block max-h-80 max-w-full object-contain transition-opacity group-hover:opacity-80"
        />
        </div>
      </div>
      <button
        className="absolute inset-0 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity"
        onClick={onZoom}
      >
        <span className="flex items-center gap-1.5 bg-background/80 backdrop-blur-sm rounded-lg px-3 py-1.5 text-sm font-medium">
          <ZoomIn className="w-4 h-4" /> Zoom
        </span>
      </button>
    </>
  );
}

function TokenDiffPill({ diff }: { diff: TokenDiff }) {
  return (
    <div className="flex items-center gap-1.5 text-xs rounded-full border px-2.5 py-1 bg-background">
      <span className="text-muted-foreground">{diff.label}:</span>
      <span className="text-sky-400 font-mono">{diff.figma}</span>
      <ChevronRight className="w-3 h-3 text-muted-foreground" />
      <span className="text-amber-400 font-mono">{diff.web}</span>
    </div>
  );
}

type ComponentRowType = Record<string, any>;
type PredictionType = {
  index: number;
  match: boolean;
  matchProbability: number;
  rawModelMatch?: boolean;
  rawModelProbability?: number;
  modelComponent?: string;
};

function ComponentCard({
  row,
  index,
  prediction,
  expanded,
  onToggle,
}: {
  row: ComponentRowType;
  index: number;
  prediction?: PredictionType;
  expanded: boolean;
  onToggle: () => void;
}) {
  const diffs = getTokenDiffs(row);
  const prob = prediction?.matchProbability ?? 0;
  const isMatch = prediction?.match ?? false;
  const pct = Math.round(prob * 100);

  return (
    <div
      className={`rounded-xl border transition-all duration-200 ${matchBg(prob)} hover:shadow-[0_0_20px_rgba(124,58,237,0.15)] cursor-pointer`}
      onClick={onToggle}
    >
      {/* header */}
      <div className="flex items-center gap-3 px-4 py-3">
        <div className="flex-1 min-w-0">
          <p className="font-medium text-sm truncate">{row.component || `Component ${index + 1}`}</p>
          <p className="text-xs text-muted-foreground truncate">{row.usage || row.web_locator || "—"}</p>
        </div>

        {/* match badge */}
        <div className="flex items-center gap-2 shrink-0">
          {isMatch ? (
            <span className="inline-flex items-center gap-1 text-xs text-emerald-400 font-medium">
              <CheckCircle2 className="w-3.5 h-3.5" /> Match
            </span>
          ) : (
            <span className="inline-flex items-center gap-1 text-xs text-red-400 font-medium">
              <XCircle className="w-3.5 h-3.5" /> Mismatch
            </span>
          )}
          <span className={`text-xs font-bold tabular-nums ${matchColor(prob)}`}>
            {pct}%
          </span>
          {diffs.length > 0 && (
            <Badge variant="outline" className="text-xs px-1.5 py-0 h-5 border-amber-500/40 text-amber-400">
              {diffs.length} diff{diffs.length > 1 ? "s" : ""}
            </Badge>
          )}
          <ChevronDown
            className={`w-4 h-4 text-muted-foreground transition-transform duration-200 ${expanded ? "rotate-180" : ""}`}
          />
        </div>
      </div>

      {/* expanded details */}
      {expanded && (
        <div
          className="border-t border-border/50 px-4 py-4 space-y-4"
          onClick={(e) => e.stopPropagation()}
        >
          {/* side-by-side token comparison */}
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <p className="text-xs font-semibold text-sky-400 flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-sky-400 inline-block" />
                Figma tokens
              </p>
              {TOKEN_PAIRS.map(([label, figmaKey]) => (
                <div key={label} className="flex items-center justify-between text-xs">
                  <span className="text-muted-foreground">{label}</span>
                  <span className={`font-mono ${tokenChanged(label, row[figmaKey], row[figmaKey.replace("figma_", "code_")]) ? "text-sky-300" : "text-muted-foreground"}`}>
                    {String(row[figmaKey] ?? "—")}
                  </span>
                </div>
              ))}
            </div>
            <div className="space-y-1.5">
              <p className="text-xs font-semibold text-amber-400 flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-amber-400 inline-block" />
                Web tokens
              </p>
              {TOKEN_PAIRS.map(([label, , webKey]) => (
                <div key={label} className="flex items-center justify-between text-xs">
                  <span className="text-muted-foreground">{label}</span>
                  <span className={`font-mono ${tokenChanged(label, row[webKey.replace("code_", "figma_")], row[webKey]) ? "text-amber-300" : "text-muted-foreground"}`}>
                    {String(row[webKey] ?? "—")}
                  </span>
                </div>
              ))}
            </div>
          </div>

          {/* token diffs summary */}
          {diffs.length > 0 && (
            <div>
              <p className="text-xs text-muted-foreground mb-2 flex items-center gap-1.5">
                <AlertTriangle className="w-3.5 h-3.5 text-amber-400" />
                Token differences
              </p>
              <div className="flex flex-wrap gap-2">
                {diffs.map((d) => <TokenDiffPill key={d.label} diff={d} />)}
              </div>
            </div>
          )}

          {/* locator */}
          {row.web_locator && (
            <div>
              <p className="text-xs text-muted-foreground mb-1">Web locator</p>
              <code className="text-xs font-mono bg-background/60 px-2 py-1 rounded border border-border break-all block">
                {row.web_locator}
              </code>
            </div>
          )}

          {/* mapping confidence */}
          <div className="flex items-center gap-3">
            <p className="text-xs text-muted-foreground">Mapping confidence</p>
            <div className="flex-1 h-1.5 rounded-full bg-border overflow-hidden">
              <div
                className="h-full rounded-full bg-gradient-to-r from-violet-500 to-purple-400 transition-all"
                style={{ width: `${Math.round(Number(row.mapping_confidence || 0) * 100)}%` }}
              />
            </div>
            <span className="text-xs font-mono text-muted-foreground">
              {Math.round(Number(row.mapping_confidence || 0) * 100)}%
            </span>
          </div>
          {row.mapping_reason && (
            <p className="text-xs text-muted-foreground">
              {String(row.mapping_reason)}
            </p>
          )}
          {prediction?.rawModelProbability !== undefined && (
            <p className="text-xs text-muted-foreground">
              ML raw score: {Math.round(Number(prediction.rawModelProbability) * 100)}%
              {prediction.modelComponent ? ` · model component: ${prediction.modelComponent}` : ""}
            </p>
          )}
        </div>
      )}
    </div>
  );
}

// ─── main page ───────────────────────────────────────────────────────────────

export function ComponentComparison() {
  const [projects, setProjects] = useState<ProjectDto[]>([]);
  const [pages, setPages] = useState<PageDto[]>([]);
  const [projectId, setProjectId] = useState("");
  const [mappingKey, setMappingKey] = useState("");
  const [comparison, setComparison] = useState<PageComparisonDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [comparing, setComparing] = useState(false);
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState<"all" | "match" | "mismatch">("all");
  const [expandedSet, setExpandedSet] = useState<Set<number>>(new Set());
  const [activeImage, setActiveImage] = useState<string | null>(null);
  const reliableRows = useMemo(() => {
    if (!comparison) return [];
    return comparison.rows
      .map((row, index) => ({
        row,
        index,
        prediction: comparison.predictions.find((p) => p.index === index),
      }))
      .filter(({ row }) => Number(row.mapping_confidence || 0) >= 0.7);
  }, [comparison]);

  // derive page mappings (pairs that have both figma + web)
  const mappings = useMemo(() => {
    const grouped = new Map<string, PageMapping>();
    pages
      .filter((p) => p.projectId === projectId)
      .forEach((page) => {
        const key = keyOf(page.name);
        if (!key) return;
        const item = grouped.get(key) || { key, name: page.name, pageId: page.id };
        if (sourceOf(page) === "FIGMA") item.figma = page;
        else { item.web = page; item.pageId = page.id; }
        grouped.set(key, item);
      });
    return Array.from(grouped.values()).filter((m) => m.figma && m.web);
  }, [pages, projectId]);

  const selectedMapping = mappings.find((m) => m.key === mappingKey);

  // filter + search components
  const filteredRows = useMemo(() => {
    if (!comparison) return [];
    return reliableRows
      .filter(({ row, prediction }) => {
        const matchesSearch =
          !search ||
          (row.component || "").toLowerCase().includes(search.toLowerCase()) ||
          (row.web_locator || "").toLowerCase().includes(search.toLowerCase());
        const matchesFilter =
          filter === "all" ||
          (filter === "match" && prediction?.match) ||
          (filter === "mismatch" && !prediction?.match);
        return matchesSearch && matchesFilter;
      });
  }, [comparison, reliableRows, search, filter]);

  const stats = useMemo(() => {
    if (!comparison) return { total: 0, matched: 0, mismatched: 0, tokenDiffs: 0 };
    const matched = reliableRows.filter(({ prediction }) => prediction?.match).length;
    const tokenDiffs = reliableRows.filter(({ row }) => getTokenDiffs(row).length > 0).length;
    return {
      total: reliableRows.length,
      matched,
      mismatched: reliableRows.length - matched,
      tokenDiffs,
    };
  }, [comparison, reliableRows]);

  // load workspace
  const loadWorkspace = async () => {
    try {
      setLoading(true);
      const loadedProjects = await designApi.getProjects();
      const activeProjectId = projectId || loadedProjects[0]?.id || "";
      const loadedPages = (
        await Promise.all(loadedProjects.map((p) => designApi.getProjectPages(p)))
      ).flat();
      setProjects(loadedProjects);
      setProjectId(activeProjectId);
      setPages(loadedPages);
    } catch (err: any) {
      toast.error("Failed to load workspace", { description: err?.message });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadWorkspace(); }, []);

  // auto-select first mapping when project changes
  useEffect(() => {
    if (!mappings.length) { setMappingKey(""); setComparison(null); return; }
    if (!mappings.some((m) => m.key === mappingKey)) setMappingKey(mappings[0].key);
  }, [mappings, mappingKey]);

  // run comparison
  const runComparison = async (refresh = false) => {
    if (!projectId || !selectedMapping) return;
    try {
      setComparing(true);
      setExpandedSet(new Set());
      const result = await designApi.comparePage({ projectId, pageId: selectedMapping.pageId, refresh });
      setComparison(result);
      if (refresh) toast.success(`${result.pageName} refreshed.`);
    } catch (err: any) {
      setComparison(null);
      toast.error("Comparison failed", { description: err?.message });
    } finally {
      setComparing(false);
    }
  };

  useEffect(() => { if (selectedMapping) runComparison(false); }, [projectId, mappingKey]);

  const toggleExpanded = (index: number) => {
    setExpandedSet((prev) => {
      const next = new Set(prev);
      next.has(index) ? next.delete(index) : next.add(index);
      return next;
    });
  };

  const expandAll = () => setExpandedSet(new Set(filteredRows.map((r) => r.index)));
  const collapseAll = () => setExpandedSet(new Set());

  return (
    <div className="min-h-screen bg-background">
      <Toaster />

      {/* Lightbox */}
      {activeImage && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm"
          onClick={() => setActiveImage(null)}
        >
          <img
            src={activeImage}
            alt="Preview"
            className="max-w-[90vw] max-h-[90vh] rounded-xl shadow-2xl border border-border object-contain"
            onClick={(e) => e.stopPropagation()}
          />
        </div>
      )}

      <TopBar title="Component Comparison" />

      <main className="mx-auto max-w-7xl px-6 py-6 space-y-6">
        {/* ── header ── */}
        <header className="flex flex-col gap-4 border-b border-border pb-5 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <h1 className="text-2xl font-semibold text-foreground flex items-center gap-2">
              <Layers className="w-6 h-6 text-violet-400" />
              Component comparison
            </h1>
            <p className="mt-1 text-sm text-muted-foreground">
              Inspect every mapped component — tokens, ML prediction, and locators.
            </p>
          </div>

          <div className="flex flex-wrap items-end gap-3">
            {/* Project */}
            <label className="space-y-1">
              <span className="block text-xs text-muted-foreground">Project</span>
              <Select value={projectId} onValueChange={setProjectId}>
                <SelectTrigger id="cmp-project-select" className="w-52 bg-card">
                  <SelectValue placeholder="Select project" />
                </SelectTrigger>
                <SelectContent>
                  {projects.map((p) => (
                    <SelectItem key={p.id} value={p.id}>{p.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </label>

            {/* Page */}
            <label className="space-y-1">
              <span className="block text-xs text-muted-foreground">Page</span>
              <Select value={mappingKey} onValueChange={setMappingKey}>
                <SelectTrigger id="cmp-page-select" className="w-52 bg-card">
                  <SelectValue placeholder="Select page" />
                </SelectTrigger>
                <SelectContent>
                  {mappings.map((m) => (
                    <SelectItem key={m.key} value={m.key}>{m.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </label>

            <GradientButton
              id="cmp-refresh-btn"
              variant="primary"
              onClick={() => runComparison(true)}
              disabled={!selectedMapping || comparing}
            >
              {comparing ? <Loader2 className="w-4 h-4 animate-spin" /> : <RefreshCw className="w-4 h-4" />}
              Refresh
            </GradientButton>
          </div>
        </header>

        {/* ── body ── */}
        {loading || (comparing && !comparison) ? (
          <LoadingState label="Loading comparison…" />
        ) : !selectedMapping ? (
          <EmptyState message="No page has both a Figma extraction and a Web extraction yet." />
        ) : !comparison ? (
          <EmptyState message="The comparison hasn't run yet — click Refresh to launch it." />
        ) : (
          <>
            {/* ── stats bar ── */}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
              {[
                { label: "Total", value: stats.total, color: "text-foreground", ring: "border-border" },
                { label: "Matched", value: stats.matched, color: "text-emerald-400", ring: "border-emerald-500/30" },
                { label: "Mismatches", value: stats.mismatched, color: "text-red-400", ring: "border-red-500/30" },
                { label: "Token diffs", value: stats.tokenDiffs, color: "text-amber-400", ring: "border-amber-500/30" },
              ].map(({ label, value, color, ring }) => (
                <div key={label} className={`rounded-xl border ${ring} bg-card/50 px-4 py-3 text-center`}>
                  <p className={`text-2xl font-bold ${color}`}>{value}</p>
                  <p className="text-xs text-muted-foreground mt-0.5">{label}</p>
                </div>
              ))}
            </div>

            {/* ── screenshots ── */}
            <div className="grid gap-4 lg:grid-cols-2">
              {[
                { title: "Figma reference", image: comparison.figmaImage, accent: "border-sky-500" },
                { title: "Web implementation", image: comparison.webImage, accent: "border-amber-500" },
              ].map(({ title, image, accent }) => (
                <figure key={title}>
                  <figcaption className="mb-2 text-sm font-medium flex items-center gap-2">
                    <span className={`w-2 h-2 rounded-full ${accent.replace("border-", "bg-")}`} />
                    {title}
                  </figcaption>
                  <div
                    className={`flex min-h-56 items-center justify-center overflow-hidden rounded-xl border-2 bg-card relative group ${accent}`}
                  >
                    {image ? (
                      <AnnotatedImage
                        image={image}
                        title={title}
                        onZoom={() => setActiveImage(image)}
                      />
                    ) : (
                      <div className="flex flex-col items-center gap-2 text-muted-foreground">
                        <ImageOff className="w-8 h-8 opacity-40" />
                        <p className="text-sm">Screenshot not available</p>
                      </div>
                    )}
                  </div>
                </figure>
              ))}
            </div>

            {/* ── AI summary ── */}
            {comparison.explanation?.summary && (
              <div className="rounded-xl border border-violet-500/20 bg-violet-500/5 px-5 py-4 flex gap-3">
                <Sparkles className="w-5 h-5 text-violet-400 shrink-0 mt-0.5" />
                <div className="space-y-1 min-w-0">
                  <p className="text-sm font-medium text-violet-300">AI Analysis</p>
                  <p className="text-sm text-muted-foreground leading-relaxed">{comparison.explanation.summary}</p>
                </div>
                {comparison.explanation.severity && (
                  <Badge
                    className={`shrink-0 self-start ${
                      comparison.explanation.severity === "high"
                        ? "bg-red-900 text-red-100"
                        : "bg-amber-900 text-amber-100"
                    }`}
                  >
                    {comparison.explanation.severity}
                  </Badge>
                )}
              </div>
            )}

            {/* ── component list ── */}
            <section>
              {/* toolbar */}
              <div className="flex flex-wrap items-center gap-3 mb-4">
                <h2 className="text-lg font-semibold flex-1">Components</h2>

                {/* search */}
                <div className="relative w-56">
                  <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-muted-foreground pointer-events-none" />
                  <Input
                    id="cmp-search"
                    placeholder="Search…"
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    className="pl-8 h-8 text-sm bg-card"
                  />
                </div>

                {/* filter */}
                {(["all", "match", "mismatch"] as const).map((f) => (
                  <button
                    key={f}
                    id={`cmp-filter-${f}`}
                    onClick={() => setFilter(f)}
                    className={`text-xs px-3 py-1.5 rounded-lg border transition-all ${
                      filter === f
                        ? "border-violet-500 bg-violet-500/20 text-violet-300"
                        : "border-border text-muted-foreground hover:border-border/80"
                    }`}
                  >
                    {f.charAt(0).toUpperCase() + f.slice(1)}
                  </button>
                ))}

                {/* expand/collapse */}
                <div className="flex gap-2">
                  <button
                    id="cmp-expand-all"
                    onClick={expandAll}
                    className="text-xs text-muted-foreground hover:text-foreground transition-colors"
                  >
                    Expand all
                  </button>
                  <span className="text-muted-foreground/30">|</span>
                  <button
                    id="cmp-collapse-all"
                    onClick={collapseAll}
                    className="text-xs text-muted-foreground hover:text-foreground transition-colors"
                  >
                    Collapse all
                  </button>
                </div>
              </div>

              {filteredRows.length === 0 ? (
                <EmptyState message="No components match your search / filter." />
              ) : (
                <div className="space-y-2">
                  {filteredRows.map(({ row, index, prediction }) => (
                    <ComponentCard
                      key={`${row.component}-${index}`}
                      row={row}
                      index={index}
                      prediction={prediction}
                      expanded={expandedSet.has(index)}
                      onToggle={() => toggleExpanded(index)}
                    />
                  ))}
                </div>
              )}
            </section>

            {/* ── recommendations ── */}
            {!!comparison.explanation?.recommendations?.length && (
              <section className="border-t border-border pt-5">
                <h2 className="mb-3 font-semibold flex items-center gap-2">
                  <AlertTriangle className="w-4 h-4 text-amber-400" />
                  Recommendations
                </h2>
                <div className="space-y-2">
                  {comparison.explanation.recommendations.map((item, i) => (
                    <p key={i} className="flex gap-2 text-sm text-muted-foreground">
                      <AlertTriangle className="mt-0.5 w-4 h-4 shrink-0 text-amber-500" />
                      {item}
                    </p>
                  ))}
                </div>
              </section>
            )}
          </>
        )}
      </main>
    </div>
  );
}
