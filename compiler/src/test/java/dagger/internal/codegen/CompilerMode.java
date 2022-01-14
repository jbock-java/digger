/*
 * Copyright (C) 2017 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.internal.codegen;

import java.util.Arrays;
import java.util.List;

/** The configuration options for compiler modes. */
enum CompilerMode {
  DEFAULT_MODE,
  FAST_INIT_MODE("-Adagger.fastInit=enabled");

  /** Returns the compiler modes as a list of parameters for parameterized tests */
  static final List<Object[]> TEST_PARAMETERS =
      Arrays.asList(
          new Object[][]{{CompilerMode.DEFAULT_MODE}, {CompilerMode.FAST_INIT_MODE}});

  private final List<String> javacopts;

  CompilerMode(String... javacopts) {
    this.javacopts = Arrays.asList(javacopts);
  }

  /** Returns the javacopts for this compiler mode. */
  List<String> javacopts() {
    return javacopts;
  }

  /**
   * Returns a {@link JavaFileBuilder} that builds {@link javax.tools.JavaFileObject}s for this
   * mode.
   */
  JavaFileBuilder javaFileBuilder(String qualifiedName) {
    return new JavaFileBuilder(this, qualifiedName);
  }
}
