import React, { memo } from 'react';
import type { FC } from 'react';
import { Button, Input } from 'antd';

import type { CommentComposerProps } from '../types';

const CommentComposer: FC<CommentComposerProps> = ({
  draft,
  submitting,
  onDraftChange,
  onSubmit,
}) => (
  <div className="comment-section__composer">
    <Input
      value={draft}
      onChange={(e) => onDraftChange(e.target.value)}
      placeholder="说点什么…"
      maxLength={2000}
      allowClear
      onPressEnter={(e) => {
        if (e.nativeEvent.isComposing) return;
        e.preventDefault();
        e.stopPropagation();
        onSubmit();
      }}
    />
    <Button type="primary" loading={submitting} onClick={onSubmit}>
      发送
    </Button>
  </div>
);

export default memo(CommentComposer);
