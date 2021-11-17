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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.Compilers.compilerWithOptions;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ElidedFactoriesTest {
  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public ElidedFactoriesTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void simpleComponent() {
    JavaFileObject injectedType =
        JavaFileObjects.forSourceLines(
            "test.InjectedType",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "final class InjectedType {",
            "  @Inject InjectedType() {}",
            "}");

    JavaFileObject dependsOnInjected =
        JavaFileObjects.forSourceLines(
            "test.InjectedType",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "",
            "final class DependsOnInjected {",
            "  @Inject DependsOnInjected(InjectedType injected) {}",
            "}");
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface SimpleComponent {",
            "  DependsOnInjected dependsOnInjected();",
            "}");
    List<String> generatedComponent = new ArrayList<>();
    Collections.addAll(generatedComponent,
        GeneratedLines.generatedImportsIndividual());
    Collections.addAll(generatedComponent,
        GeneratedLines.generatedAnnotationsIndividual());
    Collections.addAll(generatedComponent,
        "final class DaggerSimpleComponent implements SimpleComponent {",
        "  private final DaggerSimpleComponent simpleComponent = this;",
        "",
        "  private DaggerSimpleComponent() {",
        "  }",
        "",
        "  public static Builder builder() {",
        "    return new Builder();",
        "  }",
        "",
        "  public static SimpleComponent create() {",
        "    return new Builder().build();",
        "  }",
        "",
        "  @Override",
        "  public DependsOnInjected dependsOnInjected() {",
        "    return new DependsOnInjected(new InjectedType());",
        "  }",
        "",
        "  static final class Builder {",
        "    private Builder() {",
        "    }",
        "",
        "    public SimpleComponent build() {",
        "      return new DaggerSimpleComponent();",
        "    }",
        "  }",
        "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(injectedType, dependsOnInjected, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .containsLines(generatedComponent);
  }

  @Test
  public void simpleComponent_injectsProviderOf_dependsOnScoped() {
    JavaFileObject scopedType =
        JavaFileObjects.forSourceLines(
            "test.ScopedType",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "import jakarta.inject.Singleton;",
            "",
            "@Singleton",
            "final class ScopedType {",
            "  @Inject ScopedType() {}",
            "}");

    JavaFileObject dependsOnScoped =
        JavaFileObjects.forSourceLines(
            "test.ScopedType",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "import jakarta.inject.Provider;",
            "",
            "final class DependsOnScoped {",
            "  @Inject DependsOnScoped(ScopedType scoped) {}",
            "}");

    JavaFileObject needsProvider =
        JavaFileObjects.forSourceLines(
            "test.NeedsProvider",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "import jakarta.inject.Provider;",
            "",
            "class NeedsProvider {",
            "  @Inject NeedsProvider(Provider<DependsOnScoped> provider) {}",
            "}");
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Singleton;",
            "",
            "@Singleton",
            "@Component",
            "interface SimpleComponent {",
            "  NeedsProvider needsProvider();",
            "}");
    List<String> generatedComponent;
    if (compilerMode == CompilerMode.FAST_INIT_MODE) {
      generatedComponent = new ArrayList<>();
      Collections.addAll(generatedComponent,
          "package test;");
      Collections.addAll(generatedComponent,
          GeneratedLines.generatedImportsIndividual(
              "import dagger.internal.DoubleCheck;",
              "import dagger.internal.MemoizedSentinel;",
              "import jakarta.inject.Provider;"));
      Collections.addAll(generatedComponent,
          GeneratedLines.generatedAnnotationsIndividual());
      Collections.addAll(generatedComponent,
          "final class DaggerSimpleComponent implements SimpleComponent {",
          "  private final DaggerSimpleComponent simpleComponent = this;",
          "",
          "  private volatile Object scopedType = new MemoizedSentinel();",
          "",
          "  private volatile Provider<DependsOnScoped> dependsOnScopedProvider;",
          "",
          "  private DaggerSimpleComponent() {",
          "  }",
          "",
          "  public static Builder builder() {",
          "    return new Builder();",
          "  }",
          "",
          "  public static SimpleComponent create() {",
          "    return new Builder().build();",
          "  }",
          "",
          "  private ScopedType scopedType() {",
          "    Object local = scopedType;",
          "    if (local instanceof MemoizedSentinel) {",
          "      synchronized (local) {",
          "        local = scopedType;",
          "        if (local instanceof MemoizedSentinel) {",
          "          local = new ScopedType();",
          "          scopedType = DoubleCheck.reentrantCheck(scopedType, local);",
          "        }",
          "      }",
          "    }",
          "    return (ScopedType) local;",
          "  }",
          "",
          "  private DependsOnScoped dependsOnScoped() {",
          "    return new DependsOnScoped(scopedType());",
          "  }",
          "",
          "  private Provider<DependsOnScoped> dependsOnScopedProvider() {",
          "    Object local = dependsOnScopedProvider;",
          "    if (local == null) {",
          "      local = new SwitchingProvider<>(simpleComponent, 0);",
          "      dependsOnScopedProvider = (Provider<DependsOnScoped>) local;",
          "    }",
          "    return (Provider<DependsOnScoped>) local;",
          "  }",
          "",
          "  @Override",
          "  public NeedsProvider needsProvider() {",
          "    return new NeedsProvider(dependsOnScopedProvider());",
          "  }",
          "",
          "  static final class Builder {",
          "    private Builder() {",
          "    }",
          "",
          "    public SimpleComponent build() {",
          "      return new DaggerSimpleComponent();",
          "    }",
          "  }",
          "",
          "  private static final class SwitchingProvider<T> implements Provider<T> {",
          "    private final DaggerSimpleComponent simpleComponent;",
          "",
          "    private final int id;",
          "",
          "    SwitchingProvider(DaggerSimpleComponent simpleComponent, int id) {",
          "      this.simpleComponent = simpleComponent;",
          "      this.id = id;",
          "    }",
          "",
          "    @SuppressWarnings(\"unchecked\")",
          "    @Override",
          "    public T get() {",
          "      switch (id) {",
          "        case 0: // test.DependsOnScoped ",
          "        return (T) simpleComponent.dependsOnScoped();",
          "        default: throw new AssertionError(id);",
          "      }",
          "    }",
          "  }",
          "}");
    } else {
      generatedComponent = new ArrayList<>();
      Collections.addAll(generatedComponent,
          "package test;");
      Collections.addAll(generatedComponent,
          GeneratedLines.generatedImportsIndividual(
              "import dagger.internal.DoubleCheck;",
              "import jakarta.inject.Provider;"));
      Collections.addAll(generatedComponent,
          GeneratedLines.generatedAnnotationsIndividual());
      Collections.addAll(generatedComponent,
          "final class DaggerSimpleComponent implements SimpleComponent {",
          "  private final DaggerSimpleComponent simpleComponent = this;",
          "",
          "  private Provider<ScopedType> scopedTypeProvider;",
          "  private Provider<DependsOnScoped> dependsOnScopedProvider;",
          "",
          "  private DaggerSimpleComponent() {",
          "    initialize();",
          "  }",
          "",
          "  public static Builder builder() {",
          "    return new Builder();",
          "  }",
          "",
          "  public static SimpleComponent create() {",
          "    return new Builder().build();",
          "  }",
          "",
          "  @SuppressWarnings(\"unchecked\")",
          "  private void initialize() {",
          "    this.scopedTypeProvider = DoubleCheck.provider(ScopedType_Factory.create());",
          "    this.dependsOnScopedProvider = DependsOnScoped_Factory.create(scopedTypeProvider);",
          "  }",
          "",
          "  @Override",
          "  public NeedsProvider needsProvider() {",
          "    return new NeedsProvider(dependsOnScopedProvider);",
          "  }",
          "",
          "  static final class Builder {",
          "    private Builder() {",
          "    }",
          "",
          "    public SimpleComponent build() {",
          "      return new DaggerSimpleComponent();",
          "    }",
          "  }",
          "}");
    }
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(scopedType, dependsOnScoped, componentFile, needsProvider);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .containsLines(generatedComponent);
  }

  @Test
  public void scopedBinding_onlyUsedInSubcomponent() {
    JavaFileObject scopedType =
        JavaFileObjects.forSourceLines(
            "test.ScopedType",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "import jakarta.inject.Singleton;",
            "",
            "@Singleton",
            "final class ScopedType {",
            "  @Inject ScopedType() {}",
            "}");

    JavaFileObject dependsOnScoped =
        JavaFileObjects.forSourceLines(
            "test.ScopedType",
            "package test;",
            "",
            "import jakarta.inject.Inject;",
            "import jakarta.inject.Provider;",
            "",
            "final class DependsOnScoped {",
            "  @Inject DependsOnScoped(ScopedType scoped) {}",
            "}");
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Singleton;",
            "",
            "@Singleton",
            "@Component",
            "interface SimpleComponent {",
            "  Sub sub();",
            "}");
    JavaFileObject subcomponentFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Sub {",
            "  DependsOnScoped dependsOnScoped();",
            "}");

    List<String> generatedComponent;
    if (compilerMode == CompilerMode.FAST_INIT_MODE) {
      generatedComponent = new ArrayList<>();
      Collections.addAll(generatedComponent,
          "package test;");
      Collections.addAll(generatedComponent,
          GeneratedLines.generatedImportsIndividual(
              "import dagger.internal.DoubleCheck;",
              "import dagger.internal.MemoizedSentinel;"));
      Collections.addAll(generatedComponent,
          GeneratedLines.generatedAnnotationsIndividual());
      Collections.addAll(generatedComponent,
          "final class DaggerSimpleComponent implements SimpleComponent {",
          "  private final DaggerSimpleComponent simpleComponent = this;",
          "",
          "  private volatile Object scopedType = new MemoizedSentinel();",
          "",
          "  private DaggerSimpleComponent() {",
          "  }",
          "",
          "  public static Builder builder() {",
          "    return new Builder();",
          "  }",
          "",
          "  public static SimpleComponent create() {",
          "    return new Builder().build();",
          "  }",
          "",
          "  private ScopedType scopedType() {",
          "    Object local = scopedType;",
          "    if (local instanceof MemoizedSentinel) {",
          "      synchronized (local) {",
          "        local = scopedType;",
          "        if (local instanceof MemoizedSentinel) {",
          "          local = new ScopedType();",
          "          scopedType = DoubleCheck.reentrantCheck(scopedType, local);",
          "        }",
          "      }",
          "    }",
          "    return (ScopedType) local;",
          "  }",
          "",
          "  @Override",
          "  public Sub sub() {",
          "    return new SubImpl(simpleComponent);",
          "  }",
          "",
          "  static final class Builder {",
          "    private Builder() {",
          "    }",
          "",
          "    public SimpleComponent build() {",
          "      return new DaggerSimpleComponent();",
          "    }",
          "  }",
          "",
          "  private static final class SubImpl implements Sub {",
          "    private final DaggerSimpleComponent simpleComponent;",
          "",
          "    private final SubImpl subImpl = this;",
          "",
          "    private SubImpl(DaggerSimpleComponent simpleComponent) {",
          "      this.simpleComponent = simpleComponent;",
          "    }",
          "",
          "    @Override",
          "    public DependsOnScoped dependsOnScoped() {",
          "      return new DependsOnScoped(simpleComponent.scopedType());",
          "    }",
          "  }",
          "}");
    } else {
      generatedComponent = new ArrayList<>();
      Collections.addAll(generatedComponent,
          "package test;");
      Collections.addAll(generatedComponent,
          GeneratedLines.generatedImportsIndividual(
              "import dagger.internal.DoubleCheck;",
              "import jakarta.inject.Provider;"));
      Collections.addAll(generatedComponent,
          GeneratedLines.generatedAnnotationsIndividual());
      Collections.addAll(generatedComponent,
          "final class DaggerSimpleComponent implements SimpleComponent {",
          "  private final DaggerSimpleComponent simpleComponent = this;",
          "",
          "  private Provider<ScopedType> scopedTypeProvider;",
          "",
          "  private DaggerSimpleComponent() {",
          "    initialize();",
          "  }",
          "",
          "  public static Builder builder() {",
          "    return new Builder();",
          "  }",
          "",
          "  public static SimpleComponent create() {",
          "    return new Builder().build();",
          "  }",
          "",
          "  @SuppressWarnings(\"unchecked\")",
          "  private void initialize() {",
          "    this.scopedTypeProvider = DoubleCheck.provider(ScopedType_Factory.create());",
          "  }",
          "",
          "  @Override",
          "  public Sub sub() {",
          "    return new SubImpl(simpleComponent);",
          "  }",
          "",
          "  static final class Builder {",
          "    private Builder() {",
          "    }",
          "",
          "    public SimpleComponent build() {",
          "      return new DaggerSimpleComponent();",
          "    }",
          "  }",
          "",
          "  private static final class SubImpl implements Sub {",
          "    private final DaggerSimpleComponent simpleComponent;",
          "",
          "    private final SubImpl subImpl = this;",
          "",
          "    private SubImpl(DaggerSimpleComponent simpleComponent) {",
          "      this.simpleComponent = simpleComponent;",
          "    }",
          "",
          "    @Override",
          "    public DependsOnScoped dependsOnScoped() {",
          "      return new DependsOnScoped(simpleComponent.scopedTypeProvider.get());",
          "    }",
          "  }",
          "}");
    }
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(scopedType, dependsOnScoped, componentFile, subcomponentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .containsLines(generatedComponent);
  }
}
