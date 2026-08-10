/** 游戏名原始字段（不同接口字段名可能不一致） */
export interface IGameNameFields {
  name?: string;
  localizedName?: string;
  nameCn?: string;
  chineseName?: string;
}

function hasCjk(text: string): boolean {
  return /[\u3400-\u9fff]/.test(text);
}

/** 统一展示名：优先中文/本地化名，避免各接口中英文混用 */
export function resolveGameDisplayName(fields: IGameNameFields): string {
  const localized =
    fields.localizedName?.trim() ||
    fields.nameCn?.trim() ||
    fields.chineseName?.trim();
  const name = fields.name?.trim();

  if (localized && name) {
    const localizedCjk = hasCjk(localized);
    const nameCjk = hasCjk(name);
    if (localizedCjk && !nameCjk) return localized;
    if (nameCjk && !localizedCjk) return name;
    return localized;
  }

  return localized || name || '';
}
