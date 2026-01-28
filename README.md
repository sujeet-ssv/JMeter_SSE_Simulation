# JMeter SSE Simulation - 🚀 Measuring What Users Actually Feel
JMeter Support for Server-Sent Events (SSE) - Rethinking Performance simulation for SSE Based Chat Application

## 🧩Problem Statement
Modern chat applications no longer follow traditional request‑response patterns. In today’s AI‑driven world—where chatbots, virtual assistants, and real‑time dashboards dominate—the perception of speed is shaped not by when the entire response arrives, but by how quickly the first few words appear.

Technologies like Server‑Sent Events (SSE) enable this instant, streaming interaction, powering the smooth “typing effect” we’ve all come to expect from systems like ChatGPT, customer support bots, and enterprise AI assistants.

**More on SSE Protocol-**
Server-Sent Events (SSE) is a lightweight, unidirectional HTTP-based protocol for streaming real-time data from server to client, ideal for live updates like notifications, dashboards, or stock tickers. It uses a persistent connection with text/event-stream and automatic reconnection.

But here's the challenge :

**Traditional JMeter HTTP samplers measure the WRONG metric!** ⚠️

e.g. 

When you test an SSE endpoint with standard HTTP Request samplers, you get:
- ✅ Total Response Time: 11,500ms   (Total response time (request start → last byte received))
- ❌ But users saw the first response in just 3500ms..

**The Reality:**
- 🎯 **User-Perceived Performance** = Time to First Token (TTFT) or First few Words
- 📉 **JMeter Measures** = Total time until entire response completes ( typical HTTP request lifecycle)
- 🔄 **The Gap** = Total response time grows linearly with response length, masking actual latency . Longer answers naturally take longer to complete

### 🚨Why This Matters:
```
Scenario: User asks "What are dental benefits?"

Traditional JMeter View:
├─ Response Time: 11,500ms ❌ (Fails SLA of <5s)
└─ Verdict: SLOW

User's Actual Experience:
├─ First word appeared: 2750ms ✅ (Excellent!)
├─ First 20 words: 3,200ms ✅ (Very responsive)
└─ Complete answer: 11,500ms (User already reading)
```
**The problem?** Traditional tools measure streaming completion, not streaming START— the metric users care about.

As application architecture evolves, our performance testing philosophy must evolve with it. Evaluating streaming systems by full response time is like rating service by the length of the entire experience instead of how quickly it begins — the first touchpoint shapes user perception. By aligning performance testing with human perception, we:
-  Build better UX driven systems
-  Make smarter architectural decisions
-  Avoid optimizing the wrong bottlenecks

### 💡What Really Needs to be Measured:
For chat applications and AI assistants, the most important metric is:
Time to First Response (TTFR) (a.k.a. Time to First Token)

**This metric answers one critical question: ** How long does the user wait before they feel the system has responded?

### ✅The Solution: Programmatic SSE Simulation (Groovy code) using JSR223 Sampler in JMeter
#### How It Works in Your Script:
1. Initiates an SSE request to the chat backend and start a timer
2.	In JSR223 Sampler,Background thread starts → Creates CountDownLatch(1)
3.	Background thread starts → Opens SSE connection, start receiving events
4.	Main thread waits → latch.await() blocks the main thread
5.	Background thread receives data → Processes events in onEvent()
6.	Background thread continues and captures time taken by first 'N' events and 'total' events.
7.	Connection closes → onClosed() or onFailure() calls latch.countDown()
8.	Main thread resumes → Script can now exit gracefully and continue with rest of the flow.

#### Visual Flow:
  ```
  **Main Thread **                **Background Thread (SSE)**
   -----------                      -----------------------
  Start script
  Create latch(1)
  Start EventSource -----------> Opens connection
                                 Receives event 1
  latch.await [WAITING]          Receives event 2 
   (timeout 120 sec)             Receives event N  ( custom logic to capture time taken for 'N' events which is Perceived response time by user)
         |                              |
         |                              |
         |                       Receives event 1500 
         |                       Connection closes
         |                       latch.countDown() -----> Releases main thread
  Cleanup & exit
  ```

#### JMeter Integration
1. Runs as **JSR223 Sampler** in JMeter test plans
2. Populates **SampleResult** for View Results Tree. Identical to what you see for typical HTTP Request Sampler.
3. Stores metrics as **JMeter variables** for assertions and reporting
4. Custom **Response Headers** with all timing metrics
5. Detects HTTP/1.1 vs HTTP/2 automatically
6. Cookie/authentication support within code
7. Transaction Controller with a dummy sampler has been intentionally included to simulate the response time for ‘N’ events. This is done purely for reporting purposes. Refer jmx jmeter script.

### 💬 Closing Thoughts
This small shift — from **“request completed” to “response started”** — made our performance analysis far more accurate and meaningful.

If you’re building or testing:
- SSE based APIs
- AI assistants
- Streaming chat applications
  
…it might be time to rethink what “fast” really means.

