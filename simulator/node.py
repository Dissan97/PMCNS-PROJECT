from simulator.event import Event

class Node:
    def __init__(self, name, service_rates):
        self.name = name
        self.service_rates = service_rates  # μ per classe c
        self.jobs = []       # jobs in servizio o in coda
        self.last_update = 0 # ultimo t aggiornamento PS

    def _update_remaining(self, now):
        # Eq.1–3: in PS ogni job riceve μ_i/N share di servizio
        if not self.jobs:
            self.last_update = now
            return
        elapsed = now - self.last_update
        share = elapsed / len(self.jobs)
        for job in self.jobs:
            """
            if self.name == "B" and job.job_class != 1:
                print(f"brooooooooo: {job}")
            if self.name == "P" and job.job_class != 2:
                print(f"broski: {job}")
            print(f"Updating job {job.id} with class {job.job_class} at node {self.name} with rate {self.service_rates[job.job_class]}")
            """
            mu = self.service_rates[job.job_class]
            job.remaining_service = max(0.0,
                job.remaining_service - share * mu)
        self.last_update = now

    def arrival(self, job, scheduler):
        self._update_remaining(scheduler.current_time)
        self.jobs.append(job)
        self._schedule_departure(scheduler)

    def departure(self, job, scheduler):
        self._update_remaining(scheduler.current_time)
        self.jobs = [j for j in self.jobs if j.id != job.id]
        self._schedule_departure(scheduler)

    def _schedule_departure(self, scheduler):
        if not self.jobs: return
        # scelgo job con min remaining_service/mu
        min_job = min(self.jobs,
                      key=lambda j: j.remaining_service / self.service_rates[j.job_class])
        ttf = (min_job.remaining_service * len(self.jobs)
               / self.service_rates[min_job.job_class])
        #selecting job class change
        """
        next_class = min_job.job_class
        if self.name == "B" and min_job.job_class == 1:
            next_class = 2
        elif self.name == "P" and min_job.job_class == 2:
            next_class = 3
        """ 
        e = Event(time=scheduler.current_time + ttf,
                  event_type="DEPARTURE",
                  server=self.name,
                  job_id=min_job.id,
                  job_class=min_job.job_class)
        scheduler.schedule(e)
