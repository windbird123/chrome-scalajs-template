package com.alexitc.chromeapp.activetab

import com.alexitc.chromeapp.Config
import com.alexitc.chromeapp.Message.BgResponse
import com.alexitc.chromeapp.background.BackgroundAPI
import com.alexitc.chromeapp.common.I18NMessages
import com.raquo.domtypes.generic.codecs.StringAsIsCodec
import com.raquo.laminar.api.L._
import org.scalajs.dom
import org.scalajs.dom.html.Script
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._
import play.api.libs.json.Json

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

    var newWhereParam: String = ""

    val callback: js.Function1[js.Object, Unit] = (x: js.Object) => {
      def parseParam(key: String, url: String): String =
        url.split(Array('?', '&')).find(_.startsWith(s"$key=")).map(_.drop(key.length + 1)).getOrElse("")

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

      // where param
      newWhereParam = parseParam("where", newTabUrl)
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
      cls := "container-fluid fixed-bottom py-1",
      div(
//        cls := "row gx-4",
        table(
          cls := "table table-secondary table-bordered",
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

    val containerNode = dom.document.getElementById("container")
    render(containerNode, content)

    // change <a> tag title
    def getAreaCode(scriptText: String): Option[String] = {
      if (scriptText.contains("goOtherCR")) {
        val target = "a=([a-zA-Z_0-9*.]+)".r
        target.findFirstIn(scriptText).map(_.drop("a=".length))
      } else {
        None
      }
    }

    // Naver AiTEMS 추천 영역은 dom loading 이 늦는 것 같다.
    // 1초정도 delay 를 두어 완전히 로딩이 완료되었을 것 같은 시점에 a tag 를 검색해 변경한다.
    dom.window.setTimeout(
      () => {
        val aTags = dom.document.getElementsByTagName("a")
        aTags.foreach { elm =>
          val scriptText = Option(elm.getAttribute("onclick"))

          for {
            text <- scriptText
            code <- getAreaCode(text)
          } elm.setAttribute("title", code)
        }
      },
      1000
    )

    //  var nx_cr_area_info = [{"n": "pwl_nop", "r": 1},{"n": "shp_tre", "r": 2},{"n": "shb_bas", "r": 3},{"n": "ink_mik", "r": 4},{"n": "rvw", "r": 5},{"n": "img", "r": 6},{"n": "loc_plc", "r": 7},{"n": "web_gen", "r": 8},{"n": "biz_nop", "r": 9}];
    def getNxCrAreaInfo(scriptText: String): List[String] = {
      val target = ".*nx_cr_area_info\\W+(\\[.*]);".r
      scriptText match {
        case target(json) =>
          val seq = Json.parse(json) \\ "n"
          seq.map(_.as[String]).toList

        case _ => List.empty[String]
      }
    }

    def getSectionCode(sectionList: List[String], node: dom.Element): Option[String] = {
      val aTags = node.getElementsByTagName("a")

      val childCodes: collection.Seq[String] = aTags.flatMap { elm =>
        val scriptText = Option(elm.getAttribute("onclick"))

        for {
          text <- scriptText
          code <- getAreaCode(text)
        } yield code
      }

      val rep: Option[String] = childCodes.headOption

      rep.flatMap { r =>
        sectionList.find(l => r.startsWith(l))
      }
    }

    dom.window.setTimeout(
      () => {
        // 통검 페이지일 경우에만 section 정보를 노출한다.
        // Pc:nexearch, Mobile: m
        if (newWhereParam == "nexearch" || newWhereParam == "m") {

          val scriptTags = dom.document.getElementsByTagName("script").map(_.asInstanceOf[Script])
          val scriptText: Option[Script] = scriptTags.find(_.asInstanceOf[Script].text.contains("nx_cr_area_info"))

          val sectionList: List[String] = scriptText match {
            case Some(elm) => getNxCrAreaInfo(elm.text.trim)
            case None => List.empty[String]
          }

          val sectionTags = dom.document.getElementsByTagName("section")
          sectionTags.foreach { elm =>
            val sectionCode = getSectionCode(sectionList, elm).getOrElse("")
            if (sectionCode.nonEmpty) {
              elm.setAttribute("style", "border: 4px solid green;")
              val sectionCodeDiv = div(h2(b(sectionCode))).ref
              elm.insertBefore(sectionCodeDiv, elm.firstChild)
            }
          }

          // TODO: sectionList 를 노출
          println(sectionList)
        }
      },
      1000
    )
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
