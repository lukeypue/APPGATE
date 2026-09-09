package com.searchai.mobile

import java.net.URI

data class SearchSource(val id:String,val name:String,val searchUrl:String){ fun url(query:String)=searchUrl.replace("{q}",java.net.URLEncoder.encode(query,"UTF-8")) }
data class LearningRecord(val url:String,val details:String)

enum class UpdatePolicy(val label:String){ EVERY_LAUNCH("Every launch"), DAILY("Daily"), WEEKLY("Weekly"), MANUAL("Ask me first") }

object SourceRegistry {
 fun all()=listOf(
  SearchSource("general","General Web","https://www.google.com/search?q={q}"), SearchSource("ksl","KSL","https://classifieds.ksl.com/search/keyword/{q}"),
  SearchSource("facebook","Facebook Marketplace","https://www.facebook.com/marketplace/search/?query={q}"), SearchSource("autotrader","Autotrader","https://www.autotrader.com/cars-for-sale/all-cars?keywordPhrases={q}"),
  SearchSource("cargurus","CarGurus","https://www.cargurus.com/Cars/inventorylisting/viewDetailsFilterViewInventoryListing.action?zip=84067&keywords={q}"), SearchSource("zillow","Zillow","https://www.zillow.com/homes/{q}_rb/"),
  SearchSource("redfin","Redfin","https://www.redfin.com/search?q={q}"), SearchSource("offerup","OfferUp","https://offerup.com/search?q={q}"),
  SearchSource("craigslist","Craigslist","https://www.craigslist.org/search/sss?query={q}"), SearchSource("ebay","eBay","https://www.ebay.com/sch/i.html?_nkw={q}"),
  SearchSource("mercari","Mercari","https://www.mercari.com/search/?keyword={q}"), SearchSource("depop","Depop","https://www.depop.com/search/?q={q}"),
  SearchSource("linkedin","LinkedIn","https://www.linkedin.com/search/results/all/?keywords={q}"), SearchSource("nextdoor","Nextdoor","https://nextdoor.com/search/?query={q}"),
  SearchSource("stubhub","StubHub","https://www.stubhub.com/secure/search?q={q}"), SearchSource("seatgeek","SeatGeek","https://seatgeek.com/search?search={q}")
 )
}
object SearchPlanner {
 fun plan(query:String):List<SearchSource>{ val all=SourceRegistry.all(); val q=query.lowercase(); val named=all.drop(1).filter{q.contains(it.name.lowercase()) || q.contains(it.id)}; return if(named.isNotEmpty()) listOf(all.first())+named else listOf(all[0],all[1],all[3],all[8],all[9],all[7]) }
}
object LearningSanitizer {
 fun sanitize(url:String,details:String):LearningRecord { val cleanUrl=try{ val u=URI(url); URI(u.scheme,u.authority,u.path,null,null).toString() }catch(e:Exception){url.substringBefore('?')}; var d=details; listOf("cookie","token","session","password","authorization").forEach{ key-> d=d.replace(Regex("(?i)$key\\s*[=:]\\s*[^\\s]+"),"$key=[removed]") }; return LearningRecord(cleanUrl,d) }
}
object VerificationDetector { fun requiresHuman(text:String)=listOf("verify you are human","captcha","security check","are you a robot").any{text.lowercase().contains(it)} }
object Safety { fun isSensitiveAction(q:String)=listOf("submit payment","buy this","place bid","send message","delete account").any{q.lowercase().contains(it)} }
