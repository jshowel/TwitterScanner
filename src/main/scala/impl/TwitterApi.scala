package impl

import impl.TwitterApi.{GetFollowerResponse, UserInfo}
import org.slf4j.LoggerFactory
import play.api.libs.json.JsonNaming.SnakeCase
import play.api.libs.json.{Format, Json, JsonConfiguration}
import scalaj.http.{Http, HttpOptions}

trait TwitterApi {
  def getUserInfo(token: String, username: String): UserInfo

  def getFollowers(token: String, id: String, paginationToken: Option[String]): GetFollowerResponse
}


object TwitterApi {
  case class UserInfo(id: String, name: String, username: String)

  object UserInfo {
    implicit val format: Format[UserInfo] = Json.format
  }

  case class Follower(id: String, name: String, username: String)

  object Follower {
    implicit val format: Format[Follower] = Json.format
  }

  case class GetFollowerMetaData(resultCount: Int, nextToken: Option[String])

  object GetFollowerMetaData {
    implicit val config = JsonConfiguration(SnakeCase)
    implicit val format: Format[GetFollowerMetaData] = Json.format
  }

  case class GetFollowerResponse(data: Option[List[Follower]], meta: GetFollowerMetaData)

  object GetFollowerResponse {
    implicit val format: Format[GetFollowerResponse] = Json.format
  }
}

class TwitterApiImpl extends TwitterApi {
  private val log = LoggerFactory.getLogger(getClass)

  def getUserInfo(token: String, username: String): UserInfo = {
    log.info(s"Getting user info for username=$username...")

    val response = Http(s"https://api.twitter.com/2/users/by/username/$username")
      .header("Authorization", s"Bearer $token")
      .option(HttpOptions.connTimeout(10000))
      .asString

    (Json.parse(response.body) \ "data").as[UserInfo]
  }

  def getFollowers(token: String, id: String, paginationToken: Option[String]): GetFollowerResponse = {
    log.info(s"Getting followers for id=$id at offset=$paginationToken...")

    val request = Http(s"https://api.twitter.com/2/users/$id/followers")
      .header("Authorization", s"Bearer $token")
      .param("max_results", "1000")
      .option(HttpOptions.connTimeout(10000))

    val response = paginationToken match {
      case Some(pt) => request.param("pagination_token", pt).asString
      case None => request.asString
    }

    Json.parse(response.body).as[GetFollowerResponse]
  }
}

