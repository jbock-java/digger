/*
 * Copyright (C) 2014 The Dagger Authors.
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

package dagger.internal.codegen.componentgenerator;

import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.collect.ImmutableList;
import dagger.internal.codegen.writing.ComponentImplementation;
import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XFiler;
import dagger.internal.codegen.xprocessing.XProcessingEnv;
import io.jbock.javapoet.TypeSpec;
import jakarta.inject.Inject;
import java.util.Optional;

/** Generates the implementation of the abstract types annotated with {@code Component}. */
final class ComponentGenerator extends SourceFileGenerator<BindingGraph> {
  private final TopLevelImplementationComponent.Factory topLevelImplementationComponentFactory;

  @Inject
  ComponentGenerator(
      XFiler filer,
      XProcessingEnv processingEnv,
      TopLevelImplementationComponent.Factory topLevelImplementationComponentFactory) {
    super(filer, processingEnv);
    this.topLevelImplementationComponentFactory = topLevelImplementationComponentFactory;
  }

  @Override
  public XElement originatingElement(BindingGraph input) {
    return input.componentTypeElement();
  }

  @Override
  public ImmutableList<TypeSpec.Builder> topLevelTypes(BindingGraph bindingGraph) {
    ComponentImplementation componentImplementation =
        topLevelImplementationComponentFactory
            .create(bindingGraph)
            .currentImplementationSubcomponentBuilder()
            .bindingGraph(bindingGraph)
            .parentImplementation(Optional.empty())
            .parentRequestRepresentations(Optional.empty())
            .parentRequirementExpressions(Optional.empty())
            .build()
            .componentImplementation();
    return ImmutableList.of(componentImplementation.generate().toBuilder());
  }
}
