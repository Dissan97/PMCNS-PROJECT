# Installation Guide

## Requirements 
- Java version >= 21 <br>
- maven installed
### Installation
    mvn clean package
### Run
    java -jar ./target/WebAppSimulator.jar

## Output Example
    SysLogger[INFO] Select configuration:
    1) obj1.json
    2) obj2.json
    3) obj3.json
    4) Custom path 
      Enter choice [1-4]:
      1
      SysLogger[INFO] {P={2=TargetClass[serverTarget=A, eventClass=3]}, A={1=TargetClass[serverTarget=B, 
        eventClass=1], 2=TargetClass[serverTarget=P, eventClass=2], 3=TargetClass[serverTarget=A, eventClass=EXIT]}, 
        B={1=TargetClass[serverTarget=A, eventClass=2]}}

      SysLogger[INFO] Global metrics
      ┌──────────────────┬─────────────────┬───────────────┬──────────────┬──────────┬───────────┐
      │mean_response_time│std_response_time│mean_population│std_population│throughput│utilization│
      ├──────────────────┼─────────────────┼───────────────┼──────────────┼──────────┼───────────┤
      │    25,620908     │    18,225496    │   30,898842   │  23,656467   │ 1,202066 │ 0,996857  │
      └──────────────────┴─────────────────┴───────────────┴──────────────┴──────────┴───────────┘
      
    SysLogger[INFO] Per-node metrics
      ┌────┬──────────────────┬─────────────────┬───────────────┬──────────────┬──────────┬───────────┐
      │Node│mean_response_time│std_response_time│mean_population│std_population│throughput│utilization│
      ├────┼──────────────────┼─────────────────┼───────────────┼──────────────┼──────────┼───────────┤
      │ A  │     1,591980     │    3,061087     │   5,867374    │   5,884160   │ 1,202066 │ 0,841941  │
      ├────┼──────────────────┼─────────────────┼───────────────┼──────────────┼──────────┼───────────┤
      │ B  │    20,076426     │    32,320374    │   24,606252   │  22,911153   │ 0,000000 │ 0,962229  │
      ├────┼──────────────────┼─────────────────┼───────────────┼──────────────┼──────────┼───────────┤
      │ P  │     0,768541     │    0,976481     │   1,423486    │   1,420669   │ 0,000000 │ 0,479826  │
      └────┴──────────────────┴─────────────────┴───────────────┴──────────────┴──────────┴───────────┘
      SysLogger[INFO] Simulation 1 Completed: external=1000010, done=1000010, took=6,393720 s