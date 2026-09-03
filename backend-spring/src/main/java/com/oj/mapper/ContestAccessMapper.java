package com.oj.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;

@Mapper
public interface ContestAccessMapper {
    @Select("""
            SELECT COUNT(*)
            FROM \"ContestProblem\" cp
            JOIN \"Contest\" c ON c.id = cp.contest_id
            JOIN \"ContestParticipant\" participant
              ON participant.contest_id = c.id AND participant.user_id = #{userId}
            WHERE cp.problem_type = #{problemType}
              AND ((#{problemType} = 'ALGORITHM' AND cp.algorithm_problem_id = #{problemId})
                OR (#{problemType} = 'OFFICE_CHOICE' AND cp.office_question_id = #{problemId})
                OR (#{problemType} = 'OFFICE_DOCX' AND cp.office_exercise_id = #{problemId}))
              AND c.status = 'PUBLISHED'
              AND c.start_at <= #{now}
            """)
    long countAccessibleContestProblem(@Param("userId") int userId,
                                       @Param("problemType") String problemType,
                                       @Param("problemId") int problemId,
                                       @Param("now") Instant now);
}
