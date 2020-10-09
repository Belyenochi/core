package ai.mantik.planner.select

import ai.mantik.ds.TabularData
import ai.mantik.ds.sql.run.{ Compiler, TableGeneratorProgram }
import ai.mantik.ds.sql.{ AnonymousInput, Query, Select, Union }
import ai.mantik.elements.meta.MetaJson
import ai.mantik.elements.{ CombinerDefinition, MantikDefinition, MantikHeader, MantikHeaderMeta }
import ai.mantik.planner.BuiltInItems
import cats.implicits._
import io.circe.Json
import io.circe.syntax._

/**
 * Converts a [[TableGeneratorProgram]] to a [[MantikHeader]] for the select-Bridge.
 * @param program the compiled program
 * @param query human readable query
 */
case class SelectMantikHeaderBuilder(
    program: TableGeneratorProgram,
    inputs: Vector[TabularData],
    query: String
) {

  def definition: CombinerDefinition = {
    CombinerDefinition(
      bridge = BuiltInItems.SelectBridgeName,
      input = inputs,
      output = Vector(
        program.result
      )
    )
  }

  def toMantikHeader: MantikHeader[CombinerDefinition] = {
    val defJson = (definition: MantikDefinition).asJsonObject.add(
      "program", program.asJson
    ).add(
        "query", Json.fromString(query)
      )
    MantikHeader(definition, MetaJson.withoutMetaVariables(defJson), MantikHeaderMeta())
  }
}

object SelectMantikHeaderBuilder {

  /**
   * Compile Query to a select mantikHeader.
   * @return either an error or a mantikHeader which can execute the selection.
   */
  def compileToMantikHeader(query: Query): Either[String, MantikHeader[CombinerDefinition]] = {
    for {
      program <- Compiler.compile(query)
      inputs <- query.figureOutInputPorts
    } yield {
      SelectMantikHeaderBuilder(program, inputs, query.toStatement).toMantikHeader
    }
  }
}
