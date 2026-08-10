import React, { memo, useMemo } from 'react';
import type { CSSProperties, FC, ReactNode } from 'react';

import LazyImage from '@/base-ui/LazyImage';
import { normalizeSteamRichHtml } from '@/utils/normalizeSteamRichHtml';

import './style.less';

const VOID_TAGS = new Set([
  'area',
  'base',
  'br',
  'col',
  'embed',
  'hr',
  'img',
  'input',
  'link',
  'meta',
  'param',
  'source',
  'track',
  'wbr',
]);

function parseInlineStyle(style: string): CSSProperties {
  const result: Record<string, string> = {};
  style.split(';').forEach((pair) => {
    const colon = pair.indexOf(':');
    if (colon <= 0) return;
    const key = pair.slice(0, colon).trim();
    const value = pair.slice(colon + 1).trim();
    if (!key || !value) return;
    const camelKey = key.replace(/-([a-z])/g, (_, char: string) =>
      char.toUpperCase(),
    );
    result[camelKey] = value;
  });
  return result;
}

function buildElementProps(el: Element): Record<string, unknown> {
  const props: Record<string, unknown> = {};

  for (const attr of Array.from(el.attributes)) {
    if (attr.name === 'class') {
      props.className = attr.value;
      continue;
    }
    if (attr.name === 'style') {
      props.style = parseInlineStyle(attr.value);
      continue;
    }
    if (attr.name === 'for') {
      props.htmlFor = attr.value;
      continue;
    }
    props[attr.name] = attr.value;
  }

  if (el.tagName.toLowerCase() === 'a') {
    const href = el.getAttribute('href');
    if (href?.startsWith('http')) {
      props.target = props.target || '_blank';
      props.rel = 'noopener noreferrer';
    }
  }

  return props;
}

function renderNode(node: ChildNode, key: string): ReactNode {
  if (node.nodeType === Node.TEXT_NODE) {
    const text = node.textContent ?? '';
    return text.trim() ? text : null;
  }

  if (node.nodeType !== Node.ELEMENT_NODE) {
    return null;
  }

  const el = node as Element;
  const tag = el.tagName.toLowerCase();

  if (tag === 'img') {
    return (
      <LazyImage
        key={key}
        src={el.getAttribute('src')}
        alt={el.getAttribute('alt') || ''}
        className="steam-rich-html__image"
        imgClassName="steam-rich-html__image-el"
        fallback={
          <span className="steam-rich-html__image-fallback" aria-hidden />
        }
      />
    );
  }

  const props = buildElementProps(el);

  if (VOID_TAGS.has(tag)) {
    return React.createElement(tag, { key, ...props });
  }

  const children = Array.from(el.childNodes)
    .map((child, index) => renderNode(child, `${key}-${index}`))
    .filter((child) => child != null && child !== '');

  return React.createElement(tag, { key, ...props }, ...children);
}

export interface SteamRichHtmlProps {
  html?: string;
  className?: string;
}

/** Steam 商店富文本：图片走 LazyImage，其余标签按 DOM 结构还原 */
const SteamRichHtml: FC<SteamRichHtmlProps> = ({ html, className }) => {
  const content = useMemo(() => {
    if (!html?.trim()) return null;
    const normalized = normalizeSteamRichHtml(html);
    if (!normalized.trim()) return null;

    const doc = new DOMParser().parseFromString(normalized, 'text/html');
    return Array.from(doc.body.childNodes)
      .map((node, index) => renderNode(node, `steam-rich-${index}`))
      .filter((node) => node != null && node !== '');
  }, [html]);

  if (!content?.length) return null;

  return <div className={className}>{content}</div>;
};

export default memo(SteamRichHtml);
