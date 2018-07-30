package io.snyk.plugin.ui

import io.snyk.plugin.Implicits.DurationConverters._

import javafx.embed.swing.JFXPanel
import javafx.scene.{Group, Scene}
import javafx.scene.media.{Media, MediaPlayer, MediaView}
import javafx.scene.paint.Color
import javafx.beans.binding.Bindings
import javafx.beans.property.DoubleProperty
import scala.concurrent.duration._

/**
  * A panel that does nothing more than play supplied source videos on a loop and
  * scaled to fit the container.
  *
  * This entire class is nothing more than an ugly hack, and only necessary because
  * the WebView in JavaFX 8 is unable to show either h.264 video or animated GIFs!
  */
class SnykVideoPanel extends JFXPanel { self =>
  private[this] val mediaView: MediaView = new MediaView()

  private[this] var prevSource: Option[String] = None

  /**
    * If not already current, initialise a new MediaPlayer for the supplied source,
    * install the loop hook, and load it into our `MediaView`
    * - *does not* begin playback though
    */
  private[this] def loadSource(url: String): Unit = {
    if (!prevSource.contains(url)) {
      val absUrl = "/WEB-INF/" + (if (url.head == '/') url.tail else url)
      println(s"SnykVideoPanel.setSource $url [$absUrl]")
      val media = new Media(getClass.getResource(absUrl).toExternalForm)
      val player: MediaPlayer = new MediaPlayer(media)
      player.setMute(true)
      mediaView.setMediaPlayer(player)

      // This kind of monstrosity is why sane people simply don't do video in Java 8...
      // And recall, this is with the shiny new JavaFX 8 that came with such promises of being better!
      //
      // - h.264 doesn't work in webview (flv might, but good luck finding an encoder for that nowadays)
      // - animated GIFs, on Java 8, on MacOS? Nope, not a chance
      // - player.setCycleCount is ignored
      // - player.getOnEndOfMedia never fires
      // - currentTime doesn't always reach stopTime
      //     (and no, you can't get something like the current frame number, or an SMPTE time,
      //      because that might actually be useful!)
      //
      // So, here we are, with the only solution that doesn't completely fail:
      //   25fps means that 1 frame = 40ms
      //   So, we find the time of the penultimate (last-but-one) frame by subtracting 40ms from stopTime
      //   and we can't do it until the player flags itself as ready, meaning that all metadata has been parsed.
      //
      //     (because - of course - loading media, from the filesystem, is SERIOUSLY the best thing that Oracle could
      //      think of to make asynchronous in a language where even date/time parsing has not traditionally been
      //      thread-safe.  Stands to reason!)
      //
      //   Then we can FINALLY detect that we're at the end once we've passed the penultimate frame,
      //   by getting a constant stream of currentTime updates; because that sort of thing is just how Java rolls.
      player.setOnReady { () =>
        val stopTime = player.getStopTime.asScala
        val penultimateFrame = stopTime - 40.millis

        player.currentTimeProperty() addListener { (_, _, n) =>
          if (n.asScala > penultimateFrame) player.seek(Duration.Zero.asFx)
        }
      }
      prevSource = Some(url)
    }
  }

  def setSource(url: String): Unit = {
    loadSource(url)
    play()
  }

  def stop(): Unit = mediaView.getMediaPlayer.stop()
  def pause(): Unit = mediaView.getMediaPlayer.pause()
  def play(): Unit = mediaView.getMediaPlayer.play()

  loadSource("/assets/images/scanning.mp4")

  private[this] val mvw: DoubleProperty = mediaView.fitWidthProperty
  private[this] val mvh: DoubleProperty = mediaView.fitHeightProperty
  mvw.bind(Bindings.selectDouble(mediaView.sceneProperty, "width"))
  mvh.bind(Bindings.selectDouble(mediaView.sceneProperty, "height"))
  mediaView.setPreserveRatio(true)

  private[this] val root: Group = new Group
  root.getChildren.add(mediaView)

  self.setScene(new Scene(root, Color.web("#53537A")))
}
