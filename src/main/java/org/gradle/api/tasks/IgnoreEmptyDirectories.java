/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.tasks;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import de.thetaphi.forbiddenapis.gradle.CheckForbiddenApis;

/**
 * Attached to an input property to specify that directories should be ignored
 * when snapshotting inputs. Files within directories and subdirectories will be
 * snapshot, but the directories themselves will be ignored. Empty directories,
 * and directories that contain only empty directories will have no effect on the
 * resulting snapshot.
 * <p>Copy of Gradle 6.8 annotation to allow compilation of {@link CheckForbiddenApis}.
 * It is excluded from the JAR file (runtime annotations are still optional at runtime).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface IgnoreEmptyDirectories {
}
