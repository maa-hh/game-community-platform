import React, { memo } from 'react';
import type { FC } from 'react';
import { Button, Input } from 'antd';

interface PostBottomComposerProps {
  draft: string;
  submitting?: boolean;
  onDraftChange: (value: string) => void;
  onSend: () => void;
  onFocusComment?: () => void;
}

const PostBottomComposer: FC<PostBottomComposerProps> = ({
  draft,
  submitting,
  onDraftChange,
  onSend,
  onFocusComment,
}) => (
  <div className="post-bottom-bar__composer">
    <Input
      value={draft}
      onChange={(e) => onDraftChange(e.target.value)}
      placeholder="说点什么…"
      maxLength={2000}
      allowClear
      onFocus={onFocusComment}
      onPressEnter={(e) => {
        if (e.nativeEvent.isComposing) return;
        onSend();
      }}
    />
    <Button type="primary" size="middle" loading={submitting} onClick={onSend}>
      发送
    </Button>
  </div>
);

export default memo(PostBottomComposer);
