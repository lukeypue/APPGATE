package com.appgate.tv.sitebrain

object PopupDismissal {
    private val dismissWords = listOf("close", "dismiss", "no thanks", "not now", "maybe later", "skip")
    private val blockedWords = listOf(
        "accept", "agree", "allow", "continue", "subscribe", "sign in", "log in",
        "buy", "checkout", "purchase", "pay", "message", "contact", "submit"
    )

    fun isSafeDismissLabel(label: String): Boolean {
        val text = label.lowercase().replace(Regex("\\s+"), " ").trim()
        if (text in setOf("x", "×", "✕", "✖")) return true
        if (text.contains("skip to main content") || text.contains("skip navigation") || text.contains("skip to content")) return false
        if (blockedWords.any(text::contains)) return false
        return dismissWords.any(text::contains)
    }

    fun javascript(): String = """
        (function(){
          function clean(v){ return (v||'').replace(/\s+/g,' ').trim(); }
          function visible(el){
            if(!el) return false;
            var s=getComputedStyle(el), r=el.getBoundingClientRect();
            return s.display!=='none' && s.visibility!=='hidden' && r.width>0 && r.height>0;
          }
          function label(el){
            return clean(el.getAttribute('aria-label')||el.getAttribute('title')||el.innerText||el.textContent||'');
          }
          function overlayAncestor(el){
            var n=el;
            for(var i=0;i<7 && n;i++,n=n.parentElement){
              var role=(n.getAttribute&&n.getAttribute('role')||'').toLowerCase();
              var s=getComputedStyle(n), r=n.getBoundingClientRect();
              var z=parseInt(s.zIndex||'0',10)||0;
              var big=r.width*r.height > window.innerWidth*window.innerHeight*0.12;
              if(role==='dialog' || role==='alertdialog' || ((s.position==='fixed'||s.position==='sticky') && big && z>=10)) return true;
            }
            return false;
          }
          function safeText(t){
            t=clean(t).toLowerCase();
            if(['x','×','✕','✖'].indexOf(t)>=0) return true;
            if(/skip to main content|skip navigation|skip to content/.test(t)) return false;
            if(/accept|agree|allow|continue|subscribe|sign in|log in|buy|checkout|purchase|pay|message|contact|submit/.test(t)) return false;
            return /close|dismiss|no thanks|not now|maybe later|skip/.test(t);
          }
          var nodes=Array.from(document.querySelectorAll('button,[role="button"],[aria-label],a'));
          for(var i=0;i<nodes.length;i++){
            var el=nodes[i];
            if(!visible(el)) continue;
            var t=label(el);
            if(!safeText(t) || !overlayAncestor(el)) continue;
            el.click();
            return 'DISMISSED:'+t.slice(0,100);
          }
          return 'NONE';
        })();
    """.trimIndent()
}
