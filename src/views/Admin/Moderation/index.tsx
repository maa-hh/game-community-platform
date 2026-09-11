import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  Alert,
  App,
  Button,
  Descriptions,
  Drawer,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  claimModerationTaskApi,
  fetchModerationTaskDetailApi,
  fetchModerationTasksApi,
  handleModerationTaskApi,
  type IModerationTask,
  type IModerationTaskDetail,
  type ModerationHandleAction,
  type ModerationTaskStatus,
  type ModerationTaskType,
} from '@/service/moderation';
import { formatApiError } from '@/utils/apiError';
import { createRandomId } from '@/utils/randomId';

import './style.less';

const TASK_TYPE_LABEL: Record<ModerationTaskType, string> = {
  REPORT: '举报',
  ARTICLE_AUDIT: '帖子审核',
  PROFILE_AUDIT: '资料审核',
};

const STATUS_LABEL: Record<ModerationTaskStatus, string> = {
  0: '未处理',
  1: '处理中',
  2: '已完成',
};

const STATUS_COLOR: Record<ModerationTaskStatus, string> = {
  0: 'default',
  1: 'processing',
  2: 'success',
};

const TARGET_TYPE_LABEL: Record<number, string> = {
  1: '帖子',
  2: '评论',
  3: '回复',
  4: '用户',
  5: '弹幕',
  6: '站点问题反馈',
};

function actionOptions(
  taskType?: ModerationTaskType,
  availableReportActions?: ModerationHandleAction[],
  targetType?: number,
): { label: string; value: ModerationHandleAction }[] {
  if (taskType === 'ARTICLE_AUDIT') {
    return [
      { label: '审核通过并发布', value: 'AUDIT_APPROVE' },
      { label: '人工审核不通过', value: 'AUDIT_REJECT' },
    ];
  }
  if (taskType === 'PROFILE_AUDIT') {
    return [
      { label: '资料审核通过', value: 'PROFILE_APPROVE' },
      { label: '资料审核不通过', value: 'PROFILE_REJECT' },
    ];
  }
  if (targetType === 6) {
    return [{ label: '已处理并关闭', value: 'NO_VIOLATION' }];
  }
  const punitive: { label: string; value: ModerationHandleAction }[] = [
    { label: '下架帖子', value: 'OFFLINE_ARTICLE' },
    { label: '删除评论', value: 'HIDE_COMMENT' },
    { label: '删除回复', value: 'HIDE_REPLY' },
    { label: '隐藏弹幕', value: 'HIDE_DANMAKU' },
    { label: '封禁用户', value: 'BAN_USER' },
  ];
  const filtered = availableReportActions
    ? punitive.filter((item) => availableReportActions.includes(item.value))
    : punitive;
  return [{ label: '通过（不处理）', value: 'NO_VIOLATION' }, ...filtered];
}

function ModerationPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { message: appMessage } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [items, setItems] = useState<IModerationTask[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [statusFilter, setStatusFilter] = useState<
    ModerationTaskStatus | undefined
  >(0);
  const [typeFilter, setTypeFilter] = useState<
    ModerationTaskType | undefined
  >();
  const [detailOpen, setDetailOpen] = useState(false);
  const [detail, setDetail] = useState<IModerationTaskDetail | null>(null);
  const [handleOpen, setHandleOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const handleRequestIdRef = useRef<string | null>(null);
  const [form] = Form.useForm<{
    handleAction: ModerationHandleAction;
    handleRemark?: string;
  }>();

  const loadList = useCallback(async () => {
    setLoading(true);
    try {
      const res = await fetchModerationTasksApi({
        page,
        size: 20,
        status: statusFilter,
        taskType: typeFilter,
      });
      if (res.code !== 200) {
        throw new Error(res.message || '加载失败');
      }
      setItems(res.data || []);
      setTotal(Number(res.total ?? 0));
    } catch (error) {
      appMessage.error(formatApiError('加载审核列表失败', error));
    } finally {
      setLoading(false);
    }
  }, [appMessage, page, statusFilter, typeFilter]);

  useEffect(() => {
    void loadList();
  }, [loadList]);

  const openDetailByTaskKey = useCallback(
    async (taskKey: string) => {
      try {
        const res = await fetchModerationTaskDetailApi(taskKey);
        if (res.code !== 200) {
          throw new Error(res.message || '加载详情失败');
        }
        setDetail(res.data);
        setDetailOpen(true);
      } catch (error) {
        appMessage.error(formatApiError('加载详情失败', error));
      }
    },
    [appMessage],
  );

  useEffect(() => {
    const state =
      typeof location.state === 'object' && location.state !== null
        ? (location.state as { moderationTaskKey?: unknown })
        : null;
    if (typeof state?.moderationTaskKey !== 'string') return;

    void openDetailByTaskKey(state.moderationTaskKey);
    navigate(location.pathname, { replace: true, state: null });
  }, [location.pathname, location.state, navigate, openDetailByTaskKey]);

  const openDetail = async (record: IModerationTask) => {
    await openDetailByTaskKey(record.taskKey);
  };

  const prepareHandle = async (record: IModerationTask) => {
    try {
      let taskDetail = detail?.taskKey === record.taskKey ? detail : null;
      if (!taskDetail) {
        const res = await fetchModerationTaskDetailApi(record.taskKey);
        if (res.code !== 200) {
          throw new Error(res.message || '加载详情失败');
        }
        taskDetail = res.data;
        setDetail(taskDetail);
      }
      if (taskDetail.taskType !== 'REPORT' && taskDetail.reviewable === false) {
        appMessage.warning(
          taskDetail.reviewBlockReason || '当前工单不可处理，请刷新详情',
        );
        setDetail(taskDetail);
        setDetailOpen(true);
        return;
      }
      const claimRes = await claimModerationTaskApi(record.taskKey);
      if (claimRes.code !== 200) {
        throw new Error(claimRes.message || '认领失败');
      }
      const claim = claimRes.data;
      if (!claim?.claimToken) {
        throw new Error('认领结果无效，请刷新后重试');
      }
      const claimedDetail: IModerationTaskDetail = {
        ...taskDetail,
        status: 1,
        handlerAccountId: claim.handlerAccountId,
        claimToken: claim.claimToken,
        leaseExpireTime: claim.leaseExpireTime,
      };
      setDetail(claimedDetail);
      handleRequestIdRef.current = createRandomId('moderation');
      form.setFieldsValue({
        handleAction: actionOptions(
          claimedDetail.taskType,
          claimedDetail.availableReportActions,
          claimedDetail.targetType,
        )[0]?.value,
      });
      setHandleOpen(true);
      void loadList();
    } catch (error) {
      appMessage.error(formatApiError('认领失败', error));
    }
  };

  const submitHandle = async () => {
    if (!detail) return;
    try {
      const values = await form.validateFields();
      if (!detail.claimToken || !handleRequestIdRef.current) {
        throw new Error('认领已失效，请重新认领后处理');
      }
      setSubmitting(true);
      const res = await handleModerationTaskApi(detail.taskKey, {
        ...values,
        claimToken: detail.claimToken,
        requestId: handleRequestIdRef.current,
      });
      if (res.code !== 200) {
        throw new Error(res.message || '处理失败');
      }
      appMessage.success('处理完成');
      setHandleOpen(false);
      handleRequestIdRef.current = null;
      setDetailOpen(false);
      setDetail(null);
      await loadList();
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) {
        return;
      }
      appMessage.error(formatApiError('处理失败', error));
    } finally {
      setSubmitting(false);
    }
  };

  const targetLink = useMemo(() => {
    if (!detail) return null;
    if (
      detail.targetPublicId &&
      (detail.taskType === 'ARTICLE_AUDIT' ||
        detail.targetType === 1 ||
        detail.targetType === 2 ||
        detail.targetType === 3 ||
        detail.targetType === 5)
    ) {
      return (
        <Button
          type="link"
          onClick={() => {
            if (!detail.targetPublicId) return;
            setDetailOpen(false);
            navigate(`/post/${detail.targetPublicId}?moderationPreview=1`, {
              state: {
                returnTo: '/admin/moderation',
                moderationTaskKey: detail.taskKey,
              },
            });
          }}
        >
          查看帖子
        </Button>
      );
    }
    const targetAccountId = detail.targetAccountId || detail.subjectAccountId;
    if (
      targetAccountId &&
      (detail.taskType === 'PROFILE_AUDIT' || detail.targetType === 4)
    ) {
      return (
        <Button
          type="link"
          onClick={() => navigate(`/profile?accountId=${targetAccountId}`)}
        >
          查看用户主页
        </Button>
      );
    }
    return null;
  }, [detail, navigate]);

  const columns: ColumnsType<IModerationTask> = [
    {
      title: '类型',
      dataIndex: 'taskType',
      width: 110,
      render: (value: ModerationTaskType, record) =>
        record.targetType === 6 ? '问题反馈' : TASK_TYPE_LABEL[value] || value,
    },
    {
      title: '提交时间',
      dataIndex: 'createTime',
      width: 180,
    },
    {
      title: '摘要',
      dataIndex: 'summary',
      ellipsis: true,
      render: (value: string) => (
        <Typography.Paragraph
          ellipsis={{ rows: 3 }}
          className="moderation-page__summary"
        >
          {value || '—'}
        </Typography.Paragraph>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: ModerationTaskStatus) => (
        <Tag color={STATUS_COLOR[value]}>{STATUS_LABEL[value]}</Tag>
      ),
    },
    {
      title: '处理人',
      dataIndex: 'handlerName',
      width: 120,
      render: (value?: string) => value || '—',
    },
    {
      title: '操作',
      key: 'actions',
      width: 180,
      render: (_, record) => (
        <Space>
          <Button type="link" onClick={() => void openDetail(record)}>
            详情
          </Button>
          {record.status !== 2 ? (
            <Button type="link" onClick={() => void prepareHandle(record)}>
              处理
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <div className="moderation-page">
      <div className="moderation-page__toolbar">
        <Typography.Title level={3}>人工审核中心</Typography.Title>
        <Space wrap>
          <Select
            allowClear
            placeholder="状态"
            style={{ width: 140 }}
            value={statusFilter}
            options={[
              { label: '未处理', value: 0 },
              { label: '处理中', value: 1 },
              { label: '已完成', value: 2 },
            ]}
            onChange={(value) => {
              setPage(1);
              setStatusFilter(value);
            }}
          />
          <Select
            allowClear
            placeholder="类型"
            style={{ width: 140 }}
            value={typeFilter}
            options={[
              { label: '举报/问题反馈', value: 'REPORT' },
              { label: '帖子审核', value: 'ARTICLE_AUDIT' },
              { label: '资料审核', value: 'PROFILE_AUDIT' },
            ]}
            onChange={(value) => {
              setPage(1);
              setTypeFilter(value);
            }}
          />
          <Button onClick={() => void loadList()}>刷新</Button>
        </Space>
      </div>

      <Table
        rowKey="taskKey"
        loading={loading}
        columns={columns}
        dataSource={items}
        pagination={{
          current: page,
          pageSize: 20,
          total,
          onChange: setPage,
          showTotal: (count) => `共 ${count} 条`,
        }}
      />

      <Drawer
        title="审核详情"
        size="large"
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        extra={
          detail && detail.status !== 2 ? (
            <Button type="primary" onClick={() => void prepareHandle(detail)}>
              处理
            </Button>
          ) : null
        }
      >
        {detail ? (
          <>
            {detail.reviewBlockReason ? (
              <Alert
                type={
                  detail.reviewable === false && detail.taskType !== 'REPORT'
                    ? 'error'
                    : 'warning'
                }
                showIcon
                className="moderation-page__alert"
                title={detail.reviewBlockReason}
              />
            ) : null}
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="类型">
                {detail.targetType === 6
                  ? '问题反馈'
                  : TASK_TYPE_LABEL[detail.taskType]}
              </Descriptions.Item>
              {detail.targetType ? (
                <Descriptions.Item label="举报目标">
                  {TARGET_TYPE_LABEL[detail.targetType] || '其他'}
                </Descriptions.Item>
              ) : null}
              {detail.subjectUserName ? (
                <Descriptions.Item label="目标用户">
                  {detail.subjectUserName}
                </Descriptions.Item>
              ) : null}
              {detail.reporterName ? (
                <Descriptions.Item
                  label={detail.targetType === 6 ? '提交人' : '举报人'}
                >
                  {detail.reporterName}
                </Descriptions.Item>
              ) : null}
              <Descriptions.Item label="状态">
                <Tag color={STATUS_COLOR[detail.status]}>
                  {STATUS_LABEL[detail.status]}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="提交时间">
                {detail.createTime}
              </Descriptions.Item>
              {detail.currentTargetStatus ? (
                <Descriptions.Item label="目标当前状态">
                  {detail.currentTargetStatus}
                  {detail.targetContentChanged ? '（内容已变更）' : ''}
                </Descriptions.Item>
              ) : null}
              {detail.targetUpdatedAt ? (
                <Descriptions.Item label="工单快照时间">
                  {detail.targetUpdatedAt}
                </Descriptions.Item>
              ) : null}
              {detail.currentTargetUpdatedAt ? (
                <Descriptions.Item label="目标当前更新时间">
                  {detail.currentTargetUpdatedAt}
                </Descriptions.Item>
              ) : null}
              <Descriptions.Item
                label={detail.targetType === 6 ? '反馈内容' : '举报原因/说明'}
              >
                {detail.reason || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="摘要">
                <pre className="moderation-page__content">
                  {detail.summary || '—'}
                </pre>
              </Descriptions.Item>
              {detail.targetTitle ? (
                <Descriptions.Item label="标题">
                  {detail.targetTitle}
                </Descriptions.Item>
              ) : null}
              {detail.extraPayload ? (
                <Descriptions.Item label="扩展信息">
                  {detail.extraPayload}
                </Descriptions.Item>
              ) : null}
            </Descriptions>
            {targetLink}
            {detail.targetContent ? (
              <div className="moderation-page__detail-block">
                <Typography.Title level={5}>原文</Typography.Title>
                <pre className="moderation-page__content">
                  {detail.targetContent}
                </pre>
              </div>
            ) : null}
          </>
        ) : null}
      </Drawer>

      <Modal
        title="处理审核工单"
        open={handleOpen}
        confirmLoading={submitting}
        onCancel={() => {
          handleRequestIdRef.current = null;
          setHandleOpen(false);
        }}
        onOk={() => void submitHandle()}
        okText="提交处理"
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="handleAction"
            label="处理结果"
            rules={[{ required: true, message: '请选择处理结果' }]}
          >
            <Select
              options={actionOptions(
                detail?.taskType,
                detail?.availableReportActions,
                detail?.targetType,
              )}
            />
          </Form.Item>
          <Form.Item name="handleRemark" label="处理说明">
            <Input.TextArea
              rows={4}
              maxLength={255}
              showCount
              placeholder="可选"
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

export default ModerationPage;
