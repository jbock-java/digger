package dagger.internal.codegen.xprocessing;

import java.util.List;
import javax.lang.model.element.AnnotationValue;

/**
 * This wraps information about an argument in an annotation.
 */
public interface XAnnotationValue {

  /**
   * The property name.
   */
  String name();

  /**
   * The value set on the annotation property, or the default value if it was not explicitly set.
   *
   * Possible types are:
   * - Primitives (Boolean, Byte, Int, Long, Float, Double)
   * - String
   * - XEnumEntry
   * - XAnnotation
   * - XType
   * - List of {@code XAnnotationValue}
   */
  Object value();

  AnnotationValue toJavac();

  /**Returns the value a list of [XAnnotationValue]. */
  List<XAnnotationValue> asAnnotationValueList();

  /** Returns the value as a list of [XType]. */
  List<XType> asTypeList();

  /** Returns the value as a [XType]. */
  XType asType();
}