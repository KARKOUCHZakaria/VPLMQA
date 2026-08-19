# 🔄 Pipeline Structure - Feature → Scenario → Steps

## Architecture

```
Feature (Test Suite)
├── Scenario 1 (Test Case)
│   ├── Step 1: Given ...
│   ├── Step 2: When ...
│   ├── Step 3: Then ...
│   ├── Logs
│   └── Screenshots
├── Scenario 2
│   ├── Step 1: Given ...
│   ├── Step 2: And ...
│   └── ...
└── ...
```

---

## 📊 Vue d'ensemble

### Niveau 1: Pipeline Horizontal (Features)
**Composant**: `FeaturePipeline.tsx`

Affiche toutes les **Features** en ligne horizontale, style Jenkins/Blue Ocean :
- Cercles colorés avec icônes de statut
- Lignes de connexion entre features
- Statistiques : X/Y scenarios passed
- Durée d'exécution par feature
- Click pour expand/collapse

**Exemples de Features** :
1. Login & Authentication (5 scenarios)
2. Dashboard UI (6 scenarios)
3. Form Validation (4 scenarios)
4. User Profile (5 scenarios)
5. Settings (3 scenarios)

---

### Niveau 2: Liste des Scénarios (Scenarios)
**Affiché** : Quand une Feature est cliquée/expandée

Montre tous les **Scénarios** de la Feature sélectionnée :
- Cards cliquables avec bordure colorée (gauche)
- Nom + description
- Tags (@critical, @smoke, etc.)
- Statut + durée
- Click → ouvre les détails

**Exemples de Scénarios** :
- "Valid credentials login" (@critical, @smoke)
- "Invalid password attempt" (@critical)
- "Chart rendering" (@visual, @critical)
- "Responsive layout" (@responsive, @critical)

---

### Niveau 3: Détails du Scénario (Steps)
**Composant**: `ScenarioDetails.tsx`

Affiche tous les **Steps** (étapes Gherkin) du scénario :
- Timeline verticale avec icônes de statut
- Chips colorés pour keywords (Given/When/Then/And/But)
- Texte de chaque step
- Messages d'erreur inline (si failed)
- Durée par step
- Logs complets (terminal style)
- Screenshots d'erreur (si disponibles)
- **Boutons d'action** : Validate / Ignore / Refuse

**Exemple de Steps** :
```gherkin
Given I am on the login page             [✓ 0.2s]
When I enter valid email "user@..."      [✓ 0.3s]
And I enter valid password "..."         [✓ 0.3s]
And I click the login button             [✓ 0.2s]
Then I should be redirected to dashboard [✓ 1.1s]
```

---

## 🎯 Workflow Utilisateur

### 1. Vue initiale
```
[Login & Auth] → [Dashboard UI] → [Form Validation] → [User Profile] → [Settings]
      ✓              ✗                  ✓                  ⟳               ⏳
    5/5            4/6                4/4                3/5             0/3
```

### 2. Click sur "Dashboard UI"
La section s'expand et montre :
```
Dashboard UI (6 scenarios)
├─ ✓ Widget loading          [1.9s] @ui
├─ ✗ Chart rendering          [3.5s] @visual @critical  ← Click ici
├─ ✓ Data refresh             [2.1s] @feature
├─ ✗ Responsive layout        [2.8s] @responsive @critical
├─ ✓ Dark theme display       [1.5s] @ui
└─ ✓ Navigation menu          [0.5s] @navigation
```

### 3. Click sur "Chart rendering"
Panneau détaillé s'affiche :
```
╔══════════════════════════════════════════════╗
║ 🔴 Chart rendering                           ║
║ 3 steps • 2 completed                     [X]║
╠══════════════════════════════════════════════╣
║                                              ║
║ STEPS                                        ║
║ ┌────────────────────────────────────────┐  ║
║ │ ✓ [Given] I am on the dashboard page   │  ║
║ │                                   0.8s  │  ║
║ ├────────────────────────────────────────┤  ║
║ │ ✓ [When] The chart component loads     │  ║
║ │                                   1.2s  │  ║
║ ├────────────────────────────────────────┤  ║
║ │ ✗ [Then] Chart displays correct data   │  ║
║ │   ⚠ Expected 12 points, found 10       │  ║
║ │                                   1.5s  │  ║
║ └────────────────────────────────────────┘  ║
║                                              ║
║ OUTPUT LOGS                                  ║
║ ┌────────────────────────────────────────┐  ║
║ │ ✓ Dashboard loaded                      │  ║
║ │ ✓ Chart initialized                     │  ║
║ │ ✗ Data validation failed                │  ║
║ │   Expected: 12, Received: 10            │  ║
║ └────────────────────────────────────────┘  ║
║                                              ║
║ ERROR SCREENSHOTS (2)                        ║
║ ┌──────┐ ┌──────┐                           ║
║ │ 🖼️   │ │ 🖼️   │                           ║
║ │chart │ │console│                           ║
║ └──────┘ └──────┘                           ║
║                                              ║
╠══════════════════════════════════════════════╣
║ Tester Action Required                      ║
║ [✓ Validate] [⚠ Ignore] [✗ Refuse]         ║
╚══════════════════════════════════════════════╝
```

---

## 📦 Données

### Features
```typescript
{
  id: "feature-1",
  name: "Login & Authentication",
  status: "passed" | "failed" | "running" | "pending",
  duration: "8.5s",
  totalScenarios: 5,
  passedScenarios: 5,
  failedScenarios: 0,
  scenarios: [...]
}
```

### Scenarios
```typescript
{
  id: "scenario-1",
  name: "Valid credentials login",
  status: "passed" | "failed" | "running" | "pending",
  duration: "2.1s",
  description: "User can login with correct email and password",
  tags: ["@critical", "@smoke"]
}
```

### Steps
```typescript
{
  id: "step-1",
  keyword: "Given" | "When" | "Then" | "And" | "But",
  text: "I am on the login page",
  status: "pending" | "running" | "completed" | "error",
  duration: "0.2s",
  error?: "Error message if failed"
}
```

---

## 🎨 Styles et Couleurs

### Status Colors
- **PASSED**: Green gradient `#059669 → #22C55E`
- **FAILED**: Red gradient `#DC2626 → #EF4444`
- **RUNNING**: Blue gradient `#0891B2 → #38BDF8` (animated pulse)
- **PENDING**: Gray `muted`

### Keyword Colors (Gherkin)
- **Given**: Purple gradient `#7C3AED → #A855F7`
- **When**: Blue gradient `#0891B2 → #38BDF8`
- **Then**: Green gradient `#059669 → #22C55E`
- **And**: Teal gradient `#0891B2 → #22D3EE`
- **But**: Amber gradient `#D97706 → #F59E0B`

---

## 🔔 Actions Testeur

Sur chaque scénario détaillé, 3 actions possibles :

### ✅ Validate
```
toast.success("Result validated ✓")
```
- Marque le résultat comme correct et important
- Génère un ticket de fix si c'est un échec validé

### ⚠️ Ignore
```
Modal avec 2 options:
1. ✅ Valid but not important
2. 🚫 Not valid and not important
```
- Permet d'ajouter un commentaire
- toast.warning("Result ignored")

### ✗ Refuse
```
toast.error("Result refused ✗")
```
- Rejette le résultat comme invalide
- Marque pour réexécution

---

## 📍 Navigation

### URL Structure (future)
```
/tests                          → Pipeline vue d'ensemble
/tests?feature=feature-2        → Feature Dashboard UI expanded
/tests?scenario=scenario-7      → Scenario "Chart rendering" detailed
```

### Breadcrumb
```
Tests > Dashboard UI > Chart rendering
```

---

## 🚀 Animations

- **Pipeline circles**: Scale on hover, glow shadow
- **Running status**: Pulse animation
- **Scenario cards**: Scale 1.02 on hover
- **Expand/collapse**: Smooth height transition
- **Connectors**: Animate color based on status

---

## 💡 Avantages de cette Structure

1. ✅ **Hiérarchie claire** : Feature → Scenario → Steps
2. ✅ **Navigation intuitive** : Click pour drill-down
3. ✅ **Vue d'ensemble** : Pipeline horizontal montre tout
4. ✅ **Détails on-demand** : Expand seulement ce qui est nécessaire
5. ✅ **Performance** : Pas de chargement de tous les détails
6. ✅ **Familier** : Style Jenkins/Blue Ocean reconnaissable
7. ✅ **Gherkin natif** : Keywords Given/When/Then colorés
8. ✅ **Action directe** : Validate/Ignore/Refuse sur scénario

---

## 🔄 État de la page

```typescript
const [selectedScenario, setSelectedScenario] = useState<ScenarioDetail | null>(null);
```

- **null** : Aucun scénario sélectionné → Affiche placeholder
- **ScenarioDetail** : Scénario sélectionné → Affiche panneau détaillé

L'état du pipeline (quelle feature est expandée) est géré dans `FeaturePipeline`.
