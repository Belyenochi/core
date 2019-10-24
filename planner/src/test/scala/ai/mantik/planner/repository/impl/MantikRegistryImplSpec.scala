package ai.mantik.planner.repository.impl

import ai.mantik.ds.FundamentalType
import ai.mantik.elements.{ DataSetDefinition, ItemId, NamedMantikId, Mantikfile }
import ai.mantik.elements.registry.api.{ ApiFileUploadResponse, ApiLoginResponse, ApiPrepareUploadResponse, MantikRegistryApi, MantikRegistryApiCalls }
import ai.mantik.planner.repository.{ ContentTypes, MantikArtifact }
import ai.mantik.planner.util.TestBaseWithAkkaRuntime
import ai.mantik.testutils.TestBase
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.config.{ Config, ConfigFactory, ConfigValueFactory }
import net.reactivecore.fhttp.akka.{ ApiServer, ApiServerRoute }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher

import scala.concurrent.Future

class MantikRegistryImplSpec extends TestBaseWithAkkaRuntime {

  private val dummyPort = 9002

  override protected lazy val typesafeConfig: Config = ConfigFactory.load().withValue(
    "mantik.core.registry.url", ConfigValueFactory.fromAnyRef(s"http://localhost:${dummyPort}")
  )

  trait Env {
    val apiRoute = new ApiServerRoute {
      bind(MantikRegistryApiCalls.uploadFile).to {
        case (token, itemId, contentType, content) =>
          Future.successful(
            Right(ApiFileUploadResponse("file1"))
          )
      }

      bind(MantikRegistryApiCalls.prepareUpload).to {
        case (token, request) =>
          Future.successful(Right(ApiPrepareUploadResponse(Some(10))))
      }

      bind(MantikRegistryApiCalls.login).to { request =>
        Future.successful(Right(ApiLoginResponse("Token1234", None)))
      }
    }
    val fullFakeRoute = pathPrefix("api") { apiRoute }
    val server = new ApiServer(fullFakeRoute, port = dummyPort)
    akkaRuntime.lifecycle.addShutdownHook {
      server.close()
      Future.successful(())
    }
    val client = new MantikRegistryImpl()
  }

  it should "get a nice token" in new Env {
    await(client.token()) shouldBe "Token1234"
  }

  it should "not crash on empty chunks" in new Env {
    // Workaround Akka http crashes on generating empty chunks for body parts
    // MantikRegistryClient must filter them out.
    val emptyData = Source(
      List(ByteString.fromString("a"), ByteString(), ByteString.fromString("c")
      ))
    val mantikfile = Mantikfile.pure(
      DataSetDefinition(
        format = "natural",
        `type` = FundamentalType.Int32
      )
    )
    val response = await(client.addMantikArtifact(
      MantikArtifact(
        mantikfile.toJson,
        fileId = None,
        namedId = Some(NamedMantikId("hello_world")),
        itemId = ItemId.generate(),
        deploymentInfo = None
      ), payload = Some(ContentTypes.ZipFileContentType -> emptyData)
    ))
    response.fileId shouldBe Some("file1")
  }
}
