/*
 Copyright 2016 Jo Pol
 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see http://www.gnu.org/licenses/gpl.html dibl
*/
package dibl.fte

import dibl.fte.data.{ Edge, Graph, Vertex }
import dibl.fte.layout.{ LocationsDFS, OneFormTorus }
import dibl.proto.TilesConfig
import dibl.{ Diagram, LinkProps, NewPairDiagram, PairDiagram, ThreadDiagram }

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.util.Try

object GraphCreator {

  /**
    * @param urlQuery parameters for: https://d-bl.github.io/GroundForge/tiles?
    *                 For now the tile must be a checker tile and
    *                 the patch size must span 3 columns and 2 rows of checker tiles.
    *                 A simplified ascii-art view of a pattern definition:
    *                 +----+----+----+
    *                 |....|....|....|
    *                 |....|....|....|
    *                 +----+----+----+
    *                 |....|XXXX|....|
    *                 |....|XXXX|....|
    *                 +----+----+----+
    * @return The X's in the pattern definition are added to the returned graph.
    */
  def fromThreadDiagram(urlQuery: String): Try[Graph] = {
    val patternConfig = TilesConfig(urlQuery)
    val stitchConfigs = urlQuery.split("&")
      .filter(_.startsWith("droste"))
      .sortBy(identity)
      .map(_.replace("=.*", ""))
    val initialThreadDiagram = ThreadDiagram(NewPairDiagram.create(patternConfig))
    val diagram = stitchConfigs.foldLeft(initialThreadDiagram)(droste)
    val scale = Math.pow(2, stitchConfigs.length + 1).toInt
    graphFrom(getTopoLinks(diagram, scale, patternConfig))
  }

  /**
    * fold transformation
    *
    * @param threadDiagram accumulator
    * @param stitchConfig  listItem
    * @return
    */
  private def droste(threadDiagram: Diagram, stitchConfig: String): Diagram = {
    ThreadDiagram(PairDiagram(stitchConfig, threadDiagram))
  }

  def fromPairDiagram(urlQuery: String): Try[Graph] = {
    val config = TilesConfig(urlQuery)
    val diagram = NewPairDiagram.create(config)
    graphFrom(getTopoLinks(diagram, scale = 1, config))
  }

  def graphFrom(topoLinks: Seq[TopoLink]): Try[Graph] = {
    val clockWise: Map[String, Seq[TopoLink]] = {
      val linksBySource = topoLinks.groupBy(_.sourceId)
      topoLinks.groupBy(_.targetId)
        .map { case (id, linksIn) =>
          val linksOut = linksBySource(id)
          (id, linksOut.filter(_.isRightOfSource)
            ++ linksOut.filter(_.isLeftOfSource)
            ++ linksIn.filter(_.isLeftOfTarget)
            ++ linksIn.filter(_.isRightOfTarget)
          )
        }
    }
    val data = Data(Face(topoLinks), clockWise, topoLinks)
    for {
      deltas <- Delta(data, topoLinks)
      nodes = geoNodes(Map(topoLinks.head.sourceId -> (0,0)), deltas)
      graph = initGraph(deltas, clockWise)
      //_ = println(s" ===== ${graph.getVertices.size()} =?= ${nodes.size}")
      vectors = new LocationsDFS(graph.getVertices, graph.getEdges).getVectors
      //_ = println(graph.getVertices.asScala.map(v => v.getX -> v.getY ))
      //_ = println(nodes.values.toList)
      _ <- Try(Option(new OneFormTorus(graph).layout(vectors))
        .getOrElse(throw new Exception("no translation vectors found")))
      _ = println(s"${distinct(deltas)} === ${vectors.asScala.mkString("; ")}")
    } yield graph
  }

  private type GeoNodes = Map[String, (Double, Double)]
  private type Deltas = Map[TopoLink, Delta]

  private def distinct(deltas: Deltas) = deltas.values.toSeq
    .filter(_.nonZero)
    .groupBy(_.rounded) // TODO ignore direction
    .map(_._2.head) // takes the first element of each group

  @tailrec
  private def geoNodes(nodes: GeoNodes, deltas: Deltas): GeoNodes = {
    // 45 calls for 663 nodes of whiting=F14_P193&tileStitch=crcrctclclcr
    // print(s" ${nodes.size}-${deltas.size} ")

    val nodeIds = nodes.keys.toSeq
    val candidates = deltas.filter {
      case (TopoLink(source, target, _, _), _) =>
        nodeIds.contains(source) && !nodeIds.contains(target)
    }
    if (candidates.isEmpty) nodes
    else {
      val newNodes = candidates.map {
        case (TopoLink(source, target, isLeftOfTarget, isLeftOfSource), Delta(dx, dy)) =>
          val (x, y) = nodes(source)
          target -> (x + dx, y + dy)
      } ++ nodes
      val newNodeIds = newNodes.keys.toSeq
      geoNodes(
        newNodes,
        deltas.filterNot(d => newNodeIds.contains(d._1.sourceId) && newNodeIds.contains(d._1.targetId))
        // less complex to calculate but depleting deltas less
        // (doesn't influence number of calls but reduces the filter to find candidates):
        // deltas.filterNot(delta => candidates.contains(delta._1))
      )
    }
  }

  private def initGraph(deltas: Deltas, clockWise: Map[String, Seq[TopoLink]]) = {
    val topoLinks: Seq[TopoLink] = deltas.keys.toSeq
    val graph = new Graph()

    // create vertices
    implicit val vertexMap: Map[String, Vertex] = topoLinks
      .map(_.targetId).distinct.zipWithIndex
      .map { case (nodeId, i) =>
        // The initial coordinates don't matter as long as they are unique.
        nodeId -> graph.createVertex(i, 0)
      }.toMap

    // create edges
    implicit val edges: Array[Edge] = deltas.map { case (link, Delta(dx, dy)) =>
      graph.addNewEdge(new Edge(
        vertexMap(link.sourceId),
        vertexMap(link.targetId)
      )).setDeltaX(dx).setDeltaY(dy)
    }.toArray

    // add edges to vertices in clockwise order
    clockWise.foreach { case (id, topoLinks) =>
      topoLinks.foreach(findEdge(_).foreach(vertexMap(id).addEdge))
    }
    graph
  }

  private def findEdge(link: TopoLink)
                      (implicit edges: Array[Edge],
                       vertexMap: Map[String, Vertex]
                      ): Option[Edge] = {
    edges.find(edge =>
      edge.getStart == vertexMap(link.sourceId) &&
        edge.getEnd == vertexMap(link.targetId)
    )
  }

  private def getTopoLinks(implicit diagram: Diagram, scale: Int, config: TilesConfig) = {
    TopoLink
      .simplify(diagram.links.filter(inCenterBottomTile))
      .sortBy(l => l.targetId -> l.sourceId)
  }

  private def inCenterBottomTile(link: LinkProps)
                                (implicit diagram: Diagram, scale: Int, config: TilesConfig): Boolean = {
    // The top and side tiles of a diagram may have irregularities along the outer edges.
    // So select links arriving in the center bottom checker tile.
    val cols = config.patchWidth / 3
    val rows = config.patchHeight / 2

    val target = diagram.node(link.target)
    val source = diagram.node(link.source)
    val y = unScale(target.y)
    val x = unScale(target.x)
    y >= rows && x >= cols && x < cols * 2 && !link.withPin && target.id != "" && source.id != ""
  }

  /** Revert [[NewPairDiagram]].toPoint
    *
    * @param i     value for x or y
    * @param scale value 1 or factor to also undo [[ThreadDiagram]].scale
    * @return x/y on canvas reduced to row/col number in the input
    */
  private def unScale(i: Double)(implicit scale: Int): Int = (i / scale / 15 - 2).toInt
}
