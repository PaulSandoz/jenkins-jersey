/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package com.cloudbees.jersey;

import com.google.common.base.Function;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.core.header.InBoundHeaders;
import com.sun.jersey.server.impl.ThreadLocalInvoker;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerResponse;
import com.sun.jersey.spi.container.ContainerResponseWriter;
import com.sun.jersey.spi.container.WebApplication;
import com.sun.jersey.spi.container.WebApplicationFactory;
import com.sun.jersey.spi.inject.SingletonTypeInjectableProvider;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.net.URI;
import java.security.Principal;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;

import hudson.model.Hudson;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * @author Paul Sandoz
 */
public class JenkinsJerseyContainer {

    private final WebApplication wa;

    private final String basePath;

    private final ThreadLocalInvoker<HttpServletRequest> requestInvoker =
            new ThreadLocalInvoker<HttpServletRequest>();

    private final ThreadLocalInvoker<HttpServletResponse> responseInvoker =
            new ThreadLocalInvoker<HttpServletResponse>();

    public JenkinsJerseyContainer(ServletContext sc, String basePath, ResourceConfig rc) {
        this.basePath = basePath;
        this.wa = WebApplicationFactory.createWebApplication();
        configure(sc, rc);
        wa.initiate(rc);
    }

    public void destroy() {
        wa.destroy();
    }
    
    protected static class ContextInjectableProvider<T> extends
            SingletonTypeInjectableProvider<Context, T> {
        protected ContextInjectableProvider(Type type, T instance) {
            super(type, instance);
        }
    }

    private void configure(ServletContext sc, ResourceConfig rc) {
        // TODO Hudson artifacts to bind?
        
        rc.getSingletons().add(new ContextInjectableProvider<HttpServletRequest>(
                HttpServletRequest.class,
                (HttpServletRequest)Proxy.newProxyInstance(
                        this.getClass().getClassLoader(),
                        new Class[] { HttpServletRequest.class },
                        requestInvoker)));

        rc.getSingletons().add(new ContextInjectableProvider<HttpServletResponse>(
                HttpServletResponse.class,
                (HttpServletResponse)Proxy.newProxyInstance(
                        this.getClass().getClassLoader(),
                        new Class[] { HttpServletResponse.class },
                        responseInvoker)));

        GenericEntity<ThreadLocal<HttpServletRequest>> requestThreadLocal =
                new GenericEntity<ThreadLocal<HttpServletRequest>>(requestInvoker.getImmutableThreadLocal()) {};
        rc.getSingletons().add(new ContextInjectableProvider<ThreadLocal<HttpServletRequest>>(
                requestThreadLocal.getType(), requestThreadLocal.getEntity()));

        GenericEntity<ThreadLocal<HttpServletResponse>> responseThreadLocal =
                new GenericEntity<ThreadLocal<HttpServletResponse>>(responseInvoker.getImmutableThreadLocal()) {};
        rc.getSingletons().add(new ContextInjectableProvider<ThreadLocal<HttpServletResponse>>(
                responseThreadLocal.getType(), responseThreadLocal.getEntity()));

        rc.getSingletons().add(new ContextInjectableProvider<ServletContext>(
                ServletContext.class,
                sc));

        rc.getClasses().add(JellyViewProcessor.class);
    }

    public void handle(final StaplerRequest req, final StaplerResponse rsp) throws IOException {
        try {
            requestInvoker.set(req);
            responseInvoker.set(rsp);

            _handle(req, rsp);
        } finally {
            requestInvoker.set(null);
            responseInvoker.set(null);
        }
    }

    public void _handle(final StaplerRequest req, final StaplerResponse rsp) throws IOException {
        URI baseUri = UriBuilder.fromUri(req.getRootPath()).path(basePath).path("/").build();
        URI requestUri = UriBuilder.fromUri(baseUri).path(req.getRestOfPath()).replaceQuery(req.getQueryString()).build();
        ContainerRequest cr = new ContainerRequest(wa, req.getMethod(), baseUri, requestUri, getHeaders(req), req.getInputStream());
        cr.setSecurityContext(new SecurityContext() {

            public Principal getUserPrincipal() {
                return req.getUserPrincipal();
            }

            public boolean isUserInRole(String role) {
                return req.isUserInRole(role);
            }

            public boolean isSecure() {
                return req.isSecure();
            }

            public String getAuthenticationScheme() {
                return req.getAuthType();
            }
        });
        final Writer w = new Writer(false, rsp);
        wa.handleRequest(cr, w);
    }

    private InBoundHeaders getHeaders(StaplerRequest request) {
        InBoundHeaders rh = new InBoundHeaders();
        for (Enumeration<String> names = request.getHeaderNames(); names.hasMoreElements();) {
            String name = names.nextElement();
            List<String> valueList = new LinkedList<String>();
            for (Enumeration<String> values = request.getHeaders(name); values.hasMoreElements();) {
                valueList.add(values.nextElement());
            }
            rh.put(name, valueList);
        }
        return rh;
    }

    private final static class Writer extends OutputStream implements ContainerResponseWriter {
        final HttpServletResponse response;

        final boolean useSetStatusOn404;

        ContainerResponse cResponse;

        long contentLength;

        OutputStream out;

        boolean statusAndHeadersWritten = false;

        Writer(boolean useSetStatusOn404, HttpServletResponse response) {
            this.useSetStatusOn404 = useSetStatusOn404;
            this.response = response;
        }

        public OutputStream writeStatusAndHeaders(long contentLength,
                ContainerResponse cResponse) throws IOException {
            this.contentLength = contentLength;
            this.cResponse = cResponse;
            this.statusAndHeadersWritten = false;
            return this;
        }

        public void finish() throws IOException {
            if (statusAndHeadersWritten)
                return;

            // Note that the writing of headers MUST be performed before
            // the invocation of sendError as on some Servlet implementations
            // modification of the response headers will have no effect
            // after the invocation of sendError.
            writeHeaders();

            if (cResponse.getStatus() >= 400) {
                if (useSetStatusOn404 && cResponse.getStatus() == 404) {
                    response.setStatus(cResponse.getStatus());
                } else {
                    final String reason = cResponse.getStatusType().getReasonPhrase();
                    if (reason == null || reason.isEmpty()) {
                        response.sendError(cResponse.getStatus());
                    } else {
                        response.sendError(cResponse.getStatus(), reason);
                    }
                }
            } else {
                response.setStatus(cResponse.getStatus());
            }
        }

        public void write(int b) throws IOException {
            initiate();
            out.write(b);
        }

        @Override
        public void write(byte b[]) throws IOException {
            if (b.length > 0) {
                initiate();
                out.write(b);
            }
        }

        @Override
        public void write(byte b[], int off, int len) throws IOException {
            if (len > 0) {
                initiate();
                out.write(b, off, len);
            }
        }

        @Override
        public void flush() throws IOException {
            writeStatusAndHeaders();
            if (out != null)
                out.flush();
        }

        @Override
        public void close() throws IOException {
            initiate();
            out.close();
        }

        void initiate() throws IOException {
            if (out == null) {
                writeStatusAndHeaders();
                out = response.getOutputStream();
            }
        }

        void writeStatusAndHeaders() {
            if (statusAndHeadersWritten)
                return;

            writeHeaders();
            response.setStatus(cResponse.getStatus());
            statusAndHeadersWritten = true;
        }

        void writeHeaders() {
            if (contentLength != -1 && contentLength < Integer.MAX_VALUE)
                response.setContentLength((int)contentLength);

            MultivaluedMap<String, Object> headers = cResponse.getHttpHeaders();
            for (Map.Entry<String, List<Object>> e : headers.entrySet()) {
                for (Object v : e.getValue()) {
                    response.addHeader(e.getKey(), ContainerResponse.getHeaderValue(v));
                }
            }
        }
    }

    public static JenkinsJerseyContainer create(final String basePath, final ResourceConfig rc) {
        return JenkinsJerseyContainer.execWithContextClassLoader(
                JenkinsJerseyContainer.class.getClassLoader(),
                new Function<Void, JenkinsJerseyContainer>() {
                    public JenkinsJerseyContainer apply(Void from) {
                        return new JenkinsJerseyContainer(Hudson.getInstance().servletContext, basePath, rc);
                    }
                });
    }

    private static <T, V> V execWithContextClassLoader(ClassLoader cl, Function<Void, V> f) {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(cl);
            return f.apply(null);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }
}
