/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.microprofile.opentracing;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.enterprise.util.Nonbinding;
import javax.interceptor.InterceptorBinding;

@InterceptorBinding
@Retention(RUNTIME)
@Target({ TYPE, METHOD })
public @interface Traced {

    /**
     * This method only modifies the default behavior when set to false.
     * Depending if Traced has been set on the class it behaves differently:
     *
     * <ul>
     * <li>If <code>@Traced</code> exists on the class then setting this flag to false
     * on a method will disable the span creation for the endpoint</li>
     * <li>If the class doesn't have the <code>@Traced</code> marker then the parent is ignored.</li>
     * </ul>
     *
     * @return should the method be traced or not.
     */
    @Nonbinding
    boolean value() default true;

    /**
     * Allows to customize the span name.
     *
     * @return the span name for the "current" endpoint.
     */
    @Nonbinding
    String operationName() default "";
}
