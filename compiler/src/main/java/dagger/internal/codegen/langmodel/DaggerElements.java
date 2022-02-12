/*
 * Copyright (C) 2013 The Dagger Authors.
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

package dagger.internal.codegen.langmodel;

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.xprocessing.XConverters.toJavac;
import static io.jbock.auto.common.MoreElements.asExecutable;
import static io.jbock.auto.common.MoreElements.hasModifiers;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;
import static javax.lang.model.element.Modifier.ABSTRACT;

import dagger.internal.codegen.base.ClearableCache;
import dagger.internal.codegen.extension.DaggerStreams;
import dagger.internal.codegen.xprocessing.XMethodElement;
import dagger.internal.codegen.xprocessing.XProcessingEnv;
import dagger.internal.codegen.xprocessing.XTypeElement;
import io.jbock.auto.common.MoreElements;
import io.jbock.auto.common.MoreTypes;
import io.jbock.common.graph.Traverser;
import io.jbock.javapoet.ClassName;
import java.io.Writer;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.NullType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.UnionType;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.AbstractTypeVisitor8;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleElementVisitor8;
import javax.lang.model.util.Types;

/** Extension of {@link Elements} that adds Dagger-specific methods. */
public final class DaggerElements implements Elements, ClearableCache {
  private final Map<TypeElement, Set<ExecutableElement>> getLocalAndInheritedMethodsCache =
      new HashMap<>();
  private final Elements elements;
  private final Types types;

  public DaggerElements(Elements elements, Types types) {
    this.elements = requireNonNull(elements);
    this.types = requireNonNull(types);
  }

  /**
   * Returns {@code true} if {@code encloser} is equal to {@code enclosed} or recursively encloses
   * it.
   */
  public static boolean elementEncloses(TypeElement encloser, Element enclosed) {
    for (Element element : GET_ENCLOSED_ELEMENTS.breadthFirst(encloser)) {
      if (element.equals(enclosed)) {
        return true;
      }
    }
    return false;
  }

  private static final Traverser<Element> GET_ENCLOSED_ELEMENTS =
      Traverser.forTree(Element::getEnclosedElements);

  public Set<ExecutableElement> getLocalAndInheritedMethods(TypeElement type) {
    return getLocalAndInheritedMethodsCache.computeIfAbsent(
        type, k -> MoreElements.getLocalAndInheritedMethods(type, types, elements));
  }

  public Set<ExecutableElement> getUnimplementedMethods(TypeElement type) {
    return getLocalAndInheritedMethods(type).stream()
        .filter(hasModifiers(ABSTRACT))
        .collect(DaggerStreams.toImmutableSet());
  }

  @Override
  public TypeElement getTypeElement(CharSequence name) {
    return elements.getTypeElement(name);
  }

  /** Returns the type element for a class name. */
  public TypeElement getTypeElement(ClassName className) {
    return getTypeElement(className.canonicalName());
  }

  /** Returns the argument or the closest enclosing element that is a {@link TypeElement}. */
  public static TypeElement closestEnclosingTypeElement(Element element) {
    return element.accept(CLOSEST_ENCLOSING_TYPE_ELEMENT, null);
  }

  private static final ElementVisitor<TypeElement, Void> CLOSEST_ENCLOSING_TYPE_ELEMENT =
      new SimpleElementVisitor8<>() {
        @Override
        protected TypeElement defaultAction(Element element, Void p) {
          return element.getEnclosingElement().accept(this, null);
        }

        @Override
        public TypeElement visitType(TypeElement type, Void p) {
          return type;
        }
      };

  /**
   * Compares elements according to their declaration order among siblings. Only valid to compare
   * elements enclosed by the same parent.
   */
  public static final Comparator<Element> DECLARATION_ORDER =
      comparing(element -> siblings(element).indexOf(element));

  // For parameter elements, element.getEnclosingElement().getEnclosedElements() is empty. So
  // instead look at the parameter list of the enclosing executable.
  private static List<? extends Element> siblings(Element element) {
    return element.getKind().equals(ElementKind.PARAMETER)
        ? asExecutable(element.getEnclosingElement()).getParameters()
        : element.getEnclosingElement().getEnclosedElements();
  }

  /**
   * Returns {@code true} iff the given element has an {@link AnnotationMirror} whose {@linkplain
   * AnnotationMirror#getAnnotationType() annotation type} has the same canonical name as any of
   * that of {@code annotationClasses}.
   */
  public static boolean isAnyAnnotationPresent(
      Element element, Iterable<ClassName> annotationClasses) {
    for (ClassName annotation : annotationClasses) {
      if (isAnnotationPresent(element, annotation)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns {@code true} iff the given element has an {@link AnnotationMirror} whose {@linkplain
   * AnnotationMirror#getAnnotationType() annotation type} is equivalent to {@code annotationType}.
   */
  public static boolean isAnnotationPresent(Element element, TypeMirror annotationType) {
    return element.getAnnotationMirrors().stream()
        .map(AnnotationMirror::getAnnotationType)
        .anyMatch(candidate -> MoreTypes.equivalence().equivalent(candidate, annotationType));
  }

  /**
   * Returns {@code true} iff the given element has an {@link AnnotationMirror} whose {@link
   * AnnotationMirror#getAnnotationType() annotation type} has the same canonical name as that of
   * {@code annotationClass}. This method is a safer alternative to calling {@link
   * Element#getAnnotation} and checking for {@code null} as it avoids any interaction with
   * annotation proxies.
   */
  public static boolean isAnnotationPresent(Element element, ClassName annotationName) {
    return getAnnotationMirror(element, annotationName).isPresent();
  }

  /**
   * Returns the annotation present on {@code element} whose type is in {@code annotations},
   * checking each annotation type in order.
   */
  public static Optional<AnnotationMirror> getAnyAnnotation(
      Element element, Collection<ClassName> annotations) {
    return element.getAnnotationMirrors().stream()
        .filter(hasAnnotationTypeIn(annotations))
        .map((AnnotationMirror a) -> a) // Avoid returning Optional<? extends AnnotationMirror>.
        .findFirst();
  }

  /** Returns the annotations present on {@code element} of all types. */
  public static Set<AnnotationMirror> getAllAnnotations(
      Element element, List<ClassName> annotations) {
    return element.getAnnotationMirrors().stream()
        .filter(hasAnnotationTypeIn(annotations))
        .collect(toImmutableSet());
  }

  // Note: This is similar to auto-common's MoreElements except using ClassName rather than Class.
  // TODO(bcorso): Contribute a String version to auto-common's MoreElements?

  /**
   * Returns an {@link AnnotationMirror} for the annotation of type {@code annotationClass} on
   * {@code element}, or {@link Optional#empty()} if no such annotation exists. This method is a
   * safer alternative to calling {@link Element#getAnnotation} as it avoids any interaction with
   * annotation proxies.
   */
  public static Optional<AnnotationMirror> getAnnotationMirror(
      Element element, ClassName annotationName) {
    String annotationClassName = annotationName.canonicalName();
    for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
      TypeElement annotationTypeElement =
          MoreElements.asType(annotationMirror.getAnnotationType().asElement());
      if (annotationTypeElement.getQualifiedName().contentEquals(annotationClassName)) {
        return Optional.of(annotationMirror);
      }
    }
    return Optional.empty();
  }

  private static Predicate<AnnotationMirror> hasAnnotationTypeIn(
      Collection<ClassName> annotations) {
    Set<String> annotationClassNames =
        annotations.stream().map(ClassName::canonicalName).collect(toSet());
    return annotation ->
        annotationClassNames.contains(
            MoreTypes.asTypeElement(annotation.getAnnotationType()).getQualifiedName().toString());
  }

  /**
   * Returns the field descriptor of the given {@code element}.
   *
   * <p>This is useful for matching Kotlin Metadata JVM Signatures with elements from the AST.
   *
   * <p>For reference, see the <a
   * href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.3.2">JVM
   * specification, section 4.3.2</a>.
   */
  public static String getFieldDescriptor(VariableElement element) {
    return element.getSimpleName() + ":" + getDescriptor(element.asType());
  }

  /**
   * Returns the method descriptor of the given {@code element}.
   *
   * <p>This is useful for matching Kotlin Metadata JVM Signatures with elements from the AST.
   *
   * <p>For reference, see the <a
   * href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.3.3">JVM
   * specification, section 4.3.3</a>.
   */
  // TODO(bcorso): Expose getMethodDescriptor() method in XProcessing instead.
  public static String getMethodDescriptor(XMethodElement element) {
    return getMethodDescriptor(toJavac(element));
  }

  /**
   * Returns the method descriptor of the given {@code element}.
   *
   * <p>This is useful for matching Kotlin Metadata JVM Signatures with elements from the AST.
   *
   * <p>For reference, see the <a
   * href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.3.3">JVM
   * specification, section 4.3.3</a>.
   */
  public static String getMethodDescriptor(ExecutableElement element) {
    return element.getSimpleName() + getDescriptor(element.asType());
  }

  private static String getDescriptor(TypeMirror t) {
    return t.accept(JVM_DESCRIPTOR_TYPE_VISITOR, null);
  }

  private static final AbstractTypeVisitor8<String, Void> JVM_DESCRIPTOR_TYPE_VISITOR =
      new AbstractTypeVisitor8<>() {

        @Override
        public String visitArray(ArrayType arrayType, Void v) {
          return "[" + getDescriptor(arrayType.getComponentType());
        }

        @Override
        public String visitDeclared(DeclaredType declaredType, Void v) {
          return "L" + getInternalName(declaredType.asElement()) + ";";
        }

        @Override
        public String visitError(ErrorType errorType, Void v) {
          // For descriptor generating purposes we don't need a fully modeled type since we are
          // only interested in obtaining the class name in its "internal form".
          return visitDeclared(errorType, v);
        }

        @Override
        public String visitExecutable(ExecutableType executableType, Void v) {
          String parameterDescriptors =
              executableType.getParameterTypes().stream()
                  .map(DaggerElements::getDescriptor)
                  .collect(Collectors.joining());
          String returnDescriptor = getDescriptor(executableType.getReturnType());
          return "(" + parameterDescriptors + ")" + returnDescriptor;
        }

        @Override
        public String visitIntersection(IntersectionType intersectionType, Void v) {
          // For a type variable with multiple bounds: "the erasure of a type variable is determined
          // by the first type in its bound" - JVM Spec Sec 4.4
          return getDescriptor(intersectionType.getBounds().get(0));
        }

        @Override
        public String visitNoType(NoType noType, Void v) {
          return "V";
        }

        @Override
        public String visitNull(NullType nullType, Void v) {
          return visitUnknown(nullType, null);
        }

        @Override
        public String visitPrimitive(PrimitiveType primitiveType, Void v) {
          switch (primitiveType.getKind()) {
            case BOOLEAN:
              return "Z";
            case BYTE:
              return "B";
            case SHORT:
              return "S";
            case INT:
              return "I";
            case LONG:
              return "J";
            case CHAR:
              return "C";
            case FLOAT:
              return "F";
            case DOUBLE:
              return "D";
            default:
              throw new IllegalArgumentException("Unknown primitive type.");
          }
        }

        @Override
        public String visitTypeVariable(TypeVariable typeVariable, Void v) {
          // The erasure of a type variable is the erasure of its leftmost bound. - JVM Spec Sec 4.6
          return getDescriptor(typeVariable.getUpperBound());
        }

        @Override
        public String visitUnion(UnionType unionType, Void v) {
          return visitUnknown(unionType, null);
        }

        @Override
        public String visitUnknown(TypeMirror typeMirror, Void v) {
          throw new IllegalArgumentException("Unsupported type: " + typeMirror);
        }

        @Override
        public String visitWildcard(WildcardType wildcardType, Void v) {
          return "";
        }

        /**
         * Returns the name of this element in its "internal form".
         *
         * <p>For reference, see the <a
         * href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.2">JVM
         * specification, section 4.2</a>.
         */
        private String getInternalName(Element element) {
          try {
            TypeElement typeElement = MoreElements.asType(element);
            switch (typeElement.getNestingKind()) {
              case TOP_LEVEL:
                return typeElement.getQualifiedName().toString().replace('.', '/');
              case MEMBER:
                return getInternalName(typeElement.getEnclosingElement())
                    + "$"
                    + typeElement.getSimpleName();
              default:
                throw new IllegalArgumentException("Unsupported nesting kind.");
            }
          } catch (IllegalArgumentException e) {
            // Not a TypeElement, try something else...
          }

          if (element instanceof QualifiedNameable) {
            QualifiedNameable qualifiedNameElement = (QualifiedNameable) element;
            return qualifiedNameElement.getQualifiedName().toString().replace('.', '/');
          }

          return element.getSimpleName().toString();
        }
      };

  /** Returns the type element or throws {@link TypeNotPresentException} if it is null. */
  public static XTypeElement checkTypePresent(XProcessingEnv processingEnv, ClassName className) {
    XTypeElement type = processingEnv.findTypeElement(className);
    if (type == null) {
      throw new TypeNotPresentException(className.canonicalName(), null);
    }
    return type;
  }

  /**
   * Invokes {@link Elements#getTypeElement(CharSequence)}, throwing {@link TypeNotPresentException}
   * if it is not accessible in the current compilation.
   */
  public TypeElement checkTypePresent(String typeName) {
    TypeElement type = elements.getTypeElement(typeName);
    if (type == null) {
      throw new TypeNotPresentException(typeName, null);
    }
    return type;
  }

  @Override
  public PackageElement getPackageElement(CharSequence name) {
    return elements.getPackageElement(name);
  }

  @Override
  public Map<? extends ExecutableElement, ? extends AnnotationValue> getElementValuesWithDefaults(
      AnnotationMirror a) {
    return elements.getElementValuesWithDefaults(a);
  }

  @Override
  public String getDocComment(Element e) {
    return elements.getDocComment(e);
  }

  @Override
  public boolean isDeprecated(Element e) {
    return elements.isDeprecated(e);
  }

  @Override
  public Name getBinaryName(TypeElement type) {
    return elements.getBinaryName(type);
  }

  @Override
  public PackageElement getPackageOf(Element type) {
    return elements.getPackageOf(type);
  }

  @Override
  public List<? extends Element> getAllMembers(TypeElement type) {
    return elements.getAllMembers(type);
  }

  @Override
  public List<? extends AnnotationMirror> getAllAnnotationMirrors(Element e) {
    return elements.getAllAnnotationMirrors(e);
  }

  @Override
  public boolean hides(Element hider, Element hidden) {
    return elements.hides(hider, hidden);
  }

  @Override
  public boolean overrides(
      ExecutableElement overrider, ExecutableElement overridden, TypeElement type) {
    return elements.overrides(overrider, overridden, type);
  }

  @Override
  public String getConstantExpression(Object value) {
    return elements.getConstantExpression(value);
  }

  @Override
  public void printElements(Writer w, Element... elements) {
    this.elements.printElements(w, elements);
  }

  @Override
  public Name getName(CharSequence cs) {
    return elements.getName(cs);
  }

  @Override
  public boolean isFunctionalInterface(TypeElement type) {
    return elements.isFunctionalInterface(type);
  }

  @Override
  public void clearCache() {
    getLocalAndInheritedMethodsCache.clear();
  }
}
