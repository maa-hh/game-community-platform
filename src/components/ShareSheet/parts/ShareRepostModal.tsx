import React, { memo } from 'react';
import type { FC } from 'react';
import { Input, Modal } from 'antd';

import { REPOST_BODY_MAX, REPOST_TITLE_MAX } from '@/utils/shareRepost';

import { repostModalTips } from '../config';
import type { ShareRepostModalProps } from '../types';

const ShareRepostModal: FC<ShareRepostModalProps> = ({
  open,
  variant,
  targetName,
  title,
  content,
  submitting,
  onTitleChange,
  onContentChange,
  onCancel,
  onSubmit,
}) => {
  const actionLabel = variant === 'game' ? '分享' : '转发';
  const defaultHint =
    variant === 'game' ? `分享了${targetName}` : `转发了${targetName}`;

  return (
    <Modal
      open={open}
      title={`${actionLabel}为动态`}
      okText="发布"
      cancelText="取消"
      confirmLoading={submitting}
      onCancel={onCancel}
      onOk={onSubmit}
      centered
      destroyOnClose
      className="share-sheet__repost-modal"
    >
      <p className="share-sheet__repost-tip">{repostModalTips[variant]}</p>
      <div className="share-sheet__repost-field">
        <label
          className="share-sheet__repost-label"
          htmlFor="share-repost-title"
        >
          标题（选填）
        </label>
        <Input
          id="share-repost-title"
          value={title}
          onChange={(e) =>
            onTitleChange(e.target.value.slice(0, REPOST_TITLE_MAX))
          }
          placeholder={`不填则显示「${defaultHint}」`}
          maxLength={REPOST_TITLE_MAX}
        />
      </div>
      <div className="share-sheet__repost-field">
        <label
          className="share-sheet__repost-label"
          htmlFor="share-repost-content"
        >
          正文（选填）
        </label>
        <div className="share-sheet__textarea-wrap">
          <Input.TextArea
            id="share-repost-content"
            value={content}
            onChange={(e) =>
              onContentChange(e.target.value.slice(0, REPOST_BODY_MAX))
            }
            placeholder={`不填则显示「${defaultHint}」`}
            maxLength={REPOST_BODY_MAX}
            rows={4}
          />
          <span className="share-sheet__count">
            {content.length}/{REPOST_BODY_MAX}
          </span>
        </div>
      </div>
    </Modal>
  );
};

export default memo(ShareRepostModal);
