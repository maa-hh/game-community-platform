export type PanelKey = 'menu' | 'password' | 'email' | 'cancel';

export type EmailStep = 1 | 2;

export interface IProps {
  open: boolean;
  onClose: () => void;
}

export interface IChangePasswordValues {
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
}

export interface IChangeEmailStep1Values {
  oldCode: string;
}

export interface IChangeEmailStep2Values {
  newEmail: string;
  newCode: string;
}

export interface ICancelAccountValues {
  code: string;
}

export type SecurityMenuIconKey = 'lock' | 'mail' | 'delete';

export interface SecurityMenuItem {
  key: Exclude<PanelKey, 'menu'>;
  title: string;
  desc: string;
  danger?: boolean;
  icon: SecurityMenuIconKey;
}
