/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.hibernate.it.ehcache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.importer.ZipImporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Verifies Hibernate second-level cache backed by EhCache via JCache on WildFly.
 */
@RunWith(Arquillian.class)
public class HibernateEhcacheSecondLevelCacheTestCase {

    private static final String ARCHIVE_NAME = "hibernate-it";

    @Deployment
    public static Archive<?> deploy() {
        WebArchive war = ShrinkWrap.create(WebArchive.class, ARCHIVE_NAME + ".war");
        war.addClasses(
                SecondLevelCacheBean.class,
                Student.class
        );
        war.addAsResource(HibernateEhcacheSecondLevelCacheTestCase.class.getPackage(), "persistence.xml", "META-INF/persistence.xml");
        war.addAsResource(HibernateEhcacheSecondLevelCacheTestCase.class.getPackage(), "ehcache.xml", "ehcache.xml");
        war.addAsWebInfResource(HibernateEhcacheSecondLevelCacheTestCase.class.getPackage(), "jboss-deployment-structure.xml", "jboss-deployment-structure.xml");

        File libDir = new File("target/test-libs");
        if (libDir.isDirectory()) {
            File[] libs = libDir.listFiles((dir, name) -> name.endsWith(".jar"));
            if (libs != null) {
                for (File lib : libs) {
                    war.addAsLibraries(ShrinkWrap.create(ZipImporter.class, lib.getName())
                            .importFrom(lib)
                            .as(JavaArchive.class));
                }
            }
        }

        return war;
    }

    @ArquillianResource
    private InitialContext initialContext;

    @After
    public void tearDown() throws NamingException {
        lookup("SecondLevelCacheBean", SecondLevelCacheBean.class).cleanup();
    }

    @Test
    public void testSecondLevelCacheUsesEhcacheViaJCache() throws Exception {
        SecondLevelCacheBean cacheBean = lookup("SecondLevelCacheBean", SecondLevelCacheBean.class);

        Student created = cacheBean.createStudent("MADHUMITA", "SADHUKHAN", "Brno, CZ");
        assertNotNull(created.getId());

        Student cached = cacheBean.getStudent(created.getId());
        assertEquals("MADHUMITA", cached.getFirstName());

        cacheBean.updateFirstNameViaJdbc(created.getId(), "hacked");

        Student fromCache = cacheBean.getStudent(created.getId());
        assertEquals("Student first name should come from EhCache second-level cache",
                "MADHUMITA", fromCache.getFirstName());

        cacheBean.clearSecondLevelCache();

        Student fromDatabase = cacheBean.getStudent(created.getId());
        assertEquals("Student first name should come from the database after cache eviction",
                "hacked", fromDatabase.getFirstName());

        assertTrue("Expected second-level cache puts while loading entities",
                cacheBean.getSecondLevelCachePutCount() > 0);
    }

    private <T> T lookup(String beanName, Class<T> viewType) throws NamingException {
        return viewType.cast(initialContext.lookup(
                "java:global/" + ARCHIVE_NAME + "/" + beanName + "!" + viewType.getName()));
    }
}
