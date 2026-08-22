import React, { memo, useEffect, useMemo, useRef, useState } from 'react';
import type { FC, ReactNode } from 'react';

import CoverGallery from '@/base-ui/CoverGallery';
import ImageLightbox from '@/base-ui/ImageLightbox';
import PostDisplayTags from '@/components/PostDisplayTags';
import RepostBlock from '@/components/RepostBlock';
import type { PostDetailData } from '@/types/post';
import {
  BODY_IMAGE_MARKER_SPLIT_RE,
  parseImageMarker,
} from '@/utils/bodyImageMarker';

import DanmakuPlayer from './DanmakuPlayer';

import './PostBody.less';

interface IProps {
  post: PostDetailData;
  muted?: boolean;
  targetDanmakuId?: number;
  onReportDanmaku?: (messageId: string) => void;
  danmakuReportResetKey?: number;
}

function extractHtmlImages(html: string): string[] {
  const urls: string[] = [];
  const re = /<img[^>]+src=["']([^"']+)["']/gi;
  let m: RegExpExecArray | null = re.exec(html);
  while (m) {
    if (m[1]) urls.push(m[1]);
    m = re.exec(html);
  }
  return urls;
}

function renderTextWithBodyImages(
  text: string,
  bodyImages: string[],
): ReactNode[] {
  const parts = text.split(BODY_IMAGE_MARKER_SPLIT_RE);
  return parts
    .map((part, index) => {
      const marker = parseImageMarker(part);
      if (marker) {
        const src = bodyImages[marker.index];
        if (!src) return null;
        return (
          <img
            key={`img-${index}`}
            src={src}
            alt=""
            className="post-body__inline-img"
            style={{
              width: `${marker.widthPercent}%`,
              maxHeight: marker.maxHeight ? `${marker.maxHeight}px` : undefined,
            }}
          />
        );
      }
      if (!part) return null;
      return <span key={`text-${index}`}>{part}</span>;
    })
    .filter(Boolean);
}

/** 正文区：真实封面图 → 标题 → 正文 → 标签（不展示标题生成海报） */
const PostBody: FC<IProps> = ({
  post,
  muted = true,
  targetDanmakuId,
  onReportDanmaku,
  danmakuReportResetKey,
}) => {
  const htmlRef = useRef<HTMLDivElement>(null);
  const [preview, setPreview] = useState<{
    open: boolean;
    current: number;
  }>({ open: false, current: 0 });

  const legacyArticleImages = useMemo(() => {
    if (!post.contentHtml) return [] as string[];
    return extractHtmlImages(post.contentHtml);
  }, [post.contentHtml]);

  useEffect(() => {
    const root = htmlRef.current;
    if (!root || legacyArticleImages.length === 0) return undefined;

    const onClick = (e: MouseEvent) => {
      const target = e.target as HTMLElement | null;
      if (!target || target.tagName !== 'IMG') return;
      const src =
        (target as HTMLImageElement).currentSrc || target.getAttribute('src');
      if (!src) return;
      const idx = legacyArticleImages.findIndex(
        (u) => src.includes(u) || u.includes(src),
      );
      e.preventDefault();
      setPreview({ open: true, current: idx >= 0 ? idx : 0 });
    };

    root.addEventListener('click', onClick);
    return () => root.removeEventListener('click', onClick);
  }, [legacyArticleImages, post.contentHtml]);

  const isImageText =
    post.postType === 'image_text' || post.postType === 'article';
  const isVideo = post.postType === 'video';
  const isRepost = post.postType === 'repost';
  const bodyImages = post.bodyImages || [];
  const title = post.title?.trim() || '';
  const galleryImages = post.images?.filter(Boolean) ?? [];

  const renderMedia = () => {
    if (isRepost) return null;

    if (isVideo && post.videoUrl) {
      return (
        <div className="post-body__player">
          <DanmakuPlayer
            url={post.videoUrl}
            pic={post.coverUrl}
            title={post.title}
            muted={muted}
            videoPublicId={post.id}
            targetDanmakuId={targetDanmakuId}
            onReport={onReportDanmaku}
            reportResetKey={danmakuReportResetKey}
          />
        </div>
      );
    }

    if (isImageText && galleryImages.length > 0) {
      return (
        <div className="post-body__gallery">
          <CoverGallery images={galleryImages} />
        </div>
      );
    }

    return null;
  };

  const renderBody = () => {
    if (isRepost && post.refPost) {
      return (
        <div className="post-body__repost">
          <RepostBlock quote={post.content} refPost={post.refPost} />
        </div>
      );
    }

    if (isImageText) {
      return (
        <div className="post-body__content">
          {post.contentHtml ? (
            <div
              ref={htmlRef}
              className="post-body__html"
              dangerouslySetInnerHTML={{ __html: post.contentHtml }}
            />
          ) : (
            <p className="post-body__text">
              {bodyImages.length > 0
                ? renderTextWithBodyImages(post.content, bodyImages)
                : post.content}
            </p>
          )}
        </div>
      );
    }

    if (isVideo) {
      return post.content ? (
        <div className="post-body__content">
          <p className="post-body__text">{post.content}</p>
        </div>
      ) : null;
    }

    return null;
  };

  return (
    <div className="post-body">
      {renderMedia()}

      {title ? <h1 className="post-body__title">{title}</h1> : null}

      {renderBody()}

      <PostDisplayTags
        className="post-body__tags"
        postType={post.postType}
        tags={post.tags}
        gameTags={post.gameTags}
      />

      <ImageLightbox
        open={preview.open}
        images={legacyArticleImages}
        current={preview.current}
        onChange={(cur) => setPreview((p) => ({ ...p, current: cur }))}
        onClose={() => setPreview((p) => ({ ...p, open: false }))}
      />
    </div>
  );
};

export default memo(PostBody);
