package ai.mantik.planner.repository.impl

import ai.mantik.elements.errors.ErrorCodes
import ai.mantik.planner.repository.{ ContentTypes, FileRepository }
import ai.mantik.planner.util.{ ErrorCodeTestUtils, TestBaseWithAkkaRuntime }
import ai.mantik.testutils.TempDirSupport
import akka.util.ByteString

import scala.util.Random

abstract class FileRepositorySpecBase extends TestBaseWithAkkaRuntime with TempDirSupport with ErrorCodeTestUtils {

  type RepoType <: FileRepository with NonAsyncFileRepository

  protected def createRepo(): RepoType

  trait Env {
    val repo = createRepo()
  }

  protected val testBytes = ByteString {
    val bytes = new Array[Byte](1000)
    Random.nextBytes(bytes)
    bytes
  }

  it should "save and load a file" in new Env {
    val info = await(repo.requestFileStorage(ContentTypes.MantikBundleContentType, false))
    val bytesWritten = repo.storeFileSync(info.fileId, testBytes)
    bytesWritten shouldBe testBytes.length
    val get = repo.getFileSync(info.fileId, false)
    get.isTemporary shouldBe false
    val (contentType, bytesAgain) = repo.getFileContentSync(info.fileId)
    contentType shouldBe ContentTypes.MantikBundleContentType
    bytesAgain shouldBe testBytes

    withClue("copy should work") {
      val store2 = await(repo.requestFileStorage(ContentTypes.MantikBundleContentType, false))
      await(repo.copy(info.fileId, store2.fileId))

      val (contentType, bytesAgain) = repo.getFileContentSync(store2.fileId)
      contentType shouldBe ContentTypes.MantikBundleContentType
      bytesAgain shouldBe testBytes
    }

    withClue("it should fail if copy destination has the wrong content type") {
      val store2 = await(repo.requestFileStorage("Other", false))
      interceptErrorCode(FileRepository.InvalidContentType) {
        await(repo.copy(info.fileId, store2.fileId))
      }

      interceptErrorCode(FileRepository.NotFoundCode) {
        repo.getFileContentSync(store2.fileId)
      }
    }
  }

  it should "know optimistic storage" in new Env {
    val info = await(repo.requestFileStorage(ContentTypes.MantikBundleContentType, true))

    interceptErrorCode(FileRepository.NotFoundCode) {
      repo.getFileSync(info.fileId, optimistic = false)
    }
    val getFileResponse = withClue("No exception expected here") {
      repo.getFileSync(info.fileId, optimistic = true)
    }
    getFileResponse.isTemporary shouldBe true
    // now store some content
    repo.storeFileSync(info.fileId, testBytes)

    repo.getFileContentSync(info.fileId) shouldBe (ContentTypes.MantikBundleContentType -> testBytes)
  }

  it should "allow file removal " in new Env {
    val req = repo.requestAndStoreSync(true, ContentTypes.MantikBundleContentType, testBytes)
    val result = await(repo.deleteFile(req.fileId))
    result shouldBe true
    interceptErrorCode(FileRepository.NotFoundCode) {
      repo.getFileContentSync(req.fileId)
    }
    val nonExistingResult = await(repo.deleteFile("unknown"))
    nonExistingResult shouldBe false
  }
}
