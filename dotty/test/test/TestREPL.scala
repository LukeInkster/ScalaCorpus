package test

import dotty.tools.dotc.repl._
import dotty.tools.dotc.core.Contexts.Context
import collection.mutable
import java.io.StringWriter

/** A subclass of REPL used for testing.
 *  It takes a transcript of a REPL session in `script`. The transcript
 *  starts with the first input prompt `scala> ` and ends with `scala> :quit` and a newline.
 *  Invoking `process()` on the `TestREPL` runs all input lines and
 *  collects then interleaved with REPL output in a string writer `out`.
 *  Invoking `check()` checks that the collected output matches the original
 *  `script`.
 */
class TestREPL(script: String) extends REPL {

  private val out = new StringWriter()

  override lazy val config = new REPL.Config {
    override val output = new NewLinePrintWriter(out)

    override def context(ctx: Context) =
      ctx.fresh.setSetting(ctx.settings.color, "never")

    override def input(in: Interpreter)(implicit ctx: Context) = new InteractiveReader {
      val lines = script.lines
      def readLine(prompt: String): String = {
        val line = lines.next
        if (line.startsWith(prompt) || line.startsWith(continuationPrompt)) {
          output.println(line)
          line.drop(prompt.length)
        }
        else readLine(prompt)
      }
      val interactive = false
    }
  }

  def check() = {
    out.close()
    val printed = out.toString
    val transcript = printed.drop(printed.indexOf(config.prompt))
    if (transcript.toString != script) {
      println("input differs from transcript:")
      println(transcript)
      assert(false)
    }
  }
}
