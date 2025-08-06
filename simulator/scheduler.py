import heapq

class NextEventScheduler:
    def __init__(self):
        self.event_queue = []
        self.current_time = 0.0
        self.subscribers  = {}   # post‐event handlers
        self.interceptors = {}   # pre‐event handlers

    def schedule(self, event, delay=0.0):
        # Inserisce event in coda; delay opzionale su current_time
        event.time = (self.current_time + delay) if delay else event.time
        heapq.heappush(self.event_queue, event)

    def cancel(self, event):
        event.cancelled = True

    def has_next(self):
        return any(not e.cancelled for e in self.event_queue)

    def next(self):
        while self.event_queue:
            ev = heapq.heappop(self.event_queue)
            if ev.cancelled: continue
            self.current_time = ev.time

            # pre‐event (interceptors)
            for h in self.interceptors.get(ev.event_type, []):
                h(ev, self)
            # post‐event (subscribers)
            for h in self.subscribers.get(ev.event_type, []):
                #print(f"Event {ev.event_type} at {ev.time} for job {ev.job_id} with class{ev.job_class} on server {ev.server}")
                h(ev, self)
            return ev
        return None

    def subscribe(self, event_type, handler):
        self.subscribers.setdefault(event_type, []).append(handler)

    def intercept(self, event_type, handler):
        self.interceptors.setdefault(event_type, []).append(handler)
