/*
 * Copyright (C) 2018 The Dagger Authors.
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

package dagger.internal.codegen.writing;

import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.MapKeys;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import jakarta.inject.Inject;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;

/**
 * Generates a class that exposes a non-{@code public} {@link
 * ContributionBinding#mapKeyAnnotation()} @MapKey} annotation.
 */
public final class InaccessibleMapKeyProxyGenerator
    extends SourceFileGenerator<ContributionBinding> {
  private final DaggerTypes types;
  private final DaggerElements elements;

  @Inject
  InaccessibleMapKeyProxyGenerator(
      Filer filer, DaggerTypes types, DaggerElements elements) {
    super(filer, elements);
    this.types = types;
    this.elements = elements;
  }

  @Override
  public Element originatingElement(ContributionBinding binding) {
    // a map key is only ever present on bindings that have a binding element
    return binding.bindingElement().get();
  }

  @Override
  public ImmutableList<TypeSpec.Builder> topLevelTypes(ContributionBinding binding) {
    return MapKeys.mapKeyFactoryMethod(binding, types, elements)
        .map(
            method ->
                classBuilder(MapKeys.mapKeyProxyClassName(binding))
                    .addModifiers(PUBLIC, FINAL)
                    .addMethod(constructorBuilder().addModifiers(PRIVATE).build())
                    .addMethod(method))
        .map(ImmutableList::of)
        .orElse(ImmutableList.of());
  }
}
