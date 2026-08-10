import type { IRegisterFormValues } from '@/components/auth/constants';

export interface IProps {
  loading: boolean;
  /** 注册提交：由父组件传入，仅在整表 rules 校验通过后触发 */
  onFinish: (values: IRegisterFormValues) => void;
}
