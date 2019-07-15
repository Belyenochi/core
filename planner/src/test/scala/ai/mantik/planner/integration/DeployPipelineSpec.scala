package ai.mantik.planner.integration

import ai.mantik.ds.helper.circe.CirceJson
import ai.mantik.ds.{ FundamentalType, TabularData }
import ai.mantik.planner.select.Select
import ai.mantik.planner.{ Algorithm, Pipeline }
import ai.mantik.testutils.HttpSupport
import ai.mantik.testutils.tags.IntegrationTest
import akka.util.ByteString

@IntegrationTest
class DeployPipelineSpec extends IntegrationTestBase with Samples with HttpSupport {

  it should "be possible to deploy a pipeline" in new EnvWithAlgorithm {

    val inputAdaptor = Algorithm.fromSelect(Select.parse(
      TabularData(
        "x" -> FundamentalType.Int32
      ),
      "select CAST(x as float64)"
    ).forceRight)

    val outputAdapter = Algorithm.fromSelect(
      Select.parse(doubleMultiply.functionType.output.asInstanceOf[TabularData], "select CAST (y as int32)").forceRight
    )

    val pipeline = Pipeline.build(
      inputAdaptor,
      doubleMultiply,
      outputAdapter
    )

    pipeline.state.get.deployment shouldBe empty
    doubleMultiply.state.get.deployment shouldBe empty

    val deploymentState = context.execute(pipeline.deploy(ingressName = Some("pipe1")))

    pipeline.state.get.deployment shouldBe Some(deploymentState)
    deploymentState.externalUrl shouldNot be(empty)

    // Sub algorithms are now also deployed
    doubleMultiply.state.get.deployment shouldBe 'defined

    val applyUrl = s"${deploymentState.externalUrl.get}/apply"
    val sampleData = ByteString("[[4],[5]]")

    val response = eventually {
      httpPost(applyUrl, "application/json", sampleData)
    }
    val responseParsed = CirceJson.forceParseJson(response.utf8String)
    responseParsed shouldBe CirceJson.forceParseJson("[[8],[10]]")
  }
}
