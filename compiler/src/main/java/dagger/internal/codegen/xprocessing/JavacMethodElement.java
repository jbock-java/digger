package dagger.internal.codegen.xprocessing;

import io.jbock.auto.common.MoreElements;
import io.jbock.auto.common.MoreTypes;
import io.jbock.javapoet.ClassName;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

class JavacMethodElement extends JavacExecutableElement implements XMethodElement {

  JavacMethodElement(ExecutableElement element, XProcessingEnv env) {
    this(element, env.wrapTypeElement(MoreElements.asType(element.getEnclosingElement())), env);
  }

  JavacMethodElement(ExecutableElement element, XTypeElement containing, XProcessingEnv env) {
    super(element, containing, env);
  }

  @Override
  public XMethodType getExecutableType() {
    TypeMirror asMemberOf = env().toJavac().getTypeUtils()
        .asMemberOf(MoreTypes.asDeclared(containing().toJavac().asType()), toJavac());
    return new XMethodType(env(), this, MoreTypes.asExecutable(asMemberOf));
  }

  @Override
  public String getName() {
    return toJavac().getSimpleName().toString();
  }

  @Override
  public boolean isAccessibleFrom(String packageName) {
    if (isPublic() || isProtected()) {
      return true;
    }
    if (isPrivate()) {
      return false;
    }
    // check package
    Element enclosingElement = containing().toJavac();
    ClassName anObject = ClassName.get(MoreElements.asType(enclosingElement));
    return packageName.equals(anObject.packageName());
  }

  @Override
  public boolean isStaticInterfaceMethod() {
    return isStatic() && containing().toJavac().getKind() == ElementKind.INTERFACE;
  }

  @Override
  public XMethodElement copyTo(XTypeElement newContainer) {
    return new JavacMethodElement(toJavac(), newContainer, env());
  }

  @Override
  public XType getReturnType() {
    TypeMirror asMember = env().toJavac().getTypeUtils()
        .asMemberOf(MoreTypes.asDeclared(containing().toJavac().asType()), toJavac());
    ExecutableType asExec = MoreTypes.asExecutable(asMember);
    return env().wrap(asExec.getReturnType());
  }

  @Override
  public XMethodType asMemberOf(XType other) {
    if (!(other instanceof JavacDeclaredType) || containing().getType().isSameType(other)) {
      return getExecutableType();
    }
    TypeMirror asMemberOf = env().toJavac().getTypeUtils().asMemberOf(((JavacDeclaredType) other).toJavac(), toJavac());
    return new XMethodType(env(), this, MoreTypes.asExecutable(asMemberOf));
  }
}
