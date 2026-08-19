import React, { useState } from 'react';
import { createStep, Step } from '../../utils/e2eApi';
import { Plus, Trash2, GripVertical } from 'lucide-react';

interface StepEditorProps {
  scenarioId: string;
  initialSteps: any[];
  onStepsUpdated: () => void;
}

const STEP_TYPES = ['Given', 'When', 'Then', 'And', 'But'];

export const StepEditor: React.FC<StepEditorProps> = ({ scenarioId, initialSteps, onStepsUpdated }) => {
  const [newType, setNewType] = useState('Given');
  const [newText, setNewText] = useState('');

  const handleAddStep = async () => {
    if (!newText) return;
    try {
      await createStep(scenarioId, { 
        type: newType, 
        text: newText,
        sequenceOrder: initialSteps.length
      });
      setNewText('');
      onStepsUpdated();
    } catch (e) {
      console.error(e);
    }
  };

  const getStepColor = (type: string) => {
    switch (type) {
      case 'Given': return 'text-blue-500 bg-blue-500/10 border-blue-500/20';
      case 'When': return 'text-yellow-500 bg-yellow-500/10 border-yellow-500/20';
      case 'Then': return 'text-green-500 bg-green-500/10 border-green-500/20';
      case 'And':
      case 'But': return 'text-purple-500 bg-purple-500/10 border-purple-500/20';
      default: return 'text-foreground bg-muted border-border';
    }
  };

  return (
    <div className="space-y-2">
      {initialSteps.map((step, idx) => (
        <div key={step.id} className="flex items-center gap-3 p-2 rounded hover:bg-muted/30 group">
          <GripVertical size={16} className="text-muted-foreground opacity-30 group-hover:opacity-100 cursor-grab" />
          <span className={`px-2 py-1 text-xs font-bold uppercase rounded border ${getStepColor(step.type)} min-w-[60px] text-center`}>
            {step.type}
          </span>
          <span className="flex-1 text-sm font-mono">{step.text}</span>
          <button className="text-muted-foreground hover:text-error opacity-0 group-hover:opacity-100 transition-opacity">
            <Trash2 size={14} />
          </button>
        </div>
      ))}

      <div className="flex gap-2 items-center mt-4 pt-2 border-t border-border">
        <select 
          className="p-2 bg-background border border-border rounded-lg text-sm"
          value={newType}
          onChange={e => setNewType(e.target.value)}
        >
          {STEP_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
        </select>
        <input 
          className="flex-1 p-2 bg-background border border-border rounded-lg text-sm font-mono" 
          placeholder="I click on the login button..." 
          value={newText}
          onChange={e => setNewText(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && handleAddStep()}
        />
        <button 
          className="p-2 bg-primary/20 text-primary rounded-lg hover:bg-primary/30"
          onClick={handleAddStep}
        >
          <Plus size={18} />
        </button>
      </div>
    </div>
  );
};
