/*
 * Scala.js (https://www.scala-js.org/)
 *
 * Copyright EPFL.
 *
 * Licensed under Apache License 2.0
 * (https://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package org.scalajs.junit

import sbt.testing._
import scala.scalajs.reflect.Reflect
import scala.util.{Try, Success, Failure}

private[junit] final class JUnitTask(val taskDef: TaskDef,
    runSettings: RunSettings) extends Task {

  def tags: Array[String] = Array.empty

  def execute(eventHandler: EventHandler, loggers: Array[Logger],
      continuation: Array[Task] => Unit): Unit = {
    continuation(execute(eventHandler, loggers))
  }

  def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] = {
    val fullClassName = taskDef.fullyQualifiedName
    val richLogger = new RichLogger(loggers, runSettings, fullClassName)

    val bootstrapperName = fullClassName + "$scalajs$junit$bootstrapper"

    def errorWhileLoadingClass(t: Throwable): Unit = {
      richLogger.log(_.error, "Error while loading test class: " + fullClassName)
      richLogger.trace(t)
      val selector = new TestSelector(fullClassName)
      val optThrowable = new OptionalThrowable(t)
      val ev = new JUnitEvent(taskDef, Status.Failure, selector, optThrowable)
      eventHandler.handle(ev)
    }

    Try {
      Reflect
        .lookupLoadableModuleClass(bootstrapperName + "$")
        .getOrElse(throw new ClassNotFoundException(s"Cannot find $bootstrapperName$$"))
        .loadModule()
    } match {
      case Success(bootstrapper: Bootstrapper) =>
        new JUnitExecuteTest(taskDef, runSettings, bootstrapper,
            richLogger, eventHandler).executeTests()

      case Success(_) =>
        val msg = s"Expected $bootstrapperName to extend Bootstrapper"
        errorWhileLoadingClass(new Exception(msg))

      case Failure(exception) =>
        errorWhileLoadingClass(exception)
    }

    Array()
  }
}
