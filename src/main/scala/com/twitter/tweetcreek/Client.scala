package com.twitter.tweetcreek

import java.io.{BufferedReader, InputStreamReader}
import java.net.SocketTimeoutException
import java.util.concurrent.{CountDownLatch, LinkedBlockingQueue}
import java.util.concurrent.atomic.AtomicBoolean

import net.lag.logging.Logger

import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.conn.HttpHostConnectException
import org.apache.http.protocol.HttpContext

// only in 2.8.0, but a nice check if you aren't cross-compiling
//import scala.annotation.tailrec

case class ClientConfig(maxRetries: Int,
                        backOffFactorMs: Int,
                        maxQueueDepth: Int)

class Client(config: ClientConfig,
             client: HttpClient,
             request: HttpUriRequest,
             context: HttpContext)
extends Runnable {

  val log = Logger.get
  log.info("starting client with config %s", config)

  val queue = new LinkedBlockingQueue[String]()
  val stopFlag = new AtomicBoolean(false)
  val done = new CountDownLatch(1)

  var tries = 0

  def retryableError(code: Int) = {
    // server error or timeout
    code > 499 || code == -1
  }

  //@tailrec
  final def run(): Unit = {
    if (!stopFlag.get() && backoff()) {
      connect() match {
        case Right((request, response)) => {
          log.info("connected to %s", request.getRequestLine().getUri)
          tries = 0
          try {
            stream(response)
          } catch {
            case e: SocketTimeoutException => {
              log.warning("timeout reading from stream")
              run()
            }
            case e => log.error(e, "error reading stream: %s", e)
          } finally {
            run()
          }
        }
        case Left(code) => {
          if (retryableError(code)) {
            log.debug("got a response code of %d connecting to stream", code)
            run()
          } else {
            log.warning("got a fatal response of %d connecting to stream. Stopping client.", code)
            request.abort()
          }
        }
      }
    } else {
      done.countDown()
      request.abort()
    }
  }

  def backoff() = {
    if (tries > config.maxRetries) {
      log.info("retries (%d) is greater than maxRetries (%d). Exiting.",
               tries, config.maxRetries)
      false
    } else if (tries > 0) {
      val sleepTime = tries * tries * config.backOffFactorMs
      log.info("sleeping %d ms before attempting reconnect", sleepTime)
      Thread.sleep(sleepTime)
      true
    } else {
      true
    }
  }

  def stop() = {
    log.info("stopping stream")
    stopFlag.set(true)
  }

  def connect(): Either[Int, (HttpUriRequest, HttpResponse)] = {
    log.info("connecting to stream")
    tries += 1
    try  {
      val response = client.execute(request, context)
      val status = response.getStatusLine()
      val statusCode = status.getStatusCode
      if (statusCode == 200) {
        Right((request, response))
      } else {
        Left(statusCode)
      }
    } catch {
      case e: SocketTimeoutException => {
        log.warning("timeout connecting to stream")
        Left(-1)
      }
      case e: HttpHostConnectException => {
        log.warning("host connect exception connecting to stream")
        Left(-2)
      }
      case e => {
        log.warning(e, "unknown error connecting to stream")
        Left(-3)
      }
    }
  }

  def stream(response: HttpResponse): Unit = {
    val reader = new BufferedReader(new InputStreamReader(response.getEntity.getContent()))
    streamReader(reader)
  }

  //@tailrec
  final def streamReader(reader: BufferedReader): Unit = {
    var line = reader.readLine()
    if (stopFlag.get()) {
      log.info("stream stopped, exiting")
    } else if (line == null) {
      log.info("read null line, stream has ended")
    } else {
      log.trace("read line from stream %s", line)
      queue.offer(line)
      val queueSize = queue.size
      log.trace("queue size is %d", queueSize)
      if (queue.size > config.maxQueueDepth) {
        log.warning("queue size %d is greater than max queue depth (%d). Exiting",
                    queue.size, config.maxQueueDepth)
        stop()
      }
      streamReader(reader)
    }
  }
}
