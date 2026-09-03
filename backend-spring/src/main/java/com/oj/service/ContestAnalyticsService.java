package com.oj.service;

import com.oj.common.ApiException;
import com.oj.common.CurrentUser;
import com.oj.contest.ContestException;
import com.oj.contest.ContestLifecycle;
import com.oj.contest.ContestPhase;
import com.oj.contest.ContestProblemType;
import com.oj.contest.ContestScoringMode;
import com.oj.dto.ContestDtos;
import com.oj.entity.ContestEntity;
import com.oj.entity.ContestProblemEntity;
import com.oj.entity.OfficeExerciseEntity;
import com.oj.entity.OfficeQuestionEntity;
import com.oj.entity.ProblemEntity;
import com.oj.mapper.ContestMapper;
import com.oj.mapper.ContestProblemMapper;
import com.oj.mapper.OfficeExerciseMapper;
import com.oj.mapper.OfficeQuestionMapper;
import com.oj.mapper.ProblemMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manager-only, query-derived teaching analytics.  No aggregate or ranking truth is persisted:
 * raw student submissions are bulk loaded and rankings are delegated to the Stage 7 standings service.
 */
@Service
public class ContestAnalyticsService {
    private static final Set<String> TERMINAL = Set.of("AC", "WA", "TLE", "MLE", "OLE", "RE", "CE", "SE");
    private final ContestMapper contests;
    private final ContestProblemMapper problems;
    private final ContestStandingService standings;
    private final ProblemMapper algorithmProblems;
    private final OfficeQuestionMapper officeQuestions;
    private final OfficeExerciseMapper officeExercises;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ContestAnalyticsService(ContestMapper contests, ContestProblemMapper problems,
                                    ContestStandingService standings, ProblemMapper algorithmProblems,
                                    OfficeQuestionMapper officeQuestions, OfficeExerciseMapper officeExercises,
                                    JdbcTemplate jdbc, Clock clock) {
        this.contests = contests; this.problems = problems; this.standings = standings;
        this.algorithmProblems = algorithmProblems; this.officeQuestions = officeQuestions;
        this.officeExercises = officeExercises; this.jdbc = jdbc; this.clock = clock;
    }

    public ContestDtos.Analytics analytics(int contestId) {
        Context context = load(contestId);
        return new ContestDtos.Analytics(contestId, context.contest.getTitle(), context.mode.name(), context.phase.name(),
                clock.instant(), overview(context), problemAnalytics(context), timeline(context), distribution(context));
    }

    public ContestDtos.AnalyticsParticipants participants(int contestId, int page, int pageSize, String query) {
        Context context = load(contestId);
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<ContestDtos.AnalyticsParticipant> all = context.participants.values().stream()
                .filter(value -> needle.isEmpty() || value.username.toLowerCase(Locale.ROOT).contains(needle))
                .map(value -> participant(context, context.states.get(value.userId)))
                .sorted(Comparator.comparing((ContestDtos.AnalyticsParticipant value) -> value.rank() == null ? Integer.MAX_VALUE : value.rank())
                        .thenComparing(ContestDtos.AnalyticsParticipant::userId)).toList();
        int safePage = Math.max(1, page), safeSize = Math.min(50, Math.max(1, pageSize));
        int from = Math.min((safePage - 1) * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        return new ContestDtos.AnalyticsParticipants(safePage, safeSize, all.size(), all.subList(from, to));
    }

    private Context load(int contestId) {
        requireAuthenticated();
        ContestEntity contest = contests.selectById(contestId);
        if (contest == null) throw ContestException.notFound();
        requireManage(contest);
        List<ContestProblemEntity> problemList = problems.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ContestProblemEntity>()
                .eq("contest_id", contestId).orderByAsc("display_order").orderByAsc("id"));
        Map<Integer, Person> roster = new LinkedHashMap<>();
        jdbc.query("SELECT p.user_id, u.username FROM \"ContestParticipant\" p JOIN \"User\" u ON u.id=p.user_id WHERE p.contest_id=? ORDER BY p.user_id",
                (RowCallbackHandler) rs -> roster.put(rs.getInt(1), new Person(rs.getInt(1), rs.getString(2))), contestId);
        Context context = new Context(contest, ContestLifecycle.phase(contest, clock), mode(contest), problemList, roster,
                loadTitles(problemList));
        for (Person person : roster.values()) context.states.put(person.userId, new State(person));
        if (!problemList.isEmpty()) { loadAlgorithm(context); loadChoice(context); loadDocx(context); }
        if (context.phase == ContestPhase.RUNNING || context.phase == ContestPhase.ENDED) {
            for (ContestDtos.StandingEntry row : standings.standings(contestId).entries()) context.standings.put(row.userId(), row);
        }
        return context;
    }

    private void loadAlgorithm(Context c) {
        List<Long> ids = ids(c, ContestProblemType.ALGORITHM);
        if (ids.isEmpty()) return;
        String marks = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>(ids); args.add(Timestamp.from(c.contest.getStartAt())); args.add(Timestamp.from(c.contest.getEndAt()));
        jdbc.query("""
                SELECT s.id,s.user_id,s.contest_problem_id,s.created_at,s.verdict,
                       CASE WHEN s.verdict IN ('PENDING','JUDGING','JUDGE_FAILED') THEN h.verdict ELSE s.verdict END effective_verdict
                FROM "Submission" s LEFT JOIN LATERAL (
                   SELECT verdict FROM algorithm_judge_history WHERE submission_id=s.id AND judge_generation<=s.judge_generation
                     AND verdict <> 'JUDGE_FAILED' ORDER BY judge_generation DESC LIMIT 1) h ON s.verdict IN ('PENDING','JUDGING','JUDGE_FAILED')
                WHERE s.contest_problem_id IN (""" + marks + ") AND s.created_at >= (? AT TIME ZONE 'UTC') AND s.created_at < (? AT TIME ZONE 'UTC')",
                rs -> {
                    State state = c.states.get(rs.getInt("user_id")); if (state == null) return;
                    long problemId = rs.getLong("contest_problem_id");
                    Attempt attempt = state.attempt(problemId); attempt.count++;
                    Instant submittedAt = instant(rs.getTimestamp("created_at")); attempt.first = earliest(attempt.first, submittedAt); attempt.last = latest(attempt.last, submittedAt);
                    String effective = rs.getString("effective_verdict"); boolean accepted = false;
                    if (TERMINAL.contains(effective)) { attempt.valid++; if ("AC".equals(effective)) { attempt.accepted++; attempt.success = true; accepted = true; } }
                    attempt.events.add(new Event(submittedAt, ContestProblemType.ALGORITHM, accepted));
                    if ("JUDGE_FAILED".equals(rs.getString("verdict"))) attempt.infrastructure++;
                }, args.toArray());
    }

    private void loadChoice(Context c) {
        List<Long> ids = ids(c, ContestProblemType.OFFICE_CHOICE); if (ids.isEmpty()) return;
        String marks = ids.stream().map(id -> "?").collect(Collectors.joining(",")); List<Object> args = new ArrayList<>(ids);
        args.add(Timestamp.from(c.contest.getStartAt())); args.add(Timestamp.from(c.contest.getEndAt()));
        jdbc.query("SELECT user_id,contest_problem_id,created_at,correct FROM \"OfficeRecord\" WHERE contest_problem_id IN (" + marks + ") AND created_at >= (? AT TIME ZONE 'UTC') AND created_at < (? AT TIME ZONE 'UTC')",
                rs -> { State state=c.states.get(rs.getInt("user_id")); if(state==null)return; Attempt a=state.attempt(rs.getLong("contest_problem_id")); Instant submittedAt=instant(rs.getTimestamp("created_at")); boolean correct=rs.getBoolean("correct"); a.count++; a.valid++; a.first=earliest(a.first,submittedAt); a.last=latest(a.last,submittedAt); if(correct){a.accepted++;a.success=true;} a.events.add(new Event(submittedAt,ContestProblemType.OFFICE_CHOICE,correct)); }, args.toArray());
    }

    private void loadDocx(Context c) {
        List<Long> ids = ids(c, ContestProblemType.OFFICE_DOCX); if (ids.isEmpty()) return;
        String marks = ids.stream().map(id -> "?").collect(Collectors.joining(",")); List<Object> args = new ArrayList<>(ids);
        args.add(Timestamp.from(c.contest.getStartAt())); args.add(Timestamp.from(c.contest.getEndAt()));
        jdbc.query("SELECT user_id,contest_problem_id,created_at,status,score FROM \"OfficeDocSubmission\" WHERE contest_problem_id IN (" + marks + ") AND created_at >= (? AT TIME ZONE 'UTC') AND created_at < (? AT TIME ZONE 'UTC')",
                rs -> { State state=c.states.get(rs.getInt("user_id")); if(state==null)return; Attempt a=state.attempt(rs.getLong("contest_problem_id")); Instant submittedAt=instant(rs.getTimestamp("created_at")); a.count++; a.first=earliest(a.first,submittedAt); a.last=latest(a.last,submittedAt); Integer score=rs.getObject("score",Integer.class); boolean successful=false; if(score!=null && !"FAILED".equals(rs.getString("status"))){a.valid++;a.best=Math.max(a.best,score);successful=score==100;a.success|=successful;} if("NEEDS_REVIEW".equals(rs.getString("status")))a.needsReview++; a.events.add(new Event(submittedAt,ContestProblemType.OFFICE_DOCX,successful)); }, args.toArray());
    }

    private ContestDtos.Overview overview(Context c) {
        Collection<State> values=c.states.values(); int total=values.stream().mapToInt(State::count).sum(); int algorithm=sum(c,ContestProblemType.ALGORITHM,a->a.count), choice=sum(c,ContestProblemType.OFFICE_CHOICE,a->a.count), docx=sum(c,ContestProblemType.OFFICE_DOCX,a->a.count);
        int active=(int)values.stream().filter(value->value.count()>0).count(); Instant first=values.stream().flatMap(v->v.attempts.values().stream()).map(a->a.first).filter(Objects::nonNull).min(Instant::compareTo).orElse(null); Instant last=values.stream().flatMap(v->v.attempts.values().stream()).map(a->a.last).filter(Objects::nonNull).max(Instant::compareTo).orElse(null);
        if(c.mode==ContestScoringMode.SCORE){ List<Integer> scores=values.stream().map(v->standing(c,v).totalScore()).toList(); return new ContestDtos.Overview(values.size(),active,values.size()-active,total,algorithm,choice,docx,first,last,avgInt(scores),scores.stream().max(Integer::compareTo).orElse(0),scores.stream().min(Integer::compareTo).orElse(0),(int)scores.stream().filter(s->s==c.problems.size()*100).count(),null,null,null); }
        List<ContestDtos.StandingEntry> rows=values.stream().map(v->standing(c,v)).toList(); List<Integer> penalties=rows.stream().filter(v->v.solved()>0).map(ContestDtos.StandingEntry::penaltyMinutes).toList(); return new ContestDtos.Overview(values.size(),active,values.size()-active,total,algorithm,choice,docx,first,last,null,null,null,null,avgInt(rows.stream().map(ContestDtos.StandingEntry::solved).toList()),rows.stream().map(ContestDtos.StandingEntry::solved).max(Integer::compareTo).orElse(0),avgInt(penalties));
    }

    private List<ContestDtos.ProblemAnalytics> problemAnalytics(Context c) { List<ContestDtos.ProblemAnalytics> out=new ArrayList<>(); for(ContestProblemEntity p:c.problems){ List<Attempt> values=c.states.values().stream().map(s->s.attempts.get(p.getId())).filter(Objects::nonNull).toList(); int count=values.stream().mapToInt(a->a.count).sum(), unique=values.size(), success=(int)values.stream().filter(a->a.success).count(), infra=values.stream().mapToInt(a->a.infrastructure).sum(); double rate=ratio(success,c.states.size()); Integer valid=null,accepted=null,correct=null,scored=null,perfect=null,needs=null; Double acceptance=null,correctRate=null,avg=null,median=null,perfectRate=null; String type=p.getProblemType(); if(type.equals("ALGORITHM")){valid=values.stream().mapToInt(a->a.valid).sum();accepted=values.stream().mapToInt(a->a.accepted).sum();acceptance=ratio(accepted,valid);} else if(type.equals("OFFICE_CHOICE")){valid=count;correct=values.stream().mapToInt(a->a.accepted).sum();correctRate=ratio(correct,valid);} else { List<Integer> scores=values.stream().filter(a->a.valid>0).map(a->a.best).toList();scored=scores.size();avg=avgInt(scores);median=median(scores);perfect=(int)scores.stream().filter(s->s==100).count();perfectRate=ratio(perfect,c.states.size());needs=values.stream().mapToInt(a->a.needsReview).sum(); } out.add(new ContestDtos.ProblemAnalytics(p.getId(),p.getLabel(),p.getDisplayOrder(),c.titles.getOrDefault(p.getId(),p.getLabel()),type,count,unique,ratio(unique,c.states.size()),success,rate,infra,valid,accepted,acceptance,correct,valid,correctRate,scored,avg,median,perfect,perfectRate,needs)); } return out; }

    private ContestDtos.AnalyticsParticipant participant(Context c, State value){ ContestDtos.StandingEntry standing=standing(c,value); int submitted=(int)value.attempts.values().stream().filter(a->a.count>0).count(), successful=(int)value.attempts.values().stream().filter(a->a.success).count(); Instant last=value.attempts.values().stream().map(a->a.last).filter(Objects::nonNull).max(Instant::compareTo).orElse(null); return new ContestDtos.AnalyticsParticipant(value.person.userId,value.person.username,c.standings.containsKey(value.person.userId)?standing.rank():null,value.count(),submitted,successful,last,c.mode==ContestScoringMode.SCORE?standing.totalScore():null,c.mode==ContestScoringMode.ICPC?standing.solved():null,c.mode==ContestScoringMode.ICPC?standing.penaltyMinutes():null); }
    private List<ContestDtos.TimelineBucket> timeline(Context c){ Instant now=clock.instant(); if(now.isBefore(c.contest.getStartAt()))return List.of(); Instant end=now.isBefore(c.contest.getEndAt())?now:c.contest.getEndAt(); if(!end.isAfter(c.contest.getStartAt()))return List.of(); List<ContestDtos.TimelineBucket> out=new ArrayList<>(); long span=end.toEpochMilli()-c.contest.getStartAt().toEpochMilli(); for(int i=0;i<12;i++){Instant from=c.contest.getStartAt().plusMillis(span*i/12),to=i==11?end:c.contest.getStartAt().plusMillis(span*(i+1)/12);int[] n=new int[5]; for(State s:c.states.values())for(Attempt a:s.attempts.values())for(Event event:a.events){if(!event.submittedAt.isBefore(from)&&event.submittedAt.isBefore(to)){n[0]++;if(event.type==ContestProblemType.ALGORITHM)n[1]++;else if(event.type==ContestProblemType.OFFICE_CHOICE)n[2]++;else n[3]++;if(event.success)n[4]++;}} out.add(new ContestDtos.TimelineBucket(from,to,n[0],n[1],n[2],n[3],n[4]));} return out; }
    private List<ContestDtos.DistributionBucket> distribution(Context c){ if(c.mode==ContestScoringMode.ICPC){Map<Integer,Integer> counts=new TreeMap<>();for(State s:c.states.values())counts.merge(standing(c,s).solved(),1,Integer::sum);return counts.entrySet().stream().map(e->new ContestDtos.DistributionBucket(e.getKey()+" solved",e.getValue())).toList();} int max=Math.max(1,c.problems.size()*100);int[] bins=new int[7];for(State s:c.states.values()){double pct=standing(c,s).totalScore()*100d/max;int index=pct==0?0:pct>=100?6:Math.min(5,(int)Math.ceil(pct/20));bins[index]++;}String[] labels={"0%","1–20%","21–40%","41–60%","61–80%","81–99%","100%"};List<ContestDtos.DistributionBucket> out=new ArrayList<>();for(int i=0;i<7;i++)out.add(new ContestDtos.DistributionBucket(labels[i],bins[i]));return out; }
    private ContestDtos.StandingEntry standing(Context c,State s){return c.standings.getOrDefault(s.person.userId,new ContestDtos.StandingEntry(0,s.person.userId,s.person.username,0,0,0,List.of()));}
    private int sum(Context c,ContestProblemType type,java.util.function.ToIntFunction<Attempt> fn){return c.problems.stream().filter(p->p.getProblemType().equals(type.name())).mapToInt(p->c.states.values().stream().map(s->s.attempts.get(p.getId())).filter(Objects::nonNull).mapToInt(fn).sum()).sum();}
    private List<Long> ids(Context c,ContestProblemType type){return c.problems.stream().filter(p->p.getProblemType().equals(type.name())).map(ContestProblemEntity::getId).toList();}
    private String type(Context c,long id){return c.problems.stream().filter(p->Objects.equals(p.getId(),id)).findFirst().map(ContestProblemEntity::getProblemType).orElse("");}
    private Map<Long,String> loadTitles(List<ContestProblemEntity> items) {
        Map<Long,String> titles=new HashMap<>();
        List<Integer> algorithmIds=items.stream().map(ContestProblemEntity::getAlgorithmProblemId).filter(id -> id != null && id > 0).toList();
        List<Integer> choiceIds=items.stream().map(ContestProblemEntity::getOfficeQuestionId).filter(id -> id != null && id > 0).toList();
        List<Integer> docxIds=items.stream().map(ContestProblemEntity::getOfficeExerciseId).filter(id -> id != null && id > 0).toList();
        Map<Integer,String> algorithms=algorithmIds.isEmpty()?Map.of():algorithmProblems.selectBatchIds(algorithmIds).stream().collect(Collectors.toMap(ProblemEntity::getId,ProblemEntity::getTitle));
        Map<Integer,String> choices=choiceIds.isEmpty()?Map.of():officeQuestions.selectBatchIds(choiceIds).stream().collect(Collectors.toMap(OfficeQuestionEntity::getId,OfficeQuestionEntity::getContent));
        Map<Integer,String> docx=docxIds.isEmpty()?Map.of():officeExercises.selectBatchIds(docxIds).stream().collect(Collectors.toMap(OfficeExerciseEntity::getId,OfficeExerciseEntity::getTitle));
        for(ContestProblemEntity item:items){String title=switch(ContestProblemType.valueOf(item.getProblemType())){case ALGORITHM->algorithms.get(item.getAlgorithmProblemId());case OFFICE_CHOICE->choices.get(item.getOfficeQuestionId());case OFFICE_DOCX->docx.get(item.getOfficeExerciseId());}; if(title!=null)titles.put(item.getId(),title);}
        return titles;
    }
    private ContestScoringMode mode(ContestEntity contest){return ContestScoringMode.valueOf(contest.getScoringMode()==null?"SCORE":contest.getScoringMode());}
    private void requireManage(ContestEntity contest){if(!CurrentUser.isAdmin()&&!(CurrentUser.isTeacher()&&Objects.equals(CurrentUser.getId(),contest.getOwnerId())))throw ContestException.forbidden("CONTEST_FORBIDDEN","无权管理该比赛");}
    private void requireAuthenticated(){if(CurrentUser.getId()==null)throw ApiException.unauthorized("请先登录");}
    private static Instant instant(Timestamp timestamp){return timestamp.toInstant();} private static Instant earliest(Instant a,Instant b){return a==null||a.isAfter(b)?b:a;} private static Instant latest(Instant a,Instant b){return a==null||a.isBefore(b)?b:a;} private static double ratio(int n,int d){return d==0?0d:(double)n/d;} private static Double avgInt(List<Integer> values){return values.isEmpty()?null:values.stream().mapToInt(Integer::intValue).average().orElse(0);} private static Double median(List<Integer> values){if(values.isEmpty())return null;List<Integer> v=values.stream().sorted().toList();int n=v.size();return n%2==1?(double)v.get(n/2):(v.get(n/2-1)+v.get(n/2))/2d;}
    private record Person(int userId,String username){} private record Event(Instant submittedAt,ContestProblemType type,boolean success){} private static final class Attempt{int count,valid,accepted,infrastructure,best,needsReview;boolean success;Instant first,last;final List<Event> events=new ArrayList<>();} private static final class State{final Person person;final Map<Long,Attempt> attempts=new HashMap<>();State(Person person){this.person=person;}Attempt attempt(long id){return attempts.computeIfAbsent(id,k->new Attempt());}int count(){return attempts.values().stream().mapToInt(a->a.count).sum();}} private static final class Context{final ContestEntity contest;final ContestPhase phase;final ContestScoringMode mode;final List<ContestProblemEntity> problems;final Map<Integer,Person> participants;final Map<Long,String> titles;final Map<Integer,State> states=new LinkedHashMap<>();final Map<Integer,ContestDtos.StandingEntry> standings=new HashMap<>();Context(ContestEntity contest,ContestPhase phase,ContestScoringMode mode,List<ContestProblemEntity> problems,Map<Integer,Person> participants,Map<Long,String> titles){this.contest=contest;this.phase=phase;this.mode=mode;this.problems=problems;this.participants=participants;this.titles=titles;}}
}
