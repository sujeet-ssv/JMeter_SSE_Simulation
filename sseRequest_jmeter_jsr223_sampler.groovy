import okhttp3.*     // make sure okhttp-3.10.0.jar file present in dir "jmeter\lib"  , if not download and copy it. 
import okhttp3.sse.* // jar file needs to be copied  under  “jmeter\lib\ext”  https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp-sse/3.11.0/okhttp-sse-3.11.0.jar
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/* 
  Boiler Template
  ============================================================================
 	SSE (Server-Sent Events) Performance Testing Script for JMeter
  ============================================================================
  
 PURPOSE:
    Measures user-perceived performance of streaming chatbot/AI responses.
    Captures Time to First Token (TTFT) and First N Events timing - metrics   that matter to end users, not just total response time.
  
 USAGE :
 	1. Replace Existing HTTP Request Sampler
		Replace the currently used HTTP Request Sampler in the JMeter script with the provided JSR223 Sampler (this one).
		This JSR223 Sampler will fully handle request construction, payload submission, and response capture.
		
	2. Configure Required Cookies
		Update the variable cookieData (initialized on line #40) with all cookies required for your request.
		Ensure that:
		The same cookies currently being passed via the HTTP Request Sampler are added here.
		Cookie formatting follows the standard "key=value; key2=value2" pattern.

	3. Add or Remove HTTP Headers
		Modify the request headers defined in the script on line #90.
		Add any headers currently configured in the HTTP Request Sampler.
		Remove or update any headers no longer applicable.
		Ensure required headers (such as Content-Type, Accept, Authorization, etc.) are correctly set.

	4. Configure Global Parameter: eventsToTrack
		Provide a valid value for the global parameter eventsToTrack.
		If no value is supplied, the default value will be 10.
		Ensure this parameter aligns with your test scenario expectations, you can find out this value from chrome developer console refer to response body eventstream.

 	5. View Results in Standard JMeter Format
		The sampler writes the request and response data to the SampleResult JMeter variable, ensuring:
		You can inspect request/response payloads in View Results Tree.
		The output appears identical to that of a standard HTTP Request Sampler.
		This significantly improves troubleshooting analysis.

 DEPENDENCIES:
   - OkHttp 3.10.0 (HTTP client with HTTP/2 support)  Should be present natively in your JMeter installer (okhttp-3.10.0.jar) under \JMeter\lib dir
   - okhttp-sse 3.11.0 (Server-Sent Events support)   https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp-sse/3.11.0/okhttp-sse-3.11.0.jar copy under \JMeter\lib\ext

 OUTPUT VARIABLES (Available for Assertions/Extractors):
    - ${sse_event_count}         		= Total number of events received
    - ${partial_time_for_firstToken} 	= Time for first N events (ms) - USER PERCEIVED
    - ${sse_total_time}          		= Total time for all events (ms)

 CUSTOM RESPONSE HEADERS (Visible in View Results Tree):
    - X-SSE-Event-Count          = Total events
    - X-SSE-Total-Time           = Total duration (ms)
    - X-SSE-First-N-Events-Time  = First N events duration (ms)

 AUTHOR: Sujeet Velapure
  DATE: January 2026
  VERSION: 2.0

*/



//For automatic cookie retrieval to work ,  in jmeter.properties or user.properties property ' CookieManager.save.cookies' shoudl be set to true e.g. CookieManager.save.cookies=true 
// Authentication cookie (update with valid session tokens)
def cookieData ="route_token="+vars.get("COOKIE_route_token")+";saml_token_id="+vars.get("COOKIE_saml_token_id")+";_cacheId_token="+vars.get("COOKIE__cacheId_token")

log.info("Cookies retrieved to: ${cookieData} "); 
// SampleResult, vars, and log are automatically available in JSR223 Sampler

// SSE endpoint URL (chatbot/AI streaming endpoint)
def url = "https://${URL}/api/chatbot/application/chatbot/message-stream"

// Get number of events to track from JMeter variable (default: 10)
// This represents ~N words/tokens that user sees = perceived responsiveness
def eventsToTrack = (vars.get("eventsToTrack") ?: "10").toInteger()

// ============================================================================
// STATE MANAGEMENT
// ============================================================================
// Synchronization: Wait for async SSE stream to complete
def latch = new CountDownLatch(1)   // Latch with count=1, will be released in onClosed/onFailure

AtomicInteger eventCount = new AtomicInteger(0)	// Event counter:  for total events received

StringBuilder responseData = new StringBuilder() // Response data: Accumulates all events and metrics for JMeter display
def connectionOpened = false  // Has onOpen() been called?
def responseCode = 0 		// HTTP response code (e.g., 200)
def responseMessage = ""		// HTTP response message (e.g., "OK")

// Timing variables
long streamStartTime = 0		// Time from start When the user sent request
long firstNEventsTime = 0	// Time from start until Nth event received (USER PERCEIVED)
long totalEventsTime = 0		// Time from start until stream closes (total duration)
boolean firstNEventsCaptured = false	// Flag: Have we captured first N events timing?

// SampleResult is automatically available in JSR223 Sampler
// Set sample label (appears in JMeter results)
// SampleResult - JMeter's result object for storing metrics, response data, headers
SampleResult.setSampleLabel("SSE Streaming Protocol Request")

// HTTP CLIENT CONFIGURATION
OkHttpClient client;
try {
    client = new OkHttpClient.Builder()   
        .readTimeout(30, TimeUnit.SECONDS)  // 30 second timeout for JMeter
        .build()

    // Create POST body with JSON payload
    def jsonPayload = '''{
					    "action": "eMessage",
					    "chatID": "${chatID}",
					    "chatbotId": "${chatbotId}",
					    "queryText": "${query_text}",
					    "timeZone": "Asia/Calcutta"
					}'''
					
// Create request body with proper content type
    MediaType mediaType = MediaType.parse("application/json; charset=utf-8")
// Note: OkHttp 3.x signature is .create(MediaType, String)
//       OkHttp 4.x signature is .create(String, MediaType)
    RequestBody requestBody = RequestBody.create(mediaType, jsonPayload)
// Build POST request with all headers to emulate browser behavior
    Request request = new Request.Builder()
        .url(url)
        .post(requestBody)
        .header("Accept", "application/json, text/plain, */*")
        .header("Accept-Encoding", "gzip, deflate, br, zstd")
        .header("Accept-Language", "en-US,en;q=0.9")
        .header("Cache-Control", "no-cache")
        .header("Connection", "keep-alive")
        .header("Content-Type", "application/json")
        .header("Cookie", cookieData)
        .header("Host", "${URL}")
        .header("Origin", "https://${URL}")
        .header("Pragma", "no-cache")
        .header("Referer", "https://${URL}/chatbot/index.html")
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
        .header("X-Requested-By", "XMLHttpRequest")
        .build()

// Save request details for View Results Tree display
    SampleResult.setSamplerData("POST: ${url}\n\nRequest Payload:\n${jsonPayload}\n\nCookie: ${cookieData}")
    SampleResult.setRequestHeaders(request.headers().toString())

// EventSource.Factory creates SSE connections using OkHttp client
    EventSource.Factory factory = EventSources.createFactory(client)

    log.info("Connecting to: ${url}")

// ========================================================================
// EVENT SOURCE LISTENER - Core SSE Event Handler
// ========================================================================
// Implements callbacks for SSE lifecycle: onOpen, onEvent, onClosed, onFailure
// These callbacks run asynchronously as events arrive from the server
    EventSourceListener listener = new EventSourceListener() {

    	  // ====================================================================
        // CALLBACK 1: onOpen - Connection Established
        // ====================================================================
        // Called when: HTTP connection successful, headers received, stream ready
        // Timing: Occurs after DNS + TCP + TLS handshake
        @Override
        void onOpen(EventSource eventSource, Response response) {
            connectionOpened = true		// Mark connection as successful
            responseCode = response.code()
            responseMessage = response.message()
            
           // Capture all response headers from server
           // These headers will be added to SampleResult to make it visible in JMeter's View Results Tree
            StringBuilder headersString = new StringBuilder()
            response.headers().names().each { name ->
                response.headers(name).each { value ->
                    headersString.append("${name}: ${value}\n")
                }
            }
            SampleResult.setResponseHeaders(headersString.toString()) 
            // Build response data section showing connection info
            responseData.append("=== Connection Opened ===\n")
            responseData.append("Response Code: ${response.code()}\n")
            responseData.append("Response protocol: ${response.protocol()}\n")
            responseData.append("Response Message: ${response.message()}\n")
            responseData.append("Content-Type: ${response.header('Content-Type')}\n")
            responseData.append("=== Started Receiving Streaming Response ===\n")
            
            log.info("Connection opened: ${response.code()}")
        }

   	   // ====================================================================
        // CALLBACK 2: onEvent - Each SSE Event Arrives
        // ====================================================================
        // Called when: Server sends an event (typically few 100 tokens/words)
        // Parameters:
        //   - id: Event ID (optional, often null)
        //   - type: Event type (default: "message")
        //   - data: Event payload (JSON or text)
        @Override
        void onEvent(EventSource eventSource, String id, String type, String data) {
            int currentCount = eventCount.incrementAndGet()
            def eventType = type ?: "message"

            
            // ----------------------------------------------------------------
            // TIMING CAPTURE: First N Events (USER-PERCEIVED PERFORMANCE)
            // ----------------------------------------------------------------
            // When Nth event arrives, user has seen ~N words - perceived as responsive
            // This is MORE IMPORTANT than total time for SLA measurement
            if (currentCount == eventsToTrack && !firstNEventsCaptured ) {
                firstNEventsTime = System.currentTimeMillis() - streamStartTime
                firstNEventsCaptured = true
                log.info("First ${eventsToTrack} events received in: ${firstNEventsTime} ms")  
              //  log.info("Event #${currentCount} \nreceived: ${data}")
                
            }
            
            
            responseData.append("\n--- Event #${currentCount} ---\n")
            responseData.append("Type: ${eventType}   Length: ${data.length()} characters\n")
            responseData.append("Data: " + data)  // APPEND EVENT TO RESPONSE DATA 
            
            log.info("Event #${currentCount} received: ${data.length()} chars")
        }

	      // ====================================================================
        // CALLBACK 3: onClosed - Stream Completed Successfully
        // ====================================================================
        // Called when: Server gracefully closes the SSE stream (all events sent)
        // This is the NORMAL completion path - all data received
        @Override
        void onClosed(EventSource eventSource) {
            totalEventsTime = System.currentTimeMillis() - streamStartTime

            // BUILD FINAL RESPONSE DATA - Summary Section
            responseData.append("\n=== Streaming data finished , Connection Closed ===\n")
          
            responseData.append("Total time for all ${eventCount.get()} events: ${totalEventsTime} ms\n")
            
            log.info("Connection closed. Events: ${eventCount.get()}, Total time: ${totalEventsTime}ms")
            // Release the latch - allows main thread to continue and finish the sampler
            latch.countDown()
        }

	      // ====================================================================
        // CALLBACK 4: onFailure - Error or Timeout Occurred
        // ====================================================================
        // Called when: Network error, timeout, server error, or manual cancel
        // Parameters:
        //   - t: Throwable/Exception that caused the failure
        //   - response: Response object (may be null if connection failed)
        @Override
        void onFailure(EventSource eventSource, Throwable t, Response response) {
        	// "CANCEL" message indicates intentional stream cancellation (not an error)
            if (!t?.message?.contains("CANCEL")) {
                responseData.append("\n=== Error Occurred ===\n")
                responseData.append("Error Type: ${t?.getClass()?.name}\n")
                responseData.append("Error Message: ${t?.message}\n")
                // If we have a response object, capture its details
                if (response) {
                    responseCode = response.code()
                    responseData.append("Response Code: ${response.code()}\n")
                    
                    // Capture response headers even on failure
                    StringBuilder headersString = new StringBuilder()
                    response.headers().names().each { name ->
                        response.headers(name).each { value ->
                            headersString.append("${name}: ${value}\n")
                        }
                    }
                    SampleResult.setResponseHeaders(headersString.toString())
                    responseData.append("\nResponse Headers:\n${headersString}\n")
                }
                
                log.error("Error: ${t?.message}", t)
            }
            // Even on failure, calculate timing up to the failure point
            totalEventsTime = System.currentTimeMillis() - streamStartTime
            
            responseData.append("\n=== Summary ===\n")
            responseData.append("Total Events Received: ${eventCount.get()}\n")
     	// Timing metrics (up to failure point)
            responseData.append("\n=== Timing Metrics ===\n")
            if (firstNEventsCaptured) {
                responseData.append("Time for first ${eventsToTrack} events: ${firstNEventsTime} ms\n")
            } else if (eventCount.get() > 0) {
                responseData.append("Only ${eventCount.get()} event(s) received (less than ${eventsToTrack})\n")
            }
            responseData.append("Total time for all ${eventCount.get()} events: ${totalEventsTime} ms\n")
            // Release the latch - allows main thread to continue
            latch.countDown()
        }
    } // End of EventSourceListener

    // ========================================================================
    // START THE SSE STREAM
    // ========================================================================
    // Start the stream and capture exact start time
    streamStartTime = System.currentTimeMillis()
    // Create EventSource connection - this initiates the HTTP POST and opens stream
    // The listener callbacks (onOpen, onEvent, etc.) will be called asynchronously
    EventSource eventSource = factory.newEventSource(request, listener)

    // ========================================================================
    // WAIT FOR STREAM COMPLETION
    // ========================================================================
    // Main thread waits here while events are processed in background
    // Latch will be released by onClosed() or onFailure() callback
    // Timeout: 120 seconds (adjust based on expected response times)
    boolean completed = latch.await(120, TimeUnit.SECONDS)
    
    if (!completed) {
    	// Timeout reached without onClosed/onFailure being called
        responseData.append("\n=== Timeout Reached ===\n")
        responseData.append("Closing connection after 120 seconds\n")
        eventSource.cancel() // Forcefully cancel the stream
        log.info("Timeout reached, closing connection")
    }

    // POPULATE JMETER SAMPLE RESULT - View Results Tree Display
    // Set the response data (Text content visible in Response Data tab)
    SampleResult.setResponseData(responseData.toString(), "UTF-8")
    
    // Set response code and message
    SampleResult.setResponseCode(connectionOpened ? responseCode.toString() : "Timeout")
    SampleResult.setResponseMessage(connectionOpened ? responseMessage : "Connection Failed")

    // ADD CUSTOM RESPONSE HEADERS (Metrics visible in Response Headers tab)
    // These custom headers make metrics visible in JMeter's View Results Tree
    // and can be used by extractors/post-processors
    def existingHeaders = SampleResult.getResponseHeaders() ?: ""
    StringBuilder timingHeaders = new StringBuilder(existingHeaders)
    if (!existingHeaders.isEmpty()) {
        timingHeaders.append("\n")
    }
    // Event count header
    timingHeaders.append("X-SSE-Event-Count: ${eventCount.get()}\n")
    // Total time header (complete streaming duration)
    timingHeaders.append("X-SSE-Total-Time: ${totalEventsTime}\n")
    // First N events time header (USER-PERCEIVED performance metric)
    if (firstNEventsCaptured) {
        timingHeaders.append("X-SSE-First-${eventsToTrack}-Events-Time: ${firstNEventsTime}\n")
        vars.put("partial_time_for_firstToken","${firstNEventsTime}")
    } else {
        timingHeaders.append("X-SSE-First-${eventsToTrack}-Events-Time: N/A (only ${eventCount.get()} events received)\n")
        vars.put("partial_time_for_firstToken","N/A")
    }
    SampleResult.setResponseHeaders(timingHeaders.toString())
    
    
    // Set content type
    SampleResult.setContentType("text/plain")
    
    // Mark as successful if we received events
    if (eventCount.get() > 0) {
        SampleResult.setSuccessful(true)	// Mark sampler as successful
        SampleResult.setResponseOK()		// Set response as OK
    } else if (connectionOpened) {
        SampleResult.setSuccessful(true)  // Connection worked but no events
        SampleResult.setResponseMessage("Connected but no events received")
    } else {
        SampleResult.setSuccessful(false)  // Connection failed
    }
    
} catch (Exception e) {
    SampleResult.setResponseCode("500")
    SampleResult.setResponseMessage("Exception: ${e.message}")
    SampleResult.setResponseData("Error: ${e.message}\n\nStack Trace:\n${e.getStackTrace().join('\n')}", "UTF-8")
    SampleResult.setSuccessful(false)
    log.error("Exception in SSE request", e)
} finally {
  
    // Cleanup Shutdown OkHttp's thread pools and close connections
    client.dispatcher().executorService().shutdown()
    client.connectionPool().evictAll()
    log.info("Cleaning connections..")

}

// Store metrics in JMeter variables for assertions
vars.put("sse_event_count", eventCount.get().toString())
vars.put("sse_total_time", totalEventsTime.toString())

