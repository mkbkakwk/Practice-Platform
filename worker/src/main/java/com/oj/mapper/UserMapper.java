package com.oj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oj.entity.UserEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface UserMapper extends BaseMapper<UserEntity> {
    @Update("""
            UPDATE "User" u
            SET solved_count = (
                SELECT COUNT(DISTINCT s.problem_id)
                FROM "Submission" s
                WHERE s.user_id = u.id AND s.verdict = 'AC'
            )
            WHERE u.id = #{userId}
            """)
    int recalculateSolved(@Param("userId") int userId);
}
