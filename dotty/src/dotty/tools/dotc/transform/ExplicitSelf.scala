package dotty.tools.dotc
package transform

import core._
import Contexts.Context
import Types._
import TreeTransforms._
import Decorators._
import ast.Trees._
import Flags._

/** Transform references of the form
 *
 *     C.this.m
 *
 *  where `C` is a class with explicit self type and `C` is not a
 *  subclass of the owner of `m` to
 *
 *     C.this.asInstanceOf[S].m
 *
 *  where `S` is the self type of `C`.
 */
class ExplicitSelf extends MiniPhaseTransform { thisTransform =>
  import ast.tpd._

  override def phaseName = "explicitSelf"

  override def transformSelect(tree: Select)(implicit ctx: Context, info: TransformerInfo): Tree = tree match {
    case Select(thiz: This, name) if name.isTermName =>
      val cls = thiz.symbol.asClass
      val cinfo = cls.classInfo
      if (cinfo.givenSelfType.exists && !cls.derivesFrom(tree.symbol.owner))
        cpy.Select(tree)(thiz.asInstance(cinfo.selfType), name)
      else tree
    case _ => tree
  }
}
