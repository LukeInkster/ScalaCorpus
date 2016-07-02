package dotty.tools.dotc
package typer

import dotty.tools.dotc.ast.{Trees, tpd}
import core._
import Types._, Contexts._, Flags._, Symbols._, Annotations._, Trees._
import Decorators._

object Variances {
  import tpd._

  type Variance = FlagSet
  val Bivariant = VarianceFlags
  val Invariant = EmptyFlags

  /** Flip between covariant and contravariant */
  def flip(v: Variance): Variance = {
    if (v == Covariant) Contravariant
    else if (v == Contravariant) Covariant
    else v
  }

  /** Map everything below Bivariant to Invariant */
  def cut(v: Variance): Variance =
    if (v == Bivariant) v else Invariant

  def compose(v: Variance, boundsVariance: Int) =
    if (boundsVariance == 1) v
    else if (boundsVariance == -1) flip(v)
    else cut(v)

  /** Compute variance of type parameter `tparam' in types of all symbols `sym'. */
  def varianceInSyms(syms: List[Symbol])(tparam: Symbol)(implicit ctx: Context): Variance =
    (Bivariant /: syms) ((v, sym) => v & varianceInSym(sym)(tparam))

  /** Compute variance of type parameter `tparam' in type of symbol `sym'. */
  def varianceInSym(sym: Symbol)(tparam: Symbol)(implicit ctx: Context): Variance =
    if (sym.isAliasType) cut(varianceInType(sym.info)(tparam))
    else varianceInType(sym.info)(tparam)

  /** Compute variance of type parameter `tparam' in all types `tps'. */
  def varianceInTypes(tps: List[Type])(tparam: Symbol)(implicit ctx: Context): Variance =
    (Bivariant /: tps) ((v, tp) => v & varianceInType(tp)(tparam))

  /** Compute variance of type parameter `tparam' in all type arguments
   *  <code>tps</code> which correspond to formal type parameters `tparams1'.
   */
  def varianceInArgs(tps: List[Type], tparams1: List[Symbol])(tparam: Symbol)(implicit ctx: Context): Variance = {
    var v: Variance = Bivariant;
    for ((tp, tparam1) <- tps zip tparams1) {
      val v1 = varianceInType(tp)(tparam)
      v = v & (if (tparam1.is(Covariant)) v1
           else if (tparam1.is(Contravariant)) flip(v1)
           else cut(v1))
    }
    v
  }

  /** Compute variance of type parameter `tparam' in all type annotations `annots'. */
  def varianceInAnnots(annots: List[Annotation])(tparam: Symbol)(implicit ctx: Context): Variance = {
    (Bivariant /: annots) ((v, annot) => v & varianceInAnnot(annot)(tparam))
  }

  /** Compute variance of type parameter `tparam' in type annotation `annot'. */
  def varianceInAnnot(annot: Annotation)(tparam: Symbol)(implicit ctx: Context): Variance = {
    varianceInType(annot.tree.tpe)(tparam)
  }

  /** Compute variance of type parameter <code>tparam</code> in type <code>tp</code>. */
  def varianceInType(tp: Type)(tparam: Symbol)(implicit ctx: Context): Variance = tp match {
    case TermRef(pre, _) =>
      varianceInType(pre)(tparam)
    case tp @ TypeRef(pre, _) =>
      if (tp.symbol == tparam) Covariant else varianceInType(pre)(tparam)
    case tp @ TypeBounds(lo, hi) =>
      if (lo eq hi) compose(varianceInType(hi)(tparam), tp.variance)
      else flip(varianceInType(lo)(tparam)) & varianceInType(hi)(tparam)
    case tp @ RefinedType(parent, _) =>
      varianceInType(parent)(tparam) & varianceInType(tp.refinedInfo)(tparam)
    case tp @ MethodType(_, paramTypes) =>
      flip(varianceInTypes(paramTypes)(tparam)) & varianceInType(tp.resultType)(tparam)
    case ExprType(restpe) =>
      varianceInType(restpe)(tparam)
    case tp @ PolyType(_) =>
      flip(varianceInTypes(tp.paramBounds)(tparam)) & varianceInType(tp.resultType)(tparam)
    case AnnotatedType(tp, annot) =>
      varianceInType(tp)(tparam) & varianceInAnnot(annot)(tparam)
    case tp: AndOrType =>
      varianceInType(tp.tp1)(tparam) & varianceInType(tp.tp2)(tparam)
    case _ =>
      Bivariant
  }

  def varianceString(v: Variance) =
    if (v is Covariant) "covariant"
    else if (v is Contravariant) "contravariant"
    else "invariant"

  def varianceString(v: Int) =
    if (v > 0) "+"
    else if (v < 0) "-"
    else ""
}
