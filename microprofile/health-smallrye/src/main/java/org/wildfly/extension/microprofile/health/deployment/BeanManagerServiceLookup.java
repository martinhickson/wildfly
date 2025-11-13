/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2025, Red Hat, Inc., and individual contributors
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

import java.security.AccessController;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.enterprise.inject.spi.BeanManager;

import org.jboss.as.server.CurrentServiceContainer;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.weld.ServiceNames;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;

/**
 * Utility class for looking up BeanManager services using service names.
 * Provides methods to find BeanManagers for specific deployments or discover all BeanManagers.
 *
 * @author <a href="mailto:your.email@redhat.com">Your Name</a>
 */
public class BeanManagerServiceLookup {

    private static final ServiceName BEAN_MANAGER_SERVICE_NAME = ServiceName.of("beanmanager");

    /**
     * Gets the BeanManager service name for a given DeploymentUnit.
     *
     * @param deploymentUnit the deployment unit
     * @return the service name for the BeanManager associated with this deployment
     */
    public static ServiceName getBeanManagerServiceName(DeploymentUnit deploymentUnit) {
        return ServiceNames.beanManagerServiceName(deploymentUnit);
    }

    /**
     * Gets the BeanManager for a specific DeploymentUnit from the service registry.
     *
     * @param deploymentUnit the deployment unit to get the BeanManager for
     * @return the BeanManager for this deployment, or null if not found
     */
    public static BeanManager getBeanManager(DeploymentUnit deploymentUnit) {
        ServiceName beanManagerServiceName = getBeanManagerServiceName(deploymentUnit);
        return getBeanManagerByServiceName(beanManagerServiceName);
    }

    /**
     * Gets a BeanManager by its service name.
     *
     * @param beanManagerServiceName the service name of the BeanManager
     * @return the BeanManager, or null if the service is not available
     */
    public static BeanManager getBeanManagerByServiceName(ServiceName beanManagerServiceName) {
        ServiceContainer serviceContainer = getServiceContainer();
        if (serviceContainer == null) {
            return null;
        }

        ServiceController<?> controller = serviceContainer.getService(beanManagerServiceName);
        if (controller != null && controller.getState() == ServiceController.State.UP) {
            Object value = controller.getValue();
            if (value instanceof BeanManager) {
                return (BeanManager) value;
            }
        }
        return null;
    }

    /**
     * Finds all BeanManager services in the service registry.
     * This scans all services to find those that are BeanManager instances.
     *
     * @return a map of service names to BeanManager instances
     */
    public static Map<ServiceName, BeanManager> findAllBeanManagers() {
        Map<ServiceName, BeanManager> beanManagers = new HashMap<>();
        ServiceContainer serviceContainer = getServiceContainer();
        if (serviceContainer == null) {
            return beanManagers;
        }

        List<ServiceName> serviceNames = serviceContainer.getServiceNames();
        for (ServiceName serviceName : serviceNames) {
            // Check if this service name ends with "beanmanager"
            String canonicalName = serviceName.getCanonicalName();
            if (canonicalName.endsWith(".beanmanager") || canonicalName.equals("beanmanager")) {
                BeanManager beanManager = getBeanManagerByServiceName(serviceName);
                if (beanManager != null) {
                    beanManagers.put(serviceName, beanManager);
                }
            }
        }
        return beanManagers;
    }

    /**
     * Finds all BeanManager services that belong to deployments (not application server level).
     * Deployment BeanManagers typically have service names like:
     * deployment.deployment-name.beanmanager
     *
     * @return a list of BeanManager instances from deployments
     */
    public static List<BeanManager> findDeploymentBeanManagers() {
        List<BeanManager> deploymentBeanManagers = new ArrayList<>();
        Map<ServiceName, BeanManager> allBeanManagers = findAllBeanManagers();

        for (Map.Entry<ServiceName, BeanManager> entry : allBeanManagers.entrySet()) {
            ServiceName serviceName = entry.getKey();
            // Deployment BeanManagers typically start with "deployment."
            String canonicalName = serviceName.getCanonicalName();
            if (canonicalName.startsWith("deployment.")) {
                deploymentBeanManagers.add(entry.getValue());
            }
        }
        return deploymentBeanManagers;
    }

    /**
     * Finds BeanManagers for top-level deployments only (excludes nested WARs in EARs).
     * Top-level deployments have service names like:
     * deployment.standalone-war.beanmanager
     * deployment.my-ear.beanmanager (but NOT deployment.my-ear.my-nested-war.beanmanager)
     *
     * @return a map of deployment names to their BeanManager instances
     */
    public static Map<String, BeanManager> findTopLevelDeploymentBeanManagers() {
        Map<String, BeanManager> topLevelBeanManagers = new HashMap<>();
        Map<ServiceName, BeanManager> allBeanManagers = findAllBeanManagers();

        for (Map.Entry<ServiceName, BeanManager> entry : allBeanManagers.entrySet()) {
            ServiceName serviceName = entry.getKey();
            String canonicalName = serviceName.getCanonicalName();
            // Top-level deployments have pattern: deployment.<name>.beanmanager
            // Nested deployments would have: deployment.<ear>.<war>.beanmanager
            // We can detect top-level by counting dots - top-level has 2 dots (deployment.name.beanmanager)
            if (canonicalName.startsWith("deployment.") && canonicalName.endsWith(".beanmanager")) {
                // Count dots to determine if it's top-level (should have exactly 2 dots)
                long dotCount = canonicalName.chars().filter(ch -> ch == '.').count();
                if (dotCount == 2) {
                    // Extract deployment name: deployment.<name>.beanmanager -> <name>
                    int firstDot = canonicalName.indexOf('.');
                    int secondDot = canonicalName.indexOf('.', firstDot + 1);
                    if (secondDot > firstDot + 1) {
                        String deploymentName = canonicalName.substring(firstDot + 1, secondDot);
                        topLevelBeanManagers.put(deploymentName, entry.getValue());
                    }
                }
            }
        }
        return topLevelBeanManagers;
    }

    /**
     * Finds the BeanManager for a deployment by its name.
     *
     * @param deploymentName the name of the deployment (e.g., "my-app.war")
     * @return the BeanManager for this deployment, or null if not found
     */
    public static BeanManager findBeanManagerByDeploymentName(String deploymentName) {
        ServiceName beanManagerServiceName = ServiceName.of("deployment", deploymentName, "beanmanager");
        return getBeanManagerByServiceName(beanManagerServiceName);
    }

    /**
     * Gets the current service container, handling security manager if present.
     *
     * @return the current service container, or null if not available
     */
    private static ServiceContainer getServiceContainer() {
        if (System.getSecurityManager() == null) {
            return CurrentServiceContainer.getServiceContainer();
        }
        return AccessController.doPrivileged(CurrentServiceContainer.GET_ACTION);
    }

    /**
     * Checks if a BeanManager service exists for the given DeploymentUnit.
     *
     * @param deploymentUnit the deployment unit to check
     * @return true if the BeanManager service exists and is up
     */
    public static boolean hasBeanManager(DeploymentUnit deploymentUnit) {
        ServiceName beanManagerServiceName = getBeanManagerServiceName(deploymentUnit);
        ServiceContainer serviceContainer = getServiceContainer();
        if (serviceContainer == null) {
            return false;
        }

        ServiceController<?> controller = serviceContainer.getService(beanManagerServiceName);
        return controller != null && controller.getState() == ServiceController.State.UP;
    }

    /**
     * Waits for a BeanManager service to become available for a given DeploymentUnit.
     * This is useful when the BeanManager might not be immediately available during deployment processing.
     *
     * @param deploymentUnit the deployment unit
     * @param timeoutMs maximum time to wait in milliseconds
     * @return the BeanManager if available within the timeout, or null
     */
    public static BeanManager waitForBeanManager(DeploymentUnit deploymentUnit, long timeoutMs) {
        ServiceName beanManagerServiceName = getBeanManagerServiceName(deploymentUnit);
        ServiceContainer serviceContainer = getServiceContainer();
        if (serviceContainer == null) {
            return null;
        }

        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            ServiceController<?> controller = serviceContainer.getService(beanManagerServiceName);
            if (controller != null && controller.getState() == ServiceController.State.UP) {
                Object value = controller.getValue();
                if (value instanceof BeanManager) {
                    return (BeanManager) value;
                }
            }
            try {
                Thread.sleep(10); // Wait 10ms before checking again
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }
}

