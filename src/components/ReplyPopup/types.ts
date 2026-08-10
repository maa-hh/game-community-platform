export interface IProps {
  open: boolean;
  nickname: string;
  loading?: boolean;
  onClose: () => void;
  onSubmit: (content: string) => void;
}
