import type { AuthMode } from '@/components/auth/constants';
import type {
  ILoginFormValues,
  IRegisterFormValues,
  IResetPasswordFormValues,
} from '@/components/auth/constants';

export interface AuthModalBodyProps {
  mode: AuthMode;
  authTip: string;
  error: string | null;
  loading: boolean;
  successRedirecting: boolean;
  onModeChange: (value: string | number) => void;
  onClearAuthTip: () => void;
  onClearError: () => void;
  onLogin: (values: ILoginFormValues) => void;
  onRegister: (values: IRegisterFormValues) => void;
  onResetPassword: (values: IResetPasswordFormValues) => void;
  onForgotPassword: () => void;
  onBackToLogin: () => void;
}
