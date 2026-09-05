import React, { memo } from 'react';
import type { FC } from 'react';
import { Modal } from 'antd';

import { shareSheetWidth } from '@/components/ShareSheet/config';
import ShareRepostModal from '@/components/ShareSheet/parts/ShareRepostModal';

import GameShareActions from './parts/GameShareActions';
import GameSharePreview from './parts/GameSharePreview';
import type { IGameShareSheetProps } from './types';
import { useGameShareSheet } from './useGameShareSheet';

import '@/components/ShareSheet/style.less';
import './style.less';

const GameShareSheet: FC<IGameShareSheetProps> = (props) => {
  const { open, onClose, detail, priceText } = props;
  const {
    copyTextPreview,
    repostOpen,
    shareTitle,
    shareContent,
    submitting,
    setShareTitle,
    setShareContent,
    setRepostOpen,
    handleCopySteam,
    handleCopyDetail,
    openRepost,
    submitRepost,
  } = useGameShareSheet(props);

  return (
    <>
      <Modal
        open={open}
        title="分享"
        onCancel={onClose}
        footer={null}
        centered
        width={shareSheetWidth}
        className="share-sheet game-share-sheet"
        destroyOnHidden
      >
        <GameSharePreview
          detail={detail}
          priceText={priceText}
          copyText={copyTextPreview}
        />
        <GameShareActions
          hasSteamUrl={Boolean(detail.steamUrl)}
          onRepost={openRepost}
          onCopySteam={() => void handleCopySteam()}
          onCopyDetail={() => void handleCopyDetail()}
        />
      </Modal>

      <ShareRepostModal
        open={repostOpen}
        variant="game"
        targetName={detail.name}
        title={shareTitle}
        content={shareContent}
        submitting={submitting}
        onTitleChange={setShareTitle}
        onContentChange={setShareContent}
        onCancel={() => setRepostOpen(false)}
        onSubmit={() => void submitRepost()}
      />
    </>
  );
};

export default memo(GameShareSheet);
