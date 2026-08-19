import { cn } from "../ui/utils";

export type StepKeyword = "Given" | "When" | "Then" | "And" | "But";

interface StepKeywordChipProps {
  keyword: StepKeyword;
  className?: string;
}

export function StepKeywordChip({ keyword, className }: StepKeywordChipProps) {
  const variants = {
    Given: {
      bg: "bg-gradient-to-r from-[#7C3AED] to-[#A855F7]",
      text: "text-white",
    },
    When: {
      bg: "bg-gradient-to-r from-[#0891B2] to-[#38BDF8]",
      text: "text-white",
    },
    Then: {
      bg: "bg-gradient-to-r from-[#059669] to-[#22C55E]",
      text: "text-white",
    },
    And: {
      bg: "bg-gradient-to-r from-[#0891B2] to-[#22D3EE]",
      text: "text-white",
    },
    But: {
      bg: "bg-gradient-to-r from-[#D97706] to-[#F59E0B]",
      text: "text-white",
    },
  };

  const style = variants[keyword];

  return (
    <span
      className={cn(
        "inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold",
        style.bg,
        style.text,
        className
      )}
    >
      {keyword}
    </span>
  );
}
