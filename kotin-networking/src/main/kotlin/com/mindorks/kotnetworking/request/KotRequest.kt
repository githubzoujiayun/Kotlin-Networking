/*
 *    Copyright (C) 2017 MINDORKS NEXTGEN PRIVATE LIMITED
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.mindorks.kotnetworking.request

import com.mindorks.kotnetworking.common.*
import com.mindorks.kotnetworking.core.Core
import com.mindorks.kotnetworking.error.KotError
import com.mindorks.kotnetworking.internal.KotRequestQueue
import com.mindorks.kotnetworking.requestbuidler.GetRequestBuilder
import com.mindorks.kotnetworking.requestbuidler.PostRequestBuilder
import com.mindorks.kotnetworking.utils.KotUtils
import okhttp3.*
import okio.Okio
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Future


/**
 * Created by amitshekhar on 30/04/17.
 */
class KotRequest {

    //region Member Variables
    var method: Method
    var url: String
    var bodyParameterMap: MutableMap<String, String>? = null
    var urlEncodedFormBodyParameterMap: MutableMap<String, String>? = null
    var headersMap: MutableMap<String, String>
    val queryParameterMap: MutableMap<String, String>
    val pathParameterMap: MutableMap<String, String>
    var jsonObjectRequestCallback: ((response: JSONObject?, error: KotError?) -> Unit)? = null
    var jsonArrayRequestCallback: ((response: JSONArray?, error: KotError?) -> Unit)? = null
    var stringRequestCallback: ((response: String?, error: KotError?) -> Unit)? = null
    var requestType: RequestType
    var responseType: ResponseType? = null
    var priorityType: Priority? = null
    var cacheControl: CacheControl? = null
    var executor: Executor? = null
    var okHttpClient: OkHttpClient? = null
    var userAgent: String? = null
    var call: Call? = null
    var priority: Priority
    var sequenceNumber: Int = 0
    var isCancelled = false
    var isDelivered = false
    var future: Future<*>? = null
    private var applicationJsonString: String? = null
    private var stringBody: String? = null
    private var file: File? = null
    private var bytes: ByteArray? = null
    private var customMediaType: MediaType? = null
    private var JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8")
    private var MEDIA_TYPE_MARKDOWN = MediaType.parse("text/x-markdown; charset=utf-8")
    //endregion

    //region Constructors
    constructor(getRequestBuilder: GetRequestBuilder) {
        this.method = getRequestBuilder.method
        this.url = getRequestBuilder.url
        this.priority = getRequestBuilder.priority
        this.headersMap = getRequestBuilder.headersMap
        this.queryParameterMap = getRequestBuilder.queryParameterMap
        this.pathParameterMap = getRequestBuilder.pathParameterMap
        this.requestType = RequestType.SIMPLE
        this.priorityType = getRequestBuilder.priority
        this.cacheControl = getRequestBuilder.cacheControl
        this.executor = getRequestBuilder.executor
        this.okHttpClient = getRequestBuilder.okHttpClient
        this.userAgent = getRequestBuilder.userAgent
    }

    constructor(postRequestBuilder: PostRequestBuilder) {
        this.method = postRequestBuilder.method
        this.url = postRequestBuilder.url
        this.priority = postRequestBuilder.priority
        this.headersMap = postRequestBuilder.headersMap
        this.queryParameterMap = postRequestBuilder.queryParameterMap
        this.pathParameterMap = postRequestBuilder.pathParameterMap
        this.bodyParameterMap = postRequestBuilder.bodyParameterMap
        this.applicationJsonString = postRequestBuilder.applicationJsonString
        this.urlEncodedFormBodyParameterMap = postRequestBuilder.urlEncodedFormBodyParameterMap
        this.file = postRequestBuilder.file
        this.bytes = postRequestBuilder.bytes
        this.stringBody = postRequestBuilder.stringBody
        postRequestBuilder.customContentType?.let { mediaType -> this.customMediaType = MediaType.parse(mediaType) }
        this.requestType = RequestType.SIMPLE
        this.priorityType = postRequestBuilder.priority
        this.cacheControl = postRequestBuilder.cacheControl
        this.executor = postRequestBuilder.executor
        this.okHttpClient = postRequestBuilder.okHttpClient
        this.userAgent = postRequestBuilder.userAgent
    }
    //endregion

    //region Getters

    fun getAsJSONObject(handler: (result: JSONObject?, error: KotError?) -> Unit) {
        responseType = ResponseType.JSON_OBJECT
        jsonObjectRequestCallback = handler
        KotRequestQueue.instance.addRequest(this)
    }

    fun getAsString(handler: (result: String?, error: KotError?) -> Unit) {
        responseType = ResponseType.STRING
        stringRequestCallback = handler
        KotRequestQueue.instance.addRequest(this)
    }

    fun getAsJSONArray(handler: (result: JSONArray?, error: KotError?) -> Unit) {
        responseType = ResponseType.JSON_ARRAY
        jsonArrayRequestCallback = handler
        KotRequestQueue.instance.addRequest(this)
    }

    fun getFormattedUrl(): String {
        var tempUrl = url

        pathParameterMap.entries.forEach { entry -> tempUrl = tempUrl.replace("{${entry.key}}", entry.value) }

        val urlBuilder: HttpUrl.Builder = HttpUrl.parse(tempUrl).newBuilder()

        queryParameterMap.entries.forEach { entry -> urlBuilder.addQueryParameter(entry.key, entry.value) }

        return urlBuilder.build().toString()

    }

    fun getHeaders(): Headers {
        val builder: Headers.Builder = Headers.Builder()
        try {
            headersMap.entries.forEach { entry -> builder.add(entry.key, entry.value) }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return builder.build()
    }

    fun getRequestBody(): RequestBody {
        if (applicationJsonString != null) {
            if (customMediaType != null) {
                return RequestBody.create(customMediaType, applicationJsonString)
            }
            return RequestBody.create(JSON_MEDIA_TYPE, applicationJsonString)
        } else if (stringBody != null) {
            if (customMediaType != null) {
                return RequestBody.create(customMediaType, stringBody)
            }
            return RequestBody.create(MEDIA_TYPE_MARKDOWN, stringBody)
        } else if (file != null) {
            if (customMediaType != null) {
                return RequestBody.create(customMediaType, file)
            }
            return RequestBody.create(MEDIA_TYPE_MARKDOWN, file)
        } else if (bytes != null) {
            if (customMediaType != null) {
                return RequestBody.create(customMediaType, bytes)
            }
            return RequestBody.create(MEDIA_TYPE_MARKDOWN, bytes)
        } else {
            val builder: FormBody.Builder = FormBody.Builder()
            try {
                bodyParameterMap?.entries?.forEach { entry -> builder.add(entry.key, entry.value) }
                urlEncodedFormBodyParameterMap?.entries?.forEach { entry -> builder.addEncoded(entry.key, entry.value) }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            return builder.build()
        }
    }

    //endregion

    //region Delivery
    fun deliverError(kotError: KotError) {
        try {
            if (!isDelivered) {
                if (isCancelled) {
                    kotError.errorDetail = KotConstants.REQUEST_CANCELLED_ERROR
                    kotError.errorCode = 0
                }
                deliverErrorResponse(kotError)
            }
            isDelivered = true
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    private fun deliverSuccessResponse(kotResponse: KotResponse<*>) {
        jsonObjectRequestCallback?.let {
            jsonObjectRequestCallback!!.invoke(kotResponse.result as JSONObject?, null)
        }
        jsonArrayRequestCallback?.let {
            jsonArrayRequestCallback!!.invoke(kotResponse.result as JSONArray?, null)
        }
        stringRequestCallback?.let {
            stringRequestCallback!!.invoke(kotResponse.result as String?, null)
        }
    }

    private fun deliverErrorResponse(kotError: KotError) {
        jsonObjectRequestCallback?.let {
            jsonObjectRequestCallback!!.invoke(null, kotError)
        }
        jsonArrayRequestCallback?.let {
            jsonArrayRequestCallback!!.invoke(null, kotError)
        }
        stringRequestCallback?.let {
            stringRequestCallback!!.invoke(null, kotError)
        }
    }

    fun deliverOkHttpResponse(okHttpResponse: Response) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    fun deliverResponse(kotResponse: KotResponse<*>) {
        try {
            isDelivered = true
            if (!isCancelled) {
                if (executor != null) {
                    executor?.execute({ deliverSuccessResponse(kotResponse) })
                } else {
                    Core.instance.executorSupplier.forNetworkTasks().execute { deliverSuccessResponse(kotResponse) }
                }
            } else {
                val kotError: KotError = KotError()
                kotError.errorDetail = KotConstants.REQUEST_CANCELLED_ERROR
                kotError.errorCode = 0
                deliverErrorResponse(kotError)
                finish()
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
    //endregion

    //region Parsers
    fun parseNetworkError(kotError: KotError): KotError {
        try {
            kotError.response?.let {
                kotError.response!!.body()?.let {
                    kotError.response!!.body().source()?.let {
                        kotError.errorBody = Okio.buffer(kotError.response!!.body().source()).readUtf8()
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return kotError
    }

    fun parseResponse(response: Response): KotResponse<*>? {
        when (responseType) {

            ResponseType.JSON_ARRAY -> try {
                val json: JSONArray = JSONArray(Okio.buffer(response.body().source()).readUtf8())
                return KotResponse.success(json)
            } catch (e: Exception) {
                return KotResponse.failed(KotUtils.getErrorForParse(KotError(e)))
            }

            ResponseType.STRING -> try {
                return KotResponse.success(Okio.buffer(response.body().source()).readUtf8())
            } catch (e: Exception) {
                return KotResponse.failed(KotUtils.getErrorForParse(KotError(e)))
            }

            ResponseType.JSON_OBJECT -> try {
                val json: JSONObject = JSONObject(Okio.buffer(response.body().source()).readUtf8())
                return KotResponse.success(json)
            } catch (e: Exception) {
                return KotResponse.failed(KotUtils.getErrorForParse(KotError(e)))
            }

            ResponseType.OK_HTTP_RESPONSE -> {
            }

            ResponseType.PREFETCH -> {
            }

            ResponseType.PARSED -> {
            }

            else -> {
            }
        }

        return null
    }
    //endregion

    //region finisher
    fun finish() {
        KotRequestQueue.instance.finish(this)
    }
    //endregion
}