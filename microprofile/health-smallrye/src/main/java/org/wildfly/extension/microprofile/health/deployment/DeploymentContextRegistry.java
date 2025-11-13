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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import javax.enterprise.inject.spi.BeanManager;

import org.jboss.modules.Module;

/**
 * Registry that maps deployment unit names to their extension contexts.
 * This allows a single CDI extension instance to handle multiple nested WARs
 * by looking up the correct context (module, BeanManager supplier) for each BDA.
 *
 * @author <a href="mailto:your.email@redhat.com">Your Name</a>
 */
public class DeploymentContextRegistry {

    private static final DeploymentContextRegistry INSTANCE = new DeploymentContextRegistry();

    /**
     * Context information for a deployment unit.
     */
    public static class DeploymentContext {
        private final Module module;
        private final Supplier<BeanManager> beanManagerSupplier;
        private final String deploymentUnitName;

        public DeploymentContext(Module module, Supplier<BeanManager> beanManagerSupplier, String deploymentUnitName) {
            this.module = module;
            this.beanManagerSupplier = beanManagerSupplier;
            this.deploymentUnitName = deploymentUnitName;
        }

        public Module getModule() {
            return module;
        }

        public Supplier<BeanManager> getBeanManagerSupplier() {
            return beanManagerSupplier;
        }

        public String getDeploymentUnitName() {
            return deploymentUnitName;
        }
    }

    private final Map<String, DeploymentContext> contexts = new ConcurrentHashMap<>();

    private DeploymentContextRegistry() {
        // Singleton
    }

    public static DeploymentContextRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Register a deployment context for a deployment unit.
     *
     * @param deploymentUnitName the name of the deployment unit
     * @param context the deployment context
     */
    public void registerContext(String deploymentUnitName, DeploymentContext context) {
        contexts.put(deploymentUnitName, context);
    }

    /**
     * Unregister a deployment context for a deployment unit.
     *
     * @param deploymentUnitName the name of the deployment unit
     */
    public void unregisterContext(String deploymentUnitName) {
        contexts.remove(deploymentUnitName);
    }

    /**
     * Look up a deployment context by deployment unit name.
     *
     * @param deploymentUnitName the name of the deployment unit
     * @return the deployment context, or null if not found
     */
    public DeploymentContext getContext(String deploymentUnitName) {
        return contexts.get(deploymentUnitName);
    }

    /**
     * Look up a deployment context by BDA identifier.
     * The BDA identifier is typically the module name or a path within the deployment.
     *
     * @param bdaIdentifier the BDA identifier (e.g., module name or deployment path)
     * @return the deployment context, or null if not found
     */
    public DeploymentContext getContextByBDA(String bdaIdentifier) {
        // Try direct match first
        DeploymentContext context = contexts.get(bdaIdentifier);
        if (context != null) {
            return context;
        }

        // Try to find by module name match
        for (DeploymentContext ctx : contexts.values()) {
            if (ctx.getModule() != null && ctx.getModule().getName().equals(bdaIdentifier)) {
                return ctx;
            }
        }

        // Try partial match (for nested WARs, the BDA identifier might be a path like "ear.war/WEB-INF/classes")
        for (Map.Entry<String, DeploymentContext> entry : contexts.entrySet()) {
            String deploymentName = entry.getKey();
            if (bdaIdentifier.contains(deploymentName) || deploymentName.contains(bdaIdentifier)) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * Clear all registered contexts (useful for testing).
     */
    public void clear() {
        contexts.clear();
    }

    /**
     * Get the number of registered contexts.
     *
     * @return the number of registered contexts
     */
    public int size() {
        return contexts.size();
    }

    /**
     * Get all registered contexts.
     *
     * @return a map of all registered contexts (deployment unit name -> context)
     */
    public java.util.Map<String, DeploymentContext> getAllContexts() {
        return new java.util.HashMap<>(contexts);
    }
}

