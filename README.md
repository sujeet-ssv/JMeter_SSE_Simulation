# JMeter_SSE_Simulation - 🚀 Measuring What Users Actually Experience: 
JMeter Support for Server-Sent Events (SSE) Simulation for Modern Chat Applications

## Problem Statement
In today's AI-driven world, chatbots and conversational AI applications have become so important. These applications—like ChatGPT, customer support bots, and enterprise virtual assistants—use **Server-Sent Events (SSE)** to stream responses in real-time, creating that familiar "typing effect" users love.

But here's the challenge in Simulation:

### **Traditional JMeter HTTP samplers measure the WRONG metric!** ⚠️

When you test an SSE endpoint with standard HTTP Request samplers, you get:
- ✅ Total Response Time: 11,500ms
- ❌ But users saw the first response in just 3500ms!

**The Reality:**
- 🎯 **User-Perceived Performance** = Time to First Token (TTFT) or First N Words
- 📉 **JMeter Measures** = Total time until entire response completes
- 🔄 **The Gap** = Total response time grows linearly with response length, masking actual latency

### Why This Matters:
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
**The problem?** Traditional tools measure streaming completion, not streaming START—the metric users care about.

### The Solution: Custom SSE Simulation (Groovy code) using JSR223 Sampler in JMeter
#### How It Works in Your Script:
1.	Main thread starts → Creates CountDownLatch(1)
2.	Background thread starts → Opens SSE connection, receives events
3.	Main thread waits → latch.await() blocks the main thread
4.	Background thread receives data → Processes events in onEvent()
5.	Connection closes → onClosed() or onFailure() calls latch.countDown()
6.	Main thread resumes → Script can now exit gracefully

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
