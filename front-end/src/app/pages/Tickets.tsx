import { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router";
import { TopBar } from "../components/custom/TopBar";
import { GlassCard } from "../components/custom/GlassCard";
import { GradientButton } from "../components/custom/GradientButton";
import { Badge } from "../components/ui/badge";
import { Input } from "../components/ui/input";
import { Textarea } from "../components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "../components/ui/select";
import { toast } from "sonner";
import { Toaster } from "../components/ui/sonner";
import { AlertCircle, ArrowLeft, CheckCircle2, Plus, Send, UserRound } from "lucide-react";
import { ticketApi, Ticket, type AzureDevOpsConnection, type AzureDevOpsMember } from "../utils/ticketApi";
import { projectApi, type Project } from "../utils/projectApi";

type TicketDraft = Partial<Ticket> & {
  source?: string;
  action?: string;
};

const emptyDraft: TicketDraft = {
  projectId: "",
  title: "",
  description: "",
  severity: "MEDIUM",
  status: "OPEN",
  assignedTo: "",
};

export function Tickets() {
  const location = useLocation();
  const navigate = useNavigate();
  const incomingDraft = (location.state as { ticketDraft?: TicketDraft } | null)?.ticketDraft;
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [showForm, setShowForm] = useState(Boolean(incomingDraft));
  const [draft, setDraft] = useState<TicketDraft>({ ...emptyDraft, ...incomingDraft });
  const [azureMembers, setAzureMembers] = useState<AzureDevOpsMember[]>([]);
  const [azureConnection, setAzureConnection] = useState<AzureDevOpsConnection | null>(null);
  const [selectedAzureOrganization, setSelectedAzureOrganization] = useState("");
  const [selectedAzureProject, setSelectedAzureProject] = useState("");
  const [projects, setProjects] = useState<Project[]>([]);
  const [projectsLoading, setProjectsLoading] = useState(true);
  const [azureConnections, setAzureConnections] = useState<AzureDevOpsConnection[]>([]);

  const activeProjectId = useMemo(() => draft.projectId || incomingDraft?.projectId || "", [draft.projectId, incomingDraft?.projectId]);
  const availableAzureConnections = useMemo(
    () => azureConnections.filter((connection) =>
      connection.organization?.trim() &&
      connection.azureProject?.trim()
    ),
    [azureConnections]
  );
  const readyAzureConnections = useMemo(
    () => availableAzureConnections.filter((connection) => connection.enabled && connection.credentialStored),
    [availableAzureConnections]
  );
  const azureOrganizations = useMemo(
    () => Array.from(new Set(availableAzureConnections.map((connection) => connection.organization.trim()))),
    [availableAzureConnections]
  );
  const azureProjectOptions = useMemo(
    () => availableAzureConnections.filter((connection) => connection.organization.trim() === selectedAzureOrganization),
    [availableAzureConnections, selectedAzureOrganization]
  );
  const selectedAzureProjectId = useMemo(
    () => azureProjectOptions.some((connection) => connection.projectId === draft.projectId) ? draft.projectId || "" : "",
    [azureProjectOptions, draft.projectId]
  );
  const linkedProject = useMemo(
    () => projects.find((project) => project.id === draft.projectId),
    [projects, draft.projectId]
  );

  useEffect(() => {
    projectApi.getProjects()
      .then((projectList) => {
        const activeProjects = projectList.filter((project) => project.status !== "ARCHIVED");
        setProjects(activeProjects);
        setDraft((current) => {
          if (current.projectId || incomingDraft?.projectId || activeProjects.length !== 1) return current;
          return { ...current, projectId: activeProjects[0].id };
        });
      })
      .catch((error) => {
        console.error("Failed to load projects", error);
        toast.error("Failed to load projects", {
          description: error instanceof Error ? error.message : "Gateway request failed",
        });
      })
      .finally(() => setProjectsLoading(false));
  }, [incomingDraft?.projectId]);

  useEffect(() => {
    if (projects.length === 0) {
      setAzureConnections([]);
      return;
    }

    let cancelled = false;
    Promise.all(
      projects.map((project) =>
        ticketApi.getAzureDevOpsConnection(project.id).catch(() => null)
      )
    ).then((connections) => {
      if (cancelled) return;
      const configured = connections.filter((connection): connection is AzureDevOpsConnection => Boolean(connection));
      setAzureConnections(configured);
      const firstConnection =
        configured.find((connection) =>
          connection.enabled &&
          connection.credentialStored &&
          connection.organization?.trim() &&
          connection.azureProject?.trim()
        ) ||
        configured.find((connection) =>
          connection.organization?.trim() &&
          connection.azureProject?.trim()
        );
      if (firstConnection && !incomingDraft?.projectId) {
        setAzureConnection(firstConnection);
        setSelectedAzureOrganization(firstConnection.organization.trim());
        setSelectedAzureProject(firstConnection.azureProject.trim());
      }
      setDraft((current) => {
        if (current.projectId || incomingDraft?.projectId || !firstConnection) return current;
        return { ...current, projectId: firstConnection.projectId };
      });
    });

    return () => {
      cancelled = true;
    };
  }, [projects, incomingDraft?.projectId]);

  useEffect(() => {
    if (incomingDraft) {
      setDraft({ ...emptyDraft, ...incomingDraft });
      setShowForm(true);
      navigate("/tickets", { replace: true, state: null });
    }
  }, [incomingDraft, navigate]);

  useEffect(() => {
    loadTickets(activeProjectId);
    if (activeProjectId) {
      ticketApi.getAzureDevOpsConnection(activeProjectId)
        .then((connection) => {
          setAzureConnection(connection);
          setSelectedAzureOrganization(connection.organization || "");
          setSelectedAzureProject(connection.azureProject || "");
          if (connection.enabled && connection.credentialStored) {
            return ticketApi.getAzureDevOpsMembers(activeProjectId);
          }
          return [];
        })
        .then(setAzureMembers)
        .catch(() => {
          setAzureConnection(null);
          setSelectedAzureOrganization("");
          setSelectedAzureProject("");
          setAzureMembers([]);
        });
    } else {
      setAzureConnection(null);
      setSelectedAzureOrganization("");
      setSelectedAzureProject("");
      setAzureMembers([]);
    }
  }, [activeProjectId]);

  const loadTickets = async (projectId?: string) => {
    try {
      setLoading(true);
      if (!projectId) {
        setTickets([]);
        return;
      }
      const data = await ticketApi.listProjectTickets(projectId);
      setTickets(data);
    } catch (e) {
      console.error("Failed to load tickets", e);
      setTickets([]);
    } finally {
      setLoading(false);
    }
  };

  const updateDraft = (patch: Partial<TicketDraft>) => {
    setDraft((current) => ({ ...current, ...patch }));
  };

  const selectAzureConnection = (connection: AzureDevOpsConnection) => {
    setAzureConnection(connection);
    setSelectedAzureOrganization(connection.organization.trim());
    setSelectedAzureProject(connection.azureProject.trim());
    setAzureMembers([]);
    setDraft((current) => ({
      ...current,
      projectId: connection.projectId,
      assignedTo: "",
    }));
  };

  const openBlankForm = () => {
    const firstConnection = readyAzureConnections[0] || availableAzureConnections[0];
    if (firstConnection) {
      setAzureConnection(firstConnection);
      setSelectedAzureOrganization(firstConnection.organization.trim());
      setSelectedAzureProject(firstConnection.azureProject.trim());
      setAzureMembers([]);
      setDraft({ ...emptyDraft, projectId: firstConnection.projectId });
    } else {
      setDraft({ ...emptyDraft, projectId: projects.length === 1 ? projects[0].id : "" });
    }
    setShowForm(true);
  };

  const submitTicket = async () => {
    if (!draft.projectId?.trim()) {
      toast.error("Project is required");
      return;
    }
    if (!draft.title?.trim()) {
      toast.error("Ticket title is required");
      return;
    }
    if (!draft.description?.trim()) {
      toast.error("Ticket description is required");
      return;
    }

    try {
      setSaving(true);
      const ticket = await ticketApi.createTicket({
        projectId: draft.projectId,
        title: draft.title,
        description: draft.description,
        severity: draft.severity || "MEDIUM",
        status: draft.status || "OPEN",
        testExecutionId: draft.testExecutionId,
        comparisonResultId: draft.comparisonResultId,
        componentCanonicalName: draft.componentCanonicalName,
        componentHtmlId: draft.componentHtmlId,
        assignedTo: draft.assignedTo?.trim() || undefined,
      });
      if (ticket.azureSyncStatus === "FAILED") {
        toast.warning("Ticket saved locally, but Azure sync failed", {
          description: ticket.azureSyncError || "Check the Azure DevOps project, work-item type, PAT, and permissions.",
          duration: 8000,
        });
      } else if (ticket.azureSyncStatus === "SYNCED") {
        toast.success("Ticket sent to Azure DevOps", {
          description: ticket.azureWorkItemId
            ? `Azure work item #${ticket.azureWorkItemId} was created successfully.`
            : "The Azure work item was created successfully.",
        });
      } else {
        toast.success("Ticket created locally", {
          description: `Ticket ${ticket.id.slice(0, 8).toUpperCase()} is ready for tracking.`,
        });
      }
      setShowForm(false);
      setDraft({ ...emptyDraft, projectId: draft.projectId });
      await loadTickets(draft.projectId);
    } catch (error) {
      toast.error("Ticket creation failed", {
        description: error instanceof Error ? error.message : "Gateway request failed",
      });
    } finally {
      setSaving(false);
    }
  };

  const getPriorityColor = (severity: string) => {
    const s = severity?.toLowerCase() || "";
    if (s.includes("high") || s.includes("critical")) return "bg-error/20 text-error";
    if (s.includes("medium")) return "bg-warning/20 text-warning";
    return "bg-muted/20 text-muted-foreground";
  };

  const getStatusColor = (status: string) => {
    const s = status?.toLowerCase() || "";
    if (s === "open") return "bg-info/20 text-info";
    if (s === "in_progress") return "bg-primary/20 text-primary";
    if (s === "resolved") return "bg-success/20 text-success";
    return "bg-muted/20 text-muted-foreground";
  };

  return (
    <div className="min-h-screen bg-background">
      <Toaster />
      <TopBar title="Tickets" />
      <div className="px-8 py-6 space-y-6 max-w-7xl">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <h2 className="text-2xl font-bold text-foreground">Tickets</h2>
            <p className="text-muted-foreground mt-1">Create Azure DevOps-ready work items from E2E evidence</p>
          </div>
          <GradientButton variant="primary" onClick={openBlankForm}>
            <Plus className="w-4 h-4" />
            Create Ticket
          </GradientButton>
        </div>

        {showForm && (
          <GlassCard className="p-6">
            <div className="flex flex-wrap items-start justify-between gap-4 mb-6">
              <div>
                <div className="flex items-center gap-2 text-primary font-semibold">
                  <AlertCircle className="w-5 h-5" />
                  Azure DevOps Ticket Draft
                </div>
                <p className="text-sm text-muted-foreground mt-1">
                  Review the generated problem report, assign a member, then create the ticket.
                </p>
              </div>
              <GradientButton variant="ghost" onClick={() => setShowForm(false)}>
                <ArrowLeft className="w-4 h-4" />
                Back to list
              </GradientButton>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-[1fr_260px] gap-5">
              <div className="space-y-4">
                <label className="block space-y-2">
                  <span className="text-sm font-medium text-foreground">Problem title</span>
                  <Input
                    value={draft.title || ""}
                    onChange={(event) => updateDraft({ title: event.target.value })}
                    placeholder="Short problem name"
                    className="bg-input-background"
                  />
                </label>

                <label className="block space-y-2">
                  <span className="text-sm font-medium text-foreground">Description</span>
                  <Textarea
                    value={draft.description || ""}
                    onChange={(event) => updateDraft({ description: event.target.value })}
                    placeholder="Steps, actual result, expected result, logs, screenshot links..."
                    className="min-h-[280px] bg-input-background font-mono text-sm"
                  />
                </label>
              </div>

              <div className="space-y-4">
                <label className="block space-y-2">
                  <span className="text-sm font-medium text-foreground">Azure organization</span>
                  <Select
                    value={selectedAzureOrganization}
                    onValueChange={(value) => {
                      setSelectedAzureOrganization(value);
                      setSelectedAzureProject("");
                      setAzureMembers([]);
                      setDraft((current) => ({ ...current, projectId: "", assignedTo: "" }));
                      const matchingConnections = availableAzureConnections.filter((connection) => connection.organization.trim() === value);
                      if (matchingConnections.length === 1) {
                        selectAzureConnection(matchingConnections[0]);
                      } else {
                        setAzureConnection(null);
                      }
                    }}
                    disabled={projectsLoading || availableAzureConnections.length === 0}
                  >
                    <SelectTrigger className="bg-input-background">
                      <SelectValue placeholder={projectsLoading ? "Loading Azure settings..." : "Select organization"} />
                    </SelectTrigger>
                    <SelectContent>
                      {azureOrganizations.map((organization) => (
                        <SelectItem key={organization} value={organization}>{organization}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </label>

                <label className="block space-y-2">
                  <span className="text-sm font-medium text-foreground">Azure project</span>
                  <Select
                    value={selectedAzureProjectId}
                    onValueChange={(value) => {
                      const connection = availableAzureConnections.find((item) => item.projectId === value);
                      if (connection) selectAzureConnection(connection);
                    }}
                    disabled={!selectedAzureOrganization || azureProjectOptions.length === 0}
                  >
                    <SelectTrigger className="bg-input-background">
                      <SelectValue placeholder="Select Azure project" />
                    </SelectTrigger>
                    <SelectContent>
                      {azureProjectOptions.map((connection) => (
                        <SelectItem key={connection.projectId} value={connection.projectId}>
                          {connection.azureProject}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  {linkedProject && (
                    <span className="block text-xs text-muted-foreground">
                      Linked VPLMQA project: {linkedProject.name}
                    </span>
                  )}
                  {availableAzureConnections.length === 0 && !projectsLoading && (
                    <span className="block text-xs text-warning">
                      No Azure DevOps project is configured yet. Add it in Settings before sending tickets to Azure DevOps.
                    </span>
                  )}
                  {azureConnection && !azureConnection.credentialStored && (
                    <span className="block text-xs text-warning">
                      Azure project found, but the PAT is not stored in Vault. The ticket can be saved locally, but Azure sync needs the PAT in Settings.
                    </span>
                  )}
                  {azureConnection && !azureConnection.enabled && (
                    <span className="block text-xs text-warning">
                      Azure sync is disabled for this linked VPLMQA project.
                    </span>
                  )}
                </label>

                <label className="block space-y-2">
                  <span className="text-sm font-medium text-foreground">Azure assignee</span>
                  {selectedAzureProject && azureMembers.length > 0 ? (
                    <Select value={draft.assignedTo || ""} onValueChange={(value) => updateDraft({ assignedTo: value })}>
                      <SelectTrigger className="bg-input-background">
                        <SelectValue placeholder={`Select member from ${selectedAzureProject}`} />
                      </SelectTrigger>
                      <SelectContent>
                        {azureMembers.map((member) => (
                          <SelectItem key={member.id} value={member.email || member.uniqueName}>
                            {member.displayName}{member.email ? ` (${member.email})` : ""}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  ) : (
                    <div className="relative">
                      <UserRound className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                      <Input
                        value={draft.assignedTo || ""}
                        onChange={(event) => updateDraft({ assignedTo: event.target.value })}
                        placeholder={selectedAzureProject ? "No members available" : "Select an Azure project first"}
                        className="bg-input-background pl-9"
                        disabled={!selectedAzureProject}
                      />
                    </div>
                  )}
                </label>

                <label className="block space-y-2">
                  <span className="text-sm font-medium text-foreground">Severity</span>
                  <Select value={draft.severity || "MEDIUM"} onValueChange={(value) => updateDraft({ severity: value })}>
                    <SelectTrigger className="bg-input-background">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="LOW">Low</SelectItem>
                      <SelectItem value="MEDIUM">Medium</SelectItem>
                      <SelectItem value="HIGH">High</SelectItem>
                      <SelectItem value="CRITICAL">Critical</SelectItem>
                    </SelectContent>
                  </Select>
                </label>

                <label className="block space-y-2">
                  <span className="text-sm font-medium text-foreground">Status</span>
                  <Select value={draft.status || "OPEN"} onValueChange={(value) => updateDraft({ status: value })}>
                    <SelectTrigger className="bg-input-background">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="OPEN">Open</SelectItem>
                      <SelectItem value="IN_PROGRESS">In progress</SelectItem>
                      <SelectItem value="RESOLVED">Resolved</SelectItem>
                    </SelectContent>
                  </Select>
                </label>

                {draft.testExecutionId && (
                  <div className="rounded-lg border border-border bg-muted/20 p-3 text-xs text-muted-foreground break-all">
                    Execution: {draft.testExecutionId}
                  </div>
                )}

                <GradientButton className="w-full" variant="success" onClick={submitTicket} disabled={saving}>
                  <Send className="w-4 h-4" />
                  {saving ? "Creating..." : "Create Ticket"}
                </GradientButton>
              </div>
            </div>
          </GlassCard>
        )}

        <div className="grid grid-cols-1 gap-4">
          {loading ? (
            <p className="text-muted-foreground text-center py-8">Loading tickets...</p>
          ) : tickets.length === 0 ? (
            <GlassCard className="p-12 text-center">
              <h3 className="text-xl font-medium text-muted-foreground">No tickets found for this project</h3>
            </GlassCard>
          ) : (
            tickets.map((ticket) => (
              <GlassCard key={ticket.id} className="p-6 hover:shadow-[0_0_60px_rgba(124,58,237,0.3)] transition-all">
                <div className="flex items-start justify-between gap-4 mb-4">
                  <div className="flex-1">
                    <div className="flex flex-wrap items-center gap-2 mb-2">
                      <span className="text-sm font-mono text-muted-foreground">
                        {ticket.id.substring(0, 8).toUpperCase()}
                      </span>
                      <Badge className={getPriorityColor(ticket.severity)}>
                        {ticket.severity || "Medium"}
                      </Badge>
                      <Badge className={getStatusColor(ticket.status)}>
                        {ticket.status || "Open"}
                      </Badge>
                      {ticket.assignedTo && (
                        <Badge className="bg-primary/10 text-primary">
                          {ticket.assignedTo}
                        </Badge>
                      )}
                    </div>
                    <h3 className="text-lg font-semibold mb-2">{ticket.title}</h3>
                    <p className="text-sm text-muted-foreground whitespace-pre-wrap line-clamp-4">{ticket.description}</p>
                  </div>
                  <CheckCircle2 className="w-5 h-5 text-success shrink-0" />
                </div>
                <div className="flex items-center justify-between text-sm pt-4 border-t border-border">
                  <span className="text-muted-foreground">
                    Created At:{" "}
                    <span className="font-medium text-foreground">
                      {new Date(ticket.createdAt).toLocaleDateString()}
                    </span>
                  </span>
                  <GradientButton variant="ghost" size="sm" onClick={() => {
                    setDraft({ ...ticket });
                    setShowForm(true);
                  }}>
                    View Details
                  </GradientButton>
                </div>
              </GlassCard>
            ))
          )}
        </div>
      </div>
    </div>
  );
}
