import type { ILoginFormValues } from '@/components/auth/constants';

export interface IProps {
  loading: boolean;
  onFinish: (values: ILoginFormValues) => void;
  onForgotPassword?: () => void;
}
