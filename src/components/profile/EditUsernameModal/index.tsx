import React, { memo } from 'react';
import type { FC } from 'react';
import { Modal } from 'antd';

import { editUsernameModalConfig } from './config';
import EditUsernameForm from './parts/EditUsernameForm';
import type { IProps } from './types';
import { useEditUsernameModal } from './useEditUsernameModal';

const EditUsernameModal: FC<IProps> = ({ open, onClose }) => {
  const { form, submitting, handleSubmit } = useEditUsernameModal(
    open,
    onClose,
  );

  return (
    <Modal
      title={editUsernameModalConfig.title}
      open={open}
      onCancel={onClose}
      onOk={handleSubmit}
      okText={editUsernameModalConfig.okText}
      confirmLoading={submitting}
      destroyOnHidden
      width={editUsernameModalConfig.width}
    >
      <EditUsernameForm form={form} onSubmit={handleSubmit} />
    </Modal>
  );
};

export default memo(EditUsernameModal);
