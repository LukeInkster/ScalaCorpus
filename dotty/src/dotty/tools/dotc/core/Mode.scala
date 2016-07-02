package dotty.tools.dotc.core

/** A collection of mode bits that are part of a context */
case class Mode(val bits: Int) extends AnyVal {
  import Mode._
  def | (that: Mode) = Mode(bits | that.bits)
  def & (that: Mode) = Mode(bits & that.bits)
  def &~ (that: Mode) = Mode(bits & ~that.bits)
  def is (that: Mode) = (bits & that.bits) == that.bits

  def isExpr = (this & PatternOrType) == None

  override def toString =
    (0 until 31).filter(i => (bits & (1 << i)) != 0).map(modeName).mkString("Mode(", ",", ")")
}

object Mode {
  val None = Mode(0)

  private val modeName = new Array[String](32)

  def newMode(bit: Int, name: String): Mode = {
    modeName(bit) = name
    Mode(1 << bit)
  }

  val Pattern = newMode(0, "Pattern")
  val Type = newMode(1, "Type")

  val ImplicitsEnabled = newMode(2, "ImplicitsEnabled")
  val InferringReturnType = newMode(3, "InferringReturnType")

  /** This mode bit is set if we collect information without reference to a valid
   *  context with typerstate and constraint. This is typically done when we
   *  cache the eligibility of implicits. Caching needs to be done across different constraints.
   *  Therefore, if TypevarsMissContext is set, subtyping becomes looser, and assumes
   *  that PolyParams can be sub- and supertypes of anything. See TypeComparer.
   */
  val TypevarsMissContext = newMode(4, "TypevarsMissContext")
  val CheckCyclic = newMode(5, "CheckCyclic")

  val InSuperCall = newMode(6, "InSuperCall")

  /** This mode bit is set if we want to allow accessing a symbol's denotation
   *  at a period before that symbol is first valid. An example where this is
   *  the case is if we want to examine the environment where an access is made.
   *  The computation might take place at an earlier phase (e.g. it is part of
   *  some completion such as unpickling), but the environment might contain
   *  synbols that are not yet defined in that phase.
   *  If the mode bit is set, getting the denotation of a symbol at a phase
   *  before the symbol is defined will return the symbol's denotation at the
   *  first phase where it is valid, instead of throwing a NotDefinedHere error.
   */
  val FutureDefsOK = newMode(7, "FutureDefsOK")

  /** Allow GADTFlexType labelled types to have their bounds adjusted */
  val GADTflexible = newMode(8, "GADTflexible")

  /** Allow dependent functions. This is currently necessary for unpickling, because
   *  some dependent functions are passed through from the front end(s?), even though they
   *  are technically speaking illegal.
   */
  val AllowDependentFunctions = newMode(9, "AllowDependentFunctions")

  /** We are currently printing something: avoid to produce more logs about
   *  the printing
   */
  val Printing = newMode(10, "Printing")

  /** We are currently typechecking an ident to determine whether some implicit
   *  is shadowed - don't do any other shadowing tests.
   */
  val ImplicitShadowing = newMode(11, "ImplicitShadowing")

  /** We are currently in a `viewExists` check. In that case, ambiguous
   *  implicits checks are disabled and we succeed with the first implicit
   *  found.
   */
  val ImplicitExploration = newMode(12, "ImplicitExploration")

  /** We are currently unpickling Scala2 info */
  val Scala2Unpickling = newMode(13, "Scala2Unpickling")

  /** Use Scala2 scheme for overloading and implicit resolution */
  val OldOverloadingResolution = newMode(14, "OldOverloadingResolution")

  val PatternOrType = Pattern | Type
}
