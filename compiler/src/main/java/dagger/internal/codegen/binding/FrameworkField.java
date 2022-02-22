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

package dagger.internal.codegen.binding;

import static dagger.internal.codegen.xprocessing.XConverters.toJavac;
import static dagger.spi.model.BindingKind.MEMBERS_INJECTOR;

import dagger.internal.codegen.xprocessing.XType;
import io.jbock.auto.value.AutoValue;
import dagger.internal.codegen.base.CaseFormat;
import io.jbock.javapoet.ClassName;
import io.jbock.javapoet.ParameterizedTypeName;
import io.jbock.javapoet.TypeName;
import java.util.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementKindVisitor8;

/**
 * A value object that represents a field in the generated Component class.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>{@code Provider<String>}
 *   <li>{@code Producer<Widget>}
 *   <li>{@code Provider<Map<SomeMapKey, MapValue>>}.
 * </ul>
 */
@AutoValue
public abstract class FrameworkField {

  /**
   * Creates a framework field.
   *
   * @param frameworkClassName the name of the framework class (e.g., {@code jakarta.inject.Provider})
   * @param valueTypeName the name of the type parameter of the framework class (e.g., {@code Foo}
   *     for {@code Provider<Foo>}
   * @param fieldName the name of the field
   */
  public static FrameworkField create(
      ClassName frameworkClassName, TypeName valueTypeName, String fieldName) {
    String suffix = frameworkClassName.simpleName();
    return new AutoValue_FrameworkField(
        ParameterizedTypeName.get(frameworkClassName, valueTypeName),
        fieldName.endsWith(suffix) ? fieldName : fieldName + suffix);
  }

  /**
   * A framework field for a {@code ContributionBinding}.
   *
   * @param frameworkClassName if present, the field will use this framework class instead of the normal
   *     one for the binding's type.
   */
  public static FrameworkField forBinding(
      ContributionBinding binding, Optional<ClassName> frameworkClassName) {
    return create(
        frameworkClassName.orElse(binding.frameworkType().frameworkClassName()),
        fieldValueType(binding).getTypeName(),
        frameworkFieldName(binding));
  }

  private static XType fieldValueType(ContributionBinding binding) {
    return binding.contributionType().isMultibinding()
        ? binding.contributedType()
        : binding.key().type().xprocessing();
  }

  private static String frameworkFieldName(ContributionBinding binding) {
    if (binding.bindingElement().isPresent()) {
      String name = BINDING_ELEMENT_NAME.visit(toJavac(binding.bindingElement().get()), binding);
      return binding.kind().equals(MEMBERS_INJECTOR) ? name + "MembersInjector" : name;
    }
    return KeyVariableNamer.name(binding.key());
  }

  private static final ElementVisitor<String, Binding> BINDING_ELEMENT_NAME =
      new ElementKindVisitor8<String, Binding>() {

        @Override
        protected String defaultAction(Element e, Binding p) {
          throw new IllegalArgumentException("Unexpected binding " + p);
        }

        @Override
        public String visitExecutableAsConstructor(ExecutableElement e, Binding p) {
          return visit(e.getEnclosingElement(), p);
        }

        @Override
        public String visitExecutableAsMethod(ExecutableElement e, Binding p) {
          return e.getSimpleName().toString();
        }

        @Override
        public String visitType(TypeElement e, Binding p) {
          return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, e.getSimpleName().toString());
        }

        @Override
        public String visitVariableAsParameter(VariableElement e, Binding p) {
          return e.getSimpleName().toString();
        }
      };

  public abstract ParameterizedTypeName type();

  public abstract String name();
}
