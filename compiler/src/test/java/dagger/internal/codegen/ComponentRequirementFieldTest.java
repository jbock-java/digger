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
import java.util.Collections;
import java.util.List;
import javax.tools.JavaFileObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ComponentRequirementFieldTest {

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void bindsInstance(CompilerMode compilerMode) {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.Component;",
            "import java.util.List;",
            "",
            "@Component",
            "interface TestComponent {",
            "  int i();",
            "  List<String> list();",
            "",
            "  @Component.Builder",
            "  interface Builder {",
            "    @BindsInstance Builder i(int i);",
            "    @BindsInstance Builder list(List<String> list);",
            "    TestComponent build();",
            "  }",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(component);
    assertThat(compilation).succeeded();
    List<String> generatedComponent = new ArrayList<>();
    Collections.addAll(generatedComponent,
        "package test;");
    Collections.addAll(generatedComponent,
        GeneratedLines.generatedAnnotationsIndividual());
    Collections.addAll(generatedComponent,
        "final class DaggerTestComponent implements TestComponent {",
        "  private final Integer i;",
        "  private final List<String> list;",
        "",
        "  private DaggerTestComponent(Integer iParam, List<String> listParam) {",
        "    this.i = iParam;",
        "    this.list = listParam;",
        "  }",
        "",
        "  @Override",
        "  public int i() {",
        "    return i;",
        "  }",
        "",
        "  @Override",
        "  public List<String> list() {",
        "    return list;",
        "  }",
        "",
        "  private static final class Builder implements TestComponent.Builder {",
        "    private Integer i;",
        "    private List<String> list;",
        "",
        "    @Override",
        "    public Builder i(int i) {",
        "      this.i = Preconditions.checkNotNull(i);",
        "      return this;",
        "    }",
        "",
        "    @Override",
        "    public Builder list(List<String> list) {",
        "      this.list = Preconditions.checkNotNull(list);",
        "      return this;",
        "    }",
        "",
        "    @Override",
        "    public TestComponent build() {",
        "      Preconditions.checkBuilderRequirement(i, Integer.class);",
        "      Preconditions.checkBuilderRequirement(list, List.class);",
        "      return new DaggerTestComponent(i, list);",
        "    }",
        "  }",
        "}");
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsLines(generatedComponent);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void instanceModuleMethod(CompilerMode compilerMode) {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.ParentModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class ParentModule {",
            "  @Provides int i() { return 0; }",
            "}");
    JavaFileObject otherPackageModule =
        JavaFileObjects.forSourceLines(
            "other.OtherPackageModule",
            "package other;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "public class OtherPackageModule {",
            "  @Provides long l() { return 0L; }",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import other.OtherPackageModule;",
            "",
            "@Component(modules = {ParentModule.class, OtherPackageModule.class})",
            "interface TestComponent {",
            "  int i();",
            "  long l();",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(module, otherPackageModule, component);
    assertThat(compilation).succeeded();
    List<String> generatedComponent = new ArrayList<>();
    Collections.addAll(generatedComponent,
        "package test;",
        "",
        "import other.OtherPackageModule;",
        "import other.OtherPackageModule_LFactory;");
    Collections.addAll(generatedComponent,
        GeneratedLines.generatedAnnotationsIndividual());
    Collections.addAll(generatedComponent,
        "final class DaggerTestComponent implements TestComponent {",
        "  private final ParentModule parentModule;",
        "  private final OtherPackageModule otherPackageModule;",
        "",
        "  @Override",
        "  public int i() {",
        "    return parentModule.i();",
        "  }",
        "",
        "  @Override",
        "  public long l() {",
        "    return OtherPackageModule_LFactory.l(otherPackageModule);",
        "  }",
        "}");
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsLines(generatedComponent);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void componentInstances(CompilerMode compilerMode) {
    JavaFileObject dependency =
        JavaFileObjects.forSourceLines(
            "test.Dep",
            "package test;",
            "",
            "interface Dep {",
            "  String string();",
            "  Object object();",
            "}");

    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(dependencies = Dep.class)",
            "interface TestComponent {",
            "  TestComponent self();",
            "  TestSubcomponent subcomponent();",
            "",
            "  Dep dep();",
            "  String methodOnDep();",
            "  Object otherMethodOnDep();",
            "}");
    JavaFileObject subcomponent =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface TestSubcomponent {",
            "  TestComponent parent();",
            "  Dep depFromSubcomponent();",
            "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(dependency, component, subcomponent);
    assertThat(compilation).succeeded();
    List<String> generatedComponent = new ArrayList<>();
    Collections.addAll(generatedComponent,
        "package test;");
    Collections.addAll(generatedComponent,
        GeneratedLines.generatedAnnotationsIndividual());
    Collections.addAll(generatedComponent,
        "final class DaggerTestComponent implements TestComponent {",
        "  private final Dep dep;",
        "",
        "  private DaggerTestComponent(Dep depParam) {",
        "    this.dep = depParam;",
        "  }",
        "",
        "  @Override",
        "  public TestComponent self() {",
        "    return this;",
        "  }",
        "",
        "  @Override",
        "  public Dep dep() {",
        "    return dep;",
        "  }",
        "",
        "  @Override",
        "  public String methodOnDep() {",
        "    return Preconditions.checkNotNullFromComponent(dep.string());",
        "  }",
        "",
        "  @Override",
        "  public Object otherMethodOnDep() {",
        "    return Preconditions.checkNotNullFromComponent(dep.object());",
        "  }",
        "",
        "  private static final class TestSubcomponentImpl implements TestSubcomponent {",
        "    @Override",
        "    public TestComponent parent() {",
        "      return testComponent;",
        "    }",
        "",
        "    @Override",
        "    public Dep depFromSubcomponent() {",
        "      return testComponent.dep;",
        "    }",
        "  }",
        "}");
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsLines(generatedComponent);
  }

  @EnumSource(CompilerMode.class)
  @ParameterizedTest
  void componentRequirementNeededInFactoryCreationOfSubcomponent(CompilerMode compilerMode) {
    JavaFileObject parentModule =
        JavaFileObjects.forSourceLines(
            "test.ParentModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.multibindings.IntoSet;",
            "import dagger.Provides;",
            "import java.util.Set;",
            "",
            "@Module",
            "class ParentModule {",
            "  @Provides",
            // intentionally non-static. this needs to require the module when the subcompnent
            // adds to the Set binding
            "  Object reliesOnMultibinding(Set<Object> set) { return set; }",
            "",
            "  @Provides @IntoSet static Object contribution() { return new Object(); }",
            "}");

    JavaFileObject childModule =
        JavaFileObjects.forSourceLines(
            "test.ChildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.multibindings.IntoSet;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class ChildModule {",
            "  @Provides @IntoSet static Object contribution() { return new Object(); }",
            "}");

    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import jakarta.inject.Provider;",
            "",
            "@Component(modules = ParentModule.class)",
            "interface TestComponent {",
            "  Provider<Object> dependsOnMultibinding();",
            "  TestSubcomponent subcomponent();",
            "}");

    JavaFileObject subcomponent =
        JavaFileObjects.forSourceLines(
            "test.TestSubcomponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import jakarta.inject.Provider;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface TestSubcomponent {",
            "  Provider<Object> dependsOnMultibinding();",
            "}");
    List<String> generatedComponent;
    if (compilerMode == CompilerMode.FAST_INIT_MODE) {
      generatedComponent = new ArrayList<>();
      Collections.addAll(generatedComponent,
          "package test;");
      Collections.addAll(generatedComponent,
          GeneratedLines.generatedAnnotationsIndividual());
      Collections.addAll(generatedComponent,
          "final class DaggerTestComponent implements TestComponent {",
          "  private final ParentModule parentModule;",
          "",
          "  private DaggerTestComponent(ParentModule parentModuleParam) {",
          "    this.parentModule = parentModuleParam;",
          "  }",
          "",
          "  private static final class TestSubcomponentImpl implements TestSubcomponent {",
          "    private Set<Object> setOfObject() {",
          "      return SetBuilder.<Object>newSetBuilder(2).add(ParentModule_ContributionFactory.contribution()).add(ChildModule_ContributionFactory.contribution()).build();",
          "    }",
          "",
          "    private Object object() {",
          "      return ParentModule_ReliesOnMultibindingFactory.reliesOnMultibinding(testComponent.parentModule, setOfObject());",
          "    }",
          "  }",
          "}");
    } else {
      generatedComponent = new ArrayList<>();
      Collections.addAll(generatedComponent,
          "package test;");
      Collections.addAll(generatedComponent,
          GeneratedLines.generatedAnnotationsIndividual());
      Collections.addAll(generatedComponent,
          "final class DaggerTestComponent implements TestComponent {",
          "  private final ParentModule parentModule;",
          "",
          "  private DaggerTestComponent(ParentModule parentModuleParam) {",
          "    this.parentModule = parentModuleParam;",
          "    initialize(parentModuleParam);",
          "  }",
          "",
          "  @SuppressWarnings(\"unchecked\")",
          "  private void initialize(final ParentModule parentModuleParam) {",
          "    this.setOfObjectProvider = SetFactory.<Object>builder(1, 0).addProvider(ParentModule_ContributionFactory.create()).build();",
          "    this.reliesOnMultibindingProvider = ParentModule_ReliesOnMultibindingFactory.create(parentModuleParam, setOfObjectProvider);",
          "  }",
          "",
          "  private static final class TestSubcomponentImpl implements TestSubcomponent {",
          "    @SuppressWarnings(\"unchecked\")",
          "    private void initialize() {",
          "      this.setOfObjectProvider = SetFactory.<Object>builder(2, 0).addProvider(ParentModule_ContributionFactory.create()).addProvider(ChildModule_ContributionFactory.create()).build();",
          "      this.reliesOnMultibindingProvider = ParentModule_ReliesOnMultibindingFactory.create(testComponent.parentModule, setOfObjectProvider);",
          "    }",
          "  }",
          "}");
    }
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(parentModule, childModule, component, subcomponent);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsLines(generatedComponent);
  }
}
