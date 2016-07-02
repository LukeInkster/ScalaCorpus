/*                     __                                               *\
**     ________ ___   / /  ___      __ ____  Scala.js sbt plugin        **
**    / __/ __// _ | / /  / _ | __ / // __/  (c) 2013, LAMP/EPFL        **
**  __\ \/ /__/ __ |/ /__/ __ |/_// /_\ \    http://scala-js.org/       **
** /____/\___/_/ |_/____/_/ | |__/ /____/                               **
**                          |/____/                                     **
\*                                                                      */


package org.scalajs.jsenv.nodejs

import java.io.{Console => _, _}

import org.scalajs.core.tools.io._
import org.scalajs.core.tools.jsdep.ResolvedJSDependency
import org.scalajs.jsenv._

import org.scalajs.core.ir.Utils.escapeJS

class JSDOMNodeJSEnv(
  nodejsPath: String = "node",
  addArgs: Seq[String] = Seq.empty,
  addEnv: Map[String, String] = Map.empty
) extends AbstractNodeJSEnv(nodejsPath, addArgs, addEnv, sourceMap = false) {

  protected def vmName: String = "Node.js with JSDOM"

  override def jsRunner(libs: Seq[ResolvedJSDependency],
      code: VirtualJSFile): JSRunner = {
    new DOMNodeRunner(libs, code)
  }

  override def asyncRunner(libs: Seq[ResolvedJSDependency],
      code: VirtualJSFile): AsyncJSRunner = {
    new AsyncDOMNodeRunner(libs, code)
  }

  override def comRunner(libs: Seq[ResolvedJSDependency],
      code: VirtualJSFile): ComJSRunner = {
    new ComDOMNodeRunner(libs, code)
  }

  protected class DOMNodeRunner(libs: Seq[ResolvedJSDependency], code: VirtualJSFile)
      extends ExtRunner(libs, code) with AbstractDOMNodeRunner

  protected class AsyncDOMNodeRunner(libs: Seq[ResolvedJSDependency], code: VirtualJSFile)
      extends AsyncExtRunner(libs, code) with AbstractDOMNodeRunner

  protected class ComDOMNodeRunner(libs: Seq[ResolvedJSDependency], code: VirtualJSFile)
      extends AsyncDOMNodeRunner(libs, code) with NodeComJSRunner

  protected trait AbstractDOMNodeRunner extends AbstractNodeRunner {

    protected def codeWithJSDOMContext(): Seq[VirtualJSFile] = {
      val scriptsJSPaths = getLibJSFiles().map {
        case file: FileVirtualFile => file.path
        case file                  => libCache.materialize(file).getAbsolutePath
      }
      val scriptsStringPath = scriptsJSPaths.map('"' + escapeJS(_) + '"')
      val jsDOMCode = {
        s"""
           |(function () {
           |  const jsdom = require("jsdom");
           |  var windowKeys = [];
           |
           |  jsdom.env({
           |    html: "",
           |    virtualConsole: jsdom.createVirtualConsole().sendTo(console),
           |    created: function (error, window) {
           |      if (error == null) {
           |        window["__ScalaJSEnv"] = __ScalaJSEnv;
           |        window["scalajsCom"] = global.scalajsCom;
           |        windowKeys = Object.keys(window);
           |      } else {
           |        console.log(error);
           |      }
           |    },
           |    scripts: [${scriptsStringPath.mkString(", ")}],
           |    onload: function (window) {
           |      for (var k in window) {
           |        if (windowKeys.indexOf(k) == -1)
           |          global[k] = window[k];
           |      }
           |
           |      ${code.content}
           |    }
           |  });
           |})();
           |""".stripMargin
      }
      Seq(new MemVirtualJSFile("codeWithJSDOMContext.js").withContent(jsDOMCode))
    }

    override protected def getJSFiles(): Seq[VirtualJSFile] =
      initFiles() ++ customInitFiles() ++ codeWithJSDOMContext()

    /** Libraries are loaded via scripts in Node.js */
    override protected def getLibJSFiles(): Seq[VirtualJSFile] =
      libs.map(_.lib)

    // Send code to Stdin
    override protected def sendVMStdin(out: OutputStream): Unit = {
      /* Do not factor this method out into AbstractNodeRunner or when mixin in
       * the traits it would use AbstractExtRunner.sendVMStdin due to
       * linearization order.
       */
      sendJS(getJSFiles(), out)
    }
  }

}
