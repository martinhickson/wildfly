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

package org.wildfly.extension.microprofile.health.deployment;

import java.util.concurrent.CompletableFuture;

import javax.enterprise.inject.spi.BeanManager;

import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

final class BeanManagerFutureService implements Service<Void> {

    private final CompletableFuture<BeanManager> beanManagerFuture;
    private final InjectedValue<BeanManager> beanManager = new InjectedValue<>();

    BeanManagerFutureService(CompletableFuture<BeanManager> beanManagerFuture) {
        this.beanManagerFuture = beanManagerFuture;
    }

    Injector<BeanManager> getBeanManagerInjector() {
        return beanManager;
    }

    @Override
    public void start(StartContext context) {
        beanManagerFuture.complete(beanManager.getValue());
    }

    @Override
    public void stop(StopContext context) {
        beanManagerFuture.completeExceptionally(new IllegalStateException("BeanManager service stopped before it was available."));
    }

    @Override
    public Void getValue() {
        return null;
    }
}

