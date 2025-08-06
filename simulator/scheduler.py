import heapq
from simulator.event import Event


class NextEventScheduler:
    def __init__(self):
        """
            Next event scheduler that manage all the event in system
        """

        self.job_table = {}
        self.event_queue: list[Event] = []
        self.current_time = 0.0
        self.subscribers = {}  # post‐event handlers
        self.interceptors = {}  # pre‐event handlers

    def schedule(self, event: Event, delay=0.0):
        """
        Insert an event in the event queue
        :param event: the event to manage
        :param delay:
        """
        event.time = (self.current_time + delay) if delay else event.time
        heapq.heappush(self.event_queue, event)

    def cancel(self, event: Event):
        if event in self.event_queue:
            event.cancelled = True

    def has_next(self) -> bool:
        return any(not e.cancelled for e in self.event_queue)

    def next(self) -> Event | None:
        while self.event_queue:
            ev = heapq.heappop(self.event_queue)
            if ev.cancelled: continue
            self.current_time = ev.time

            # pre‐event (interceptors)
            for handler in self.interceptors.get(ev.event_type, []):
                handler(ev, self)
            # post‐event (subscribers)
            for handler in self.subscribers.get(ev.event_type, []):
                # print(f"Event {ev.event_type} at {ev.time} for job {ev.job_id} with class{ev.job_class} on server {ev.server}")
                handler(ev, self)
            return ev
        return None

    def subscribe(self, event_type, handler):
        self.subscribers.setdefault(event_type, []).append(handler)

    def intercept(self, event_type, handler):
        self.interceptors.setdefault(event_type, []).append(handler)
