package dotty.tools.dotc
package transform

import core._
import Contexts._, Symbols._, Types._, Flags._, Decorators._, StdNames._, Constants._, Phases._
import TreeTransforms._
import ast.Trees._
import collection.mutable

object NonLocalReturns {
  import ast.tpd._
  def isNonLocalReturn(ret: Return)(implicit ctx: Context) =
    ret.from.symbol != ctx.owner.enclosingMethod || ctx.owner.is(Lazy)
}

/** Implement non-local returns using NonLocalReturnControl exceptions.
 */
class NonLocalReturns extends MiniPhaseTransform { thisTransformer =>
  override def phaseName = "nonLocalReturns"

  import NonLocalReturns._
  import ast.tpd._

  override def runsAfter: Set[Class[_ <: Phase]] = Set(classOf[ElimByName])

  private def ensureConforms(tree: Tree, pt: Type)(implicit ctx: Context) =
    if (tree.tpe <:< pt) tree
    else Erasure.Boxing.adaptToType(tree, pt)

  /** The type of a non-local return expression with given argument type */
  private def nonLocalReturnExceptionType(argtype: Type)(implicit ctx: Context) =
    defn.NonLocalReturnControlType.appliedTo(argtype)

  /** A hashmap from method symbols to non-local return keys */
  private val nonLocalReturnKeys = mutable.Map[Symbol, TermSymbol]()

  /** Return non-local return key for given method */
  private def nonLocalReturnKey(meth: Symbol)(implicit ctx: Context) =
    nonLocalReturnKeys.getOrElseUpdate(meth,
      ctx.newSymbol(
        meth, ctx.freshName("nonLocalReturnKey").toTermName, Synthetic, defn.ObjectType, coord = meth.pos))

  /** Generate a non-local return throw with given return expression from given method.
   *  I.e. for the method's non-local return key, generate:
   *
   *    throw new NonLocalReturnControl(key, expr)
   *  todo: maybe clone a pre-existing exception instead?
   *  (but what to do about exceptions that miss their targets?)
   */
  private def nonLocalReturnThrow(expr: Tree, meth: Symbol)(implicit ctx: Context) =
    Throw(
      New(
        defn.NonLocalReturnControlType,
        ref(nonLocalReturnKey(meth)) :: expr.ensureConforms(defn.ObjectType) :: Nil))

  /** Transform (body, key) to:
   *
   *  {
   *    val key = new Object()
   *    try {
   *      body
   *    } catch {
   *      case ex: NonLocalReturnControl =>
   *        if (ex.key().eq(key)) ex.value().asInstanceOf[T]
   *        else throw ex
   *    }
   *  }
   */
  private def nonLocalReturnTry(body: Tree, key: TermSymbol, meth: Symbol)(implicit ctx: Context) = {
    val keyDef = ValDef(key, New(defn.ObjectType, Nil))
    val nonLocalReturnControl = defn.NonLocalReturnControlType
    val ex = ctx.newSymbol(meth, nme.ex, EmptyFlags, nonLocalReturnControl, coord = body.pos)
    val pat = BindTyped(ex, nonLocalReturnControl)
    val rhs = If(
        ref(ex).select(nme.key).appliedToNone.select(nme.eq).appliedTo(ref(key)),
        ref(ex).select(nme.value).ensureConforms(meth.info.finalResultType),
        Throw(ref(ex)))
    val catches = CaseDef(pat, EmptyTree, rhs) :: Nil
    val tryCatch = Try(body, catches, EmptyTree)
    Block(keyDef :: Nil, tryCatch)
  }

  override def transformDefDef(tree: DefDef)(implicit ctx: Context, info: TransformerInfo): Tree =
    nonLocalReturnKeys.remove(tree.symbol) match {
      case Some(key) => cpy.DefDef(tree)(rhs = nonLocalReturnTry(tree.rhs, key, tree.symbol))
      case _ => tree
    }

  override def transformReturn(tree: Return)(implicit ctx: Context, info: TransformerInfo): Tree =
    if (isNonLocalReturn(tree)) nonLocalReturnThrow(tree.expr, tree.from.symbol).withPos(tree.pos)
    else tree
}
