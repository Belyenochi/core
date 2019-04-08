package ai.mantik.planner.impl

import ai.mantik.executor.model._
import ai.mantik.planner.plugins.Plugins
import ai.mantik.planner.{ Plan, Planner, Source }
import ai.mantik.repository.FileRepository.{ FileGetResult, FileStorageResult }
import ai.mantik.repository._

/**
 * Raw Elements in Plan Construction.
 * Class should have no side effects (except nodeIdGenerator).
 */
class PlannerElements(formats: Plugins, isolationSpace: String, contentType: String) {

  /** Converts a plan to a job. */
  def sourcePlanToJob(sourcePlan: ResourcePlan): Plan = {
    Plan.seq(
      sourcePlan.preplan,
      Plan.RunJob(Job(isolationSpace, sourcePlan.graph, Some(contentType)))
    )
  }

  /** Converts a Literal into a push plan. */
  def literalToPushBundle(literal: Source.Literal, fileId: String): Plan = {
    literal match {
      case Source.BundleLiteral(content) =>
        Plan.PushBundle(content, fileId)
    }
  }

  /** Creates a [[ResourcePlan]] which saves data from it's sink to a file. */
  def createStoreFileNode(storage: FileStorageResult)(implicit nodeIdGenerator: NodeIdGenerator): ResourcePlan = {
    val node = Node(
      ExistingService(storage.executorClusterUrl),
      resources = Map(
        storage.resource -> ResourceType.Sink
      )
    )
    val nodeId = nodeIdGenerator.makeId()
    ResourcePlan(
      graph = Graph(
        Map(
          nodeId -> node
        )
      ),
      inputs = Seq(NodeResourceRef(nodeId, storage.resource))
    )
  }

  /** Creates a [[ResourcePlan]] which loads a file and represents it as output. */
  def loadFileNode(fileGetResult: FileGetResult)(implicit nodeIdGenerator: NodeIdGenerator): ResourcePlan = {
    val nodeId = nodeIdGenerator.makeId()
    val graph = Graph(
      nodes = Map(
        nodeId -> Node(
          ExistingService(fileGetResult.executorClusterUrl),
          resources = Map(fileGetResult.resource -> ResourceType.Source)
        )
      ),
      links = Seq.empty
    )
    ResourcePlan(
      graph = graph,
      outputs = Seq(NodeResourceRef(nodeId, fileGetResult.resource))
    )
  }

  /**
   * Generates the plan for a loaded Mantik DataSet.
   * @param artefact the mantik artefact
   * @param file the file, if one is present.
   */
  def dataSet(mantikfile: Mantikfile[DataSetDefinition], file: Option[FileGetResult])(implicit nodeIdGenerator: NodeIdGenerator): ResourcePlan = {
    val plugin = formats.pluginForFormat(mantikfile.definition.format).getOrElse {
      throw new Planner.FormatNotSupportedException(mantikfile.definition.format)
    }
    val nodeId = nodeIdGenerator.makeId()
    val (node, resourceId) = plugin.createClusterReader(mantikfile, file.map { f => f.executorClusterUrl -> f.resource })
    val graph = Graph(
      nodes = Map(
        nodeId -> node
      )
    )
    ResourcePlan(
      graph = graph,
      outputs = Seq(NodeResourceRef(nodeId, resourceId))
    )
  }

  /** Generates the plan for an algorithm which runtime data may come from a file. */
  def algorithm(mantikfile: Mantikfile[AlgorithmDefinition], file: Option[FileGetResult])(implicit nodeIdGenerator: NodeIdGenerator): ResourcePlan = {
    val plugin = formats.pluginForAlgorithm(mantikfile.definition.stack).getOrElse {
      throw new Planner.AlgorithmStackNotSupportedException(mantikfile.definition.stack)
    }
    val imageName = plugin.transformationContainerImage
    val applyResource = "apply"

    val node = Node(
      ContainerService(
        main = Container(
          imageName
        ),
        dataProvider = Some(
          createDataProvider(file, mantikfile)
        )
      ),
      Map(
        applyResource -> ResourceType.Transformer
      )
    )

    val nodeId = nodeIdGenerator.makeId()
    val graph = Graph(
      nodes = Map(
        nodeId -> node
      )
    )
    ResourcePlan(
      graph = graph,
      inputs = Seq(NodeResourceRef(nodeId, applyResource)),
      outputs = Seq(NodeResourceRef(nodeId, applyResource))
    )
  }

  /** Generates the plan for a trainable algorithm. */
  def trainableAlgorithm(mantikfile: Mantikfile[TrainableAlgorithmDefinition], file: Option[FileGetResult])(implicit nodeIdGenerator: NodeIdGenerator): ResourcePlan = {
    val plugin = formats.pluginForTrainableAlgorithm(mantikfile.definition.stack).getOrElse {
      throw new Planner.AlgorithmStackNotSupportedException(mantikfile.definition.stack)
    }
    val image = plugin.trainableContainerImage

    val trainResource = "train"
    val statsResource = "stats"
    val resultResource = "result"

    val containerService = ContainerService(
      main = Container(
        image
      ),
      dataProvider = Some(
        createDataProvider(file, mantikfile)
      )
    )

    val node = Node(
      containerService,
      Map(
        trainResource -> ResourceType.Sink,
        statsResource -> ResourceType.Source,
        resultResource -> ResourceType.Source
      )
    )

    val nodeId = nodeIdGenerator.makeId()
    val graph = Graph(
      nodes = Map(
        nodeId -> node
      )
    )
    ResourcePlan(
      graph = graph,
      inputs = Seq(NodeResourceRef(nodeId, trainResource)),
      outputs = Seq(
        NodeResourceRef(nodeId, resultResource),
        NodeResourceRef(nodeId, statsResource)
      )
    )
  }

  private def createDataProvider(file: Option[FileGetResult], mantikfile: Mantikfile[_ <: MantikDefinition]): DataProvider = {
    val payloadUrl = file.map { file =>
      file.executorClusterUrl + file.resource
    }
    DataProvider(
      url = payloadUrl,
      mantikfile = Some(mantikfile.json.spaces2),
      directory = mantikfile.definition.directory
    )
  }
}
