package com.game.community.steam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.game.GameChartSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GameChartSnapshotMapper extends BaseMapper<GameChartSnapshot> {

    /** 批量写入榜单快照，减少单条 INSERT 的数据库往返。 */
    int insertBatch(@Param("rows") List<GameChartSnapshot> rows);
}
