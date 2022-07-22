package impl

import java.nio.file.{Files, Path, Paths, StandardOpenOption}

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import impl.TwitterApi.{GetFollowerResponse, UserInfo}
import impl.TwitterScanner._
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

class TwitterScanner(twitterApi: TwitterApi, apiKeys: Seq[String], capperNames: Seq[String], outputPath: String) {
  val log = LoggerFactory.getLogger(getClass)

  private val interval: FiniteDuration = {
    val seconds: Int = Math.floor(60.0 / apiKeys.size).toInt + 1
    seconds.seconds
  }

  private var capperCounter: Int = 0
  private var getUserInfoCounter: Int = 0
  private var getFollowersCounter: Int = 0

  def behavior: Behavior[TwitterScannerCommand] = {
    Behaviors.withTimers { timers =>
      log.info(s"Starting Twitter Scanner...")

      Behaviors.receive { (context: ActorContext[TwitterScannerCommand], command: TwitterScannerCommand) => command match {
          case Start =>
            log.info(s"Received Start command...")
            val command = CreateNewCapperSetup(capperNames(capperCounter), apiKeys(getUserInfoCounter))

            context.self ! command
            Behaviors.same

          case CreateNewCapperSetup(username, apiKey) =>
            log.info(s"Received CreateNewCapperSetup command for username=$username...")

            val command = handleCreateNewCapperSetup(username, apiKey)
            context.self ! command
            Behaviors.same

          case GetFollowers(userId, username, paginationToken, apiKey) =>
            log.info(s"Received GetFollowers command for userId=$userId at offset=$paginationToken...")

            val command = handleGetFollowers(userId, username, paginationToken, apiKey)
            timers.startSingleTimer(command, interval)
            Behaviors.same

          case Shutdown =>
            log.info(s"Received Shutdown...")

            context.system.terminate()
            Behaviors.stopped
        }
      }
    }
  }

  def handleCreateNewCapperSetup(username: String, apiKey: String): TwitterScannerCommand = {
    val response: UserInfo = twitterApi.getUserInfo(apiKey, username)

    val apiKeyIndex = getFollowersCounter % apiKeys.size
    getFollowersCounter += 1

    GetFollowers(response.id, username, None, apiKeys(apiKeyIndex))
  }

  def handleGetFollowers(userId: String, username: String, paginationToken: Option[String], apiKey: String): TwitterScannerCommand = {
    val response: GetFollowerResponse = twitterApi.getFollowers(apiKey, userId, paginationToken)

    log.info(s"Received GetFollowersResponse with ${response.meta.resultCount} results...")

    // Write to file
    writeToFile(username, response.data.map(_.map(_.id)))

    response.meta.nextToken match {
      case Some(nt) =>
        val apiKeyIndex = getFollowersCounter % apiKeys.size
        getFollowersCounter += 1

        GetFollowers(userId, username, Some(nt), apiKeys(apiKeyIndex))

      case None =>
        if (capperNames.size <= capperCounter + 1) {
          Shutdown
        } else {
          val apiKeyIndex = getUserInfoCounter % apiKeys.size
          getUserInfoCounter += 1
          capperCounter += 1

          CreateNewCapperSetup(capperNames(capperCounter), apiKeys(apiKeyIndex))
        }

    }
  }

  private def writeToFile(username: String, idsOpt: Option[Seq[CharSequence]]) = {
    idsOpt match {
      case Some(ids) =>
        if (ids.nonEmpty) {
          log.info(s"Writing ${ids.size} ids to file for user $username...")

          val path: Path = Paths.get(s"$outputPath/$username.csv")
          Files.write(path, ids.mkString("\n").getBytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        } else {
          writeUserToEmptyFile(username)
        }
      case None => writeUserToEmptyFile(username)
    }
  }

  private def writeUserToEmptyFile(username: String) = {
    log.info(s"Adding $username to empty file...")

    val path: Path = Paths.get(s"$outputPath/_empty_capper_file.csv")
    Files.write(path, s"$username\n".getBytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
  }
}



object TwitterScanner {
  sealed trait TwitterScannerCommand

  case object Start extends TwitterScannerCommand
  case object Shutdown extends TwitterScannerCommand
  case class CreateNewCapperSetup(username: String, apiKey: String) extends TwitterScannerCommand
  case class GetFollowers(userId: String, username: String, paginationToken: Option[String], apiKey: String) extends TwitterScannerCommand
}
