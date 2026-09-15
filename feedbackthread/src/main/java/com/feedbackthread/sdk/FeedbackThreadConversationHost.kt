package com.feedbackthread.sdk

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal val LocalFeedbackThreadConversations = staticCompositionLocalOf<FeedbackThreadConversations?> { null }

/** Keep at app root and pass the supplied secure client to all FeedbackThread screens. */
@Composable
public fun FeedbackThreadConversationHost(
    conversations: FeedbackThreadConversations,
    modifier: Modifier = Modifier,
    content: @Composable (FeedbackThreadClient) -> Unit,
) {
    val state by conversations.state.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(conversations, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { conversations.runLive() }
    }
    CompositionLocalProvider(LocalFeedbackThreadConversations provides conversations) {
        key(conversations) {
            Column(modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    if (state.ready) content(conversations.client)
                    else Column(Modifier.padding(24.dp)) {
                        CircularProgressIndicator()
                        Text(state.error ?: "Connecting to your feedback…")
                    }
                }
                state.inbox.firstOrNull { it.unreadCount > 0 }?.let { unread ->
                    TextButton(onClick = { conversations.open(FeedbackThreadConversationRoute(unread.feedbackId,unread.audience)) }, modifier=Modifier.fillMaxWidth()) {
                        Text("New message (${state.unreadCount})")
                    }
                }
            }
            state.route?.let { route ->
                Dialog(onDismissRequest={conversations.open(null)}, properties=DialogProperties(usePlatformDefaultWidth=false)) {
                    key(route) { Surface { FeedbackThreadConversationScreen(conversations,route,{conversations.open(null)}) } }
                }
            }
        }
    }
}

/** Opening history does not mark it read. Only messages displayed in the foreground do. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun FeedbackThreadConversationScreen(
    conversations: FeedbackThreadConversations,
    route: FeedbackThreadConversationRoute,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    key(conversations,route) { ConversationContent(conversations,route,onDismiss,modifier) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationContent(conversations: FeedbackThreadConversations,route: FeedbackThreadConversationRoute,onDismiss: () -> Unit,modifier: Modifier) {
    val state by conversations.state.collectAsState()
    val scope=rememberCoroutineScope()
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val list=rememberLazyListState()
    var history by remember { mutableStateOf<FeedbackThreadConversationHistory?>(null) }
    var earlier by remember { mutableStateOf(emptyList<FeedbackThreadConversationMessage>()) }
    var pages by remember { mutableStateOf(1) }
    var before by remember { mutableStateOf<Long?>(null) }
    var draft by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Pair<String,String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableStateOf(0) }
    var remove by remember { mutableStateOf<FeedbackThreadConversationMessage?>(null) }
    val messages=(earlier+(history?.messages ?: emptyList())).associateBy { it.id }.values.sortedBy { it.seq }
    LaunchedEffect(state.revision,revision) {
        try {
            val value=conversations.history(route)
            var cursor=value.nextBefore
            val older=mutableListOf<FeedbackThreadConversationMessage>()
            repeat(pages-1) {
                cursor?.let { next -> val page=conversations.history(route,next); older.addAll(page.messages); cursor=page.nextBefore }
            }
            history=value; earlier=older; before=cursor; error=null
        } catch(e: CancellationException) { throw e } catch(e: Exception) { history=null; earlier=emptyList(); pages=1; error="Could not load this conversation." }
    }
    LaunchedEffect(messages) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow { list.layoutInfo.visibleItemsInfo.mapNotNull { info -> messages.firstOrNull { it.id==info.key }?.seq }.maxOrNull() }
                .distinctUntilChanged().collect { seq ->
                    if(seq!=null) try { conversations.markRead(route,seq) } catch(e: CancellationException) { throw e } catch(_: Exception) { }
                }
        }
    }
    fun action(work: suspend () -> Unit) {
        if(busy) return
        busy=true; error=null
        scope.launch {
            try { work(); revision++; try { conversations.refresh() } catch(e: CancellationException) { throw e } catch(_: Exception) {} }
            catch(e: CancellationException) { throw e } catch(_: Exception) { error="Could not complete this action. Try again." }
            finally { busy=false }
        }
    }
    val disabled=route.audience=="public" && state.ready && !state.publicCommentsEnabled
    Scaffold(modifier=modifier.fillMaxSize().imePadding(),topBar={ TopAppBar(title={Text(if(route.audience=="private") "Replies" else "Comments")},navigationIcon={TextButton(onClick=onDismiss){Text("Back")}}) },bottomBar={
        if(!disabled) Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)) {
            OutlinedTextField(value=draft,onValueChange={ if(it.length<=4000) draft=it },label={Text(if(route.audience=="private") "Write a private reply" else "Write a public comment")},enabled=!busy,modifier=Modifier.fillMaxWidth(),maxLines=5)
            Button(enabled=!busy && history!=null && draft.isNotBlank(),onClick={
                val text=draft.trim(); if(pending?.first!=text) pending=text to UUID.randomUUID().toString()
                val id=pending!!.second
                action { conversations.send(route,text,id); draft=""; pending=null }
            },modifier=Modifier.fillMaxWidth()) { Text(if(busy) "Sending…" else if(route.audience=="private") "Send reply" else "Post comment") }
        }
    }) { padding ->
        if(disabled) Text("Public comments are disabled for this app.",Modifier.padding(padding).padding(20.dp))
        else LazyColumn(state=list,modifier=Modifier.padding(padding).fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {
                Text(if(route.audience=="private") "Only you and the app developer can see these replies." else "Everyone using this app can see these comments.")
                error?.let { Text(it,color=MaterialTheme.colorScheme.error); TextButton(onClick={revision++}){Text("Try again")} }
                history?.let { value -> TextButton(enabled=!busy,onClick={action {conversations.follow(route,!value.following)}}){Text(if(value.following) "Mute notifications" else "Notify me")} }
                if(before!=null) TextButton(enabled=!busy,onClick={val cursor=before; action {val value=conversations.history(route,cursor); pages++; earlier=value.messages+earlier; before=value.nextBefore}}){Text("Load earlier messages")}
                if(messages.isEmpty()) Text(if(history==null) "Loading…" else "No messages yet.")
            }
            items(messages,key={it.id}) { message ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(if(message.mine) "You" else if(message.authorRole=="developer") "Developer" else "App user",style=MaterialTheme.typography.titleSmall)
                    Text(if(message.deletedAt!=null) "Message removed" else message.body)
                    if(message.mine) Text(if((history?.otherReadSeq ?: 0)>=message.seq) "Read" else "Posted",style=MaterialTheme.typography.labelSmall)
                    if(message.mine && message.deletedAt==null) TextButton(enabled=!busy,onClick={remove=message}){Text("Remove message")}
                } }
            }
        }
    }
    remove?.let { message -> AlertDialog(onDismissRequest={remove=null},title={Text("Remove message?")},text={Text("The conversation will show that this message was removed.")},confirmButton={TextButton(onClick={remove=null; action {conversations.remove(route,message.id)}}){Text("Remove")}},dismissButton={TextButton(onClick={remove=null}){Text("Cancel")}}) }
}
