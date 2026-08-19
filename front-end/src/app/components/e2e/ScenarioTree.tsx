import React, { useState, useEffect } from 'react';
import { getFeatureWithHierarchy, createScenario, deleteFeature, Scenario } from '../../utils/e2eApi';
import { Plus, ChevronDown, ChevronRight, FileCode2 } from 'lucide-react';
import { StepEditor } from './StepEditor';

interface ScenarioTreeProps {
  featureId: string;
}

export const ScenarioTree: React.FC<ScenarioTreeProps> = ({ featureId }) => {
  const [scenarios, setScenarios] = useState<any[]>([]);
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  const [newScenarioName, setNewScenarioName] = useState('');

  useEffect(() => {
    loadHierarchy();
  }, [featureId]);

  const loadHierarchy = async () => {
    try {
      const data = await getFeatureWithHierarchy(featureId);
      setScenarios(data.scenarios || []);
      // Expand all by default
      const exp: Record<string, boolean> = {};
      (data.scenarios || []).forEach((s: any) => { exp[s.id] = true; });
      setExpanded(exp);
    } catch (e) {
      console.error("Error loading hierarchy:", e);
    }
  };

  const handleCreateScenario = async () => {

    if (!newScenarioName) return;
    try {
      await createScenario(featureId, { name: newScenarioName, description: '', sequenceOrder: scenarios.length });
      setNewScenarioName('');
      loadHierarchy();
    } catch (e) {
      console.error(e);
    }
  };

  const toggleExpand = (id: string) => {
    setExpanded(prev => ({ ...prev, [id]: !prev[id] }));
  };

  return (
    <div>
      <div className="flex justify-between items-center mb-4">
        <h3 className="text-lg font-semibold flex items-center gap-2"><FileCode2 size={18}/> Scenarios</h3>
      </div>

      <div className="flex gap-2 mb-6">
        <input 
          className="flex-1 p-2 bg-background border border-border rounded-lg" 
          placeholder="New scenario name..." 
          value={newScenarioName} 
          onChange={e => setNewScenarioName(e.target.value)} 
          onKeyDown={e => e.key === 'Enter' && handleCreateScenario()}
        />
        <button 
          className="bg-secondary text-secondary-foreground px-4 py-2 rounded-lg flex items-center gap-2 hover:bg-secondary/80"
          onClick={handleCreateScenario}
        >
          <Plus size={16} /> Add Scenario
        </button>
      </div>

      <div className="space-y-4">
        {scenarios?.map((scenario) => (
          <div key={scenario.id} className="border border-border rounded-xl bg-card overflow-hidden">
            <div 
              className="p-3 bg-muted/30 flex justify-between items-center cursor-pointer hover:bg-muted/50"
              onClick={() => toggleExpand(scenario.id)}
            >
              <div className="flex items-center gap-2 font-medium">
                {expanded[scenario.id] ? <ChevronDown size={18} /> : <ChevronRight size={18} />}
                Scenario: {scenario.name}
              </div>
            </div>
            
            {expanded[scenario.id] && (
              <div className="p-4 bg-background">
                <StepEditor scenarioId={scenario.id} initialSteps={scenario.steps || []} onStepsUpdated={loadHierarchy} />
              </div>
            )}
          </div>
        ))}
        {scenarios.length === 0 && (
          <p className="text-muted-foreground text-center py-4 border border-dashed border-border rounded-xl">
            No scenarios defined yet. Create one to get started.
          </p>
        )}
      </div>
    </div>
  );
};
