/*
 * (c) Copyright 2022 Carter Kozak
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.ckozak.speeddial.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation which may be applied to an interface that's often cast to from a supertype, to bypass poor
 * secondary_super_cache performance as described in
 * <a href="https://bugs.openjdk.org/browse/JDK-8180450">JDK-8180450</a>.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface SpeedDial {
    /**
     * The base type that's targeted for invocation. The specified type should require a cast for invocation.
     */
    Class<?> target() default Object.class;
    /**
     * One or more common implementations of the annotated method.
     * These will be checked via instanceof before casting to the annotated type.
     */
    Class<?>[] common();
}
