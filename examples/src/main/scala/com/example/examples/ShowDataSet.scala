package com.example.examples

import ai.mantik.ds.{FundamentalType, TabularData}
import ai.mantik.ds.element.{Bundle, TabularBundle}
import ai.mantik.planner.{DataSet, PlanningContext}

object ShowDataSet extends ExampleBase {

  override protected def run(implicit context: PlanningContext): Unit = {
    val id = "sample1"

    val ds = DataSet
      .literal(
        TabularBundle
          .build(
            TabularData(
              "x" -> FundamentalType.Int32,
              "y" -> FundamentalType.StringType
            )
          )
          .row(1, "Hello")
          .row(2, "World")
          .result
      )
      .tag(id)

    ds.save().run()

    val result = context.loadDataSet(id).fetch.run()

    println(result)
  }
}
