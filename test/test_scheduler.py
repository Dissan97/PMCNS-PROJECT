import sys
import os
sys.path.insert(0, os.path.abspath("../"))
from simulator.event import Event
import heapq
from simulator.scheduler import NextEventScheduler

# ==== TEST SCHEDULER ====
if __name__ == "__main__":
    # Creiamo lo scheduler
    scheduler = NextEventScheduler()

    # Handlers di esempio
    def interceptor_example(event, scheduler):
        print(f"[Interceptor] Evento {event.event_type} sul server {event.server} prima della logica.")

    def subscriber_example(event, scheduler):
        print(f"[Subscriber] Evento {event.event_type} sul server {event.server} dopo la logica.")

    # Registriamo gli handler
    scheduler.intercept("ARRIVAL", interceptor_example)
    scheduler.subscribe("ARRIVAL", subscriber_example)

    scheduler.intercept("DEPARTURE", interceptor_example)
    scheduler.subscribe("DEPARTURE", subscriber_example)

    # Creiamo eventi di esempio
    e1 = Event(time=0.0, event_type="ARRIVAL", server="A", job_id=1, job_class=1)
    e2 = Event(time=0.0, event_type="DEPARTURE", server="B", job_id=2, job_class=1)
    e3 = Event(time=0.0, event_type="ARRIVAL", server="P", job_id=3, job_class=2)

    # Pianifichiamo eventi con diversi delay
    scheduler.schedule(e1, delay=5.0)  # eseguirà a t=5
    scheduler.schedule(e2, delay=2.0)  # eseguirà a t=2
    scheduler.schedule(e3, delay=7.0)  # eseguirà a t=7

    # Cancelliamo il terzo evento per test
    scheduler.cancel(e3)

    # Eseguiamo la simulazione
    while scheduler.has_next():
        event = scheduler.next()
        if event:
            print(f"[Main] Eseguito evento: {event}")
