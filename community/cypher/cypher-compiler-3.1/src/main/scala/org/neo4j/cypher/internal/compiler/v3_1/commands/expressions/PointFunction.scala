/*
 * Copyright (c) 2002-2016 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
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
package org.neo4j.cypher.internal.compiler.v3_1.commands.expressions

import org.neo4j.cypher.internal.compiler.v3_1.helpers.IsMap
import org.neo4j.cypher.internal.compiler.v3_1.pipes.QueryState
import org.neo4j.cypher.internal.compiler.v3_1.symbols.SymbolTable
import org.neo4j.cypher.internal.compiler.v3_1.{CRS, CartesianPoint, ExecutionContext, GeographicPoint}
import org.neo4j.cypher.internal.frontend.v3_1.symbols._
import org.neo4j.cypher.internal.frontend.v3_1.{CypherTypeException, InvalidArgumentException, SyntaxException}

case class PointFunction(data: Expression) extends NullInNullOutExpression(data) {

  override def compute(value: Any, ctx: ExecutionContext)(implicit state: QueryState): Any = value match {
    case IsMap(mapCreator) =>
      val map = mapCreator(state.query)
      map.getOrElse("crs", CRS.WGS84.name) match {
        case CRS.Cartesian.name =>
          if (!map.contains("x") || !map.contains("y")) throw new InvalidArgumentException("A cartesian point must contain 'x' and 'y' coordinates")
          val x = safeToDouble(map("x"))
          val y = safeToDouble(map("y"))
          CartesianPoint(x, y)

        case CRS.WGS84.name =>
          if (!map.contains("longitude") || !map.contains("latitude")) throw new SyntaxException("A cartesian point must contain 'x' and 'y' coordinates")
          val longitude = safeToDouble(map("longitude"))
          val latitude = safeToDouble(map("latitude"))
          GeographicPoint(longitude, latitude, CRS.WGS84)

        case unknown => throw new InvalidArgumentException(s"$unknown is not a supported coordinate system, supported values " +
                                                    s"are ${CRS.Cartesian.name} and ${CRS.WGS84.name}")
      }
    case x => throw new CypherTypeException(s"Expected a map but got $x")
  }

  override def rewrite(f: (Expression) => Expression) = f(PointFunction(data.rewrite(f)))

  override def arguments = data.arguments

  override def calculateType(symbols: SymbolTable) = CTPoint

  override def symbolTableDependencies = data.symbolTableDependencies

  override def toString = "Point(" + data + ")"

  private def safeToDouble(value: Any) = value match {
    case n: Number => n.doubleValue()
    case other => throw new CypherTypeException(other.getClass.getSimpleName + " is not a valid coordinate type.")
  }
}
