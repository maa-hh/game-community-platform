import React, { memo } from 'react';

import { appShallowEqual, useAppDispatch, useAppSelector } from '@/store';
import { decrement, increment } from '@/store/modules/counter';

// 业务组件：演示 useAppSelector + shallowEqual 的正确用法
function CounterPanel() {
  const dispatch = useAppDispatch();

  const { count, message } = useAppSelector(
    (state) => ({
      count: state.counter.count,
      message: state.counter.message,
    }),
    appShallowEqual,
  );

  return (
    <div className="counter-panel">
      <p>count: {count}</p>
      <p>message: {message}</p>
      <button type="button" onClick={() => dispatch(increment())}>
        +1
      </button>
      <button type="button" onClick={() => dispatch(decrement())}>
        -1
      </button>
    </div>
  );
}

export default memo(CounterPanel);
