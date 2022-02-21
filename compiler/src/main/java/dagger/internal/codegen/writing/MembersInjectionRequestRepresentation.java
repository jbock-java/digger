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

package dagger.internal.codegen.writing;

import static dagger.internal.codegen.xprocessing.XType.isVoid;
import static dagger.internal.codegen.xprocessing.XConverters.toJavac;
import static dagger.internal.codegen.collect.Iterables.getOnlyElement;

import dagger.internal.codegen.xprocessing.XMethodElement;
import io.jbock.javapoet.ClassName;
import io.jbock.javapoet.CodeBlock;
import io.jbock.javapoet.ParameterSpec;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.binding.MembersInjectionBinding;
import dagger.internal.codegen.javapoet.Expression;

/**
 * A binding expression for members injection component methods. See {@link
 * MembersInjectionMethods}.
 */
final class MembersInjectionRequestRepresentation extends RequestRepresentation {
  private final MembersInjectionBinding binding;
  private final MembersInjectionMethods membersInjectionMethods;

  @AssistedInject
  MembersInjectionRequestRepresentation(
      @Assisted MembersInjectionBinding binding, MembersInjectionMethods membersInjectionMethods) {
    this.binding = binding;
    this.membersInjectionMethods = membersInjectionMethods;
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    throw new UnsupportedOperationException(binding.toString());
  }

  // TODO(ronshapiro): This class doesn't need to be a RequestRepresentation, as
  // getDependencyExpression() should never be called for members injection methods. It's probably
  // better suited as a method on MembersInjectionMethods
  @Override
  protected CodeBlock getComponentMethodImplementation(
      ComponentMethodDescriptor componentMethod, ComponentImplementation component) {
    XMethodElement methodElement = componentMethod.methodElement();
    ParameterSpec parameter =
        ParameterSpec.get(toJavac(getOnlyElement(methodElement.getParameters())));

    if (binding.injectionSites().isEmpty()) {
      return isVoid(methodElement.getReturnType())
          ? CodeBlock.of("")
          : CodeBlock.of("return $N;", parameter);
    } else {
      ClassName requestingClass = component.name();
      return isVoid(methodElement.getReturnType())
          ? CodeBlock.of("$L;", membersInjectionInvocation(parameter, requestingClass).codeBlock())
          : CodeBlock.of(
              "return $L;", membersInjectionInvocation(parameter, requestingClass).codeBlock());
    }
  }

  private Expression membersInjectionInvocation(ParameterSpec target, ClassName requestingClass) {
    return membersInjectionMethods.getInjectExpression(
        binding.key(), CodeBlock.of("$N", target), requestingClass);
  }

  @AssistedFactory
  static interface Factory {
    MembersInjectionRequestRepresentation create(MembersInjectionBinding binding);
  }
}
