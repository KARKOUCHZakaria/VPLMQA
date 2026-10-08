import { useState, useEffect } from "react";
import { useNavigate } from "react-router";
import { TopBar } from "../components/custom/TopBar";
import { GlassCard } from "../components/custom/GlassCard";
import { GradientButton } from "../components/custom/GradientButton";
import { Input } from "../components/ui/input";
import { Label } from "../components/ui/label";
import { Switch } from "../components/ui/switch";
import { Separator } from "../components/ui/separator";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "../components/ui/select";
import { Clock, KeyRound, ChevronRight, Loader2, Cloud, Users, CheckCircle2 } from "lucide-react";
import { Badge } from "../components/ui/badge";
import { api } from "../utils/api";
import { toast } from "sonner";
import { Toaster } from "../components/ui/sonner";
import { ticketApi, type AzureDevOpsMember } from "../utils/ticketApi";

const azureMembersCacheKey = (projectId: string) => `vplmqa.azureMembers.${projectId}`;

const readCachedAzureMembers = (projectId: string): AzureDevOpsMember[] => {
  try {
    const raw = localStorage.getItem(azureMembersCacheKey(projectId));
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
};

const cacheAzureMembers = (projectId: string, members: AzureDevOpsMember[]) => {
  localStorage.setItem(azureMembersCacheKey(projectId), JSON.stringify(members));
};

export function Settings() {
  const navigate = useNavigate();
  const [scheduleType, setScheduleType] = useState("daily");
  const [customInterval, setCustomInterval] = useState("1");
  const [dailyTime, setDailyTime] = useState("06:00");
  const [runTestsOnDeploy, setRunTestsOnDeploy] = useState(true);
  const [aiInsights, setAiInsights] = useState(true);
  const [testTimeoutSeconds, setTestTimeoutSeconds] = useState("30");
  const [actionDelayMs, setActionDelayMs] = useState("320");
  const [emailNotifications, setEmailNotifications] = useState(true);
  const [slackNotifications, setSlackNotifications] = useState(false);
  const [notifyOnFailure, setNotifyOnFailure] = useState(true);
  const [weeklySummaryReports, setWeeklySummaryReports] = useState(true);

  const [projectId, setProjectId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [slackWebhook, setSlackWebhook] = useState("");
  const [adminEmail, setAdminEmail] = useState("");
  const [autoTicket, setAutoTicket] = useState(true);
  const [azureOrganization, setAzureOrganization] = useState("");
  const [azureProject, setAzureProject] = useState("");
  const [azureWorkItemType, setAzureWorkItemType] = useState("Bug");
  const [azureAreaPath, setAzureAreaPath] = useState("");
  const [azurePat, setAzurePat] = useState("");
  const [azureEnabled, setAzureEnabled] = useState(true);
  const [azureCredentialStored, setAzureCredentialStored] = useState(false);
  const [azureMembers, setAzureMembers] = useState<AzureDevOpsMember[]>([]);
  const [azureBusy, setAzureBusy] = useState(false);

  useEffect(() => {
    async function loadData() {
      try {
        setLoading(true);
        // 1. Get all projects
        const projects = await api.get<any[]>("/api/v1/projects");
        
        const project = projects[0];
        if (!project) {
          toast.info("Create a project first", {
            description: "Settings will be available after a project exists.",
          });
          return;
        }
        
        setProjectId(project.id);
        setAzureMembers(readCachedAzureMembers(project.id));
        // Load the platform settings for this project
        const settings = await api.get<any>(`/api/v1/projects/${project.id}/settings`);
        if (settings) {
          setSlackWebhook(settings.slackWebhook || "");
          setAdminEmail(settings.notificationEmail || "");
          setAutoTicket(settings.autoTicketCreation);
          setRunTestsOnDeploy(settings.runTestsOnDeploy ?? true);
          setAiInsights(settings.aiInsights ?? true);
          setTestTimeoutSeconds(String(settings.testTimeoutSeconds ?? 30));
          setActionDelayMs(String(settings.actionDelayMs ?? 320));
          setScheduleType(settings.scheduleType || "daily");
          setCustomInterval(String(settings.scheduleIntervalHours ?? 1));
          setDailyTime(settings.scheduleDailyTime || "06:00");
          setEmailNotifications(settings.emailNotifications ?? true);
          setSlackNotifications(settings.slackNotifications ?? false);
          setNotifyOnFailure(settings.notifyOnFailure ?? true);
          setWeeklySummaryReports(settings.weeklySummaryReports ?? true);
        }

        try {
          const azure = await ticketApi.getAzureDevOpsConnection(project.id);
          setAzureOrganization(azure.organization || "");
          setAzureProject(azure.azureProject || "");
          setAzureWorkItemType(azure.workItemType || "Bug");
          setAzureAreaPath(azure.areaPath || "");
          setAzureEnabled(azure.enabled);
          setAzureCredentialStored(azure.credentialStored);
          if (azure.credentialStored && azure.enabled) {
            try {
              const members = await ticketApi.getAzureDevOpsMembers(project.id);
              setAzureMembers(members);
              cacheAzureMembers(project.id, members);
            } catch (error) {
              toast.warning("Azure members were loaded from the last saved state", {
                description: error instanceof Error ? error.message : "The saved connection is still displayed.",
              });
            }
          }
        } catch (error) {
          toast.warning("Azure DevOps settings are not reachable", {
            description: error instanceof Error ? error.message : "Project settings remain available.",
          });
        }
      } catch (err: any) {
        toast.error("Failed to load settings", {
          description: err.message || "Could not fetch configurations.",
        });
      } finally {
        setLoading(false);
      }
    }

    loadData();
  }, []);

  const handleSave = async () => {
    if (!projectId) return;
    try {
      setLoading(true);
      // Update settings
      await api.put(`/api/v1/projects/${projectId}/settings`, {
        notificationEmail: adminEmail,
        slackWebhook: slackWebhook,
        autoTicketCreation: autoTicket,
        defaultSeverity: "MEDIUM",
        emailNotifications,
        slackNotifications,
        notifyOnFailure,
        weeklySummaryReports,
        runTestsOnDeploy,
        aiInsights,
        testTimeoutSeconds: Number(testTimeoutSeconds) || 30,
        actionDelayMs: Number(actionDelayMs) || 320,
        scheduleType,
        scheduleIntervalHours: Number(customInterval) || 1,
        scheduleDailyTime: dailyTime
      });

      toast.success("Settings saved successfully!", {
        description: "Your platform configurations are updated in the cloud.",
      });
    } catch (err: any) {
      toast.error("Failed to save changes", {
        description: err.message || "An error occurred.",
      });
    } finally {
      setLoading(false);
    }
  };

  const handleSaveAzure = async () => {
    if (!projectId || !azureOrganization.trim() || !azureProject.trim()) {
      toast.error("Organization and Azure project are required.");
      return;
    }
    if (!azureCredentialStored && !azurePat.trim()) {
      toast.error("Enter an Azure DevOps personal access token.");
      return;
    }
    try {
      setAzureBusy(true);
      const saved = await ticketApi.saveAzureDevOpsConnection(projectId, {
        organization: azureOrganization.trim(),
        azureProject: azureProject.trim(),
        workItemType: azureWorkItemType,
        areaPath: azureAreaPath.trim(),
        enabled: azureEnabled,
        personalAccessToken: azurePat.trim() || undefined,
      });
      setAzureOrganization(saved.organization);
      setAzureProject(saved.azureProject);
      setAzureWorkItemType(saved.workItemType || "Bug");
      setAzureAreaPath(saved.areaPath || "");
      setAzureEnabled(saved.enabled);
      setAzureCredentialStored(saved.credentialStored);
      setAzurePat("");
      setAzureMembers([]);
      localStorage.removeItem(azureMembersCacheKey(projectId));
      toast.success("Azure DevOps connection saved", {
        description: saved.credentialStored ? "The PAT remains stored securely in Vault." : "Connection settings were saved.",
      });
      if (!saved.enabled || !saved.credentialStored) {
        return;
      }
      try {
        const members = await ticketApi.getAzureDevOpsMembers(projectId);
        setAzureMembers(members);
        cacheAzureMembers(projectId, members);
        toast.success("Azure DevOps members loaded", {
          description: `${members.length} project member${members.length === 1 ? "" : "s"} available for assignment.`,
        });
      } catch (error) {
        toast.warning("Connection saved, but members were not refreshed", {
          description: error instanceof Error ? error.message : "Use Refresh Members after checking PAT scopes.",
        });
      }
    } catch (error) {
      toast.error("Azure DevOps connection failed", {
        description: error instanceof Error ? error.message : "Check the organization, project, PAT, and scopes.",
      });
    } finally {
      setAzureBusy(false);
    }
  };

  const handleLoadAzureMembers = async () => {
    if (!projectId) return;
    try {
      setAzureBusy(true);
      const members = await ticketApi.getAzureDevOpsMembers(projectId);
      setAzureMembers(members);
      cacheAzureMembers(projectId, members);
      toast.success(`${members.length} Azure DevOps members loaded.`);
    } catch (error) {
      toast.error("Could not load Azure DevOps members", {
        description: error instanceof Error ? error.message : "Test the connection and PAT scopes.",
      });
    } finally {
      setAzureBusy(false);
    }
  };

  return (
    <div className="min-h-screen bg-background">
      <Toaster />
      <TopBar title="Settings" />
      <div className="px-8 py-6 space-y-6 max-w-4xl">
      <div>
        <h2 className="text-2xl font-bold text-foreground">Settings</h2>
        <p className="text-muted-foreground mt-1">Manage your VQA platform configuration</p>
      </div>
      {/* Security Settings */}
      <GlassCard className="p-6">
        <div className="mb-4">
          <h3 className="text-lg font-semibold">Security</h3>
          <p className="text-sm text-muted-foreground mt-1">
            Manage your account security and password
          </p>
        </div>
        <button
          onClick={() => navigate("/change-password")}
          className="w-full flex items-center justify-between p-4 rounded-lg border border-border hover:bg-accent/50 transition-colors"
        >
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
              <KeyRound className="w-5 h-5 text-primary" />
            </div>
            <div className="text-left">
              <p className="font-medium">Change Password</p>
              <p className="text-xs text-muted-foreground">Update your account password</p>
            </div>
          </div>
          <ChevronRight className="w-5 h-5 text-muted-foreground" />
        </button>
      </GlassCard>
      {/* Azure DevOps Integration */}
      <GlassCard className="p-6">
        <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
          <div className="flex items-start gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-sky-500/10">
              <Cloud className="h-5 w-5 text-sky-400" />
            </div>
            <div>
              <h3 className="text-lg font-semibold">Azure DevOps</h3>
              <p className="mt-1 text-sm text-muted-foreground">
                Send validated tickets to the Azure project linked to the current VPLMQA project.
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            {azureCredentialStored && (
              <Badge className="bg-green-500/15 text-green-400 hover:bg-green-500/15">
                <CheckCircle2 className="mr-1 h-3.5 w-3.5" />
                Credential stored
              </Badge>
            )}
            <Switch checked={azureEnabled} onCheckedChange={setAzureEnabled} />
          </div>
        </div>

        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor="azure-organization">Organization</Label>
            <Input
              id="azure-organization"
              value={azureOrganization}
              onChange={(event) => setAzureOrganization(event.target.value)}
              placeholder="organization from dev.azure.com/organization"
              className="bg-card border-border"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="azure-project">Azure project</Label>
            <Input
              id="azure-project"
              value={azureProject}
              onChange={(event) => setAzureProject(event.target.value)}
              placeholder="Project name or ID"
              className="bg-card border-border"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="azure-work-item-type">Work-item type</Label>
            <Select value={azureWorkItemType} onValueChange={setAzureWorkItemType}>
              <SelectTrigger id="azure-work-item-type" className="bg-card border-border">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="Bug">Bug</SelectItem>
                <SelectItem value="Issue">Issue</SelectItem>
                <SelectItem value="Task">Task</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label htmlFor="azure-area-path">Area path</Label>
            <Input
              id="azure-area-path"
              value={azureAreaPath}
              onChange={(event) => setAzureAreaPath(event.target.value)}
              placeholder="Optional, for example Project\\Web"
              className="bg-card border-border"
            />
          </div>
          <div className="space-y-2 md:col-span-2">
            <Label htmlFor="azure-pat">Personal access token</Label>
            <Input
              id="azure-pat"
              type="password"
              value={azurePat}
              onChange={(event) => setAzurePat(event.target.value)}
              placeholder={azureCredentialStored ? "Leave blank to keep the stored PAT" : "Paste a PAT with Work Items and Project and Team scopes"}
              autoComplete="new-password"
              className="bg-card border-border"
            />
            <p className="text-xs text-muted-foreground">
              The token is written directly to Vault. It is never returned to this page or stored in the project database.
            </p>
          </div>
        </div>

        <div className="mt-5 flex flex-wrap gap-3">
          <GradientButton onClick={handleSaveAzure} disabled={azureBusy || !projectId}>
            {azureBusy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Cloud className="h-4 w-4" />}
            Save & Test Connection
          </GradientButton>
          <GradientButton
            variant="ghost"
            onClick={handleLoadAzureMembers}
            disabled={azureBusy || !azureCredentialStored || !projectId}
          >
            <Users className="h-4 w-4" />
            Refresh Members
          </GradientButton>
        </div>

        {azureMembers.length > 0 && (
          <div className="mt-5 border-t border-border pt-5">
            <div className="mb-3 flex items-center justify-between">
              <div>
                <p className="font-medium">Azure project members</p>
                <p className="text-xs text-muted-foreground">Available as ticket assignees</p>
              </div>
              <Badge variant="outline" className="border-border">{azureMembers.length} members</Badge>
            </div>
            <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
              {azureMembers.slice(0, 12).map((member) => (
                <div key={member.id} className="flex items-center gap-3 border border-border bg-card/40 p-3">
                  <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary/10 text-sm font-semibold text-primary">
                    {member.displayName.slice(0, 1).toUpperCase()}
                  </div>
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">{member.displayName}</p>
                    <p className="truncate text-xs text-muted-foreground">{member.email || member.uniqueName}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </GlassCard>
      {/* General Settings */}
      <GlassCard className="p-6">
        <div className="mb-4">
          <h3 className="text-lg font-semibold">General Settings</h3>
          <p className="text-sm text-muted-foreground mt-1">Configure basic platform settings</p>
        </div>
        <div className="space-y-4">
<div className="space-y-2">
            <Label htmlFor="admin-email" className="text-foreground">Admin Email</Label>
            <Input
              id="admin-email"
              type="email"
              value={adminEmail}
              onChange={(e) => setAdminEmail(e.target.value)}
              className="bg-card border-border text-foreground"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="slack-webhook" className="text-foreground">Slack Webhook URL</Label>
            <Input
              id="slack-webhook"
              type="url"
              placeholder="https://hooks.slack.com/services/..."
              value={slackWebhook}
              onChange={(e) => setSlackWebhook(e.target.value)}
              className="bg-card border-border text-foreground placeholder:text-muted-foreground"
            />
          </div>
        </div>
      </GlassCard>

      {/* Test Configuration */}
      <GlassCard className="p-6">
        <div className="mb-4">
          <h3 className="text-lg font-semibold">Test Configuration</h3>
          <p className="text-sm text-muted-foreground mt-1">
            Configure the options applied to new E2E executions
          </p>
        </div>
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <Label className="text-foreground">Run tests on deploy</Label>
              <p className="text-sm text-muted-foreground">Automatically run tests when code is deployed</p>
            </div>
            <Switch checked={runTestsOnDeploy} onCheckedChange={setRunTestsOnDeploy} />
          </div>
          <Separator className="bg-card" />
          <div className="flex items-center justify-between">
            <div>
              <Label className="text-foreground">Enable AI insights</Label>
              <p className="text-sm text-muted-foreground">Use AI to generate testing recommendations</p>
            </div>
            <Switch checked={aiInsights} onCheckedChange={setAiInsights} />
          </div>
          <Separator className="bg-card" />
          <div className="space-y-2">
            <Label htmlFor="test-timeout" className="text-foreground">Test Timeout (seconds)</Label>
            <Input
              id="test-timeout"
              type="number"
              min="1"
              max="600"
              value={testTimeoutSeconds}
              onChange={(event) => setTestTimeoutSeconds(event.target.value)}
              className="bg-card border-border text-foreground"
            />
          </div>
          <Separator className="bg-card" />
          <div className="space-y-2">
            <Label htmlFor="action-delay" className="text-foreground">Step wait / human pacing (ms)</Label>
            <Input
              id="action-delay"
              type="number"
              min="0"
              max="3000"
              value={actionDelayMs}
              onChange={(event) => setActionDelayMs(event.target.value)}
              className="bg-card border-border text-foreground"
            />
            <p className="text-xs text-muted-foreground">
              This delay is passed to the E2E agent so execution stays readable without being unnecessarily slow.
            </p>
          </div>
          <Separator className="bg-card" />
          <div className="space-y-3">
            <div className="flex items-center gap-2">
              <Clock className="w-4 h-4 text-blue-400" />
              <Label className="text-foreground">E2E Test Launch Schedule</Label>
            </div>
            <p className="text-sm text-muted-foreground">Configure when automated E2E tests should run</p>
            
            <div className="space-y-2">
              <Label htmlFor="schedule-type" className="text-foreground">Schedule Type</Label>
              <Select value={scheduleType} onValueChange={setScheduleType}>
                <SelectTrigger className="bg-card border-border text-foreground">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="disabled">Disabled</SelectItem>
                  <SelectItem value="daily">Daily at specific time</SelectItem>
                  <SelectItem value="hourly">Every hour</SelectItem>
                  <SelectItem value="custom">Custom interval</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {scheduleType === "daily" && (
              <div className="space-y-2">
                <Label htmlFor="daily-time" className="text-foreground">Time</Label>
                <Input
                  id="daily-time"
                  type="time"
                  value={dailyTime}
                  onChange={(e) => setDailyTime(e.target.value)}
                  className="bg-card border-border text-foreground"
                />
                <p className="text-xs text-muted-foreground">Tests will run every day at {dailyTime}</p>
              </div>
            )}

            {scheduleType === "custom" && (
              <div className="space-y-2">
                <Label htmlFor="custom-interval" className="text-foreground">Run every (hours)</Label>
                <div className="flex gap-2 items-center">
                  <Input
                    id="custom-interval"
                    type="number"
                    min="1"
                    max="24"
                    value={customInterval}
                    onChange={(e) => setCustomInterval(e.target.value)}
                    className="bg-card border-border text-foreground w-24"
                  />
                  <span className="text-sm text-muted-foreground">hour(s)</span>
                </div>
                <p className="text-xs text-muted-foreground">Tests will run every {customInterval} hour(s)</p>
              </div>
            )}

            {scheduleType === "hourly" && (
              <div className="p-3 bg-blue-950/30 border border-blue-900 rounded-lg">
                <p className="text-sm text-blue-300">
                  Tests will run automatically every hour
                </p>
              </div>
            )}

            {scheduleType === "disabled" && (
              <div className="p-3 bg-card/30 border border-border rounded-lg">
                <p className="text-sm text-muted-foreground">
                  Scheduled tests are disabled. Tests will only run manually or on deploy.
                </p>
              </div>
            )}
          </div>
        </div>
      </GlassCard>

      {/* Notifications */}
      <GlassCard className="p-6">
        <div className="mb-4">
          <h3 className="text-lg font-semibold">Notifications</h3>
          <p className="text-sm text-muted-foreground mt-1">Manage notification preferences</p>
        </div>
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <Label className="text-foreground">Email notifications</Label>
              <p className="text-sm text-muted-foreground">Receive test results via email</p>
            </div>
            <Switch checked={emailNotifications} onCheckedChange={setEmailNotifications} />
          </div>
          <Separator className="bg-card" />
          <div className="flex items-center justify-between">
            <div>
              <Label className="text-foreground">Slack notifications</Label>
              <p className="text-sm text-muted-foreground">Send test results to Slack</p>
            </div>
            <Switch checked={slackNotifications} onCheckedChange={setSlackNotifications} />
          </div>
          <Separator className="bg-card" />
          <div className="flex items-center justify-between">
            <div>
              <Label className="text-foreground">Notify on test failure</Label>
              <p className="text-sm text-muted-foreground">Get notified when tests fail</p>
            </div>
            <Switch checked={notifyOnFailure} onCheckedChange={setNotifyOnFailure} />
          </div>
          <Separator className="bg-card" />
          <div className="flex items-center justify-between">
            <div>
              <Label className="text-foreground">Weekly summary reports</Label>
              <p className="text-sm text-muted-foreground">Receive weekly test summaries</p>
            </div>
            <Switch checked={weeklySummaryReports} onCheckedChange={setWeeklySummaryReports} />
          </div>
        </div>
      </GlassCard>

      {/* Team Members */}
      <GlassCard className="p-6">
        <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
          <div>
            <h3 className="text-lg font-semibold">Team Members</h3>
            <p className="text-sm text-muted-foreground mt-1">
              Members available for ticket assignment in Azure DevOps
            </p>
          </div>
          <GradientButton
            variant="ghost"
            size="sm"
            onClick={handleLoadAzureMembers}
            disabled={azureBusy || !azureCredentialStored}
          >
            {azureBusy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Users className="h-4 w-4" />}
            Refresh
          </GradientButton>
        </div>

        {azureOrganization && azureProject ? (
          <div className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3 border border-border bg-card/40 p-4">
              <div>
                <p className="text-xs uppercase text-muted-foreground">Azure DevOps source</p>
                <p className="mt-1 font-medium">{azureOrganization} / {azureProject}</p>
              </div>
              <Badge variant="outline" className="border-border">
                {azureMembers.length} member{azureMembers.length === 1 ? "" : "s"}
              </Badge>
            </div>

            {azureMembers.length > 0 ? (
              <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
                {azureMembers.map((member) => (
                  <div
                    key={member.id}
                    className="flex items-center justify-between gap-3 border border-border bg-card/30 p-3"
                  >
                    <div className="flex min-w-0 items-center gap-3">
                      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary/10 font-semibold text-primary">
                        {member.displayName.slice(0, 1).toUpperCase()}
                      </div>
                      <div className="min-w-0">
                        <p className="truncate font-medium text-foreground">{member.displayName}</p>
                        <p className="truncate text-sm text-muted-foreground">
                          {member.email || member.uniqueName}
                        </p>
                      </div>
                    </div>
                    <span className="shrink-0 text-xs text-muted-foreground">Azure member</span>
                  </div>
                ))}
              </div>
            ) : (
              <div
                className="flex flex-col items-center justify-center border border-dashed border-border px-4 py-8 text-center"
              >
                <Users className="mb-3 h-8 w-8 text-muted-foreground" />
                <p className="font-medium">No members loaded</p>
                <p className="mt-1 text-sm text-muted-foreground">
                  Save and test the Azure connection, then refresh the member list.
                </p>
              </div>
            )}
          </div>
        ) : (
          <div className="border border-dashed border-border px-4 py-8 text-center">
            <p className="font-medium">Azure DevOps is not configured</p>
            <p className="mt-1 text-sm text-muted-foreground">
              Add an organization, project, and PAT in the Azure DevOps section above.
            </p>
          </div>
        )}
      </GlassCard>

      <div className="flex justify-end gap-3">
        <GradientButton variant="ghost" onClick={() => navigate("/home")} disabled={loading}>
          Cancel
        </GradientButton>
        <GradientButton variant="primary" onClick={handleSave} disabled={loading}>
          {loading ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : null}
          Save Changes
        </GradientButton>
      </div>
      </div>
    </div>
  );
}
