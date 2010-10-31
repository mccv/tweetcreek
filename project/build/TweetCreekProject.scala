import sbt._
import com.twitter.sbt._

class TweetCreekProject(info: ProjectInfo) extends StandardProject(info) with SubversionPublisher {
  override def disableCrossPaths = false

  val httpClient = "org.apache.httpcomponents" % "httpclient" % "4.0.3"
  val configgy  = buildScalaVersion match {
    case "2.7.7" => "net.lag" % "configgy" % "1.5.3"
    case _ => "net.lag" % "configgy" % "2.0.1"
  }

  val specs     = buildScalaVersion match {
    case "2.7.7" => "org.scala-tools.testing" % "specs" % "1.6.2.1"
    case _ => "org.scala-tools.testing" %% "specs" % "1.6.5"
  }

  val jettyVersion = "6.1.25"
  val jetty = "org.mortbay.jetty" % "jetty" % jettyVersion % "test"

  override def pomExtra =
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
}
