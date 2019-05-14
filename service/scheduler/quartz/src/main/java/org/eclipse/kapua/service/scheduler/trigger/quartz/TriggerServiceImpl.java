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
package org.eclipse.kapua.service.scheduler.trigger.quartz;

import org.eclipse.kapua.KapuaDuplicateNameException;
import org.eclipse.kapua.KapuaEndBeforeStartTimeException;
import org.eclipse.kapua.KapuaEntityNotFoundException;
import org.eclipse.kapua.KapuaErrorCodes;
import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.commons.configuration.AbstractKapuaConfigurableResourceLimitedService;
import org.eclipse.kapua.commons.security.KapuaSecurityUtils;
import org.eclipse.kapua.commons.util.ArgumentValidator;
import org.eclipse.kapua.locator.KapuaLocator;
import org.eclipse.kapua.locator.KapuaProvider;
import org.eclipse.kapua.model.domain.Actions;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.model.query.KapuaQuery;
import org.eclipse.kapua.model.query.predicate.AttributePredicate.Operator;
import org.eclipse.kapua.service.authorization.AuthorizationService;
import org.eclipse.kapua.service.authorization.permission.PermissionFactory;
import org.eclipse.kapua.service.scheduler.SchedulerDomains;
import org.eclipse.kapua.service.scheduler.quartz.SchedulerEntityManagerFactory;
import org.eclipse.kapua.service.scheduler.quartz.utils.QuartzTriggerUtils;
import org.eclipse.kapua.service.scheduler.trigger.Trigger;
import org.eclipse.kapua.service.scheduler.trigger.TriggerAttributes;
import org.eclipse.kapua.service.scheduler.trigger.TriggerCreator;
import org.eclipse.kapua.service.scheduler.trigger.TriggerFactory;
import org.eclipse.kapua.service.scheduler.trigger.TriggerListResult;
import org.eclipse.kapua.service.scheduler.trigger.TriggerQuery;
import org.eclipse.kapua.service.scheduler.trigger.TriggerService;
import org.eclipse.kapua.service.scheduler.trigger.definition.TriggerDefinition;
import org.eclipse.kapua.service.scheduler.trigger.definition.TriggerDefinitionAttributes;
import org.eclipse.kapua.service.scheduler.trigger.definition.TriggerDefinitionFactory;
import org.eclipse.kapua.service.scheduler.trigger.definition.TriggerDefinitionListResult;
import org.eclipse.kapua.service.scheduler.trigger.definition.TriggerDefinitionQuery;
import org.eclipse.kapua.service.scheduler.trigger.definition.TriggerDefinitionService;
import org.eclipse.kapua.service.scheduler.trigger.definition.TriggerProperty;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Date;

/**
 * {@link TriggerService} implementation.
 *
 * @since 1.0.0
 */
@KapuaProvider
public class TriggerServiceImpl extends AbstractKapuaConfigurableResourceLimitedService<Trigger, TriggerCreator, TriggerService, TriggerListResult, TriggerQuery, TriggerFactory> implements TriggerService {

    private static final Logger LOG = LoggerFactory.getLogger(TriggerServiceImpl.class);

    private static final KapuaLocator LOCATOR = KapuaLocator.getInstance();

    private static final AuthorizationService AUTHORIZATION_SERVICE = LOCATOR.getService(AuthorizationService.class);
    private static final PermissionFactory PERMISSION_FACTORY = LOCATOR.getFactory(PermissionFactory.class);

    private static final TriggerDefinitionService TRIGGER_DEFINITION_SERVICE = LOCATOR.getService(TriggerDefinitionService.class);
    private static final TriggerDefinitionFactory TRIGGER_DEFINITION_FACTORY = LOCATOR.getFactory(TriggerDefinitionFactory.class);

    private static final TriggerDefinition INTERVAL_JOB__TRIGGER;
    private static final TriggerDefinition CRON_JOB__TRIGGER;

    static {
        TriggerDefinition intervalJobTrigger = null;
        try {
            TriggerDefinitionQuery query = TRIGGER_DEFINITION_FACTORY.newQuery(null);

            query.setPredicate(query.attributePredicate(TriggerDefinitionAttributes.NAME, "Interval Job"));

            TriggerDefinitionListResult triggerDefinitions = KapuaSecurityUtils.doPrivileged(() -> TRIGGER_DEFINITION_SERVICE.query(query));

            if (triggerDefinitions.isEmpty()) {
                throw KapuaException.internalError("Error while searching 'Interval Job'");
            }

            intervalJobTrigger = triggerDefinitions.getFirstItem();
        } catch (Exception e) {
            LOG.error("Error while initializing class", e);
        }

        TriggerDefinition cronJobTrigger = null;
        try {
            TriggerDefinitionQuery query = TRIGGER_DEFINITION_FACTORY.newQuery(null);

            query.setPredicate(query.attributePredicate(TriggerDefinitionAttributes.NAME, "Cron Job"));

            TriggerDefinitionListResult triggerDefinitions = KapuaSecurityUtils.doPrivileged(() -> TRIGGER_DEFINITION_SERVICE.query(query));

            if (triggerDefinitions.isEmpty()) {
                throw KapuaException.internalError("Error while searching 'Cron Job'");
            }

            cronJobTrigger = triggerDefinitions.getFirstItem();
        } catch (Exception e) {
            LOG.error("Error while initializing class", e);
        }

        INTERVAL_JOB__TRIGGER = intervalJobTrigger;
        CRON_JOB__TRIGGER = cronJobTrigger;
    }


    /**
     * Constructor.
     *
     * @since 1.0.0
     */
    public TriggerServiceImpl() {
        super(TriggerService.class.getName(), SchedulerDomains.SCHEDULER_DOMAIN, SchedulerEntityManagerFactory.getInstance(), TriggerService.class, TriggerFactory.class);
    }

    @Override
    public Trigger create(TriggerCreator triggerCreator) throws KapuaException {
        //
        // Argument validation
        ArgumentValidator.notNull(triggerCreator, "triggerCreator");
        ArgumentValidator.notNull(triggerCreator.getScopeId(), "triggerCreator.scopeId");
        ArgumentValidator.notEmptyOrNull(triggerCreator.getName(), "triggerCreator.name");
        ArgumentValidator.notNull(triggerCreator.getStartsOn(), "triggerCreator.startsOn");

        //
        // Check Access
        AUTHORIZATION_SERVICE.checkPermission(PERMISSION_FACTORY.newPermission(SchedulerDomains.SCHEDULER_DOMAIN, Actions.write, triggerCreator.getScopeId()));

        //
        // Check trigger definition
        TriggerDefinition triggerDefinition = TRIGGER_DEFINITION_SERVICE.find(triggerCreator.getScopeId(), triggerCreator.getTriggerDefinitionId());
        ArgumentValidator.notNull(triggerDefinition, "triggerCreator.triggerDefinitionId");

        for (TriggerProperty jsp : triggerCreator.getTriggerProperties()) {
            for (TriggerProperty jsdp : triggerDefinition.getTriggerProperties()) {
                if (jsp.getName().equals(jsdp.getName())) {
                    ArgumentValidator.areEqual(jsp.getPropertyType(), jsdp.getPropertyType(), "triggerCreator.triggerProperties{}." + jsp.getName());
                    break;
                }
            }
        }

        //
        // Check duplicate name
        TriggerQuery query = new TriggerQueryImpl(triggerCreator.getScopeId());
        query.setPredicate(query.attributePredicate(TriggerAttributes.NAME, triggerCreator.getName()));

        if (count(query) > 0) {
            throw new KapuaDuplicateNameException();
        }

        if (triggerCreator.getStartsOn().equals(triggerCreator.getEndsOn()) && triggerCreator.getStartsOn().getTime() == (triggerCreator.getEndsOn().getTime())) {
            throw new KapuaException(KapuaErrorCodes.SAME_START_AND_DATE);
        }

        if (triggerCreator.getEndsOn() != null) {
            Date startTime = new Date(triggerCreator.getStartsOn().getTime());
            Date endTime = new Date(triggerCreator.getEndsOn().getTime());
            if (startTime.after(endTime)) {
                throw new KapuaEndBeforeStartTimeException();
            }
        }

        //
        // Do create
        return entityManagerSession.onTransactedInsert(em -> {

            Trigger trigger = TriggerDAO.create(em, triggerCreator);

            // Quartz Job definition and creation
            if (INTERVAL_JOB__TRIGGER.getId().equals(triggerCreator.getTriggerDefinitionId())) {
                QuartzTriggerUtils.createIntervalJobTrigger(trigger);
            } else if (CRON_JOB__TRIGGER.getId().equals(triggerCreator.getTriggerDefinitionId())) {
                QuartzTriggerUtils.createCronJobTrigger(trigger);
            }

            return trigger;
        });
    }

    @Override
    public Trigger update(Trigger trigger) throws KapuaException {
        //
        // Argument validation
        ArgumentValidator.notNull(trigger.getScopeId(), "trigger.scopeId");
        ArgumentValidator.notNull(trigger.getId(), "trigger.id");
        ArgumentValidator.notEmptyOrNull(trigger.getName(), "trigger.name");

        //
        // Check Access
        AUTHORIZATION_SERVICE.checkPermission(PERMISSION_FACTORY.newPermission(SchedulerDomains.SCHEDULER_DOMAIN, Actions.write, trigger.getScopeId()));

        //
        // Check existence
        if (find(trigger.getScopeId(), trigger.getId()) == null) {
            throw new KapuaEntityNotFoundException(trigger.getType(), trigger.getId());
        }

        //
        // Check duplicate name
        TriggerQuery query = new TriggerQueryImpl(trigger.getScopeId());
        query.setPredicate(
                query.andPredicate(
                        query.attributePredicate(TriggerAttributes.NAME, trigger.getName()),
                        query.attributePredicate(TriggerAttributes.ENTITY_ID, trigger.getId(), Operator.NOT_EQUAL)
                )
        );

        if (count(query) > 0) {
            throw new KapuaDuplicateNameException(trigger.getName());
        }

        //
        // Do update
        return entityManagerSession.onTransactedResult(em -> TriggerDAO.update(em, trigger));
    }

    @Override
    public void delete(KapuaId scopeId, KapuaId triggerId) throws KapuaException {
        //
        // Argument validation
        ArgumentValidator.notNull(triggerId, "scopeId");
        ArgumentValidator.notNull(scopeId, "triggerId");

        //
        // Check Access
        AUTHORIZATION_SERVICE.checkPermission(PERMISSION_FACTORY.newPermission(SchedulerDomains.SCHEDULER_DOMAIN, Actions.delete, scopeId));

        //
        // Check existence
        if (find(scopeId, triggerId) == null) {
            throw new KapuaEntityNotFoundException(Trigger.TYPE, triggerId);
        }

        //
        // Do delete
        entityManagerSession.onTransactedAction(em -> {
            TriggerDAO.delete(em, scopeId, triggerId);

            try {
                SchedulerFactory sf = new StdSchedulerFactory();
                Scheduler scheduler = sf.getScheduler();

                TriggerKey triggerKey = TriggerKey.triggerKey(triggerId.toCompactId(), scopeId.toCompactId());

                scheduler.unscheduleJob(triggerKey);

            } catch (SchedulerException se) {
                throw new RuntimeException(se);
            }
        });
    }

    @Override
    public Trigger find(KapuaId scopeId, KapuaId triggerId) throws KapuaException {
        //
        // Argument validation
        ArgumentValidator.notNull(scopeId, "scopeId");
        ArgumentValidator.notNull(triggerId, "triggerId");

        //
        // Check Access
        AUTHORIZATION_SERVICE.checkPermission(PERMISSION_FACTORY.newPermission(SchedulerDomains.SCHEDULER_DOMAIN, Actions.read, scopeId));

        //
        // Do find
        return entityManagerSession.onResult(em -> TriggerDAO.find(em, scopeId, triggerId));
    }

    @Override
    public TriggerListResult query(KapuaQuery<Trigger> query) throws KapuaException {
        //
        // Argument validation
        ArgumentValidator.notNull(query, "query");
        ArgumentValidator.notNull(query.getScopeId(), "query.scopeId");

        //
        // Check Access
        AUTHORIZATION_SERVICE.checkPermission(PERMISSION_FACTORY.newPermission(SchedulerDomains.SCHEDULER_DOMAIN, Actions.read, query.getScopeId()));

        //
        // Do query
        return entityManagerSession.onResult(em -> TriggerDAO.query(em, query));
    }

    @Override
    public long count(KapuaQuery<Trigger> query) throws KapuaException {
        //
        // Argument validation
        ArgumentValidator.notNull(query, "query");
        ArgumentValidator.notNull(query.getScopeId(), "query.scopeId");

        //
        // Check Access
        AUTHORIZATION_SERVICE.checkPermission(PERMISSION_FACTORY.newPermission(SchedulerDomains.SCHEDULER_DOMAIN, Actions.read, query.getScopeId()));

        //
        // Do count
        return entityManagerSession.onResult(em -> TriggerDAO.count(em, query));
    }

}
