export function createImageId() {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
}

export function dedupeUrls(urls: string[]) {
  const seen = new Set<string>();
  return urls.filter((url) => {
    const key = url.trim();
    if (!key || seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

export function revokeBlobUrl(url: string | null | undefined) {
  if (url?.startsWith('blob:')) {
    URL.revokeObjectURL(url);
  }
}
