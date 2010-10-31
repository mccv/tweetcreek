# TweetCreek

A minimal Twitter Streaming API client in Scala.
Supports the following key items

*    Reconnect/backoff strategy on non-200 HTTP responses
*    Reconnect strategy on socket timeouts
*    Reconnect strategy on connection timeouts
*    Asynchronous processing of messages

What it does *not* support:

*    Setting up Apache HttpComponents connections, authentication,
etc. You have the   control, you have the responsibility.
*    Parsing messages.  This is a line oriented client.  While you can use it with XML messages, it's far better suited for handling JSON responses.

What it should support, but doesn't (yet)

*    Stats gathering

To build the project, you'll need
[sbt](http://code.google.com/p/simple-build-tool/). From the
tweetcreek directory, run sbt update, then sbt package.  You'll get a
jar for scala 2.8.0 in target.

If you need to build for Scala 2.7.7, run sbt +update, then sbt
+compile.  You will get both 2.7.7 and 2.8.0 versions in your target
directory.

For usage, see Sample.scala in [src/test/scala/com/twitter/tweetcreek](http://github.com/mccv/tweetcreek/blob/master/src/test/scala/com/twitter/tweetcreek/Sample.scala).
