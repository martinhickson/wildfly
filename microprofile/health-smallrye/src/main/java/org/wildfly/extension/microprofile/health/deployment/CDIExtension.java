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

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_CYAN = "\u001B[36m";

    public CDIExtension(MicroProfileHealthReporter healthReporter, Module module, Supplier<BeanManager> beanManagerSupplier) {
        System.out.println(ANSI_CYAN + "🚀 [MicroProfile Health CDIExtension] Constructor called for module: " +
                (module != null ? module.getName() : "null") + ANSI_RESET);
        MicroProfileHealthLogger.LOGGER.warn("🚀 [MicroProfile Health CDIExtension] Constructor called for module: " +
                (module != null ? module.getName() : "null"));
        this.reporter = healthReporter;
        this.module = module;
        this.beanManagerSupplier = beanManagerSupplier;
    }

    public void afterDeploymentValidation(@Observes final AfterDeploymentValidation adv, BeanManager eventBM) {
        System.out.println(ANSI_CYAN + "================================================================================" + ANSI_RESET);
        MicroProfileHealthLogger.LOGGER.warn("================================================================================");
        System.out.println(ANSI_CYAN + "✨ [afterDeploymentValidation] called" + ANSI_RESET);
        MicroProfileHealthLogger.LOGGER.warn("✨ [afterDeploymentValidation] called");
        System.out.println(ANSI_CYAN + "   Extension instance: " + this + ANSI_RESET);
        MicroProfileHealthLogger.LOGGER.warn("   Extension instance: " + this);
        System.out.println(ANSI_CYAN + "   Event BeanManager: " + eventBM + ANSI_RESET);
        MicroProfileHealthLogger.LOGGER.warn("   Event BeanManager: " + eventBM);
        
        // Get the registry and process ALL registered deployment contexts
        // This ensures we discover health checks in the EAR and all nested WARs
        DeploymentContextRegistry registry = DeploymentContextRegistry.getInstance();
        System.out.println(ANSI_CYAN + "   Registry contains " + registry.size() + " deployment context(s)" + ANSI_RESET);
        MicroProfileHealthLogger.LOGGER.warn("   Registry contains " + registry.size() + " deployment context(s)");
        
        // Process each deployment context (EAR + all nested WARs)
        for (java.util.Map.Entry<String, DeploymentContextRegistry.DeploymentContext> entry : 
             new java.util.HashMap<>(registry.getAllContexts()).entrySet()) {
            String deploymentUnitName = entry.getKey();
            DeploymentContextRegistry.DeploymentContext deploymentContext = entry.getValue();
            
            System.out.println(ANSI_CYAN + "   Processing deployment context: " + deploymentUnitName + ANSI_RESET);
            MicroProfileHealthLogger.LOGGER.warn("   Processing deployment context: " + deploymentUnitName);
            
            Module contextModule = deploymentContext.getModule();
            Supplier<BeanManager> contextBeanManagerSupplier = deploymentContext.getBeanManagerSupplier();
            
            if (contextModule == null) {
                System.out.println(ANSI_YELLOW + "   ⚠ Skipping deployment context with null module: " + deploymentUnitName + ANSI_RESET);
                MicroProfileHealthLogger.LOGGER.warn("   ⚠ Skipping deployment context with null module: " + deploymentUnitName);
                continue;
            }
            
            System.out.println(ANSI_CYAN + "   Context module: " + contextModule.getName() + ANSI_RESET);
            MicroProfileHealthLogger.LOGGER.warn("   Context module: " + contextModule.getName());
            
            // Get the BeanManager for this deployment context
            BeanManager contextBeanManager = null;
            if (contextBeanManagerSupplier != null) {
                try {
                    contextBeanManager = contextBeanManagerSupplier.get();
                    System.out.println(ANSI_CYAN + "   Context BeanManager: " + contextBeanManager + ANSI_RESET);
                    MicroProfileHealthLogger.LOGGER.warn("   Context BeanManager: " + contextBeanManager);
                } catch (Exception e) {
                    System.out.println(ANSI_YELLOW + "   ⚠ Could not get BeanManager for " + deploymentUnitName + ": " + e.getMessage() + ANSI_RESET);
                    MicroProfileHealthLogger.LOGGER.warn("   ⚠ Could not get BeanManager for " + deploymentUnitName + ": " + e.getMessage());
                    continue;
                }
            }
            
            if (contextBeanManager == null) {
                System.out.println(ANSI_YELLOW + "   ⚠ Skipping deployment context with null BeanManager: " + deploymentUnitName + ANSI_RESET);
                MicroProfileHealthLogger.LOGGER.warn("   ⚠ Skipping deployment context with null BeanManager: " + deploymentUnitName);
                continue;
            }
            
            // Store the context module for use in addHealthChecks
            currentContextModule = contextModule;
            
            // Create Instance from the context BeanManager
            System.out.println(ANSI_CYAN + "   Creating Instance from context BeanManager for: " + deploymentUnitName + ANSI_RESET);
            MicroProfileHealthLogger.LOGGER.warn("   Creating Instance from context BeanManager for: " + deploymentUnitName);
            instance = contextBeanManager.createInstance();
            System.out.println(ANSI_CYAN + "🛠 Created Instance: " + instance + ANSI_RESET);
            MicroProfileHealthLogger.LOGGER.warn("🛠 Created Instance: " + instance);
            
            System.out.println(ANSI_CYAN + "   Searching for health checks in module: " + contextModule.getName() + ANSI_RESET);
            MicroProfileHealthLogger.LOGGER.warn("   Searching for health checks in module: " + contextModule.getName());
            
            // Discover health checks for this deployment context
            System.out.println(ANSI_CYAN + "   [1/3] Searching for Liveness checks in " + deploymentUnitName + "..." + ANSI_RESET);
            MicroProfileHealthLogger.LOGGER.warn("   [1/3] Searching for Liveness checks in " + deploymentUnitName + "...");
            addHealthChecks(Liveness.Literal.INSTANCE, reporter::addLivenessCheck, livenessChecks);
            
            System.out.println(ANSI_CYAN + "   [2/3] Searching for Readiness checks in " + deploymentUnitName + "..." + ANSI_RESET);
            MicroProfileHealthLogger.LOGGER.warn("   [2/3] Searching for Readiness checks in " + deploymentUnitName + "...");
            addHealthChecks(Readiness.Literal.INSTANCE, reporter::addReadinessCheck, readinessChecks);
            
            System.out.println(ANSI_CYAN + "   [3/3] Searching for Startup checks in " + deploymentUnitName + "..." + ANSI_RESET);
            MicroProfileHealthLogger.LOGGER.warn("   [3/3] Searching for Startup checks in " + deploymentUnitName + "...");
            addHealthChecks(Startup.Literal.INSTANCE, reporter::addStartupCheck, startupChecks);
            
            System.out.println(ANSI_CYAN + "   Summary for " + deploymentUnitName + " (module: " + contextModule.getName() + "):" + ANSI_RESET);
            MicroProfileHealthLogger.LOGGER.warn("   Summary for " + deploymentUnitName + " (module: " + contextModule.getName() + "):");
            System.out.println(ANSI_CYAN + "     - Liveness checks found: " + livenessChecks.size() + ANSI_RESET);
            MicroProfileHealthLogger.LOGGER.warn("     - Liveness checks found: " + livenessChecks.size());
            System.out.println(ANSI_CYAN + "     - Readiness checks found: " + readinessChecks.size() + ANSI_RESET);
            MicroProfileHealthLogger.LOGGER.warn("     - Readiness checks found: " + readinessChecks.size());
            System.out.println(ANSI_CYAN + "     - Startup checks found: " + startupChecks.size() + ANSI_RESET);
            MicroProfileHealthLogger.LOGGER.warn("     - Startup checks found: " + startupChecks.size());
            
            // Check for default checks (only for this specific deployment, not global)
            Config config = ConfigProvider.getConfig(contextModule.getClassLoader());
            boolean disableDefaults = config.getOptionalValue(MP_HEALTH_DISABLE_DEFAULT_PROCEDURES, Boolean.class).orElse(false);
            
            if (readinessChecks.isEmpty() && !disableDefaults) {
                defaultReadinessCheck = new DefaultReadinessHealthCheck(contextModule.getName());
                reporter.addReadinessCheck(defaultReadinessCheck, contextModule.getClassLoader());
                System.out.println(ANSI_YELLOW + "⚡ Registered default Readiness check for: " + contextModule.getName() + ANSI_RESET);
                MicroProfileHealthLogger.LOGGER.warn("⚡ Registered default Readiness check for: " + contextModule.getName());
            }
            
            if (startupChecks.isEmpty() && !disableDefaults) {
                defaultStartupCheck = new DefaultStartupHealthCheck(contextModule.getName());
                reporter.addStartupCheck(defaultStartupCheck, contextModule.getClassLoader());
                System.out.println(ANSI_YELLOW + "⚡ Registered default Startup check for: " + contextModule.getName() + ANSI_RESET);
                MicroProfileHealthLogger.LOGGER.warn("⚡ Registered default Startup check for: " + contextModule.getName());
            }
        }
        
        reporter.setUserChecksProcessed(true);
        System.out.println(ANSI_CYAN + "================================================================================" + ANSI_RESET);
        MicroProfileHealthLogger.LOGGER.warn("================================================================================");
    }

    private void addHealthChecks(AnnotationLiteral qualifier,
                                 BiConsumer<HealthCheck, ClassLoader> healthFunction,
                                 List<HealthCheck> healthChecks) {

        System.out.println(ANSI_CYAN + "🔍 addHealthChecks() - Qualifier: " + qualifier + ANSI_RESET);
        MicroProfileHealthLogger.LOGGER.warn("🔍 addHealthChecks() - Qualifier: " + qualifier);
        System.out.println(ANSI_CYAN + "   Instance: " + instance + ANSI_RESET);
        MicroProfileHealthLogger.LOGGER.warn("   Instance: " + instance);
        System.out.println(ANSI_CYAN + "   Instance class: " + (instance != null ? instance.getClass().getName() : "null") + ANSI_RESET);
        MicroProfileHealthLogger.LOGGER.warn("   Instance class: " + (instance != null ? instance.getClass().getName() : "null"));
        System.out.println(ANSI_CYAN + "   Selecting HealthCheck.class with qualifier: " + qualifier + ANSI_RESET);
        MicroProfileHealthLogger.LOGGER.warn("   Selecting HealthCheck.class with qualifier: " + qualifier);
        
        
        Instance<HealthCheck> healthCheckInstance = instance.select(HealthCheck.class, qualifier);
        System.out.println(ANSI_CYAN + "   HealthCheck Instance: " + healthCheckInstance + ANSI_RESET);
        MicroProfileHealthLogger.LOGGER.warn("   HealthCheck Instance: " + healthCheckInstance);
        System.out.println(ANSI_CYAN + "   HealthCheck Instance class: " + (healthCheckInstance != null ? healthCheckInstance.getClass().getName() : "null") + ANSI_RESET);
        MicroProfileHealthLogger.LOGGER.warn("   HealthCheck Instance class: " + (healthCheckInstance != null ? healthCheckInstance.getClass().getName() : "null"));
        System.out.println(ANSI_CYAN + "   Is Unsatisfied: " + healthCheckInstance.isUnsatisfied() + ANSI_RESET);
        MicroProfileHealthLogger.LOGGER.warn("   Is Unsatisfied: " + healthCheckInstance.isUnsatisfied());
        System.out.println(ANSI_CYAN + "   Is Ambiguous: " + healthCheckInstance.isAmbiguous() + ANSI_RESET);
        MicroProfileHealthLogger.LOGGER.warn("   Is Ambiguous: " + healthCheckInstance.isAmbiguous());
        

        if (healthCheckInstance.isUnsatisfied()) {
            System.out.println(ANSI_YELLOW + "⚠ No health checks found for qualifier: " + qualifier + ANSI_RESET);
            MicroProfileHealthLogger.LOGGER.warn("⚠ No health checks found for qualifier: " + qualifier);
            System.out.println(ANSI_YELLOW + "   This BDA does not contain any beans annotated with " + qualifier + ANSI_RESET);
            MicroProfileHealthLogger.LOGGER.warn("   This BDA does not contain any beans annotated with " + qualifier);
            return;
        }

        int count = 0;
        for (HealthCheck hc : healthCheckInstance) {
            count++;
            String hcClassName = hc.getClass().getName();
            ClassLoader hcClassLoader = hc.getClass().getClassLoader();
            System.out.println(ANSI_GREEN + "✅ Found health check #" + count + ": " + hcClassName + ANSI_RESET);
            MicroProfileHealthLogger.LOGGER.warn("✅ Found health check #" + count + ": " + hcClassName);
            System.out.println(ANSI_GREEN + "   Health check classloader: " + hcClassLoader + ANSI_RESET);
            MicroProfileHealthLogger.LOGGER.warn("   Health check classloader: " + hcClassLoader);
            // Use currentContextModule if available, otherwise fall back to module
            Module moduleToUse = currentContextModule != null ? currentContextModule : module;
            System.out.println(ANSI_GREEN + "   Registering with module classloader: " + moduleToUse.getClassLoader() + ANSI_RESET);
            MicroProfileHealthLogger.LOGGER.warn("   Registering with module classloader: " + moduleToUse.getClassLoader());
            healthFunction.accept(hc, moduleToUse.getClassLoader());
            healthChecks.add(hc);
            System.out.println(ANSI_GREEN + "   ✓ Registered successfully" + ANSI_RESET);
            MicroProfileHealthLogger.LOGGER.warn("   ✓ Registered successfully");
        }
        System.out.println(ANSI_CYAN + "   Total health checks found for " + qualifier + ": " + count + ANSI_RESET);
        MicroProfileHealthLogger.LOGGER.warn("   Total health checks found for " + qualifier + ": " + count);
    }

    public void beforeShutdown(@Observes final BeforeShutdown bs) {
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
        System.out.println(ANSI_CYAN + "🛑 [beforeShutdown] All health checks removed" + ANSI_RESET);
        MicroProfileHealthLogger.LOGGER.warn("🛑 [beforeShutdown] All health checks removed");
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
        System.out.println(ANSI_YELLOW + "🚫 SmallRyeHealthReporter vetoed" + ANSI_RESET);
        MicroProfileHealthLogger.LOGGER.warn("🚫 SmallRyeHealthReporter vetoed");
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
