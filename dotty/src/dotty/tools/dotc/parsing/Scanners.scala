package dotty.tools
package dotc
package parsing

import core.Names._, core.Contexts._, core.Decorators._, util.Positions._
import core.StdNames._
import util.SourceFile
import java.lang.Character.isDigit
import scala.reflect.internal.Chars._
import Tokens._
import scala.annotation.{ switch, tailrec }
import scala.collection.mutable
import mutable.ListBuffer
import Utility.isNameStart


object Scanners {

  /** Offset into source character array */
  type Offset = Int

  /** An undefined offset */
  val NoOffset: Offset = -1

  case class Comment(pos: Position, chrs: String) {
    def isDocComment = chrs.startsWith("/**")
  }

  type Token = Int

  trait TokenData {

    /** the next token */
    var token: Token = EMPTY

    /** the offset of the first character of the current token */
    var offset: Offset = 0

    /** the offset of the character following the token preceding this one */
    var lastOffset: Offset = 0

    /** the name of an identifier */
    var name: TermName = null

    /** the string value of a literal */
    var strVal: String = null

    /** the base of a number */
    var base: Int = 0

    def copyFrom(td: TokenData) = {
      this.token = td.token
      this.offset = td.offset
      this.lastOffset = td.lastOffset
      this.name = td.name
      this.strVal = td.strVal
      this.base = td.base
    }
  }

  abstract class ScannerCommon(source: SourceFile)(implicit ctx: Context) extends CharArrayReader with TokenData {
    val buf = source.content

    // Errors -----------------------------------------------------------------

    /** the last error offset
      */
    var errOffset: Offset = NoOffset


    /** Generate an error at the given offset */
    def error(msg: String, off: Offset = offset) = {
      ctx.error(msg, source atPos Position(off))
      token = ERROR
      errOffset = off
    }

    /** signal an error where the input ended in the middle of a token */
    def incompleteInputError(msg: String): Unit = {
      ctx.incompleteInputError(msg, source atPos Position(offset))
      token = EOF
      errOffset = offset
    }

    // Setting token data ----------------------------------------------------

    /** A character buffer for literals
      */
    val litBuf = new StringBuilder

    /** append Unicode character to "litBuf" buffer
      */
    protected def putChar(c: Char): Unit = litBuf.append(c)

    /** Return buffer contents and clear */
    def flushBuf(buf: StringBuilder): String = {
      val str = buf.toString
      buf.clear()
      str
    }

    /** Clear buffer and set name and token */
    def finishNamed(idtoken: Token = IDENTIFIER, target: TokenData = this): Unit = {
      target.name = flushBuf(litBuf).toTermName
      target.token = idtoken
      if (idtoken == IDENTIFIER) {
        val idx = target.name.start
        target.token = toToken(idx)
      }
    }
    def toToken(idx: Int): Token

    /** Clear buffer and set string */
    def setStrVal() =
      strVal = flushBuf(litBuf)

    /** Convert current strVal to char value
      */
    def charVal: Char = if (strVal.length > 0) strVal.charAt(0) else 0

    /** Convert current strVal, base to long value
      *  This is tricky because of max negative value.
      */
    def intVal(negated: Boolean): Long = {
      if (token == CHARLIT && !negated) {
        charVal
      } else {
        var value: Long = 0
        val divider = if (base == 10) 1 else 2
        val limit: Long =
          if (token == LONGLIT) Long.MaxValue else Int.MaxValue
        var i = 0
        val len = strVal.length
        while (i < len) {
          val d = digit2int(strVal charAt i, base)
          if (d < 0) {
            error("malformed integer number")
            return 0
          }
          if (value < 0 ||
            limit / (base / divider) < value ||
            limit - (d / divider) < value * (base / divider) &&
              !(negated && limit == value * base - 1 + d)) {
            error("integer number too large")
            return 0
          }
          value = value * base + d
          i += 1
        }
        if (negated) -value else value
      }
    }

    def intVal: Long = intVal(false)

    /** Convert current strVal, base to double value
      */
    def floatVal(negated: Boolean): Double = {
      val limit: Double =
        if (token == DOUBLELIT) Double.MaxValue else Float.MaxValue
      try {
        val value: Double = java.lang.Double.valueOf(strVal).doubleValue()
        if (value > limit)
          error("floating point number too large")
        if (negated) -value else value
      } catch {
        case _: NumberFormatException =>
          error("malformed floating point number")
          0.0
      }
    }

    def floatVal: Double = floatVal(false)

  }

  class Scanner(source: SourceFile, override val startFrom: Offset = 0)(implicit ctx: Context) extends ScannerCommon(source)(ctx) {
    val keepComments = ctx.settings.YkeepComments.value

    /** All doc comments as encountered, each list contains doc comments from
     *  the same block level. Starting with the deepest level and going upward
     */
    private[this] var docsPerBlockStack: List[List[Comment]] = List(Nil)

    /** Adds level of nesting to docstrings */
    def enterBlock(): Unit =
      docsPerBlockStack = List(Nil) ::: docsPerBlockStack

    /** Removes level of nesting for docstrings */
    def exitBlock(): Unit = docsPerBlockStack = docsPerBlockStack match {
      case x :: Nil => List(Nil)
      case _ => docsPerBlockStack.tail
    }

    /** Returns the closest docstring preceding the position supplied */
    def getDocString(pos: Int): Option[String] = {
      def closest(c: Comment, docstrings: List[Comment]): Comment = docstrings match {
        case x :: xs if (c.pos.end < x.pos.end && x.pos.end <= pos) => closest(x, xs)
        case Nil => c
      }

      docsPerBlockStack match {
        case (list @ (x :: xs)) :: _ => {
          val c = closest(x, xs)
          docsPerBlockStack = list.dropWhile(_ != c).tail :: docsPerBlockStack.tail
          Some(c.chrs)
        }
        case _ => None
      }
    }

    /** A buffer for comments */
    val commentBuf = new StringBuilder

    def toToken(idx: Int): Token =
      if (idx >= 0 && idx <= lastKeywordStart) kwArray(idx) else IDENTIFIER

    private class TokenData0 extends TokenData

    /** we need one token lookahead and one token history
     */
    val next : TokenData = new TokenData0
    private val prev : TokenData = new TokenData0

    /** a stack of tokens which indicates whether line-ends can be statement separators
     *  also used for keeping track of nesting levels.
     *  We keep track of the closing symbol of a region. This can be
     *  RPAREN    if region starts with '('
     *  RBRACKET  if region starts with '['
     *  RBRACE    if region starts with '{'
     *  ARROW     if region starts with `case'
     *  STRINGLIT if region is a string interpolation expression starting with '${'
     *            (the STRINGLIT appears twice in succession on the stack iff the
     *             expression is a multiline string literal).
     */
    var sepRegions: List[Token] = List()

// Get next token ------------------------------------------------------------

    /** Are we directly in a string interpolation expression?
     */
    private def inStringInterpolation =
      sepRegions.nonEmpty && sepRegions.head == STRINGLIT

    /** Are we directly in a multiline string interpolation expression?
     *  @pre inStringInterpolation
     */
    private def inMultiLineInterpolation =
      inStringInterpolation && sepRegions.tail.nonEmpty && sepRegions.tail.head == STRINGPART

    /** read next token and return last offset
     */
    def skipToken(): Offset = {
      val off = offset
      nextToken()
      off
    }

    def adjustSepRegions(lastToken: Token): Unit = (lastToken: @switch) match {
      case LPAREN =>
        sepRegions = RPAREN :: sepRegions
      case LBRACKET =>
        sepRegions = RBRACKET :: sepRegions
      case LBRACE =>
        sepRegions = RBRACE :: sepRegions
      case CASE =>
        sepRegions = ARROW :: sepRegions
      case RBRACE =>
        while (!sepRegions.isEmpty && sepRegions.head != RBRACE)
          sepRegions = sepRegions.tail
        if (!sepRegions.isEmpty) sepRegions = sepRegions.tail
      case RBRACKET | RPAREN =>
        if (!sepRegions.isEmpty && sepRegions.head == lastToken)
          sepRegions = sepRegions.tail
      case ARROW =>
        if (!sepRegions.isEmpty && sepRegions.head == lastToken)
          sepRegions = sepRegions.tail
      case STRINGLIT =>
        if (inMultiLineInterpolation)
          sepRegions = sepRegions.tail.tail
        else if (inStringInterpolation)
          sepRegions = sepRegions.tail
      case _ =>
    }

    /** Produce next token, filling TokenData fields of Scanner.
     */
    def nextToken(): Unit = {
      val lastToken = token
      adjustSepRegions(lastToken)

      // Read a token or copy it from `next` tokenData
      if (next.token == EMPTY) {
        lastOffset = lastCharOffset
        if (inStringInterpolation) fetchStringPart()
        else fetchToken()
        if (token == ERROR) adjustSepRegions(STRINGLIT)
      } else {
        this copyFrom next
        next.token = EMPTY
      }

      /** Insert NEWLINE or NEWLINES if
       *  - we are after a newline
       *  - we are within a { ... } or on toplevel (wrt sepRegions)
       *  - the current token can start a statement and the one before can end it
       *  insert NEWLINES if we are past a blank line, NEWLINE otherwise
       */
      if (isAfterLineEnd() &&
          (canEndStatTokens contains lastToken) &&
          (canStartStatTokens contains token) &&
          (sepRegions.isEmpty || sepRegions.head == RBRACE)) {
        next copyFrom this
        //  todo: make offset line-end of previous line?
        offset = if (lineStartOffset <= offset) lineStartOffset else lastLineStartOffset
        token = if (pastBlankLine()) NEWLINES else NEWLINE
      }

      postProcessToken()
      // print("[" + this +"]")
    }

    def postProcessToken() = {
      // Join CASE + CLASS => CASECLASS, CASE + OBJECT => CASEOBJECT, SEMI + ELSE => ELSE
      def lookahead() = {
        prev copyFrom this
        fetchToken()
      }
      def reset(nextLastOffset: Offset) = {
        lastOffset = nextLastOffset
        next copyFrom this
        this copyFrom prev
      }
      def fuse(tok: Int) = {
        token = tok
        offset = prev.offset
        lastOffset = prev.lastOffset
      }
      if (token == CASE) {
        val nextLastOffset = lastCharOffset
        lookahead()
        if (token == CLASS) fuse(CASECLASS)
        else if (token == OBJECT) fuse(CASEOBJECT)
        else reset(nextLastOffset)
      } else if (token == SEMI) {
        val nextLastOffset = lastCharOffset
        lookahead()
        if (token != ELSE) reset(nextLastOffset)
      }
    }

    /** Is current token first one after a newline? */
    def isAfterLineEnd(): Boolean =
      lastOffset < lineStartOffset &&
      (lineStartOffset <= offset ||
       lastOffset < lastLineStartOffset && lastLineStartOffset <= offset)

    /** Is there a blank line between the current token and the last one?
     *  @pre  afterLineEnd().
     */
    private def pastBlankLine(): Boolean = {
      val end = offset
      def recur(idx: Offset, isBlank: Boolean): Boolean =
        idx < end && {
          val ch = buf(idx)
          if (ch == LF || ch == FF) isBlank || recur(idx + 1, true)
          else recur(idx + 1, isBlank && ch <= ' ')
        }
      recur(lastOffset, false)
    }

    /** read next token, filling TokenData fields of Scanner.
     */
    protected final def fetchToken(): Unit = {
      offset = charOffset - 1
      (ch: @switch) match {
        case ' ' | '\t' | CR | LF | FF =>
          nextChar()
          fetchToken()
        case 'A' | 'B' | 'C' | 'D' | 'E' |
             'F' | 'G' | 'H' | 'I' | 'J' |
             'K' | 'L' | 'M' | 'N' | 'O' |
             'P' | 'Q' | 'R' | 'S' | 'T' |
             'U' | 'V' | 'W' | 'X' | 'Y' |
             'Z' | '$' | '_' |
             'a' | 'b' | 'c' | 'd' | 'e' |
             'f' | 'g' | 'h' | 'i' | 'j' |
             'k' | 'l' | 'm' | 'n' | 'o' |
             'p' | 'q' | 'r' | 's' | 't' |
             'u' | 'v' | 'w' | 'x' | 'y' |
             'z' =>
          putChar(ch)
          nextChar()
          getIdentRest()
          if (ch == '"' && token == IDENTIFIER)
            token = INTERPOLATIONID
        case '<' => // is XMLSTART?
          def fetchLT() = {
            val last = if (charOffset >= 2) buf(charOffset - 2) else ' '
            nextChar()
            last match {
              case ' ' | '\t' | '\n' | '{' | '(' | '>' if isNameStart(ch) || ch == '!' || ch == '?' =>
                token = XMLSTART
              case _ =>
                // Console.println("found '<', but last is '" + in.last +"'"); // DEBUG
                putChar('<')
                getOperatorRest()
            }
          }
          fetchLT
        case '~' | '!' | '@' | '#' | '%' |
             '^' | '*' | '+' | '-' | /*'<' | */
             '>' | '?' | ':' | '=' | '&' |
             '|' | '\\' =>
          putChar(ch)
          nextChar()
          getOperatorRest()
        case '/' =>
          if (skipComment()) {
            fetchToken()
          } else {
            putChar('/')
            getOperatorRest()
          }
        case '0' =>
          def fetchZero() = {
            putChar(ch)
            nextChar()
            if (ch == 'x' || ch == 'X') {
              nextChar()
              base = 16
            } else {
              /**
               * What should leading 0 be in the future? It is potentially dangerous
               *  to let it be base-10 because of history.  Should it be an error? Is
               *  there a realistic situation where one would need it?
               */
              if (isDigit(ch))
                error("Non-zero numbers may not have a leading zero.")
              base = 10
            }
            getNumber()
          }
          fetchZero
        case '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9' =>
          base = 10
          getNumber()
        case '`' =>
          getBackquotedIdent()
        case '\"' =>
          def fetchDoubleQuote() = {
            if (token == INTERPOLATIONID) {
              nextRawChar()
              if (ch == '\"') {
                nextRawChar()
                if (ch == '\"') {
                  nextRawChar()
                  getStringPart(multiLine = true)
                  sepRegions = STRINGPART :: sepRegions // indicate string part
                  sepRegions = STRINGLIT :: sepRegions // once more to indicate multi line string part
                } else {
                  token = STRINGLIT
                  strVal = ""
                }
              } else {
                getStringPart(multiLine = false)
                sepRegions = STRINGLIT :: sepRegions // indicate single line string part
              }
            } else {
              nextChar()
              if (ch == '\"') {
                nextChar()
                if (ch == '\"') {
                  nextRawChar()
                  getRawStringLit()
                } else {
                  token = STRINGLIT
                  strVal = ""
                }
              } else {
                getStringLit()
              }
            }
          }
          fetchDoubleQuote
        case '\'' =>
          def fetchSingleQuote() = {
            nextChar()
            if (isIdentifierStart(ch))
              charLitOr(getIdentRest)
            else if (isOperatorPart(ch) && (ch != '\\'))
              charLitOr(getOperatorRest)
            else {
              getLitChar()
              if (ch == '\'') {
                nextChar()
                token = CHARLIT
                setStrVal()
              } else {
                error("unclosed character literal")
              }
            }
          }
          fetchSingleQuote
        case '.' =>
          nextChar()
          if ('0' <= ch && ch <= '9') {
            putChar('.'); getFraction(); setStrVal()
          } else {
            token = DOT
          }
        case ';' =>
          nextChar(); token = SEMI
        case ',' =>
          nextChar(); token = COMMA
        case '(' =>
          enterBlock(); nextChar(); token = LPAREN
        case '{' =>
          enterBlock(); nextChar(); token = LBRACE
        case ')' =>
          exitBlock(); nextChar(); token = RPAREN
        case '}' =>
          exitBlock(); nextChar(); token = RBRACE
        case '[' =>
          nextChar(); token = LBRACKET
        case ']' =>
          nextChar(); token = RBRACKET
        case SU =>
          if (isAtEnd) token = EOF
          else {
            error("illegal character")
            nextChar()
          }
        case _ =>
          def fetchOther() = {
            if (ch == '\u21D2') {
              nextChar(); token = ARROW
            } else if (ch == '\u2190') {
              nextChar(); token = LARROW
            } else if (Character.isUnicodeIdentifierStart(ch)) {
              putChar(ch)
              nextChar()
              getIdentRest()
            } else if (isSpecial(ch)) {
              putChar(ch)
              nextChar()
              getOperatorRest()
            } else {
              error(f"illegal character '\\u${ch: Int}%04x'")
              nextChar()
            }
          }
          fetchOther
      }
    }

    private def skipComment(): Boolean = {
      def appendToComment(ch: Char) =
        if (keepComments) commentBuf.append(ch)
      def nextChar() = {
        appendToComment(ch)
        Scanner.this.nextChar()
      }
      def skipLine(): Unit = {
        nextChar()
        if ((ch != CR) && (ch != LF) && (ch != SU)) skipLine()
      }
      @tailrec
      def skipComment(): Unit = {
        if (ch == '/') {
          nextChar()
          if (ch == '*') nestedComment()
          skipComment()
        }
        else if (ch == '*') {
          do nextChar() while (ch == '*')
          if (ch == '/') nextChar()
          else skipComment()
        }
        else if (ch == SU) incompleteInputError("unclosed comment")
        else { nextChar(); skipComment() }
      }
      def nestedComment() = { nextChar(); skipComment() }
      val start = lastCharOffset
      def finishComment(): Boolean = {
        if (keepComments) {
          val pos = Position(start, charOffset, start)
          val comment = Comment(pos, flushBuf(commentBuf))

          if (comment.isDocComment)
            docsPerBlockStack = (docsPerBlockStack.head :+ comment) :: docsPerBlockStack.tail
        }

        true
      }
      nextChar()
      if (ch == '/') { skipLine(); finishComment() }
      else if (ch == '*') { nextChar(); skipComment(); finishComment() }
      else false
    }

// Identifiers ---------------------------------------------------------------

    private def getBackquotedIdent(): Unit = {
      nextChar()
      getLitChars('`')
      if (ch == '`') {
        nextChar()
        finishNamed(BACKQUOTED_IDENT)
        if (name.length == 0)
          error("empty quoted identifier")
        else if (name == nme.WILDCARD)
          error("wildcard invalid as backquoted identifier")
      }
      else error("unclosed quoted identifier")
    }

    private def getIdentRest(): Unit = (ch: @switch) match {
      case 'A' | 'B' | 'C' | 'D' | 'E' |
           'F' | 'G' | 'H' | 'I' | 'J' |
           'K' | 'L' | 'M' | 'N' | 'O' |
           'P' | 'Q' | 'R' | 'S' | 'T' |
           'U' | 'V' | 'W' | 'X' | 'Y' |
           'Z' | '$' |
           'a' | 'b' | 'c' | 'd' | 'e' |
           'f' | 'g' | 'h' | 'i' | 'j' |
           'k' | 'l' | 'm' | 'n' | 'o' |
           'p' | 'q' | 'r' | 's' | 't' |
           'u' | 'v' | 'w' | 'x' | 'y' |
           'z' |
           '0' | '1' | '2' | '3' | '4' |
           '5' | '6' | '7' | '8' | '9' =>
        putChar(ch)
        nextChar()
        getIdentRest()
      case '_' =>
        putChar(ch)
        nextChar()
        getIdentOrOperatorRest()
      case SU => // strangely enough, Character.isUnicodeIdentifierPart(SU) returns true!
        finishNamed()
      case _ =>
        if (Character.isUnicodeIdentifierPart(ch)) {
          putChar(ch)
          nextChar()
          getIdentRest()
        } else {
          finishNamed()
        }
    }

    private def getOperatorRest(): Unit = (ch: @switch) match {
      case '~' | '!' | '@' | '#' | '%' |
           '^' | '*' | '+' | '-' | '<' |
           '>' | '?' | ':' | '=' | '&' |
           '|' | '\\' =>
        putChar(ch); nextChar(); getOperatorRest()
      case '/' =>
        if (skipComment()) finishNamed()
        else { putChar('/'); getOperatorRest() }
      case _ =>
        if (isSpecial(ch)) { putChar(ch); nextChar(); getOperatorRest() }
        else finishNamed()
    }

    private def getIdentOrOperatorRest(): Unit = {
      if (isIdentifierPart(ch))
        getIdentRest()
      else ch match {
        case '~' | '!' | '@' | '#' | '%' |
             '^' | '*' | '+' | '-' | '<' |
             '>' | '?' | ':' | '=' | '&' |
             '|' | '\\' | '/' =>
          getOperatorRest()
        case _ =>
          if (isSpecial(ch)) getOperatorRest()
          else finishNamed()
      }
    }


// Literals -----------------------------------------------------------------

    private def getStringLit() = {
      getLitChars('"')
      if (ch == '"') {
        setStrVal()
        nextChar()
        token = STRINGLIT
      } else error("unclosed string literal")
    }

    private def getRawStringLit(): Unit = {
      if (ch == '\"') {
        nextRawChar()
        if (isTripleQuote()) {
          setStrVal()
          token = STRINGLIT
        } else
          getRawStringLit()
      } else if (ch == SU) {
        incompleteInputError("unclosed multi-line string literal")
      } else {
        putChar(ch)
        nextRawChar()
        getRawStringLit()
      }
    }

    @annotation.tailrec private def getStringPart(multiLine: Boolean): Unit = {
      def finishStringPart() = {
        setStrVal()
        token = STRINGPART
        next.lastOffset = charOffset - 1
        next.offset = charOffset - 1
      }
      if (ch == '"') {
        if (multiLine) {
          nextRawChar()
          if (isTripleQuote()) {
            setStrVal()
            token = STRINGLIT
          } else
            getStringPart(multiLine)
        } else {
          nextChar()
          setStrVal()
          token = STRINGLIT
        }
      } else if (ch == '$') {
        nextRawChar()
        if (ch == '$') {
          putChar(ch)
          nextRawChar()
          getStringPart(multiLine)
        } else if (ch == '{') {
          finishStringPart()
          nextRawChar()
          next.token = LBRACE
        } else if (Character.isUnicodeIdentifierStart(ch)) {
          finishStringPart()
          do {
            putChar(ch)
            nextRawChar()
          } while (ch != SU && Character.isUnicodeIdentifierPart(ch))
          finishNamed(target = next)
        } else {
          error("invalid string interpolation: `$$', `$'ident or `$'BlockExpr expected")
        }
      } else {
        val isUnclosedLiteral = !isUnicodeEscape && (ch == SU || (!multiLine && (ch == CR || ch == LF)))
        if (isUnclosedLiteral) {
          if (multiLine)
            incompleteInputError("unclosed multi-line string literal")
          else
            error("unclosed string literal")
        }
        else {
          putChar(ch)
          nextRawChar()
          getStringPart(multiLine)
        }
      }
    }

    private def fetchStringPart() = {
      offset = charOffset - 1
      getStringPart(multiLine = inMultiLineInterpolation)
    }

    private def isTripleQuote(): Boolean =
      if (ch == '"') {
        nextRawChar()
        if (ch == '"') {
          nextChar()
          while (ch == '"') {
            putChar('"')
            nextChar()
          }
          true
        } else {
          putChar('"')
          putChar('"')
          false
        }
      } else {
        putChar('"')
        false
      }

    /** copy current character into litBuf, interpreting any escape sequences,
     *  and advance to next character.
     */
    protected def getLitChar(): Unit =
      if (ch == '\\') {
        nextChar()
        if ('0' <= ch && ch <= '7') {
          val leadch: Char = ch
          var oct: Int = digit2int(ch, 8)
          nextChar()
          if ('0' <= ch && ch <= '7') {
            oct = oct * 8 + digit2int(ch, 8)
            nextChar()
            if (leadch <= '3' && '0' <= ch && ch <= '7') {
              oct = oct * 8 + digit2int(ch, 8)
              nextChar()
            }
          }
          putChar(oct.toChar)
        } else {
          ch match {
            case 'b'  => putChar('\b')
            case 't'  => putChar('\t')
            case 'n'  => putChar('\n')
            case 'f'  => putChar('\f')
            case 'r'  => putChar('\r')
            case '\"' => putChar('\"')
            case '\'' => putChar('\'')
            case '\\' => putChar('\\')
            case _    => invalidEscape()
          }
          nextChar()
        }
      } else  {
        putChar(ch)
        nextChar()
      }

    protected def invalidEscape(): Unit = {
      error("invalid escape character", charOffset - 1)
      putChar(ch)
    }

    private def getLitChars(delimiter: Char) = {
      while (ch != delimiter && !isAtEnd && (ch != SU && ch != CR && ch != LF || isUnicodeEscape))
        getLitChar()
    }

    /** read fractional part and exponent of floating point number
     *  if one is present.
     */
    protected def getFraction(): Unit = {
      token = DOUBLELIT
      while ('0' <= ch && ch <= '9') {
        putChar(ch)
        nextChar()
      }
      if (ch == 'e' || ch == 'E') {
        val lookahead = lookaheadReader
        lookahead.nextChar()
        if (lookahead.ch == '+' || lookahead.ch == '-') {
          lookahead.nextChar()
        }
        if ('0' <= lookahead.ch && lookahead.ch <= '9') {
          putChar(ch)
          nextChar()
          if (ch == '+' || ch == '-') {
            putChar(ch)
            nextChar()
          }
          while ('0' <= ch && ch <= '9') {
            putChar(ch)
            nextChar()
          }
        }
        token = DOUBLELIT
      }
      if (ch == 'd' || ch == 'D') {
        putChar(ch)
        nextChar()
        token = DOUBLELIT
      } else if (ch == 'f' || ch == 'F') {
        putChar(ch)
        nextChar()
        token = FLOATLIT
      }
      checkNoLetter()
    }
    def checkNoLetter(): Unit = {
      if (isIdentifierPart(ch) && ch >= ' ')
        error("Invalid literal number")
    }

    /** Read a number into strVal and set base
    */
    protected def getNumber(): Unit = {
      while (digit2int(ch, base) >= 0) {
        putChar(ch)
        nextChar()
      }
      token = INTLIT
      if (base == 10 && ch == '.') {
        val isDefinitelyNumber = {
          val lookahead = lookaheadReader
          val c = lookahead.getc()
          (c: @switch) match {
            /** Another digit is a giveaway. */
            case '0' | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9' =>
              true

            /** Backquoted idents like 22.`foo`. */
            case '`' =>
              false

            /** These letters may be part of a literal, or a method invocation on an Int.
             */
            case 'd' | 'D' | 'f' | 'F' =>
              !isIdentifierPart(lookahead.getc())

            /** A little more special handling for e.g. 5e7 */
            case 'e' | 'E' =>
              val ch = lookahead.getc()
              !isIdentifierPart(ch) || (isDigit(ch) || ch == '+' || ch == '-')

            case x =>
              !isIdentifierStart(x)
          }
        }
        if (isDefinitelyNumber) {
          putChar(ch)
          nextChar()
          getFraction()
        }
      } else (ch: @switch) match {
        case 'e' | 'E' | 'f' | 'F' | 'd' | 'D' =>
          if (base == 10) getFraction()
        case 'l' | 'L' =>
          nextChar()
          token = LONGLIT
        case _ =>
      }
      setStrVal()
    }

    /** Parse character literal if current character is followed by \',
     *  or follow with given op and return a symbol literal token
     */
    def charLitOr(op: () => Unit): Unit = {
      putChar(ch)
      nextChar()
      if (ch == '\'') {
        nextChar()
        token = CHARLIT
        setStrVal()
      } else {
        op()
        token = SYMBOLLIT
        strVal = name.toString
      }
    }
    override def toString =
      showTokenDetailed(token) + {
        if ((identifierTokens contains token) || (literalTokens contains token)) " " + name
        else ""
      }

    def show: String = token match {
      case IDENTIFIER | BACKQUOTED_IDENT => s"id($name)"
      case CHARLIT => s"char($intVal)"
      case INTLIT => s"int($intVal)"
      case LONGLIT => s"long($intVal)"
      case FLOATLIT => s"float($floatVal)"
      case DOUBLELIT => s"double($floatVal)"
      case STRINGLIT => s"string($strVal)"
      case STRINGPART => s"stringpart($strVal)"
      case INTERPOLATIONID => s"interpolationid($name)"
      case SEMI => ";"
      case NEWLINE => ";"
      case NEWLINES => ";;"
      case COMMA => ","
      case _ => showToken(token)
    }

// (does not seem to be needed) def flush = { charOffset = offset; nextChar(); this }

    /* Resume normal scanning after XML */
    def resume(lastToken: Token) = {
      token = lastToken
      if (next.token != EMPTY && !ctx.reporter.hasErrors)
        error("unexpected end of input: possible missing '}' in XML block")

      nextToken()
    }

   /* Initialization: read first char, then first token */
    nextChar()
    nextToken()
  } // end Scanner

  // ------------- keyword configuration -----------------------------------

  val (lastKeywordStart, kwArray) = buildKeywordArray(keywords)
}
