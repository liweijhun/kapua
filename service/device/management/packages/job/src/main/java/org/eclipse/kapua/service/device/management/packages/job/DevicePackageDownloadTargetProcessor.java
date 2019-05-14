/*******************************************************************************
 * Copyright (c) 2017, 2019 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eurotech - initial API and implementation
 *******************************************************************************/
package org.eclipse.kapua.service.device.management.packages.job;

import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.commons.model.id.IdGenerator;
import org.eclipse.kapua.commons.model.id.KapuaEid;
import org.eclipse.kapua.commons.security.KapuaSecurityUtils;
import org.eclipse.kapua.job.engine.commons.operation.AbstractTargetProcessor;
import org.eclipse.kapua.job.engine.commons.wrappers.JobTargetWrapper;
import org.eclipse.kapua.locator.KapuaLocator;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.service.device.management.packages.DevicePackageFactory;
import org.eclipse.kapua.service.device.management.packages.DevicePackageManagementService;
import org.eclipse.kapua.service.device.management.packages.job.definition.DevicePackageDownloadPropertyKeys;
import org.eclipse.kapua.service.device.management.packages.model.download.DevicePackageDownloadOptions;
import org.eclipse.kapua.service.device.management.packages.model.download.DevicePackageDownloadRequest;
import org.eclipse.kapua.service.job.operation.TargetOperation;
import org.eclipse.kapua.service.job.targets.JobTarget;

import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import javax.inject.Inject;

/**
 * {@link TargetOperation} for {@link DevicePackageManagementService#downloadExec(KapuaId, KapuaId, DevicePackageDownloadRequest, Long)}.
 *
 * @since 1.0.0
 */
public class DevicePackageDownloadTargetProcessor extends AbstractTargetProcessor implements TargetOperation {

    private static final KapuaLocator LOCATOR = KapuaLocator.getInstance();

    private static final DevicePackageManagementService PACKAGES_MANAGEMENT_SERVICE = LOCATOR.getService(DevicePackageManagementService.class);
    private static final DevicePackageFactory DEVICE_PACKAGE_FACTORY = LOCATOR.getFactory(DevicePackageFactory.class);

    @Inject
    JobContext jobContext;

    @Inject
    StepContext stepContext;

    @Override
    protected void initProcessing(JobTargetWrapper wrappedJobTarget) {
        setContext(jobContext, stepContext);
    }

    @Override
    public void processTarget(JobTarget jobTarget) throws KapuaException {

        KapuaId operationId = new KapuaEid(IdGenerator.generate());

        //
        // Extract parameters from context
        DevicePackageDownloadRequest packageDownloadRequest = stepContextWrapper.getStepProperty(DevicePackageDownloadPropertyKeys.PACKAGE_DOWNLOAD_REQUEST, DevicePackageDownloadRequest.class);
        Long timeout = stepContextWrapper.getStepProperty(DevicePackageDownloadPropertyKeys.TIMEOUT, Long.class);

        //
        // Send the request
        DevicePackageDownloadOptions packageDownloadOptions = DEVICE_PACKAGE_FACTORY.newDevicePackageDownloadOptions();
        packageDownloadOptions.setTimeout(timeout);
        packageDownloadOptions.setForcedOperationId(operationId);

        KapuaSecurityUtils.doPrivileged(() -> PACKAGES_MANAGEMENT_SERVICE.downloadExec(jobTarget.getScopeId(), jobTarget.getJobTargetId(), packageDownloadRequest, packageDownloadOptions));
    }
}
