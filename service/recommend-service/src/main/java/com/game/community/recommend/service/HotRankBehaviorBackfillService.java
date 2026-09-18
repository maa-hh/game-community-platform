package com.game.community.recommend.service;

public interface HotRankBehaviorBackfillService {

    /**
     * 从 social / content 历史数据回填行为事件表。
     *
     * @param force 为 true 时先清空事件表再回填
     * @return 写入条数
     */
    long backfillFromSocial(boolean force);

    /** 只重建弹幕来源事件，保留社交行为事件，供显式运维任务调用。 */
    long refreshDanmakuFromSocial();
}
