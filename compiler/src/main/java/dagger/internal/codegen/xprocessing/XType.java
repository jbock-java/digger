package dagger.internal.codegen.xprocessing;

import io.jbock.javapoet.TypeName;
import java.util.List;
import javax.lang.model.type.TypeMirror;

public interface XType {

  TypeMirror toJavac();

  XTypeElement getTypeElement();

  List<XType> getTypeArguments();

  TypeName getTypeName();

  boolean isSameType(XType other);

  boolean isVoid();

  boolean isArray();

  /**
   * Returns boxed version of this type if it is a primitive or itself if it is not a primitive
   * type.
   */
  XType boxed();

  /**
   * Returns {@code true} if this type can be assigned from {@code other}
   */
  boolean isAssignableFrom(XType other);
}
