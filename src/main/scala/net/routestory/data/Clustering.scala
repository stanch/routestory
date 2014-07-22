package net.routestory.data

import com.javadocmd.simplelatlng.{LatLngTool, LatLng}

import scala.annotation.tailrec

trait Trees {
  import LatLngTool.{ distanceInRadians ⇒ distance }

  sealed trait Tree[A] {
    def timestamp: Double
    def location: LatLng
    def data: A

    def children: Vector[Tree[A]]

    /** Map the nodes */
    def map[B](f: Tree[A] ⇒ B): Tree[B] = this match {
      case x @ Leaf(_, _, _, data) ⇒ x.withData(f(x))
      case x @ Node(children, _, _, data) ⇒
        val mapped = children.map(_.map(f))
        x.withChildren(mapped, f(x))
    }

    /** Iterate through all nodes */
    def foreach(f: Tree[A] ⇒ Unit): Unit = {
      f(this)
      children.foreach(_.foreach(f))
    }

    /** Leaf story elements */
    def leaves: Vector[Leaf[A]] = this match {
      case x @ Leaf(_, _, _, _) ⇒ Vector(x)
      case x @ Node(_, _, _, _) ⇒ x.children.flatMap(_.leaves)
    }

    /** Minimum distance, in radians, at which the node can be exploded */
    def minScale: Double

    /** Children with regards to specified explosion distance */
    def childrenAtScale(scale: Double): Vector[Tree[A]] = if (scale < minScale) {
      children.flatMap(_.childrenAtScale(scale))
    } else {
      Vector(this)
    }
  }

  case class Leaf[A](element: Timed[Story.KnownElement], index: Int, chapter: Story.Chapter, data: A) extends Tree[A] {
    lazy val timestamp = element.timestamp.toDouble
    lazy val location = chapter.location(element)
    val minScale = 0.0
    val children = Vector()

    def withData[B](d: B) = copy(data = d)
  }

  case class Node[A](children: Vector[Tree[A]], minScale: Double, chapter: Story.Chapter, data: A) extends Tree[A] {
    // average
    lazy val timestamp = children.foldLeft((0.0, 0)) {
      case ((s, n), c) ⇒ (s + c.timestamp, n + 1)
    } match {
      case (s, n) ⇒ s / n
    }

    lazy val location = chapter.locationAt(timestamp)

    def withData(d: A) = copy(data = d)
    def withChildren[B](ch: Vector[Tree[B]], d: B) = copy(children = ch, data = d)
  }

  object Node {
    def merge(tree1: Tree[Unit], tree2: Tree[Unit], chapter: Story.Chapter) = (tree1, tree2) match {
      case (node @ Node(children, minScale, _, data), leaf: Leaf[Unit]) if children.forall(item ⇒ distance(item.location, leaf.location) < minScale) ⇒
        Node(children :+ leaf, minScale, chapter, data)

      case (leaf: Leaf[Unit], node @ Node(children, minScale, c, data)) if children.forall(item ⇒ distance(item.location, leaf.location) < minScale) ⇒
        Node(children :+ leaf, minScale, chapter, data)

      case (_, _) ⇒
        Node(Vector(tree1, tree2), distance(tree1.location, tree2.location), chapter, tree1.data)
    }
  }

  // TODO: that’s super inefficient
  def indexLookup[A](trees: Vector[Tree[A]], index: Int) =
    Option(trees.indexWhere(_.leaves.map(_.index).contains(index))).filter(_ >= 0)
}

object Clustering extends Trees {
  import LatLngTool.{ distanceInRadians ⇒ distance }

  type DistanceTable = Map[(Tree[Unit], Tree[Unit]), Double]

  @tailrec
  def fillDistanceTable(nodes: Vector[Tree[Unit]], radius: Double, table: DistanceTable): (DistanceTable, Double) = if (table.isEmpty) {
    val add = nodes.tails.flatMap {
      case current +: next ⇒
        next.takeWhile {
          _.timestamp < current.timestamp + radius
        }.filterNot { neighbor ⇒
          table.contains((current, neighbor))
        }.map { neighbor ⇒
          (current, neighbor) → distance(current.location, neighbor.location)
        }
      case _ ⇒
        Vector.empty
    }.toMap
    fillDistanceTable(nodes, radius * 2, table ++ add)
  } else (table, radius)

  def patchDistanceTable(left: Vector[Tree[Unit]], right: Vector[Tree[Unit]], insert: Tree[Unit], radius: Double, table: DistanceTable) = {
    val fromRight = right.takeWhile {
      _.timestamp < insert.timestamp + radius
    }.map { item ⇒
      (insert, item) → distance(insert.location, item.location)
    }.toMap

    val fromLeft = left.dropWhile {
      _.timestamp < insert.timestamp - radius
    }.map { item ⇒
      (item, insert) → distance(item.location, insert.location)
    }.toMap

    table ++ fromRight ++ fromLeft
  }

  @tailrec
  def clusterRound(chapter: Story.Chapter, nodes: Vector[Tree[Unit]], clusterRadius: Double, distanceTable: DistanceTable = Map.empty): Tree[Unit] = {
    nodes match {
      case Vector(single) ⇒ single
      case _ ⇒
        // refill distance table
        val (table, radius) = fillDistanceTable(nodes, clusterRadius, distanceTable)

        // search for the closest clusters
        val closest = table.minBy(_._2)._1

        // remove them
        val nodesPopped = nodes diff List(closest._1, closest._2)
        val tablePopped = table filter {
          case ((item1, item2), _) ⇒
            (Set(item1, item2) & Set(closest._1, closest._2)).isEmpty
        }

        // merge them
        val group = Node.merge(closest._1, closest._2, chapter)
        val index = nodesPopped lastIndexWhere { _.timestamp <= group.timestamp }
        val (left, right) = nodesPopped.splitAt(index + 1)
        val nodesPushed = left ++ Vector(group) ++ right

        // update distance table
        val tablePushed = patchDistanceTable(left, right, group, radius, tablePopped)

        // recurse
        clusterRound(chapter, nodesPushed, radius, tablePushed)
    }
  }

  def cluster(chapter: Story.Chapter) = chapter.knownElements match {
    case Nil ⇒ None
    case e :: Nil ⇒ Some(Leaf(e, 0, chapter, ()))
    case es ⇒
      val leaves = es.zipWithIndex.toVector map { case (e, i) ⇒ Leaf(e, i, chapter, ()) }
      Some(clusterRound(chapter, leaves, chapter.duration.toDouble / 4))
  }
}
