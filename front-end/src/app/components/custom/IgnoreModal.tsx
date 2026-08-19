import { useState } from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "../ui/dialog";
import { GradientButton } from "./GradientButton";
import { Textarea } from "../ui/textarea";
import { cn } from "../ui/utils";

interface IgnoreModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: (reason: "valid_not_important" | "not_valid_not_important", comment: string) => void;
}

export function IgnoreModal({ open, onOpenChange, onConfirm }: IgnoreModalProps) {
  const [selectedReason, setSelectedReason] = useState<"valid_not_important" | "not_valid_not_important" | null>(null);
  const [comment, setComment] = useState("");

  const handleConfirm = () => {
    if (selectedReason) {
      onConfirm(selectedReason, comment);
      setSelectedReason(null);
      setComment("");
      onOpenChange(false);
    }
  };

  const handleCancel = () => {
    setSelectedReason(null);
    setComment("");
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[480px] bg-card/95 backdrop-blur-xl border-border">
        <DialogHeader>
          <DialogTitle className="text-xl font-bold">Ignore this result</DialogTitle>
          <p className="text-sm text-muted-foreground mt-1">Choose the reason for ignoring</p>
        </DialogHeader>

        <div className="space-y-4 mt-4">
          {/* Option 1 */}
          <button
            type="button"
            onClick={() => setSelectedReason("valid_not_important")}
            className={cn(
              "w-full p-4 rounded-xl border-2 text-left transition-all hover:shadow-lg",
              selectedReason === "valid_not_important"
                ? "border-warning bg-gradient-to-r from-warning/10 to-warning/5 shadow-[0_0_20px_rgba(245,158,11,0.3)]"
                : "border-border bg-card hover:border-warning/50"
            )}
          >
            <div className="flex items-start gap-3">
              <div className="flex-shrink-0 mt-0.5">
                <div
                  className={cn(
                    "w-5 h-5 rounded-full border-2 flex items-center justify-center transition-colors",
                    selectedReason === "valid_not_important"
                      ? "border-warning bg-warning"
                      : "border-muted-foreground"
                  )}
                >
                  {selectedReason === "valid_not_important" && (
                    <div className="w-2.5 h-2.5 bg-white rounded-full" />
                  )}
                </div>
              </div>
              <div className="flex-1">
                <div className="flex items-center gap-2 mb-1">
                  <span className="text-lg">✅</span>
                  <h4 className="font-semibold">Valid but not important</h4>
                </div>
                <p className="text-sm text-muted-foreground">
                  The issue exists but does not affect user experience significantly
                </p>
              </div>
            </div>
          </button>

          {/* Option 2 */}
          <button
            type="button"
            onClick={() => setSelectedReason("not_valid_not_important")}
            className={cn(
              "w-full p-4 rounded-xl border-2 text-left transition-all hover:shadow-lg",
              selectedReason === "not_valid_not_important"
                ? "border-error bg-gradient-to-r from-error/10 to-error/5 shadow-[0_0_20px_rgba(239,68,68,0.3)]"
                : "border-border bg-card hover:border-error/50"
            )}
          >
            <div className="flex items-start gap-3">
              <div className="flex-shrink-0 mt-0.5">
                <div
                  className={cn(
                    "w-5 h-5 rounded-full border-2 flex items-center justify-center transition-colors",
                    selectedReason === "not_valid_not_important"
                      ? "border-error bg-error"
                      : "border-muted-foreground"
                  )}
                >
                  {selectedReason === "not_valid_not_important" && (
                    <div className="w-2.5 h-2.5 bg-white rounded-full" />
                  )}
                </div>
              </div>
              <div className="flex-1">
                <div className="flex items-center gap-2 mb-1">
                  <span className="text-lg">🚫</span>
                  <h4 className="font-semibold">Not valid and not important</h4>
                </div>
                <p className="text-sm text-muted-foreground">
                  This result is incorrect and irrelevant — discard entirely
                </p>
              </div>
            </div>
          </button>

          {/* Comment textarea */}
          <div className="space-y-2">
            <label htmlFor="comment" className="text-sm font-medium">
              Add a note <span className="text-muted-foreground">(optional)</span>
            </label>
            <Textarea
              id="comment"
              placeholder="Enter your comment here..."
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              className="min-h-[100px] resize-none bg-input-background border-input"
            />
          </div>

          {/* Footer buttons */}
          <div className="flex items-center gap-3 pt-2">
            <GradientButton
              variant="ghost"
              onClick={handleCancel}
              className="flex-1"
            >
              Cancel
            </GradientButton>
            <GradientButton
              variant="amber"
              onClick={handleConfirm}
              disabled={!selectedReason}
              className="flex-1"
            >
              Confirm Ignore
            </GradientButton>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
