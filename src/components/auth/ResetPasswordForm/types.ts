import type { IResetPasswordFormValues } from '@/components/auth/constants';

export interface IProps {
  loading: boolean;
  onFinish: (values: IResetPasswordFormValues) => void;
  onBackToLogin: () => void;
}
