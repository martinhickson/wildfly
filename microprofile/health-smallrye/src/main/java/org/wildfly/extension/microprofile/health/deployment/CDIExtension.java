package org.wildfly.extension.microprofile.health.deployment;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeShutdown;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.util.AnnotationLiteral;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.Readiness;
import org.eclipse.microprofile.health.Startup;
import org.jboss.modules.Module;
import org.wildfly.extension.microprofile.health.MicroProfileHealthReporter;
import org.wildfly.extension.microprofile.health._private.MicroProfileHealthLogger;
import org.wildfly.extension.microprofile.health.deployment.DeploymentContextRegistry;
import org.wildfly.extension.microprofile.health.deployment.DeploymentContextRegistry.DeploymentContext;

import io.smallrye.health.SmallRyeHealthReporter;

import java.util.function.Supplier;

public class CDIExtension implements Extension {

    private final String MP_HEALTH_DISABLE_DEFAULT_PROCEDURES = "mp.health.disable-default-procedures";

    private final MicroProfileHealthReporter reporter;
    private final Module module;
    private final Supplier<BeanManager> beanManagerSupplier;

    private Instance<Object> instance;
    private final List<HealthCheck> livenessChecks = new ArrayList<>();
    private final List<HealthCheck> readinessChecks = new ArrayList<>();
    private final List<HealthCheck> startupChecks = new ArrayList<>();
    private HealthCheck defaultReadinessCheck;
    private HealthCheck defaultStartupCheck;
    private Module currentContextModule; // Module for the current BDA being processed

    public CDIExtension(MicroProfileHealthReporter healthReporter, Module module, Supplier<BeanManager> beanManagerSupplier) {
        MicroProfileHealthLogger.LOGGER.debug("CDIExtension constructor called for module: " + (module != null ? module.getName() : "null"));
        this.reporter = healthReporter;
        this.module = module;
        this.beanManagerSupplier = beanManagerSupplier;
    }

    public void afterDeploymentValidation(@Observes final AfterDeploymentValidation adv, BeanManager eventBM) {
        MicroProfileHealthLogger.LOGGER.debug("afterDeploymentValidation called");
        MicroProfileHealthLogger.LOGGER.debug("  Extension instance: " + this);
        MicroProfileHealthLogger.LOGGER.debug("  Event BeanManager: " + eventBM);
        
        // Get the registry and process ALL registered deployment contexts
        // This ensures we discover health checks in the EAR and all nested WARs
        DeploymentContextRegistry registry = DeploymentContextRegistry.getInstance();
        MicroProfileHealthLogger.LOGGER.debug("  Registry contains " + registry.size() + " deployment context(s)");
        
        // Process each deployment context (EAR + all nested WARs)
        for (java.util.Map.Entry<String, DeploymentContextRegistry.DeploymentContext> entry : 
             new java.util.HashMap<>(registry.getAllContexts()).entrySet()) {
            String deploymentUnitName = entry.getKey();
            DeploymentContextRegistry.DeploymentContext deploymentContext = entry.getValue();
            
            MicroProfileHealthLogger.LOGGER.debug("  Processing deployment context: " + deploymentUnitName);
            
            Module contextModule = deploymentContext.getModule();
            Supplier<BeanManager> contextBeanManagerSupplier = deploymentContext.getBeanManagerSupplier();
            
            if (contextModule == null) {
                MicroProfileHealthLogger.LOGGER.warn("Skipping deployment context with null module: " + deploymentUnitName);
                continue;
            }
            
            MicroProfileHealthLogger.LOGGER.debug("  Context module: " + contextModule.getName());
            
            // Get the BeanManager for this deployment context
            BeanManager contextBeanManager = null;
            if (contextBeanManagerSupplier != null) {
                try {
                    contextBeanManager = contextBeanManagerSupplier.get();
                    MicroProfileHealthLogger.LOGGER.debug("  Context BeanManager: " + contextBeanManager);
                } catch (Exception e) {
                    MicroProfileHealthLogger.LOGGER.error("Could not get BeanManager for " + deploymentUnitName + ": " + e.getMessage());
                    continue;
                }
            }
            
            if (contextBeanManager == null) {
                MicroProfileHealthLogger.LOGGER.warn("Skipping deployment context with null BeanManager: " + deploymentUnitName);
                continue;
            }
            
            // Store the context module for use in addHealthChecks
            currentContextModule = contextModule;
            
            // Create Instance from the context BeanManager
            MicroProfileHealthLogger.LOGGER.debug("  Creating Instance from context BeanManager for: " + deploymentUnitName);
            instance = contextBeanManager.createInstance();
            MicroProfileHealthLogger.LOGGER.debug("  Created Instance: " + instance);
            
            MicroProfileHealthLogger.LOGGER.debug("  Searching for health checks in module: " + contextModule.getName());
            
            // Discover health checks for this deployment context
            MicroProfileHealthLogger.LOGGER.debug("  [1/3] Searching for Liveness checks in " + deploymentUnitName + "...");
            addHealthChecks(Liveness.Literal.INSTANCE, reporter::addLivenessCheck, livenessChecks);
            
            MicroProfileHealthLogger.LOGGER.debug("  [2/3] Searching for Readiness checks in " + deploymentUnitName + "...");
            addHealthChecks(Readiness.Literal.INSTANCE, reporter::addReadinessCheck, readinessChecks);
            
            MicroProfileHealthLogger.LOGGER.debug("  [3/3] Searching for Startup checks in " + deploymentUnitName + "...");
            addHealthChecks(Startup.Literal.INSTANCE, reporter::addStartupCheck, startupChecks);
            
            // Log summary of health checks found
            MicroProfileHealthLogger.LOGGER.debug("  Summary for " + deploymentUnitName + " (module: " + contextModule.getName() + "):");
            MicroProfileHealthLogger.LOGGER.debug("    - Liveness checks found: " + livenessChecks.size());
            MicroProfileHealthLogger.LOGGER.debug("    - Readiness checks found: " + readinessChecks.size());
            MicroProfileHealthLogger.LOGGER.debug("    - Startup checks found: " + startupChecks.size());
            
            if (livenessChecks.size() > 0 || readinessChecks.size() > 0 || startupChecks.size() > 0) {
                MicroProfileHealthLogger.LOGGER.debug("Health checks discovered for " + deploymentUnitName + 
                    " (Liveness: " + livenessChecks.size() + 
                    ", Readiness: " + readinessChecks.size() + 
                    ", Startup: " + startupChecks.size() + ")");
            }
            
            // Check for default checks (only for this specific deployment, not global)
            Config config = ConfigProvider.getConfig(contextModule.getClassLoader());
            boolean disableDefaults = config.getOptionalValue(MP_HEALTH_DISABLE_DEFAULT_PROCEDURES, Boolean.class).orElse(false);
            
            if (readinessChecks.isEmpty() && !disableDefaults) {
                defaultReadinessCheck = new DefaultReadinessHealthCheck(contextModule.getName());
                reporter.addReadinessCheck(defaultReadinessCheck, contextModule.getClassLoader());
                MicroProfileHealthLogger.LOGGER.debug("  Registered default Readiness check for: " + contextModule.getName());
            }
            
            if (startupChecks.isEmpty() && !disableDefaults) {
                defaultStartupCheck = new DefaultStartupHealthCheck(contextModule.getName());
                reporter.addStartupCheck(defaultStartupCheck, contextModule.getClassLoader());
                MicroProfileHealthLogger.LOGGER.debug("  Registered default Startup check for: " + contextModule.getName());
            }
        }
        
        reporter.setUserChecksProcessed(true);
        MicroProfileHealthLogger.LOGGER.debug("afterDeploymentValidation completed");
    }

    private void addHealthChecks(AnnotationLiteral qualifier,
                                 BiConsumer<HealthCheck, ClassLoader> healthFunction,
                                 List<HealthCheck> healthChecks) {

        MicroProfileHealthLogger.LOGGER.debug("  addHealthChecks() - Qualifier: " + qualifier);
        MicroProfileHealthLogger.LOGGER.debug("    Instance: " + instance);
        MicroProfileHealthLogger.LOGGER.debug("    Instance class: " + (instance != null ? instance.getClass().getName() : "null"));
        MicroProfileHealthLogger.LOGGER.debug("    Selecting HealthCheck.class with qualifier: " + qualifier);
        
        Instance<HealthCheck> healthCheckInstance = instance.select(HealthCheck.class, qualifier);
        MicroProfileHealthLogger.LOGGER.debug("    HealthCheck Instance: " + healthCheckInstance);
        MicroProfileHealthLogger.LOGGER.debug("    HealthCheck Instance class: " + (healthCheckInstance != null ? healthCheckInstance.getClass().getName() : "null"));
        MicroProfileHealthLogger.LOGGER.debug("    Is Unsatisfied: " + healthCheckInstance.isUnsatisfied());
        MicroProfileHealthLogger.LOGGER.debug("    Is Ambiguous: " + healthCheckInstance.isAmbiguous());

        if (healthCheckInstance.isUnsatisfied()) {
            MicroProfileHealthLogger.LOGGER.debug("    No health checks found for qualifier: " + qualifier);
            MicroProfileHealthLogger.LOGGER.debug("    This BDA does not contain any beans annotated with " + qualifier);
            return;
        }

        int count = 0;
        for (HealthCheck hc : healthCheckInstance) {
            count++;
            String hcClassName = hc.getClass().getName();
            ClassLoader hcClassLoader = hc.getClass().getClassLoader();
            MicroProfileHealthLogger.LOGGER.debug("    Found health check #" + count + ": " + hcClassName);
            MicroProfileHealthLogger.LOGGER.debug("      Health check classloader: " + hcClassLoader);
            
            // Use currentContextModule if available, otherwise fall back to module
            Module moduleToUse = currentContextModule != null ? currentContextModule : module;
            MicroProfileHealthLogger.LOGGER.debug("      Registering with module classloader: " + moduleToUse.getClassLoader());
            
            healthFunction.accept(hc, moduleToUse.getClassLoader());
            healthChecks.add(hc);
            MicroProfileHealthLogger.LOGGER.debug("      Registered successfully");
        }
        
        MicroProfileHealthLogger.LOGGER.debug("    Total health checks found for " + qualifier + ": " + count);
    }

    public void beforeShutdown(@Observes final BeforeShutdown bs) {
        MicroProfileHealthLogger.LOGGER.debug("beforeShutdown called - removing all health checks");
        removeHealthCheck(livenessChecks, reporter::removeLivenessCheck);
        removeHealthCheck(readinessChecks, reporter::removeReadinessCheck);
        removeHealthCheck(startupChecks, reporter::removeStartupCheck);

        if (defaultReadinessCheck != null) {
            reporter.removeReadinessCheck(defaultReadinessCheck);
            defaultReadinessCheck = null;
        }

        if (defaultStartupCheck != null) {
            reporter.removeStartupCheck(defaultStartupCheck);
            defaultStartupCheck = null;
        }

        instance = null;
        MicroProfileHealthLogger.LOGGER.debug("beforeShutdown completed - all health checks removed");
    }

    private void removeHealthCheck(List<HealthCheck> healthChecks,
                                   Consumer<HealthCheck> healthFunction) {
        for (HealthCheck hc : healthChecks) {
            healthFunction.accept(hc);
            instance.destroy(hc);
        }
        healthChecks.clear();
    }

    public void vetoSmallryeHealthReporter(@Observes ProcessAnnotatedType<SmallRyeHealthReporter> pat) {
        pat.veto();
        MicroProfileHealthLogger.LOGGER.debug("SmallRyeHealthReporter vetoed");
    }

    private static final class DefaultReadinessHealthCheck implements HealthCheck {
        private final String deploymentName;

        DefaultReadinessHealthCheck(String deploymentName) {
            this.deploymentName = deploymentName;
        }

        @Override
        public HealthCheckResponse call() {
            return HealthCheckResponse.named("ready-" + deploymentName).up().build();
        }
    }

    private static final class DefaultStartupHealthCheck implements HealthCheck {
        private final String deploymentName;

        DefaultStartupHealthCheck(String deploymentName) {
            this.deploymentName = deploymentName;
        }

        @Override
        public HealthCheckResponse call() {
            return HealthCheckResponse.named("started-" + deploymentName).up().build();
        }
    }
}
