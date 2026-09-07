export interface FeedbackModalProps {
  open: boolean;
  onClose: () => void;
  onSuccess?: () => void;
}

export interface FeedbackFormValues {
  feedbackType: string;
  content: string;
}
