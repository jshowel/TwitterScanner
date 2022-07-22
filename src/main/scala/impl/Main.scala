package impl

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import impl.TwitterScanner.{Start, TwitterScannerCommand}

object Main {

  /**
   * Add keys here
   * Ex:
   * Seq(
   *  "AAAAAAAAAAAAAAAAAAAAAAciHQEAAAAAlCDjw3c109jf109-HFasdsaxKlE%3Dasdfs25C02pR48v9XnHwejqhajsasdjDGXJLfB2E9MyxvpegZt",
   *  "AAAAAAAAAAAAAAAAAAAAAAciHQEAAAAAlCDjw3cCEzgB23901jf10-9jf1E%3Dasdfa25C02pR48v9XnhakljhlakjshflkjhhfBadfaksdfjZjt",
   *  "AAAAAAAAAAAAAAAAAAAAAAciHQEAAAAAlCDjwasf9a0sdfa09safja9xKlE%3asfasf25C02pR48v9XnHPumdSy1iSxQjDGXJasf892f98fn2fj0"
   *)
   */
  val apiKeys: Seq[String] = Seq(
    ""
  )

  /**
   * Can change this by hard coding, loading from file, etc.
   * Just username, NO @
   *
   */
  val userNames: Seq[String] = Seq("AdamSchefter", "SHAQ", "KDTrey5")

  /**
   * Local path you want the output files to be created at
   */
    val outputPath: String = ""

  /**
   * This will iterate through all the usernames, and output each users follower ids to a separate file.
   * This title of each output file will be the username of the capper.
   * Usernames of cappers that return 0 followers (I think this will happen depending on the user settings in twitter)
   * will be written to _empty_capper_file.csv.
   *
   * Twitter applies a rate limit of 15 requests over any 15 window for the get followers API.
   * Therefore the Twitter Scanner rotates through API keys and rate limits itself to call the
   * Twitter API as fast as it can without going over the limit.
   */
  def apply: Behavior[TwitterScannerCommand] = Behaviors.setup { context: ActorContext[TwitterScannerCommand] =>
    val twitterApi: TwitterApi = new TwitterApiImpl()
    val scanner = new TwitterScanner(twitterApi, apiKeys, userNames, outputPath)

    val scannerRef = context.spawn(scanner.behavior, "twitterScanner")
    context.watch(scannerRef)

    scannerRef ! Start

    Behaviors.empty[TwitterScannerCommand]
  }

  def main(args: Array[String]): Unit = {
    ActorSystem(guardianBehavior = Main.apply, name = "TwitterScannerSystem")
  }
}
