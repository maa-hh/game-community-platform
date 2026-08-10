import React, { memo } from 'react';
import type { FC } from 'react';
import { Modal } from 'antd';

import { shareSheetWidth } from './config';
import ShareRepostModal from './parts/ShareRepostModal';
import ShareActions from './parts/ShareActions';
import ShareLinkPreview from './parts/ShareLinkPreview';
import type { IShareSheetProps } from './types';
import { useShareSheet } from './useShareSheet';

import './style.less';

const ShareSheet: FC<IShareSheetProps> = (props) => {
  const { open, onClose } = props;
  const {
    shareCard,
    copyText,
    repostOpen,
    repostTitle,
    repostContent,
    submitting,
    setRepostTitle,
    setRepostContent,
    setRepostOpen,
    handleCopy,
    openRepost,
    submitRepost,
  } = useShareSheet(props);

  return (
    <>
      <Modal
        open={open}
        title="分享"
        onCancel={onClose}
        footer={null}
        centered
        width={shareSheetWidth}
        className="share-sheet"
        destroyOnClose
      >
        <ShareLinkPreview card={shareCard} copyText={copyText} />
        <ShareActions onCopy={handleCopy} onRepost={openRepost} />
      </Modal>

      <ShareRepostModal
        open={repostOpen}
        variant="post"
        targetName={props.articleTitle}
        title={repostTitle}
        content={repostContent}
        submitting={submitting}
        onTitleChange={setRepostTitle}
        onContentChange={setRepostContent}
        onCancel={() => setRepostOpen(false)}
        onSubmit={submitRepost}
      />
    </>
  );
};

export default memo(ShareSheet);
