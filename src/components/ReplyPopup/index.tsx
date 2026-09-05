import React, { memo, useEffect, useState } from 'react';
import type { FC } from 'react';
import { Button, Input, Modal } from 'antd';

import type { IProps } from './types';

import './style.less';

/** 就地回复弹窗 */
const ReplyPopup: FC<IProps> = ({
  open,
  nickname,
  loading,
  onClose,
  onSubmit,
}) => {
  const [value, setValue] = useState('');

  useEffect(() => {
    if (open) setValue('');
  }, [open, nickname]);

  return (
    <Modal
      open={open}
      title={`回复 @${nickname}`}
      onCancel={onClose}
      footer={null}
      centered
      width={420}
      destroyOnHidden
      className="reply-popup"
    >
      <Input
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder={`回复 @${nickname}`}
        maxLength={1000}
        allowClear
        onPressEnter={(e) => {
          if (e.nativeEvent.isComposing) return;
          const text = value.trim();
          if (text) onSubmit(text);
        }}
        autoFocus
      />
      <div className="reply-popup__footer">
        <Button onClick={onClose}>取消</Button>
        <Button
          type="primary"
          loading={loading}
          onClick={() => {
            const text = value.trim();
            if (text) onSubmit(text);
          }}
        >
          回复
        </Button>
      </div>
    </Modal>
  );
};

export default memo(ReplyPopup);
