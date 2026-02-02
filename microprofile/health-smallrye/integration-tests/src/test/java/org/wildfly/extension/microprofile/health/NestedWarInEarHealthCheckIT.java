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

package org.wildfly.extension.microprofile.health;

import java.io.IOException;

import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.api.ContainerResource;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import javax.json.JsonObject;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Integration test to verify that health check annotations in nested WARs inside EARs
 * are properly handled.
 * 
 * This test verifies the behavior of health checks in nested WARs:
 * - Deploys an EAR containing a nested WAR with health check annotations
 * - Verifies whether health checks from nested WARs are discovered
 * - Tests the BeanManager service lookup mechanism for nested deployments
 * 
 * Note: Based on WildFly behavior, nested WARs may or may not be scanned depending on
 * the deployment processor configuration. This test verifies the actual behavior.
 *
 * @author <a href="mailto:your.email@redhat.com">Your Name</a>
 */
@RunWith(Arquillian.class)
@RunAsClient
public class NestedWarInEarHealthCheckIT extends IntegrationTestBase {

    private static final String EAR_NAME = "health-check-ear";
    private static final String NESTED_WAR_NAME = "health-check-war";

    @ContainerResource
    ManagementClient managementClient;

    @ArquillianResource
    private Deployer deployer;

    /**
     * Creates an EAR deployment containing a nested WAR with health checks.
     */
    @Deployment(name = EAR_NAME, managed = false)
    public static EnterpriseArchive createEarDeployment() {
        // Create the nested WAR with health checks
        // Note: MicroProfile Health API should be available from the server's module system
        // In WildFly, MP Health API is provided as a module, so we don't need to bundle it
        WebArchive nestedWar = ShrinkWrap.create(WebArchive.class, NESTED_WAR_NAME + ".war")
                .addClasses(
                        NestedWarLivenessCheck.class,
                        NestedWarReadinessCheck.class,
                        NestedWarStartupCheck.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                // Add a MANIFEST.MF that declares dependency on the MicroProfile Health API module
                .setManifest(new StringAsset("Dependencies: org.eclipse.microprofile.health.api\n"));

        // Create the EAR and add the nested WAR
        EnterpriseArchive ear = ShrinkWrap.create(EnterpriseArchive.class, EAR_NAME + ".ear")
                .addAsModule(nestedWar)
                .setApplicationXML(createApplicationXml())
                .addAsManifestResource(createJBossDeploymentStructure(), "jboss-deployment-structure.xml");

        return ear;
    }

    /**
     * Creates application.xml for the EAR deployment.
     */
    private static StringAsset createApplicationXml() {
        String applicationXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<application xmlns=\"http://java.sun.com/xml/ns/javaee\"\n" +
                "             xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "             xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/application_6.xsd\"\n" +
                "             version=\"6\">\n" +
                "    <module>\n" +
                "        <web>\n" +
                "            <web-uri>" + NESTED_WAR_NAME + ".war</web-uri>\n" +
                "            <context-root>/" + NESTED_WAR_NAME + "</context-root>\n" +
                "        </web>\n" +
                "    </module>\n" +
                "</application>";
        return new StringAsset(applicationXml);
    }

    /**
     * Creates jboss-deployment-structure.xml to control module dependencies.
     * This makes the arquillian-service dependency optional to prevent deployment failures.
     */
    private static StringAsset createJBossDeploymentStructure() {
        String jbossDeploymentStructure = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<jboss-deployment-structure xmlns=\"urn:jboss:deployment-structure:1.2\">\n" +
                "    <deployment>\n" +
                "        <dependencies>\n" +
                "            <module name=\"deployment.arquillian-service\" optional=\"true\" />\n" +
                "        </dependencies>\n" +
                "    </deployment>\n" +
                "    <sub-deployment name=\"" + NESTED_WAR_NAME + ".war\">\n" +
                "        <dependencies>\n" +
                "            <module name=\"deployment.arquillian-service\" optional=\"true\" />\n" +
                "        </dependencies>\n" +
                "    </sub-deployment>\n" +
                "</jboss-deployment-structure>";
        return new StringAsset(jbossDeploymentStructure);
    }

    @Test
    @InSequence(1)
    public void testHealthChecksBeforeDeployment() throws IOException {
        // Before deployment, health should be UP (WildFly has default health checks)
        checkGlobalOutcome(managementClient, "check-ready", true, null);
        checkGlobalOutcome(managementClient, "check-live", true, null);
    }

    @Test
    @InSequence(2)
    public void deployEar() {
        System.out.println("========================================");
        System.out.println("Deploying EAR: " + EAR_NAME);
        System.out.println("========================================");
        try {
            deployer.deploy(EAR_NAME);
            System.out.println("EAR deployment completed successfully");
        } catch (Exception e) {
            System.err.println("EAR deployment failed: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Deployment failed", e);
        }
    }

    @Test
    @InSequence(3)
    @OperateOnDeployment(EAR_NAME)
    public void testHealthChecksAfterEarDeployment() throws IOException {
        // After deployment, verify health check behavior
        // If nested WARs are scanned, these checks should be found
        // If nested WARs are excluded, these checks will not be found
        
        // Check if nested WAR health checks are present
        JsonObject liveResponse = getHealthResponse(managementClient, "/health/live");
        JsonObject readyResponse = getHealthResponse(managementClient, "/health/ready");
        
        // Debug: print the actual response to see what checks are present
        System.out.println("Live response: " + liveResponse);
        System.out.println("Ready response: " + readyResponse);
        
        boolean livenessFound = healthCheckExists(liveResponse, "nested-war-liveness");
        boolean readinessFound = healthCheckExists(readyResponse, "nested-war-readiness");
        
        // Nested WARs are now supported, so these checks should be found
        org.junit.Assert.assertTrue("Nested WAR liveness check should be found. Response: " + liveResponse, livenessFound);
        org.junit.Assert.assertTrue("Nested WAR readiness check should be found. Response: " + readyResponse, readinessFound);
        
        // Verify overall health status is UP
        org.junit.Assert.assertEquals("Overall health should be UP", "UP", liveResponse.getString("status"));
        org.junit.Assert.assertEquals("Overall health should be UP", "UP", readyResponse.getString("status"));
        
        // Check startup endpoint (uses /health/started, not /health/startup)
        JsonObject startupResponse = getHealthResponse(managementClient, "/health/started");
        boolean startupFound = healthCheckExists(startupResponse, "nested-war-startup");
        org.junit.Assert.assertTrue("Nested WAR startup check should be found", startupFound);
        org.junit.Assert.assertEquals("Overall health should be UP", "UP", startupResponse.getString("status"));
    }

    /**
     * Helper method to check if a health check exists in the response.
     */
    private boolean healthCheckExists(JsonObject response, String checkName) {
        javax.json.JsonArray checksArray = response.getJsonArray("checks");
        if (checksArray == null) {
            return false;
        }
        for (javax.json.JsonValue check : checksArray) {
            javax.json.JsonObject checkObj = check.asJsonObject();
            String name = checkObj.containsKey("name") ? checkObj.getString("name") : null;
            if (checkName.equals(name)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @InSequence(5)
    public void undeployEar() {
        deployer.undeploy(EAR_NAME);
    }

    @Test
    @InSequence(6)
    public void testHealthChecksAfterUndeployment() throws IOException {
        // After undeployment, health should still be UP (WildFly has default health checks)
        checkGlobalOutcome(managementClient, "check-ready", true, null);
    }

    /**
     * Asserts that a specific health check exists in the response with the expected status.
     */
    private void assertHealthCheckExists(JsonObject response, String checkName, String expectedStatus) {
        boolean found = false;
        for (javax.json.JsonValue check : response.getJsonArray("checks")) {
            javax.json.JsonObject checkObj = check.asJsonObject();
            if (checkName.equals(checkObj.getString("name"))) {
                found = true;
                org.junit.Assert.assertEquals("Health check " + checkName + " should be " + expectedStatus,
                        expectedStatus, checkObj.getString("status"));
                break;
            }
        }
        if (!found) {
            org.junit.Assert.fail("Health check named " + checkName + " not found in response: " + response);
        }
    }
}

