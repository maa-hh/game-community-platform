function copyWithSelection(text: string): ClipboardCopyResult {
  if (typeof document === 'undefined') {
    return { copied: false, reliable: false };
  }

  const textarea = document.createElement('textarea');
  const activeElement = document.activeElement;
  const selection = window.getSelection?.();
  const previousRanges = selection
    ? Array.from({ length: selection.rangeCount }, (_, index) =>
        selection.getRangeAt(index).cloneRange(),
      )
    : [];
  textarea.value = text;
  textarea.setAttribute('aria-hidden', 'true');
  textarea.style.position = 'fixed';
  // iOS/WebView 可能拒绝选择视口外或 display:none 的节点；留在视口内但裁成 1px。
  textarea.style.left = '0';
  textarea.style.top = '0';
  textarea.style.width = '1px';
  textarea.style.height = '1px';
  textarea.style.padding = '0';
  textarea.style.border = '0';
  textarea.style.opacity = '0';
  textarea.style.pointerEvents = 'none';
  textarea.style.fontSize = '16px';
  document.body.appendChild(textarea);

  let copied = false;
  let copyEventHandled = false;
  const handleCopy = (event: ClipboardEvent) => {
    if (!event.clipboardData) return;
    event.clipboardData.setData('text/plain', text);
    event.preventDefault();
    copyEventHandled = true;
  };
  document.addEventListener('copy', handleCopy);
  try {
    textarea.focus();
    textarea.select();
    textarea.setSelectionRange(0, text.length);
    copied = document.execCommand('copy');
  } catch {
    copied = false;
  } finally {
    document.removeEventListener('copy', handleCopy);
    textarea.remove();
    if (selection) {
      selection.removeAllRanges();
      previousRanges.forEach((range) => selection.addRange(range));
    }
    if (activeElement instanceof HTMLElement) activeElement.focus();
  }

  return {
    copied,
    // copy 事件被触发且数据已写入时，比只相信 execCommand 的返回值可靠。
    reliable: copied && copyEventHandled,
  };
}

export interface ClipboardCopyResult {
  copied: boolean;
  /** 只有 Clipboard API 成功或回读一致时才可自动关闭分享面板。 */
  reliable: boolean;
}

/** 兼容 HTTPS、HTTP 页面与部分内嵌浏览器的文本复制。 */
export async function copyTextToClipboard(
  text: string,
): Promise<ClipboardCopyResult> {
  const clipboardApiAvailable =
    typeof navigator !== 'undefined' && Boolean(navigator.clipboard?.writeText);
  const needsSynchronousFallback =
    !clipboardApiAvailable ||
    (typeof window !== 'undefined' && window.isSecureContext === false);
  let synchronousFallback: ClipboardCopyResult | null = null;

  // HTTP 与部分 WebView 会异步拒绝 Clipboard API；等到拒绝后用户激活已经失效，
  // 因此必须在点击事件仍有效时同步执行兼容复制。
  if (needsSynchronousFallback) {
    synchronousFallback = copyWithSelection(text);
    if (synchronousFallback.reliable || !clipboardApiAvailable) {
      return synchronousFallback;
    }
  }

  if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text);
      if (navigator.clipboard.readText) {
        try {
          // 部分内嵌浏览器会让 writeText 成功返回但不真正写入；能回读时必须校验。
          if ((await navigator.clipboard.readText()) !== text) {
            return synchronousFallback ?? copyWithSelection(text);
          }
        } catch {
          // 浏览器常允许写但禁止读；writeText 已完成时仍视为成功。
        }
      }
      return { copied: true, reliable: true };
    } catch {
      // HTTP 页面或内嵌浏览器可能拒绝 Clipboard API，继续尝试选区复制。
    }
  }

  // execCommand 已废弃，部分 WebView 会返回 true 却不写入。仍尝试执行，
  // 但结果不可验证，调用方必须保留手动复制入口，不能提示确定成功。
  return synchronousFallback ?? copyWithSelection(text);
}
