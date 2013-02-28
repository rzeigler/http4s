/*
 * Derived from Twitter Finagle.
 *
 * Original source:
 * https://github.com/twitter/finagle/blob/6e2462acc32ac753bf4e9d8e672f9f361be6b2da/finagle-http/src/main/scala/com/twitter/finagle/http/path/Path.scala
 */
package org.http4s

import scala.util.control.Exception.catching

/** Base class for path extractors. */
abstract class Path {
  def /(child: String) = new /(this, child)
  def :?(params: Map[String, Seq[String]]) = new :?(this, params)
  def toList: List[String]
  def parent: Path
  def lastOption: Option[String]
  def startsWith(other: Path): Boolean
}


object Path {
  def apply(str: String): Path =
    if (str == "" || str == "/")
      Root
    else if (!str.startsWith("/"))
      Path("/" + str)
    else {
      val slash = str.lastIndexOf('/')
      val prefix = Path(str.substring(0, slash))
      if (slash == str.length - 1)
        prefix
      else
        prefix / str.substring(slash + 1)
    }

  def apply(first: String, rest: String*): Path =
    rest.foldLeft(Root / first)( _ / _)

  def apply(list: List[String]): Path = list.foldLeft(Root : Path)(_ / _)

  def unapplySeq(path: Path): Option[List[String]] = Some(path.toList)

}


case class :?(path: Path, params: Map[String, Seq[String]]) {
  override def toString = params.toString
}


/** File extension extractor */
object ~ {
  /**
   * File extension extractor for Path:
   *   Path("example.json") match {
   *     case Root / "example" ~ "json" => ...
   */
  def unapply(path: Path): Option[(Path, String)] = {
    path match {
      case Root => None
      case parent / last =>
        unapply(last) map {
          case (base, ext) => (parent / base, ext)
        }
    }
  }

  /**
   * File extension matcher for String:
   *   "example.json" match {
   *      case => "example" ~ "json" => ...
   */
  def unapply(fileName: String): Option[(String, String)] = {
    fileName.lastIndexOf('.') match {
      case -1 => Some((fileName, ""))
      case index => Some((fileName.substring(0, index), fileName.substring(index + 1)))
    }
  }
}
//
///** HttpMethod extractor */
//object -> {
//  /**
//   * HttpMethod extractor:
//   *   (request.method, Path(request.path)) match {
//   *     case Methd.Get -> Root / "test.json" => ...
//   */
//  def unapply(x: (Method, Path)) = Some(x)
//}


case class /(parent: Path, child: String) extends Path {
  lazy val toList: List[String] = parent.toList ++ List(child)
  def lastOption: Option[String] = Some(child)
  lazy val asString = parent.toString + "/" + child
  override def toString = asString
  def startsWith(other: Path) = {
    val components = other.toList
    (toList take components.length) == components
  }
}


/**
 * Root extractor:
 *   Path("/") match {
 *     case Root => ...
 *   }
 */
case object Root extends Path {
  def toList: List[String] = Nil
  def parent = this
  def lastOption: Option[String] = None
  override def toString = ""
  def startsWith(other: Path) = other == Root
  def unapply(request: RequestPrelude): Option[Path] = Path(request.scriptName+request.pathInfo).toList match {
    case Nil => Some(Path(Nil))
    case head :: tail => Some(Path(tail))
  }
}


/**
 * Path separator extractor:
 *   Path("/1/2/3/test.json") match {
 *     case Root / "1" / "2" / "3" / "test.json" => ...
 */
object /: {
  def unapply(path: Path): Option[(String, Path)] = {
    path.toList match {
      case Nil => None
      case head :: tail => Some((head, Path(tail)))
    }
  }
}


// Base class for Integer and Long extractors.
protected class Numeric[A <: AnyVal](cast: String => A) {
  def unapply(str: String): Option[A] = {
    if (!str.isEmpty && str.forall(Character.isDigit _))
      try {
        Some(cast(str))
      } catch {
        case _: NumberFormatException =>
          None
      }
    else
      None
  }
}

/**
 * Integer extractor:
 *   Path("/user/123") match {
 *      case Root / "user" / Int(userId) => ...
 */
object Int extends Numeric(_.toInt)

/**
 * Long extractor:
 *   Path("/user/123") match {
 *      case Root / "user" / Long(userId) => ...
 */
object Long extends Numeric(_.toLong)



/**
 * Multiple param extractor:
 *   object A extends ParamMatcher("a")
 *   object B extends ParamMatcher("b")
 *   (Path(request.path) :? request.params) match {
 *     case Root / "user" :? A(a) :& B(b) => ...
 */
object :& {
  def unapply(params: Map[String, Seq[String]]) = Some((params, params))
}


/**
 * Param extractor:
 *   object ScreenName extends ParamMatcher("screen_name")
 *   (Path(request.path) :? request.params) match {
 *     case Root / "user" :? ScreenName(screenName) => ...
 */
abstract class ParamMatcher(name: String) {
  def unapply(params: Map[String, Seq[String]]) = unapplySeq(params).flatMap(_.headOption)
  def unapplySeq(params: Map[String, Seq[String]]) = params.get(name)
}


/**
 * Int param extractor:
 *   object Page extends IntParamMatcher("page")
 *   (Path(request.path) :? request.params) match {
 *     case Root / "blog" :? Page(page) => ...
 */
abstract class IntParamMatcher(name: String) {
  def unapplySeq(params: Map[String, Seq[String]]): Option[Seq[Int]] =
    params.get(name) map { value =>
      (value map { v =>
        catching(classOf[NumberFormatException]) opt v.toInt
      }).flatten
    }

  def unapply(params: Map[String, Seq[String]]): Option[Int] = unapplySeq(params).flatMap(_.headOption)
}


/**
 * Long param extractor:
 *   object UserId extends LongParamMatcher("user_id")
 *   (Path(request.path) :? request.params) match {
 *     case Root / "user" :? UserId(userId) => ...
 */
abstract class LongParamMatcher(name: String) {
  def unapplySeq(params: Map[String, Seq[String]]): Option[Seq[Long]] =
    params.get(name) map { value =>
      (value map { v =>
        catching(classOf[NumberFormatException]) opt v.toLong
      }).flatten
    }

  def unapply(params: Map[String, Seq[String]]): Option[Long] = unapplySeq(params).flatMap(_.headOption)
}

/**
 * Double param extractor:
 *   object Latitude extends DoubleParamMatcher("lat")
 *   (Path(request.path) :? request.params) match {
 *     case Root / "closest" :? Latitude("lat") => ...
 */
abstract class DoubleParamMatcher(name: String) {
  def unapplySeq(params: Map[String, Seq[String]]): Option[Seq[Double]] =
    params.get(name) map { value =>
      (value map { v =>
        catching(classOf[NumberFormatException]) opt v.toDouble
      }).flatten
    }

  def unapply(params: Map[String, Seq[String]]): Option[Double] = unapplySeq(params).flatMap(_.headOption)
}