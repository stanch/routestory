package net.routestory.data

import com.javadocmd.simplelatlng.{LatLngTool, LatLng}

import scala.annotation.tailrec

object Clustering {
  import LatLngTool.{ distanceInRadians ⇒ distance }

  sealed trait Tree {
    def timestamp: Double
    def location: LatLng
  }
  case class Leaf(element: Story.Element)(implicit chapter: Story.Chapter) extends Tree {
    lazy val timestamp = element.timestamp.toDouble
    lazy val location = element.location
  }
  case class Node(children: Vector[Tree], closest: Double)(implicit chapter: Story.Chapter) extends Tree {
    // average
    lazy val timestamp = children.foldLeft((0.0, 0)) {
      case ((s, n), c) ⇒ (s + c.timestamp, n + 1)
    } match {
      case (s, n) ⇒ s / n
    }

    lazy val location = chapter.locationAt(timestamp)
  }
  object Node {
    def merge(tree1: Tree, tree2: Tree)(implicit chapter: Story.Chapter) = (tree1, tree2) match {
      case (node: Node, leaf: Leaf) if node.children.forall(item ⇒ distance(item.location, leaf.location) < node.closest) ⇒ Node(
        node.children :+ leaf,
        node.closest
      )
      case (leaf: Leaf, node: Node) if node.children.forall(item ⇒ distance(item.location, leaf.location) < node.closest) ⇒ Node(
        node.children :+ leaf,
        node.closest
      )
      case (_, _) ⇒ Node(
        Vector(tree1, tree2),
        distance(tree1.location, tree2.location)
      )
    }
  }

  type DistanceTable = Map[(Tree, Tree), Double]

  @tailrec
  def fillDistanceTable(nodes: Vector[Tree], radius: Double, table: DistanceTable): (DistanceTable, Double) = if (table.isEmpty) {
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

  def patchDistanceTable(left: Vector[Tree], right: Vector[Tree], insert: Tree, radius: Double, table: DistanceTable) = {
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
  def clusterRound(nodes: Vector[Tree], clusterRadius: Double, distanceTable: DistanceTable = Map.empty)(implicit chapter: Story.Chapter): Tree = {
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
        val group = Node.merge(closest._1, closest._2)
        val index = nodesPopped lastIndexWhere { _.timestamp <= group.timestamp }
        val (left, right) = nodesPopped.splitAt(index + 1)
        val nodesPushed = left ++ Vector(group) ++ right

        // update distance table
        val tablePushed = patchDistanceTable(left, right, group, radius, tablePopped)

        // recurse
        clusterRound(nodesPushed, radius, tablePushed)
    }
  }

  def cluster(implicit chapter: Story.Chapter) = chapter.elements match {
    case Nil ⇒ None
    case e :: Nil ⇒ Some(Leaf(e))
    case _ ⇒ Some(clusterRound(chapter.elements.toVector.map(Leaf.apply), chapter.duration.toDouble / 4))
  }
}
