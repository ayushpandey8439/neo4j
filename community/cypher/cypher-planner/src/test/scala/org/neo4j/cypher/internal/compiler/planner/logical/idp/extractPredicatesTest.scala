/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.compiler.planner.logical.idp

import org.neo4j.cypher.internal.ast.AstConstructionTestSupport
import org.neo4j.cypher.internal.expressions.AllIterablePredicate
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.FilterScope
import org.neo4j.cypher.internal.expressions.MultiRelationshipPathStep
import org.neo4j.cypher.internal.expressions.NilPathStep
import org.neo4j.cypher.internal.expressions.NodePathStep
import org.neo4j.cypher.internal.expressions.NoneIterablePredicate
import org.neo4j.cypher.internal.expressions.PathExpression
import org.neo4j.cypher.internal.expressions.SemanticDirection
import org.neo4j.cypher.internal.ir.VarPatternLength
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class extractPredicatesTest extends CypherFunSuite with AstConstructionTestSupport {
  test("()-[*]->()") {
    val (nodePredicates: Seq[Expression], relationshipPredicates: Seq[Expression], solvedPredicates: Seq[Expression]) =
      extractPredicates(Seq(), "r", "r-relationship", "r-node", "n", "  UNNAMED2")

    nodePredicates shouldBe empty
    relationshipPredicates shouldBe empty
    solvedPredicates shouldBe empty
  }

  test("(n)-[r* {prop: 42}]->()") {
    val rewrittenPredicate = AllIterablePredicate(FilterScope(varFor("  FRESHID15"), Some(propEquality("  FRESHID15", "prop", 42)))(pos), varFor("r"))(pos)

    val (nodePredicates: Seq[Expression], relationshipPredicates: Seq[Expression], solvedPredicates: Seq[Expression]) =
      extractPredicates(Seq(rewrittenPredicate), "r", "r-relationship", "r-node", "n", "  UNNAMED0")

    nodePredicates shouldBe empty
    relationshipPredicates shouldBe List(propEquality("r-relationship", "prop", 42))
    solvedPredicates shouldBe List(rewrittenPredicate)
  }

  test("(n)-[x*]->(m) WHERE ALL(r in x WHERE r.prop < 4)") {
    val rewrittenPredicate = AllIterablePredicate(
      FilterScope(varFor("r"), Some(propLessThan("r", "prop", 4)))(pos),
      function(
        "relationships",
        PathExpression(
          NodePathStep(
            varFor("n"),
            MultiRelationshipPathStep(varFor("x"), SemanticDirection.OUTGOING, Some(varFor("m")), NilPathStep()(pos))(pos))(pos))(pos)))(pos)

    val (nodePredicates: Seq[Expression], relationshipPredicates: Seq[Expression], solvedPredicates: Seq[Expression]) =
      extractPredicates(Seq(rewrittenPredicate), "x", "x-relationship", "x-node", "n", "m")

    nodePredicates shouldBe empty
    relationshipPredicates shouldBe List(propLessThan("x-relationship", "prop", 4))
    solvedPredicates shouldBe List(rewrittenPredicate)
  }

  test("p = (n)-[x*]->(o) WHERE NONE(r in relationships(p) WHERE r.prop < 4) AND ALL(m in nodes(p) WHERE m.prop IS NOT NULL)") {

    val rewrittenRelPredicate = NoneIterablePredicate(
      FilterScope(varFor("r"), Some(propLessThan("r", "prop", 4)))(pos),
      function(
        "relationships",
        PathExpression(
          NodePathStep(
            varFor("n"),
            MultiRelationshipPathStep(varFor("x"), SemanticDirection.OUTGOING, Some(varFor("0")), NilPathStep()(pos))(pos))(pos))(pos)))(pos)

    val rewrittenNodePredicate = AllIterablePredicate(
      FilterScope(varFor("m"), Some(isNotNull(prop("m", "prop"))))(pos),
      function(
        "nodes",
        PathExpression(
          NodePathStep(
            varFor("n"),
            MultiRelationshipPathStep(varFor("x"), SemanticDirection.OUTGOING, Some(varFor("o")), NilPathStep()(pos))(pos))(pos))(pos)))(pos)

    val (nodePredicates: Seq[Expression], relationshipPredicates: Seq[Expression], solvedPredicates: Seq[Expression]) =
      extractPredicates(Seq(rewrittenRelPredicate, rewrittenNodePredicate), "x", "x-relationship", "x-node", "n", "o")

    nodePredicates shouldBe List(isNotNull(prop("x-node", "prop")))
    relationshipPredicates shouldBe List(not(propLessThan("x-relationship", "prop", 4)))
    solvedPredicates shouldBe List(rewrittenRelPredicate, rewrittenNodePredicate)
  }

  test("p = (n)-[r*1]->(m) WHERE ALL (x IN nodes(p) WHERE x.prop = n.prop") {
    val pathExpression = PathExpression(
      NodePathStep(
        varFor("n"),
        MultiRelationshipPathStep(varFor("r"),SemanticDirection.OUTGOING, Some(varFor("m")), NilPathStep()(pos))(pos))(pos))(pos)

    val nodePredicate = equals(prop("x", "prop"), prop("n", "prop"))
    val rewrittenPredicate = AllIterablePredicate(
      FilterScope(varFor("x"), Some(nodePredicate))(pos),
      function("nodes", pathExpression))(pos)

    val (nodePredicates: Seq[Expression], relationshipPredicates: Seq[Expression], solvedPredicates: Seq[Expression]) =
      extractPredicates(Seq(rewrittenPredicate), "r", "r-relationship", "r-node", "n", "m")

    nodePredicates shouldBe List(equals(prop("r-node", "prop"), prop("n", "prop")))
    relationshipPredicates shouldBe empty
    solvedPredicates shouldBe List(rewrittenPredicate)
  }

  test("p = (n)-[r*1]->(m) WHERE ALL (x IN nodes(p) WHERE length(p) = 1)") {
    val pathExpression = PathExpression(
      NodePathStep(
        varFor("n"),
        MultiRelationshipPathStep(varFor("r"),SemanticDirection.OUTGOING, Some(varFor("m")), NilPathStep()(pos))(pos))(pos))(pos)

    val rewrittenPredicate = AllIterablePredicate(
      FilterScope(varFor("x"), Some(equals(function("length", pathExpression), literalInt(1))))(pos),
      function("nodes", pathExpression))(pos)

    val (nodePredicates: Seq[Expression], relationshipPredicates: Seq[Expression], solvedPredicates: Seq[Expression]) =
      extractPredicates(Seq(rewrittenPredicate), "r", "r-relationship", "r-node", "n", "m")

    nodePredicates shouldBe empty
    relationshipPredicates shouldBe empty
    solvedPredicates shouldBe empty
  }

  test("p=(n)-[rel:*1..3]->(m) WHERE ALL (x in nodes(p) WHERE x = n or x = m)") {

    val pathExpression = PathExpression(
      NodePathStep(
        varFor("n"),
        MultiRelationshipPathStep(varFor("rel"), SemanticDirection.OUTGOING, Some(varFor("m")), NilPathStep()(pos))(pos))(pos))(pos)

    val rewrittenPredicate = AllIterablePredicate(
      FilterScope(varFor("x"), Some(ors(equals(varFor("x"), varFor("n")), equals(varFor("x"), varFor("m")))))(pos),
      function("nodes", pathExpression))(pos)

    val (nodePredicates, relationshipPredicates, solvedPredicates) =
      extractPredicates(Seq(rewrittenPredicate), "rel", "rel_RELS", "rel_NODES", "n", "m")

    nodePredicates shouldBe empty
    relationshipPredicates shouldBe empty
    solvedPredicates shouldBe empty
  }

  test("p = (n)-[r*1]->() WHERE ALL (x IN relationships(p) WHERE length(p) = 1)") {
    val pathExpression = PathExpression(
      NodePathStep(
        varFor("n"),
        MultiRelationshipPathStep(varFor("r"),SemanticDirection.OUTGOING, Some(varFor("m")), NilPathStep()(pos))(pos))(pos))(pos)

    val rewrittenPredicate = AllIterablePredicate(
      FilterScope(varFor("x"), Some(equals(function("length", pathExpression), literalInt(1))))(pos),
      function("relationships", pathExpression))(pos)

    val (nodePredicates: Seq[Expression], relationshipPredicates: Seq[Expression], solvedPredicates: Seq[Expression]) =
      extractPredicates(Seq(rewrittenPredicate), "r", "r-relationship", "r-node", "n", "  UNNAMED0")

    nodePredicates shouldBe empty
    relationshipPredicates shouldBe empty
    solvedPredicates shouldBe empty
  }

  test("p = (n)-[r*1]->() WHERE NONE (x IN nodes(p) WHERE length(p) = 1)") {
    val pathExpression = PathExpression(
      NodePathStep(
        varFor("n"),
        MultiRelationshipPathStep(varFor("r"), SemanticDirection.OUTGOING, Some(varFor("m")), NilPathStep()(pos))(pos))(pos))(pos)

    val rewrittenPredicate = NoneIterablePredicate(
      FilterScope(varFor("x"), Some(equals(function("length", pathExpression), literalInt(1))))(pos),
      function("nodes", pathExpression))(pos)

    val (nodePredicates: Seq[Expression], relationshipPredicates: Seq[Expression], solvedPredicates: Seq[Expression]) =
      extractPredicates(Seq(rewrittenPredicate), "r", "r-relationship", "r-node", "n", "  UNNAMED0")

    nodePredicates shouldBe empty
    relationshipPredicates shouldBe empty
    solvedPredicates shouldBe empty
  }

  test("p = (n)-[r*1]->(m) WHERE NONE (x IN relationships(p) WHERE length(p) = 1)") {
    val pathExpression = PathExpression(
      NodePathStep(
        varFor("n"),
        MultiRelationshipPathStep(varFor("r"),SemanticDirection.OUTGOING, Some(varFor("m")), NilPathStep()(pos))(pos))(pos))(pos)

    val rewrittenPredicate = NoneIterablePredicate(
      FilterScope(varFor("x"), Some(equals(function("length", pathExpression), literalInt(1))))(pos),
      function("relationships", pathExpression))(pos)

    val (nodePredicates: Seq[Expression], relationshipPredicates: Seq[Expression], solvedPredicates: Seq[Expression]) =
      extractPredicates(Seq(rewrittenPredicate), "r", "r-relationship", "r-node", "n", "m")

    nodePredicates shouldBe empty
    relationshipPredicates shouldBe empty
    solvedPredicates shouldBe empty
  }

  // "MATCH (a:A)-[r* {aProp: a.prop, bProp: b.prop}]->(b)"
  test("p = (a)-[r*]->(b) WHERE ALL (x IN relationships(p) WHERE x.aProp = a.prop AND x.bProp = b.prop)") {
    PathExpression(
      NodePathStep(
        varFor("n"),
        MultiRelationshipPathStep(varFor("r"),SemanticDirection.OUTGOING, Some(varFor("m")), NilPathStep()(pos))(pos))(pos))(pos)

    val rewrittenPredicate = AllIterablePredicate(
      FilterScope(varFor("x"), Some(
        ands(
          equals(prop("x", "aProp"), prop("a", "prop")),
          equals(prop("x", "bProp"), prop("b", "prop")),
        )))(pos),
      varFor("r")
    )(pos)

    val (nodePredicates: Seq[Expression], relationshipPredicates: Seq[Expression], solvedPredicates: Seq[Expression]) =
      extractPredicates(Seq(rewrittenPredicate), "r", "r-relationship", "r-node", "a", "b")

    nodePredicates shouldBe empty
    relationshipPredicates shouldBe empty
    solvedPredicates shouldBe empty
  }

  // "MATCH (a:A)-[r* {aProp: a.prop, bProp: b.prop}]->(b)"
  test("p = (a)-[r*]->(b) WHERE ALL (x IN relationships(p) WHERE x.aProp = a.prop) AND ALL (x IN relationships(p) WHERE x.bProp = b.prop)") {
    PathExpression(
      NodePathStep(
        varFor("n"),
        MultiRelationshipPathStep(varFor("r"),SemanticDirection.OUTGOING, Some(varFor("m")), NilPathStep()(pos))(pos))(pos))(pos)

    val solvableAllPredicate = AllIterablePredicate(
      FilterScope(varFor("x"), Some(
        equals(prop("x", "aProp"), prop("a", "prop")),
      ))(pos),
      varFor("r")
    )(pos)
    val dependingAllPredicate = AllIterablePredicate(
      FilterScope(varFor("x"), Some(
        equals(prop("x", "bProp"), prop("b", "prop")),
      ))(pos),
      varFor("r")
    )(pos)
    val rewrittenPredicates = Seq(
      solvableAllPredicate,
      dependingAllPredicate
    )

    val (nodePredicates: Seq[Expression], relationshipPredicates: Seq[Expression], solvedPredicates: Seq[Expression]) =
      extractPredicates(rewrittenPredicates, "r", "r-relationship", "r-node", "a", "b")

    nodePredicates shouldBe empty
    relationshipPredicates shouldBe Seq(equals(prop("r-relationship", "aProp"), prop("a", "prop")))
    solvedPredicates shouldBe Seq(solvableAllPredicate)
  }

  // Extracting VarLengthBound predicates
  test("should not extract VarLengthBound predicates if there is no var-length relationship solved") {
    val (nodePredicates, relationshipPredicates, solvedPredicates) =
      extractPredicates(
        Seq(varLengthLowerLimitPredicate("rel", 42), varLengthUpperLimitPredicate("rel", 42)),
        "rel",
        "n",
        "m",
        "", // <- we do not care about these.
        "", // <- we do not care about these.
        maybeVarLength = None
      )

    nodePredicates shouldBe empty
    relationshipPredicates shouldBe empty
    solvedPredicates shouldBe empty
  }

  test("should extract VarLengthBound predicates on exact match") {
    val varLengthPredicates = Seq(varLengthLowerLimitPredicate("rel", 42), varLengthUpperLimitPredicate("rel", 42))
    val (nodePredicates, relationshipPredicates, solvedPredicates) =
      extractPredicates(
        varLengthPredicates,
        "rel",
        "n",
        "m",
        "", // <- we do not care about these.
        "", // <- we do not care about these.
        maybeVarLength = Some(VarPatternLength(42, Some(42)))
      )

    nodePredicates shouldBe empty
    relationshipPredicates shouldBe empty
    solvedPredicates shouldBe varLengthPredicates
  }

  test("should not extract VarLengthBound predicates if on other relationship") {
    val (nodePredicates, relationshipPredicates, solvedPredicates) =
      extractPredicates(
        Seq(varLengthLowerLimitPredicate("rel", 42), varLengthUpperLimitPredicate("rel", 42)),
        "rel2",
        "n",
        "m",
        "", // <- we do not care about these.
        "", // <- we do not care about these.
        maybeVarLength = Some(VarPatternLength(42, Some(42)))
      )

    nodePredicates shouldBe empty
    relationshipPredicates shouldBe empty
    solvedPredicates shouldBe empty
  }

  test("should not extract more specific VarLengthBound predicates") {
    val (nodePredicates, relationshipPredicates, solvedPredicates) =
      extractPredicates(
        Seq(varLengthLowerLimitPredicate("rel", 42), varLengthUpperLimitPredicate("rel", 42)),
        "rel",
        "n",
        "m",
        "", // <- we do not care about these.
        "", // <- we do not care about these.
        maybeVarLength = Some(VarPatternLength(40, Some(45)))
      )

    nodePredicates shouldBe empty
    relationshipPredicates shouldBe empty
    solvedPredicates shouldBe empty
  }

  test("should extract only VarLengthBound predicates which would be satisfied by the var-length relationship given") {
    val lowerBoundPredicate = varLengthLowerLimitPredicate("rel", 40)
    val (nodePredicates, relationshipPredicates, solvedPredicates) =
      extractPredicates(
        Seq(lowerBoundPredicate, varLengthUpperLimitPredicate("rel", 42)),
        "rel",
        "n",
        "m",
        "", // <- we do not care about these.
        "", // <- we do not care about these.
        maybeVarLength = Some(VarPatternLength(42, Some(45)))
      )

    nodePredicates shouldBe empty
    relationshipPredicates shouldBe empty
    solvedPredicates shouldBe List(lowerBoundPredicate)
  }

  test("should extract predicates regardless of function name spelling") {

    def makePredicate(funcName: String, negatedPredicate: Boolean) = {
      val pred = lessThan(prop("m", "prop"), literalInt(123))

      // p = (a)-[r*]->(b)
      val pathExpr = PathExpression(
        NodePathStep(
          varFor("a"),
          MultiRelationshipPathStep(varFor("r"), SemanticDirection.OUTGOING, Some(varFor("b")), NilPathStep()(pos))(
            pos
          )
        )(pos)
      )(pos)

      val iterablePredicate = if (negatedPredicate)
        NoneIterablePredicate(varFor("m"), function(funcName, pathExpr), Some(pred))(pos)
      else
        AllIterablePredicate(varFor("m"), function(funcName, pathExpr), Some(pred))(pos)

      val solvedPredicate = if (negatedPredicate) not(pred) else pred
      (iterablePredicate, solvedPredicate)
    }

    val functionNames = Seq(("nodes", "relationships"), ("NODES", "RELATIONSHIPS"))
    for ((nodesF, relationshipsF) <- functionNames) withClue((nodesF, relationshipsF)) {
      val (allNode, allSolvedNode) = makePredicate(nodesF, negatedPredicate = false)
      val (allRel, allSolvedRel) = makePredicate(relationshipsF, negatedPredicate = false)
      val (noneNode, noneSolvedNode) = makePredicate(nodesF, negatedPredicate = true)
      val (noneRel, noneSolvedRel) = makePredicate(relationshipsF, negatedPredicate = true)

      val (nodePredicates, relationshipPredicates, solvedPredicates) =
        extractPredicates(
          Seq(allNode, allRel, noneNode, noneRel),
          "r",
          "m",
          "m",
          "a",
          "b",
          Some(VarPatternLength.unlimited)
        )

      nodePredicates shouldBe List(allSolvedNode, noneSolvedNode)
      relationshipPredicates shouldBe List(allSolvedRel, noneSolvedRel)
      solvedPredicates shouldBe List(allNode, allRel, noneNode, noneRel)
    }
  }
}
