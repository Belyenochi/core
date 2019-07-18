package ai.mantik.planner.repository.impl

import ai.mantik.planner.repository.{ ContentTypes, Errors, FileRepository }
import ai.mantik.planner.util.TestBaseWithAkkaRuntime
import ai.mantik.planner.utils.AkkaRuntime
import ai.mantik.testutils.{ AkkaSupport, FakeClock, TestBase }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.config.{ ConfigFactory, ConfigValueFactory }

import scala.util.Random

abstract class FileRepositorySpecBase extends TestBaseWithAkkaRuntime {

  override protected def config = ConfigFactory.load().withValue(
    "mantik.repository.fileRepository.port", ConfigValueFactory.fromAnyRef(0)
  )

  protected val MantikBundleContentType = ContentType.apply(
    MediaType.custom(ContentTypes.MantikBundleContentType, true).asInstanceOf[MediaType.Binary]
  )

  // Hooks for derived testcases
  type FileRepoType <: FileRepository with NonAsyncFileRepository

  protected def createRepo: FileRepoType

  protected def filePrefix = "/files/"

  protected def withRepo[T](f: FileRepoType => T): Unit = {
    val repo = createRepo
    try {
      f(repo)
    } finally {
      repo.shutdown()
    }
  }

  protected val testBytes = ByteString {
    val bytes = new Array[Byte](1000)
    Random.nextBytes(bytes)
    bytes
  }

  // Custom Content Type

  protected def fileUri(repo: FileRepoType): Uri = {
    Uri(filePrefix).resolvedAgainst(rootUri(repo))
  }

  protected def rootUri(repo: FileRepository): Uri = {
    val address = repo.address()
    Uri(s"http://localhost:${address.getPort}")
  }

  it should "return 200 on root paths" in {
    withRepo { repo =>
      val response = await(Http().singleRequest(HttpRequest(uri = fileUri(repo))))
      response.status.intValue() shouldBe 200
      val response2 = await(Http().singleRequest(HttpRequest(uri = s"http://localhost:${repo.address().getPort}")))
      response2.status.intValue() shouldBe 200
    }
  }

  it should "allow file upload and download" in {
    withRepo { repo =>
      val s = await(repo.requestFileStorage(true))
      s.path shouldBe s"files/${s.fileId}"

      val uri = Uri(s.fileId).resolvedAgainst(fileUri(repo))

      val postRequest = HttpRequest(method = HttpMethods.POST, uri = uri)
        .withEntity(HttpEntity(MantikBundleContentType, testBytes))

      val postResponse = await(Http().singleRequest(
        postRequest
      ))
      postResponse.status.isSuccess() shouldBe true

      val getRequest = HttpRequest(uri = uri).addHeader(
        Accept(MantikBundleContentType.mediaType)
      )
      val getResponse = await(Http().singleRequest(getRequest))
      getResponse.status.intValue() shouldBe 200
      val bytes = collectByteSource(getResponse.entity.dataBytes)
      bytes shouldBe testBytes
    }
  }

  it should "allow direct storage" in {
    withRepo { repo =>
      val s = await(repo.requestFileStorage(true))
      val sink = await(repo.storeFile(s.fileId, ContentTypes.MantikBundleContentType))
      await(Source.single(testBytes).runWith(sink))

      val getResult = await(repo.requestFileGet(s.fileId))
      getResult.fileId shouldBe s.fileId
      getResult.contentType shouldBe Some(ContentTypes.MantikBundleContentType)

      val source = await(repo.loadFile(s.fileId))
      val bytes = collectByteSource(source)
      bytes shouldBe testBytes
    }
  }

  it should "know optimistic storage" in {
    withRepo { repo =>
      val info = await(repo.requestFileStorage(true))

      intercept[Errors.NotFoundException] {
        repo.getFileSync(info.fileId, optimistic = false)
      }
      val getFileResponse = withClue("No exception expected here") {
        repo.getFileSync(info.fileId, optimistic = true)
      }
      // now store some content
      repo.storeFileSync(info.fileId, ContentTypes.MantikBundleContentType, testBytes)

      val uri = Uri(getFileResponse.path).resolvedAgainst(rootUri(repo))
      val getRequest = HttpRequest(uri = uri).addHeader(
        Accept(MantikBundleContentType.mediaType)
      )
      val getResponse = await(Http().singleRequest(getRequest))
      getResponse.status.intValue() shouldBe 200
      val bytes = collectByteSource(getResponse.entity.dataBytes)
      bytes shouldBe testBytes
    }
  }

  it should "know it's address" in {
    withRepo { repo =>
      val address = repo.address()
      address.getAddress.getHostAddress shouldNot startWith("127.0.") // No loopback devices
    }
  }

  it should "allow file removal " in {
    withRepo { repo =>
      val req = repo.requestAndStoreSync(true, ContentTypes.MantikBundleContentType, testBytes)
      val result = await(repo.deleteFile(req.fileId))
      result shouldBe true
      intercept[Errors.NotFoundException] {
        repo.getFileContentSync(req.fileId)
      }
      val nonExistingResult = await(repo.deleteFile("unknown"))
      nonExistingResult shouldBe false
    }
  }

}

