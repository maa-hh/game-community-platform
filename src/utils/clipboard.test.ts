import { copyTextToClipboard } from './clipboard';

describe('copyTextToClipboard', () => {
  const originalClipboard = navigator.clipboard;
  const originalExecCommand = document.execCommand;

  afterEach(() => {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: originalClipboard,
    });
    document.execCommand = originalExecCommand;
    document.body.innerHTML = '';
    jest.restoreAllMocks();
  });

  it('uses the Clipboard API when available', async () => {
    const writeText = jest.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText },
    });

    await expect(copyTextToClipboard('分享内容')).resolves.toEqual({
      copied: true,
      reliable: true,
    });
    expect(writeText).toHaveBeenCalledWith('分享内容');
  });

  it('falls back when an embedded clipboard reports success without writing', async () => {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: {
        writeText: jest.fn().mockResolvedValue(undefined),
        readText: jest.fn().mockResolvedValue('旧内容'),
      },
    });
    document.execCommand = jest.fn().mockReturnValue(true);

    await expect(copyTextToClipboard('分享内容')).resolves.toEqual({
      copied: true,
      reliable: false,
    });
    expect(document.execCommand).toHaveBeenCalledWith('copy');
  });

  it('falls back to selection copy when the Clipboard API is rejected', async () => {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: jest.fn().mockRejectedValue(new Error('blocked')) },
    });
    document.execCommand = jest.fn().mockReturnValue(true);

    await expect(copyTextToClipboard('分享内容')).resolves.toEqual({
      copied: true,
      reliable: false,
    });
    expect(document.execCommand).toHaveBeenCalledWith('copy');
    expect(document.querySelector('textarea')).toBeNull();
  });

  it('treats a handled native copy event as a verified legacy copy', async () => {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: undefined,
    });
    const setData = jest.fn();
    document.execCommand = jest.fn(() => {
      const copyEvent = new Event('copy', {
        bubbles: true,
        cancelable: true,
      });
      Object.defineProperty(copyEvent, 'clipboardData', {
        value: { setData },
      });
      document.dispatchEvent(copyEvent);
      return true;
    });

    await expect(copyTextToClipboard('分享内容')).resolves.toEqual({
      copied: true,
      reliable: true,
    });
    expect(setData).toHaveBeenCalledWith('text/plain', '分享内容');
  });

  it('reports failure when both copy mechanisms are unavailable', async () => {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: undefined,
    });
    document.execCommand = jest.fn().mockReturnValue(false);

    await expect(copyTextToClipboard('分享内容')).resolves.toEqual({
      copied: false,
      reliable: false,
    });
  });
});
