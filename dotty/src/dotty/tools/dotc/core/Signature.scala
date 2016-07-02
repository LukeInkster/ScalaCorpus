package dotty.tools.dotc
package core

import Names._, Types._, Contexts._, StdNames._
import TypeErasure.sigName

/** The signature of a denotation.
 *  Overloaded denotations with the same name are distinguished by
 *  their signatures. A signature of a method (of type PolyType,MethodType, or ExprType) is
 *  composed of a list of signature names, one for each parameter type, plus a signature for
 *  the result type. Methods are uncurried before taking their signatures.
 *  The signature name of a type is the fully qualified name of the type symbol of the type's erasure.
 *
 *  For instance a definition
 *
 *      def f(x: Int)(y: List[String]): String
 *
 *  would have signature
 *
 *      Signature(
 *        List("scala.Int".toTypeName, "scala.collection.immutable.List".toTypeName),
 *        "scala.String".toTypeName)
 *
 *  The signatures of non-method types are always `NotAMethod`.
 */
case class Signature(paramsSig: List[TypeName], resSig: TypeName) {
  import Signature._

  /** Does this signature coincide with that signature on their parameter parts? */
  final def sameParams(that: Signature): Boolean = this.paramsSig == that.paramsSig

  /** The degree to which this signature matches `that`.
   *  If both parameter and result type names match (i.e. they are the same
   *  or one is a wildcard), the result is `FullMatch`.
   *  If only the parameter names match, the result is `ParamMatch` before erasure and
   *  `NoMatch` otherwise.
   *  If the parameters do not match, the result is always `NoMatch`.
   */
  final def matchDegree(that: Signature)(implicit ctx: Context): MatchDegree =
    if (sameParams(that))
      if (resSig == that.resSig || isWildcard(resSig) || isWildcard(that.resSig)) FullMatch
      else if (!ctx.erasedTypes) ParamMatch
      else NoMatch
    else NoMatch

  /** name.toString == "" or name.toString == "_" */
  private def isWildcard(name: TypeName) = name.isEmpty || name == tpnme.WILDCARD

  /** Construct a signature by prepending the signature names of the given `params`
   *  to the parameter part of this signature.
   */
  def prepend(params: List[Type], isJava: Boolean)(implicit ctx: Context) =
    Signature((params.map(sigName(_, isJava))) ++ paramsSig, resSig)

}

object Signature {

  type MatchDegree = Int
  val NoMatch = 0
  val ParamMatch = 1
  val FullMatch = 2

  /** The signature of everything that's not a method, i.e. that has
   *  a type different from PolyType, MethodType, or ExprType.
   */
  val NotAMethod = Signature(List(), EmptyTypeName)

  /** The signature of an overloaded denotation.
   */
  val OverloadedSignature = Signature(List(tpnme.OVERLOADED), EmptyTypeName)

  /** The signature of a method with no parameters and result type `resultType`. */
  def apply(resultType: Type, isJava: Boolean)(implicit ctx: Context): Signature = {
    assert(!resultType.isInstanceOf[ExprType])
    apply(Nil, sigName(resultType, isJava))
  }
}
