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

For usage, see Sample.scala in src/test/scala/com/twitter/tweetcreek.

