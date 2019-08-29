package ai.mantik.engine.integration

import java.io.File

import ai.mantik.ds.element.Bundle
import ai.mantik.engine.protos.ds.{ BundleEncoding, DataType }
import ai.mantik.engine.protos.graph_builder.{ ApplyRequest, GetRequest, LiteralRequest }
import ai.mantik.engine.protos.graph_executor.FetchItemRequest
import ai.mantik.engine.protos.sessions.CreateSessionRequest
import ai.mantik.engine.server.services.Converters
import ai.mantik.planner.repository.Errors
import ai.mantik.testutils.tags.IntegrationTest
import com.google.protobuf.empty.Empty

@IntegrationTest
class HelloWorldSpec extends IntegrationTestBase {

  val sampleFile = new File("bridge/tf/saved_model/test/resources/samples/double_multiply").toPath

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    context.pushLocalMantikFile(sampleFile)
  }

  it should "be possible to run a simple command" in {
    val response = engineClient.aboutService.version(Empty())
    response.version shouldNot be(empty)
  }

  it should "support a simple calculation" in {
    val session = engineClient.sessionService.createSession(CreateSessionRequest())
    val algorithm = engineClient.graphBuilder.get(GetRequest(sessionId = session.sessionId, name = "double_multiply"))
    val myBundle = ai.mantik.ds.element.Bundle.buildColumnWise
      .withPrimitives("x", 1.0, 2.0)
      .result
    val encodeBundle = await(Converters.encodeBundle(myBundle, BundleEncoding.ENCODING_JSON))
    val dataset = engineClient.graphBuilder.literal(
      LiteralRequest(
        sessionId = session.sessionId,
        bundle = Some(
          encodeBundle
        )
      )
    )
    val result = engineClient.graphBuilder.algorithmApply(
      ApplyRequest(sessionId = session.sessionId, datasetId = dataset.itemId, algorithmId = algorithm.itemId)
    )
    val evaluated = engineClient.graphExecutor.fetchDataSet(
      FetchItemRequest(
        sessionId = session.sessionId,
        datasetId = result.itemId,
        encoding = BundleEncoding.ENCODING_JSON
      )
    )
    val decoded = await(Converters.decodeBundle(evaluated.bundle.get))
    decoded shouldBe Bundle.buildColumnWise
      .withPrimitives("y", 2.0, 4.0)
      .result
  }

  it should "give access to a context" in {
    val context = engineClient.createContext()
    intercept[Errors.NotFoundException] {
      await(context.localRegistry.get("Not-existing"))
    }
  }
}
