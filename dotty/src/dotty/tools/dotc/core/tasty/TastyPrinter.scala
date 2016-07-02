package dotty.tools.dotc
package core
package tasty

import Contexts._, Decorators._
import printing.Texts._
import TastyName._
import StdNames._
import TastyUnpickler._
import TastyBuffer.Addr
import util.Positions.{Position, offsetToInt}
import collection.mutable

class TastyPrinter(bytes: Array[Byte])(implicit ctx: Context) {

  val unpickler = new TastyUnpickler(bytes)
  import unpickler.{tastyName, unpickle}

  def nameToString(name: TastyName): String = name match {
    case Simple(name) => name.toString
    case Qualified(qual, name) => nameRefToString(qual) + "." + nameRefToString(name)
    case Signed(original, params, result) =>
      i"${nameRefToString(original)}@${params.map(nameRefToString)}%,%:${nameRefToString(result)}"
    case Expanded(prefix, original) => s"$prefix${nme.EXPAND_SEPARATOR}$original"
    case ModuleClass(original) => nameRefToString(original) + "/MODULECLASS"
    case SuperAccessor(accessed) => nameRefToString(accessed) + "/SUPERACCESSOR"
    case DefaultGetter(meth, num) => nameRefToString(meth) + "/DEFAULTGETTER" + num
    case Shadowed(original) => nameRefToString(original) + "/SHADOWED"
  }

  def nameRefToString(ref: NameRef): String = nameToString(tastyName(ref))

  def printNames() =
    for ((name, idx) <- tastyName.contents.zipWithIndex)
      println(f"$idx%4d: " + nameToString(name))

  def printContents(): Unit = {
    println("Names:")
    printNames()
    println("Trees:")
    unpickle(new TreeSectionUnpickler)
    unpickle(new PositionSectionUnpickler)
  }

  class TreeSectionUnpickler extends SectionUnpickler[Unit]("ASTs") {
    import TastyFormat._
    def unpickle(reader: TastyReader, tastyName: TastyName.Table): Unit = {
      import reader._
      var indent = 0
      def newLine() = print(f"\n ${index(currentAddr) - index(startAddr)}%5d:" + " " * indent)
      def printNat() = print(" " + readNat())
      def printName() = {
        val idx = readNat()
        print(" ") ;print(idx); print("["); print(nameRefToString(NameRef(idx))); print("]")
      }
      def printTree(): Unit = {
        newLine()
        val tag = readByte()
        print(" ");print(astTagToString(tag))
        indent += 2
        if (tag >= firstLengthTreeTag) {
          val len = readNat()
          print(s"($len)")
          val end = currentAddr + len
          def printTrees() = until(end)(printTree())
          tag match {
            case RENAMED =>
              printName(); printName()
            case VALDEF | DEFDEF | TYPEDEF | TYPEPARAM | PARAM | NAMEDARG | BIND =>
              printName(); printTrees()
            case REFINEDtype =>
              printTree(); printName(); printTrees()
            case RETURN =>
              printNat(); printTrees()
            case METHODtype | POLYtype =>
              printTree()
              until(end) { printName(); printTree() }
            case PARAMtype =>
              printNat(); printNat()
            case _ =>
              printTrees()
          }
          if (currentAddr != end) {
            println(s"incomplete read, current = $currentAddr, end = $end")
            goto(end)
          }
        }
        else if (tag >= firstNatASTTreeTag) {
          tag match {
            case IDENT | SELECT | TERMREF | TYPEREF | SELFDEF => printName()
            case _ => printNat()
          }
          printTree()
        }
        else if (tag >= firstASTTreeTag)
          printTree()
        else if (tag >= firstNatTreeTag)
          tag match {
            case TERMREFpkg | TYPEREFpkg | STRINGconst | IMPORTED => printName()
            case _ => printNat()
          }
        indent -= 2
      }
      println(i"start = ${reader.startAddr}, base = $base, current = $currentAddr, end = $endAddr")
      println(s"${endAddr.index - startAddr.index} bytes of AST, base = $currentAddr")
      while (!isAtEnd) {
        printTree()
        newLine()
      }
    }
  }

  class PositionSectionUnpickler extends SectionUnpickler[Unit]("Positions") {
    def unpickle(reader: TastyReader, tastyName: TastyName.Table): Unit = {
      print(s"${reader.endAddr.index - reader.currentAddr.index}")
      val (totalRange, positions) = new PositionUnpickler(reader).unpickle()
      println(s" position bytes in $totalRange:")
      val sorted = positions.toSeq.sortBy(_._1.index)
      for ((addr, pos) <- sorted) println(s"${addr.index}: ${offsetToInt(pos.start)} .. ${pos.end}")
    }
  }
}
