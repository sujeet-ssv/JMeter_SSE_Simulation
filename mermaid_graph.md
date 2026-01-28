```mermaid
graph TB
    
    subgraph Init["🎬 Start"]
        A[Initiate SSE Request] --> B[Start Timer<br/>startTime = now]
         C[Connection Established<br/>Waiting for Events ] 
    end
      
    subgraph EventLoop["🔄 Process Events"]
        F[📨 onEvent Handler Called] --> G[eventCount++<br/>Event Received]
        
        G --> H{'N' Events<br/>been Received?}
        H -->|Yes| I[⏱️ Capture Time Taken<br/>timeForN = now - startTime]
        H -->|No| J[Continue]
        
        I --> K[Append Event Data<br/>to responseData]
        J --> K
        
        K --> L{More Events<br/>Coming?}
        L -->|Yes| F
        L -->|No| M[All Events Received]
    end
    
    
    subgraph ClosePhase["✅ Completion - onClosed"]
        M -->|onClose handler|O[totalTime = now - startTime]
    
    end
    
    subgraph FinalizePhase["📊 Finalization"]
        Q[Set responseData<br/>to SampleResult ]
        Q -->S[SSE Request Sampler Finished]
    end
   
    subgraph ErrorPhase["❌ Error"]
        M -->|onFailure handler| U[Update responseData with Error Details]
       
    end
   
    B -->|📡 onOpen handler| C
    C --> F
    O --> Q
    U --> Q
    
    style B fill:#4caf50,stroke:#1b5e20,stroke-width:3px,color:#fff
    style I fill:#ffd54f,stroke:#f57f17,stroke-width:4px
    style K fill:#81c784,stroke:#2e7d32,stroke-width:3px,color:#fff
    style Q fill:#ba68c8,stroke:#6a1b9a,stroke-width:4px,color:#fff
    style S fill:#a5d6a7,stroke:#1b5e20,stroke-width:4px

