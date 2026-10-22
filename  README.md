--Curl Out put--

1. {"id":83,"name":"tomcat-handler-9","virtual":true,"state":"RUNNABLE","description":"VirtualThread[#83,tomcat-handler-9]/runnable@ForkJoinPool-1-worker-5"}
--Curl Out put--

--Vragen--
1. Wat is het verschil tussen een platform thread en een virtual thread, qua geheugen en wie ze beheert?

Platform thread vs. virtual thread

┌─────────────────────────────────────┬────────────────────────────────────────────────┬───────────────────────────────────────────────────────────────────────┐                                        
│                                     │                Platform thread                 │                            Virtual thread                             │                                        
├─────────────────────────────────────┼────────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────────┤                                        
│ Wat is het?                         │ Een dun laagje om een echte OS-thread (1-op-1) │ Een Java-object dat de JVM beheert                                    │                                        
├─────────────────────────────────────┼────────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────────┤                                        
│ Wie plant het in?                   │ Het besturingssysteem                          │ De JVM                                                                │                                        
├─────────────────────────────────────┼────────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────────┤                                        
│ Geheugen                            │ Vaste stack, standaard ~1 MB gereserveerd      │ Stack staat op de heap en groeit en krimpt mee, vaak maar een paar KB │                                        
├─────────────────────────────────────┼────────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────────┤                                        
│ Hoeveel kunnen er tegelijk bestaan? │ Duizenden                                      │ Miljoenen                                                             │                                        
├─────────────────────────────────────┼────────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────────┤                                        
│ Aanmaken                            │ Duur, dus je gebruikt een pool                 │ Goedkoop, dus één per taak en niet poolen                             │

2. Wat is een carrier thread, en wat betekenen mount en unmount?
- Een carrier thread is een gewone platform thread uit de ForkJoinPool van de JVM. Alleen een carrier kan echt code uitvoeren op de CPU.
    - Mount: de JVM zet een virtual thread op een vrije carrier en voert zijn code uit.
    - Unmount: zodra de virtual thread moet wachten (sleep, database-call, HTTP-request, socket-I/O), slaat de JVM zijn stack op de heap op en maakt de carrier vrij. Als het wachten voorbij is, wordt hij
      weer gemount, op welke carrier dan ook vrij is. Daarom zie je in experiment 2 soms een andere worker.
    - Pinning (uitzondering): soms kan een VT niet unmounten en blijft hij aan de carrier "vastgepind". Dat gebeurt bijvoorbeeld in native code (JNI), en vóór Java 24 ook binnen een synchronized-blok. JEP
      491 (Java 24) heeft dat laatste opgelost. Jij gebruikt Java 25, dus daar heb je geen last meer van.

3. Hoeveel carriers zijn er standaard? sysctl -n hw.ncpu resultateert in 10.
   Evenveel als het aantal beschikbare CPU-cores (availableProcessors()). in mijn macbook 10 cores

--Vragen--

--Experiement 3--
Kijk of je de extra worker threads kan starten met:
 -Djdk.virtualThreadScheduler.parallelism=2                                                                                                                                                              
Zet dit in IntelliJ bij Run → Edit Configurations → VM options, herstart, en kijk of je alleen nog worker-1 en worker-2 ziet. Dat is meteen een leuk extra experiment voor je README.                   
 