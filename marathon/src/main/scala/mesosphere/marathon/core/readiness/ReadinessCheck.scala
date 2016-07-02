package mesosphere.marathon.core.readiness

import com.wix.accord.Validator
import com.wix.accord.dsl._
import mesosphere.marathon.api.v2.Validation._
import mesosphere.marathon.state.RunSpec
import org.apache.http.HttpStatus

import scala.concurrent.duration._

case class ReadinessCheck(
  name: String = ReadinessCheck.DefaultName,
  protocol: ReadinessCheck.Protocol = ReadinessCheck.DefaultProtocol,

  path: String = ReadinessCheck.DefaultPath,
  portName: String = ReadinessCheck.DefaultPortName,

  interval: FiniteDuration = ReadinessCheck.DefaultInterval,
  timeout: FiniteDuration = ReadinessCheck.DefaultTimeout,

  httpStatusCodesForReady: Set[Int] = ReadinessCheck.DefaultHttpStatusCodesForReady,
  preserveLastResponse: Boolean = ReadinessCheck.DefaultPreserveLastResponse)

object ReadinessCheck {
  import scala.language.implicitConversions

  val DefaultName = "readinessCheck"
  val DefaultProtocol = Protocol.HTTP
  val DefaultPortName = "http-api"
  val DefaultPath = "/"
  val DefaultInterval = 30.seconds
  val DefaultTimeout = 10.seconds
  val DefaultHttpStatusCodesForReady = Set(HttpStatus.SC_OK)
  val DefaultPreserveLastResponse = false

  sealed trait Protocol
  object Protocol {
    case object HTTP extends Protocol
    case object HTTPS extends Protocol
  }

  implicit def readinessCheckValidator(runSpec: RunSpec): Validator[ReadinessCheck] =
    validator[ReadinessCheck] { rc =>
      rc.name is notEmpty
      rc.path is notEmpty
      rc.portName is notEmpty
      rc.portName is oneOf(runSpec.portNames: _*)
      rc.timeout.toSeconds should be < rc.interval.toSeconds
      rc.timeout.toSeconds should be > 0L
      rc.httpStatusCodesForReady is notEmpty
    }
}
