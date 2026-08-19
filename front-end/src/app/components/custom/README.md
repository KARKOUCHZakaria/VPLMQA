# VPLMQA Custom Component Library

This library contains custom-styled components following the purple gradient design system specified for the VPLMQA application.

## Design System Colors

### Purple Gradient Theme
- **Primary Gradient**: `#7C3AED` → `#A855F7` → `#E879F9`
- **Dark Mode Background**: `#0D0118` → `#130226` → `#1C0538`
- **Light Mode Background**: `#FAF7FF` → `#F3EDFE` → `#E5D9FD`
- **Success**: `#22C55E`
- **Warning**: `#F59E0B`
- **Error**: `#EF4444`
- **Info**: `#38BDF8`

## Components

### 1. GradientButton

Buttons with gradient backgrounds and glow effects.

**Variants:**
- `primary` - Purple gradient with glow
- `ghost` - Transparent with purple border, fills on hover
- `danger` - Red gradient for destructive actions
- `success` - Green gradient for positive actions
- `amber` - Orange/amber gradient for warnings

**Usage:**
```tsx
import { GradientButton } from "./components/custom";

<GradientButton variant="primary" onClick={handleClick}>
  Click Me
</GradientButton>
```

### 2. StatusBadge

Colored badges for test and scenario statuses.

**Variants:**
- `PASSED` - Green gradient with checkmark
- `FAILED` - Red gradient with X mark
- `RUNNING` - Blue gradient with spinning icon (animated)
- `PENDING` - Gray gradient with hourglass
- `IGNORED` - Amber gradient
- `REFUSED` - Red gradient
- `VALIDATED` - Purple gradient with checkmark

**Usage:**
```tsx
import { StatusBadge } from "./components/custom";

<StatusBadge variant="PASSED" />
<StatusBadge variant="RUNNING" />
```

### 3. StepKeywordChip

Chips for Gherkin/BDD step keywords.

**Keywords:**
- `Given` - Purple gradient
- `When` - Blue gradient
- `Then` - Green gradient
- `And` - Teal gradient
- `But` - Amber gradient

**Usage:**
```tsx
import { StepKeywordChip } from "./components/custom";

<StepKeywordChip keyword="Given" />
<StepKeywordChip keyword="When" />
```

### 4. IgnoreModal

Modal dialog for ignoring test results with two radio card options.

**Features:**
- 480px width, centered
- Blurred background overlay
- Two selectable reason cards:
  - ✅ Valid but not important
  - 🚫 Not valid and not important
- Optional comment textarea
- Cancel (ghost) and Confirm Ignore (amber gradient) buttons

**Usage:**
```tsx
import { IgnoreModal } from "./components/custom";

const [open, setOpen] = useState(false);

const handleConfirm = (reason, comment) => {
  console.log("Ignored:", reason, comment);
  toast.warning("Result ignored");
};

<IgnoreModal
  open={open}
  onOpenChange={setOpen}
  onConfirm={handleConfirm}
/>
```

### 5. GlassCard

Card with glass morphism effect and backdrop blur.

**Features:**
- Rounded corners (16px)
- Backdrop blur (20px)
- Gradient background
- Purple shadow glow

**Usage:**
```tsx
import { GlassCard } from "./components/custom";

<GlassCard>
  <h3>Card Content</h3>
  <p>This is a glass card with blur effect</p>
</GlassCard>
```

### 6. PipelineStageChip

Chips for pipeline execution stages with status icons.

**Statuses:**
- `pending` - Gray with clock icon
- `running` - Blue with spinning loader (animated)
- `passed` - Green with checkmark
- `failed` - Red with X mark

**Usage:**
```tsx
import { PipelineStageChip } from "./components/custom";

<PipelineStageChip label="Extraction" status="running" />
<PipelineStageChip label="Comparison" status="passed" />
```

## Toast Notifications

The application uses Sonner for toast notifications with three variants:

```tsx
import { toast } from "sonner";

// Success toast (green)
toast.success("Result validated ✓", {
  description: "Scenario has been validated",
  duration: 3000,
});

// Warning toast (amber)
toast.warning("Result ignored", {
  description: "Scenario has been ignored",
  duration: 3000,
});

// Error toast (red)
toast.error("Result refused ✗", {
  description: "Scenario has been rejected",
  duration: 3000,
});
```

## Styling Guidelines

### Border Radius
- Cards: `16px` (`rounded-2xl`)
- Buttons: `10px` (`rounded-[10px]`)
- Chips: `999px` (`rounded-full`)

### Shadows
- **Dark mode**: `0 0 40px rgba(124,58,237,0.15)`
- **Light mode**: `0 4px 24px rgba(124,58,237,0.12)`
- **Button glow**: `0 0 20px rgba(124,58,237,0.5)` → `0 0 30px rgba(124,58,237,0.7)` on hover

### Glass Effects
- Backdrop filter: `blur(20px)`
- Border: `1px solid rgba(168,85,247,0.2)`
- Background: Use `bg-card/80 backdrop-blur-xl` for glass cards

## Typography

The design system uses:
- **Display/Headings**: Syne Bold 700-800 (not yet implemented, defaults to system)
- **Body/Labels**: DM Sans 300-500 (not yet implemented, defaults to system)
- **Code/Logs**: JetBrains Mono 400 (use `font-mono`)

## Animations

- Button hover: `scale-[1.02]` with `transition-all`
- Spinner: `animate-spin` for loading states
- Pulse: `animate-pulse` for running/active states
