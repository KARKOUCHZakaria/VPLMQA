import React, { useState } from "react";
import { Plus, X, FileText } from "lucide-react";
import { TopBar } from "../components/custom/TopBar";

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
};

export function FeatureBuilder() {
  const [featureName, setFeatureName] = useState("");
  const [features, setFeatures] = useState<Feature[]>([]);

  const generateId = () => Math.random().toString(36).substr(2, 9);

  const handleAddFeature = () => {
    if (!featureName.trim()) return;
    setFeatures([
      ...features,
      {
        id: generateId(),
        name: featureName.trim(),
        scenarios: []
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
    <div className="min-h-screen bg-[#F8FAFC] flex flex-col font-sans">
      <TopBar title="Feature Builder" />
      
      <div className="flex-1 overflow-auto py-8 px-8">
        <div className="max-w-6xl mx-auto space-y-6">
          
          {/* Create Feature Section */}
          <div className="bg-white rounded-lg p-6 shadow-sm border border-gray-100">
            <h2 className="text-xl font-bold text-gray-900 mb-4">Create Feature</h2>
            <div className="flex gap-4">
              <input 
                placeholder="Feature name (e.g., User Login)" 
                value={featureName}
                onChange={(e) => setFeatureName(e.target.value)}
                className="flex-1 bg-white border border-blue-200 rounded-md px-4 py-2 focus:outline-none focus:border-blue-400 focus:ring-1 focus:ring-blue-400 text-gray-700"
                onKeyDown={(e) => { if (e.key === 'Enter') handleAddFeature(); }}
              />
              <button 
                onClick={handleAddFeature}
                className="bg-[#6366F1] hover:bg-[#4F46E5] text-white px-6 py-2 rounded-md font-medium flex items-center transition-colors"
              >
                <Plus className="w-4 h-4 mr-1" /> Add Feature
              </button>
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
                  <button 
                    onClick={() => handleDeleteFeature(feature.id)}
                    className="bg-[#F87171] hover:bg-[#EF4444] text-white px-4 py-1.5 rounded-md font-medium text-sm transition-colors"
                  >
                    Delete
                  </button>
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
    </div>
  );
}
