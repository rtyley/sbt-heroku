package com.heroku.sbt

import sbt.Keys._
import sbt._

object HerokuPlugin extends AutoPlugin {

  object autoImport {

    val deployHeroku = taskKey[Unit]("Deploy to Heroku.")
    val herokuJdkVersion = settingKey[String]("Set the major version of the JDK to use.")
    val herokuAppName = settingKey[String]("Set the name of the Heroku application.")
    val herokuConfigVars = settingKey[Map[String,String]]("Config variables to set on the Heroku application.")
    val herokuJdkUrl = settingKey[String]("The location of the JDK binary.")
    val herokuProcessTypes = settingKey[Map[String,String]]("The process types to run on Heroku (similar to Procfile).")
    val herokuIncludePaths = settingKey[Seq[String]]("A list of directory paths to include in the slug.")

    lazy val baseHerokuSettings: Seq[Def.Setting[_]] = Seq(
      deployHeroku := {
        val builder = new DeployBuilder().
          withBaseDirectory(baseDirectory.value).
          withTargetDirectory(target.value).
          withAppName((herokuAppName in deployHeroku).value).
          withConfigVars((herokuConfigVars in deployHeroku).value).
          withProcTypes((herokuProcessTypes in deployHeroku).value).
          withIncludePaths((herokuIncludePaths in deployHeroku).value).
          withLogger(streams.value.log)
        if ((herokuJdkUrl in deployHeroku).value.isEmpty) {
          builder.withJdkVersion((herokuJdkVersion in deployHeroku).value)
        } else {
          builder.withJdkUrl(new java.net.URL((herokuJdkUrl in deployHeroku).value))
        }
        builder.deploy()
      },
      herokuJdkVersion in Compile := "1.7",
      herokuAppName in Compile := "",
      herokuConfigVars in Compile := Map[String,String](),
      herokuProcessTypes in Compile := Map[String,String](),
      herokuJdkUrl in Compile := "",
      herokuIncludePaths in Compile := Seq()
    )
  }

  import com.heroku.sbt.HerokuPlugin.autoImport._

  override def requires = sbt.plugins.JvmPlugin

  override def trigger = allRequirements

  override val projectSettings =
    inConfig(Compile)(baseHerokuSettings) ++
    Seq(
      extraLoggers := {
        val currentFunction = extraLoggers.value
        (key: ScopedKey[_]) => {
          val taskOption = key.scope.task.toOption
          val loggers = currentFunction(key)
          if (taskOption.map(_.label) == Some("deployHeroku"))
            new HerokuLogger(target.value / "heroku" / "diagnostics.log") +: loggers
          else
            loggers
        }
      }
    )
}

class HerokuLogger (diagnosticsFile: File) extends BasicLogger {
  IO.writeLines(diagnosticsFile, Seq(
    "+--------------------------------------------------------------------",
    "| JDK Version -> " + System.getProperty("java.version"),
    "| Java Vendor -> " + System.getProperty("java.vendor.url"),
    "| Java Home   -> " + System.getProperty("java.home"),
    "| OS Arch     -> " + System.getProperty("os.arch"),
    "| OS Name     -> " + System.getProperty("os.name"),
    "| JAVA_OPTS   -> " + System.getenv("JAVA_OPTS"),
    "| SBT_OPTS    -> " + System.getenv("SBT_OPTS"),
    "+--------------------------------------------------------------------"
  ))

  def log(level: Level.Value, message: => String): Unit =
    IO.write(diagnosticsFile, message + "\n", IO.defaultCharset, true)

  def trace(t: => Throwable): Unit = {
    IO.write(diagnosticsFile, t.getMessage, IO.defaultCharset, true)
    IO.writeLines(diagnosticsFile, t.getStackTrace.map("    " + _.toString), IO.defaultCharset, true)
  }

  def success(message: => String): Unit =
    IO.write(diagnosticsFile, message + "\n", IO.defaultCharset, true)

  def control(event: ControlEvent.Value, message: => String): Unit = {}
  def logAll(events: Seq[LogEvent]): Unit = events.foreach(log)
}