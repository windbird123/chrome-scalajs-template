package com.alexitc.chromeapp.activetab

import com.alexitc.chromeapp.Config
import com.alexitc.chromeapp.Message.BgResponse
import com.alexitc.chromeapp.background.BackgroundAPI
import com.alexitc.chromeapp.common.I18NMessages
import com.alexitc.chromeapp.facades.SweetAlert
import com.raquo.domtypes.generic.codecs.StringAsIsCodec
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

import scala.concurrent.Future
import scala.scalajs.js.JSConverters._
import com.raquo.laminar.api.L._
import org.scalajs.dom

import scala.scalajs.js
import scala.util.{Failure, Success}

class Runner(
    config: ActiveTabConfig,
    backgroundAPI: BackgroundAPI,
    messages: I18NMessages,
    scriptInjector: ScriptInjector,
    externalMessageProcessor: ExternalMessageProcessor
) {

  def run(): Unit = {
    val prevPageId: Var[String] = Var("")
    val currPageId: Var[String] = Var("")
    val isPageIdChanged: Var[Boolean] = Var(false)

    val smParm: Var[String] = Var("MY_SM_PARAM")

    val callback: js.Function1[js.Object, Unit] = (x: js.Object) => {
      val bgResponse = BgResponse.decode(x.asInstanceOf[String])

      val currPageIdValue = bgResponse.oldPageId
      val newPageIdValue = bgResponse.newPageId

      isPageIdChanged.set(currPageIdValue.nonEmpty && newPageIdValue != currPageIdValue)
      prevPageId.set(currPageIdValue)
      currPageId.set(newPageIdValue)
    }

    chrome.runtime.Runtime.sendMessage(message = "{}", responseCallback = callback)

    val scopeAttr: HtmlAttr[String] = customHtmlAttr("scope", StringAsIsCodec)

    def makeButton(query: String) = a(
      "조회",
      role := "button",
      cls := "btn btn-success btn-sm",
      href := s"https://www.google.com/search?q=$query",
      target := "_blank"
    )

    val content = div(
      cls := "container-fluid fixed-bottom mb-2 py-2",
      div(
        cls := "row gx-4",
        div(
          cls := "col-8",
          table(
            cls := "table table-dark table-bordered",
            thead(
              tr(th(scopeAttr := "col", ""), th(scopeAttr := "col", "Previous"), th(scopeAttr := "col", "Current"))
            ),
            tbody(
              tr(
                th(scopeAttr := "row", "page-id"),
                td(
                  div(
                    cls := "d-flex",
                    div(child.text <-- prevPageId),
                    div(child <-- prevPageId.signal.map(makeButton))
                  )
                ),
                td(
                  cls.toggle("table-warning") <-- isPageIdChanged,
                  div(
                    cls := "d-flex",
                    div(child.text <-- currPageId),
                    div(child <-- currPageId.signal.map(makeButton))
                  )
                )
              ),
              tr(
                th(scopeAttr := "row", "session-id"),
                td("prev-sessionId"),
                td("current-sessionId")
              )
            )
          )
        ),
        div(
          cls := "col-4",
          table(
            cls := "table table-dark table-bordered",
            thead(
              tr(th(scopeAttr := "col", ""), th(scopeAttr := "col", "Value"))
            ),
            tbody(
              tr(
                th(scopeAttr := "row", "sm"),
                td(child.text <-- smParm.signal)
              )
            )
          )
        )
      )
    )

    val containerNode = dom.document.getElementById("_sch")
    render(containerNode, content)

  }

  private def injectPrivilegedScripts(scripts: Seq[String]): Future[Unit] = {
    // it's important to load the scripts in the right order
    scripts.foldLeft(Future.unit) { case (acc, cur) =>
      acc.flatMap(_ => scriptInjector.injectPrivilegedScript(cur))
    }
  }

  private def log(msg: String): Unit = {
    println(s"activeTab: $msg")
  }
}

object Runner {

  def apply(config: Config): Runner = {
    val backgroundAPI = new BackgroundAPI
    val messages = new I18NMessages
    val scriptInjector = new ScriptInjector
    val commandProcessor = new CommandProcessor
    val externalMessageProcessor = new ExternalMessageProcessor(commandProcessor)
    new Runner(config.activeTabConfig, backgroundAPI, messages, scriptInjector, externalMessageProcessor)
  }
}
