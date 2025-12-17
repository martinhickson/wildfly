/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.extension.microprofile.health.deployment;

import static org.jboss.as.weld.Capabilities.WELD_CAPABILITY_NAME;

import java.util.List;
import java.util.function.Supplier;

import javax.enterprise.inject.spi.BeanManager;

import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.ee.structure.DeploymentType;
import org.jboss.as.ee.structure.DeploymentTypeMarker;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.weld.WeldCapability;
import org.jboss.modules.Module;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.wildfly.extension.microprofile.health.MicroProfileHealthReporter;
import org.wildfly.extension.microprofile.health.MicroProfileHealthSubsystemDefinition;
import org.wildfly.extension.microprofile.health._private.MicroProfileHealthLogger;
import org.wildfly.extension.microprofile.health.deployment.DeploymentContextRegistry;
import org.wildfly.extension.microprofile.health.deployment.DeploymentContextRegistry.DeploymentContext;

/**
 */
public class DeploymentProcessor implements DeploymentUnitProcessor {

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        
        // Skip processing of arquillian-service deployment
        if ("arquillian-service".equals(deploymentUnit.getName())) {
            return;
        }
        
        MicroProfileHealthLogger.LOGGER.debug("DeploymentProcessor.deploy() called for: " + deploymentUnit.getName() + 
            " (Phase: " + phaseContext.getPhase() + ", Type: " + getDeploymentType(deploymentUnit) + ")");
        
        // Register the extension for all deployments (top-level and subdeployments)
        // We use a registry to store context for each deployment unit, and register the extension only once
        processDeploymentUnit(phaseContext, deploymentUnit);
        
        MicroProfileHealthLogger.LOGGER.debug("DeploymentProcessor.deploy() completed for: " + deploymentUnit.getName());
    }
    
    private String getDeploymentType(DeploymentUnit deploymentUnit) {
        if (DeploymentTypeMarker.isType(DeploymentType.EAR, deploymentUnit)) {
            return "EAR";
        } else if (DeploymentTypeMarker.isType(DeploymentType.WAR, deploymentUnit)) {
            return "WAR";
        } else if (DeploymentTypeMarker.isType(DeploymentType.EJB_JAR, deploymentUnit)) {
            return "EJB_JAR";
        } else if (DeploymentTypeMarker.isType(DeploymentType.APPLICATION_CLIENT, deploymentUnit)) {
            return "APPLICATION_CLIENT";
        }
        return "UNKNOWN";
    }

    private void processDeploymentUnit(DeploymentPhaseContext phaseContext, DeploymentUnit deploymentUnit) throws DeploymentUnitProcessingException {
        MicroProfileHealthLogger.LOGGER.debug("processDeploymentUnit() - Starting for: " + deploymentUnit.getName());
        
        Module module = deploymentUnit.getAttachment(Attachments.MODULE);
        if (module == null) {
            // Module not yet attached, skip this deployment unit
            MicroProfileHealthLogger.LOGGER.debug("Module is null, skipping deployment unit: " + deploymentUnit.getName());
            return;
        }
        
        MicroProfileHealthLogger.LOGGER.debug("Module found: " + module.getName() + " (ClassLoader: " + module.getClassLoader() + ")");

        // For subdeployments, capability support may be attached to the root deployment unit
        CapabilityServiceSupport support = deploymentUnit.getAttachment(Attachments.CAPABILITY_SERVICE_SUPPORT);
        MicroProfileHealthLogger.LOGGER.debug("CapabilityServiceSupport on current deployment unit: " + (support != null ? "FOUND" : "NOT FOUND"));
        
        if (support == null && deploymentUnit.getParent() != null) {
            // Try to get capability support from the root deployment unit for subdeployments
            MicroProfileHealthLogger.LOGGER.debug("This is a subdeployment, attempting to get capability support from root deployment unit");
            DeploymentUnit rootDeploymentUnit = getRootDeploymentUnit(deploymentUnit);
            MicroProfileHealthLogger.LOGGER.debug("Root deployment unit: " + rootDeploymentUnit.getName());
            support = rootDeploymentUnit.getAttachment(Attachments.CAPABILITY_SERVICE_SUPPORT);
            MicroProfileHealthLogger.LOGGER.debug("CapabilityServiceSupport on root deployment unit: " + (support != null ? "FOUND" : "NOT FOUND"));
        }
        if (support == null) {
            // Capability support not available, skip this deployment unit
            MicroProfileHealthLogger.LOGGER.error("CapabilityServiceSupport not available, skipping deployment unit: " + deploymentUnit.getName());
            return;
        }
        
        MicroProfileHealthLogger.LOGGER.debug("CapabilityServiceSupport obtained successfully");

        final WeldCapability weldCapability;
        try {
            MicroProfileHealthLogger.LOGGER.debug("Attempting to get WeldCapability from CapabilityServiceSupport");
            weldCapability = support.getCapabilityRuntimeAPI(WELD_CAPABILITY_NAME, WeldCapability.class);
            MicroProfileHealthLogger.LOGGER.debug("WeldCapability obtained successfully: " + weldCapability);
        } catch (CapabilityServiceSupport.NoSuchCapabilityException e) {
            //We should not be here since the subsystem depends on weld capability. Just in case ...
            MicroProfileHealthLogger.LOGGER.error("Failed to get WeldCapability: " + e.getMessage());
            throw MicroProfileHealthLogger.LOGGER.deploymentRequiresCapability(
                    deploymentUnit.getName(),
                    WELD_CAPABILITY_NAME);
        }
        
        MicroProfileHealthLogger.LOGGER.debug("Checking if deployment unit is part of Weld deployment: " + deploymentUnit.getName());
        boolean isWeldDeployment = weldCapability.isPartOfWeldDeployment(deploymentUnit);
        MicroProfileHealthLogger.LOGGER.debug("isPartOfWeldDeployment() result: " + isWeldDeployment);
        
        if (isWeldDeployment) {
            MicroProfileHealthLogger.LOGGER.debug("This is a Weld deployment, proceeding with CDI extension registration");
            final MicroProfileHealthReporter healthReporter = (MicroProfileHealthReporter) phaseContext.getServiceRegistry().getService(MicroProfileHealthSubsystemDefinition.HEALTH_REPORTER_SERVICE).getValue();
            MicroProfileHealthLogger.LOGGER.debug("MicroProfileHealthReporter service: " + (healthReporter != null ? "FOUND" : "NULL"));
            
            if (healthReporter != null) {
                // Create service builder for BeanManager dependency
                ServiceBuilder<?> serviceBuilder = phaseContext.getServiceTarget()
                    .addService(phaseContext.getPhaseServiceName().append("microprofile-health-beanmanager"));
                
                // Get BeanManager supplier via proper dependency injection
                Supplier<BeanManager> beanManagerSupplier = weldCapability.addBeanManagerService(deploymentUnit, serviceBuilder);
                
                if (beanManagerSupplier != null) {
                    MicroProfileHealthLogger.LOGGER.debug("BeanManager supplier obtained successfully");
                    MicroProfileHealthLogger.LOGGER.debug("Service name for BeanManager dependency: " + phaseContext.getPhaseServiceName().append("microprofile-health-beanmanager"));
                    
                    // Set a dummy service instance to hold the dependency
                    serviceBuilder.setInstance(new Service<Object>() {
                        @Override
                        public void start(StartContext context) {
                            MicroProfileHealthLogger.LOGGER.debug("Service started for BeanManager dependency");
                        }
                        
                        @Override
                        public void stop(StopContext context) {
                            MicroProfileHealthLogger.LOGGER.debug("Service stopped for BeanManager dependency");
                        }
                        
                        @Override
                        public Object getValue() {
                            return null;
                        }
                    });
                    serviceBuilder.install();
                    MicroProfileHealthLogger.LOGGER.debug("Service installed for BeanManager dependency");
                    
                    // Register the deployment context in the registry
                    DeploymentContextRegistry registry = DeploymentContextRegistry.getInstance();
                    DeploymentContext deploymentContext = new DeploymentContext(module, beanManagerSupplier, deploymentUnit.getName());
                    registry.registerContext(deploymentUnit.getName(), deploymentContext);
                    MicroProfileHealthLogger.LOGGER.debug("Registered deployment context for: " + deploymentUnit.getName());
                    
                    // Register the extension for each deployment unit so Weld fires AfterDeploymentValidation for each BDA
                    // Even though WeldPortableExtensions will overwrite the extension instance, the registry stores the context
                    // and the extension will look up the correct context based on which BDA is being processed
                    MicroProfileHealthLogger.LOGGER.debug("Creating CDIExtension instance for deployment: " + deploymentUnit.getName());
                    MicroProfileHealthLogger.LOGGER.debug("  - Module: " + module.getName());
                    MicroProfileHealthLogger.LOGGER.debug("  - Module ClassLoader: " + module.getClassLoader());
                    MicroProfileHealthLogger.LOGGER.debug("  - Deployment Unit: " + deploymentUnit.getName());
                    
                    CDIExtension cdiExtension = new CDIExtension(healthReporter, null, null);
                    MicroProfileHealthLogger.LOGGER.debug("Registering CDIExtension instance for deployment: " + deploymentUnit.getName());
                    MicroProfileHealthLogger.LOGGER.debug("  - Extension class: " + cdiExtension.getClass().getName());
                    MicroProfileHealthLogger.LOGGER.debug("  - Extension instance: " + cdiExtension);
                    MicroProfileHealthLogger.LOGGER.debug("  - Extension will use registry to look up context for BDA: " + deploymentUnit.getName());
                    
                    weldCapability.registerExtensionInstance(cdiExtension, deploymentUnit);
                    MicroProfileHealthLogger.LOGGER.debug("CDIExtension registered successfully for: " + deploymentUnit.getName());
                    MicroProfileHealthLogger.LOGGER.debug("Extension will observe AfterDeploymentValidation events and use registry to look up contexts");
                } else {
                    MicroProfileHealthLogger.LOGGER.error("BeanManager supplier is null for deployment unit: " + deploymentUnit.getName());
                }
            } else {
                MicroProfileHealthLogger.LOGGER.error("MicroProfileHealthReporter service is null, cannot register CDI extension");
            }
        } else {
            MicroProfileHealthLogger.LOGGER.debug("This is NOT a Weld deployment, skipping CDI extension registration for: " + deploymentUnit.getName());
        }
        
        MicroProfileHealthLogger.LOGGER.debug("processDeploymentUnit() - Completed for: " + deploymentUnit.getName());
    }

    @Override
    public void undeploy(DeploymentUnit deploymentUnit) {
        // Clean up the deployment context from the registry
        DeploymentContextRegistry registry = DeploymentContextRegistry.getInstance();
        registry.unregisterContext(deploymentUnit.getName());
        MicroProfileHealthLogger.LOGGER.debug("Unregistered deployment context for: " + deploymentUnit.getName());
    }

    /**
     * Get the root deployment unit (top-level deployment, not a subdeployment)
     */
    private DeploymentUnit getRootDeploymentUnit(DeploymentUnit deploymentUnit) {
        MicroProfileHealthLogger.LOGGER.debug("getRootDeploymentUnit() - Current: " + deploymentUnit.getName());
        DeploymentUnit parent = deploymentUnit.getParent();
        MicroProfileHealthLogger.LOGGER.debug("Parent deployment unit: " + (parent != null ? parent.getName() : "null"));
        if (parent == null) {
            MicroProfileHealthLogger.LOGGER.debug("Reached root deployment unit: " + deploymentUnit.getName());
            return deploymentUnit;
        }
        MicroProfileHealthLogger.LOGGER.debug("Traversing to parent deployment unit: " + parent.getName());
        return getRootDeploymentUnit(parent);
    }
}