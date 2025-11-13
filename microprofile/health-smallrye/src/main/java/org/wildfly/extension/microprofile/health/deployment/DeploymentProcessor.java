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
        
        System.out.println("================================================================================");
        MicroProfileHealthLogger.LOGGER.warn("================================================================================");
        System.out.println("[MicroProfile Health] DeploymentProcessor.deploy() called");
        MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] DeploymentProcessor.deploy() called");
        System.out.println("[MicroProfile Health] Phase: " + phaseContext.getPhase());
        MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] Phase: " + phaseContext.getPhase());
        System.out.println("[MicroProfile Health] Phase Service Name: " + phaseContext.getPhaseServiceName());
        MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] Phase Service Name: " + phaseContext.getPhaseServiceName());
        System.out.println("[MicroProfile Health] Deployment Unit Name: " + deploymentUnit.getName());
        MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] Deployment Unit Name: " + deploymentUnit.getName());
        System.out.println("[MicroProfile Health] Deployment Unit Parent: " + (deploymentUnit.getParent() != null ? deploymentUnit.getParent().getName() : "null (root deployment)"));
        MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] Deployment Unit Parent: " + (deploymentUnit.getParent() != null ? deploymentUnit.getParent().getName() : "null (root deployment)"));
        System.out.println("[MicroProfile Health] Is Subdeployment: " + (deploymentUnit.getParent() != null));
        MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] Is Subdeployment: " + (deploymentUnit.getParent() != null));
        System.out.println("[MicroProfile Health] Deployment Type: " + getDeploymentType(deploymentUnit));
        MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] Deployment Type: " + getDeploymentType(deploymentUnit));
        
        // List all subdeployments if this is an EAR
        if (deploymentUnit.getParent() == null) {
            List<DeploymentUnit> subDeployments = deploymentUnit.getAttachmentList(org.jboss.as.server.deployment.Attachments.SUB_DEPLOYMENTS);
            System.out.println("[MicroProfile Health] Subdeployments count: " + (subDeployments != null ? subDeployments.size() : 0));
            MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] Subdeployments count: " + (subDeployments != null ? subDeployments.size() : 0));
            if (subDeployments != null && !subDeployments.isEmpty()) {
                for (DeploymentUnit sub : subDeployments) {
                    System.out.println("[MicroProfile Health]   - Subdeployment: " + sub.getName());
                    MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health]   - Subdeployment: " + sub.getName());
                    Module subModule = sub.getAttachment(Attachments.MODULE);
                    System.out.println("[MicroProfile Health]     Module: " + (subModule != null ? subModule.getName() : "null"));
                    MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health]     Module: " + (subModule != null ? subModule.getName() : "null"));
                }
            }
        }
        
        // Register the extension for all deployments (top-level and subdeployments)
        // We use a registry to store context for each deployment unit, and register the extension only once
        System.out.println("[MicroProfile Health] Processing deployment - will register context and extension if needed");
        MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] Processing deployment - will register context and extension if needed");
        processDeploymentUnit(phaseContext, deploymentUnit);
        System.out.println("[MicroProfile Health] DeploymentProcessor.deploy() completed for: " + deploymentUnit.getName());
        MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] DeploymentProcessor.deploy() completed for: " + deploymentUnit.getName());
        System.out.println("================================================================================");
        MicroProfileHealthLogger.LOGGER.warn("================================================================================");
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
        System.out.println("[MicroProfile Health] processDeploymentUnit() - Starting for: " + deploymentUnit.getName());
        MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] processDeploymentUnit() - Starting for: " + deploymentUnit.getName());
        
        Module module = deploymentUnit.getAttachment(Attachments.MODULE);
        System.out.println("[MicroProfile Health] Module attachment check - Module: " + (module != null ? module.getName() : "null"));
        MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] Module attachment check - Module: " + (module != null ? module.getName() : "null"));
        if (module == null) {
            // Module not yet attached, skip this deployment unit
            System.out.println("[MicroProfile Health] WARNING: Module is null, skipping deployment unit: " + deploymentUnit.getName());
            MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] WARNING: Module is null, skipping deployment unit: " + deploymentUnit.getName());
            return;
        }
        System.out.println("[MicroProfile Health] Module found: " + module.getName() + " (ClassLoader: " + module.getClassLoader() + ")");
        MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] Module found: " + module.getName() + " (ClassLoader: " + module.getClassLoader() + ")");

        // For subdeployments, capability support may be attached to the root deployment unit
        System.out.println("[MicroProfile Health] Checking for CapabilityServiceSupport on deployment unit: " + deploymentUnit.getName());
        MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] Checking for CapabilityServiceSupport on deployment unit: " + deploymentUnit.getName());
        CapabilityServiceSupport support = deploymentUnit.getAttachment(Attachments.CAPABILITY_SERVICE_SUPPORT);
        System.out.println("[MicroProfile Health] CapabilityServiceSupport on current deployment unit: " + (support != null ? "FOUND" : "NOT FOUND"));
        MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] CapabilityServiceSupport on current deployment unit: " + (support != null ? "FOUND" : "NOT FOUND"));
        
        if (support == null && deploymentUnit.getParent() != null) {
            // Try to get capability support from the root deployment unit for subdeployments
            System.out.println("[MicroProfile Health] This is a subdeployment, attempting to get capability support from root deployment unit");
            MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] This is a subdeployment, attempting to get capability support from root deployment unit");
            DeploymentUnit rootDeploymentUnit = getRootDeploymentUnit(deploymentUnit);
            System.out.println("[MicroProfile Health] Root deployment unit: " + rootDeploymentUnit.getName());
            MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] Root deployment unit: " + rootDeploymentUnit.getName());
            support = rootDeploymentUnit.getAttachment(Attachments.CAPABILITY_SERVICE_SUPPORT);
            System.out.println("[MicroProfile Health] CapabilityServiceSupport on root deployment unit: " + (support != null ? "FOUND" : "NOT FOUND"));
            MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] CapabilityServiceSupport on root deployment unit: " + (support != null ? "FOUND" : "NOT FOUND"));
        }
        if (support == null) {
            // Capability support not available, skip this deployment unit
            System.out.println("[MicroProfile Health] ERROR: CapabilityServiceSupport not available, skipping deployment unit: " + deploymentUnit.getName());
            MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] ERROR: CapabilityServiceSupport not available, skipping deployment unit: " + deploymentUnit.getName());
            return;
        }
        System.out.println("[MicroProfile Health] CapabilityServiceSupport obtained successfully");
        MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] CapabilityServiceSupport obtained successfully");

        final WeldCapability weldCapability;
        try {
            System.out.println("[MicroProfile Health] Attempting to get WeldCapability from CapabilityServiceSupport");
            MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] Attempting to get WeldCapability from CapabilityServiceSupport");
            weldCapability = support.getCapabilityRuntimeAPI(WELD_CAPABILITY_NAME, WeldCapability.class);
            System.out.println("[MicroProfile Health] WeldCapability obtained successfully: " + weldCapability);
            MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] WeldCapability obtained successfully: " + weldCapability);
        } catch (CapabilityServiceSupport.NoSuchCapabilityException e) {
            //We should not be here since the subsystem depends on weld capability. Just in case ...
            System.out.println("[MicroProfile Health] ERROR: Failed to get WeldCapability: " + e.getMessage());
            MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] ERROR: Failed to get WeldCapability: " + e.getMessage());
            throw MicroProfileHealthLogger.LOGGER.deploymentRequiresCapability(
                    deploymentUnit.getName(),
                    WELD_CAPABILITY_NAME);
        }
        
        System.out.println("[MicroProfile Health] Checking if deployment unit is part of Weld deployment: " + deploymentUnit.getName());
        MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] Checking if deployment unit is part of Weld deployment: " + deploymentUnit.getName());
        boolean isWeldDeployment = weldCapability.isPartOfWeldDeployment(deploymentUnit);
        System.out.println("[MicroProfile Health] isPartOfWeldDeployment() result: " + isWeldDeployment);
        MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] isPartOfWeldDeployment() result: " + isWeldDeployment);
        
        if (isWeldDeployment) {
            System.out.println("[MicroProfile Health] This is a Weld deployment, proceeding with CDI extension registration");
            MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] This is a Weld deployment, proceeding with CDI extension registration");
            System.out.println("[MicroProfile Health] Looking up MicroProfileHealthReporter service");
            MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] Looking up MicroProfileHealthReporter service");
            final MicroProfileHealthReporter healthReporter = (MicroProfileHealthReporter) phaseContext.getServiceRegistry().getService(MicroProfileHealthSubsystemDefinition.HEALTH_REPORTER_SERVICE).getValue();
            System.out.println("[MicroProfile Health] MicroProfileHealthReporter service: " + (healthReporter != null ? "FOUND" : "NULL"));
            MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] MicroProfileHealthReporter service: " + (healthReporter != null ? "FOUND" : "NULL"));
            
                   if (healthReporter != null) {
                       // Create service builder for BeanManager dependency
                       ServiceBuilder<?> serviceBuilder = phaseContext.getServiceTarget()
                           .addService(phaseContext.getPhaseServiceName().append("microprofile-health-beanmanager"));
                       
                       // Get BeanManager supplier via proper dependency injection
                       Supplier<BeanManager> beanManagerSupplier = weldCapability.addBeanManagerService(deploymentUnit, serviceBuilder);
                       
                       if (beanManagerSupplier != null) {
                           System.out.println("[MicroProfile Health] BeanManager supplier obtained successfully");
                           MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] BeanManager supplier obtained successfully");
                           System.out.println("[MicroProfile Health] Service name for BeanManager dependency: " + phaseContext.getPhaseServiceName().append("microprofile-health-beanmanager"));
                           MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] Service name for BeanManager dependency: " + phaseContext.getPhaseServiceName().append("microprofile-health-beanmanager"));
                           
                           // Set a dummy service instance to hold the dependency
                           serviceBuilder.setInstance(new Service<Object>() {
                               @Override
                               public void start(StartContext context) {
                                   System.out.println("[MicroProfile Health] Service started for BeanManager dependency");
                                   MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] Service started for BeanManager dependency");
                               }
                               
                               @Override
                               public void stop(StopContext context) {
                                   System.out.println("[MicroProfile Health] Service stopped for BeanManager dependency");
                                   MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] Service stopped for BeanManager dependency");
                               }
                               
                               @Override
                               public Object getValue() {
                                   return null;
                               }
                           });
                           serviceBuilder.install();
                           System.out.println("[MicroProfile Health] Service installed for BeanManager dependency");
                           MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] Service installed for BeanManager dependency");
                           
                           // Register the deployment context in the registry
                           DeploymentContextRegistry registry = DeploymentContextRegistry.getInstance();
                           DeploymentContext deploymentContext = new DeploymentContext(module, beanManagerSupplier, deploymentUnit.getName());
                           registry.registerContext(deploymentUnit.getName(), deploymentContext);
                           System.out.println("[MicroProfile Health] Registered deployment context for: " + deploymentUnit.getName());
                           MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] Registered deployment context for: " + deploymentUnit.getName());
                           
                           // Register the extension for each deployment unit so Weld fires AfterDeploymentValidation for each BDA
                           // Even though WeldPortableExtensions will overwrite the extension instance, the registry stores the context
                           // and the extension will look up the correct context based on which BDA is being processed
                           System.out.println("[MicroProfile Health] Creating CDIExtension instance for deployment: " + deploymentUnit.getName());
                           MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] Creating CDIExtension instance for deployment: " + deploymentUnit.getName());
                           System.out.println("[MicroProfile Health]   - Module: " + module.getName());
                           MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health]   - Module: " + module.getName());
                           System.out.println("[MicroProfile Health]   - Module ClassLoader: " + module.getClassLoader());
                           MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health]   - Module ClassLoader: " + module.getClassLoader());
                           System.out.println("[MicroProfile Health]   - Deployment Unit: " + deploymentUnit.getName());
                           MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health]   - Deployment Unit: " + deploymentUnit.getName());
                           
                           // Create extension - it will use the registry to look up the correct context for each BDA
                           CDIExtension cdiExtension = new CDIExtension(healthReporter, null, null);
                           System.out.println("[MicroProfile Health] Registering CDIExtension instance for deployment: " + deploymentUnit.getName());
                           MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] Registering CDIExtension instance for deployment: " + deploymentUnit.getName());
                           System.out.println("[MicroProfile Health]   - Extension class: " + cdiExtension.getClass().getName());
                           MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health]   - Extension class: " + cdiExtension.getClass().getName());
                           System.out.println("[MicroProfile Health]   - Extension instance: " + cdiExtension);
                           MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health]   - Extension instance: " + cdiExtension);
                           System.out.println("[MicroProfile Health]   - Extension will use registry to look up context for BDA: " + deploymentUnit.getName());
                           MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health]   - Extension will use registry to look up context for BDA: " + deploymentUnit.getName());
                           
                           weldCapability.registerExtensionInstance(cdiExtension, deploymentUnit);
                           System.out.println("[MicroProfile Health] CDIExtension registered successfully for: " + deploymentUnit.getName());
                           MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] CDIExtension registered successfully for: " + deploymentUnit.getName());
                           System.out.println("[MicroProfile Health] Extension will observe AfterDeploymentValidation events and use registry to look up contexts");
                           MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] Extension will observe AfterDeploymentValidation events and use registry to look up contexts");
                       } else {
                           System.out.println("[MicroProfile Health] ERROR: BeanManager supplier is null for deployment unit: " + deploymentUnit.getName());
                           MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] ERROR: BeanManager supplier is null for deployment unit: " + deploymentUnit.getName());
                       }
                   } else {
                       System.out.println("[MicroProfile Health] ERROR: MicroProfileHealthReporter service is null, cannot register CDI extension");
                       MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] ERROR: MicroProfileHealthReporter service is null, cannot register CDI extension");
                   }
        } else {
            System.out.println("[MicroProfile Health] This is NOT a Weld deployment, skipping CDI extension registration for: " + deploymentUnit.getName());
            MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] This is NOT a Weld deployment, skipping CDI extension registration for: " + deploymentUnit.getName());
        }
        
        System.out.println("[MicroProfile Health] processDeploymentUnit() - Completed for: " + deploymentUnit.getName());
        MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] processDeploymentUnit() - Completed for: " + deploymentUnit.getName());
    }

    @Override
    public void undeploy(DeploymentUnit deploymentUnit) {
        // Clean up the deployment context from the registry
        DeploymentContextRegistry registry = DeploymentContextRegistry.getInstance();
        registry.unregisterContext(deploymentUnit.getName());
        System.out.println("[MicroProfile Health] Unregistered deployment context for: " + deploymentUnit.getName());
        MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] Unregistered deployment context for: " + deploymentUnit.getName());
    }

    /**
     * Get the root deployment unit (top-level deployment, not a subdeployment)
     */
    private DeploymentUnit getRootDeploymentUnit(DeploymentUnit deploymentUnit) {
        System.out.println("[MicroProfile Health] getRootDeploymentUnit() - Current: " + deploymentUnit.getName());
        MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] getRootDeploymentUnit() - Current: " + deploymentUnit.getName());
        DeploymentUnit parent = deploymentUnit.getParent();
        System.out.println("[MicroProfile Health] Parent deployment unit: " + (parent != null ? parent.getName() : "null"));
        MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] Parent deployment unit: " + (parent != null ? parent.getName() : "null"));
        if (parent == null) {
            System.out.println("[MicroProfile Health] Reached root deployment unit: " + deploymentUnit.getName());
            MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] Reached root deployment unit: " + deploymentUnit.getName());
            return deploymentUnit;
        }
        System.out.println("[MicroProfile Health] Traversing to parent deployment unit: " + parent.getName());
        MicroProfileHealthLogger.LOGGER.warn("[MicroProfile Health] Traversing to parent deployment unit: " + parent.getName());
        return getRootDeploymentUnit(parent);
    }
}