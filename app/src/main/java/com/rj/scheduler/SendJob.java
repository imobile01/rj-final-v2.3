package com.rj.scheduler;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;

/**
 * Background job that does the actual emailing. Android only starts it when the phone
 * has internet, gives it up to several minutes, and retries every 15 minutes if needed.
 */
public class SendJob extends JobService {

    private static final int JOB_ID = 4711;
    private static final long RETRY_MS = 15 * 60 * 1000L;

    private static int jobId(String slot) { return JOB_ID + Sched.slotNum(slot) - 1; }

    static void enqueue(Context c, String slot) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        PersistableBundle extras = new PersistableBundle();
        extras.putString("slot", slot);
        JobInfo job = new JobInfo.Builder(jobId(slot), new ComponentName(c, SendJob.class))
                .setExtras(extras)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(RETRY_MS, JobInfo.BACKOFF_POLICY_LINEAR)
                .setPersisted(true)                     // survives a phone restart
                .build();
        js.schedule(job);
    }

    static void cancel(Context c, String slot) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js != null) js.cancel(jobId(slot));
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        PersistableBundle ex = params.getExtras();
        final String slot = ex != null ? ex.getString("slot", "") : "";
        new Thread(() -> {
            boolean retry = false;
            try {
                retry = Sched.run(getApplicationContext(), slot) == Sched.Result.RETRY;
            } catch (Throwable t) {
                retry = true;
            }
            jobFinished(params, retry);
        }, "rj-send").start();
        return true;                                    // work continues on the thread
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;                                    // interrupted: try again later
    }
}
