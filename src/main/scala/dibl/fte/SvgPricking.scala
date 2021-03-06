/*
 Copyright 2015 Jo Pol
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

import java.lang.Math.{log, max, min}

import scala.scalajs.js.{Array, Dictionary}
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

@JSExportTopLevel("SvgPricking") object SvgPricking {

  // TODO make implicit like scale
  private val offsetX = 300
  private val offsetY = 250

  @JSExport
  def create(deltas: Map[TopoLink, Delta]): String = {
    println(s"SvgPricking.create deltas=${deltas.mkString("; ")}")
    val startId = deltas.keys.head.sourceId
    val nodes = Locations.create(Map(startId -> (0, 0)), deltas)
    val tileVectors = TileVector(startId, deltas).toSeq
    val minLength = deltas.values.map {case Delta(dx,dy) =>
      Math.sqrt(dx*dx + dy*dy)
    }.min

    implicit val scale: Double = 30 / minLength
    val tile = deltas.map { case (tl @ TopoLink(_, s, _, t, weight), Delta(dx, dy)) =>
      val (x1, y1) = nodes(s)
      val w = min(5,max(2, 2 + log(3*weight)))
      val l = line(x1, y1, x1 - dx, y1 - dy, s"""id="$s-$t" style="stroke:rgb(0,0,0);stroke-width:$w" """)
        .replace("/>", s"><title>$tl</title></line>")
      s"""<a href="#" onclick="clickedLink(this)">$l</a>"""
    }
    val dots = nodes.map{ case (id,(x,y)) =>
      val c = s"""<circle id="$id" cx="${ scale * x + offsetX}" cy="${ scale * y + offsetY}" r="8" style="fill:rgb(225,0,0);opacity:0.65"><title>$id</title></circle>"""
      s"""<a href="#" onclick="clickedDot(this)">$c</a>"""
    }
    val clones = if (tileVectors.isEmpty) Seq("")
                 else {
                   val vectorLines = tileVectors.map { case (dx, dy) =>
                     line(0, 0, dx, dy, """style="stroke:rgb(0,255,0);stroke-width:3" """)
                   }
                   val (dx1, dy1) = tileVectors.head
                   val (dx2, dy2) = tileVectors.tail.headOption.getOrElse((-dy1 * 4, dx1 * 4))
                   val clones = for {
                     i <- -3 to 6
                     j <- -3 to 6
                   } yield {
                     if (i == 0 && j == 0) ""
                     else s"""<use transform="translate(${ i * dx1 * scale + j * dx2 * scale },${ i * dy1 * scale + j * dy2 * scale })" xlink:href="#tile" style="opacity:0.65"/>"""
                   }
                   vectorLines ++ clones
                 }
    s"""<svg
       |  xmlns="http://www.w3.org/2000/svg"
       |  xmlns:xlink="http://www.w3.org/1999/xlink"
       |  id="svg2" version="1.1"
       |  width="${ 5 * scale }" height="${ 5 * scale }"
       |>
       |<g id="tile">
       |${ tile.mkString("\n") }
       |${ dots.mkString("\n") }
       |</g>
       |${ clones.mkString("\n") }
       |</svg>
       |""".stripMargin
  }

  private def line(x1: Double, y1: Double, x2: Double, y2: Double, attrs: String)(implicit scale: Double): String = {
    s"""<line x1="${ scale * x1 + offsetX }" y1="${ scale * y1 + offsetY }" x2="${ scale * x2 + offsetX }" y2="${ scale * y2 + offsetY }" $attrs/>"""
  }
}
