/*
 * Copyright (C) 2020 The Dagger Authors.
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

import static dagger.internal.codegen.base.Preconditions.checkArgument;
import static dagger.internal.codegen.base.Util.asStream;
import static dagger.internal.codegen.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.langmodel.DaggerElements.isAnnotationPresent;
import static dagger.internal.codegen.xprocessing.XElements.asConstructor;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;

import dagger.internal.codegen.collect.ImmutableList;
import dagger.internal.codegen.collect.ImmutableMap;
import dagger.internal.codegen.collect.ImmutableSet;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.xprocessing.XConstructorElement;
import dagger.internal.codegen.xprocessing.XConstructorType;
import dagger.internal.codegen.xprocessing.XElement;
import dagger.internal.codegen.xprocessing.XHasModifiers;
import dagger.internal.codegen.xprocessing.XMethodElement;
import dagger.internal.codegen.xprocessing.XMethodType;
import dagger.internal.codegen.xprocessing.XType;
import dagger.internal.codegen.xprocessing.XTypeElement;
import dagger.internal.codegen.xprocessing.XVariableElement;
import dagger.spi.model.BindingKind;
import io.jbock.auto.common.MoreElements;
import io.jbock.auto.value.AutoValue;
import io.jbock.auto.value.extension.memoized.Memoized;
import io.jbock.javapoet.ParameterSpec;
import io.jbock.javapoet.TypeName;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.VariableElement;

/** Assisted injection utility methods. */
public final class AssistedInjectionAnnotations {
  /** Returns the factory method for the given factory {@code XTypeElement}. */
  public static XMethodElement assistedFactoryMethod(XTypeElement factory) {
    return getOnlyElement(assistedFactoryMethods(factory));
  }

  /** Returns the list of abstract factory methods for the given factory {@code XTypeElement}. */
  public static ImmutableSet<XMethodElement> assistedFactoryMethods(XTypeElement factory) {
    return asStream(factory.getAllNonPrivateInstanceMethods())
        .filter(XHasModifiers::isAbstract)
        .filter(method -> !method.isJavaDefault())
        .collect(toImmutableSet());
  }

  /** Returns {@code true} if the element uses assisted injection. */
  public static boolean isAssistedInjectionType(XTypeElement typeElement) {
    return assistedInjectedConstructors(typeElement).stream()
        .anyMatch(constructor -> constructor.hasAnnotation(TypeNames.ASSISTED_INJECT));
  }

  /** Returns {@code true} if this binding is an assisted factory. */
  public static boolean isAssistedFactoryType(XElement element) {
    return element.hasAnnotation(TypeNames.ASSISTED_FACTORY);
  }

  /**
   * Returns the list of assisted parameters as {@code ParameterSpec}s.
   *
   * <p>The type of each parameter will be the resolved type given by the binding key, and the name
   * of each parameter will be the name given in the {@code
   * dagger.assisted.AssistedInject}-annotated constructor.
   */
  public static ImmutableList<ParameterSpec> assistedParameterSpecs(Binding binding) {
    checkArgument(binding.kind() == BindingKind.ASSISTED_INJECTION);
    XConstructorElement constructor = asConstructor(binding.bindingElement().get());
    XConstructorType constructorType = constructor.asMemberOf(binding.key().type().xprocessing());
    return assistedParameterSpecs(constructor.getParameters(), constructorType.getParameterTypes());
  }

  private static ImmutableList<ParameterSpec> assistedParameterSpecs(
      List<? extends XVariableElement> paramElements, List<XType> paramTypes) {
    ImmutableList.Builder<ParameterSpec> assistedParameterSpecs = ImmutableList.builder();
    for (int i = 0; i < paramElements.size(); i++) {
      XVariableElement paramElement = paramElements.get(i);
      XType paramType = paramTypes.get(i);
      if (isAssistedParameter(paramElement)) {
        assistedParameterSpecs.add(
            ParameterSpec.builder(paramType.getTypeName(), getSimpleName(paramElement)).build());
      }
    }
    return assistedParameterSpecs.build();
  }

  /**
   * Returns the list of assisted factory parameters as {@code ParameterSpec}s.
   *
   * <p>The type of each parameter will be the resolved type given by the binding key, and the name
   * of each parameter will be the name given in the {@code
   * dagger.assisted.AssistedInject}-annotated constructor.
   */
  public static ImmutableList<ParameterSpec> assistedFactoryParameterSpecs(Binding binding) {
    checkArgument(binding.kind() == BindingKind.ASSISTED_FACTORY);

    XTypeElement factory = asTypeElement(binding.bindingElement().get());
    AssistedFactoryMetadata metadata = AssistedFactoryMetadata.create(factory.getType());
    XMethodType factoryMethodType =
        metadata.factoryMethod().asMemberOf(binding.key().type().xprocessing());
    return assistedParameterSpecs(
        // Use the order of the parameters from the @AssistedFactory method but use the parameter
        // names of the @AssistedInject constructor.
        metadata.assistedFactoryAssistedParameters().stream()
            .map(metadata.assistedInjectAssistedParametersMap()::get)
            .collect(toImmutableList()),
        factoryMethodType.getParameterTypes());
  }

  /** Returns the constructors in {@code type} that are annotated with {@code AssistedInject}. */
  public static ImmutableSet<XConstructorElement> assistedInjectedConstructors(XTypeElement type) {
    return type.getConstructors().stream()
        .filter(constructor -> constructor.hasAnnotation(TypeNames.ASSISTED_INJECT))
        .collect(toImmutableSet());
  }

  public static ImmutableList<XVariableElement> assistedParameters(Binding binding) {
    return binding.kind() == BindingKind.ASSISTED_INJECTION
        ? asConstructor(binding.bindingElement().get()).getParameters().stream()
            .filter(AssistedInjectionAnnotations::isAssistedParameter)
            .collect(toImmutableList())
        : ImmutableList.of();
  }

  /** Returns {@code true} if this binding is uses assisted injection. */
  public static boolean isAssistedParameter(XVariableElement param) {
    return param.hasAnnotation(TypeNames.ASSISTED);
  }

  /** Returns {@code true} if this binding is uses assisted injection. */
  public static boolean isAssistedParameter(VariableElement param) {
    return isAnnotationPresent(MoreElements.asVariable(param), TypeNames.ASSISTED);
  }

  /** Metadata about an {@code dagger.assisted.AssistedFactory} annotated type. */
  @AutoValue
  public abstract static class AssistedFactoryMetadata {
    public static AssistedFactoryMetadata create(XType factoryType) {
      XTypeElement factoryElement = factoryType.getTypeElement();
      XMethodElement factoryMethod = assistedFactoryMethod(factoryElement);
      XMethodType factoryMethodType = factoryMethod.asMemberOf(factoryType);
      XType assistedInjectType = factoryMethodType.getReturnType();
      XTypeElement assistedInjectElement = assistedInjectType.getTypeElement();
      return new AutoValue_AssistedInjectionAnnotations_AssistedFactoryMetadata(
          factoryElement,
          factoryType,
          factoryMethod,
          factoryMethodType,
          assistedInjectElement,
          assistedInjectType,
          AssistedInjectionAnnotations.assistedInjectAssistedParameters(assistedInjectType),
          AssistedInjectionAnnotations.assistedFactoryAssistedParameters(
              factoryMethod, factoryMethodType));
    }

    public abstract XTypeElement factory();

    public abstract XType factoryType();

    public abstract XMethodElement factoryMethod();

    public abstract XMethodType factoryMethodType();

    public abstract XTypeElement assistedInjectElement();

    public abstract XType assistedInjectType();

    public abstract ImmutableList<AssistedParameter> assistedInjectAssistedParameters();

    public abstract ImmutableList<AssistedParameter> assistedFactoryAssistedParameters();

    @Memoized
    public ImmutableMap<AssistedParameter, XVariableElement> assistedInjectAssistedParametersMap() {
      ImmutableMap.Builder<AssistedParameter, XVariableElement> builder = ImmutableMap.builder();
      for (AssistedParameter assistedParameter : assistedInjectAssistedParameters()) {
        builder.put(assistedParameter, assistedParameter.element());
      }
      return builder.build();
    }

    @Memoized
    public ImmutableMap<AssistedParameter, XVariableElement>
        assistedFactoryAssistedParametersMap() {
      ImmutableMap.Builder<AssistedParameter, XVariableElement> builder = ImmutableMap.builder();
      for (AssistedParameter assistedParameter : assistedFactoryAssistedParameters()) {
        builder.put(assistedParameter, assistedParameter.element());
      }
      return builder.build();
    }
  }

  /**
   * Metadata about an {@code Assisted} annotated parameter.
   *
   * <p>This parameter can represent an {@code Assisted} annotated parameter from an {@code
   * AssistedInject} constructor or an {@code AssistedFactory} method.
   */
  @AutoValue
  public abstract static class AssistedParameter {
    public static AssistedParameter create(XVariableElement parameter, XType parameterType) {
      AssistedParameter assistedParameter =
          new AutoValue_AssistedInjectionAnnotations_AssistedParameter(
              Optional.ofNullable(parameter.getAnnotation(TypeNames.ASSISTED))
                  .map(assisted -> assisted.getAsString("value"))
                  .orElse(""),
              parameterType.getTypeName());
      assistedParameter.parameterElement = parameter;
      assistedParameter.parameterType = parameterType;
      return assistedParameter;
    }

    private XVariableElement parameterElement;
    private XType parameterType;

    /** Returns the string qualifier from the {@code Assisted#value()}. */
    public abstract String qualifier();

    /** Returns the type annotated with {@code Assisted}. */
    abstract TypeName typeName();

    /** Returns the type annotated with {@code Assisted}. */
    public final XType type() {
      return parameterType;
    }

    public final XVariableElement element() {
      return parameterElement;
    }

    @Override
    public final String toString() {
      return qualifier().isEmpty()
          ? String.format("@Assisted %s", type())
          : String.format("@Assisted(\"%s\") %s", qualifier(), type());
    }
  }

  public static ImmutableList<AssistedParameter> assistedInjectAssistedParameters(
      XType assistedInjectType) {
    // We keep track of the constructor both as an ExecutableElement to access @Assisted
    // parameters and as an ExecutableType to access the resolved parameter types.
    XConstructorElement assistedInjectConstructor =
        getOnlyElement(assistedInjectedConstructors(assistedInjectType.getTypeElement()));
    XConstructorType assistedInjectConstructorType =
        assistedInjectConstructor.asMemberOf(assistedInjectType);

    ImmutableList.Builder<AssistedParameter> builder = ImmutableList.builder();
    for (int i = 0; i < assistedInjectConstructor.getParameters().size(); i++) {
      XVariableElement parameter = assistedInjectConstructor.getParameters().get(i);
      XType parameterType = assistedInjectConstructorType.getParameterTypes().get(i);
      if (parameter.hasAnnotation(TypeNames.ASSISTED)) {
        builder.add(AssistedParameter.create(parameter, parameterType));
      }
    }
    return builder.build();
  }

  private static ImmutableList<AssistedParameter> assistedFactoryAssistedParameters(
      XMethodElement factoryMethod, XMethodType factoryMethodType) {
    ImmutableList.Builder<AssistedParameter> builder = ImmutableList.builder();
    for (int i = 0; i < factoryMethod.getParameters().size(); i++) {
      XVariableElement parameter = factoryMethod.getParameters().get(i);
      XType parameterType = factoryMethodType.getParameterTypes().get(i);
      builder.add(AssistedParameter.create(parameter, parameterType));
    }
    return builder.build();
  }

  private AssistedInjectionAnnotations() {}
}
