interface IRgb {
  r: number;
  g: number;
  b: number;
}

function expandHex(hex: string): string {
  if (hex.length === 3) {
    return hex
      .split('')
      .map((char) => char + char)
      .join('');
  }
  return hex;
}

export function parseCssColorToRgb(color?: string): IRgb | undefined {
  if (!color?.trim()) return undefined;

  const trimmed = color.trim().toLowerCase();
  const hexMatch = trimmed.match(/^#([0-9a-f]{3}|[0-9a-f]{6})$/i);
  if (hexMatch) {
    const hex = expandHex(hexMatch[1]);
    return {
      r: Number.parseInt(hex.slice(0, 2), 16),
      g: Number.parseInt(hex.slice(2, 4), 16),
      b: Number.parseInt(hex.slice(4, 6), 16),
    };
  }

  const rgbMatch = trimmed.match(
    /^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)/,
  );
  if (rgbMatch) {
    return {
      r: Number(rgbMatch[1]),
      g: Number(rgbMatch[2]),
      b: Number(rgbMatch[3]),
    };
  }

  return undefined;
}

export function getRelativeLuminance(rgb: IRgb): number {
  const [rs, gs, bs] = [rgb.r, rgb.g, rgb.b].map((channel) => {
    const value = channel / 255;
    return value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * rs + 0.7152 * gs + 0.0722 * bs;
}

export function isDarkColor(color?: string): boolean {
  const rgb = parseCssColorToRgb(color);
  if (!rgb) return false;
  return getRelativeLuminance(rgb) < 0.45;
}
