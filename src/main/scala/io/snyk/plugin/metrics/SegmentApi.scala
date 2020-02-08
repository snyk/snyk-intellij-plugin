package io.snyk.plugin.metrics


import java.util.UUID

import com.segment.analytics.messages._
import io.snyk.plugin.client.{SnykCLIClient, SnykUserInfo}
import com.segment.analytics.Analytics

import scala.util.{Success, Try}
import scala.collection.JavaConverters._

object SegmentApi {
  object Messages {
    def identify(userId: UUID, traits: Map[String, String]): IdentifyMessage.Builder =
      IdentifyMessage.builder().userId(userId.toString).traits(traits.asJava)

    def identify(user: SnykUserInfo): IdentifyMessage.Builder =
      identify(
        userId = user.id,
        traits = Map(
          "name" -> user.name,
          "email" -> user.email,
          "username" -> user.username,
          "created_at" -> user.created.toEpochSecond.toString,
          )
      )

    def track(event: String, userId: UUID, props: Map[String, Any]): TrackMessage.Builder =
      TrackMessage.builder(event).userId(userId.toString).properties(props.asJava)

    def screen(name: String, userId: UUID, props: Map[String, Any]): ScreenMessage.Builder =
      ScreenMessage.builder(name).userId(userId.toString).properties(props.asJava)

    def page(name: String, userId: UUID, props: Map[String, Any]): PageMessage.Builder =
      PageMessage.builder(name).userId(userId.toString).properties(props.asJava)
  }

  def apply(apiClient: SnykCLIClient): SegmentApi = new LiveSegmentApi(apiClient)
}


trait SegmentApi {
  def identify(): Try[Unit]
  def track(eventName: String, props: Map[String, Any] = Map.empty): Try[Unit]
}

object MockSegmentApi extends SegmentApi {
  override def identify(): Try[Unit] = Success(())
  override def track(eventName: String, props: Map[String, Any]): Try[Unit] = Success(())
}

class LiveSegmentApi(apiClient: SnykCLIClient) extends SegmentApi {
  import SegmentApi._

  val DefaultWriteKey = "rrZlYpGGcdrLvloIXwGTqX8ZAQNsB9A0"

  val writeKey = System.getenv.asScala.getOrElse(
    "SEGMENT_WRITE_KEY",
    System.getProperty(
      "SegmentWriteKey",
      DefaultWriteKey
    )
  )


  val segment: Analytics = Analytics.builder(writeKey).build

  private def withUserInfo[T](fn: SnykUserInfo => T): Try[T] = apiClient.userInfo() map fn

  override def identify(): Try[Unit] = withUserInfo { user =>
    segment.enqueue(Messages.identify(user))
  }

  override def track(eventName: String, props: Map[String, Any]): Try[Unit] = withUserInfo { user =>
    segment.enqueue(Messages.track(eventName, user.id, props))
  }
}
