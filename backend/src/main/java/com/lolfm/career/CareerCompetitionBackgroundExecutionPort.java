package com.lolfm.career;

/** Bounded trigger for a persisted Career competition Auto job. */
public interface CareerCompetitionBackgroundExecutionPort {
    boolean submit(String jobId);
}
