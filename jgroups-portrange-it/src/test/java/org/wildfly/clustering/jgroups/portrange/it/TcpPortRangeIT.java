/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2026, Red Hat, Inc., and individual contributors
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

package org.wildfly.clustering.jgroups.portrange.it;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.net.ServerSocket;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jgroups.JChannel;
import org.jgroups.protocols.TCP;
import org.jgroups.protocols.TP;
import org.jgroups.protocols.UDP;
import org.jgroups.stack.IpAddress;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Verifies, inside a managed WildFly container, that the {@code port_range} default change in
 * {@code io.github.martinhickson:jgroups} (4.2.21-bravura-2+) behaves as intended:
 *
 * <ul>
 *   <li>{@code TCP} defaults {@code port_range} to 0: binds exactly {@code bind_port} and fails fast if taken</li>
 *   <li>{@code UDP} keeps the legacy {@code TP} default of 10</li>
 *   <li>Explicitly configured {@code port_range} values win over the constructor default (injection ordering)</li>
 *   <li>Legacy fallback (probing {@code bind_port+1 .. bind_port+port_range}) still works when {@code port_range > 0}</li>
 * </ul>
 *
 * Internal state is verified by reflecting on the protected {@code TP.port_range} field, so the
 * tests observe the real field the transport reads, not just the getter.
 */
@RunWith(Arquillian.class)
public class TcpPortRangeIT {

    private static final String PKG = TcpPortRangeIT.class.getPackage().getName();

    @Deployment(testable = true)
    public static WebArchive deployment() {
        return ShrinkWrap.create(WebArchive.class, "portrange-it.war")
                .addPackage(TcpPortRangeIT.class.getPackage())
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsWebInfResource(String.format("%s/deployment/jboss-deployment-structure.xml", PKG.replace('.', '/')))
                .setWebXML(String.format("%s/deployment/web.xml", PKG.replace('.', '/')));
    }

    /** Reads the bound port of a connected channel from the transport's local physical address. */
    private static int boundPort(JChannel channel) throws Exception {
        TP transport = channel.getProtocolStack().getTransport();
        Field field = TP.class.getDeclaredField("local_physical_addr");
        field.setAccessible(true);
        IpAddress address = (IpAddress) field.get(transport);
        return address.getPort();
    }

    // ------------------------------------------------------------------ internal state inspection

    /** Reads the protected {@code TP.port_range} field of the given transport via reflection. */
    private static int portRangeField(TP transport) throws Exception {
        for (Class<?> clazz = transport.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
            try {
                Field field = clazz.getDeclaredField("port_range");
                field.setAccessible(true);
                return field.getInt(transport);
            } catch (NoSuchFieldException ignore) {
                // walk up the hierarchy
            }
        }
        throw new NoSuchFieldException("port_range not found on " + transport.getClass().getName());
    }

    // ------------------------------------------------------------------ default values

    @Test
    public void tcpDefaultsPortRangeToZero() throws Exception {
        TCP tcp = new TCP();
        assertEquals("TP.port_range field must be 0 on a freshly constructed TCP", 0, portRangeField(tcp));
        assertEquals("getPortRange() must agree with the field", 0, tcp.getPortRange());
    }

    @Test
    public void udpKeepsLegacyDefaultOfTen() throws Exception {
        UDP udp = new UDP();
        assertEquals("UDP must keep the legacy TP default of 10", 10, portRangeField(udp));
        assertEquals(10, udp.getPortRange());
    }

    // ------------------------------------------------------------------ configurability

    @Test
    public void configuredPortRangeWinsOverConstructorDefault() throws Exception {
        JChannel channel = null;
        try {
            channel = new JChannel(stream(stack(findFreePort(), 5))); // explicit port_range=5, transport-level
            channel.connect("portrange-it-configured");
            TCP tcp = channel.getProtocolStack().findProtocol(TCP.class);
            assertEquals("XML-configured port_range must override the constructor default", 5, portRangeField(tcp));
            assertEquals(5, tcp.getPortRange());
        } finally {
            closeQuietly(channel);
        }
    }

    // ------------------------------------------------------------------ binding behavior

    @Test
    public void defaultRangeBindsExactlyBindPortAndFailsFastWhenTaken() throws Exception {
        int port = findFreePort();
        JChannel first = null;
        try {
            first = new JChannel(stream(stack(port, null))); // default range (0)
            first.connect("portrange-it-exclusive");
            assertEquals("member must bind exactly bind_port", port, boundPort(first));

            JChannel second = new JChannel(stream(stack(port, null)));
            try {
                second.connect("portrange-it-exclusive");
                fail("Second member must fail fast when bind_port is taken and port_range=0");
            } catch (Exception expected) {
                // fail-fast is the expected outcome: no silent fallback to bind_port+1..+10
            } finally {
                closeQuietly(second);
            }
        } finally {
            closeQuietly(first);
        }
    }

    @Test
    public void explicitRangeStillFallsBackToAdjacentPort() throws Exception {
        int port = findFreePort();
        JChannel first = null, second = null;
        try {
            first = new JChannel(stream(stack(port, 5)));
            first.connect("portrange-it-fallback");
            assertEquals(port, boundPort(first));

            second = new JChannel(stream(stack(port, 5)));
            second.connect("portrange-it-fallback");
            int secondPort = boundPort(second);
            assertTrue("second member must land within [bind_port+1 .. bind_port+port_range] but bound " + secondPort,
                    secondPort > port && secondPort <= port + 5);
        } finally {
            closeQuietly(second);
            closeQuietly(first);
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Builds a minimal TCP stack XML. {@code range} is {@code null} to leave port_range at the default,
     * otherwise the explicit value is written into the transport configuration.
     */
    private static String stack(int bindPort, Integer range) {
        String rangeAttr = range == null ? "" : String.format(" port_range=\"%d\"", range);
        return String.format(
                "<config xmlns=\"urn:org:jgroups\">"
                        + "<TCP bind_port=\"%d\"%s/>"
                        + "<TCPPING initial_hosts=\"localhost[%d]\" port_range=\"0\"/>"
                        + "</config>",
                bindPort, rangeAttr, bindPort);
    }

    private static java.io.InputStream stream(String xml) {
        return new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static int findFreePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void closeQuietly(JChannel channel) {
        if (channel != null) {
            channel.close();
        }
    }
}
