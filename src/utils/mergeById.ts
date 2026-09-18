export type MergeKey = string | number;

/**
 * 保持旧列表顺序，用新数据覆盖同 ID 记录，并把新 ID 追加到末尾。
 * 统一用于分页回填、通知增量和局部刷新，避免各页面各写一套 append 逻辑。
 */
export function mergeById<T>(
  previous: T[],
  incoming: T[],
  getKey: (item: T) => MergeKey | undefined,
): T[] {
  const indexByKey = new Map<MergeKey, number>();
  const result = previous.slice();

  result.forEach((item, index) => {
    const key = getKey(item);
    if (key != null) indexByKey.set(key, index);
  });

  incoming.forEach((item) => {
    const key = getKey(item);
    if (key == null) {
      result.push(item);
      return;
    }

    const existingIndex = indexByKey.get(key);
    if (existingIndex == null) {
      indexByKey.set(key, result.length);
      result.push(item);
    } else {
      result[existingIndex] = item;
    }
  });

  return result;
}
