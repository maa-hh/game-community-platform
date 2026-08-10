import SparkMD5 from 'spark-md5';

const HASH_CHUNK_SIZE = 5 * 1024 * 1024;

/**
 * 分块计算文件 MD5，避免一次性把大文件读入内存。
 */
export async function calculateFileMd5(
  file: File,
  signal?: AbortSignal,
): Promise<string> {
  const hash = new SparkMD5.ArrayBuffer();

  for (let offset = 0; offset < file.size; offset += HASH_CHUNK_SIZE) {
    if (signal?.aborted) {
      throw new DOMException('MD5 calculation aborted', 'AbortError');
    }
    const chunk = await file
      .slice(offset, Math.min(file.size, offset + HASH_CHUNK_SIZE))
      .arrayBuffer();
    hash.append(chunk);
    // 把控制权交还给浏览器，避免计算大文件时长时间阻塞 UI。
    await new Promise<void>((resolve) => window.setTimeout(resolve, 0));
  }

  return hash.end();
}
