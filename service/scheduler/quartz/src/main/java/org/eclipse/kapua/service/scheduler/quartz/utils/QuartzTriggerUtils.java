/*******************************************************************************
 * Copyright (c) 2016, 2019 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eurotech - initial API and implementation
 *******************************************************************************/
package org.eclipse.kapua.service.scheduler.quartz.utils;

import org.eclipse.kapua.KapuaErrorCodes;
import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.commons.model.id.KapuaEid;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.service.job.Job;
import org.eclipse.kapua.service.scheduler.quartz.job.KapuaJobLauncher;
import org.eclipse.kapua.service.scheduler.trigger.Trigger;
import org.eclipse.kapua.service.scheduler.trigger.TriggerProperty;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;

import java.util.TimeZone;

/**
 * Utility class to crate Quartz {@link org.quartz.Trigger}s.
 *
 * @since 1.1.0
 */
public class QuartzTriggerUtils {

    private QuartzTriggerUtils() {
    }

    /**
     * Creates a Quartz {@link org.quartz.Trigger} that starts now.
     *
     * @param scopeId        The {@link Job#getScopeId()} to start.
     * @param jobId          The {@link Job#getId()} to start
     * @param uniqueId       A unique {@link KapuaId} to associate with the Quartz {@link org.quartz.Trigger}.
     * @param triggerDataMap The {@link JobDataMap} with properties for the Quartz {@link org.quartz.Trigger}
     * @since 1.1.0
     */
    public static void createQuartzTrigger(KapuaId scopeId, KapuaId jobId, KapuaId uniqueId, JobDataMap triggerDataMap) {
        JobKey jobkey = JobKey.jobKey(KapuaJobLauncher.class.getName(), "USER");

        SchedulerFactory sf = new StdSchedulerFactory();
        Scheduler scheduler;
        try {
            scheduler = sf.getScheduler();
        } catch (SchedulerException se) {
            se.printStackTrace();
            throw new RuntimeException(se);
        }

        JobDetail kapuaJobLauncherJobDetail;
        try {
            kapuaJobLauncherJobDetail = scheduler.getJobDetail(jobkey);

            if (kapuaJobLauncherJobDetail == null) {
                kapuaJobLauncherJobDetail = JobBuilder.newJob(KapuaJobLauncher.class)
                        .withIdentity(jobkey)
                        .storeDurably()
                        .build();

                scheduler.addJob(kapuaJobLauncherJobDetail, false);
            }
        } catch (SchedulerException se) {
            se.printStackTrace();
            throw new RuntimeException(se);
        }

        // Quartz Trigger data map definition
        TriggerKey triggerKey = TriggerKey.triggerKey(jobId.toCompactId().concat("-").concat(uniqueId.toCompactId()), scopeId.toCompactId());

        // Quartz Trigger definition
        TriggerBuilder<org.quartz.Trigger> triggerBuilder = TriggerBuilder.newTrigger()
                .forJob(kapuaJobLauncherJobDetail)
                .withIdentity(triggerKey)
                .usingJobData(triggerDataMap)
                .startNow();

        org.quartz.Trigger quarztTrigger = triggerBuilder.build();
        try {
            scheduler.scheduleJob(quarztTrigger);
        } catch (SchedulerException se) {
            se.printStackTrace();
            throw new RuntimeException(se);
        }
    }

    /**
     * Creates a Quartz {@link org.quartz.Trigger} from the Kapua {@link Trigger}.
     *
     * @param trigger The {@link Trigger} from which to extract data.
     * @throws KapuaException If something goes bad
     * @since 1.1.0
     */
    public static void createQuartzTrigger(Trigger trigger) throws KapuaException {
        JobKey jobkey = JobKey.jobKey(KapuaJobLauncher.class.getName(), "USER");

        SchedulerFactory sf = new StdSchedulerFactory();
        Scheduler scheduler;
        try {
            scheduler = sf.getScheduler();
        } catch (SchedulerException se) {
            se.printStackTrace();
            throw new RuntimeException(se);
        }

        JobDetail kapuaJobLauncherJobDetail;
        try {
            kapuaJobLauncherJobDetail = scheduler.getJobDetail(jobkey);

            if (kapuaJobLauncherJobDetail == null) {
                kapuaJobLauncherJobDetail = JobBuilder.newJob(KapuaJobLauncher.class)
                        .withIdentity(jobkey)
                        .storeDurably()
                        .build();

                scheduler.addJob(kapuaJobLauncherJobDetail, false);
            }
        } catch (SchedulerException se) {
            se.printStackTrace();
            throw new RuntimeException(se);
        }

        // Quartz Trigger data map definition
        TriggerKey triggerKey = TriggerKey.triggerKey(trigger.getId().toCompactId(), trigger.getScopeId().toCompactId());

        JobDataMap triggerDataMap = new JobDataMap();
        for (TriggerProperty tp : trigger.getTriggerProperties()) {
            triggerDataMap.put(tp.getName(), KapuaEid.parseCompactId(tp.getPropertyValue()));
        }

        // Quartz Trigger definition
        TriggerBuilder<org.quartz.Trigger> triggerBuilder = TriggerBuilder.newTrigger()
                .forJob(kapuaJobLauncherJobDetail)
                .withIdentity(triggerKey)
                .usingJobData(triggerDataMap)
                .startAt(trigger.getStartsOn())
                .endAt(trigger.getEndsOn());

        if (trigger.getRetryInterval() != null) {
            triggerBuilder.withSchedule(SimpleScheduleBuilder.repeatSecondlyForever(trigger.getRetryInterval().intValue()));
        } else {
            triggerBuilder.withSchedule(CronScheduleBuilder.cronSchedule(trigger.getCronScheduling()).inTimeZone(TimeZone.getTimeZone("UTC")));
        }

        org.quartz.Trigger quarztTrigger = triggerBuilder.build();
        try {
            scheduler.scheduleJob(quarztTrigger);
        } catch (SchedulerException se) {
            se.printStackTrace();
            throw new KapuaException(KapuaErrorCodes.TRIGGER_NEVER_FIRE);
        }
    }
}
