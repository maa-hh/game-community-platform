package com.game.community.steam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.game.GameCatalog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GameCatalogMapper extends BaseMapper<GameCatalog> {

    /** 批量插入或更新榜单基础目录，避免逐条执行 INSERT/UPDATE。 */
    int upsertBasicCatalogBatch(@Param("rows") List<GameCatalog> rows);
}
