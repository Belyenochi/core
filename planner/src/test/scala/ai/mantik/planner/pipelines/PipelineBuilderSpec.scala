package ai.mantik.planner.pipelines

import ai.mantik.ds.funcational.FunctionType
import ai.mantik.ds.sql.Select
import ai.mantik.ds.{ FundamentalType, TabularData }
import ai.mantik.elements
import ai.mantik.elements.PipelineStep.{ AlgorithmStep, SelectStep }
import ai.mantik.elements.{ AlgorithmDefinition, ItemId, MantikHeader, NamedMantikId, PipelineStep }
import ai.mantik.planner.impl.TestItems
import ai.mantik.planner.repository.ContentTypes
import ai.mantik.planner.{ Algorithm, DefinitionSource, PayloadSource, Source }
import ai.mantik.testutils.TestBase

class PipelineBuilderSpec extends TestBase {

  val algorithm1 = Algorithm(
    source = Source(DefinitionSource.Loaded(Some("algo1"), ItemId.generate()), PayloadSource.Loaded("file1", ContentTypes.MantikBundleContentType)),
    MantikHeader.pure(
      AlgorithmDefinition(
        bridge = TestItems.algoBridge.mantikId,
        `type` = FunctionType(
          input = TabularData("x" -> FundamentalType.Int32),
          output = TabularData("y" -> FundamentalType.StringType)
        )
      )
    ),
    TestItems.algoBridge
  )

  val algorithm2 = Algorithm(
    source = Source.constructed(PayloadSource.Loaded("file2", ContentTypes.MantikBundleContentType)),
    MantikHeader.pure(
      elements.AlgorithmDefinition(
        bridge = TestItems.algoBridge.mantikId,
        `type` = FunctionType(
          input = TabularData("y" -> FundamentalType.StringType),
          output = TabularData("z" -> FundamentalType.Float64)
        )
      )
    ),
    TestItems.algoBridge
  )

  val select =
    Select.parse(
      TabularData(
        "x" -> FundamentalType.Int32
      ), "select cast (x as string) as y"
    ).forceRight

  it should "build pipelines" in {
    val pipeline = PipelineBuilder.build(Seq(Right(algorithm1), Right(algorithm2))).forceRight
    pipeline.definitionSource shouldBe DefinitionSource.Constructed()
    pipeline.payloadSource shouldBe PayloadSource.Empty
    pipeline.resolved.steps shouldBe Seq(
      ResolvedPipelineStep.AlgorithmStep(algorithm1),
      ResolvedPipelineStep.AlgorithmStep(algorithm2)
    )

    withClue("Algorithms which are loaded are using their real ids") {
      val algo1 = pipeline.resolved.steps.head.asInstanceOf[ResolvedPipelineStep.AlgorithmStep].algorithm
      algo1.mantikId shouldBe an[NamedMantikId]
      val algo2 = pipeline.resolved.steps(1).asInstanceOf[ResolvedPipelineStep.AlgorithmStep].algorithm
      algo2.mantikId shouldBe an[ItemId]

      pipeline.mantikHeader.definition.referencedItems.size shouldBe 2
    }
  }

  it should "insert select steps, if possible" in {
    val pipeline = PipelineBuilder.build(Seq(Left(select), Right(algorithm2))).forceRight
    val step1 = pipeline.resolved.steps.head
    val encodedStep = step1
    encodedStep shouldBe ResolvedPipelineStep.SelectStep(select)
    val step2 = pipeline.resolved.steps(1)
    step2 shouldBe an[ResolvedPipelineStep.AlgorithmStep]

    pipeline.mantikHeader.definition.referencedItems.size shouldBe 1 // select is not referenced
  }
}
