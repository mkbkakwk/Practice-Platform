package com.oj.office;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.oj.config.AppProperties;
import com.oj.entity.OfficeDocSubmissionEntity;
import com.oj.entity.OfficeExerciseEntity;
import com.oj.mapper.OfficeDocSubmissionMapper;
import com.oj.mapper.OfficeExerciseMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class OfficeFileReconciler {

    private static final Logger log = LoggerFactory.getLogger(OfficeFileReconciler.class);

    private final OfficeStorageService storage;
    private final OfficeExerciseMapper exercises;
    private final OfficeDocSubmissionMapper submissions;
    private final AppProperties.Office limits;

    public OfficeFileReconciler(OfficeStorageService storage,
                                OfficeExerciseMapper exercises,
                                OfficeDocSubmissionMapper submissions,
                                AppProperties properties) {
        this.storage = storage;
        this.exercises = exercises;
        this.submissions = submissions;
        this.limits = properties.getOffice();
    }

    @Scheduled(fixedDelayString = "${OFFICE_ORPHAN_SCAN_INTERVAL_MS:3600000}")
    public void removeOldUnreferencedManagedFiles() {
        Instant cutoff = Instant.now().minus(limits.getOrphanMinAge());
        for (String storageId : storage.managedFilesOlderThan(cutoff)) {
            if (isReferenced(storageId)) continue;
            if (storage.delete(storageId)) {
                log.info("Removed unreferenced Office document storageId={}", storageId);
            }
        }
    }

    private boolean isReferenced(String storageId) {
        String absolutePath;
        try {
            absolutePath = storage.require(storageId).toString();
        } catch (OfficeDocumentException exception) {
            return true;
        }
        long exerciseRefs = exercises.selectCount(
                new QueryWrapper<OfficeExerciseEntity>()
                        .in("teacher_doc_path", storageId, absolutePath));
        long submissionRefs = submissions.selectCount(
                new QueryWrapper<OfficeDocSubmissionEntity>()
                        .in("student_doc_path", storageId, absolutePath));
        return exerciseRefs > 0 || submissionRefs > 0;
    }
}
