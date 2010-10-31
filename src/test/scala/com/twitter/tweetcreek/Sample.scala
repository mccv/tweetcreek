package com.twitter.tweetcreek

import com.twitter.tweetcreek._

import java.util.concurrent.{CountDownLatch, TimeUnit}

import net.lag.logging.Logger

import org.apache.http.auth.{UsernamePasswordCredentials, AuthScope}
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.protocol.ClientContext
import org.apache.http.impl.client.{BasicCredentialsProvider, DefaultHttpClient}
import org.apache.http.params.{BasicHttpParams, HttpConnectionParams}
import org.apache.http.protocol.BasicHttpContext

import org.specs.Specification
import org.specs.matcher.Matcher

object SampleSpec extends Specification {
  val log = Logger.get

  "Sample" should {
    "sample" in {
      val httpParams = new BasicHttpParams()
      HttpConnectionParams.setConnectionTimeout(httpParams, 1000)
      HttpConnectionParams.setSoTimeout(httpParams, 1000)

      val client = new DefaultHttpClient(httpParams)
      val clientConfig = ClientConfig(2, 100, 50)

      val getMethod = new HttpGet("http://stream.twitter.com/1/statuses/sample.json")
      val localContext = new BasicHttpContext()

      // set these to run the sample test
      val username = System.getenv.get("TC_USERNAME")
      val password = System.getenv.get("TC_PASSWORD")
      if (username == null || password == null) {
        log.info("no username or password set.  Not running sample test")
      } else {
        // this is how you inject auth
        val credentials = new UsernamePasswordCredentials(username, password)
        val credsProvider = new BasicCredentialsProvider()
        credsProvider.setCredentials(
          new AuthScope("stream.twitter.com", AuthScope.ANY_PORT),
          credentials)
        localContext.setAttribute(ClientContext.CREDS_PROVIDER, credsProvider)
        val tweetClient = new Client(clientConfig, client, getMethod, localContext)
        val t = new Thread(tweetClient)
        t.start()
        var linesRead = 0
        for (i <- 1 to 50) {
          val line = tweetClient.queue.poll(60000, TimeUnit.MILLISECONDS)
          log.debug("read line %s", line)
          linesRead += 1
        }
        tweetClient.stop()
        linesRead must be_==(50)
      }
    }
  }
}
