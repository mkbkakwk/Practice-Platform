package com.oj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oj.entity.UserEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface UserMapper extends BaseMapper<UserEntity> {

    @Select("""
            SELECT *
            FROM "User"
            WHERE id = #{id}
            FOR UPDATE
            """)
    UserEntity selectByIdForUpdate(@Param("id") int id);

    @Update("""
            UPDATE "User"
            SET role = #{role},
                token_version = token_version + 1
            WHERE id = #{id}
            """)
    int updateRoleAndInvalidateTokens(@Param("id") int id, @Param("role") String role);

    @Update("""
            UPDATE "User"
            SET password = #{password},
                token_version = token_version + 1
            WHERE id = #{id}
            """)
    int updatePasswordAndInvalidateTokens(@Param("id") int id, @Param("password") String password);

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
