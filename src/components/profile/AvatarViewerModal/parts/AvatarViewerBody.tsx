import React from 'react';
import type { FC } from 'react';
import { Avatar, Button, Upload } from 'antd';
import { CameraOutlined } from '@ant-design/icons';
import type { UploadProps } from 'antd';

import { ACCEPT_TYPES, avatarViewerModalConfig } from '../config';

interface IProps {
  displayAvatar?: string;
  displayName: string;
  avatarBusy: boolean;
  uploading: boolean;
  beforeUpload: UploadProps['beforeUpload'];
}

const AvatarViewerBody: FC<IProps> = ({
  displayAvatar,
  displayName,
  avatarBusy,
  uploading,
  beforeUpload,
}) => {
  return (
    <div className="avatar-viewer-modal__body">
      <Avatar
        size={avatarViewerModalConfig.avatarSize}
        src={displayAvatar}
        className="avatar-viewer-modal__preview"
      >
        {displayName.slice(0, 1).toUpperCase()}
      </Avatar>

      <Upload
        accept={ACCEPT_TYPES.join(',')}
        showUploadList={false}
        beforeUpload={beforeUpload}
        disabled={avatarBusy || uploading}
      >
        <Button
          type="primary"
          icon={<CameraOutlined />}
          loading={uploading}
          disabled={avatarBusy}
          block
        >
          {avatarBusy
            ? avatarViewerModalConfig.messages.busyButton
            : avatarViewerModalConfig.messages.uploadButton}
        </Button>
      </Upload>
    </div>
  );
};

export default AvatarViewerBody;
