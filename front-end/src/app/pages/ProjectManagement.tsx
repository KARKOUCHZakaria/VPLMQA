import { useEffect, useMemo, useState } from "react";
import { CheckCircle2, FolderKanban, Globe, Loader2, Pencil, Plus, Trash2, X } from "lucide-react";
import { toast } from "sonner";
import { TopBar } from "../components/custom/TopBar";
import { GlassCard } from "../components/custom/GlassCard";
import { GradientButton } from "../components/custom/GradientButton";
import { Badge } from "../components/ui/badge";
import { Input } from "../components/ui/input";
import { Label } from "../components/ui/label";
import { Toaster } from "../components/ui/sonner";
import { e2eProjectApi, type PageDto, type ProjectDto } from "../utils/e2eProjectApi";

// The delivered UI is E2E-only; legacy Figma pages remain hidden if they exist
// in a shared project-service database from an older deployment.
const isWebPage = (page: PageDto) => page.source?.toUpperCase() !== "FIGMA";

// Normalize both a route and a pasted URL into the route format stored with an
// E2E page. This prevents duplicate slashes when a base URL is combined later.
const toPath = (value: string) => {
  if (!value.trim()) return "/";
  try { return new URL(value).pathname || "/"; }
  catch { return value.startsWith("/") ? value : `/${value}`; }
};
const joinUrl = (baseUrl: string, path: string) => baseUrl.trim() ? `${baseUrl.replace(/\/+$/, "")}${toPath(path)}` : "";

export function ProjectManagement() {
  const [projects, setProjects] = useState<ProjectDto[]>([]);
  const [pages, setPages] = useState<PageDto[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState("");
  const [loading, setLoading] = useState(true);
  const [savingProject, setSavingProject] = useState(false);
  const [savingPage, setSavingPage] = useState(false);
  const [projectName, setProjectName] = useState("");
  const [projectUrl, setProjectUrl] = useState("");
  const [projectDescription, setProjectDescription] = useState("");
  const [editingProject, setEditingProject] = useState<ProjectDto | null>(null);
  const [pageName, setPageName] = useState("");
  const [pagePath, setPagePath] = useState("/login");
  const [pageUrl, setPageUrl] = useState("");
  const [editingPage, setEditingPage] = useState<PageDto | null>(null);

  const selectedProject = useMemo(() => projects.find((project) => project.id === selectedProjectId) ?? null, [projects, selectedProjectId]);
  const loadPages = async (project: ProjectDto) => setPages((await e2eProjectApi.getProjectPages(project.id)).filter(isWebPage));

  const loadProjects = async () => {
    try {
      setLoading(true);
      const e2eProjects = await e2eProjectApi.getProjects();
      setProjects(e2eProjects);
      const selected = e2eProjects.find((project) => project.id === selectedProjectId) ?? e2eProjects[0];
      if (selected) { setSelectedProjectId(selected.id); await loadPages(selected); }
      else { setSelectedProjectId(""); setPages([]); }
    } catch (error) {
      toast.error("Unable to load projects", { description: error instanceof Error ? error.message : "Please try again." });
    } finally { setLoading(false); }
  };

  useEffect(() => { void loadProjects(); }, []);

  const resetProjectForm = () => { setProjectName(""); setProjectUrl(""); setProjectDescription(""); setEditingProject(null); };
  const resetPageForm = () => { setPageName(""); setPagePath("/login"); setPageUrl(""); setEditingPage(null); };

  const chooseProject = async (projectId: string) => {
    const project = projects.find((item) => item.id === projectId);
    if (!project) return;
    setSelectedProjectId(projectId);
    resetPageForm();
    try { setLoading(true); await loadPages(project); }
    catch (error) { toast.error("Unable to load web pages", { description: error instanceof Error ? error.message : "Please try again." }); }
    finally { setLoading(false); }
  };

  const saveProject = async () => {
    if (!projectName.trim()) { toast.error("A project name is required."); return; }
    try {
      setSavingProject(true);
      const payload = { name: projectName.trim(), url: projectUrl.trim(), description: projectDescription.trim() };
      const saved = editingProject ? await e2eProjectApi.updateProject(editingProject.id, payload) : await e2eProjectApi.createProject(payload);
      setProjects((current) => editingProject ? current.map((project) => project.id === saved.id ? saved : project) : [saved, ...current]);
      setSelectedProjectId(saved.id);
      if (!editingProject) setPages([]);
      resetProjectForm();
      toast.success(editingProject ? "E2E project updated" : "E2E project created");
    } catch (error) {
      toast.error("Unable to save project", { description: error instanceof Error ? error.message : "Please try again." });
    } finally { setSavingProject(false); }
  };

  const editProject = (project: ProjectDto) => {
    setEditingProject(project); setProjectName(project.name); setProjectUrl(project.url || ""); setProjectDescription(project.description || "");
  };

  const deleteProject = async (project: ProjectDto) => {
    if (!window.confirm(`Delete the E2E project "${project.name}" and its pages?`)) return;
    try {
      setLoading(true); await e2eProjectApi.deleteProject(project.id);
      const remaining = projects.filter((item) => item.id !== project.id);
      setProjects(remaining);
      const next = remaining[0];
      if (next) { setSelectedProjectId(next.id); await loadPages(next); }
      else { setSelectedProjectId(""); setPages([]); }
      toast.success("E2E project deleted");
    } catch (error) {
      toast.error("Unable to delete project", { description: error instanceof Error ? error.message : "Please try again." });
    } finally { setLoading(false); }
  };

  const savePage = async () => {
    if (!selectedProject) { toast.error("Choose an E2E project first."); return; }
    if (!pageName.trim()) { toast.error("A page name is required."); return; }
    const path = toPath(pagePath);
    const url = pageUrl.trim() || joinUrl(selectedProject.url || "", path);
    if (!url) { toast.error("Add the page URL or a target application URL to the project."); return; }
    try {
      setSavingPage(true);
      const payload = { name: pageName.trim(), path, url };
      const saved = editingPage ? await e2eProjectApi.updatePage(selectedProject.id, editingPage.id, payload) : await e2eProjectApi.createPage(selectedProject.id, payload);
      setPages((current) => {
        const next = editingPage ? current.map((page) => page.id === saved.id ? saved : page) : [...current, saved];
        return next.sort((left, right) => left.name.localeCompare(right.name));
      });
      resetPageForm();
      toast.success(editingPage ? "Web page updated" : "Web page added");
    } catch (error) {
      toast.error("Unable to save web page", { description: error instanceof Error ? error.message : "Please try again." });
    } finally { setSavingPage(false); }
  };

  const editPage = (page: PageDto) => { setEditingPage(page); setPageName(page.name); setPagePath(page.path || toPath(page.url || "/")); setPageUrl(page.url || ""); };
  const deletePage = async (page: PageDto) => {
    if (!selectedProject || !window.confirm(`Delete the web page "${page.name}"?`)) return;
    try {
      setSavingPage(true); await e2eProjectApi.deletePage(selectedProject.id, page.id);
      setPages((current) => current.filter((item) => item.id !== page.id));
      if (editingPage?.id === page.id) resetPageForm();
      toast.success("Web page deleted");
    } catch (error) {
      toast.error("Unable to delete web page", { description: error instanceof Error ? error.message : "Please try again." });
    } finally { setSavingPage(false); }
  };

  return (
    <div className="min-h-screen bg-background">
      <TopBar title="E2E Projects" />
      <Toaster />
      <main className="mx-auto max-w-7xl space-y-6 p-6">
        <GlassCard className="p-6">
          <div className="mb-5 flex items-start justify-between gap-4">
            <div>
              <div className="flex items-center gap-2"><FolderKanban className="h-5 w-5 text-primary" /><h2 className="text-xl font-semibold">E2E test projects</h2></div>
              <p className="mt-1 text-sm text-muted-foreground">Create the target application used by your end-to-end tests.</p>
            </div>
            <Badge className="bg-green-900 text-green-200">E2E only</Badge>
          </div>
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            <div className="space-y-2"><Label htmlFor="project-name">Project name</Label><Input id="project-name" value={projectName} onChange={(event) => setProjectName(event.target.value)} placeholder="VPLMQA" /></div>
            <div className="space-y-2"><Label htmlFor="project-url">Target application URL</Label><Input id="project-url" value={projectUrl} onChange={(event) => setProjectUrl(event.target.value)} placeholder="https://application.example.com" /></div>
            <div className="space-y-2"><Label htmlFor="project-description">Description</Label><Input id="project-description" value={projectDescription} onChange={(event) => setProjectDescription(event.target.value)} placeholder="Authentication and smoke tests" /></div>
          </div>
          <div className="mt-5 flex flex-wrap gap-3">
            <GradientButton onClick={() => void saveProject()} disabled={savingProject}>{savingProject ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}{editingProject ? "Save project" : "Create E2E project"}</GradientButton>
            {editingProject && <GradientButton variant="ghost" onClick={resetProjectForm}><X className="h-4 w-4" /> Cancel</GradientButton>}
          </div>
        </GlassCard>

        <div className="grid gap-6 lg:grid-cols-[minmax(270px,0.8fr)_minmax(0,1.7fr)]">
          <GlassCard className="p-5">
            <div className="mb-4 flex items-center justify-between"><h2 className="font-semibold">Projects</h2><Badge variant="outline">{projects.length}</Badge></div>
            {loading && projects.length === 0 ? <div className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading projects</div> : projects.length === 0 ? <p className="text-sm text-muted-foreground">Create your first E2E project to add its application pages.</p> : (
              <div className="space-y-2">
                {projects.map((project) => (
                  <button key={project.id} type="button" onClick={() => void chooseProject(project.id)} className={`w-full rounded-lg border p-3 text-left transition-colors ${project.id === selectedProjectId ? "border-primary bg-primary/10" : "border-border hover:bg-accent"}`}>
                    <div className="flex items-center justify-between gap-2"><span className="font-medium">{project.name}</span>{project.id === selectedProjectId && <CheckCircle2 className="h-4 w-4 text-primary" />}</div>
                    <p className="mt-1 truncate text-xs text-muted-foreground">{project.url || "Target URL not defined"}</p>
                  </button>
                ))}
              </div>
            )}
          </GlassCard>

          <div className="space-y-6">
            <GlassCard className="p-6">
              <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
                <div>
                  <div className="flex items-center gap-2"><Globe className="h-5 w-5 text-primary" /><h2 className="text-xl font-semibold">Web pages</h2></div>
                  <p className="mt-1 text-sm text-muted-foreground">{selectedProject ? `Pages used by ${selectedProject.name}'s E2E scenarios.` : "Select an E2E project to manage its pages."}</p>
                </div>
                {selectedProject && <div className="flex gap-2"><GradientButton variant="ghost" className="px-3 py-2" onClick={() => editProject(selectedProject)}><Pencil className="h-4 w-4" /> Edit project</GradientButton><GradientButton variant="danger" className="px-3 py-2" onClick={() => void deleteProject(selectedProject)}><Trash2 className="h-4 w-4" /> Delete</GradientButton></div>}
              </div>
              {selectedProject && (
                <div className="grid gap-4 rounded-lg border border-border bg-card/40 p-4 md:grid-cols-3">
                  <div className="space-y-2"><Label htmlFor="page-name">Page name</Label><Input id="page-name" value={pageName} onChange={(event) => setPageName(event.target.value)} placeholder="Login" /></div>
                  <div className="space-y-2"><Label htmlFor="page-path">Path</Label><Input id="page-path" value={pagePath} onChange={(event) => setPagePath(event.target.value)} placeholder="/login" /></div>
                  <div className="space-y-2"><Label htmlFor="page-url">Page URL</Label><Input id="page-url" value={pageUrl} onChange={(event) => setPageUrl(event.target.value)} placeholder={joinUrl(selectedProject.url || "", pagePath) || "https://application.example.com/login"} /></div>
                  <div className="flex flex-wrap items-center gap-3 md:col-span-3">
                    <GradientButton onClick={() => void savePage()} disabled={savingPage}>{savingPage ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}{editingPage ? "Save web page" : "Add web page"}</GradientButton>
                    {editingPage && <GradientButton variant="ghost" onClick={resetPageForm}><X className="h-4 w-4" /> Cancel</GradientButton>}
                    <span className="text-xs text-muted-foreground">No design extraction or comparison is attached to these pages.</span>
                  </div>
                </div>
              )}
            </GlassCard>

            <GlassCard className="overflow-hidden">
              <div className="border-b border-border px-6 py-4"><h3 className="font-semibold">Registered application pages</h3></div>
              {!selectedProject ? <p className="p-6 text-sm text-muted-foreground">Choose a project from the list to see its web pages.</p> : loading ? <div className="flex items-center gap-2 p-6 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading pages</div> : pages.length === 0 ? <p className="p-6 text-sm text-muted-foreground">No web page is registered yet.</p> : (
                <div className="divide-y divide-border">
                  {pages.map((page) => (
                    <div key={page.id} className="flex flex-wrap items-center justify-between gap-3 px-6 py-4">
                      <div className="min-w-0"><div className="flex items-center gap-2"><span className="font-medium">{page.name}</span><Badge variant="outline">WEB</Badge></div><p className="mt-1 truncate text-sm text-muted-foreground">{page.url || page.path || "/"}</p></div>
                      <div className="flex gap-2"><GradientButton variant="ghost" className="px-3 py-2" onClick={() => editPage(page)} aria-label={`Edit ${page.name}`}><Pencil className="h-4 w-4" /></GradientButton><GradientButton variant="danger" className="px-3 py-2" onClick={() => void deletePage(page)} aria-label={`Delete ${page.name}`}><Trash2 className="h-4 w-4" /></GradientButton></div>
                    </div>
                  ))}
                </div>
              )}
            </GlassCard>
          </div>
        </div>
      </main>
    </div>
  );
}
