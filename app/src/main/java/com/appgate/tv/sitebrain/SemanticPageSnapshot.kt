package com.appgate.tv.sitebrain

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest

object SemanticPageSnapshot {
    fun javascript(): String = """
        (function(){
          function clean(v){ return (v||'').replace(/\s+/g,' ').trim(); }
          function visible(el){
            if(!el) return false;
            var s=window.getComputedStyle(el);
            var r=el.getBoundingClientRect();
            return s && s.display!=='none' && s.visibility!=='hidden' && r.width>0 && r.height>0;
          }
          function label(el){
            var aria=el.getAttribute('aria-label');
            if(aria) return clean(aria);
            var labelled=el.getAttribute('aria-labelledby');
            if(labelled){
              var txt=labelled.split(/\s+/).map(function(id){ var n=document.getElementById(id); return n?clean(n.innerText||n.textContent):''; }).join(' ');
              if(clean(txt)) return clean(txt);
            }
            if(el.labels && el.labels.length) return clean(Array.from(el.labels).map(function(x){return x.innerText||x.textContent||'';}).join(' '));
            return clean(el.innerText||el.textContent||el.getAttribute('title')||el.getAttribute('placeholder')||el.getAttribute('name')||'');
          }
          function cssHint(el){
            if(el.id) return '#'+CSS.escape(el.id);
            var test=el.getAttribute('data-testid');
            if(test) return '[data-testid="'+test.replace(/"/g,'')+'"]';
            var name=el.getAttribute('name');
            if(name) return el.tagName.toLowerCase()+'[name="'+name.replace(/"/g,'')+'"]';
            var aria=el.getAttribute('aria-label');
            var role=el.getAttribute('role');
            if(aria && role) return '[role="'+role.replace(/"/g,'')+'"][aria-label="'+aria.replace(/"/g,'')+'"]';
            if(aria) return '[aria-label="'+aria.replace(/"/g,'')+'"]';
            if(role) return '[role="'+role.replace(/"/g,'')+'"]';
            return el.tagName.toLowerCase();
          }
          var selectors='a[href],button,input,select,textarea,[role="button"],[role="link"],[role="tab"],[role="menuitem"],[role="combobox"],[role="listbox"],[role="option"],[aria-haspopup="listbox"],[aria-expanded],[aria-label]';
          var elements=[];
          Array.from(document.querySelectorAll(selectors)).slice(0,700).forEach(function(el,i){
            if(!visible(el)) return;
            var type=(el.getAttribute('type')||'').toLowerCase();
            if(type==='password'||type==='hidden'||type==='file') return;
            var l=label(el).slice(0,180);
            var near='';
            var p=el.parentElement;
            if(p) near=clean(p.innerText||p.textContent||'').slice(0,220);
            elements.push({
              id:'e'+i,
              tag:(el.tagName||'').toLowerCase(),
              role:el.getAttribute('role')||null,
              label:l,
              href:el.href||null,
              inputType:type||null,
              selected:!!(el.checked||el.selected||el.getAttribute('aria-selected')==='true'||el.getAttribute('aria-pressed')==='true'),
              disabled:!!(el.disabled||el.getAttribute('aria-disabled')==='true'),
              nearbyText:near,
              locatorHints:[cssHint(el)],
              currentValue:(el.tagName==='SELECT' ? clean(el.options && el.selectedIndex>=0 ? el.options[el.selectedIndex].text : el.value) : clean(el.value||el.getAttribute('aria-valuetext')||el.getAttribute('aria-selected')||'')),
              choices:(el.tagName==='SELECT' && el.options ? Array.from(el.options).map(function(o){return clean(o.text||o.value||'');}).filter(Boolean).slice(0,120) : [])
            });
          });
          var headings=Array.from(document.querySelectorAll('h1,h2,h3,[role="heading"]')).filter(visible).map(function(h){return clean(h.innerText||h.textContent||'').slice(0,160);}).filter(Boolean).slice(0,50);
          var body=clean((document.body&&document.body.innerText)||'').slice(0,12000);
          var lower=((document.title||'')+' '+body).toLowerCase();
          var path=(location.pathname||'').toLowerCase();
          var visiblePasswordFields=Array.from(document.querySelectorAll('input[type="password"]')).filter(visible).length;
          var richInteractivePage=elements.length>=25 && body.length>=500;
          var authPath=/(^|\/)(login|signin|sign-in|checkpoint|auth)(\/|$)/.test(path);
          var authGate=/(log in to continue|login to continue|sign in to continue|sign up \/ log in|continue with google|continue with facebook|continue with apple)/.test(lower);
          var login=visiblePasswordFields>0 || (!richInteractivePage && (authPath || authGate));
          var challengePath=/(^|\/)(captcha|challenge|checkpoint|verify|security-check)(\/|$)/.test(path);
          var challengeTitle=/(captcha|verify you are human|security check|unusual traffic|confirm your identity|are you a robot)/.test((document.title||'').toLowerCase());
          var challengePhrase=/(verify you are human|unusual traffic|confirm your identity|are you a robot|complete the captcha|enter the characters you see)/.test(lower);
          var challengeWidget=Array.from(document.querySelectorAll(
            'iframe[src*="recaptcha"],iframe[src*="hcaptcha"],iframe[src*="turnstile"],[class*="recaptcha"],[class*="hcaptcha"],[class*="cf-turnstile"],[data-sitekey]'
          )).some(visible);
          var challenge=challengeWidget || (!richInteractivePage && (challengePath || challengeTitle || challengePhrase));
          var pageType='UNKNOWN';
          if(challenge) pageType='CHALLENGE';
          else if(login) pageType='LOGIN';
          else if(/search|results|listings|items found/.test(lower) && elements.filter(function(e){return !!e.href;}).length>5) pageType='RESULT_LIST';
          else if(/price|mileage|description|seller|condition/.test(lower) && headings.length>0) pageType='DETAIL';
          else if(location.pathname==='/'||location.pathname==='') pageType='HOME';
          else if(/category|classified|rent|sale|cars|vehicles|property/.test(location.pathname.toLowerCase())) pageType='CATEGORY';
          return JSON.stringify({
            url:location.href,
            host:location.host.toLowerCase(),
            title:document.title||'',
            visibleTextSummary:body,
            headings:headings,
            elements:elements,
            pageType:pageType,
            loginDetected:login,
            challengeDetected:challenge
          });
        })();
    """.trimIndent()

    fun parse(json: String): PageSnapshot {
        val o = JSONObject(json)
        val url = o.optString("url")
        val host = o.optString("host").ifBlank { runCatching { URI(url).host.orEmpty() }.getOrDefault("") }
        val headings = o.optJSONArray("headings").toStringList()
        val elements = o.optJSONArray("elements").toElements()
        val pageType = runCatching { PageType.valueOf(o.optString("pageType", "UNKNOWN")) }.getOrDefault(PageType.UNKNOWN)
        val base = PageSnapshot(
            url = url,
            host = host,
            routeSignature = normalizeRoute(url),
            title = o.optString("title"),
            visibleTextSummary = o.optString("visibleTextSummary").take(12000),
            headings = headings,
            elements = elements,
            pageType = pageType,
            loginDetected = o.optBoolean("loginDetected", false),
            challengeDetected = o.optBoolean("challengeDetected", false),
            fingerprint = ""
        )
        return base.copy(fingerprint = PageFingerprint.compute(base))
    }

    fun normalizeRoute(url: String): String {
        return runCatching {
            val uri = URI(url)
            val path = (uri.path ?: "/")
                .replace(Regex("/+$"), "")
                .ifBlank { "/" }
                .replace(Regex("/[0-9]{5,}(?=/|$)"), "/:id")
                .replace(Regex("/[0-9a-fA-F-]{24,}(?=/|$)"), "/:id")
            "${uri.host.orEmpty().lowercase()}$path"
        }.getOrElse { url.substringBefore('?').substringBefore('#').lowercase() }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it).trim().takeIf(String::isNotBlank) }
    }

    private fun JSONArray?.toElements(): List<SemanticElement> {
        if (this == null) return emptyList()
        val out = ArrayList<SemanticElement>()
        for (i in 0 until length()) {
            val e = optJSONObject(i) ?: continue
            out += SemanticElement(
                id = e.optString("id", "e$i"),
                tag = e.optString("tag"),
                role = e.optNullableString("role"),
                label = e.optString("label").trim(),
                href = e.optNullableString("href"),
                inputType = e.optNullableString("inputType"),
                selected = e.optBoolean("selected", false),
                disabled = e.optBoolean("disabled", false),
                nearbyText = e.optNullableString("nearbyText"),
                locatorHints = e.optJSONArray("locatorHints").toStringList(),
                currentValue = e.optNullableString("currentValue"),
                choices = e.optJSONArray("choices").toStringList()
            )
        }
        return out
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() && it != "null" }
    }
}

object PageFingerprint {
    fun compute(snapshot: PageSnapshot): String {
        val normalizedHeadings = snapshot.headings
            .map(::normalize)
            .filter(String::isNotBlank)
            .sorted()
            .take(20)
        val controls = snapshot.elements
            .map { "${it.tag}:${normalize(it.role.orEmpty())}:${normalize(it.label)}" }
            .filter { it.length > 3 }
            .distinct()
            .sorted()
            .take(100)
        val seed = buildString {
            append(snapshot.host.lowercase())
            append('|').append(snapshot.routeSignature.lowercase())
            append('|').append(snapshot.pageType.name)
            append('|').append(normalizedHeadings.joinToString(";"))
            append('|').append(controls.joinToString(";"))
        }
        val bytes = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(24)
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .replace(Regex("\\$?[0-9][0-9,]*(?:\\.[0-9]+)?"), "#")
        .replace(Regex("\\s+"), " ")
        .trim()
}
