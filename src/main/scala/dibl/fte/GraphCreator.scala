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

import scala.util.Try

object GraphCreator {

  def graphFrom(topoLinks: Seq[TopoLink]): Try[String] = {
    for {
      data <- Data(topoLinks)
      _ = if (topoLinks.size < 19) println("DATA " + data.map(_.map(_.toInt).mkString("[", ",", "]")).mkString("[", ",", "]"))
      deltas <- Delta(data, topoLinks)
      svg = SvgPricking(topoLinks, deltas)
    } yield svg
  }
}
