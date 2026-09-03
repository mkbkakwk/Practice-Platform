package com.oj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oj.entity.ContestEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ContestMapper extends BaseMapper<ContestEntity> {
    @Select("SELECT * FROM \"Contest\" WHERE id = #{id} FOR UPDATE")
    ContestEntity selectByIdForUpdate(@Param("id") int id);
}
