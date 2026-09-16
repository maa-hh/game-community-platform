package com.game.community.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.cosmetic.CosmeticDef;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CosmeticDefMapper extends BaseMapper<CosmeticDef> {

    /** 批量查询装扮定义，避免逐条查定义表。 */
    @Select({
            "<script>",
            "SELECT * FROM t_cosmetic_def WHERE code IN",
            "<foreach collection='codes' item='code' open='(' separator=',' close=')'>",
            "#{code}",
            "</foreach>",
            "</script>"
    })
    /** 按装扮编码批量读取定义，避免背包查询逐条访问数据库。 */
    List<CosmeticDef> selectByCodes(@Param("codes") List<String> codes);
}
