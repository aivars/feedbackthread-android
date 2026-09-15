package com.feedbackthread.sdk

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class FeedbackThreadConversationsTest {
    private val session = """{"customerId":"guest","externalUserId":"ft-guest:guest","token":"${"a".repeat(72)}"}"""
    private val route = FeedbackThreadConversationRoute("FDBK-test01","private")
    private class Store : FeedbackThreadConversationStore {
        val values = mutableMapOf<String,String>()
        override fun load(key:String)=values[key]
        override fun save(key:String,value:String) { values[key]=value }
        override fun remove(key:String) { values.remove(key) }
    }
    private class Connection(url:URL, private val response:String):HttpURLConnection(url) {
        val output=ByteArrayOutputStream()
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy()=false
        override fun getOutputStream()=output
        override fun getResponseCode()=200
        override fun getInputStream()=ByteArrayInputStream(response.toByteArray())
    }
    @Test fun concurrentPreparationPersistsOneSession():Unit=runBlocking {
        val store=Store(); var creates=0
        val factory:(URL)->HttpURLConnection={ url -> creates++; Connection(url,session) }
        val config=FeedbackThreadConfiguration(projectKey="test")
        val manager=FeedbackThreadConversations(config,store,connectionFactory=factory)
        coroutineScope { List(5) { async { manager.prepare() } }.awaitAll() }
        FeedbackThreadConversations(config,store,connectionFactory=factory).prepare()
        assertEquals(1,creates); assertEquals(1,store.values.size)
        FeedbackThreadConversations(config,store,"other-account",factory).prepare()
        assertEquals(2,creates)
    }
    @Test fun historyUsesPrivateCredentialWithoutMarkingRead():Unit=runBlocking {
        val paths=mutableListOf<String>()
        val manager=FeedbackThreadConversations(FeedbackThreadConfiguration(projectKey="test"),Store(),connectionFactory={url->
            paths.add(url.path)
            Connection(url,if(url.path.endsWith("/session")) session else """{"thread":{"id":"thread","audience":"private","status":"waiting"},"messages":[],"unreadCount":1,"following":true,"hasMore":false}""")
        })
        assertEquals(1,manager.history(route).unreadCount)
        assertEquals(listOf("/v1/projects/test/chat/session","/v1/projects/test/chat/threads/FDBK-test01/private"),paths)
    }
    @Test fun logoutRevokesAndPermanentlyClosesManager():Unit=runBlocking {
        val store=Store(); val connections=mutableListOf<Connection>()
        val manager=FeedbackThreadConversations(FeedbackThreadConfiguration(projectKey="test"),store,connectionFactory={url->Connection(url,session).also{connections.add(it)}})
        manager.prepare(); manager.logout()
        assertEquals("DELETE",connections.last().requestMethod)
        assertEquals("a".repeat(72),connections.last().getRequestProperty("X-FeedbackThread-Customer"))
        assertTrue(store.values.isEmpty())
        try { manager.prepare(); fail("A logged-out manager must not create a new identity") } catch(_:IllegalStateException) {}
        manager.open(route); assertNull(manager.state.value.route)
    }
    @Test fun explicitRetryKeepsTheSameId():Unit=runBlocking {
        val messages=mutableListOf<Connection>()
        val manager=FeedbackThreadConversations(FeedbackThreadConfiguration(projectKey="test"),Store(),connectionFactory={url->Connection(url,if(url.path.endsWith("/session")) session else "{}").also { if(url.path.endsWith("/messages")) messages.add(it) }})
        manager.send(route,"Reply","stable-id"); manager.send(route,"Reply","stable-id")
        assertEquals(messages.first().output.toString(),messages.last().output.toString())
        assertTrue(messages.first().output.toString().contains("stable-id"))
    }
}
