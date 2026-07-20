package com.oj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oj.entity.UserEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface UserMapper extends BaseMapper<UserEntity> {
    @Update("UPDATE \"User\" SET solved_count = solved_count + 1 WHERE id = #{userId}")
    int incrementSolved(@Param("userId") int userId);
}
