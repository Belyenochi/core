package ai.mantik.testutils

import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.concurrent.Eventually
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, EitherValues, FlatSpec, Matchers }
import org.slf4j.LoggerFactory

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

abstract class TestBase extends FlatSpec with Matchers with BeforeAndAfterEach with BeforeAndAfterAll with Eventually with EitherExt {

  protected val timeout: FiniteDuration = 10.seconds
  protected val logger = LoggerFactory.getLogger(getClass)

  protected lazy val typesafeConfig: Config = ConfigFactory.load()

  def await[T](f: => Future[T]): T = {
    Await.result(f, timeout)
  }
}
