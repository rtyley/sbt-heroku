package com.example

import com.twitter.finagle.{Http, Service}
import com.twitter.util.{Await, Future}
import com.twitter.finagle.http.Response
import java.net.InetSocketAddress
import org.jboss.netty.handler.codec.http._
import util.Properties

import java.io.{InputStream, FileNotFoundException, InputStreamReader, BufferedReader}
import java.net.URL
import javax.net.ssl.HttpsURLConnection

import scala.collection.JavaConversions._

object Server {
  def main(args: Array[String]) {
    val port = Properties.envOrElse("PORT", "8080").toInt
    println("Starting on port: "+port)

    val server = Http.serve(":" + port, new Hello)
    Await.ready(server)
  }
}

class Hello extends Service[HttpRequest, HttpResponse] {
  def apply(request: HttpRequest): Future[HttpResponse] = {
    val response = Response()

    val urlStr = "https://httpbin.org/get?show_env=1"
    val url = new URL(urlStr)
    val con = url.openConnection.asInstanceOf[HttpsURLConnection]
    con.setDoInput(true)
    con.setRequestMethod("GET")

    //headers.foreach { case (key, value) => con.setRequestProperty(key, value)}

    val r = handleResponse(con)
    response.setContentString("Hello from Scala on JDK " + System.getProperty("java.specification.version"))

    Future(response)
  }

  def handleResponse(con: HttpsURLConnection): String = {
    try {
      readStream(con.getInputStream)
    } catch {
      case e: Exception =>
        e.printStackTrace()
        val output = readStream(con.getErrorStream)
        throw new Exception("HTTP " + String.valueOf(con.getResponseCode) + ": " + e.getMessage)
    }
  }

  def readStream(is: InputStream): String = {
    val reader = new BufferedReader(new InputStreamReader(is))
    var output = ""
    var tmp = reader.readLine
    while (tmp != null) {
      output += tmp
      tmp = reader.readLine
    }
    output
  }
}
