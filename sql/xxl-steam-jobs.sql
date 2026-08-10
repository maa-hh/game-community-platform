USE game_community;
SET NAMES utf8mb4;

-- Steam 服务的两个定时任务由 XXL-JOB Admin 管理；同一 handler 只保留一条配置。
INSERT INTO xxl_job_group (app_name, title, address_type, address_list, update_time)
SELECT 'steam-service', 'Steam服务', 0, NULL, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM xxl_job_group WHERE app_name = 'steam-service'
);

SET @steam_job_group_id := (
    SELECT id FROM xxl_job_group WHERE app_name = 'steam-service' LIMIT 1
);

INSERT INTO xxl_job_info (
    job_group, job_desc, add_time, update_time, author, alarm_email,
    schedule_type, schedule_conf, misfire_strategy, executor_route_strategy,
    executor_handler, executor_param, executor_block_strategy, executor_timeout,
    executor_fail_retry_count, glue_type, glue_source, glue_remark, glue_updatetime,
    child_jobid, trigger_status, trigger_last_time, trigger_next_time
)
SELECT @steam_job_group_id, 'Steam游戏榜单每日同步', NOW(), NOW(), 'codex', '',
       'CRON', '0 0 5 * * ?', 'DO_NOTHING', 'ROUND',
       'steamGameChartDailySyncJob', '', 'SERIAL_EXECUTION', 0,
       1, 'BEAN', NULL, 'XXL-JOB Bean', NOW(),
       '', 1, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM xxl_job_info
    WHERE job_group = @steam_job_group_id
      AND executor_handler = 'steamGameChartDailySyncJob'
);

INSERT INTO xxl_job_info (
    job_group, job_desc, add_time, update_time, author, alarm_email,
    schedule_type, schedule_conf, misfire_strategy, executor_route_strategy,
    executor_handler, executor_param, executor_block_strategy, executor_timeout,
    executor_fail_retry_count, glue_type, glue_source, glue_remark, glue_updatetime,
    child_jobid, trigger_status, trigger_last_time, trigger_next_time
)
SELECT @steam_job_group_id, 'Steam游戏价格评价每日同步', NOW(), NOW(), 'codex', '',
       'CRON', '0 0 3 * * ?', 'DO_NOTHING', 'ROUND',
       'steamCatalogMetricsPriceDailySyncJob', '', 'SERIAL_EXECUTION', 0,
       1, 'BEAN', NULL, 'XXL-JOB Bean', NOW(),
       '', 1, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM xxl_job_info
    WHERE job_group = @steam_job_group_id
      AND executor_handler = 'steamCatalogMetricsPriceDailySyncJob'
);
