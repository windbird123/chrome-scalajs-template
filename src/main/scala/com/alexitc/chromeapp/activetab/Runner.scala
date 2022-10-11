package com.alexitc.chromeapp.activetab

import com.alexitc.chromeapp.Config
import com.alexitc.chromeapp.Message.BgResponse
import com.alexitc.chromeapp.background.BackgroundAPI
import com.alexitc.chromeapp.common.I18NMessages
import com.raquo.domtypes.generic.codecs.StringAsIsCodec
import com.raquo.laminar.api.L._
import org.scalajs.dom
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

import scala.concurrent.Future
import scala.scalajs.js

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

    val prevSessionId: Var[String] = Var("")
    val currSessionId: Var[String] = Var("")
    val isSessionIdChanged: Var[Boolean] = Var(false)

    val prevSmParam: Var[String] = Var("")
    val currSmParam: Var[String] = Var("")
    val isSmParamChanged: Var[Boolean] = Var(false)

    val callback: js.Function1[js.Object, Unit] = (x: js.Object) => {
      def parseParam(key: String, url: String): String =
        url.split(Array('?', '&')).find(_.startsWith(s"$key=")).map(_.drop(3)).getOrElse("")

      val bgResponse: BgResponse = BgResponse.decode(x.asInstanceOf[String])

      // pageId
      val currPageIdValue = bgResponse.oldPageId
      val newPageIdValue = bgResponse.newPageId

      isPageIdChanged.set(currPageIdValue.nonEmpty && newPageIdValue != currPageIdValue)
      prevPageId.set(currPageIdValue)
      currPageId.set(newPageIdValue)

      // sessionId
      val currSessionIdValue = bgResponse.oldSessionId
      val newSessionIdValue = bgResponse.newSessionId

      isSessionIdChanged.set(currSessionIdValue.nonEmpty && newSessionIdValue != currSessionIdValue)
      prevSessionId.set(currSessionIdValue)
      currSessionId.set(newSessionIdValue)

      // sm param
      val currTabUrl = bgResponse.oldTabUrl
      val newTabUrl = bgResponse.newTabUrl
      val currSmParamValue = parseParam("sm", currTabUrl)
      val newSmParamValue = parseParam("sm", newTabUrl)

      isSmParamChanged.set(currSmParamValue.nonEmpty && newSmParamValue != currSmParamValue)
      prevSmParam.set(currSmParamValue)
      currSmParam.set(newSmParamValue)
    }

    chrome.runtime.Runtime.sendMessage(message = "{}", responseCallback = callback)

    val scopeAttr: HtmlAttr[String] = customHtmlAttr("scope", StringAsIsCodec)

    def makeButton(paramName: String)(query: String) = a(
      "조회",
      role := "button",
      cls := "btn btn-success btn-sm mx-2",
      href := s"https://www.google.com/search?$paramName=$query",
      target := "_blank"
    )

    val content = div(
      cls := "container-fluid fixed-bottom mb-2 py-2",
      div(
//        cls := "row gx-4",
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
                  div(child <-- prevPageId.signal.map(makeButton("q")))
                )
              ),
              td(
                cls.toggle("table-warning") <-- isPageIdChanged,
                div(
                  cls := "d-flex",
                  div(child.text <-- currPageId),
                  div(child <-- currPageId.signal.map(makeButton("q")))
                )
              )
            ),
            tr(
              th(scopeAttr := "row", "session-id"),
              td(
                div(
                  cls := "d-flex",
                  div(child.text <-- prevSessionId),
                  div(child <-- prevSessionId.signal.map(makeButton("q")))
                )
              ),
              td(
                cls.toggle("table-warning") <-- isSessionIdChanged,
                div(
                  cls := "d-flex",
                  div(child.text <-- currSessionId),
                  div(child <-- currSessionId.signal.map(makeButton("q")))
                )
              )
            ),
            tr(
              th(scopeAttr := "row", "sm"),
              td(
                div(
                  cls := "d-flex",
                  div(child.text <-- prevSmParam)
                )
              ),
              td(
                cls.toggle("table-warning") <-- isSmParamChanged,
                div(
                  cls := "d-flex",
                  div(child.text <-- currSmParam)
                )
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
