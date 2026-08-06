-- 内容分区初始数据（发帖必选 categoryId）
-- 编码：文件须 UTF-8（无 BOM）；执行时 sync-mysql.sh 已指定 --default-character-set=utf8mb4
-- 可重复执行：先按 sort 清理种子行，再插入（覆盖历史上客户端字符集错误产生的乱码）

SET NAMES utf8mb4;

DELETE FROM t_category
WHERE deleted = 0
  AND sort IN (50, 60, 70, 80, 90, 100);

INSERT INTO t_category (name, description, status, sort, deleted, create_time, update_time)
VALUES
    ('综合讨论', '社区日常交流、提问与杂谈', 1, 100, 0, NOW(), NOW()),
    ('攻略心得', '副本、职业、玩法攻略与经验分享', 1, 90, 0, NOW(), NOW()),
    ('组队交友', '找队友、公会招募、一起开黑', 1, 80, 0, NOW(), NOW()),
    ('装备交易', '装备、道具交流与交易信息', 1, 70, 0, NOW(), NOW()),
    ('游戏资讯', '版本更新、活动资讯与官方动态', 1, 60, 0, NOW(), NOW()),
    ('创作分享', '截图、视频、同人作品与创意展示', 1, 50, 0, NOW(), NOW());
