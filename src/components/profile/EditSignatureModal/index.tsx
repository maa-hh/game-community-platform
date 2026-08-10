import React, { memo } from 'react';
import type { FC } from 'react';
import { Modal } from 'antd';

import { editSignatureModalConfig } from './config';
import EditSignatureForm from './parts/EditSignatureForm';
import type { IProps } from './types';
import { useEditSignatureModal } from './useEditSignatureModal';

const EditSignatureModal: FC<IProps> = ({ open, onClose }) => {
  const { form, submitting, handleSubmit } = useEditSignatureModal(
    open,
    onClose,
  );

  return (
    <Modal
      title={editSignatureModalConfig.title}
      open={open}
      onCancel={onClose}
      onOk={handleSubmit}
      okText={editSignatureModalConfig.okText}
      confirmLoading={submitting}
      destroyOnHidden
      width={editSignatureModalConfig.width}
    >
      <EditSignatureForm form={form} onSubmit={handleSubmit} />
    </Modal>
  );
};

export default memo(EditSignatureModal);
