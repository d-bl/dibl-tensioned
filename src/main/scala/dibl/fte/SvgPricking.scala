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

object SvgPricking {
  def apply(nodes: Locations,
            deltas: Map[TopoLink, Delta],
            tileVectors: Seq[(Double, Double)],
           ): String = {

    val tile = deltas.map { case (TopoLink(s, t, _, _), Delta(dx, dy)) =>
      val (x1, y1) = nodes(s)
      line(s"$s-$t", x1, y1, x1 + dx, y1 + dy, "rgb(0,0,0)")
    }
    val clones = if (tileVectors.isEmpty) Seq("")
                 else {
                   val vectorLines = tileVectors.map { case (dx, dy) =>
                     line(s"$dx $dy", 0, 0, dx, dy, "rgb(0,255,0)")
                   }
                   val (dx1, dy1) = tileVectors.head
                   val (dx2, dy2) = tileVectors.tail.headOption.getOrElse((-dy1 * 4, dx1 * 4))
                   val clones = for {
                     i <- -200 to 400 by 100
                     j <- -200 to 400 by 100
                   } yield {
                     s"""<use transform="translate(${ i * dx1 + j * dx2 },${ i * dy1 + j * dy2 })" xlink:href="#tile" style="opacity:0.5"/>"""
                   }
                   vectorLines ++ clones
                 }
    s"""<svg
       |  xmlns="http://www.w3.org/2000/svg"
       |  xmlns:svg="http://www.w3.org/2000/svg"
       |  xmlns:xlink="http://www.w3.org/1999/xlink"
       |  id="svg2" version="1.1"
       |  width="500" height="500"
       |>
       |<svg:g id="tile">
       |${ tile.mkString("\n") }
       |</svg:g>
       |${ clones.mkString("\n") }
       |</svg>
       |""".stripMargin
  }

  private def line(classAttr: String, x1: Double, y1: Double, x2: Double, y2: Double, color: String): String = {
    s"""  <line class="$classAttr" x1="${ 100 * x1 + 200 }" y1="${ 100 * y1 + 200 }" x2="${ 100 * x2 + 200 }" y2="${ 100 * y2 + 200 }" style="stroke: $color" />"""
  }
}
