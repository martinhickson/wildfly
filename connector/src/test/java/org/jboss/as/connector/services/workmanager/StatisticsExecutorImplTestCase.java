/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.connector.services.workmanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.as.threads.ManagedQueueExecutorService;
import org.junit.Test;

/**
 * Unit tests for {@link StatisticsExecutorImpl} after migration from {@code BlockingExecutor}.
 */
public class StatisticsExecutorImplTestCase {

    @Test
    public void testExecuteDelegatesToBackingExecutor() {
        final AtomicBoolean executed = new AtomicBoolean();
        final StatisticsExecutorImpl statisticsExecutor = new StatisticsExecutorImpl(command -> executed.set(true));

        statisticsExecutor.execute(() -> {
        });

        assertTrue(executed.get());
    }

    @Test
    public void testFreeThreadsForManagedQueueExecutorService() {
        final ManagedQueueExecutorService backing = managedQueueStub(10, 3);
        final StatisticsExecutorImpl statisticsExecutor = new StatisticsExecutorImpl(backing);

        assertEquals(7L, statisticsExecutor.getNumberOfFreeThreads());
    }

    @Test
    public void testFreeThreadsForUnknownExecutorReturnsZero() {
        final Executor backing = command -> {
        };
        final StatisticsExecutorImpl statisticsExecutor = new StatisticsExecutorImpl(backing);

        assertEquals(0L, statisticsExecutor.getNumberOfFreeThreads());
    }

    private static ManagedQueueExecutorService managedQueueStub(int maxThreads, int currentThreadCount) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                switch (method.getName()) {
                    case "getMaxThreads":
                    case "getCoreThreads":
                    case "getLargestPoolSize":
                        return maxThreads;
                    case "getCurrentThreadCount":
                    case "getLargestThreadCount":
                    case "getActiveCount":
                        return currentThreadCount;
                    case "execute":
                        ((Runnable) args[0]).run();
                        return null;
                    case "isBlocking":
                    case "isAllowCoreTimeout":
                    case "isShutdown":
                    case "isTerminated":
                    case "awaitTermination":
                        return false;
                    case "getKeepAlive":
                    case "getRejectedCount":
                    case "getTaskCount":
                    case "getCompletedTaskCount":
                        return 0L;
                    case "getQueueSize":
                        return 0;
                    case "shutdown":
                    case "shutdownNow":
                        return null;
                    case "equals":
                        return proxy == args[0];
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "toString":
                        return "ManagedQueueExecutorServiceStub";
                    default:
                        Class<?> returnType = method.getReturnType();
                        if (returnType == boolean.class) {
                            return false;
                        }
                        if (returnType == int.class) {
                            return 0;
                        }
                        if (returnType == long.class) {
                            return 0L;
                        }
                        return null;
                }
            }
        };
        return (ManagedQueueExecutorService) Proxy.newProxyInstance(
                ManagedQueueExecutorService.class.getClassLoader(),
                new Class<?>[] { ManagedQueueExecutorService.class },
                handler);
    }
}
