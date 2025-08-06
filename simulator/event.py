import itertools

class Event:
    _ids = itertools.count()

    def __init__(self, time, event_type, server=None, job_id=None, job_class=None):
        """
            Event constructor
            :param time: the time of the event
            :param event_type: the type of event ARRIVAL | DEPARTURE
            :param server: the server that manage the event
            :param job_id: the job id unique for this event
            :param job_class: the job class | class-1 | class-2 | class-3
        """
        self.id = next(Event._ids)
        self.time = time
        self.event_type = event_type  # "ARRIVAL" o "DEPARTURE"
        self.server = server
        self.job_id = job_id
        self.job_class = job_class
        self.cancelled = False

    def __lt__(self, other):
        if self.time == other.time:
            return self.id < other.id
        return self.time < other.time

    def __repr__(self):
        return (f"Event(t={self.time:.4f}, type={self.event_type}, "
                f"server={self.server}, job={self.job_id}, class={self.job_class})")
