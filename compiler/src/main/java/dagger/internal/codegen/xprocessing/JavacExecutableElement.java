package dagger.internal.codegen.xprocessing;

import io.jbock.auto.common.MoreElements;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

abstract class JavacExecutableElement extends JavacElement implements XExecutableElement {

  private final ExecutableElement executableElement;
  private final XTypeElement containing;

  JavacExecutableElement(ExecutableElement element, XTypeElement containing, XProcessingEnv env) {
    super(env, element);
    this.executableElement = element;
    this.containing = containing;
  }

  @Override
  public ExecutableElement toJavac() {
    return executableElement;
  }

  @Override
  public XMemberContainer getEnclosingElement() {
    return env().wrapTypeElement(MoreElements.asType(executableElement.getEnclosingElement()));
  }

  public final List<XExecutableParameterElement> getParameters() {
    return executableElement.getParameters().stream()
        .map(p -> new XExecutableParameterElement(p, env()))
        .collect(Collectors.toList());
  }

  public List<? extends TypeMirror> getThrownTypes() {
    return executableElement.getThrownTypes();
  }

  XTypeElement containing() {
    return containing;
  }
}
