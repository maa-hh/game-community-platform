import React, { memo } from 'react';
import type { FC } from 'react';
import { Modal } from 'antd';

import { getSecurityTitle } from './config';
import CancelAccountPanel from './parts/CancelAccountPanel';
import ChangeEmailStep1Panel from './parts/ChangeEmailStep1Panel';
import ChangeEmailStep2Panel from './parts/ChangeEmailStep2Panel';
import ChangePasswordPanel from './parts/ChangePasswordPanel';
import SecurityMenu from './parts/SecurityMenu';
import SecurityModalTitle from './parts/SecurityModalTitle';
import type { IProps } from './types';
import { useAccountSecurityModal } from './useAccountSecurityModal';

import './style.less';

const AccountSecurityModal: FC<IProps> = ({ open, onClose }) => {
  const {
    user,
    panel,
    emailStep,
    submitting,
    sendingCode,
    countdown,
    passwordForm,
    emailStep1Form,
    emailStep2Form,
    cancelForm,
    handleMenuClick,
    handleBack,
    handleSendOldEmailCode,
    handleEmailStep1Next,
    handleSendNewEmailCode,
    handleSendCancelCode,
    handleChangePassword,
    handleConfirmChangeEmail,
    handleCancelAccount,
  } = useAccountSecurityModal(open, onClose);

  return (
    <Modal
      title={
        <SecurityModalTitle
          title={getSecurityTitle(panel, emailStep)}
          showBack={panel !== 'menu'}
          submitting={submitting}
          onBack={handleBack}
        />
      }
      open={open}
      onCancel={() => {
        if (submitting) return;
        onClose();
      }}
      footer={null}
      destroyOnHidden
      maskClosable={!submitting}
      keyboard={!submitting}
      width={440}
      className="account-security-modal"
    >
      {panel === 'menu' && <SecurityMenu onMenuClick={handleMenuClick} />}

      {panel === 'password' && (
        <ChangePasswordPanel
          form={passwordForm}
          submitting={submitting}
          onFinish={handleChangePassword}
        />
      )}

      {panel === 'email' && emailStep === 1 && (
        <ChangeEmailStep1Panel
          form={emailStep1Form}
          currentEmail={user?.email}
          submitting={submitting}
          sendingCode={sendingCode}
          countdown={countdown}
          onSendCode={handleSendOldEmailCode}
          onFinish={handleEmailStep1Next}
        />
      )}

      {panel === 'email' && emailStep === 2 && (
        <ChangeEmailStep2Panel
          form={emailStep2Form}
          submitting={submitting}
          sendingCode={sendingCode}
          countdown={countdown}
          onSendCode={handleSendNewEmailCode}
          onFinish={handleConfirmChangeEmail}
        />
      )}

      {panel === 'cancel' && (
        <CancelAccountPanel
          form={cancelForm}
          currentEmail={user?.email}
          submitting={submitting}
          sendingCode={sendingCode}
          countdown={countdown}
          onSendCode={handleSendCancelCode}
          onFinish={handleCancelAccount}
        />
      )}
    </Modal>
  );
};

export default memo(AccountSecurityModal);
