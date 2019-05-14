package org.eclipse.kapua.job.remote;

import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.commons.util.ArgumentValidator;
import org.eclipse.kapua.job.engine.JobEngineService;
import org.eclipse.kapua.job.engine.JobStartOptions;
import org.eclipse.kapua.locator.KapuaLocator;
import org.eclipse.kapua.locator.KapuaProvider;
import org.eclipse.kapua.model.domain.Actions;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.service.authorization.AuthorizationService;
import org.eclipse.kapua.service.authorization.permission.PermissionFactory;
import org.eclipse.kapua.service.job.JobDomains;
import org.eclipse.kapua.service.scheduler.quartz.utils.QuartzTriggerUtils;
import org.quartz.JobDataMap;

@KapuaProvider
public class JobEngineServiceRemote implements JobEngineService {

    private static final KapuaLocator LOCATOR = KapuaLocator.getInstance();

    private static final AuthorizationService AUTHORIZATION_SERVICE = LOCATOR.getService(AuthorizationService.class);
    private static final PermissionFactory PERMISSION_FACTORY = LOCATOR.getFactory(PermissionFactory.class);

    @Override
    public void startJob(KapuaId scopeId, KapuaId jobId) throws KapuaException {

    }

    @Override
    public void startJob(KapuaId scopeId, KapuaId jobId, JobStartOptions jobStartOptions) throws KapuaException {
        //
        // Argument Validation
        ArgumentValidator.notNull(scopeId, "scopeId");
        ArgumentValidator.notNull(jobId, "jobId");
        ArgumentValidator.notNull(jobStartOptions, "jobStartOptions");

        //
        // Check Access
        AUTHORIZATION_SERVICE.checkPermission(PERMISSION_FACTORY.newPermission(JobDomains.JOB_DOMAIN, Actions.execute, scopeId));

        //
        // Seed the trigger
        try {
            // Build job data map
            JobDataMap jobDataMap = new JobDataMap();
            jobDataMap.put("scopeId", scopeId);
            jobDataMap.put("jobId", jobId);

            if (jobStartOptions != null) {
                jobDataMap.put("jobStartOptions", jobStartOptions);
            }

            // Create the trigger
            QuartzTriggerUtils.createQuartzTrigger(scopeId, jobId, jobDataMap);
        } catch (Exception e) {
            KapuaException.internalError(e, "Error!");
        }
    }

    @Override
    public boolean isRunning(KapuaId scopeId, KapuaId jobId) throws KapuaException {
        return false;
    }

    @Override
    public void stopJob(KapuaId scopeId, KapuaId jobId) throws KapuaException {

    }

    @Override
    public void stopJobExecution(KapuaId scopeId, KapuaId jobId, KapuaId jobExecutionId) throws KapuaException {

    }

    @Override
    public void resumeJobExecution(KapuaId scopeId, KapuaId jobId, KapuaId jobExecutionId) throws KapuaException {

    }

    @Override
    public void cleanJobData(KapuaId scopeId, KapuaId jobId) throws KapuaException {

    }
}
