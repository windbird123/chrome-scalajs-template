import chrome.permissions.Permission
import chrome.permissions.Permission.API
import chrome.{Background, BrowserAction, ContentScript, ExtensionManifest}
import com.alexitc.Chrome

object AppManifest {
  // scripts used on all modules
  val commonScripts = List("scripts/common.js", "main-bundle.js")

  // The script that runs on the current tab context needs the common scripts to execute scalajs code.
  val manifestActiveTabWebsiteScripts = commonScripts :+ "scripts/active-tab-website-script.js"

  def generate(appName: String, appVersion: String): ExtensionManifest = {
    new ExtensionManifest {
      override val name = appName
      override val version = appVersion

      override val description = Some(
        "TO BE UPDATED" // TODO: REPLACE ME
      )
      override val icons = Chrome.icons("icons", "app.png", Set(48, 96, 128))

      // TODO: REPLACE ME, use only the minimum required permissions
      override val permissions = Set[Permission](
        API("http://d2m0.search.naver.com/"),
        API.System.CPU,
        API.Tabs,
        API.Cookies,
        API.Storage,
        API.Notifications,
        API.Alarms
      )

      override val defaultLocale: Option[String] = Some("en")

      // TODO: REPLACE ME
      override val browserAction: Option[BrowserAction] =
        Some(BrowserAction(icons, Some("TO BE DEFINED - POPUP TITLE"), Some("popup.html")))

      override val background = Background(
        scripts = commonScripts ::: List("scripts/background-script.js")
      )

      override val contentScripts: List[ContentScript] = List(
        ContentScript(
          matches = List(
            "http://d2m0.search.naver.com:10088/*" // TODO: REPLACE ME
          ),
          css = List("css/active-tab.css", "css/bootstrap.min.css"),
          js = commonScripts ::: List("scripts/active-tab-script.js", "scripts/bootstrap.bundle.min.js")
        )
      )

      // the script running on the tab context requires the common scripts
      override val webAccessibleResources = "icons/*" :: manifestActiveTabWebsiteScripts
    }
  }
}
