import React, { memo, useState } from 'react';
import type { FC } from 'react';

import './style.less';

interface IProps {
  text: string;
  className?: string;
}

const ClampText: FC<IProps> = ({ text, className }) => {
  const [expanded, setExpanded] = useState(false);
  const long = text.length > 48 || text.includes('\n');
  return (
    <div className={`clamp-text${className ? ` ${className}` : ''}`}>
      <p className={`clamp-text__body${expanded ? ' is-expanded' : ''}`}>
        {text}
      </p>
      {long && !expanded && (
        <button
          type="button"
          className="clamp-text__more"
          onClick={() => setExpanded(true)}
        >
          全文
        </button>
      )}
    </div>
  );
};

export default memo(ClampText);
