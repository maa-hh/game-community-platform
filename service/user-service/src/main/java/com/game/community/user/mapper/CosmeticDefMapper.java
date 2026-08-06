package com.game.community.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.cosmetic.CosmeticDef;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CosmeticDefMapper extends BaseMapper<CosmeticDef> {

    @Select("SELECT * FROM t_cosmetic_def WHERE code = #{code} LIMIT 1")
    CosmeticDef selectByCode(String code);

    @Select({
            "<script>",
            "SELECT * FROM t_cosmetic_def WHERE code IN",
            "<foreach collection='codes' item='code' open='(' separator=',' close=')'>",
            "#{code}",
            "</foreach>",
            "</script>"
    })
    List<CosmeticDef> selectByCodes(@Param("codes") List<String> codes);
}
