package com.alexitc.chromeapp.background

import chrome.tabs.bindings.{Tab, TabQuery}
import com.alexitc.chromeapp.Config
import com.alexitc.chromeapp.Message.BgResponse
import com.alexitc.chromeapp.background.alarms.AlarmRunner
import com.alexitc.chromeapp.background.models.{Command, Event}
import com.alexitc.chromeapp.background.services.browser.BrowserNotificationService
import com.alexitc.chromeapp.background.services.storage.StorageService
import com.alexitc.chromeapp.common.I18NMessages
import io.circe.generic.auto._
import io.circe.syntax._

import scala.concurrent.{Future, Promise}
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

import scala.scalajs.js
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

class Runner(
    commandProcessor: CommandProcessor,
    alarmRunner: AlarmRunner
) {

  def run(): Unit = {
    println("BACK run only once ------------------------- ")

//    chrome.system.cpu.CPU.getInfo.onComplete {
//      case Success(info) => println(info.archName)
//      case Failure(error) => println("ohoh something went wrong!")
//    }

    var oldPageId: String = ""

    chrome.runtime.Runtime.onMessage.listen { message =>
      message.value.foreach { any =>
//        chrome.tabs.Tabs.query(TabQuery(active = true, lastFocusedWindow = true)).onComplete {
//          case Success(tabs) =>
//            println(tabs.head.url)
//          case Failure(error) => println("ohoh something went wrong!")
//        }

        val s = any.asInstanceOf[String]
        println(s)

        val p: Promise[String] = Promise[String]()
        val f: js.Function1[js.Dynamic, Unit] = (info: js.Dynamic) => {
          println("Cookie-Value: " + info.value)


          val newPageId = info.value.asInstanceOf[String]
          val bgResponse = BgResponse(oldPageId, newPageId)
          p.success(bgResponse.encode())

          oldPageId = newPageId
        }

        js.Dynamic.global.chrome.cookies.get(
          js.Dictionary("name" -> "page_uid", "url" -> "http://d2m0.search.naver.com"),
          f
        )

        message.response(p.future, "failed")
      }
    }

    log("This was run by the background script")
//    alarmRunner.register()
//    processExternalMessages()
  }

  /** Enables the future-based communication between contexts to the background contexts.
    *
    * Internally, this is done by string-based messages, which we encode as JSON.
    */
  private def processExternalMessages(): Unit = {
    chrome.runtime.Runtime.onMessage.listen { message =>
      message.value.foreach { any =>
        val response = Future
          .fromTry { Try(any.asInstanceOf[String]).flatMap(Command.decode) }
          .map { cmd =>
            log(s"Got command = $cmd")
            cmd
          }
          .flatMap(commandProcessor.process)
          .recover { case NonFatal(ex) =>
            log(s"Failed to process command, error = ${ex.getMessage}")
            Event.CommandRejected(ex.getMessage)
          }
          .map(_.asJson.noSpaces)

        /** NOTE: When replying on futures, the method returning an async response is the only reliable one otherwise,
          * the sender is getting no response, a way to use the async method is to pass a response in case of failures
          * even if that case was already handled with the CommandRejected event.
          */
        message.response(response, "Impossible failure")
      }
    }
  }

  private def log(msg: String): Unit = {
    println(s"background: $msg")
  }
}

object Runner {

  def apply(config: Config): Runner = {
    val storage = new StorageService
    val messages = new I18NMessages
    val browserNotificationService = new BrowserNotificationService(messages)
    val commandProcessor =
      new CommandProcessor(storage, browserNotificationService)

    val productUpdaterAlarm = new AlarmRunner(
      config.alarmRunnerConfig,
      messages,
      browserNotificationService
    )
    new Runner(commandProcessor, productUpdaterAlarm)
  }
}
