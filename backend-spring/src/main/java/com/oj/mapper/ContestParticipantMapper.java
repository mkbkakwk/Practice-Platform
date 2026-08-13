package com.oj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oj.entity.ContestParticipantEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ContestParticipantMapper extends BaseMapper<ContestParticipantEntity> {
    @Insert("""
            INSERT INTO \"ContestParticipant\" (contest_id, user_id, added_by)
            VALUES (#{contestId}, #{userId}, #{addedBy})
            ON CONFLICT (contest_id, user_id) DO NOTHING
            """)
    int insertIfAbsent(@Param("contestId") int contestId,
                       @Param("userId") int userId,
                       @Param("addedBy") Integer addedBy);
}
