package dotty.tools.dotc
package transform

import ast.{Trees, tpd}
import core._, core.Decorators._
import Contexts._, Flags._, Trees._, Types._, StdNames._, Symbols._
import ValueClasses._

object TreeExtractors {
  import tpd._

  /** Match arg1.op(arg2) and extract (arg1, op.symbol, arg2) */
  object BinaryOp {
    def unapply(t: Tree)(implicit ctx: Context): Option[(Tree, Symbol, Tree)] = t match {
      case Apply(sel @ Select(arg1, _), List(arg2)) =>
        Some((arg1, sel.symbol, arg2))
      case _ =>
        None
    }
  }

 /** Match new C(args) and extract (C, args) */
  object NewWithArgs {
    def unapply(t: Tree)(implicit ctx: Context): Option[(Type, List[Tree])] = t match {
      case Apply(Select(New(_), nme.CONSTRUCTOR), args) =>
        Some((t.tpe, args))
      case _ =>
        None
    }
  }

  /** For an instance v of a value class like:
   *    class V(val underlying: X) extends AnyVal
   *  Match v.underlying() and extract v
   */
  object ValueClassUnbox {
    def unapply(t: Tree)(implicit ctx: Context): Option[Tree] = t match {
      case Apply(sel @ Select(ref, _), Nil) =>
        val d = ref.tpe.widenDealias.typeSymbol.denot
        if (isDerivedValueClass(d) && (sel.symbol eq valueClassUnbox(d.asClass))) {
          Some(ref)
        } else
          None
      case _ =>
        None
    }
  }
}
