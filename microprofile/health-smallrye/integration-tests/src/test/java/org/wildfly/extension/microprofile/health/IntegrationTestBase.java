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

import org.junit.Assert;

import java.io.IOException;
import java.io.StringReader;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jboss.as.arquillian.container.ManagementClient;

/**
 * Base class for health check integration tests.
 * Provides utility methods for checking health endpoints.
 *
 * @author <a href="mailto:your.email@redhat.com">Your Name</a>
 */
public abstract class IntegrationTestBase {

    /**
     * Checks the global outcome of a health check operation via HTTP endpoint.
     *
     * @param managementClient the management client
     * @param operation the operation (check, check-live, check-ready, check-startup)
     * @param mustBeUP whether the status should be UP
     * @param probeName the name of the probe to check for (null to check global status only)
     * @throws IOException if there's an error communicating with the server
     */
    protected void checkGlobalOutcome(ManagementClient managementClient, String operation, boolean mustBeUP, String probeName) throws IOException {
        final String httpEndpoint;
        switch(operation) {
            case "check-live":
                httpEndpoint = "/health/live";
                break;
            case "check-ready":
                httpEndpoint = "/health/ready";
                break;
            case "check-startup":
            case "check-started":
                httpEndpoint = "/health/started";
                break;
            case "check":
            default:
                httpEndpoint = "/health";
                break;
        }
        final String healthURL = "http://" + managementClient.getMgmtAddress() + ":" + managementClient.getMgmtPort() + httpEndpoint;

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            CloseableHttpResponse resp = client.execute(new HttpGet(healthURL));
            Assert.assertEquals("Health endpoint should return " + (mustBeUP ? "200" : "503"),
                    mustBeUP ? 200 : 503, resp.getStatusLine().getStatusCode());

            String content = EntityUtils.toString(resp.getEntity());
            resp.close();

            try (JsonReader jsonReader = Json.createReader(new StringReader(content))) {
                JsonValue value = jsonReader.readValue();
                boolean isUp;
                JsonArray checksArray;
                
                if (value.getValueType() == JsonValue.ValueType.ARRAY) {
                    // Array format: [{"name": "...", "outcome": true/false}, ...]
                    JsonArray array = value.asJsonArray();
                    checksArray = array;
                    // Overall status is UP if all checks have outcome: true
                    isUp = true;
                    for (JsonValue checkValue : array) {
                        JsonObject checkObj = checkValue.asJsonObject();
                        if (checkObj.containsKey("outcome")) {
                            boolean outcome = checkObj.getBoolean("outcome");
                            if (!outcome) {
                                isUp = false;
                                break;
                            }
                        }
                    }
                } else if (value.getValueType() == JsonValue.ValueType.OBJECT) {
                    // Object format: {"status": "UP", "checks": [...]}
                    JsonObject payload = value.asJsonObject();
                    String status = payload.getString("status");
                    isUp = "UP".equals(status);
                    checksArray = payload.getJsonArray("checks");
                } else {
                    Assert.fail("Unexpected JSON type in health response: " + value.getValueType() + ", content: " + content);
                    return; // unreachable
                }
                
                Assert.assertEquals("Health status should be " + (mustBeUP ? "UP" : "DOWN"),
                        mustBeUP, isUp);

                if (probeName != null && checksArray != null) {
                    boolean found = false;
                    for (JsonValue checkValue : checksArray) {
                        JsonObject checkObj = checkValue.asJsonObject();
                        String name = checkObj.containsKey("name") ? checkObj.getString("name") : null;
                        if (probeName.equals(name)) {
                            found = true;
                            // Check outcome (boolean) or status (string)
                            if (checkObj.containsKey("outcome")) {
                                boolean outcome = checkObj.getBoolean("outcome");
                                Assert.assertEquals("Probe " + probeName + " should be " + (mustBeUP ? "UP" : "DOWN"),
                                        mustBeUP, outcome);
                            } else if (checkObj.containsKey("status")) {
                                String status = checkObj.getString("status");
                                Assert.assertEquals("Probe " + probeName + " should be " + (mustBeUP ? "UP" : "DOWN"),
                                        mustBeUP ? "UP" : "DOWN", status);
                            }
                            break;
                        }
                    }
                    if (!found) {
                        Assert.fail("Probe named " + probeName + " not found in " + content);
                    }
                }
            }
        }
    }

    /**
     * Gets the full health response JSON for inspection.
     *
     * @param managementClient the management client
     * @param endpoint the endpoint (/health, /health/live, /health/ready, /health/started)
     * @return the JSON object representing the health response
     * @throws IOException if there's an error communicating with the server
     */
    protected JsonObject getHealthResponse(ManagementClient managementClient, String endpoint) throws IOException {
        final String healthURL = "http://" + managementClient.getMgmtAddress() + ":" + managementClient.getMgmtPort() + endpoint;

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            CloseableHttpResponse resp = client.execute(new HttpGet(healthURL));
            Assert.assertNotNull("Response should not be null", resp);
            int statusCode = resp.getStatusLine().getStatusCode();
            String content = EntityUtils.toString(resp.getEntity());
            resp.close();

            try (JsonReader jsonReader = Json.createReader(new StringReader(content))) {
                JsonValue value = jsonReader.readValue();
                if (value.getValueType() == JsonValue.ValueType.OBJECT) {
                    return value.asJsonObject();
                } else if (value.getValueType() == JsonValue.ValueType.ARRAY) {
                    // If it's an array, wrap it in an object with a "checks" field for compatibility
                    JsonArray array = value.asJsonArray();
                    // Create a wrapper object with checks array and compute overall status
                    String overallStatus = "UP";
                    for (JsonValue checkValue : array) {
                        JsonObject checkObj = checkValue.asJsonObject();
                        if (checkObj.containsKey("outcome") && !checkObj.getBoolean("outcome")) {
                            overallStatus = "DOWN";
                            break;
                        } else if (checkObj.containsKey("status") && "DOWN".equals(checkObj.getString("status"))) {
                            overallStatus = "DOWN";
                            break;
                        }
                    }
                    // Return a wrapper object that mimics the object format
                    return Json.createObjectBuilder()
                            .add("status", overallStatus)
                            .add("checks", array)
                            .build();
                } else {
                    Assert.fail("Health endpoint returned unexpected JSON type: " + value.getValueType() + ", content: " + content);
                    return null; // unreachable
                }
            } catch (javax.json.stream.JsonParsingException e) {
                // If we get HTML or other non-JSON content, show what we got
                String contentPreview = content.length() > 200 ? content.substring(0, 200) + "..." : content;
                Assert.fail("Failed to parse JSON from health endpoint. URL: " + healthURL 
                        + ", Response status: " + statusCode 
                        + ", Content preview: " + contentPreview + ", Error: " + e.getMessage());
                return null; // unreachable
            }
        }
    }
}

