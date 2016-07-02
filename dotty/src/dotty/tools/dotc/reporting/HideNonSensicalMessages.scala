package dotty.tools
package dotc
package reporting

import core.Contexts.Context

/**
 * This trait implements `isHidden` so that we avoid reporting non-sensical messages.
 */
trait HideNonSensicalMessages extends Reporter {
  /** Hides non-sensical messages, unless we haven't reported any error yet or
   *  `-Yshow-suppressed-errors` is set.
   */
  override def isHidden(d: Diagnostic)(implicit ctx: Context): Boolean =
    super.isHidden(d) || {
        d.isNonSensical &&
        hasErrors && // if there are no errors yet, report even if diagnostic is non-sensical
        !ctx.settings.YshowSuppressedErrors.value
    }
}
