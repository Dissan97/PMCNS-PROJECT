import itertools

class Job:
    _ids = itertools.count()

    def __init__(self, job_class, arrival_time, service_time):
        self.id = next(Job._ids)
        self.job_class = job_class
        self.arrival_time = arrival_time
        self.remaining_service = service_time

    def __repr__(self):
        return (f"Job(id={self.id}, class={self.job_class}, "
                f"arr={self.arrival_time:.4f}, rem={self.remaining_service:.4f})")
