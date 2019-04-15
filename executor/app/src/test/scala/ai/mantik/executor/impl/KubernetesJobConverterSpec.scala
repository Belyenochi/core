package ai.mantik.executor.impl

import java.nio.charset.StandardCharsets

import ai.mantik.executor.Config
import ai.mantik.executor.model._
import ai.mantik.executor.model.docker.{ Container, DockerConfig, DockerLogin }
import ai.mantik.testutils.TestBase
import io.circe.Json
import skuber.RestartPolicy
import io.circe.syntax._

class KubernetesJobConverterSpec extends TestBase {

  val config = Config().copy(
    sideCar = Container("my_sidecar", Seq("sidecar_arg")),
    coordinator = Container("my_coordinator", Seq("coordinator_arg")),
    payloadPreparer = Container("payload_preparer"),
    namespacePrefix = "systemtest-",
    podTrackerId = "mantik-executor",
    dockerConfig = DockerConfig().copy(
      logins = Seq(
        DockerLogin("repo1", "user1", "password1")
      )
    )
  )

  val simpleAbJob = Job(
    "helloworld",
    graph = Graph(
      nodes = Map(
        "A" -> Node.source(
          ContainerService(
            main = Container(
              image = "executor_sample_source"
            )
          )
        ),
        "B" -> Node.sink(
          ContainerService(
            main = Container(
              image = "executor_sample_sink"
            )
          )
        )
      ),
      links = Link.links(
        NodeResourceRef("A", ExecutorModelDefaults.SourceResource) -> NodeResourceRef("B", ExecutorModelDefaults.SinkResource)
      )
    ),
    contentType = Some("application/my-content-type"),
    extraLogins = Seq(
      DockerLogin("repo2", "user2", "password2")
    )
  )

  trait SimpleAbEnv {
    val converter = new KubernetesJobConverter(config, simpleAbJob, "job1")
    val podNameA = converter.namer.podName("A")
    val podNameB = converter.namer.podName("B")
    val ipMapping = Map(
      podNameA -> "192.168.1.1",
      podNameB -> "192.168.1.2"
    )
  }

  it should "create nice pods" in new SimpleAbEnv {
    val pods = converter.pods
    pods.size shouldBe 2
    withClue("It should have disabled restart policy") {
      pods.foreach { pod =>
        pod.spec.get.restartPolicy shouldBe RestartPolicy.Never
      }
    }
    withClue("It should all have the job embedded") {
      pods.foreach { pod =>
        val labels = pod.metadata.labels
        labels shouldBe Map(
          "jobId" -> "job1",
          "trackerId" -> config.podTrackerId,
          "role" -> KubernetesJobConverter.WorkerRole
        )
      }
    }
    withClue("It should embed a sidecar for every one") {
      pods.foreach { pod =>
        val spec = pod.spec.get
        spec.containers.size shouldBe 2
        val sidecar = spec.containers.find(_.name == "sidecar").get
        sidecar.image shouldBe config.sideCar.image
        sidecar.args shouldBe Seq("sidecar_arg", "-url", "http://localhost:8502", "-shutdown")
      }
    }
  }

  it should "create a coordinator plan" in new SimpleAbEnv {
    converter.coordinatorPlan(ipMapping) shouldBe CoordinatorPlan(
      nodes = Map(
        "A" -> CoordinatorPlan.Node("192.168.1.1:8503"),
        "B" -> CoordinatorPlan.Node("192.168.1.2:8503")
      ),
      flows = Seq(
        Seq(NodeResourceRef("A", "out"), NodeResourceRef("B", "in"))
      ),
      contentType = Some("application/my-content-type")
    )
  }

  it should "create a nice config ConfigMap" in new SimpleAbEnv {
    val configMap = converter.configuration(ipMapping)
    configMap.metadata.name shouldBe converter.namer.configName
    configMap.data shouldBe Map(
      "plan" -> converter.coordinatorPlan(ipMapping).asJson.toString
    )
  }

  it should "create a nice job" in new SimpleAbEnv {
    pending
  }

  "convertNode" should "like data containers" in new SimpleAbEnv {
    val node = Node.sink(
      ContainerService(
        main = Container(
          image = "runner"
        ),
        dataProvider = Some(DataProvider(
          url = Some("url1"),
          mantikfile = Some("mf1"),
          directory = Some("dir1")
        ))
      )
    )
    val converted = converter.convertNode("A", node)
    val spec = converted.spec.get
    spec.containers.size shouldBe 2 // sidecar, main
    spec.containers.map(_.image) should contain theSameElementsAs Seq("runner", config.sideCar.image)
    spec.containers.find(_.name == "main").get.volumeMounts.map(_.name) shouldBe List("data")
    spec.initContainers.size shouldBe 1
    spec.initContainers.head.image shouldBe "payload_preparer"
    spec.initContainers.head.args shouldBe List("-url", "url1", "-mantikfile", "bWYx", "-pdir", "dir1")
    spec.initContainers.head.volumeMounts.map(_.name) shouldBe List("data")
    spec.volumes.map(_.name) shouldBe List("data")

    withClue("there must be an fsGroup element") {
      spec.securityContext.flatMap(_.fsGroup) shouldBe Some(1000)
    }
  }

  "pullSecrets" should "convert pull screts" in new SimpleAbEnv {
    val secrets = converter.pullSecret.get
    secrets.name shouldBe "job-job1-pullsecret"
    secrets.metadata.labels.get("jobId") shouldBe Some("job1")
    val value = secrets.data.ensuring(_.size == 1).get(".dockerconfigjson").get
    val parsed = io.circe.parser.parse(new String(value, StandardCharsets.UTF_8)).right.get
    parsed shouldBe Json.obj(
      "auths" -> Json.obj(
        "repo1" -> Json.obj(
          "username" -> Json.fromString("user1"),
          "password" -> Json.fromString("password1")
        ),
        "repo2" -> Json.obj(
          "username" -> Json.fromString("user2"),
          "password" -> Json.fromString("password2")
        )
      )
    )
  }
}
