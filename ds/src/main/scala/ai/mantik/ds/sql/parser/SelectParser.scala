package ai.mantik.ds.sql.parser

import ai.mantik.ds.sql.parser.AST.{ AnonymousReference, ExpressionNode, QueryNode, SelectColumnNode, SelectNode }
import org.parboiled2.{ ParseError, Parser, ParserInput, Rule1 }

import scala.collection.immutable

private[parser] trait SelectParser extends ExpressionParser with InnerQueryParser {
  self: Parser =>
  def Select: Rule1[SelectNode] = rule {
    (keyword("select") ~ SelectColumns ~
      optional(keyword("from") ~ SelectLikeInnerQuery) ~
      optional(keyword("where") ~ Expression)
    ) ~> { (columns, from, where) =>
        SelectNode(columns, where, from)
      }
  }

  def SelectColumns: Rule1[List[SelectColumnNode]] = rule {
    SelectAllColumns | SelectSomeColumns
  }

  def SelectAllColumns: Rule1[List[SelectColumnNode]] = rule {
    symbolw('*') ~ push(Nil: List[SelectColumnNode])
  }

  def SelectSomeColumns: Rule1[List[SelectColumnNode]] = rule {
    oneOrMore(SelectColumn).separatedBy(symbolw(',')) ~> { elements: immutable.Seq[SelectColumnNode] =>
      elements.toList
    }
  }

  def SelectColumn: Rule1[SelectColumnNode] = rule {
    (Expression ~ optional(keyword("as") ~ Identifier)) ~> { (expression, asValue) =>
      AST.SelectColumnNode(expression, asValue)
    }
  }
}
