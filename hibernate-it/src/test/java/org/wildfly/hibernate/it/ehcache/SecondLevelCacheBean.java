/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.hibernate.it.ehcache;

import java.sql.Connection;
import java.sql.PreparedStatement;

import javax.sql.DataSource;

import jakarta.annotation.Resource;
import jakarta.ejb.Stateful;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.hibernate.Session;
import org.hibernate.stat.Statistics;

@Stateful
public class SecondLevelCacheBean {

    @PersistenceContext(unitName = "hibernate-it")
    private EntityManager entityManager;

    @Resource(lookup = "java:jboss/datasources/ExampleDS")
    private DataSource dataSource;

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Student createStudent(String firstName, String lastName, String address) {
        Student student = new Student();
        student.setFirstName(firstName);
        student.setLastName(lastName);
        student.setAddress(address);
        entityManager.persist(student);
        entityManager.flush();
        return student;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Student getStudent(long id) {
        return entityManager.find(Student.class, id);
    }

    @TransactionAttribute(TransactionAttributeType.NEVER)
    public void updateFirstNameViaJdbc(long id, String firstName) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "update student_it set first_name = ? where id = ?")) {
            statement.setString(1, firstName);
            statement.setLong(2, id);
            statement.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to update student via JDBC", e);
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void clearSecondLevelCache() {
        entityManager.getEntityManagerFactory().getCache().evictAll();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public long getSecondLevelCachePutCount() {
        Statistics statistics = entityManager.unwrap(Session.class).getSessionFactory().getStatistics();
        return statistics.getSecondLevelCachePutCount();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void cleanup() {
        entityManager.createQuery("delete from Student").executeUpdate();
    }
}
