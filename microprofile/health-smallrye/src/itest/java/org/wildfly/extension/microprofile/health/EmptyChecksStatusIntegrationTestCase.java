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

import static org.jboss.as.controller.PathElement.pathElement;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.wildfly.extension.microprofile.health.MicroProfileHealthExtension.SUBSYSTEM_NAME;
import static org.wildfly.extension.microprofile.health.MicroProfileHealthSubsystemDefinition.*;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.KernelServicesBuilder;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 * Integration test for empty checks status configuration attributes.
 * Tests that empty-liveness-checks-status, empty-readiness-checks-status,
 * and empty-startup-checks-status can be configured and their values are properly handled.
 *
 * @author <a href="mailto:your.email@redhat.com">Your Name</a>
 */
public class EmptyChecksStatusIntegrationTestCase extends AbstractSubsystemBaseTest {

    public EmptyChecksStatusIntegrationTestCase() {
        super(SUBSYSTEM_NAME, new MicroProfileHealthExtension());
    }

    @Override
    protected String getSubsystemXml() throws Exception {
        return readResource("subsystem_3_0.xml");
    }

    @Override
    protected AdditionalInitialization createAdditionalInitialization() {
        return AdditionalInitialization.withCapabilities(
                org.jboss.as.weld.Capabilities.WELD_CAPABILITY_NAME,
                "org.wildfly.management.executor",
                "org.wildfly.management.http.extensible",
                HEALTH_HTTP_CONTEXT_CAPABILITY,
                HEALTH_SERVER_PROBE_CAPABILITY);
    }

    /**
     * Test that empty-liveness-checks-status can be set to UP
     */
    @Test
    public void testEmptyLivenessChecksStatusUP() throws Exception {
        KernelServices services = createKernelServicesBuilder(createAdditionalInitialization())
                .setSubsystemXmlResource("subsystem_3_0.xml")
                .build();

        PathAddress subsystemAddress = PathAddress.pathAddress(pathElement(SUBSYSTEM, SUBSYSTEM_NAME));
        ModelNode operation = Util.getWriteAttributeOperation(subsystemAddress, EMPTY_LIVENESS_CHECKS_STATUS.getName(), "UP");
        ModelNode result = services.executeOperation(operation);
        Assert.assertEquals("Operation should succeed", ModelNode.SUCCESS, result.get("outcome").asString());

        ModelNode readAttribute = Util.getReadAttributeOperation(subsystemAddress, EMPTY_LIVENESS_CHECKS_STATUS.getName());
        ModelNode readResult = services.executeOperation(readAttribute);
        Assert.assertEquals("Should read attribute successfully", ModelNode.SUCCESS, readResult.get("outcome").asString());
        Assert.assertEquals("Value should be UP", "UP", readResult.get("result").asString());
    }

    /**
     * Test that empty-liveness-checks-status can be set to DOWN
     */
    @Test
    public void testEmptyLivenessChecksStatusDOWN() throws Exception {
        KernelServices services = createKernelServicesBuilder(createAdditionalInitialization())
                .setSubsystemXmlResource("subsystem_3_0.xml")
                .build();

        PathAddress subsystemAddress = PathAddress.pathAddress(pathElement(SUBSYSTEM, SUBSYSTEM_NAME));
        ModelNode operation = Util.getWriteAttributeOperation(subsystemAddress, EMPTY_LIVENESS_CHECKS_STATUS.getName(), "DOWN");
        ModelNode result = services.executeOperation(operation);
        Assert.assertEquals("Operation should succeed", ModelNode.SUCCESS, result.get("outcome").asString());

        ModelNode readAttribute = Util.getReadAttributeOperation(subsystemAddress, EMPTY_LIVENESS_CHECKS_STATUS.getName());
        ModelNode readResult = services.executeOperation(readAttribute);
        Assert.assertEquals("Should read attribute successfully", ModelNode.SUCCESS, readResult.get("outcome").asString());
        Assert.assertEquals("Value should be DOWN", "DOWN", readResult.get("result").asString());
    }

    /**
     * Test that empty-readiness-checks-status can be set to UP
     */
    @Test
    public void testEmptyReadinessChecksStatusUP() throws Exception {
        KernelServices services = createKernelServicesBuilder(createAdditionalInitialization())
                .setSubsystemXmlResource("subsystem_3_0.xml")
                .build();

        PathAddress subsystemAddress = PathAddress.pathAddress(pathElement(SUBSYSTEM, SUBSYSTEM_NAME));
        ModelNode operation = Util.getWriteAttributeOperation(subsystemAddress, EMPTY_READINESS_CHECKS_STATUS.getName(), "UP");
        ModelNode result = services.executeOperation(operation);
        Assert.assertEquals("Operation should succeed", ModelNode.SUCCESS, result.get("outcome").asString());

        ModelNode readAttribute = Util.getReadAttributeOperation(subsystemAddress, EMPTY_READINESS_CHECKS_STATUS.getName());
        ModelNode readResult = services.executeOperation(readAttribute);
        Assert.assertEquals("Should read attribute successfully", ModelNode.SUCCESS, readResult.get("outcome").asString());
        Assert.assertEquals("Value should be UP", "UP", readResult.get("result").asString());
    }

    /**
     * Test that empty-readiness-checks-status can be set to DOWN
     */
    @Test
    public void testEmptyReadinessChecksStatusDOWN() throws Exception {
        KernelServices services = createKernelServicesBuilder(createAdditionalInitialization())
                .setSubsystemXmlResource("subsystem_3_0.xml")
                .build();

        PathAddress subsystemAddress = PathAddress.pathAddress(pathElement(SUBSYSTEM, SUBSYSTEM_NAME));
        ModelNode operation = Util.getWriteAttributeOperation(subsystemAddress, EMPTY_READINESS_CHECKS_STATUS.getName(), "DOWN");
        ModelNode result = services.executeOperation(operation);
        Assert.assertEquals("Operation should succeed", ModelNode.SUCCESS, result.get("outcome").asString());

        ModelNode readAttribute = Util.getReadAttributeOperation(subsystemAddress, EMPTY_READINESS_CHECKS_STATUS.getName());
        ModelNode readResult = services.executeOperation(readAttribute);
        Assert.assertEquals("Should read attribute successfully", ModelNode.SUCCESS, readResult.get("outcome").asString());
        Assert.assertEquals("Value should be DOWN", "DOWN", readResult.get("result").asString());
    }

    /**
     * Test that empty-startup-checks-status can be set to UP
     */
    @Test
    public void testEmptyStartupChecksStatusUP() throws Exception {
        KernelServices services = createKernelServicesBuilder(createAdditionalInitialization())
                .setSubsystemXmlResource("subsystem_3_0.xml")
                .build();

        PathAddress subsystemAddress = PathAddress.pathAddress(pathElement(SUBSYSTEM, SUBSYSTEM_NAME));
        ModelNode operation = Util.getWriteAttributeOperation(subsystemAddress, EMPTY_STARTUP_CHECKS_STATUS.getName(), "UP");
        ModelNode result = services.executeOperation(operation);
        Assert.assertEquals("Operation should succeed", ModelNode.SUCCESS, result.get("outcome").asString());

        ModelNode readAttribute = Util.getReadAttributeOperation(subsystemAddress, EMPTY_STARTUP_CHECKS_STATUS.getName());
        ModelNode readResult = services.executeOperation(readAttribute);
        Assert.assertEquals("Should read attribute successfully", ModelNode.SUCCESS, readResult.get("outcome").asString());
        Assert.assertEquals("Value should be UP", "UP", readResult.get("result").asString());
    }

    /**
     * Test that empty-startup-checks-status can be set to DOWN
     */
    @Test
    public void testEmptyStartupChecksStatusDOWN() throws Exception {
        KernelServices services = createKernelServicesBuilder(createAdditionalInitialization())
                .setSubsystemXmlResource("subsystem_3_0.xml")
                .build();

        PathAddress subsystemAddress = PathAddress.pathAddress(pathElement(SUBSYSTEM, SUBSYSTEM_NAME));
        ModelNode operation = Util.getWriteAttributeOperation(subsystemAddress, EMPTY_STARTUP_CHECKS_STATUS.getName(), "DOWN");
        ModelNode result = services.executeOperation(operation);
        Assert.assertEquals("Operation should succeed", ModelNode.SUCCESS, result.get("outcome").asString());

        ModelNode readAttribute = Util.getReadAttributeOperation(subsystemAddress, EMPTY_STARTUP_CHECKS_STATUS.getName());
        ModelNode readResult = services.executeOperation(readAttribute);
        Assert.assertEquals("Should read attribute successfully", ModelNode.SUCCESS, readResult.get("outcome").asString());
        Assert.assertEquals("Value should be DOWN", "DOWN", readResult.get("result").asString());
    }

    /**
     * Test that all three empty check status attributes can be configured together
     */
    @Test
    public void testAllEmptyChecksStatusAttributes() throws Exception {
        KernelServices services = createKernelServicesBuilder(createAdditionalInitialization())
                .setSubsystemXmlResource("subsystem_3_0.xml")
                .build();

        PathAddress subsystemAddress = PathAddress.pathAddress(pathElement(SUBSYSTEM, SUBSYSTEM_NAME));

        // Set all three attributes
        ModelNode operation1 = Util.getWriteAttributeOperation(subsystemAddress, EMPTY_LIVENESS_CHECKS_STATUS.getName(), "DOWN");
        ModelNode operation2 = Util.getWriteAttributeOperation(subsystemAddress, EMPTY_READINESS_CHECKS_STATUS.getName(), "UP");
        ModelNode operation3 = Util.getWriteAttributeOperation(subsystemAddress, EMPTY_STARTUP_CHECKS_STATUS.getName(), "DOWN");

        Assert.assertEquals("Liveness operation should succeed", ModelNode.SUCCESS, services.executeOperation(operation1).get("outcome").asString());
        Assert.assertEquals("Readiness operation should succeed", ModelNode.SUCCESS, services.executeOperation(operation2).get("outcome").asString());
        Assert.assertEquals("Startup operation should succeed", ModelNode.SUCCESS, services.executeOperation(operation3).get("outcome").asString());

        // Verify all values
        Assert.assertEquals("DOWN", services.executeOperation(Util.getReadAttributeOperation(subsystemAddress, EMPTY_LIVENESS_CHECKS_STATUS.getName())).get("result").asString());
        Assert.assertEquals("UP", services.executeOperation(Util.getReadAttributeOperation(subsystemAddress, EMPTY_READINESS_CHECKS_STATUS.getName())).get("result").asString());
        Assert.assertEquals("DOWN", services.executeOperation(Util.getReadAttributeOperation(subsystemAddress, EMPTY_STARTUP_CHECKS_STATUS.getName())).get("result").asString());
    }

    /**
     * Test that invalid values are rejected
     */
    @Test
    public void testInvalidEmptyChecksStatusValue() throws Exception {
        KernelServices services = createKernelServicesBuilder(createAdditionalInitialization())
                .setSubsystemXmlResource("subsystem_3_0.xml")
                .build();

        PathAddress subsystemAddress = PathAddress.pathAddress(pathElement(SUBSYSTEM, SUBSYSTEM_NAME));
        ModelNode operation = Util.getWriteAttributeOperation(subsystemAddress, EMPTY_LIVENESS_CHECKS_STATUS.getName(), "INVALID");
        ModelNode result = services.executeOperation(operation);
        Assert.assertEquals("Operation should fail for invalid value", "failed", result.get("outcome").asString());
    }

}

