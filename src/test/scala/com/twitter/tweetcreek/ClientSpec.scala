package com.twitter.tweetcreek

import java.util.concurrent.{CountDownLatch, TimeUnit}

import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.params.{BasicHttpParams, HttpConnectionParams}
import org.apache.http.protocol.BasicHttpContext

import org.specs.Specification
import org.specs.matcher.Matcher

object ClientSpec extends Specification {
  "Client" should {
    val httpParams = new BasicHttpParams()
    HttpConnectionParams.setConnectionTimeout(httpParams, 1000)
    HttpConnectionParams.setSoTimeout(httpParams, 1000)

    val client = new DefaultHttpClient(httpParams)
    val clientConfig = ClientConfig(2, 100, 5)
    val getMethod = new HttpGet("http://localhost:9501/1/foo")
    val localContext = new BasicHttpContext()

    // this is how you inject auth
    /*val credentials = new UsernamePasswordCredentials("username", "password")
     val credsProvider = new BasicCredentialsProvider()
     credsProvider.setCredentials(
     new AuthScope("stream.twitter.com", AuthScope.ANY_PORT),
     credentials)
     localContext.setAttribute(ClientContext.CREDS_PROVIDER, credsProvider)*/

    var sheddy: Sheddy = null
    doBefore {
      sheddy = new Sheddy(9501)
    }

    doAfter {
      if (sheddy != null) {
        sheddy.stop()
        sheddy = null
      }
    }

    "handle a basic stream" in {
      var hasRun = false
      var countDown = new CountDownLatch(1)
      var done = new CountDownLatch(1)
      sheddy(".*")((path, request, response) => {
        if (!hasRun) {
          response.setStatus(200)
          response.flushBuffer()
          val writer = response.getWriter()
          writer.println("foo")
          writer.println("bar")
          writer.println("baz")
          writer.flush()
          countDown.await()
          writer.close()
          done.countDown()
          hasRun = true
        } else {
          response.getOutputStream.close()
        }
      })

      val tweetClient = new Client(clientConfig, client, getMethod, localContext)

      val queue = tweetClient.queue
      val t = new Thread(tweetClient)
      t.start()
      val res1 = queue.poll(1000, TimeUnit.MILLISECONDS)
      val res2 = queue.poll(1000, TimeUnit.MILLISECONDS)
      val res3 = queue.poll(1000, TimeUnit.MILLISECONDS)
      res1 must be_==("foo")
      res2 must be_==("bar")
      res3 must be_==("baz")
      tweetClient.stop()
      countDown.countDown()
      tweetClient.done.await()
      done.await()
      t.getState must be_==(Thread.State.TERMINATED)
    }

    "handle connect timeouts" in {
      var hasRun = false
      var countDown = new CountDownLatch(1)
      var done = new CountDownLatch(1)
      sheddy(".*")((path, request, response) => {
        if (!hasRun) {
          hasRun = true
          countDown.await()
          response.setStatus(200)
          response.flushBuffer()
          response.getOutputStream().close()
        } else {
          response.setStatus(200)
          response.flushBuffer()
          val writer = response.getWriter()
          writer.println("foo")
          writer.flush()
          countDown.await()
          writer.close()
          done.countDown()
        }
      })

      val tweetClient = new Client(clientConfig, client, getMethod, localContext)
      val queue = tweetClient.queue
      val t = new Thread(tweetClient)
      t.start()
      val res1 = queue.poll(2000, TimeUnit.MILLISECONDS)
      tweetClient.stop()
      tweetClient.done.await()
      countDown.countDown()
      res1 must be_==("foo")
      done.await()
      t.getState must be_==(Thread.State.TERMINATED)
    }

    "handle socket timeouts" in {
      val sleepTime = 1500
      var hasRun = false
      var countDown = new CountDownLatch(1)
      var done = new CountDownLatch(2)
      sheddy(".*")((path, request, response) => {
        response.setStatus(200)
        response.flushBuffer()
        val writer = response.getWriter()
        writer.println("foo")
        writer.flush()
        if (!hasRun) {
          // the first run times out.
          hasRun = true
          countDown.await()
          writer.close()
          done.countDown()
        } else {
          // the second flushes the first message, then waits
          writer.flush()
          countDown.await()
          writer.close()
          done.countDown()
        }
      })

      val tweetClient = new Client(clientConfig, client, getMethod, localContext)
      val queue = tweetClient.queue
      val t = new Thread(tweetClient)
      t.start()
      val res1 = queue.poll(sleepTime + 1000, TimeUnit.MILLISECONDS)
      val res2 = queue.poll(sleepTime + 1000, TimeUnit.MILLISECONDS)
      tweetClient.stop()
      res1 must be_==("foo")
      res2 must be_==("foo")
      // free up original hanger
      countDown.countDown()
      done.await()
      tweetClient.done.await()
      t.getState must be_==(Thread.State.TERMINATED)
    }

    "exit on backpressure" in {
      val sleepTime = 1500
      var hasRun = false
      var countDown = new CountDownLatch(1)
      var done = new CountDownLatch(1)
      sheddy(".*")((path, request, response) => {
        response.setStatus(200)
        response.flushBuffer()
        val writer = response.getWriter()
        writer.println("foo")
        writer.println("foo")
        writer.println("foo")
        writer.println("foo")
        writer.println("foo")
        writer.println("foo")
        writer.println("foo")
        writer.flush()
        countDown.await()
        writer.close()
        done.countDown()
      })

      val tweetClient = new Client(clientConfig, client, getMethod, localContext)
      val queue = tweetClient.queue
      val t = new Thread(tweetClient)
      t.start()
      tweetClient.done.await()
      countDown.countDown()
      t.getState must be_==(Thread.State.TERMINATED)
      done.await()
    }
  }
}
