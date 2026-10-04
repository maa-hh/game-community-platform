import React, { memo } from 'react';
import type { FC } from 'react';

import './style.less';

interface ManualCopyFieldProps {
  text: string;
}

/** 浏览器拒绝自动写入剪贴板时，提供与普通正文一致的原生文本选区。 */
const ManualCopyField: FC<ManualCopyFieldProps> = ({ text }) => (
  <div className="manual-copy-field" role="status">
    <p className="manual-copy-field__tip">
      自动复制受浏览器限制，请在下方文字中长按并选择“复制”
    </p>
    <pre
      className="manual-copy-field__input"
      aria-label="待手动复制的内容"
      tabIndex={0}
    >
      {text}
    </pre>
  </div>
);

export default memo(ManualCopyField);
