package jcef;

import org.cef.browser.CefBrowser;
import org.cef.callback.CefJSDialogCallback;
import org.cef.handler.CefJSDialogHandler;
import org.cef.misc.BoolRef;

import static org.cef.handler.CefJSDialogHandler.JSDialogType.JSDIALOGTYPE_ALERT;
import static org.cef.handler.CefJSDialogHandler.JSDialogType.JSDIALOGTYPE_CONFIRM;
import static org.cef.handler.CefJSDialogHandler.JSDialogType.JSDIALOGTYPE_PROMPT;

public class JsDialogHandler implements CefJSDialogHandler {
  @Override
  public boolean onJSDialog(CefBrowser browser,
                            java.lang.String origin_url,
                            CefJSDialogHandler.JSDialogType dialog_type,
                            java.lang.String message_text,
                            java.lang.String default_prompt_text,
                            CefJSDialogCallback callback,
                            BoolRef suppress_message) {
    suppress_message.set(false);
    if (dialog_type == JSDIALOGTYPE_ALERT) {
      return true;
    }
    if (dialog_type == JSDIALOGTYPE_CONFIRM) {
      return true;
    }

    if (dialog_type == JSDIALOGTYPE_PROMPT) {
      return true;
    }
    return false;
  }

  @Override
  public boolean onBeforeUnloadDialog(CefBrowser cefBrowser, String s, boolean b, CefJSDialogCallback cefJSDialogCallback) {
    return false;
  }

  @Override
  public void onResetDialogState(CefBrowser cefBrowser) {

  }

  @Override
  public void onDialogClosed(CefBrowser cefBrowser) {

  }
}
