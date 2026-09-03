package com.oj.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("\"ContestParticipant\"")
public class ContestParticipantEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer contestId;
    private Integer userId;
    private Integer addedBy;
    private Instant joinedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getContestId() { return contestId; }
    public void setContestId(Integer contestId) { this.contestId = contestId; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public Integer getAddedBy() { return addedBy; }
    public void setAddedBy(Integer addedBy) { this.addedBy = addedBy; }
    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }
}
