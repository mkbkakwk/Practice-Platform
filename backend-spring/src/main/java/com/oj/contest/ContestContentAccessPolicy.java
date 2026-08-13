package com.oj.contest;

import com.oj.common.CurrentUser;
import com.oj.mapper.ContestAccessMapper;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class ContestContentAccessPolicy {
    private final ContestAccessMapper mapper;
    private final Clock clock;

    public ContestContentAccessPolicy(ContestAccessMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    public boolean canReadContestOnly(ContestProblemType type, int problemId) {
        Integer userId = CurrentUser.getId();
        return userId != null && mapper.countAccessibleContestProblem(
                userId, type.name(), problemId, clock.instant()) > 0;
    }
}
