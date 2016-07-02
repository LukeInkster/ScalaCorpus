package dotty.tools.dotc
package transform

import core._
import DenotTransformers.IdentityDenotTransformer
import Contexts.Context
import Symbols._
import Scopes._
import collection.mutable
import TreeTransforms.MiniPhaseTransform
import SymDenotations._
import ast.Trees._
import NameOps._
import TreeTransforms.TransformerInfo
import StdNames._

/** The preceding lambda lift and flatten phases move symbols to different scopes
 *  and rename them. This miniphase cleans up afterwards and makes sure that all
 *  class scopes contain the symbols defined in them.
 */
class RestoreScopes extends MiniPhaseTransform with IdentityDenotTransformer { thisTransform =>
  import ast.tpd._
  override def phaseName = "restoreScopes"

  /* Note: We need to wait until we see a package definition because
   * DropEmptyConstructors changes template members when analyzing the
   * enclosing package definitions. So by the time RestoreScopes gets to
   * see a typedef or template, it still might be changed by DropEmptyConstructors.
   */
  override def transformPackageDef(pdef: PackageDef)(implicit ctx: Context, info: TransformerInfo) = {
    pdef.stats.foreach(restoreScope)
    pdef
  }

  private def restoreScope(tree: Tree)(implicit ctx: Context, info: TransformerInfo) = tree match {
    case TypeDef(_, impl: Template) =>
      val restoredDecls = newScope
      for (stat <- impl.constr :: impl.body)
        if (stat.isInstanceOf[MemberDef] && stat.symbol.exists)
          restoredDecls.enter(stat.symbol)
      // Enter class in enclosing package scope, in case it was an inner class before flatten.
      // For top-level classes this does nothing.
      val cls = tree.symbol.asClass
      val pkg = cls.owner.asClass

      // Bring back companion links
      val companionClass = cls.info.decls.lookup(nme.COMPANION_CLASS_METHOD)
      val companionModule = cls.info.decls.lookup(nme.COMPANION_MODULE_METHOD)

      if (companionClass.exists) {
        restoredDecls.enter(companionClass)
      }

      if (companionModule.exists) {
        restoredDecls.enter(companionModule)
      }

      pkg.enter(cls)
      val cinfo = cls.classInfo
      tree.symbol.copySymDenotation(
        info = cinfo.derivedClassInfo( // Dotty deviation: Cannot expand cinfo inline without a type error
          decls = restoredDecls: Scope)).installAfter(thisTransform)
      tree
    case tree => tree
  }
}

