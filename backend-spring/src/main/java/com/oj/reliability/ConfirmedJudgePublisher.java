package com.oj.reliability;

public interface ConfirmedJudgePublisher {
    PublishResult publish(JudgeOutboxEvent event);

    record PublishResult(boolean confirmed, String failureCategory) {
        public static PublishResult ack() {
            return new PublishResult(true, "");
        }

        public static PublishResult failed(String category) {
            return new PublishResult(false, category);
        }
    }
}
