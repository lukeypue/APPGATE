package com.searchai.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){ super.onCreate(savedInstanceState); setContent { MaterialTheme { SearchAiScreen { source,q -> startActivity(Intent(this,BrowserActivity::class.java).putExtra("url",source.url(q)).putExtra("source",source.name)) } } } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SearchAiScreen(open:(SearchSource,String)->Unit){
 var query by remember{ mutableStateOf("") }
 var searched by remember{ mutableStateOf(false) }
 var showSettings by remember{mutableStateOf(false)}
 val sensitive = Safety.isSensitiveAction(query)
 val sources=if(searched) SearchPlanner.plan(query) else emptyList()
 Scaffold(topBar={TopAppBar(title={Text("SearchAI ✨")},actions={TextButton(onClick={showSettings=true}){Text("Updates")}})}){ pad->
  LazyColumn(Modifier.padding(pad).padding(20.dp).fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)){
   item{Text("Search more of the web. Learn the tricky parts. Keep getting better.",style=MaterialTheme.typography.bodyLarge)}
   item{OutlinedTextField(query,{query=it; searched=false},Modifier.fillMaxWidth(),label={Text("What are you looking for?")},minLines=2)}
   item{Button(onClick={searched=true},enabled=query.isNotBlank() && !sensitive,modifier=Modifier.fillMaxWidth()){Text("Search Everywhere")}}
   if(sensitive) item{Text("SearchAI can help you research and compare, but it won't make purchases, payments, bids, or messages for you.")}
   items(sources){s-> Card(onClick={open(s,query)},Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(s.name,style=MaterialTheme.typography.titleMedium);Text("Ready to search • Tap to open")}}}
   if(searched) item{Text("If a website changes, SearchAI can save sanitized structural notes to improve future site updates. Human verification always stays human.",style=MaterialTheme.typography.bodySmall)}
  }
 }
 if(showSettings) UpdateDialog(onDismiss={showSettings=false})
}

@Composable fun UpdateDialog(onDismiss:()->Unit){ AlertDialog(onDismissRequest=onDismiss,confirmButton={TextButton(onClick=onDismiss){Text("Done")}},title={Text("Site knowledge updates")},text={Column{Text("Websites change constantly. Choose how often SearchAI checks its site knowledge.");Spacer(Modifier.height(8.dp));UpdatePolicy.entries.forEach{Text("• ${it.label}",Modifier.padding(vertical=4.dp))}}}) }
