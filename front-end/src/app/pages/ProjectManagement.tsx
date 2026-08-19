import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
  Boxes,
  Braces,
  CheckCircle2,
  Code2,
  Database,
  FileJson,
  FolderKanban,
  Globe,
  Layers,
  Loader2,
  Pencil,
  Plus,
  RefreshCw,
  SearchCode,
  Upload,
  X,
} from "lucide-react";
import { TopBar } from "../components/custom/TopBar";
import { GlassCard } from "../components/custom/GlassCard";
import { GradientButton } from "../components/custom/GradientButton";
import { Badge } from "../components/ui/badge";
import { Input } from "../components/ui/input";
import { Label } from "../components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "../components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "../components/ui/tabs";
import { Toaster } from "../components/ui/sonner";
import { cn } from "../components/ui/utils";
import {
  designApi,
  type ExtractionArtifactDto,
  type MlDatasetDto,
  type PageDto,
  type ProjectDto,
  type WebComponentDto,
} from "../utils/designApi";

const statusClass = (status?: string | null) => {
  const normalized = (status || "PENDING").toUpperCase();
  if (normalized.includes("EXTRACTED") || normalized === "ACTIVE" || normalized === "PASSED") {
    return "bg-green-900 text-green-200";
  }
  if (normalized.includes("FAILED") || normalized.includes("ERROR")) {
    return "bg-red-900 text-red-200";
  }
  if (normalized.includes("RUNNING")) {
    return "bg-sky-900 text-sky-200";
  }
  return "bg-amber-900 text-amber-200";
};

const toPath = (url: string) => {
  try {
    return new URL(url).pathname || "/";
  } catch {
    return url.startsWith("/") ? url : "/login";
  }
};

const joinUrl = (baseUrl?: string, path = "/") => {
  const base = (baseUrl || "").replace(/\/+$/, "");
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  return `${base}${normalizedPath}`;
};

const getJson = (value: unknown) => JSON.stringify(value, null, 2);

const numberToken = (value: unknown) => {
  if (typeof value === "number") return Math.round(value);
  if (typeof value === "string") {
    const parsed = Number.parseFloat(value.replace("px", ""));
    return Number.isFinite(parsed) ? Math.round(parsed) : 0;
  }
  return 0;
};

const tokenValue = (component: WebComponentDto, key: string) => component.cssProperties?.[key] ?? "";

type DatasetPageOption = {
  key: string;
  name: string;
  pageId: string;
  hasFigma: boolean;
  hasWeb: boolean;
  figmaComponentCount: number;
  webComponentCount: number;
};

const pageSource = (page?: PageDto | null) => {
  const explicit = page?.source?.toUpperCase();
  if (explicit === "FIGMA" || explicit === "WEB") return explicit;
  if (page?.figmaObjectPath) return "FIGMA";
  return "WEB";
};

const matchKey = (value?: string | null) =>
  (value || "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "")
    .trim();

type BusyAction =
  | "load"
  | "project"
  | "project-update"
  | "figma-design"
  | "figma-pages"
  | "figma-components"
  | "web-page"
  | "web-extract"
  | "web-components"
  | "ml-dataset"
  | null;

export function ProjectManagement() {
  const [projects, setProjects] = useState<ProjectDto[]>([]);
  const [pages, setPages] = useState<PageDto[]>([]);
  const [components, setComponents] = useState<WebComponentDto[]>([]);
  const [busyAction, setBusyAction] = useState<BusyAction>("load");

  const [selectedProjectId, setSelectedProjectId] = useState("");
  const [selectedPageId, setSelectedPageId] = useState("");
  const [selectedDatasetPageId, setSelectedDatasetPageId] = useState("");
  const [projectPurpose, setProjectPurpose] = useState<"e2e" | "design">("e2e");
  const [projectName, setProjectName] = useState("VPLMQA Smoke");
  const [editingProject, setEditingProject] = useState<ProjectDto | null>(null);
  const [editProjectName, setEditProjectName] = useState("");
  const [editProjectUrl, setEditProjectUrl] = useState("");
  const [editProjectDescription, setEditProjectDescription] = useState("");
  const [figmaAccessToken, setFigmaAccessToken] = useState("");
  const [figmaFileKey, setFigmaFileKey] = useState("");
  const [webBaseUrl, setWebBaseUrl] = useState("http://front-end");
  const [webPagesDraft, setWebPagesDraft] = useState([{ tag: "Login", path: "/login" }]);
  const [lastArtifact, setLastArtifact] = useState<ExtractionArtifactDto | null>(null);
  const [lastMlDataset, setLastMlDataset] = useState<MlDatasetDto | null>(null);

  const selectedProject = projects.find((project) => project.id === selectedProjectId);
  const selectedPage = pages.find((page) => page.id === selectedPageId);

  const projectPages = useMemo(() => {
    if (!selectedProjectId) {
      return pages;
    }
    return pages.filter((page) => page.projectId === selectedProjectId);
  }, [pages, selectedProjectId]);
  const figmaPages = projectPages.filter((page) => pageSource(page) === "FIGMA");
  const webPages = projectPages.filter((page) => pageSource(page) === "WEB");
  const selectedFigmaPage = pageSource(selectedPage) === "FIGMA" ? selectedPage : figmaPages[0];
  const selectedWebPage = pageSource(selectedPage) === "WEB" ? selectedPage : webPages[0];

  const figmaComponents = components.filter((component) => component.source === "FIGMA" && component.page?.projectId === selectedProjectId);
  const webComponents = components.filter((component) => component.source === "WEB" && component.page?.projectId === selectedProjectId);
  const selectedPageComponents = selectedPage
    ? components.filter((component) => component.page?.id === selectedPage.id)
    : components;
  const datasetPageOptions = useMemo<DatasetPageOption[]>(() => {
    const groups = new Map<string, DatasetPageOption>();
    for (const page of projectPages) {
      const key = matchKey(page.name);
      if (!key) {
        continue;
      }
      const source = pageSource(page);
      const existing = groups.get(key) || {
        key,
        name: page.name,
        pageId: page.id,
        hasFigma: false,
        hasWeb: false,
        figmaComponentCount: 0,
        webComponentCount: 0,
      };
      if (source === "FIGMA") {
        existing.hasFigma = true;
        existing.pageId = page.id;
      }
      if (source === "WEB") {
        existing.hasWeb = true;
        if (!existing.hasFigma) {
          existing.pageId = page.id;
        }
      }
      groups.set(key, existing);
    }
    for (const component of components) {
      if (component.page?.projectId !== selectedProjectId) {
        continue;
      }
      const key = matchKey(component.page?.name);
      const group = groups.get(key);
      if (!group) {
        continue;
      }
      if (component.source === "FIGMA") {
        group.figmaComponentCount += 1;
      }
      if (component.source === "WEB") {
        group.webComponentCount += 1;
      }
    }
    return Array.from(groups.values()).sort((a, b) => a.name.localeCompare(b.name));
  }, [components, projectPages, selectedProjectId]);

  const projectJson = {
    project: selectedProject || null,
    page: selectedPage || null,
    figmaAccessToken: figmaAccessToken ? "Stored on project submit" : "Missing project token",
    figmaFileKey,
    webBaseUrl,
    webPagesDraft,
    figmaPages,
    webPages,
    components: selectedPageComponents,
    lastArtifact,
    lastMlDataset,
  };

  const mlPreview = figmaComponents.map((figmaComponent) => {
    const figmaPageKey = matchKey(figmaComponent.page?.name);
    const figmaComponentKey = matchKey(figmaComponent.componentName);
    const webComponent =
      webComponents.find((component) => matchKey(component.page?.name) === figmaPageKey && matchKey(component.componentName) === figmaComponentKey) ||
      webComponents.find((component) => matchKey(component.componentName) === figmaComponentKey);
    return {
      component: figmaComponent.componentName,
      figma_color: tokenValue(figmaComponent, "color"),
      code_color: webComponent ? tokenValue(webComponent, "color") || tokenValue(webComponent, "backgroundColor") : "",
      figma_spacing: numberToken(tokenValue(figmaComponent, "spacing")),
      code_spacing: webComponent ? numberToken(tokenValue(webComponent, "padding")) || numberToken(tokenValue(webComponent, "paddingTop")) : 0,
      figma_font_size: numberToken(tokenValue(figmaComponent, "fontSize")),
      code_font_size: webComponent ? numberToken(tokenValue(webComponent, "fontSize")) : 0,
      figma_font_weight: String(tokenValue(figmaComponent, "fontWeight") || ""),
      code_font_weight: webComponent ? String(tokenValue(webComponent, "fontWeight") || "") : "",
      figma_border_radius: numberToken(tokenValue(figmaComponent, "borderRadius")),
      code_border_radius: webComponent ? numberToken(tokenValue(webComponent, "borderRadius")) : 0,
      figma_width: numberToken(figmaComponent.boundingBox?.width),
      code_width: webComponent ? numberToken(webComponent.boundingBox?.width) : 0,
      figma_height: numberToken(figmaComponent.boundingBox?.height),
      code_height: webComponent ? numberToken(webComponent.boundingBox?.height) : 0,
      figma_page: figmaComponent.page?.name || "",
      web_page: webComponent?.page?.name || "",
      web_locator: webComponent?.testIdentifier || webComponent?.htmlID || webComponent?.cssSelector || "",
    };
  });

  const loadData = async () => {
    try {
      setBusyAction("load");
      const loadedProjects = await designApi.getProjects();
      setProjects(loadedProjects);

      const activeProjectId = selectedProjectId || loadedProjects[0]?.id || "";
      if (!selectedProjectId && activeProjectId) {
        setSelectedProjectId(activeProjectId);
      }

      const loadedPages = loadedProjects.length
        ? (await Promise.all(loadedProjects.map((project) => designApi.getProjectPages(project)))).flat()
        : [];
      setPages(loadedPages);

      const activePageId =
        selectedPageId ||
        loadedPages.find((page) => page.projectId === activeProjectId && page.name.toLowerCase() === "login")?.id ||
        loadedPages.find((page) => page.projectId === activeProjectId)?.id ||
        loadedPages[0]?.id ||
        "";
      if (!selectedPageId && activePageId) {
        setSelectedPageId(activePageId);
      }

      const loadedComponents = loadedPages.length
        ? (await Promise.all(loadedPages.map((page) => designApi.getPageComponents(page)))).flat()
        : [];
      setComponents(loadedComponents);
    } catch (error: any) {
      toast.error("Gateway request failed", {
        description: error?.message ?? "Check gateway, auth token, and project-service.",
      });
    } finally {
      setBusyAction(null);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  useEffect(() => {
    if (selectedDatasetPageId && !datasetPageOptions.some((option) => option.pageId === selectedDatasetPageId)) {
      setSelectedDatasetPageId("");
    }
  }, [datasetPageOptions, selectedDatasetPageId]);

  const createProject = async () => {
    if (!projectName.trim()) {
      toast.error("Project name is required.");
      return;
    }
    if (!webBaseUrl.trim()) {
      toast.error("Application URL is required.");
      return;
    }
    if (projectPurpose === "design" && (!figmaAccessToken.trim() || !figmaFileKey.trim())) {
      toast.error("Figma token and file key are required for design comparison.");
      return;
    }
    try {
      setBusyAction("project");
      const project = await designApi.createProject({
        name: projectName.trim(),
        url: webBaseUrl.trim(),
        description: projectPurpose === "e2e" ? "E2E testing project" : "Design comparison project",
        figmaProjectName: projectPurpose === "design" ? figmaAccessToken.trim() : "",
        figmaDesignFileId: projectPurpose === "design" ? figmaFileKey.trim() : "",
      });
      setSelectedProjectId(project.id);
      const validRows = projectPurpose === "design"
        ? webPagesDraft.filter((row) => row.tag.trim() && row.path.trim())
        : [];
      let firstPageId = "";
      for (const row of validRows) {
        const page = await designApi.createWebPage({
          projectId: project.id,
          tagName: row.tag.trim(),
          url: joinUrl(project.url || webBaseUrl, row.path.trim()),
        });
        firstPageId ||= page.id;
      }
      if (firstPageId) {
        setSelectedPageId(firstPageId);
      }
      toast.success(
        projectPurpose === "e2e"
          ? "E2E project created. You can now build and run features against its application URL."
          : validRows.length
          ? `Project created. Extraction and comparison started for ${validRows.length} page(s).`
          : "Project created. Figma extraction started."
      );
      await loadData();
    } catch (error: any) {
      toast.error("Project creation failed", { description: error?.message });
    } finally {
      setBusyAction(null);
    }
  };

  const openProjectEditor = (project: ProjectDto) => {
    setEditingProject(project);
    setEditProjectName(project.name || "");
    setEditProjectUrl(project.url || "");
    setEditProjectDescription(project.description || "");
    setSelectedProjectId(project.id);
  };

  const closeProjectEditor = () => {
    if (isBusy("project-update")) {
      return;
    }
    setEditingProject(null);
    setEditProjectName("");
    setEditProjectUrl("");
    setEditProjectDescription("");
  };

  const updateProject = async () => {
    if (!editingProject) {
      return;
    }
    if (!editProjectName.trim()) {
      toast.error("Project name is required.");
      return;
    }
    if (!editProjectUrl.trim()) {
      toast.error("Application URL is required.");
      return;
    }
    try {
      setBusyAction("project-update");
      const updatedProject = await designApi.updateProject(editingProject.id, {
        name: editProjectName.trim(),
        description: editProjectDescription.trim(),
        figmaDesignFileId: editingProject.figmaDesignFileId || "",
        url: editProjectUrl.trim(),
      });
      setProjects((current) => current.map((project) => (project.id === updatedProject.id ? updatedProject : project)));
      setSelectedProjectId(updatedProject.id);
      setWebBaseUrl(updatedProject.url || editProjectUrl.trim());
      setEditingProject(null);
      toast.success("Project URL updated.", {
        description: updatedProject.url || editProjectUrl.trim(),
      });
      await loadData();
    } catch (error: any) {
      toast.error("Project update failed", { description: error?.message });
    } finally {
      setBusyAction(null);
    }
  };

  const syncFigmaDesign = async () => {
    if (!selectedProjectId) {
      toast.error("Select or create a project first.");
      return;
    }
    try {
      setBusyAction("figma-design");
      setLastArtifact(null);
      const artifact = await designApi.extractFigmaDesign({ projectId: selectedProjectId });
      setLastArtifact(artifact);
      toast.success(`Extracted real Figma design with ${artifact.componentCount} page(s).`);
      await loadData();
    } catch (error: any) {
      toast.error("Figma design save failed", { description: error?.message });
    } finally {
      setBusyAction(null);
    }
  };

  const extractFigmaPages = async () => {
    if (!selectedProjectId) {
      toast.error("Select or create a project first.");
      return;
    }
    try {
      setBusyAction("figma-pages");
      const artifact = await designApi.extractFigmaDesign({ projectId: selectedProjectId });
      setLastArtifact(artifact);
      toast.success(`Extracted ${artifact.componentCount} Figma page(s).`);
      await loadData();
    } catch (error: any) {
      toast.error("Figma page extraction failed", { description: error?.message });
    } finally {
      setBusyAction(null);
    }
  };

  const extractFigmaComponents = async () => {
    const figmaPageId = selectedFigmaPage?.id;
    if (!selectedProjectId || !figmaPageId) {
      toast.error("Select a project and page first.");
      return;
    }
    try {
      setBusyAction("figma-components");
      const artifact = await designApi.extractFigmaComponents({
        projectId: selectedProjectId,
        pageId: figmaPageId,
      });
      setLastArtifact(artifact);
      toast.success(`Stored ${artifact.componentCount} Figma components.`);
      await loadData();
    } catch (error: any) {
      toast.error("Figma component extraction failed", { description: error?.message });
    } finally {
      setBusyAction(null);
    }
  };

  const addWebDraftRow = () => {
    setWebPagesDraft((rows) => [...rows, { tag: `Page ${rows.length + 1}`, path: "/" }]);
  };

  const updateWebDraftRow = (index: number, patch: Partial<{ tag: string; path: string }>) => {
    setWebPagesDraft((rows) => rows.map((row, rowIndex) => (rowIndex === index ? { ...row, ...patch } : row)));
  };

  const removeWebDraftRow = (index: number) => {
    setWebPagesDraft((rows) => rows.filter((_, rowIndex) => rowIndex !== index));
  };

  const extractWebPages = async () => {
    if (!selectedProjectId) {
      toast.error("Select or create a project first.");
      return;
    }
    try {
      setBusyAction("web-page");
      const validRows = webPagesDraft.filter((row) => row.tag.trim() && row.path.trim());
      if (!validRows.length) {
        throw new Error("Add at least one web page tag and path.");
      }

      let firstPageId = "";
      for (const row of validRows) {
        const page = await designApi.createWebPage({
          projectId: selectedProjectId,
          tagName: row.tag.trim(),
          url: joinUrl(selectedProject?.url || webBaseUrl, row.path.trim()),
        });
        firstPageId ||= page.id;
      }
      if (firstPageId) {
        setSelectedPageId(firstPageId);
      }
      toast.success(`Saved ${validRows.length} web page(s).`);
      await loadData();
    } catch (error: any) {
      toast.error("Web page save failed", { description: error?.message });
    } finally {
      setBusyAction(null);
    }
  };

  const extractWebComponents = async () => {
    const webPageId = selectedWebPage?.id;
    if (!selectedProjectId || !webPageId) {
      toast.error("Select a project and page first.");
      return;
    }
    try {
      setBusyAction("web-components");
      const artifact = await designApi.extractWebPage({ projectId: selectedProjectId, pageId: webPageId });
      setLastArtifact(artifact);
      toast.success(`Extracted ${artifact.componentCount} web components.`);
      await loadData();
    } catch (error: any) {
      toast.error("Web component extraction failed", { description: error?.message });
    } finally {
      setBusyAction(null);
    }
  };

  const extractSelectedWebPage = async () => {
    const webPageId = selectedWebPage?.id;
    if (!selectedProjectId || !webPageId) {
      toast.error("Save and select a web page first.");
      return;
    }
    try {
      setBusyAction("web-extract");
      const artifact = await designApi.extractWebPage({ projectId: selectedProjectId, pageId: webPageId });
      setLastArtifact(artifact);
      toast.success(`Extracted ${artifact.pageName} with ${artifact.componentCount} component(s).`);
      await loadData();
    } catch (error: any) {
      toast.error("Web page extraction failed", { description: error?.message });
    } finally {
      setBusyAction(null);
    }
  };

  const generateMlDataset = async () => {
    if (!selectedProjectId) {
      toast.error("Select or create a project first.");
      return;
    }
    try {
      setBusyAction("ml-dataset");
      const dataset = await designApi.generateMlDataset({
        projectId: selectedProjectId,
        pageId: selectedDatasetPageId || undefined,
      });
      setLastMlDataset(dataset);
      const selectedDatasetOption = datasetPageOptions.find((option) => option.pageId === selectedDatasetPageId);
      const pageLabel = selectedDatasetOption
        ? selectedDatasetOption.name
        : "all pages";
      toast.success(`Generated ${dataset.rowCount} ML dataset row(s) for ${pageLabel}.`);
      await loadData();
    } catch (error: any) {
      toast.error("ML dataset generation failed", { description: error?.message });
    } finally {
      setBusyAction(null);
    }
  };

  const isBusy = (action?: BusyAction) => (action ? busyAction === action : Boolean(busyAction));

  return (
    <div className="min-h-screen bg-background">
      <Toaster />
      <TopBar title="Project Management" />

      {editingProject && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4 backdrop-blur-sm">
          <GlassCard className="w-full max-w-2xl p-5 shadow-2xl">
            <div className="mb-5 flex items-start justify-between gap-4">
              <div>
                <h3 className="text-xl font-semibold">Update Project</h3>
                <p className="mt-1 text-sm text-muted-foreground">
                  Change the target application URL used by E2E tests and web extraction.
                </p>
              </div>
              <button
                type="button"
                onClick={closeProjectEditor}
                className="rounded-md border border-border p-2 text-muted-foreground transition-colors hover:text-foreground"
                title="Close editor"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
            <div className="grid grid-cols-1 gap-4">
              <Field label="Project name">
                <Input value={editProjectName} onChange={(event) => setEditProjectName(event.target.value)} />
              </Field>
              <Field label="Application URL">
                <Input
                  value={editProjectUrl}
                  onChange={(event) => setEditProjectUrl(event.target.value)}
                  placeholder="https://192.168.100.26"
                />
              </Field>
              <Field label="Description">
                <Input
                  value={editProjectDescription}
                  onChange={(event) => setEditProjectDescription(event.target.value)}
                  placeholder="Optional project description"
                />
              </Field>
            </div>
            <div className="mt-5 flex flex-wrap justify-end gap-3">
              <GradientButton type="button" variant="ghost" onClick={closeProjectEditor} disabled={isBusy("project-update")}>
                Cancel
              </GradientButton>
              <GradientButton type="button" onClick={updateProject} disabled={isBusy("project-update")}>
                {isBusy("project-update") ? <Loader2 className="h-4 w-4 animate-spin" /> : <Pencil className="h-4 w-4" />}
                Save Changes
              </GradientButton>
            </div>
          </GlassCard>
        </div>
      )}

      <div className="max-w-7xl space-y-5 px-8 py-6">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <h2 className="text-2xl font-semibold text-foreground">Project Extraction Workspace</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Gateway project-service workflow for Figma, Web, MinIO artifacts, and ML input inspection.
            </p>
          </div>
          <GradientButton variant="ghost" onClick={loadData} disabled={isBusy()}>
            {isBusy("load") ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCw className="h-4 w-4" />}
            Refresh
          </GradientButton>
        </div>

        <GlassCard className="p-5">
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-[1.2fr_1.2fr_1fr]">
            <Field label="Active project">
              <Select value={selectedProjectId} onValueChange={setSelectedProjectId}>
                <SelectTrigger className="bg-card">
                  <SelectValue placeholder="Select project" />
                </SelectTrigger>
                <SelectContent>
                  {projects.map((project) => (
                    <SelectItem key={project.id} value={project.id}>
                      {project.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>
            <Field label="Active page">
              <Select value={selectedPageId} onValueChange={setSelectedPageId}>
                <SelectTrigger className="bg-card">
                  <SelectValue placeholder="Select page" />
                </SelectTrigger>
                <SelectContent>
                  {projectPages.map((page) => (
                    <SelectItem key={page.id} value={page.id}>
                      [{pageSource(page)}] {page.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>
            <div className="grid grid-cols-3 gap-3">
              <Metric label="Projects" value={projects.length} />
              <Metric label="Pages" value={figmaPages.length + webPages.length} />
              <Metric label="Components" value={selectedPageComponents.length} />
            </div>
          </div>
        </GlassCard>

        <Tabs defaultValue="create" className="space-y-5">
          <TabsList className="h-auto flex-wrap justify-start bg-card/80 p-1">
            <TabsTrigger value="create" className="px-4 py-2">
              <Plus className="h-4 w-4" />
              Create Project
            </TabsTrigger>
            <TabsTrigger value="figma" className="px-4 py-2">
              <Layers className="h-4 w-4" />
              Figma
            </TabsTrigger>
            <TabsTrigger value="web" className="px-4 py-2">
              <Globe className="h-4 w-4" />
              Web
            </TabsTrigger>
            <TabsTrigger value="json" className="px-4 py-2">
              <Braces className="h-4 w-4" />
              JSON
            </TabsTrigger>
          </TabsList>

          <TabsContent value="create" className="space-y-5">
            <GlassCard className="p-5">
              <div className="mb-5">
                <Label className="mb-2 block">Project purpose</Label>
                <div className="inline-flex rounded-md border border-border bg-background p-1">
                  <button
                    type="button"
                    onClick={() => setProjectPurpose("e2e")}
                    className={cn(
                      "rounded px-4 py-2 text-sm font-medium transition-colors",
                      projectPurpose === "e2e"
                        ? "bg-primary text-primary-foreground"
                        : "text-muted-foreground hover:text-foreground"
                    )}
                  >
                    E2E testing
                  </button>
                  <button
                    type="button"
                    onClick={() => setProjectPurpose("design")}
                    className={cn(
                      "rounded px-4 py-2 text-sm font-medium transition-colors",
                      projectPurpose === "design"
                        ? "bg-primary text-primary-foreground"
                        : "text-muted-foreground hover:text-foreground"
                    )}
                  >
                    Design comparison
                  </button>
                </div>
              </div>
              <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
                <Field label="Project name">
                  <Input value={projectName} onChange={(event) => setProjectName(event.target.value)} />
                </Field>
                <Field label="Application URL">
                  <Input
                    value={webBaseUrl}
                    onChange={(event) => setWebBaseUrl(event.target.value)}
                    placeholder="https://192.168.19.128"
                  />
                </Field>
                {projectPurpose === "design" && (
                  <>
                    <Field label="Figma API Token">
                      <Input
                        value={figmaAccessToken}
                        onChange={(event) => setFigmaAccessToken(event.target.value)}
                        placeholder="Paste the project Figma personal access token"
                        type="password"
                      />
                    </Field>
                    <Field label="Figma file key">
                      <Input value={figmaFileKey} onChange={(event) => setFigmaFileKey(event.target.value)} />
                    </Field>
                  </>
                )}
              </div>
              <div className="mt-5 flex flex-wrap gap-3">
                <GradientButton onClick={createProject} disabled={isBusy()}>
                  {isBusy("project") ? <Loader2 className="h-4 w-4 animate-spin" /> : <FolderKanban className="h-4 w-4" />}
                  {projectPurpose === "e2e" ? "Create E2E Project" : "Create Project & Start Pipeline"}
                </GradientButton>
              </div>
            </GlassCard>

            <ProjectList
              projects={projects}
              selectedProjectId={selectedProjectId}
              onSelect={setSelectedProjectId}
              onEdit={openProjectEditor}
            />
          </TabsContent>

          <TabsContent value="figma" className="space-y-5">
            <Tabs defaultValue="design" className="space-y-5">
              <TabsList className="h-auto flex-wrap justify-start bg-card/80 p-1">
                <TabsTrigger value="design" className="px-4 py-2">
                  <FileJson className="h-4 w-4" />
                  Extract Design
                </TabsTrigger>
                <TabsTrigger value="pages" className="px-4 py-2">
                  <Layers className="h-4 w-4" />
                  Extract Pages
                </TabsTrigger>
                <TabsTrigger value="components" className="px-4 py-2">
                  <Boxes className="h-4 w-4" />
                  Extract Components
                </TabsTrigger>
              </TabsList>

              <TabsContent value="design" className="space-y-5">
                <GlassCard className="p-5">
                  <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
                    <InfoTile label="Selected project" value={selectedProject?.name || "No project selected"} />
                    <InfoTile label="Figma file key" value={selectedProject?.figmaDesignFileId || "Stored on project"} />
                    <InfoTile label="Project web URL" value={selectedProject?.url || webBaseUrl} />
                  </div>
                  <div className="mt-5">
                    <GradientButton onClick={syncFigmaDesign} disabled={isBusy()}>
                      {isBusy("figma-design") ? <Loader2 className="h-4 w-4 animate-spin" /> : <CheckCircle2 className="h-4 w-4" />}
                      Extract Design
                    </GradientButton>
                  </div>
                </GlassCard>
                <JsonPanel title="Figma design metadata" value={{ selectedProject, lastArtifact }} />
              </TabsContent>

              <TabsContent value="pages" className="space-y-5">
                <GlassCard className="p-5">
                  <div className="mb-4 grid grid-cols-1 gap-4 lg:grid-cols-3">
                    <InfoTile label="Source" value={selectedProject?.figmaDesignFileId || "Project Figma file"} />
                    <InfoTile label="Stored pages" value={String(figmaPages.length)} />
                    <InfoTile label="Project" value={selectedProject?.name || "No project selected"} />
                  </div>
                  <div className="mt-5">
                    <GradientButton onClick={extractFigmaPages} disabled={isBusy()}>
                      {isBusy("figma-pages") ? <Loader2 className="h-4 w-4 animate-spin" /> : <Layers className="h-4 w-4" />}
                      Extract All Pages
                    </GradientButton>
                  </div>
                </GlassCard>
                <PageGrid pages={figmaPages} selectedPageId={selectedPageId} onSelect={setSelectedPageId} />
              </TabsContent>

              <TabsContent value="components" className="space-y-5">
                <div className="space-y-5">
                  <GlassCard className="p-5">
                    <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                      <div className="flex items-center gap-2">
                        <Code2 className="h-5 w-5 text-primary" />
                        <p className="font-semibold">{selectedFigmaPage?.name || "Select a Figma page"}</p>
                      </div>
                      <GradientButton variant="success" onClick={extractFigmaComponents} disabled={isBusy()}>
                        {isBusy("figma-components") ? <Loader2 className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}
                        Extract Components
                      </GradientButton>
                    </div>
                  </GlassCard>
                  <PageGrid pages={figmaPages} selectedPageId={selectedPageId} onSelect={setSelectedPageId} />
                  <ComponentGrid components={figmaComponents.filter((component) => !selectedFigmaPage || component.page?.id === selectedFigmaPage.id)} emptyLabel="No Figma components stored yet." />
                </div>
              </TabsContent>
            </Tabs>
          </TabsContent>

          <TabsContent value="web" className="space-y-5">
            <Tabs defaultValue="page" className="space-y-5">
              <TabsList className="h-auto flex-wrap justify-start bg-card/80 p-1">
                <TabsTrigger value="page" className="px-4 py-2">
                  <Globe className="h-4 w-4" />
                  Save / Extract Page
                </TabsTrigger>
                <TabsTrigger value="components" className="px-4 py-2">
                  <SearchCode className="h-4 w-4" />
                  Extract Components
                </TabsTrigger>
              </TabsList>

              <TabsContent value="page" className="space-y-5">
                <GlassCard className="p-5">
                  <div className="mb-4 grid grid-cols-1 gap-4 lg:grid-cols-2">
                    <InfoTile label="Project base URL" value={selectedProject?.url || webBaseUrl} />
                    <InfoTile label="Pages to store" value={String(webPagesDraft.length)} />
                  </div>
                  <div className="space-y-3">
                    {webPagesDraft.map((row, index) => (
                      <div key={`${row.tag}-${index}`} className="grid grid-cols-1 gap-3 rounded-lg border border-border bg-card/40 p-3 lg:grid-cols-[1fr_1fr_auto]">
                        <Field label="Tag / Figma page match">
                          <Input value={row.tag} onChange={(event) => updateWebDraftRow(index, { tag: event.target.value })} />
                        </Field>
                        <Field label="Relative URL">
                          <Input value={row.path} onChange={(event) => updateWebDraftRow(index, { path: event.target.value })} />
                        </Field>
                        <div className="flex items-end">
                          <GradientButton
                            type="button"
                            variant="ghost"
                            onClick={() => removeWebDraftRow(index)}
                            disabled={webPagesDraft.length === 1}
                            className="h-10"
                          >
                            Remove
                          </GradientButton>
                        </div>
                        <p className="lg:col-span-3 text-xs text-muted-foreground">
                          Full URL: {joinUrl(selectedProject?.url || webBaseUrl, row.path)}
                        </p>
                      </div>
                    ))}
                  </div>
                  <div className="mt-5">
                    <div className="flex flex-wrap gap-3">
                      <GradientButton type="button" variant="ghost" onClick={addWebDraftRow} disabled={isBusy()}>
                        <Plus className="h-4 w-4" />
                        Add Page
                      </GradientButton>
                      <GradientButton onClick={extractWebPages} disabled={isBusy()}>
                        {isBusy("web-page") ? <Loader2 className="h-4 w-4 animate-spin" /> : <Globe className="h-4 w-4" />}
                        Save Web Pages
                      </GradientButton>
                      <GradientButton variant="success" onClick={extractSelectedWebPage} disabled={isBusy() || !selectedWebPage}>
                        {isBusy("web-extract") ? <Loader2 className="h-4 w-4 animate-spin" /> : <SearchCode className="h-4 w-4" />}
                        Extract Web Page
                      </GradientButton>
                    </div>
                  </div>
                </GlassCard>
                <PageGrid pages={webPages} selectedPageId={selectedPageId} onSelect={setSelectedPageId} />
              </TabsContent>

              <TabsContent value="components" className="space-y-5">
                <GlassCard className="p-5">
                  <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                    <div className="flex items-center gap-2">
                      <Database className="h-5 w-5 text-primary" />
                      <p className="font-semibold">{selectedWebPage?.name || "Selected web page"}</p>
                    </div>
                    <GradientButton variant="success" onClick={extractWebComponents} disabled={isBusy()}>
                      {isBusy("web-components") ? <Loader2 className="h-4 w-4 animate-spin" /> : <SearchCode className="h-4 w-4" />}
                      Extract Components
                    </GradientButton>
                  </div>
                </GlassCard>
                <PageGrid pages={webPages} selectedPageId={selectedPageId} onSelect={setSelectedPageId} />
                <ComponentGrid components={webComponents.filter((component) => !selectedWebPage || component.page?.id === selectedWebPage.id)} emptyLabel="No web components extracted yet." />
              </TabsContent>
            </Tabs>
          </TabsContent>

          <TabsContent value="json" className="space-y-5">
            <GlassCard className="p-5">
              <div className="grid grid-cols-1 gap-4 lg:grid-cols-[1.2fr_1fr_auto] lg:items-end">
                <div className="flex items-center gap-2">
                  <Database className="h-5 w-5 text-primary" />
                  <div>
                    <p className="font-semibold">ML Dataset</p>
                    <p className="text-xs text-muted-foreground">Generate full project data or a page/tag test dataset.</p>
                  </div>
                </div>
                <Field label="Dataset page">
                  <Select value={selectedDatasetPageId || "all"} onValueChange={(value) => setSelectedDatasetPageId(value === "all" ? "" : value)}>
                    <SelectTrigger className="bg-card">
                      <SelectValue placeholder="All page mappings" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="all">All page mappings</SelectItem>
                      {datasetPageOptions.map((option) => (
                        <SelectItem key={option.key} value={option.pageId}>
                          {option.name} ({option.hasFigma ? "FIGMA" : "no FIGMA"} + {option.hasWeb ? "WEB" : "no WEB"}) - {option.figmaComponentCount}/{option.webComponentCount} comps
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </Field>
                <GradientButton onClick={generateMlDataset} disabled={isBusy()}>
                  {isBusy("ml-dataset") ? <Loader2 className="h-4 w-4 animate-spin" /> : <Database className="h-4 w-4" />}
                  Generate Dataset
                </GradientButton>
              </div>
              {lastMlDataset && (
                <div className="mt-4 grid grid-cols-1 gap-3 text-sm md:grid-cols-3">
                  <Spec label="Rows" value={String(lastMlDataset.rowCount)} />
                  <Spec label="JSON" value={lastMlDataset.jsonObjectPath} />
                  <Spec label="CSV" value={lastMlDataset.csvObjectPath} />
                </div>
              )}
            </GlassCard>
            <div className="grid grid-cols-1 gap-5 xl:grid-cols-2">
              <JsonPanel title="Current workspace JSON" value={projectJson} />
              <JsonPanel title="ML input preview" value={lastMlDataset?.rows || mlPreview} />
              {lastMlDataset && <JsonPanel title="Stored ML dataset artifact" value={lastMlDataset} />}
              {lastArtifact && <JsonPanel title="Last artifact" value={lastArtifact} />}
              <ComponentGrid components={selectedPageComponents} emptyLabel="No components available for the selected page." />
            </div>
          </TabsContent>
        </Tabs>
      </div>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-2">
      <Label>{label}</Label>
      {children}
    </div>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-lg border border-border bg-card/50 px-3 py-2">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-lg font-semibold">{value}</p>
    </div>
  );
}

function InfoTile({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0 rounded-lg border border-border bg-card/50 px-3 py-2">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="truncate text-sm font-medium">{value}</p>
    </div>
  );
}

function ProjectList({
  projects,
  selectedProjectId,
  onSelect,
  onEdit,
}: {
  projects: ProjectDto[];
  selectedProjectId: string;
  onSelect: (id: string) => void;
  onEdit: (project: ProjectDto) => void;
}) {
  if (!projects.length) {
    return <EmptyPanel label="No projects yet." />;
  }

  return (
    <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
      {projects.map((project) => (
        <div
          key={project.id}
          className={cn(
            "rounded-lg border bg-card/50 p-5 transition-all hover:border-primary/60 hover:bg-accent/20",
            selectedProjectId === project.id && "border-primary shadow-[0_0_20px_rgba(124,58,237,0.25)]"
          )}
        >
          <div className="flex items-start justify-between gap-4">
            <button type="button" onClick={() => onSelect(project.id)} className="min-w-0 flex-1 text-left">
              <div className="flex items-center gap-2">
                <FolderKanban className="h-5 w-5 text-primary" />
                <p className="text-lg font-semibold">{project.name}</p>
              </div>
              <p className="mt-1 text-sm text-muted-foreground">{project.url || "No web URL"}</p>
              <p className="mt-1 text-xs text-muted-foreground">{project.figmaDesignFileId || "No Figma file key"}</p>
            </button>
            <div className="flex flex-col items-end gap-2">
              <Badge className={statusClass(project.designImplementationStatus)}>
                {(project.designImplementationStatus || "ACTIVE").toUpperCase()}
              </Badge>
              <GradientButton type="button" variant="ghost" onClick={() => onEdit(project)} className="h-9 px-3">
                <Pencil className="h-4 w-4" />
                Update
              </GradientButton>
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}

function PageGrid({
  pages,
  selectedPageId,
  onSelect,
}: {
  pages: PageDto[];
  selectedPageId: string;
  onSelect: (id: string) => void;
}) {
  if (!pages.length) {
    return <EmptyPanel label="No pages stored for this project." />;
  }

  return (
    <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
      {pages.map((page) => (
        <button
          key={page.id}
          type="button"
          onClick={() => onSelect(page.id)}
          className={cn(
            "rounded-lg border bg-card/50 p-5 text-left transition-all hover:border-primary/60 hover:bg-accent/20",
            selectedPageId === page.id && "border-primary shadow-[0_0_20px_rgba(124,58,237,0.25)]"
          )}
        >
          <div className="flex items-start justify-between gap-4">
            <div>
              <p className="text-lg font-semibold">{page.name}</p>
              <p className="mt-1 text-sm text-muted-foreground">
                {pageSource(page) === "FIGMA" ? page.figmaObjectPath || "Figma page artifact" : page.url || page.path || "-"}
              </p>
            </div>
            <div className="flex flex-col items-end gap-2">
              <Badge className={pageSource(page) === "FIGMA" ? "bg-violet-900 text-violet-100" : "bg-sky-900 text-sky-100"}>
                {pageSource(page)}
              </Badge>
              <Badge className={statusClass(page.scanStatus)}>{page.scanStatus || "PENDING"}</Badge>
            </div>
          </div>
        </button>
      ))}
    </div>
  );
}

function ComponentGrid({ components, emptyLabel }: { components: WebComponentDto[]; emptyLabel: string }) {
  if (!components.length) {
    return <EmptyPanel label={emptyLabel} />;
  }

  return (
    <div className="grid grid-cols-1 gap-4">
      {components.map((component) => (
        <GlassCard key={component.id} className="p-5">
          <div className="flex items-start justify-between gap-4">
            <div>
              <div className="flex items-center gap-2">
                <Boxes className="h-5 w-5 text-primary" />
                <p className="text-lg font-semibold">{component.componentName}</p>
              </div>
              <p className="mt-1 text-sm text-muted-foreground">
                {component.page?.name || "Page"} / {component.source || "SOURCE"}
              </p>
            </div>
            <Badge className={statusClass(component.mappingStatus)}>{component.mappingStatus || "PENDING"}</Badge>
          </div>
          <div className="mt-4 grid grid-cols-2 gap-3 text-sm md:grid-cols-4">
            <Spec label="Identifier" value={component.testIdentifier || component.htmlID || "-"} />
            <Spec label="Role" value={component.functionalRole || "-"} />
            <Spec label="Width" value={String(component.boundingBox?.width ?? "-")} />
            <Spec label="Height" value={String(component.boundingBox?.height ?? "-")} />
            <Spec label="Font size" value={String(component.cssProperties?.fontSize ?? "-")} />
            <Spec label="Font weight" value={String(component.cssProperties?.fontWeight ?? "-")} />
            <Spec label="Radius" value={String(component.cssProperties?.borderRadius ?? "-")} />
            <Spec label="Selector" value={component.cssSelector || "-"} />
          </div>
        </GlassCard>
      ))}
    </div>
  );
}

function Spec({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="truncate text-sm">{value}</p>
    </div>
  );
}

function JsonPanel({ title, value }: { title: string; value: unknown }) {
  const [open, setOpen] = useState(false);

  return (
    <GlassCard className="p-5">
      <button
        type="button"
        onClick={() => setOpen((current) => !current)}
        className="flex w-full items-center justify-between gap-3 text-left"
      >
        <span className="flex items-center gap-2">
          <Braces className="h-5 w-5 text-primary" />
          <span className="font-semibold">{title}</span>
        </span>
        <Badge className="bg-primary/10 text-primary">{open ? "Hide JSON" : "Show JSON"}</Badge>
      </button>
      {open && (
        <pre className="mt-4 max-h-[460px] overflow-auto rounded-lg border border-border bg-black/30 p-4 text-xs leading-relaxed text-foreground">
          {getJson(value)}
        </pre>
      )}
    </GlassCard>
  );
}

function EmptyPanel({ label }: { label: string }) {
  return (
    <GlassCard className="p-6">
      <p className="text-sm text-muted-foreground">{label}</p>
    </GlassCard>
  );
}
