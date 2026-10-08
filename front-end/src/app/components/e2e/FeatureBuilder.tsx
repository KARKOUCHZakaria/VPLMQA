import React, { useEffect, useRef, useState } from "react";
import { Download, FileInput, FileText, Plus, X } from "lucide-react";
import { projectApi, type Project } from "../../utils/projectApi";
import { createFeature, createScenario, createStep } from "../../utils/e2eApi";
import { toast } from "sonner";

type Step = {
  id: string;
  type: string;
  description: string;
};

type Scenario = {
  id: string;
  name: string;
  steps: Step[];
};

type Feature = {
  id: string;
  name: string;
  scenarios: Scenario[];
  targetMode: "PROJECT" | "EXTERNAL";
  projectId?: string;
};

const STEP_TYPES = ["Given", "When", "Then", "And", "But"];

export function FeatureBuilder({ onSave }: { onSave?: (feature: Feature) => void }) {
  const [featureName, setFeatureName] = useState("");
  const [features, setFeatures] = useState<Feature[]>([]);
  const [targetMode, setTargetMode] = useState<"PROJECT" | "EXTERNAL">("PROJECT");
  const [projectId, setProjectId] = useState("");
  const [projects, setProjects] = useState<Project[]>([]);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [savingFeatureIds, setSavingFeatureIds] = useState<string[]>([]);
  const savingFeatureIdsRef = useRef<Set<string>>(new Set());

  useEffect(() => {
    projectApi.getProjects().then(setProjects).catch(() => setProjects([]));
  }, []);

  const generateId = () => Math.random().toString(36).substr(2, 9);

  const buildGherkin = (feature: Feature) => {
    const lines = [`Feature: ${feature.name}`];
    feature.scenarios.forEach((scenario) => {
      lines.push("", `  Scenario: ${scenario.name || "Unnamed scenario"}`);
      scenario.steps.forEach((step) => {
        lines.push(`    ${step.type || "Given"} ${step.description || ""}`.trimEnd());
      });
    });
    return `${lines.join("\n")}\n`;
  };

  const safeFileName = (name: string) =>
    `${name.trim().replace(/[^a-z0-9-_]+/gi, "-").replace(/^-+|-+$/g, "").toLowerCase() || "feature"}.feature`;

  const parseGherkin = (content: string, fallbackName: string): Feature => {
    const importedFeature: Feature = {
      id: generateId(),
      name: fallbackName.replace(/\.feature$/i, "").trim() || "Imported Feature",
      targetMode,
      projectId: targetMode === "PROJECT" ? projectId : undefined,
      scenarios: [],
    };

    let currentScenario: Scenario | null = null;
    content.split(/\r?\n/).forEach((rawLine) => {
      const line = rawLine.trim();
      if (!line || line.startsWith("#") || line.startsWith("@")) return;

      const featureMatch = line.match(/^Feature:\s*(.+)$/i);
      if (featureMatch) {
        importedFeature.name = featureMatch[1].trim() || importedFeature.name;
        return;
      }

      const scenarioMatch = line.match(/^(Scenario|Scenario Outline):\s*(.+)$/i);
      if (scenarioMatch) {
        currentScenario = {
          id: generateId(),
          name: scenarioMatch[2].trim() || "Imported scenario",
          steps: [],
        };
        importedFeature.scenarios.push(currentScenario);
        return;
      }

      const stepMatch = line.match(/^(Given|When|Then|And|But)\s+(.+)$/i);
      if (stepMatch && currentScenario) {
        const rawType = stepMatch[1].toLowerCase();
        const type = STEP_TYPES.find((candidate) => candidate.toLowerCase() === rawType) || "Given";
        currentScenario.steps.push({
          id: generateId(),
          type,
          description: stepMatch[2].trim(),
        });
      }
    });

    return importedFeature;
  };

  const handleConfirmAndSave = async (feature: Feature) => {
    if (!feature.name.trim()) {
      toast.error("A feature name is required.");
      return;
    }
    if (feature.targetMode === "PROJECT" && !feature.projectId) {
      toast.error("Select a project before saving this feature.");
      return;
    }
    if (!feature.scenarios.length || feature.scenarios.some((scenario) => !scenario.name.trim() || !scenario.steps.length || scenario.steps.some((step) => !step.description.trim()))) {
      toast.error("Each saved feature needs named scenarios with complete steps.");
      return;
    }

    if (savingFeatureIdsRef.current.has(feature.id)) {
      return;
    }

    try {
      savingFeatureIdsRef.current.add(feature.id);
      setSavingFeatureIds((current) => current.includes(feature.id) ? current : [...current, feature.id]);
      const savedFeature = await createFeature({
        name: feature.name.trim(),
        description: "Created with Feature Builder",
        gherkinContent: buildGherkin(feature),
        projectId: feature.targetMode === "PROJECT" ? feature.projectId : undefined,
        targetMode: feature.targetMode,
      });

      for (const [scenarioIndex, scenario] of feature.scenarios.entries()) {
        const savedScenario = await createScenario(savedFeature.id, {
          name: scenario.name.trim(),
          description: scenario.name.trim(),
          sequenceOrder: scenarioIndex,
        });
        for (const [stepIndex, step] of scenario.steps.entries()) {
          await createStep(savedScenario.id, {
            type: step.type,
            text: step.description.trim(),
            sequenceOrder: stepIndex,
          });
        }
      }

      onSave?.(feature);
      setFeatures((current) => current.filter((item) => item.id !== feature.id));
      toast.success("Feature saved", { description: "The feature, its scenarios, and its steps are ready in E2E Tests." });
    } catch (error) {
      toast.error("Feature could not be saved", {
        description: error instanceof Error ? error.message : "The E2E service did not accept the feature.",
      });
    } finally {
      savingFeatureIdsRef.current.delete(feature.id);
      setSavingFeatureIds((current) => current.filter((id) => id !== feature.id));
    }
  };
  const handleExportFeature = (feature: Feature) => {
    const blob = new Blob([buildGherkin(feature)], { type: "text/x-gherkin;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = safeFileName(feature.name);
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  };

  const handleImportFiles = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files || []);
    event.target.value = "";
    if (!files.length) return;
    if (targetMode === "PROJECT" && !projectId) {
      toast.error("Select a project before importing project features.");
      return;
    }

    const imported = await Promise.all(
      files.map(async (file) => parseGherkin(await file.text(), file.name))
    );
    const usable = imported.filter((feature) => feature.scenarios.length > 0);
    if (!usable.length) {
      toast.error("No valid scenarios found in the selected .feature file.");
      return;
    }

    setFeatures((current) => [...current, ...usable]);
    toast.success(`${usable.length} feature${usable.length === 1 ? "" : "s"} imported.`);
  };

  const handleAddFeature = () => {
    if (!featureName.trim() || (targetMode === "PROJECT" && !projectId)) return;
    setFeatures([
      ...features,
      {
        id: generateId(),
        name: featureName.trim(),
        scenarios: [],
        targetMode,
        projectId: targetMode === "PROJECT" ? projectId : undefined,
      }
    ]);
    setFeatureName("");
  };

  const handleDeleteFeature = (featureId: string) => {
    setFeatures(features.filter(f => f.id !== featureId));
  };

  const handleAddScenario = (featureId: string) => {
    setFeatures(features.map(f => {
      if (f.id === featureId) {
        return {
          ...f,
          scenarios: [
            ...f.scenarios,
            { id: generateId(), name: "", steps: [] }
          ]
        };
      }
      return f;
    }));
  };

  const handleDeleteScenario = (featureId: string, scenarioId: string) => {
    setFeatures(features.map(f => {
      if (f.id === featureId) {
        return {
          ...f,
          scenarios: f.scenarios.filter(s => s.id !== scenarioId)
        };
      }
      return f;
    }));
  };

  const handleUpdateScenarioName = (featureId: string, scenarioId: string, newName: string) => {
    setFeatures(features.map(f => {
      if (f.id === featureId) {
        return {
          ...f,
          scenarios: f.scenarios.map(s => s.id === scenarioId ? { ...s, name: newName } : s)
        };
      }
      return f;
    }));
  };

  const handleAddStep = (featureId: string, scenarioId: string) => {
    setFeatures(features.map(f => {
      if (f.id === featureId) {
        return {
          ...f,
          scenarios: f.scenarios.map(s => {
            if (s.id === scenarioId) {
              return {
                ...s,
                steps: [...s.steps, { id: generateId(), type: "Given", description: "" }]
              };
            }
            return s;
          })
        };
      }
      return f;
    }));
  };

  const handleDeleteStep = (featureId: string, scenarioId: string, stepId: string) => {
    setFeatures(features.map(f => {
      if (f.id === featureId) {
        return {
          ...f,
          scenarios: f.scenarios.map(s => {
            if (s.id === scenarioId) {
              return {
                ...s,
                steps: s.steps.filter(st => st.id !== stepId)
              };
            }
            return s;
          })
        };
      }
      return f;
    }));
  };

  const handleUpdateStep = (featureId: string, scenarioId: string, stepId: string, field: "type" | "description", value: string) => {
    setFeatures(features.map(f => {
      if (f.id === featureId) {
        return {
          ...f,
          scenarios: f.scenarios.map(s => {
            if (s.id === scenarioId) {
              return {
                ...s,
                steps: s.steps.map(st => st.id === stepId ? { ...st, [field]: value } : st)
              };
            }
            return s;
          })
        };
      }
      return f;
    }));
  };

  return (
    <div className="w-full font-sans pb-8">
      <div className="max-w-6xl mx-auto space-y-6">
        
        {/* Create Feature Section */}
        <div className="bg-white rounded-lg p-6 shadow-sm border border-gray-100">
          <h2 className="text-xl font-bold text-gray-900 mb-4">Create Feature</h2>
          <div className="grid gap-4 md:grid-cols-[1fr_160px_1fr_auto]">
            <input 
              placeholder="Feature name (e.g., User Login)" 
              value={featureName}
              onChange={(e) => setFeatureName(e.target.value)}
              className="min-w-0 bg-white border border-blue-200 rounded-md px-4 py-2 focus:outline-none focus:border-blue-400 focus:ring-1 focus:ring-blue-400 text-gray-700"
              onKeyDown={(e) => { if (e.key === 'Enter') handleAddFeature(); }}
            />
            <select value={targetMode} onChange={(event) => setTargetMode(event.target.value as "PROJECT" | "EXTERNAL")} className="bg-white border border-gray-200 rounded-md px-3 py-2 text-gray-700">
              <option value="PROJECT">Project</option>
              <option value="EXTERNAL">External site</option>
            </select>
            <select disabled={targetMode === "EXTERNAL"} value={projectId} onChange={(event) => setProjectId(event.target.value)} className="min-w-0 bg-white border border-gray-200 rounded-md px-3 py-2 text-gray-700 disabled:opacity-50">
              <option value="">Select project</option>
              {projects.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}
            </select>
            <button 
              onClick={handleAddFeature}
              className="bg-[#6366F1] hover:bg-[#4F46E5] text-white px-6 py-2 rounded-md font-medium flex items-center transition-colors"
            >
              <Plus className="w-4 h-4 mr-1" /> Add Feature
            </button>
          </div>
          <div className="mt-4 flex flex-wrap items-center gap-3">
            <input
              ref={fileInputRef}
              type="file"
              accept=".feature,.gherkin,.txt"
              multiple
              className="hidden"
              onChange={handleImportFiles}
            />
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              className="inline-flex items-center gap-2 rounded-md border border-[#6366F1]/40 px-4 py-2 text-sm font-semibold text-[#4F46E5] hover:bg-[#EEF2FF] transition-colors"
            >
              <FileInput className="w-4 h-4" />
              Import .feature file(s)
            </button>
            <p className="text-sm text-gray-500">
              You can import one file or many files at once. Each file becomes a feature draft.
            </p>
          </div>
        </div>

        {/* Features List */}
        <div className="space-y-6">
          {features?.map((feature) => (
            <div key={feature.id} className="bg-[#F8FAFC] rounded-lg p-6 border-l-[6px] border-[#6366F1] shadow-sm relative border border-y-gray-200 border-r-gray-200">
              
              {/* Feature Header */}
              <div className="flex justify-between items-center mb-6">
                <div className="flex items-center gap-2 text-gray-900">
                  <FileText className="w-5 h-5 text-gray-400" />
                  <h3 className="text-xl font-bold">{feature.name}</h3>
                </div>
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => handleExportFeature(feature)}
                      className="border border-[#6366F1]/40 text-[#4F46E5] hover:bg-[#EEF2FF] px-4 py-1.5 rounded-md font-medium text-sm transition-colors inline-flex items-center gap-2"
                    >
                      <Download className="w-4 h-4" />
                      Export .feature
                    </button>
                    <button
                      type="button"
                      onClick={() => void handleConfirmAndSave(feature)}
                      disabled={savingFeatureIds.includes(feature.id)}
                      className="bg-primary hover:opacity-90 text-primary-foreground px-4 py-1.5 rounded-md font-medium text-sm transition-colors disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      {savingFeatureIds.includes(feature.id) ? "Saving..." : "Confirm & Save"}
                    </button>
                    <button 
                      onClick={() => handleDeleteFeature(feature.id)}
                      className="bg-[#F87171] hover:bg-[#EF4444] text-white px-4 py-1.5 rounded-md font-medium text-sm transition-colors"
                    >
                      Delete
                    </button>
                  </div>
                </div>
              
              {/* Scenarios Header */}
              <div className="flex justify-between items-center mb-4">
                <span className="font-bold text-gray-900">Scenarios ({feature.scenarios?.length || 0})</span>
                <button 
                  onClick={() => handleAddScenario(feature.id)}
                  className="bg-[#34D399] hover:bg-[#10B981] text-white px-4 py-1.5 rounded-md font-medium flex items-center text-sm transition-colors"
                >
                  <Plus className="w-4 h-4 mr-1" /> Add Scenario
                </button>
              </div>

              {/* Scenarios List */}
              <div className="space-y-4">
                {feature.scenarios?.map((scenario) => (
                  <div key={scenario.id} className="bg-[#F0FDF4] rounded-lg p-5 border-l-[4px] border-[#34D399] border border-y-green-100 border-r-green-100">
                    
                    {/* Scenario Name Input & Delete */}
                    <div className="flex gap-4 mb-5">
                      <input 
                        value={scenario.name}
                        onChange={(e) => handleUpdateScenarioName(feature.id, scenario.id, e.target.value)}
                        placeholder="Scenario description"
                        className="flex-1 bg-white border border-gray-200 rounded-md px-4 py-2 focus:outline-none focus:border-green-400 text-gray-700"
                      />
                      <button 
                        onClick={() => handleDeleteScenario(feature.id, scenario.id)}
                        className="bg-[#F87171] hover:bg-[#EF4444] text-white px-4 py-2 rounded-md font-medium text-sm transition-colors shrink-0"
                      >
                        Delete
                      </button>
                    </div>

                    {/* Steps Header */}
                    <div className="flex justify-between items-center mb-3">
                      <span className="font-bold text-gray-900 text-sm">Steps ({scenario.steps?.length || 0})</span>
                      <button 
                        onClick={() => handleAddStep(feature.id, scenario.id)}
                        className="bg-[#60A5FA] hover:bg-[#3B82F6] text-white px-3 py-1.5 rounded-md font-medium flex items-center text-xs transition-colors"
                      >
                        <Plus className="w-3 h-3 mr-1" /> Add Step
                      </button>
                    </div>

                    {/* Steps List */}
                    <div className="space-y-2">
                      {scenario.steps?.map((step) => (
                        <div key={step.id} className="flex gap-3 items-center">
                          <select 
                            value={step.type} 
                            onChange={(e) => handleUpdateStep(feature.id, scenario.id, step.id, "type", e.target.value)}
                            className="w-32 bg-white border border-gray-200 rounded-md px-3 py-2 focus:outline-none focus:border-blue-400 text-gray-700 text-sm"
                          >
                            <option value="Given">Given</option>
                            <option value="When">When</option>
                            <option value="Then">Then</option>
                            <option value="And">And</option>
                            <option value="But">But</option>
                          </select>
                          
                          <input 
                            value={step.description}
                            placeholder="Step description"
                            className="flex-1 bg-white border border-gray-200 rounded-md px-4 py-2 focus:outline-none focus:border-blue-400 text-gray-700 text-sm"
                            onChange={(e) => handleUpdateStep(feature.id, scenario.id, step.id, "description", e.target.value)}
                          />
                          
                          <button 
                            onClick={() => handleDeleteStep(feature.id, scenario.id, step.id)}
                            className="bg-[#F87171] hover:bg-[#EF4444] text-white w-9 h-9 rounded-md flex items-center justify-center transition-colors shrink-0"
                          >
                            <X className="w-4 h-4" />
                          </button>
                        </div>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}



