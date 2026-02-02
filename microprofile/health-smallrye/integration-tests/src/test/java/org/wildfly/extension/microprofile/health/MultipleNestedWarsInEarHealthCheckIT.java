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
 * Integration test for multiple nested WARs in an EAR with health checks.
 * Tests that health checks from different nested WARs are discovered and registered correctly.
 *
 * @author <a href="mailto:your.email@redhat.com">Your Name</a>
 */
@RunWith(Arquillian.class)
@RunAsClient
public class MultipleNestedWarsInEarHealthCheckIT extends IntegrationTestBase {

    private static final String EAR_NAME = "multi-war-health-check-ear";
    private static final String WAR1_NAME = "health-check-war1";
    private static final String WAR2_NAME = "health-check-war2";

    @ContainerResource
    ManagementClient managementClient;

    @ArquillianResource
    private Deployer deployer;

    /**
     * Creates an EAR deployment containing two nested WARs with different health checks.
     */
    @Deployment(name = EAR_NAME, managed = false)
    public static EnterpriseArchive createEarDeployment() {
        // Create the first nested WAR with Liveness and Readiness checks
        WebArchive war1 = ShrinkWrap.create(WebArchive.class, WAR1_NAME + ".war")
                .addClasses(
                        War1LivenessCheck.class,
                        War1ReadinessCheck.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .setManifest(new StringAsset("Dependencies: org.eclipse.microprofile.health.api\n"));

        // Create the second nested WAR with Readiness and Startup checks
        WebArchive war2 = ShrinkWrap.create(WebArchive.class, WAR2_NAME + ".war")
                .addClasses(
                        War2ReadinessCheck.class,
                        War2StartupCheck.class)
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .setManifest(new StringAsset("Dependencies: org.eclipse.microprofile.health.api\n"));

        // Create the EAR and add both nested WARs
        EnterpriseArchive ear = ShrinkWrap.create(EnterpriseArchive.class, EAR_NAME + ".ear")
                .addAsModule(war1)
                .addAsModule(war2)
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
                "            <web-uri>" + WAR1_NAME + ".war</web-uri>\n" +
                "            <context-root>/" + WAR1_NAME + "</context-root>\n" +
                "        </web>\n" +
                "    </module>\n" +
                "    <module>\n" +
                "        <web>\n" +
                "            <web-uri>" + WAR2_NAME + ".war</web-uri>\n" +
                "            <context-root>/" + WAR2_NAME + "</context-root>\n" +
                "        </web>\n" +
                "    </module>\n" +
                "</application>";
        return new StringAsset(applicationXml);
    }

    /**
     * Creates jboss-deployment-structure.xml to control module dependencies.
     */
    private static StringAsset createJBossDeploymentStructure() {
        String jbossDeploymentStructure = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<jboss-deployment-structure xmlns=\"urn:jboss:deployment-structure:1.2\">\n" +
                "    <deployment>\n" +
                "        <dependencies>\n" +
                "            <module name=\"deployment.arquillian-service\" optional=\"true\" />\n" +
                "        </dependencies>\n" +
                "    </deployment>\n" +
                "    <sub-deployment name=\"" + WAR1_NAME + ".war\">\n" +
                "        <dependencies>\n" +
                "            <module name=\"deployment.arquillian-service\" optional=\"true\" />\n" +
                "        </dependencies>\n" +
                "    </sub-deployment>\n" +
                "    <sub-deployment name=\"" + WAR2_NAME + ".war\">\n" +
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
        System.out.println("Deploying EAR with multiple WARs: " + EAR_NAME);
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
    public void testHealthChecksFromMultipleWars() throws IOException {
        // After deployment, verify health checks from both nested WARs are present
        
        // Check liveness endpoint - should have check from WAR1
        JsonObject liveResponse = getHealthResponse(managementClient, "/health/live");
        System.out.println("Live response: " + liveResponse);
        boolean war1LivenessFound = healthCheckExists(liveResponse, "war1-liveness");
        org.junit.Assert.assertTrue("WAR1 liveness check should be found. Response: " + liveResponse, war1LivenessFound);
        
        // Check readiness endpoint - should have checks from both WAR1 and WAR2
        JsonObject readyResponse = getHealthResponse(managementClient, "/health/ready");
        System.out.println("Ready response: " + readyResponse);
        boolean war1ReadinessFound = healthCheckExists(readyResponse, "war1-readiness");
        boolean war2ReadinessFound = healthCheckExists(readyResponse, "war2-readiness");
        org.junit.Assert.assertTrue("WAR1 readiness check should be found. Response: " + readyResponse, war1ReadinessFound);
        org.junit.Assert.assertTrue("WAR2 readiness check should be found. Response: " + readyResponse, war2ReadinessFound);
        
        // Check startup endpoint - should have check from WAR2
        JsonObject startupResponse = getHealthResponse(managementClient, "/health/started");
        System.out.println("Startup response: " + startupResponse);
        boolean war2StartupFound = healthCheckExists(startupResponse, "war2-startup");
        org.junit.Assert.assertTrue("WAR2 startup check should be found. Response: " + startupResponse, war2StartupFound);
        
        // Verify overall health status is UP
        org.junit.Assert.assertEquals("Overall liveness health should be UP", "UP", liveResponse.getString("status"));
        org.junit.Assert.assertEquals("Overall readiness health should be UP", "UP", readyResponse.getString("status"));
        org.junit.Assert.assertEquals("Overall startup health should be UP", "UP", startupResponse.getString("status"));
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

}

