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
package org.apache.camel.impl.converter;

import java.lang.reflect.Method;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.TypeConverterSupport;

/**
 * A {@link TypeConverter} implementation which instantiates an object so that an instance method can be used as a type
 * converter
 */
public class InstanceMethodTypeConverter extends TypeConverterSupport {
    private final CachingInjector<?> injector;
    private final Method method;
    private final boolean useExchange;
    private final boolean allowNull;

    public InstanceMethodTypeConverter(CachingInjector<?> injector, Method method, TypeConverterRegistry registry,
                                       boolean allowNull) {
        this.injector = injector;
        this.method = method;
        this.useExchange = method.getParameterCount() == 2;
        this.allowNull = allowNull;
    }

    @Override
    public String toString() {
        return "InstanceMethodTypeConverter: " + method;
    }

    @Override
    public boolean allowNull() {
        return allowNull;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T convertTo(Class<T> type, Exchange exchange, Object value) {
        Object instance = injector.newInstance();
        if (instance == null) {
            throw new RuntimeCamelException("Could not instantiate an instance of: " + type.getCanonicalName());
        }
        Object answer = useExchange
                ? (T) ObjectHelper.invokeMethod(method, instance, value, exchange) : (T) ObjectHelper
                        .invokeMethod(method, instance, value);
        if (answer == null && allowNull) {
            answer = Void.class;
        }
        return (T) answer;
    }

}
