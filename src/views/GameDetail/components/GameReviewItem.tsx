import React, { useState } from 'react';
import type { FC } from 'react';
import { Button, Input, List, Rate, Space, Typography, message } from 'antd';
import {
  LikeFilled,
  LikeOutlined,
  MessageOutlined,
  SendOutlined,
} from '@ant-design/icons';

import ProfileUserLink from '@/components/ProfileUserLink';
import { formatApiError } from '@/utils/apiError';
import { formatDateTime } from '@/utils/mapPost';
import type { IGameReview, IGameReviewReply } from '@/types/game';
import {
  addGameReviewReplyApi,
  fetchGameReviewRepliesApi,
  likeGameReviewApi,
  likeGameReviewReplyApi,
  unlikeGameReviewApi,
  unlikeGameReviewReplyApi,
} from '@/service/game';

import './GameReviewItem.less';

const { Paragraph, Text } = Typography;
const { TextArea } = Input;

interface GameReviewItemProps {
  review: IGameReview;
  onRequireLogin: () => boolean;
}

const GameReviewItem: FC<GameReviewItemProps> = ({
  review,
  onRequireLogin,
}) => {
  const [liked, setLiked] = useState(Boolean(review.liked));
  const [likeCount, setLikeCount] = useState(review.likeCount ?? 0);
  const [replies, setReplies] = useState<IGameReviewReply[]>([]);
  const [replyCount, setReplyCount] = useState(review.replyCount ?? 0);
  const [repliesLoaded, setRepliesLoaded] = useState(false);
  const [repliesLoading, setRepliesLoading] = useState(false);
  const [replyOpen, setReplyOpen] = useState(false);
  const [replyContent, setReplyContent] = useState('');
  const [replySubmitting, setReplySubmitting] = useState(false);

  const toggleLike = async () => {
    if (!onRequireLogin()) return;
    try {
      if (liked) {
        await unlikeGameReviewApi(review.reviewId);
        setLiked(false);
        setLikeCount((value) => Math.max(0, value - 1));
      } else {
        await likeGameReviewApi(review.reviewId);
        setLiked(true);
        setLikeCount((value) => value + 1);
      }
    } catch (err) {
      message.error(formatApiError('更新点赞失败', err));
    }
  };

  const loadReplies = async () => {
    if (repliesLoaded) {
      setReplyOpen((value) => !value);
      return;
    }
    setReplyOpen(true);
    setRepliesLoading(true);
    try {
      const res = await fetchGameReviewRepliesApi(review.reviewId);
      setReplies(res.data ?? []);
      setReplyCount(res.total ?? replyCount);
      setRepliesLoaded(true);
    } catch (err) {
      message.error(formatApiError('加载回复失败', err));
    } finally {
      setRepliesLoading(false);
    }
  };

  const submitReply = async () => {
    if (!onRequireLogin()) return;
    const content = replyContent.trim();
    if (!content) {
      message.warning('请输入回复内容');
      return;
    }
    setReplySubmitting(true);
    try {
      await addGameReviewReplyApi(review.reviewId, content);
      setReplyContent('');
      setReplyCount((value) => value + 1);
      setRepliesLoading(true);
      const res = await fetchGameReviewRepliesApi(review.reviewId);
      setReplies(res.data ?? []);
      setReplyCount(res.total ?? replyCount + 1);
      setRepliesLoaded(true);
      setReplyOpen(true);
      message.success('回复已发布');
    } catch (err) {
      message.error(formatApiError('发布回复失败', err));
    } finally {
      setRepliesLoading(false);
      setReplySubmitting(false);
    }
  };

  const toggleReplyLike = async (reply: IGameReviewReply) => {
    if (!onRequireLogin()) return;
    try {
      if (reply.liked) {
        await unlikeGameReviewReplyApi(reply.replyId);
      } else {
        await likeGameReviewReplyApi(reply.replyId);
      }
      setReplies((items) =>
        items.map((item) =>
          item.replyId === reply.replyId
            ? {
                ...item,
                liked: !item.liked,
                likeCount: Math.max(
                  0,
                  (item.likeCount ?? 0) + (item.liked ? -1 : 1),
                ),
              }
            : item,
        ),
      );
    } catch (err) {
      message.error(formatApiError('更新回复点赞失败', err));
    }
  };

  return (
    <List.Item className="game-detail__review-item">
      <List.Item.Meta
        avatar={
          <ProfileUserLink
            accountId={review.accountId}
            nickname={review.username || `玩家${review.accountId}`}
            avatar={review.avatar}
            showNickname={false}
          />
        }
        title={
          <div className="game-detail__review-head">
            <ProfileUserLink
              accountId={review.accountId}
              nickname={review.username || `玩家${review.accountId}`}
              avatar={review.avatar}
              showAvatar={false}
            />
            <Rate count={10} disabled value={review.score} />
          </div>
        }
        description={
          <>
            {review.content ? (
              <Paragraph className="game-detail__review-content">
                {review.content}
              </Paragraph>
            ) : null}
            <Text type="secondary" className="game-detail__review-time">
              {formatDateTime(review.createTime)}
            </Text>
            <Space className="game-detail__review-actions" size="small">
              <Button
                type="text"
                size="small"
                icon={liked ? <LikeFilled /> : <LikeOutlined />}
                className={liked ? 'is-liked' : undefined}
                onClick={() => void toggleLike()}
              >
                {likeCount > 0 ? likeCount : '点赞'}
              </Button>
              <Button
                type="text"
                size="small"
                icon={<MessageOutlined />}
                onClick={() => void loadReplies()}
              >
                {replyCount > 0 ? `${replyCount} 条回复` : '回复'}
              </Button>
            </Space>
            {replyOpen ? (
              <div className="game-detail__review-replies">
                <List
                  size="small"
                  loading={repliesLoading}
                  dataSource={replies}
                  locale={{ emptyText: '暂无回复' }}
                  renderItem={(reply) => (
                    <List.Item
                      actions={[
                        <Button
                          key="like"
                          type="text"
                          size="small"
                          icon={reply.liked ? <LikeFilled /> : <LikeOutlined />}
                          className={reply.liked ? 'is-liked' : undefined}
                          onClick={() => void toggleReplyLike(reply)}
                        >
                          {reply.likeCount ?? 0}
                        </Button>,
                      ]}
                    >
                      <List.Item.Meta
                        avatar={
                          <ProfileUserLink
                            accountId={reply.accountId}
                            nickname={reply.username || '玩家'}
                            avatar={reply.avatar}
                            showNickname={false}
                          />
                        }
                        title={reply.username || '玩家'}
                        description={
                          <>
                            <span>{reply.content}</span>
                            <br />
                            <Text type="secondary">
                              {formatDateTime(reply.createTime)}
                            </Text>
                          </>
                        }
                      />
                    </List.Item>
                  )}
                />
                <Space.Compact className="game-detail__review-reply-editor">
                  <TextArea
                    autoSize={{ minRows: 1, maxRows: 4 }}
                    value={replyContent}
                    onChange={(event) => setReplyContent(event.target.value)}
                    placeholder="写下回复…"
                    maxLength={1000}
                    disabled={replySubmitting}
                  />
                  <Button
                    type="primary"
                    icon={<SendOutlined />}
                    loading={replySubmitting}
                    onClick={() => void submitReply()}
                  >
                    发送
                  </Button>
                </Space.Compact>
              </div>
            ) : null}
          </>
        }
      />
    </List.Item>
  );
};

export default GameReviewItem;
