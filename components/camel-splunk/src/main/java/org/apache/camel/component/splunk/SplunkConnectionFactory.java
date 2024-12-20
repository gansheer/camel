/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.splunk;

import java.net.URLStreamHandler;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.splunk.HttpService;
import com.splunk.SSLSecurityProtocol;
import com.splunk.Service;
import com.splunk.ServiceArgs;
import org.apache.camel.CamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SplunkConnectionFactory {
    private static final Logger LOG = LoggerFactory.getLogger(SplunkConnectionFactory.class);

    private String host;
    private int port;
    private String scheme;
    private String app;
    private String owner;
    private String username;
    private String password;
    private String token;
    private int connectionTimeout;
    private boolean useSunHttpsHandler;
    private SSLSecurityProtocol sslProtocol;
    private boolean validateCertificates;
    private final Lock lock = new ReentrantLock();

    public SplunkConnectionFactory(final String host, final int port, final String username, final String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public SplunkConnectionFactory(final String username, final String password) {
        this(Service.DEFAULT_HOST, Service.DEFAULT_PORT, username, password);
    }

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public boolean isUseSunHttpsHandler() {
        return useSunHttpsHandler;
    }

    public void setUseSunHttpsHandler(boolean useSunHttpsHandler) {
        this.useSunHttpsHandler = useSunHttpsHandler;
    }

    public SSLSecurityProtocol getSslProtocol() {
        return sslProtocol;
    }

    public void setSslProtocol(SSLSecurityProtocol sslProtocol) {
        this.sslProtocol = sslProtocol;
    }

    public boolean isValidateCertificates() {
        return validateCertificates;
    }

    public void setValidateCertificates(boolean validateCertificates) {
        this.validateCertificates = validateCertificates;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Service createService(CamelContext camelContext) {
        lock.lock();
        try {
            final ServiceArgs args = new ServiceArgs();
            if (host != null) {
                args.setHost(host);
            }
            if (port > 0) {
                args.setPort(port);
            }
            if (scheme != null) {
                args.setScheme(scheme);
            }
            if (app != null) {
                args.setApp(app);
            }
            if (owner != null) {
                args.setOwner(owner);
            }
            if (username != null) {
                args.setUsername(username);
            }
            if (password != null && token == null) {
                args.setPassword(password);
            }
            if (token != null) {
                args.setToken(String.format("Bearer %s", token));
                args.remove("username");
                args.remove("password");
            }
            // useful in cases where you want to bypass app. servers https handling
            // (wls i'm looking at you)
            if (isUseSunHttpsHandler()) {
                String sunHandlerClassName = "sun.net.www.protocol.https.Handler";
                Class<URLStreamHandler> clazz
                        = camelContext.getClassResolver().resolveClass(sunHandlerClassName, URLStreamHandler.class);
                if (clazz != null) {
                    URLStreamHandler handler = camelContext.getInjector().newInstance(clazz);
                    args.setHTTPSHandler(handler);
                    LOG.debug("using the URLStreamHandler {} for {}", handler, args);
                } else {
                    LOG.warn("could not resolve and use the URLStreamHandler class '{}'", sunHandlerClassName);
                }
            }

            ExecutorService executor
                    = camelContext.getExecutorServiceManager().newSingleThreadExecutor(this, "DefaultSplunkConnectionFactory");

            Future<Service> future = executor.submit(new Callable<Service>() {
                public Service call() throws Exception {
                    if (Service.DEFAULT_SCHEME.equals(getScheme())) {
                        LOG.debug("Https in use. Setting SSL protocol to {} and sertificate validation to %s", getSslProtocol(),
                                isValidateCertificates());
                        HttpService.setValidateCertificates(isValidateCertificates());
                        HttpService.setSslSecurityProtocol(getSslProtocol());
                    }
                    return Service.connect(args);
                }
            });
            try {
                Service service = null;
                if (connectionTimeout > 0) {
                    service = future.get(connectionTimeout, TimeUnit.MILLISECONDS);
                } else {
                    service = future.get();
                }
                LOG.info("Successfully connected to Splunk");
                return service;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(
                        String.format("could not connect to Splunk Server @ %s:%d - %s", host, port, e.getMessage()), e);
            } catch (Exception e) {
                throw new RuntimeException(
                        String.format("could not connect to Splunk Server @ %s:%d - %s", host, port, e.getMessage()), e);
            } finally {
                camelContext.getExecutorServiceManager().shutdownNow(executor);
            }
        } finally {
            lock.unlock();
        }
    }
}
