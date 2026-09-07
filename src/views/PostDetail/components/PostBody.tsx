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

/**
 * 详情请求会补回统计、作者关注状态等字段，这些变化不需要重建正文区。
 * 尤其是视频帖，正文区跳过这类更新可以让播放器继续复用原来的实例。
 */
function areBodyPostsEqual(
  previous: PostDetailData,
  next: PostDetailData,
): boolean {
  if (previous === next) return true;
  return (
    previous.id === next.id &&
    previous.postType === next.postType &&
    previous.title === next.title &&
    previous.content === next.content &&
    previous.contentHtml === next.contentHtml &&
    previous.coverUrl === next.coverUrl &&
    previous.videoUrl === next.videoUrl &&
    areStringArraysEqual(previous.images, next.images) &&
    areStringArraysEqual(previous.bodyImages, next.bodyImages) &&
    JSON.stringify(previous.tags) === JSON.stringify(next.tags) &&
    JSON.stringify(previous.gameTags) === JSON.stringify(next.gameTags) &&
    JSON.stringify(previous.refPost) === JSON.stringify(next.refPost)
  );
}

function areStringArraysEqual(previous?: string[], next?: string[]): boolean {
  if (previous === next) return true;
  if (!previous || !next || previous.length !== next.length) return false;
  return previous.every((value, index) => value === next[index]);
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

interface PostContentProps {
  post: PostDetailData;
}

/** 媒体区单独比较，正文、标签变化不会重建图片画廊或视频播放器。 */
const PostMedia: FC<IProps> = ({
  post,
  muted = true,
  targetDanmakuId,
  onReportDanmaku,
  danmakuReportResetKey,
}) => {
  const isImageText =
    post.postType === 'image_text' || post.postType === 'article';
  const isVideo = post.postType === 'video';
  const galleryImages = post.images?.filter(Boolean) ?? [];

  if (post.postType === 'repost') return null;

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

const areMediaPropsEqual = (previous: IProps, next: IProps) => {
  return (
    previous.muted === next.muted &&
    previous.targetDanmakuId === next.targetDanmakuId &&
    previous.danmakuReportResetKey === next.danmakuReportResetKey &&
    previous.onReportDanmaku === next.onReportDanmaku &&
    previous.post.id === next.post.id &&
    previous.post.postType === next.post.postType &&
    previous.post.title === next.post.title &&
    previous.post.coverUrl === next.post.coverUrl &&
    previous.post.videoUrl === next.post.videoUrl &&
    areStringArraysEqual(previous.post.images, next.post.images)
  );
};

const MemoPostMedia = memo(PostMedia, areMediaPropsEqual);

/** 正文和正文内图片单独比较，媒体区变化不会替换正文 DOM。 */
const PostContent: FC<PostContentProps> = ({ post }) => {
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

  let body: ReactNode = null;
  if (isRepost && post.refPost) {
    body = (
      <div className="post-body__repost">
        <RepostBlock quote={post.content} refPost={post.refPost} />
      </div>
    );
  } else if (isImageText) {
    body = (
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
  } else if (isVideo && post.content) {
    body = (
      <div className="post-body__content">
        <p className="post-body__text">{post.content}</p>
      </div>
    );
  }

  return (
    <>
      {body}
      <ImageLightbox
        open={preview.open}
        images={legacyArticleImages}
        current={preview.current}
        onChange={(cur) => setPreview((p) => ({ ...p, current: cur }))}
        onClose={() => setPreview((p) => ({ ...p, open: false }))}
      />
    </>
  );
};

const areContentPropsEqual = (
  previous: PostContentProps,
  next: PostContentProps,
) => {
  return (
    previous.post.id === next.post.id &&
    previous.post.postType === next.post.postType &&
    previous.post.content === next.post.content &&
    previous.post.contentHtml === next.post.contentHtml &&
    areStringArraysEqual(previous.post.bodyImages, next.post.bodyImages) &&
    JSON.stringify(previous.post.refPost) === JSON.stringify(next.post.refPost)
  );
};

const MemoPostContent = memo(PostContent, areContentPropsEqual);

/** 正文区：真实封面图 → 标题 → 正文 → 标签（不展示标题生成海报） */
const PostBody: FC<IProps> = ({
  post,
  muted = true,
  targetDanmakuId,
  onReportDanmaku,
  danmakuReportResetKey,
}) => {
  const title = post.title?.trim() || '';

  return (
    <div className="post-body">
      <MemoPostMedia
        post={post}
        muted={muted}
        targetDanmakuId={targetDanmakuId}
        onReportDanmaku={onReportDanmaku}
        danmakuReportResetKey={danmakuReportResetKey}
      />

      {title ? <h1 className="post-body__title">{title}</h1> : null}

      <MemoPostContent post={post} />

      <PostDisplayTags
        className="post-body__tags"
        postType={post.postType}
        tags={post.tags}
        gameTags={post.gameTags}
      />
    </div>
  );
};

export default memo(PostBody, (previous, next) => {
  return (
    previous.muted === next.muted &&
    previous.targetDanmakuId === next.targetDanmakuId &&
    previous.danmakuReportResetKey === next.danmakuReportResetKey &&
    previous.onReportDanmaku === next.onReportDanmaku &&
    areBodyPostsEqual(previous.post, next.post)
  );
});
