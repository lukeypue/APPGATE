package com.appgate.tv.sitebrain

import java.net.URI

object SafeActionExecutor {
    fun javascriptFor(
        element: SemanticElement,
        actionKind: ActionKind,
        query: String?,
        currentHost: String
    ): String? {
        if (SafeActionClassifier.classify(element) != SafetyClass.SAFE) return null
        if (actionKind in setOf(
                ActionKind.MESSAGE,
                ActionKind.PURCHASE,
                ActionKind.POST,
                ActionKind.DELETE,
                ActionKind.FOLLOW,
                ActionKind.ACCOUNT_CHANGE,
                ActionKind.SUBMIT_FORM,
                ActionKind.LOGIN,
                ActionKind.UNKNOWN
            )) return null

        val href = element.href?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        if (href != null) {
            val host = runCatching { URI(href).host.orEmpty().lowercase().removePrefix("www.") }.getOrDefault("")
            val expected = currentHost.lowercase().removePrefix("www.")
            if (host.isBlank() || !(host == expected || host.endsWith(".$expected") || expected.endsWith(".$host"))) return null
            return "window.location.href=${jsString(href)}; 'NAVIGATE';"
        }

        val selector = element.locatorHints.firstOrNull { isSafeSelector(it) } ?: return null
        if (actionKind == ActionKind.SEARCH && element.tag in setOf("input", "textarea")) {
            val value = query?.trim()?.takeIf { it.isNotBlank() } ?: return null
            return """
                (function(){
                  var el=document.querySelector(${jsString(selector)});
                  if(!el) return 'MISSING';
                  if(el.disabled || el.getAttribute('aria-disabled')==='true') return 'DISABLED';
                  el.focus();
                  el.value=${jsString(value)};
                  el.dispatchEvent(new Event('input',{bubbles:true}));
                  el.dispatchEvent(new Event('change',{bubbles:true}));
                  var form=el.form || (el.closest ? el.closest('form') : null);
                  if(form){
                    if(typeof form.requestSubmit==='function'){ form.requestSubmit(); return 'SUBMITTED'; }
                    form.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));
                    if(typeof form.submit==='function') form.submit();
                    return 'SUBMITTED';
                  }
                  var kd=new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true});
                  var ku=new KeyboardEvent('keyup',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true});
                  el.dispatchEvent(kd); el.dispatchEvent(ku);
                  return 'ENTER';
                })();
            """.trimIndent()
        }

        return """
            (function(){
              var el=document.querySelector(${jsString(selector)});
              if(!el) return 'MISSING';
              if(el.disabled || el.getAttribute('aria-disabled')==='true') return 'DISABLED';
              el.click();
              return 'CLICKED';
            })();
        """.trimIndent()
    }

    private fun isSafeSelector(value: String): Boolean {
        val v = value.trim()
        if (v.isBlank() || v.length > 220) return false
        return v.startsWith("#") || v.startsWith("[") || Regex("^[a-zA-Z][a-zA-Z0-9_-]*(\\[[^]]+])?$").matches(v)
    }

    private fun jsString(value: String): String = "'" + value
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n") + "'"
}
