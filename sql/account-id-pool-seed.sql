-- 账号号池初始种子：5 位 ID 10000-10999（共 1000 个）
-- 空库注册会因旧版 expandPool 递归 CTE 超限失败；执行本脚本可立刻恢复注册。
-- 应用侧已改为分批扩容，后续耗尽会自动再扩。

INSERT IGNORE INTO t_account_id_pool (account_id, digit_count, status, create_time, update_time)
SELECT 10000 + seq.n, 5, 0, NOW(), NOW()
FROM (
  WITH RECURSIVE seq AS (
    SELECT 0 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 999
  )
  SELECT n FROM seq
) seq;
