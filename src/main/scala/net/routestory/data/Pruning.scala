package net.routestory.data

import com.javadocmd.simplelatlng.{LatLngTool, LatLng}

object Pruning {
  def distanceToSegment(start: LatLng, end: LatLng)(point: LatLng) = {
    // TODO: this is actually the distance to great-circle path, not segment
    val δ13 = LatLngTool.distanceInRadians(start, point)
    val θ13 = LatLngTool.initialBearingInRadians(start, point)
    val θ12 = LatLngTool.initialBearingInRadians(start, end)
    Math.asin(Math.sin(δ13) * Math.sin(θ13 - θ12))
  }

  def pruneLocations(locations: Vector[Timed[LatLng]], tolerance: Double): Vector[Timed[LatLng]] = locations match {
    case Vector() ⇒ Vector()
    case Vector(l) ⇒ Vector(l)
    case first +: tail ⇒
      val last = tail.last
      val middle = tail.dropRight(1)
      val distances = middle.map(l ⇒ distanceToSegment(first.data, last.data)(l.data))
      val (m, i) = distances.zipWithIndex.maxBy(_._1)
      if (m > tolerance) {
        val left = pruneLocations(first +: middle.take(i + 1), tolerance)
        val right = pruneLocations(middle.drop(i) :+ last, tolerance)
        left.dropRight(1) ++ right
      } else {
        Vector(first, last)
      }
  }
}
