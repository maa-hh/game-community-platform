import React, { memo } from 'react';
import type { FC } from 'react';
import { Input, Modal } from 'antd';

import { REPOST_QUOTE_MAX, repostModalTip } from '../config';
import type { RepostModalProps } from '../types';

const RepostModal: FC<RepostModalProps> = ({
  open,
  quote,
  submitting,
  onQuoteChange,
  onCancel,
  onSubmit,
}) => (
  <Modal
    open={open}
    title="转发动态"
    okText="发布"
    cancelText="取消"
    confirmLoading={submitting}
    onCancel={onCancel}
    onOk={onSubmit}
    centered
    destroyOnHidden
    className="share-sheet__repost-modal"
  >
    <p className="share-sheet__repost-tip">{repostModalTip}</p>
    <div className="share-sheet__textarea-wrap">
      <Input.TextArea
        value={quote}
        onChange={(e) =>
          onQuoteChange(e.target.value.slice(0, REPOST_QUOTE_MAX))
        }
        placeholder="说点什么…"
        maxLength={REPOST_QUOTE_MAX}
        rows={4}
      />
      <span className="share-sheet__count">
        {quote.length}/{REPOST_QUOTE_MAX}
      </span>
    </div>
  </Modal>
);

export default memo(RepostModal);
